"""A two-way bridge between Blender and Hellcraft's Blockbench models (versions/26.3/art/<model>.bbmodel), so a model
can be reshaped, painted and re-animated in Blender and still come out as what Minecraft draws: boxes on bones.

Run it inside Blender (5.x), from the Blender MCP or Blender's Python console:

    import sys; sys.path.insert(0, "<repo>/tools/art"); import blender_bridge as bb
    bb.import_model("minos")            # versions/26.3/art/minos.bbmodel -> a "minos" collection
    bb.play("minos", "strike")          # pose the bones with one animation (scrub the timeline to see it)
    bb.rest("minos")                    # back to the rest pose (edit pivots and cubes here)
    bb.export_model("minos")            # the collection -> versions/26.3/art/minos.bbmodel
    then, outside Blender: python3 tools/gen_bosses.py

or headless, to import and re-export models unchanged (the bridge's own test; writes over the .bbmodel unless an
output folder is given):   blender -b --factory-startup -P tools/art/blender_bridge.py -- roundtrip <model|all> [dir]

What the scene holds (one collection per model, named after it):
- An Empty named after the model (the root) holding the file's other fields. Bones are Empties parented under it, one
  per Blockbench group, at the group's pivot and rest rotation; cubes are mesh objects (8 vertices, 6 faces), each
  parented to its bone, with its origin at the cube's own rotation origin. Everything keeps its Blockbench name, uuid
  and order in custom properties ("bb_*"), so Blender's ".001" renames change nothing.
- Each cube's UVs are Minecraft's own box UV at its uv_offset ("bb_uv"), with mirror_uv ("bb_mirror"); inflate
  ("bb_inflate") is stored, not drawn. One material shows the texture with nearest filtering and the glow texture as
  emission. The images are "<model>.png" and "<model>_glow.png": paint them in Texture Paint, at the same size.
- Each animation is an Action named "<model>.<anim>", one slot per bone it moves; its length and loop are custom
  properties ("bb_length", "bb_loop"), its events pose markers named after the event (one frame = one game tick, 20 fps).
  Keyframes hold the bone's whole value (rest + the animation's offset, as Minecraft adds them); interpolation BEZIER
  stands for Catmull-Rom and LINEAR for linear.

Axes: 1 Blender unit = 1 block = 16 pixels. Blender is Z up and the model faces -Y (Front view looks at its face);
Blockbench is Y up and the model faces -Z, with +X its right hand. A point maps Blender (x, y, z) <-> Blockbench
(-16x, 16z, 16y). Minecraft and Blockbench turn a part by R = Rz Ry Rx (X first) in Blockbench's axes; in Blender's
that is Euler mode XZY with angles (-rx, rz, ry).

Only what Minecraft's ModelPart can draw survives export: axis-aligned boxes (whole-pixel sizes), turned as a whole,
on a tree of bones. Export refuses anything else (extra vertices, modifiers, a cube outside every bone, sheared or
negative scale) rather than guess.
"""
import base64
import hashlib
import json
import math
import os
import struct
import sys
import tempfile
import uuid
import zlib

import bpy
import numpy as np
from mathutils import Euler, Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.normpath(os.path.join(HERE, "..", "..", "versions", "26.3", "art"))
NAMESPACE = uuid.UUID("6f2b8c1e-3d4a-4b5c-9e8f-1a2b3c4d5e6f")  # tools/art/bbmodel.py's, so ids match
FPS = 20
ROT_MODE = "XZY"
INTERP_IN = {"catmullrom": "BEZIER", "bezier": "BEZIER", "linear": "LINEAR", "step": "CONSTANT"}
INTERP_OUT = {"BEZIER": "catmullrom", "LINEAR": "linear", "CONSTANT": "step"}
CHANNELS = (("rotation", "rotation_euler"), ("position", "location"), ("scale", "scale"))
UV_FACES = {"north": "front", "east": "right", "south": "back", "west": "left", "up": "top", "down": "bottom"}


def stable_uuid(*parts):
    return str(uuid.uuid5(NAMESPACE, "/".join(str(p) for p in parts)))


class BridgeError(Exception):
    pass


# ------------------------------------------------------------------------------------------------- axes and numbers

def to_blender(v):
    """A Blockbench vector (pixels) -> Blender (blocks)."""
    return Vector((-v[0] / 16.0, v[2] / 16.0, v[1] / 16.0))


def to_bb(v):
    return [-v[0] * 16.0, v[2] * 16.0, v[1] * 16.0]


def rot_to_blender(r):
    """Blockbench degrees (x, y, z) -> Blender radians for Euler mode XZY."""
    return (math.radians(-r[0]), math.radians(r[2]), math.radians(r[1]))


def rot_to_bb(e):
    return [-math.degrees(e[0]), math.degrees(e[2]), math.degrees(e[1])]


def pos_to_blender(p):
    """An animation's position offset (Blockbench pixels, in the parent's axes) -> Blender."""
    return to_blender(p)


def scale_to_blender(s):
    return (s[0], s[2], s[1])


def scale_to_bb(s):
    return [s[0], s[2], s[1]]


def clean(v):
    """Blender keeps floats in 32 bits: snap a value back to the short decimal it most likely came from."""
    v = float(v)
    # the shortest of 3, 4 or 5 decimals within 3e-5: float32 noise on a rest-plus-offset value in radians or blocks
    # stays under that, and the game's files print 4 decimals, so a snap can't change them
    for places in (3, 4, 5):
        r = round(v, places)
        if abs(r - v) <= 3e-5:
            v = r
            break
    if v == 0:
        v = 0.0
    if v.is_integer():
        return float(int(v))
    return v


def num(v):
    """A keyframe value: a number, or (Molang) text holding one; keeps -0.0 as it is."""
    if isinstance(v, (int, float)):
        return float(v)
    v = str(v or "").strip()
    return float(v) if v else 0.0


def cleanv(vs):
    return [clean(v) for v in vs]


def same32(a, b):
    """Whether two lists hold the same 32-bit floats (what Blender stores)."""
    return len(a) == len(b) and all(np.float32(x) == np.float32(y) for x, y in zip(a, b))


