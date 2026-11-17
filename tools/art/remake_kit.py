"""Helpers for remaking a creature in Blender (run inside Blender 5.x, with tools/art on sys.path), on top of
tools/art/blender_bridge.py: lay out bones and cubes in Blockbench terms, pack their box UV, paint them with
tools/art/bbmodel.py's painters, build the model in the scene, and key its animations with their events.

    import remake_kit as kit
    b = kit.Builder("cerberus", tex_width=512)
    b.bone(None, "body", (0, 56, 0))
    b.cube("body", "chest", (-26, 50, -60), (52, 42, 46), FUR, detail=mange)
    b.seg("body", "tail_1", p0, p1, 14, 14, SKIN)       # a cube laid along p0 -> p1
    b.build(header_from="cerberus")                      # replaces the model in the scene
    a = kit.Anim("cerberus", "bite_mid", 1.3, events=[(0.35, "flash"), (0.75, "bite")])
    a.rot("neck_mid", [(0, (0, 0, 0)), (0.75, (-20, 0, 0)), (1.3, (0, 0, 0))])

Coordinates are Blockbench's model pixels (Y up, feet at 0, front -Z, +X the creature's right); rotations are degrees
applied X, then Y, then Z: +X tips a forward-pointing part up and swings a hanging one forward, +Y turns the front
toward the creature's left, +Z leans the top toward its left (and swings a hanging right-side part out to the right).
Animation keys are offsets from rest, as Minecraft adds them. Remakes are built at twice the detail (two model pixels
to a game pixel) and drawn at half size by their renderer."""
import json
import math
import os

import bpy
import numpy as np

import blender_bridge as bb
from bbmodel import Canvas, Face

ZERO = (0, 0, 0)


def mirror(v):
    """A rotation for the other side of the body (turns about Y and Z flip)."""
    return (v[0], -v[1], -v[2])


def mirror_pos(v):
    return (-v[0], v[1], v[2])


class _Stub:
    """What a painter's Face needs of its cube: a name (seeds its noise) and where it starts."""

    def __init__(self, c):
        self.name = c["name"]
        self.frm = c["frm"]