def _memo(idb):
    """What an imported value was exactly, and what Blender held right after import ("snap"): while Blender still
    holds the same 32-bit numbers, export writes the exact originals back, so an untouched model comes out unchanged."""
    raw = idb.get("bb_memo")
    return json.loads(raw) if raw else None


# ------------------------------------------------------------------------------------------------- textures

def png_bytes(width, height, rgba):
    """rgba: uint8 array (height, width, 4), top row first. The same encoder as tools/art/bbmodel.py."""
    raw = np.zeros((height, width * 4 + 1), dtype=np.uint8)
    raw[:, 1:] = rgba.reshape(height, width * 4)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw.tobytes(), 9)) + chunk(b"IEND", b""))


def image_rgba(img):
    """An image's pixels as uint8 (height, width, 4), top row first."""
    w, h = img.size
    buf = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(buf)
    return np.flipud(np.round(buf.reshape(h, w, 4) * 255.0).clip(0, 255).astype(np.uint8))


def pixel_hash(img):
    return hashlib.sha1(image_rgba(img).tobytes()).hexdigest()


def load_image(name, png):
    old = bpy.data.images.get(name)
    if old is not None:
        bpy.data.images.remove(old)
    path = os.path.join(tempfile.gettempdir(), f"bb_bridge_{os.getpid()}_{name}")
    with open(path, "wb") as fh:
        fh.write(png)
    img = bpy.data.images.load(path)
    img.name = name
    img.pack()
    img.filepath = ""
    os.remove(path)
    img.alpha_mode = "STRAIGHT"
    img.colorspace_settings.name = "sRGB"
    return img


def image_source(img):
    """The texture as Blockbench embeds it: the original bytes while the pixels are untouched, else re-encoded."""
    if img.get("bb_source") and img.get("bb_hash") == pixel_hash(img):
        return img["bb_source"]
    w, h = img.size
    return "data:image/png;base64," + base64.b64encode(png_bytes(w, h, image_rgba(img))).decode()


def make_material(model, tex, glow):
    name = model
    old = bpy.data.materials.get(name)
    if old is not None:
        bpy.data.materials.remove(old)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nodes, links = nt.nodes, nt.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Roughness"].default_value = 1.0
    t = nodes.new("ShaderNodeTexImage")
    t.image = tex
    t.interpolation = "Closest"
    t.location = (-500, 200)
    links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(t.outputs["Alpha"], bsdf.inputs["Alpha"])
    if glow is not None:
        g = nodes.new("ShaderNodeTexImage")
        g.image = glow
        g.interpolation = "Closest"
        g.location = (-500, -150)
        links.new(g.outputs["Color"], bsdf.inputs["Emission Color"])
        links.new(g.outputs["Alpha"], bsdf.inputs["Emission Strength"])
    nodes.active = t  # Texture Paint and Solid view's "Texture" colour use the active image node
    try:
        mat.surface_render_method = "DITHERED"
    except AttributeError:
        pass
    return mat


# ------------------------------------------------------------------------------------------------- cubes

def box_vertices(frm, to, origin, mirror):
    """A cube's 8 corners (Blender, relative to its origin) in Minecraft's order v0..v7, and its 6 faces with their
    texel rectangles, exactly as net.minecraft.client.model.geom.ModelPart.Cube builds them.

    Minecraft's model space is Blockbench's with X and Y negated, so a Minecraft point (X, Y, Z) is Blender
    (X, Z, -Y) / 16."""
    lo = [-to[0] + origin[0], -to[1] + origin[1], frm[2] - origin[2]]  # relative to the origin, in Minecraft's axes
    hi = [-frm[0] + origin[0], -frm[1] + origin[1], to[2] - origin[2]]
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    if mirror:
        x0, x1 = x1, x0
    mc = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    return [Vector((x / 16.0, z / 16.0, -y / 16.0)) for x, y, z in mc]


def box_faces(size, uv):
    """(vertex indices, (u1, v1, u2, v2)) per face, in Minecraft's order: down, up, west, north, east, south."""
    w, h, d = size
    u, v = uv
    us = [u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w]
    vs = [v, v + d, v + d + h]
    return [((5, 4, 0, 1), (us[1], vs[0], us[2], vs[1])),
            ((2, 3, 7, 6), (us[2], vs[1], us[3], vs[0])),
            ((0, 4, 7, 3), (us[0], vs[1], us[1], vs[2])),
            ((1, 0, 3, 2), (us[1], vs[1], us[2], vs[2])),
            ((5, 1, 2, 6), (us[2], vs[1], us[4], vs[2])),
            ((4, 5, 6, 7), (us[4], vs[1], us[5], vs[2]))]


def build_cube_mesh(name, frm, to, origin, uv, mirror, tex_w, tex_h, mesh=None):
    size = [to[i] - frm[i] for i in range(3)]
    verts = box_vertices(frm, to, origin, mirror)
    faces = box_faces(size, uv)
    polys = []
    uvs = []
    for idx, (u1, v1, u2, v2) in faces:
        # ModelPart.Polygon: vertex 0 -> (u2, v1), 1 -> (u1, v1), 2 -> (u1, v2), 3 -> (u2, v2); a mirrored cube
        # reverses each polygon's vertices (with their UVs) so it still faces out
        corner_uv = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
        pairs = list(zip(idx, corner_uv))
        if mirror:
            pairs.reverse()
        polys.append([p[0] for p in pairs])
        uvs.extend((cu / tex_w, 1.0 - cv / tex_h) for _, (cu, cv) in pairs)
    if mesh is None:
        mesh = bpy.data.meshes.new(name)
    else:
        mesh.clear_geometry()
    mesh.from_pydata([tuple(v) for v in verts], [], polys)
    layer = mesh.uv_layers.new(name="box_uv")
    for i, loop_uv in enumerate(uvs):
        layer.data[i].uv = loop_uv
    mesh.update()
    return mesh


def cube_bounds(obj):
    """A cube object's box in its own axes (Blender units), with its object scale applied; refuses non-boxes."""
    me = obj.data
    if obj.type != "MESH":
        raise BridgeError(f"{obj.name}: not a mesh")
    if obj.modifiers:
        raise BridgeError(f"{obj.name}: has modifiers ({', '.join(m.name for m in obj.modifiers)}); apply or remove them")
    if len(me.vertices) != 8 or len(me.polygons) != 6:
        raise BridgeError(f"{obj.name}: a cube needs 8 vertices and 6 faces, has {len(me.vertices)} and {len(me.polygons)}")
    s = obj.scale
    if min(s) <= 0:
        raise BridgeError(f"{obj.name}: negative or zero scale {tuple(s)}")
    co = np.array([[v.co[i] * s[i] for i in range(3)] for v in me.vertices])
    lo, hi = co.min(axis=0), co.max(axis=0)
    for i in range(3):
        for c in co[:, i]:
            if min(abs(c - lo[i]), abs(c - hi[i])) > 1e-4:
                raise BridgeError(f"{obj.name}: not an axis-aligned box (vertex off its faces on axis {'xyz'[i]})")
    return Vector(lo), Vector(hi)


# ------------------------------------------------------------------------------------------------- import

def _link(obj, coll):
    coll.objects.link(obj)
    return obj


def _set_extra(idb, d, drop):
    idb["bb_extra"] = json.dumps({k: v for k, v in d.items() if k not in drop})


def _get_extra(idb):
    return json.loads(idb["bb_extra"]) if "bb_extra" in idb else {}


def import_model(model, path=None, collection=None):
    """Reads versions/26.3/art/<model>.bbmodel (or path) into a fresh collection named after the model."""
    path = path or os.path.join(ART, model + ".bbmodel")
    with open(path) as fh:
        doc = json.load(fh)
    name = doc["name"]
    scene = bpy.context.scene
    scene.render.fps = FPS
    scene.render.fps_base = 1.0
    remove_model(name)
    coll = bpy.data.collections.new(collection or name)
    scene.collection.children.link(coll)

    root = _link(bpy.data.objects.new(name, None), coll)
    root.empty_display_type = "ARROWS"
    root.empty_display_size = 0.5
    root["bb_model"] = name
    root["bb_header"] = json.dumps({k: v for k, v in doc.items() if k not in ("elements", "outliner", "animations", "textures")})

    tex_w, tex_h = doc["resolution"]["width"], doc["resolution"]["height"]
    images = []
    for i, t in enumerate(doc.get("textures", [])):
        src = t["source"]
        tname = t["name"] if t["name"].endswith(".png") else t["name"] + ".png"
        img = load_image(tname, base64.b64decode(src.split(",", 1)[1]))
        img["bb_source"] = src
        img["bb_hash"] = pixel_hash(img)
        img["bb_index"] = i
        img["bb_model"] = name
        _set_extra(img, t, ("source",))
        images.append(img)
    tex = images[0] if images else None
    glow = next((im for im in images if im.name.endswith("_glow.png")), None)
    mat = make_material(name, tex, glow) if tex else None

    elements = {e["uuid"]: (i, e) for i, e in enumerate(doc["elements"])}
    bones = {}

    def add_group(g, parent_obj, parent_origin, order):
        b = _link(bpy.data.objects.new(g["name"], None), coll)
        b.empty_display_type = "PLAIN_AXES"
        b.empty_display_size = 0.12
        b.parent = parent_obj
        b.matrix_parent_inverse = Matrix.Identity(4)
        b.rotation_mode = ROT_MODE
        origin = g["origin"]
        b.location = to_blender([origin[i] - parent_origin[i] for i in range(3)])
        b.rotation_euler = rot_to_blender(g.get("rotation", [0, 0, 0]))
        b["bb_name"] = g["name"]
        b["bb_kind"] = "bone"
        b["bb_order"] = order
        b["bb_model"] = name
        _set_extra(b, g, ("name", "origin", "rotation", "children"))
        _store_rest(b)
        b["bb_memo"] = json.dumps({"exact": [list(origin), list(g.get("rotation", [0, 0, 0])), list(parent_origin)],
                                   "snap": list(b.location) + list(b.rotation_euler)})
        bones[g["name"]] = b
        for k, child in enumerate(g.get("children", [])):
            if isinstance(child, dict):
                add_group(child, b, origin, k)
            else:
                index, e = elements[child]
                add_cube_from_element(e, b, origin, k, index)

    def add_cube_from_element(e, bone, pivot, order, index):
        origin = e.get("origin", pivot)
        frm, to = e["from"], e["to"]
        uv = e.get("uv_offset", [0, 0])
        mirror = bool(e.get("mirror_uv"))
        me = build_cube_mesh(e["name"], frm, to, origin, uv, mirror, tex_w, tex_h)
        if mat:
            me.materials.append(mat)
        c = _link(bpy.data.objects.new(e["name"], me), coll)
        c.parent = bone
        c.matrix_parent_inverse = Matrix.Identity(4)
        c.rotation_mode = ROT_MODE
        c.location = to_blender([origin[i] - pivot[i] for i in range(3)])
        c.rotation_euler = rot_to_blender(e.get("rotation", [0, 0, 0]))
        c["bb_name"] = e["name"]
        c["bb_kind"] = "cube"
        c["bb_order"] = order
        c["bb_index"] = index
        c["bb_uv"] = [int(uv[0]), int(uv[1])]
        c["bb_inflate"] = float(e.get("inflate", 0) or 0)
        c["bb_mirror"] = mirror
        c["bb_model"] = name
        _set_extra(c, e, ("name", "from", "to", "origin", "rotation", "uv_offset", "inflate", "mirror_uv", "faces"))
        lo, hi = cube_bounds(c)
        c["bb_memo"] = json.dumps({"exact": {"from": list(frm), "to": list(to), "origin": list(origin), "pivot": list(pivot),
                                             "rotation": list(e.get("rotation", [0, 0, 0]))},
                                   "snap": list(lo) + list(hi) + list(c.location) + list(c.rotation_euler)})

    for k, g in enumerate(doc["outliner"]):
        if not isinstance(g, dict):
            raise BridgeError(f"{path}: cube {elements[g][1]['name']} is outside every bone")
        add_group(g, root, [0, 0, 0], k)

    for i, a in enumerate(doc.get("animations", [])):
        _import_animation(name, a, i, bones)
    root["bb_playing"] = ""
    return coll