class Builder:
    def __init__(self, model, tex_width=512):
        self.model = model
        self.tex_width = tex_width
        self.cubes = []
        self.bones = []

    # ---------------------------------------------------------------------------------------------- layout

    def bone(self, parent, name, pivot, rotation=ZERO):
        self.bones.append((parent, name, tuple(pivot), tuple(rotation)))
        return name

    def cube(self, bone, name, frm, size, mat, inflate=0.0, rotation=None, origin=None, detail=None, mirror=False):
        size = tuple(int(round(s)) for s in size)
        assert all(s > 0 for s in size), f"{name}: size {size}"
        self.cubes.append(dict(bone=bone, name=name, frm=tuple(float(v) for v in frm), size=size, mat=mat,
                               inflate=inflate, rotation=tuple(rotation or ZERO), origin=origin, detail=detail,
                               mirror=mirror))

    def seg(self, bone, name, p0, p1, w, h, mat, extra=0.0, **kw):
        """A cube laid along p0 -> p1 (its length on its own Z), w wide and h thick, turned about its centre; extra
        lengthens it so neighbouring segments overlap at their joints."""
        d = [p1[i] - p0[i] for i in range(3)]
        length = math.sqrt(sum(v * v for v in d))
        size = max(1, math.ceil(length + extra - 1e-6))
        c = [round((p0[i] + p1[i]) / 2, 3) for i in range(3)]
        pitch = -math.degrees(math.asin(max(-1.0, min(1.0, d[1] / length))))
        yaw = math.degrees(math.atan2(d[0], d[2]))
        self.cube(bone, name, (c[0] - w / 2, c[1] - h / 2, c[2] - size / 2), (w, h, size), mat,
                  rotation=(round(pitch, 3), round(yaw, 3), 0), origin=tuple(c), **kw)

    def hang(self, bone, name, pivot, w, length, d, mat, top=0.0, **kw):
        """A piece hanging straight down from a pivot (its top `top` above it, to overlap the joint)."""
        x, y, z = pivot
        self.cube(bone, name, (x - w / 2, y - length + top, z - d / 2), (w, length, d), mat, **kw)

    def reach(self, bone, name, pivot, w, length, h, mat, back=0.0, **kw):
        """A piece reaching forward (-Z) from a pivot, w wide and h tall (starting `back` behind it)."""
        x, y, z = pivot
        self.cube(bone, name, (x - w / 2, y - h / 2, z - length + back), (w, h, length), mat, **kw)

    # ---------------------------------------------------------------------------------------------- texture

    @staticmethod
    def rects(u, v, w, h, d):
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

    def pack(self):
        """Shelf-packs every cube's box UV patch, tallest first; returns the texture's height."""
        def uv_size(c):
            w, h, d = c["size"]
            return 2 * (d + w), d + h
        x = y = shelf = 0
        for c in sorted(self.cubes, key=lambda c: (-uv_size(c)[1], -uv_size(c)[0], c["name"])):
            w, h = uv_size(c)
            assert w <= self.tex_width, f"{c['name']} is wider than the texture"
            if x + w > self.tex_width:
                x, y, shelf = 0, y + shelf, 0
            c["uv"] = (x, y)
            x += w
            shelf = max(shelf, h)
        height = 16
        while height < y + shelf:
            height *= 2
        return height

    def paint(self, height):
        canvas, glow = Canvas(self.tex_width, height), Canvas(self.tex_width, height)
        for c in self.cubes:
            w, h, d = c["size"]
            stub = _Stub(c)
            faces = {n: Face(stub, n, *r, canvas, glow) for n, r in self.rects(*c["uv"], w, h, d).items()}
            for f in faces.values():
                c["mat"].paint(f)
            if c["detail"]:
                c["detail"](faces)
        return canvas, glow

    @staticmethod
    def to_image(img, cv):
        if tuple(img.size) != (cv.width, cv.height):
            img.scale(cv.width, cv.height)
        arr = np.array(cv.pixels, dtype=np.float32).reshape(cv.height, cv.width, 4) / 255.0
        img.pixels.foreach_set(np.flipud(arr).ravel())
        img.update()

    # ---------------------------------------------------------------------------------------------- build

    def build(self, header_from=None):
        """Replaces the model in the scene with this one (its animations go too: key them afresh with Anim).
        header_from: the name of a model in versions/26.3/art whose other .bbmodel fields to keep."""
        header = None
        if header_from:
            with open(f"{bb.ART}/{header_from}.bbmodel") as fh:
                header = {k: v for k, v in json.load(fh).items()
                          if k not in ("elements", "outliner", "animations", "textures")}
        height = self.pack()
        canvas, glow = self.paint(height)
        glows = any(p[3] for p in glow.pixels)  # a glow texture only if something glows
        bb.new_model(self.model, self.tex_width, height, header=header, glow=glows)
        self.to_image(bpy.data.images[f"{self.model}.png"], canvas)
        if glows:
            self.to_image(bpy.data.images[f"{self.model}_glow.png"], glow)
        for parent, name, pivot, rot in self.bones:
            bb.add_bone(self.model, parent, name, pivot, rot)
        for c in self.cubes:
            bb.add_cube(self.model, c["bone"], c["name"], c["frm"], c["size"], c["uv"], rotation=c["rotation"],
                        origin=c["origin"], inflate=c["inflate"])
        return {"cubes": len(self.cubes), "bones": len(self.bones), "texture": [self.tex_width, height]}


class Anim:
    """One animation, keyed afresh (an older one of the same name is replaced). events: [(seconds, name)], the
    moments the server times blows by; keep them on the ticks the game's <Model>Anim expects."""

    def __init__(self, model, name, length, loop=False, events=()):
        for act in [a for a in bpy.data.actions if a.get("bb_model") == model and a.get("bb_anim") == name]:
            bpy.data.actions.remove(act)
        self.model, self.name, self.length = model, name, length
        self.act = bb.new_animation(model, name, length, loop)
        evs = sorted((float(t), n) for t, n in events)
        self.act["bb_events"] = json.dumps(evs)
        for t, n in evs:
            self.act.pose_markers.new(n).frame = int(round(t * bb.FPS))

    def rot(self, bone, keys, interp="catmullrom"):
        bb.key(self.model, self.name, bone, "rotation", keys, interp)
        return self

    def pos(self, bone, keys, interp="catmullrom"):
        bb.key(self.model, self.name, bone, "position", keys, interp)
        return self

    def pair(self, right, left, keys, channel="rot"):
        """The same keys on a right-side bone and, mirrored, on its left-side twin."""
        f = mirror if channel == "rot" else mirror_pos
        put = self.rot if channel == "rot" else self.pos
        put(right, keys)
        put(left, [(t, f(v)) for t, v in keys])
        return self

    def wave(self, bone, amp, phase=0.0, axis=1, base=ZERO, cycles=1, points=8):
        """A smooth loop over the whole animation: base + amp * sin, `cycles` times round, shifted by phase."""
        keys = []
        n = points * cycles
        for i in range(n + 1):
            v = list(base)
            v[axis] += round(amp * math.sin(2 * math.pi * i / points - phase), 3)
            keys.append((round(self.length * i / n, 4), tuple(v)))
        return self.rot(bone, keys)