def _store_rest(b):
    b["bb_rest_loc"] = list(b.location)
    b["bb_rest_rot"] = list(b.rotation_euler)


def _anim_short(model, a_name):
    prefix = f"animation.{model}."
    return a_name[len(prefix):] if a_name.startswith(prefix) else a_name.split(".")[-1]


def _import_animation(model, a, index, bones):
    short = _anim_short(model, a["name"])
    act_name = f"{model}.{short}"
    old = bpy.data.actions.get(act_name)
    if old is not None:
        bpy.data.actions.remove(old)
    act = bpy.data.actions.new(act_name)
    act.use_fake_user = True
    act["bb_model"] = model
    act["bb_anim"] = short
    act["bb_index"] = index
    act["bb_length"] = float(a["length"])
    act["bb_loop"] = a.get("loop") == "loop"
    _set_extra(act, a, ("animators", "length", "loop"))
    act.use_frame_range = True
    act.frame_start = 0
    act.frame_end = max(1.0, float(a["length"]) * FPS)
    act.use_cyclic = a.get("loop") == "loop"
    layer = act.layers.new("bb")
    strip = layer.strips.new(type="KEYFRAME")
    memo = {}
    events = []
    for animator in a.get("animators", {}).values():
        if animator.get("type") == "effect":
            for k in animator["keyframes"]:
                if k["channel"] == "timeline":
                    for dp in k["data_points"]:
                        script = str(dp.get("script", "")).strip()
                        if script:
                            events.append((float(k["time"]), script))
            continue
        bone = bones.get(animator["name"])
        if bone is None:
            raise BridgeError(f"{a['name']} moves {animator['name']}, which isn't a bone")
        slot = act.slots.new(id_type="OBJECT", name=animator["name"])
        bag = strip.channelbag(slot, ensure=True)
        rest_loc = Vector(bone["bb_rest_loc"])
        rest_rot = Vector(bone["bb_rest_rot"])
        for channel, prop in CHANNELS:
            keys = sorted((k for k in animator["keyframes"] if k["channel"] == channel), key=lambda k: float(k["time"]))
            if not keys:
                continue
            curves = [bag.fcurves.new(prop, index=i) for i in range(3)]
            for fc in curves:
                fc.keyframe_points.add(len(keys))
            frames = []
            for k in keys:
                fr = float(k["time"]) * FPS
                if frames and fr < frames[-1] + 0.02:
                    fr = frames[-1] + 0.02  # Blender merges keys within 0.01 frame; keep a second one just after
                frames.append(fr)
            for j, k in enumerate(keys):
                dp = k["data_points"][0]
                vec = [num(dp.get(ax, 0)) for ax in "xyz"]
                if channel == "rotation":
                    val = Vector(rot_to_blender(vec)) + rest_rot
                elif channel == "position":
                    val = pos_to_blender(vec) + rest_loc
                else:
                    val = Vector(scale_to_blender(vec))
                interp = INTERP_IN.get(k.get("interpolation", "linear"), "LINEAR")
                for i, fc in enumerate(curves):
                    kp = fc.keyframe_points[j]
                    kp.co = (frames[j], val[i])
                    kp.interpolation = interp
                    kp.handle_left_type = kp.handle_right_type = "AUTO"
            for fc in curves:
                fc.update()
            for j, k in enumerate(keys):
                dp = k["data_points"][0]
                memo[f"{animator['name']}|{channel}|{j}"] = [
                    [float(k["time"])] + [num(dp.get(ax, 0)) for ax in "xyz"],
                    [c for fc in curves for c in fc.keyframe_points[j].co]]
    act["bb_memo"] = json.dumps(memo)
    events.sort()
    act["bb_events"] = json.dumps(events)
    for t, ev in events:
        m = act.pose_markers.new(ev)
        m.frame = int(round(t * FPS))
    return act


# ------------------------------------------------------------------------------------------------- scene helpers

def model_root(model):
    root = bpy.data.objects.get(model)
    if root is None or root.get("bb_model") != model or "bb_header" not in root:
        for o in bpy.data.objects:
            if o.get("bb_model") == model and "bb_header" in o:
                return o
        raise BridgeError(f"no model {model} in this scene (import_model first)")
    return root


def model_objects(model):
    root = model_root(model)
    return [o for o in root.children_recursive]


def model_bones(model):
    return {(o.get("bb_name") or o.name): o for o in model_objects(model)
            if o.get("bb_kind") == "bone" or (o.type == "EMPTY" and "bb_kind" not in o)}


def model_actions(model):
    acts = [a for a in bpy.data.actions if a.get("bb_model") == model]
    return sorted(acts, key=lambda a: (a.get("bb_index", 1 << 30), a.name))


def remove_model(model):
    """Deletes a model's collection, objects, meshes, actions, material and images from this .blend."""
    coll = bpy.data.collections.get(model)
    if coll is not None:
        for o in list(coll.all_objects):
            data = o.data
            bpy.data.objects.remove(o)
            if data is not None and data.users == 0 and isinstance(data, bpy.types.Mesh):
                bpy.data.meshes.remove(data)
        bpy.data.collections.remove(coll)
    for a in [a for a in bpy.data.actions if a.get("bb_model") == model]:
        bpy.data.actions.remove(a)
    for im in [im for im in bpy.data.images if im.get("bb_model") == model]:
        bpy.data.images.remove(im)


def _capture_rest(model):
    """Takes the bones' current transforms as their rest pose (the scene is at rest), shifting every animation's
    keys by the same amount so each animation's offsets from rest stay as they were."""
    shifts = {}
    for name, b in model_bones(model).items():
        if b.rotation_mode != ROT_MODE:
            b.rotation_mode = ROT_MODE
        if any(abs(s - 1) > 1e-6 for s in b.scale):
            raise BridgeError(f"bone {name}: scale {tuple(b.scale)} at rest; bones must rest at scale 1 (scale the cubes)")
        if "bb_rest_loc" not in b:
            _store_rest(b)
            continue
        dl = Vector(b.location) - Vector(b["bb_rest_loc"])
        dr = Vector(b.rotation_euler) - Vector(b["bb_rest_rot"])
        if dl.length > 1e-7 or dr.length > 1e-7:
            shifts[name] = (dl, dr)
        _store_rest(b)
    if shifts:
        for act in model_actions(model):
            for slot in act.slots:
                if slot.name_display not in shifts:
                    continue
                dl, dr = shifts[slot.name_display]
                for layer in act.layers:
                    for strip in layer.strips:
                        bag = strip.channelbag(slot)
                        if bag is None:
                            continue
                        for fc in bag.fcurves:
                            d = {"location": dl, "rotation_euler": dr}.get(fc.data_path)
                            if d is None:
                                continue
                            for kp in fc.keyframe_points:
                                for p in (kp.co, kp.handle_left, kp.handle_right):
                                    p[1] += d[fc.array_index]
                            fc.update()


def rest(model):
    """Unhooks every animation and puts each bone back at its rest pose."""
    root = model_root(model)
    for b in model_bones(model).values():
        if b.animation_data:
            b.animation_data.action = None
        if "bb_rest_loc" in b:
            b.location = b["bb_rest_loc"]
            b.rotation_euler = b["bb_rest_rot"]
        b.scale = (1, 1, 1)
    root["bb_playing"] = ""


def play(model, anim, frame=None):
    """Poses the model with one animation: each bone it moves gets the action and its slot, the others rest. The
    scene's frame range becomes the animation's. Leaving the rest pose first keeps any edits made to it."""
    root = model_root(model)
    if not root.get("bb_playing"):
        _capture_rest(model)
    act = bpy.data.actions.get(f"{model}.{anim}") or bpy.data.actions.get(anim)
    if act is None or act.get("bb_model") != model:
        raise BridgeError(f"{model} has no animation {anim}: " + ", ".join(a["bb_anim"] for a in model_actions(model)))
    rest(model)
    slots = {s.name_display: s for s in act.slots}
    for name, b in model_bones(model).items():
        slot = slots.get(name)
        if slot is None:
            continue
        ad = b.animation_data or b.animation_data_create()
        ad.action = act
        ad.action_slot = slot
    scene = bpy.context.scene
    scene.frame_start = 0
    scene.frame_end = int(math.ceil(act["bb_length"] * FPS))
    scene.frame_set(int(frame) if frame is not None else 0)
    root["bb_playing"] = act["bb_anim"]
    return act


def new_animation(model, anim, length, loop=False):
    """An empty animation to key in Blender (bones get keys with play() and Insert Keyframe as usual; add a slot per
    bone through the action editor, or use key())."""
    act = bpy.data.actions.new(f"{model}.{anim}")
    act.use_fake_user = True
    act["bb_model"] = model
    act["bb_anim"] = anim
    act["bb_index"] = max([a.get("bb_index", -1) for a in model_actions(model)] + [-1]) + 1
    act["bb_length"] = float(length)
    act["bb_loop"] = bool(loop)
    act["bb_events"] = "[]"
    act.use_frame_range = True
    act.frame_start = 0
    act.frame_end = max(1.0, length * FPS)
    act.layers.new("bb").strips.new(type="KEYFRAME")
    return act


def add_bone(model, parent, name, pivot, rotation=(0, 0, 0)):
    """Adds a bone (Blockbench pivot in pixels and rest rotation in degrees) under parent (a bone's name, or None for
    the model's root)."""
    root = model_root(model)
    if name in model_bones(model):
        raise BridgeError(f"{model} already has a bone {name}")
    par = model_bones(model)[parent] if parent else root
    parent_pivot = bone_pivot(model, parent) if parent else [0.0, 0.0, 0.0]
    b = bpy.data.objects.new(name, None)
    for coll in root.users_collection:
        coll.objects.link(b)
    b.empty_display_type = "PLAIN_AXES"
    b.empty_display_size = 0.12
    b.parent = par
    b.matrix_parent_inverse = Matrix.Identity(4)
    b.rotation_mode = ROT_MODE
    b.location = to_blender([pivot[i] - parent_pivot[i] for i in range(3)])
    b.rotation_euler = rot_to_blender(rotation)
    b["bb_name"] = name
    b["bb_kind"] = "bone"
    b["bb_model"] = model
    _store_rest(b)
    return b


def key(model, anim, bone, channel, keys, interp="catmullrom"):
    """Keys a bone in an animation the Blockbench way: channel "rotation" (degrees), "position" (pixels) or "scale",
    keys [(seconds, (x, y, z)), ...] as offsets from rest (scale as factors). Replaces that bone's keys on the channel."""
    act = bpy.data.actions.get(f"{model}.{anim}")
    if act is None:
        raise BridgeError(f"{model} has no animation {anim}")
    b = model_bones(model)[bone]
    prop = dict(CHANNELS)[channel]
    slot = next((s for s in act.slots if s.name_display == bone), None) or act.slots.new(id_type="OBJECT", name=bone)
    if not act.layers:
        act.layers.new("bb").strips.new(type="KEYFRAME")
    bag = act.layers[0].strips[0].channelbag(slot, ensure=True)
    for i in range(3):
        old = bag.fcurves.find(prop, index=i)
        if old is not None:
            bag.fcurves.remove(old)
    curves = [bag.fcurves.new(prop, index=i) for i in range(3)]
    rest_loc, rest_rot = Vector(b["bb_rest_loc"]), Vector(b["bb_rest_rot"])
    for fc in curves:
        fc.keyframe_points.add(len(keys))
    for j, (t, vec) in enumerate(sorted(keys, key=lambda k: k[0])):
        if channel == "rotation":
            val = Vector(rot_to_blender(vec)) + rest_rot
        elif channel == "position":
            val = to_blender(vec) + rest_loc
        else:
            val = Vector(scale_to_blender(vec))
        for i, fc in enumerate(curves):
            kp = fc.keyframe_points[j]
            kp.co = (t * FPS, val[i])
            kp.interpolation = INTERP_IN[interp]
            kp.handle_left_type = kp.handle_right_type = "AUTO"
    for fc in curves:
        fc.update()