# ------------------------------------------------------------------------------------------------- remakes, headless

def fresh_scene():
    """An empty scene (for running under `python3` with the bpy module, or `blender -b -P`)."""
    bpy.ops.wm.read_factory_settings(use_empty=True)


def ported(old, k=2):
    """A detail that carries an old cube's hand-painted detail over to the same cube at k times the size: the old
    material and detail are painted at the old size off to one side, and every pixel the detail changed is brought
    across to the k x k pixels it covers: a plain darkening or lightening as the same tone over the new, finer grain;
    a new colour, a glow or a hole as itself."""
    def detail(faces):
        w, h, d = old.size
        cv, gl = Canvas(2 * (w + d), h + d), Canvas(2 * (w + d), h + d)
        small = {n: Face(old, n, *r, cv, gl) for n, r in Builder.rects(0, 0, w, h, d).items()}
        for f in small.values():
            old.material.paint(f)
        before = list(cv.pixels)
        old.detail(small)
        for n, sf in small.items():
            f = faces[n]
            for y in range(sf.h):
                for x in range(sf.w):
                    i = (sf.y + y) * cv.width + sf.x + x
                    was, now, glow = before[i], cv.pixels[i], gl.pixels[i]
                    if was == now and not glow[3]:
                        continue
                    for yy in range(y * k, y * k + k):
                        for xx in range(x * k, x * k + k):
                            if not (0 <= xx < f.w and 0 <= yy < f.h):
                                continue
                            if glow[3]:
                                f.glow(xx, yy, glow[:3])
                            elif not now[3]:
                                f.px(xx, yy, (0, 0, 0), 0)
                            else:
                                lw, ln = sum(was[:3]) + 1, sum(now[:3]) + 1
                                r = ln / lw
                                plain = was[3] and all(abs(now[j] - was[j] * r) < 14 for j in range(3))
                                cur = f.canvas.get(f.x + xx, f.y + yy)
                                if plain and cur[3]:
                                    f.px(xx, yy, tuple(max(0, min(255, int(round(v * r)))) for v in cur[:3]))
                                else:
                                    j = 0.95 + 0.1 * f.rng.random()
                                    f.px(xx, yy, tuple(max(0, min(255, int(round(v * j)))) for v in now[:3]))
    return detail