def add_cube(model, bone, name, frm, size, uv, rotation=(0, 0, 0), origin=None, inflate=0.0, mirror=False):
    """Adds a cube in Blockbench terms (pixels; frm = its lowest corner, size whole pixels) to a bone, with its box UV
    patch at uv (a free patch of the texture: 2 * (depth + width) wide, depth + height tall)."""
    bones = model_bones(model)
    b = bones[bone]
    if any(float(s) != int(s) or s <= 0 for s in size):
        raise BridgeError(f"{name}: box UV needs whole sizes, got {size}")
    pivot = bone_pivot(model, bone)
    origin = list(origin) if origin else pivot
    to = [frm[i] + size[i] for i in range(3)]
    tex = _texture(model)
    me = build_cube_mesh(name, frm, to, origin, uv, mirror, tex.size[0], tex.size[1])
    mat = bpy.data.materials.get(model)
    if mat:
        me.materials.append(mat)
    c = bpy.data.objects.new(name, me)
    for coll in model_root(model).users_collection:
        coll.objects.link(c)
    c.parent = b
    c.matrix_parent_inverse = Matrix.Identity(4)
    c.rotation_mode = ROT_MODE
    c.location = to_blender([origin[i] - pivot[i] for i in range(3)])
    c.rotation_euler = rot_to_blender(rotation)
    c["bb_name"] = name
    c["bb_kind"] = "cube"
    c["bb_uv"] = [int(uv[0]), int(uv[1])]
    c["bb_inflate"] = float(inflate)
    c["bb_mirror"] = bool(mirror)
    c["bb_model"] = model
    return c


def refresh_uvs(obj):
    """Rebuilds a cube's mesh and box UVs from its current box (after resizing it, or changing bb_uv/bb_mirror)."""
    model = obj["bb_model"]
    lo, hi = cube_bounds(obj)
    a, b = to_bb(lo), to_bb(hi)
    frm = [min(a[i], b[i]) for i in range(3)]
    to = [max(a[i], b[i]) for i in range(3)]
    tex = _texture(model)
    obj.scale = (1, 1, 1)
    build_cube_mesh(obj.name, cleanv(frm), cleanv(to), [0, 0, 0], obj["bb_uv"], bool(obj.get("bb_mirror")),
                    tex.size[0], tex.size[1], mesh=obj.data)


def bone_pivot(model, bone):
    """A bone's pivot in Blockbench model space (pixels), from its rest chain."""
    b = model_bones(model)[bone]
    p = [0.0, 0.0, 0.0]
    while b is not None and b.get("bb_kind") == "bone":
        loc = b["bb_rest_loc"] if "bb_rest_loc" in b else b.location
        d = to_bb(loc)
        p = [p[i] + d[i] for i in range(3)]
        b = b.parent
    return cleanv(p)


def _texture(model):
    ims = sorted((im for im in bpy.data.images if im.get("bb_model") == model and not im.name.endswith("_glow.png")),
                 key=lambda im: im.get("bb_index", 0))
    if not ims:
        raise BridgeError(f"{model} has no texture")
    return ims[0]


# ------------------------------------------------------------------------------------------------- export

def _local(obj):
    """(location, rotation_euler XZY) of an object relative to its parent's frame."""
    if obj.matrix_parent_inverse == Matrix.Identity(4) and obj.rotation_mode == ROT_MODE:
        return Vector(obj.location), Vector(obj.rotation_euler)
    m = obj.matrix_parent_inverse @ obj.matrix_basis
    loc, q, s = m.decompose()
    e = q.to_euler(ROT_MODE)
    return loc, Vector(e)


def export_model(model, path=None):
    """Writes the model's collection back to versions/26.3/art/<model>.bbmodel (or path), in the format
    tools/gen_bosses.py reads. Returns the path."""
    root = model_root(model)
    if not root.get("bb_playing"):
        _capture_rest(model)
    header = json.loads(root["bb_header"])
    tex = _texture(model)
    tex_w, tex_h = tex.size
    images = sorted((im for im in bpy.data.images if im.get("bb_model") == model), key=lambda im: (im.get("bb_index", 99), im.name))
    for im in images:
        if tuple(im.size) != (tex_w, tex_h):
            raise BridgeError(f"{im.name} is {tuple(im.size)}, the texture is {(tex_w, tex_h)}: keep them the same size")

    names = set()
    elements = []

    def cube_sort(o):
        return (o.get("bb_order", 1 << 30), o.name)

    def element(c, pivot):
        lo, hi = cube_bounds(c)
        loc, rot = _local(c)
        origin = [pivot[i] + to_bb(loc)[i] for i in range(3)]
        a, b = to_bb(lo), to_bb(hi)
        frm = [origin[i] + min(a[i], b[i]) for i in range(3)]
        to = [origin[i] + max(a[i], b[i]) for i in range(3)]
        frm, to, origin = cleanv(frm), cleanv(to), cleanv(origin)
        size = [to[i] - frm[i] for i in range(3)]
        name = c.get("bb_name") or c.name
        for s in size:
            if abs(s - round(s)) > 1e-3 or round(s) <= 0:
                raise BridgeError(f"cube {name}: size {size} isn't whole pixels (box UV needs them)")
        size = [int(round(s)) for s in size]
        to = [clean(frm[i] + size[i]) for i in range(3)]
        rotation = cleanv(rot_to_bb(rot))
        memo = _memo(c)
        if memo and same32(memo["snap"], list(lo) + list(hi) + list(loc) + list(rot)):
            ex = memo["exact"]
            shift = [pivot[i] - ex["pivot"][i] for i in range(3)]
            if any(shift):
                frm, to, origin = ([clean(v[i] + shift[i]) for i in range(3)] for v in (ex["from"], ex["to"], ex["origin"]))
            else:
                frm, to, origin = ex["from"], ex["to"], ex["origin"]
            rotation = ex["rotation"]
        if "bb_uv" not in c:
            raise BridgeError(f"cube {name}: no box UV patch (set its bb_uv, or make it with add_cube)")
        u, v = (int(x) for x in c["bb_uv"])
        w, h, d = size
        if u + 2 * (d + w) > tex_w or v + d + h > tex_h:
            raise BridgeError(f"cube {name}: its UV patch at {u},{v} runs off the {tex_w}x{tex_h} texture")
        extra = _get_extra(c)
        rects = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                 "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        faces = {}
        for bbf, ours in UV_FACES.items():
            x, y, fw, fh = rects[ours]
            faces[bbf] = {"uv": [x, y, x + fw, y + fh], "texture": 0}
        e = {"name": name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
             "allow_mirror_modeling": True, "from": frm, "to": to, "autouv": 0, "color": extra.get("color"),
             "inflate": c.get("bb_inflate", 0.0), "origin": origin, "uv_offset": [u, v], "faces": faces, "type": "cube",
             "uuid": stable_uuid(model, "cube", name)}
        for k, val in extra.items():
            if k not in ("from", "to", "origin", "uv_offset", "faces", "inflate", "name"):
                e[k] = val
        e["inflate"] = c.get("bb_inflate", 0.0)
        if any(abs(r) > 1e-9 for r in rotation):
            e["rotation"] = rotation
            if name + "_r" in names:
                raise BridgeError(f"two turned cubes named {name}")
            names.add(name + "_r")
        if c.get("bb_mirror"):
            e["mirror_uv"] = True
        return e

    def group(b, parent_origin):
        name = b.get("bb_name") or b.name
        if name in names:
            raise BridgeError(f"two parts named {name}")
        names.add(name)
        loc, rot = _local(b)
        if b.get("bb_rest_loc") is not None:
            loc, rot = Vector(b["bb_rest_loc"]), Vector(b["bb_rest_rot"])
        memo = _memo(b)
        if memo and same32(memo["snap"], list(loc) + list(rot)):
            ex_origin, rotation, ex_parent = memo["exact"]
            if list(parent_origin) == list(ex_parent):
                origin = ex_origin
            else:
                origin = cleanv([parent_origin[i] + ex_origin[i] - ex_parent[i] for i in range(3)])
        else:
            origin = cleanv([parent_origin[i] + to_bb(loc)[i] for i in range(3)])
            rotation = cleanv(rot_to_bb(rot))
        g = {"name": name, "origin": origin, "color": 0, "uuid": stable_uuid(model, "bone", name), "export": True,
             "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0}
        g.update({k: v for k, v in _get_extra(b).items() if k not in ("name", "origin", "rotation", "children")})
        children = []
        for ch in sorted(b.children, key=cube_sort):
            if ch.get("bb_kind") == "bone" or ch.type == "EMPTY":
                children.append(("g", ch))
            elif ch.type == "MESH":
                children.append(("c", ch))
        out = []
        for kind, ch in children:
            if kind == "g":
                out.append(group(ch, origin))
            else:
                e = element(ch, origin)
                elements.append((ch.get("bb_index", 1 << 30), ch.name, e))
                out.append(e["uuid"])
        g["children"] = out
        if any(abs(r) > 1e-9 for r in rotation):
            g["rotation"] = rotation  # after children, as tools/art/bbmodel.py writes it
        return g

    outliner = []
    for ch in sorted(root.children, key=cube_sort):
        if ch.type == "MESH":
            raise BridgeError(f"cube {ch.name} is outside every bone (parent it to a bone)")
        outliner.append(group(ch, [0, 0, 0]))
    order = sorted(elements, key=lambda t: (t[0], t[1]))
    els = []
    for i, (_, _, e) in enumerate(order):
        if e.get("color") is None:
            e["color"] = i % 8
        els.append(e)
    by_uuid = {}
    for e in els:
        by_uuid.setdefault(e["uuid"], []).append(e)
    for uid, same in by_uuid.items():
        if len(same) > 1:
            plain = [{k: v for k, v in e.items() if k != "color"} for e in same]
            if any(p != plain[0] for p in plain):
                raise BridgeError(f"cubes named {same[0]['name']} share an id but differ; rename them")
            print(f"blender_bridge: warning: {len(same)} cubes named {same[0]['name']} share one id (the game draws "
                  f"the same box for each); rename them when remaking {model}")

    bones = model_bones(model)
    animations = [_export_animation(model, act, bones) for act in model_actions(model)]

    textures = []
    for i, im in enumerate(images):
        t = _get_extra(im)
        t.update({"name": im.name, "width": tex_w, "height": tex_h, "uv_width": tex_w, "uv_height": tex_h})
        t.setdefault("id", str(i))
        t.setdefault("uuid", stable_uuid(model, "texture", im.name))
        t["source"] = image_source(im)
        textures.append(t)

    doc = dict(header)
    doc["name"] = model
    doc["resolution"] = {"width": tex_w, "height": tex_h}
    doc["elements"] = els
    doc["outliner"] = outliner
    doc["textures"] = textures
    doc["animations"] = animations
    keys = ["meta", "name", "model_identifier", "modded_entity_entity_class", "modded_entity_version",
            "modded_entity_flip_y", "visible_box", "variable_placeholders", "variable_placeholder_buttons",
            "timeline_setups", "unhandled_root_fields", "resolution", "elements", "outliner", "textures", "animations"]
    ordered = {k: doc[k] for k in keys if k in doc}
    ordered.update({k: v for k, v in doc.items() if k not in ordered})
    path = path or os.path.join(ART, model + ".bbmodel")
    with open(path, "w") as fh:
        json.dump(ordered, fh, indent=1)
        fh.write("\n")
    return path


def _export_animation(model, act, bones):
    short = act["bb_anim"]
    length = float(act["bb_length"])
    memo = _memo(act) or {}
    animators = {}
    for slot in act.slots:
        bone_name = slot.name_display
        b = bones.get(bone_name)
        if b is None:
            raise BridgeError(f"{act.name} moves {bone_name}, which isn't a bone")
        rest_loc = Vector(b["bb_rest_loc"])
        rest_rot = Vector(b["bb_rest_rot"])
        curves = {}
        for layer in act.layers:
            for strip in layer.strips:
                bag = strip.channelbag(slot)
                if bag is None:
                    continue
                for fc in bag.fcurves:
                    curves[(fc.data_path, fc.array_index)] = fc
        entry = {"name": bone_name, "type": "bone", "keyframes": []}
        for channel, prop in sorted(CHANNELS):
            fcs = [curves.get((prop, i)) for i in range(3)]
            if not any(fcs):
                continue
            if all(fcs) and len({len(fc.keyframe_points) for fc in fcs}) == 1 and all(
                    abs(fcs[0].keyframe_points[j].co[0] - fc.keyframe_points[j].co[0]) < 1e-4
                    for fc in fcs for j in range(len(fc.keyframe_points))):
                # the usual case: x, y and z keyed together, key for key (keeps two keys on one frame apart)
                for j in range(len(fcs[0].keyframe_points)):
                    kps = [fc.keyframe_points[j] for fc in fcs]
                    ex = memo.get(f"{bone_name}|{channel}|{j}")
                    if ex and same32(ex[1], [c for kp in kps for c in kp.co]):
                        t, vec = ex[0][0], ex[0][1:]
                    else:
                        vals = Vector([kp.co[1] for kp in kps])
                        t = clean(kps[0].co[0] / FPS)
                        if channel == "rotation":
                            vec = cleanv(rot_to_bb(vals - rest_rot))
                        elif channel == "position":
                            vec = cleanv(to_bb(vals - rest_loc))
                        else:
                            vec = cleanv(scale_to_bb(vals))
                    entry["keyframes"].append({
                        "channel": channel, "data_points": [{"x": vec[0], "y": vec[1], "z": vec[2]}],
                        "uuid": stable_uuid(model, short, bone_name, channel, j), "time": t, "color": -1,
                        "interpolation": INTERP_OUT.get(kps[0].interpolation, "catmullrom")})
                continue
            frames = sorted({round(kp.co[0], 4) for fc in fcs if fc for kp in fc.keyframe_points})
            for j, fr in enumerate(frames):
                vals = []
                interp = "linear"
                for i, fc in enumerate(fcs):
                    kp = None
                    if fc:
                        kp = next((k for k in fc.keyframe_points if abs(k.co[0] - fr) < 1e-3), None)
                    if kp is not None:
                        vals.append(kp.co[1])
                        interp = INTERP_OUT.get(kp.interpolation, "catmullrom")
                    elif fc:
                        vals.append(fc.evaluate(fr))
                    else:
                        vals.append({"location": rest_loc, "rotation_euler": rest_rot}.get(prop, Vector((1, 1, 1)))[i])
                if channel == "rotation":
                    vec = rot_to_bb(Vector(vals) - rest_rot)
                elif channel == "position":
                    vec = to_bb(Vector(vals) - rest_loc)
                else:
                    vec = scale_to_bb(vals)
                vec = cleanv(vec)
                entry["keyframes"].append({"channel": channel, "data_points": [{"x": vec[0], "y": vec[1], "z": vec[2]}],
                                           "uuid": stable_uuid(model, short, bone_name, channel, j), "time": clean(fr / FPS),
                                           "color": -1, "interpolation": interp})
        if entry["keyframes"]:
            animators[stable_uuid(model, "bone", bone_name)] = entry
    animators = dict(sorted(animators.items(), key=lambda kv: kv[1]["name"]))
    events = _export_events(act)
    if events:
        animators["effects"] = {"name": "Effects", "type": "effect", "keyframes": [
            {"channel": "timeline", "data_points": [{"script": name}], "uuid": stable_uuid(model, short, "event", i),
             "time": t, "color": -1, "interpolation": "linear"} for i, (t, name) in enumerate(events)]}
    a = {"uuid": stable_uuid(model, "animation", short), "name": f"animation.{model}.{short}",
         "loop": "loop" if act.get("bb_loop") else "once", "override": False, "length": clean(length), "snapping": 20,
         "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": ""}
    a.update({k: v for k, v in _get_extra(act).items() if k not in ("name", "animators", "length", "loop")})
    a["animators"] = animators
    return a


def _export_events(act):
    """The action's pose markers as (seconds, name); a marker still on the frame its imported event rounded to keeps
    that event's exact time."""
    stored = [tuple(e) for e in json.loads(act.get("bb_events", "[]"))]
    out = []
    for m in act.pose_markers:
        match = next((e for e in stored if e[1] == m.name and int(round(e[0] * FPS)) == m.frame), None)
        if match:
            stored.remove(match)
            out.append((float(match[0]), m.name))
        else:
            out.append((clean(m.frame / FPS), m.name))
    return sorted(out)


# ------------------------------------------------------------------------------------------------- headless use

def roundtrip(model, out=None):
    """Imports a model and exports it again unchanged (the bridge's own test)."""
    import_model(model)
    path = export_model(model, out)
    print(f"blender_bridge: {model} -> {path}")
    return path


def _main(argv):
    if not argv:
        print(__doc__)
        return
    cmd, rest_args = argv[0], argv[1:]
    if cmd == "roundtrip":
        models = rest_args[:1] if rest_args and rest_args[0] != "all" else [
            f[:-len(".bbmodel")] for f in sorted(os.listdir(ART)) if f.endswith(".bbmodel")]
        out_dir = rest_args[1] if len(rest_args) > 1 else None
        for m in models:
            roundtrip(m, os.path.join(out_dir, m + ".bbmodel") if out_dir else None)
            remove_model(m)
    else:
        raise SystemExit(f"unknown command {cmd} (roundtrip <model|all> [out dir])")


if __name__ == "__main__":
    _main(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