class Remake(Builder):
    """Remakes a model's body and keeps its skeleton and animations: reads versions/26.3/art/<model>.bbmodel, brings
    it to `detail` model pixels per game pixel (pivots and position keys scale; the file remembers its detail in
    "hellcraft_detail", so running a remake twice changes nothing), throws its cubes away and takes this builder's.
    Bones the file has are there already (`pivot(name)`); `bone(...)` adds the ones it lacks.

        b = kit.Remake("minotaur")                      # tools/art/remake/minotaur.py
        b.cube("torso", "chest", ...)
        b.finish()                                      # writes the .bbmodel; then python3 tools/gen_bosses.py
    """

    def __init__(self, model, tex_width=512, detail=2):
        super().__init__(model, tex_width)
        self.detail = detail
        fresh_scene()
        bb.import_model(model)
        root = bb.model_root(model)
        header = json.loads(root["bb_header"])
        was = header.get("hellcraft_detail", 1)
        if was != detail:
            bb.scale_model(model, detail / was)
            header["hellcraft_detail"] = detail
            root["bb_header"] = json.dumps(header)
        # the old body, at the new detail, for `keep`: name -> its bone, box, turn and inflate
        self.old = {}
        with open(f"{bb.ART}/{model}.bbmodel") as fh:
            doc = json.load(fh)
        k = detail / was
        els = {e["uuid"]: e for e in doc["elements"]}

        def walk(g):
            for ch in g["children"]:
                if isinstance(ch, dict):
                    walk(ch)
                else:
                    e = els[ch]
                    self.old.setdefault(e["name"], dict(
                        bone=g["name"], frm=tuple(v * k for v in e["from"]),
                        size=tuple(round((e["to"][i] - e["from"][i]) * k) for i in range(3)),
                        rotation=tuple(e.get("rotation", ZERO)), origin=tuple(v * k for v in e["origin"]) if e.get("rotation") else None,
                        inflate=(e.get("inflate") or 0.0) * k))
        for g in doc["outliner"]:
            walk(g)
        for o in list(bb.model_objects(model)):
            if o.get("bb_kind") == "cube":
                data = o.data
                bpy.data.objects.remove(o)
                if data is not None and data.users == 0:
                    bpy.data.meshes.remove(data)

    def pivot(self, name):
        return tuple(bb.bone_pivot(self.model, name))

    def has(self, name):
        return name in bb.model_bones(self.model)

    def bone(self, parent, name, pivot, rotation=ZERO):
        """Adds a bone at once (so its children can ask for its pivot); one the model has already must sit where
        the script says."""
        if self.has(name):
            old = self.pivot(name)
            assert all(abs(old[i] - pivot[i]) < 0.02 for i in range(3)), f"{name} is at {old}, not {pivot}"
            return name
        bb.add_bone(self.model, parent, name, pivot, rotation)
        return name

    def keep(self, name, mat, detail=None, grow=ZERO, shift=ZERO, inflate=0.0, bone=None, rename=None):
        """Takes a cube of the old body as it was (at the new detail), in a new material and detail; grow widens it
        by that much on each side of each axis, shift moves it."""
        o = self.old[name]
        frm = tuple(o["frm"][i] - grow[i] + shift[i] for i in range(3))
        size = tuple(o["size"][i] + 2 * grow[i] for i in range(3))
        origin = tuple(o["origin"][i] + shift[i] for i in range(3)) if o["origin"] else None
        self.cube(bone or o["bone"], rename or name, frm, size, mat, inflate=inflate, rotation=o["rotation"], origin=origin,
                  detail=detail)

    def port(self, script, skip=(), mats=None, details=None, also=None):
        """Brings over the body an old script built (tools/art/legacy/<script>.py), every cube at the new detail in
        its old material, so the material's grain is finer, with the old script's hand-painted details carried over
        (`ported`). skip: names (or prefixes ending in *) to leave out, for parts this remake rebuilds; mats and
        details: name -> a new material or detail (also may be a function of the name; a detail of None keeps the old one carried over; give a function
        to repaint the part at the new detail); also: name -> a detail painted after the carried-over one."""
        import importlib
        import sys
        import bbmodel
        sys.path.insert(0, os.path.join(bb.HERE, "legacy"))
        mod = importlib.import_module(script)
        self.legacy = mod
        k = self.detail
        mats, details, also = mats or {}, details or {}, also or {}

        def skipped(name):
            return any(name == s or (s.endswith("*") and name.startswith(s[:-1])) for s in skip)
        self.ported = {}
        for c in mod.build().cubes:
            self.ported[c.name] = c
            if skipped(c.name):
                continue
            detail = details[c.name] if c.name in details else (ported(c, k) if c.detail else None)
            extra = also(c.name) if callable(also) else also.get(c.name)
            if extra:
                first, second = detail, extra
                detail = (lambda fs, first=first, second=second: ((first(fs) if first else None), second(fs)))
            turned = any(c.rotation)
            self.cube(c.bone.name, c.name, tuple(v * k for v in c.frm), tuple(s * k for s in c.size),
                      mats.get(c.name, c.material), rotation=c.rotation if turned else None,
                      origin=tuple(v * k for v in c.origin) if turned else None, detail=detail, mirror=c.mirror)

    def stick(self, bone, name, a, b, t, mat, depth=None, **kw):
        """A cube t wide (depth deep) standing on point a and reaching to point b, turned about a (the old scripts'
        stick): a fold, a brow, a serpent's segment."""
        d = [b[i] - a[i] for i in range(3)]
        length = math.sqrt(sum(v * v for v in d)) or 1.0
        rx = math.degrees(math.asin(max(-1.0, min(1.0, d[2] / length))))
        rz = math.degrees(math.atan2(-d[0], d[1]))
        depth = depth or t
        self.cube(bone, name, (a[0] - t / 2, a[1], a[2] - depth / 2), (t, max(1, round(length)), depth), mat,
                  rotation=(round(rx, 2), 0, round(rz, 2)), origin=tuple(a), **kw)

    def finish(self):
        model = self.model
        height = self.pack()
        canvas, glow = self.paint(height)
        glows = any(p[3] for p in glow.pixels)
        for im in [im for im in bpy.data.images if im.get("bb_model") == model]:
            bpy.data.images.remove(im)
        images = []
        for i, name in enumerate([f"{model}.png"] + ([f"{model}_glow.png"] if glows else [])):
            img = bpy.data.images.new(name, self.tex_width, height, alpha=True)
            img.alpha_mode = "STRAIGHT"
            img["bb_index"] = i
            img["bb_model"] = model
            self.to_image(img, canvas if i == 0 else glow)
            img.pack()
            images.append(img)
        bb.make_material(model, images[0], images[1] if glows else None)
        for c in self.cubes:
            bb.add_cube(model, c["bone"], c["name"], c["frm"], c["size"], c["uv"], rotation=c["rotation"],
                        origin=c["origin"], inflate=c["inflate"], mirror=c["mirror"])
        path = bb.export_model(model)
        print(f"{model}: {len(self.cubes)} cubes, {len(bb.model_bones(model))} bones, texture {self.tex_width}x{height} -> {path}")
