"""Builds Blockbench models (.bbmodel) from code: bones, box-UV cubes, a texture painted material by material, and
keyframe animations with named events. A file in tools/art (minos.py, ...) describes one model with this; running it
writes versions/26.3/art/<name>.bbmodel, which Blockbench opens (to look at the model and play its animations), and
tools/gen_bosses.py turns into game code.

Coordinates are Blockbench's: pixels (16 to a block), Y up, feet at y = 0, the model faces -Z (north) and +X is its
right hand. Rotations are degrees about the bone's pivot, applied Z, then Y, then X: +X tips the front upward (a
head looks up, a hanging arm swings forward), +Y turns the front toward the model's left, +Z leans the top toward
its left (a hanging right arm swings out to the right).

Box UV: each cube gets its own patch of the texture, laid out as Minecraft unwraps a box (top and bottom on the
first row; right side, front, left side and back on the second). Painters fill each face; nothing needs an image
library, and the output is the same on every run (ids and noise are seeded by name).
"""
import base64
import json
import math
import os
import random
import struct
import uuid
import zlib

ART = os.path.join(os.path.dirname(__file__), "..", "..", "versions", "26.3", "art")
FACES = ("top", "bottom", "right", "front", "left", "back")
NAMESPACE = uuid.UUID("6f2b8c1e-3d4a-4b5c-9e8f-1a2b3c4d5e6f")


def stable_uuid(*parts):
    return str(uuid.uuid5(NAMESPACE, "/".join(str(p) for p in parts)))


# ------------------------------------------------------------------------------------------------- pixels

def png_bytes(width, height, pixels):
    """pixels: a flat list of (r, g, b, a), row by row."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(bytes(pixels[y * width + x]))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.pixels = [(0, 0, 0, 0)] * (width * height)

    def set(self, x, y, colour, alpha=255):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y * self.width + x] = (colour[0], colour[1], colour[2], alpha)

    def get(self, x, y):
        return self.pixels[y * self.width + x]

    def png(self):
        return png_bytes(self.width, self.height, self.pixels)


class Face:
    """One face's patch of the texture, as a painter sees it: (0, 0) is its top left, seen from outside the box.
    On the right side the model's front is at the right edge; on the left side, at the left edge. On top, the front
    is the bottom row."""

    def __init__(self, cube, name, x, y, w, h, canvas, glow):
        self.cube = cube
        self.name = name
        self.x, self.y, self.w, self.h = x, y, w, h
        self.canvas = canvas
        self.glow_canvas = glow
        self.rng = random.Random(f"{cube.name}/{name}")

    def px(self, fx, fy, colour, alpha=255):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.canvas.set(self.x + fx, self.y + fy, colour, alpha)

    def glow(self, fx, fy, colour):
        """An emissive pixel: drawn on the texture and on the glow layer (lit even in the dark)."""
        self.px(fx, fy, colour)
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.glow_canvas.set(self.x + fx, self.y + fy, colour)

    def fill(self, colour_at):
        """colour_at(fx, fy) -> (r, g, b) or None to leave a pixel transparent."""
        for fy in range(self.h):
            for fx in range(self.w):
                c = colour_at(fx, fy)
                if c is not None:
                    self.px(fx, fy, c)

    def rect(self, fx, fy, w, h, colour):
        for y in range(fy, fy + h):
            for x in range(fx, fx + w):
                self.px(x, y, colour)


# ------------------------------------------------------------------------------------------------- materials

class Material:
    """How a material looks: a painter function, called once per face (face, material)."""

    def __init__(self, name, painter, **colours):
        self.name = name
        self.painter = painter
        self.c = {k: hexc(v) if isinstance(v, str) and len(v.lstrip("#")) == 6 and all(ch in "0123456789abcdefABCDEF" for ch in v.lstrip("#"))
                  else v for k, v in colours.items()}

    def paint(self, face):
        self.painter(face, self)
        if self.c.get("edges", True):
            edge_shade(face)


def face_light(face):
    """Tops a little lighter, bottoms darker: the texture's own shading, on top of the game's."""
    return {"top": 1.12, "bottom": 0.72}.get(face.name, 1.0)


def value_noise(face, cell=4):
    """Smooth noise over a face, in [-1, 1]: random values on a coarse grid, blended between (mottling, not static)."""
    gw = face.w // cell + 2
    gh = face.h // cell + 2
    grid = [[face.rng.random() * 2 - 1 for _ in range(gw)] for _ in range(gh)]

    def at(fx, fy):
        gx, gy = fx / cell, fy / cell
        x0, y0 = int(gx), int(gy)
        tx, ty = gx - x0, gy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        top = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
        bottom = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
        return top * (1 - ty) + bottom * ty
    return at


def noisy(face, base, amount=0.08, gradient=0.18):
    """A base colour with a vertical gradient (darker toward the bottom of side faces), soft mottling and a little
    fine grain."""
    light = face_light(face)
    coarse = value_noise(face, 4)

    def at(fx, fy):
        g = 1.0
        if face.name not in ("top", "bottom") and face.h > 1:
            g = 1.0 + gradient * (0.5 - fy / (face.h - 1))
        n = 1.0 + coarse(fx, fy) * amount * 1.4 + (face.rng.random() - 0.5) * amount * 0.5
        return shade(base, light * g * n)

    face.fill(at)


def edge_shade(face, dark=0.8, lit=1.1):
    """Defines the cube's shape: its faces' rims a little darker (the corners darker still), and the top rim of a side
    lit, as light catches an edge. Applied to every face after its material."""
    if face.w < 3 or face.h < 3:
        return
    for fy in range(face.h):
        for fx in range(face.w):
            rim = fx == 0 or fy == face.h - 1 or fx == face.w - 1 or (fy == 0 and face.name in ("top", "bottom"))
            top = fy == 0 and face.name not in ("top", "bottom")
            if not (rim or top):
                continue
            c = face.canvas.get(face.x + fx, face.y + fy)
            if c is None or (len(c) > 3 and c[3] == 0):
                continue
            f = lit if top and not (fx == 0 or fx == face.w - 1) else dark
            if (fx in (0, face.w - 1)) and (fy in (0, face.h - 1)):
                f = dark * 0.92
            face.canvas.set(face.x + fx, face.y + fy, shade(tuple(c[:3]), f), c[3] if len(c) > 3 else 255)


def paint_skin(face, m):
    """Skin: a smooth tone with soft mottling, a little darker in the creases (the face's lower part), and a few pores
    and blemishes, never the streaks that made it look like stone."""
    noisy(face, m.c["base"], 0.07, 0.2)
    lf = face_light(face)
    blotch = value_noise(face, 6)
    for fy in range(face.h):
        for fx in range(face.w):
            if blotch(fx, fy) > 0.55:
                c = face.canvas.get(face.x + fx, face.y + fy)
                if c is not None:
                    face.px(fx, fy, mix(tuple(c[:3]), shade(m.c["dark"], lf), 0.25))
    for _ in range(max(1, face.w * face.h // 90)):
        x, y = face.rng.randrange(face.w), face.rng.randrange(face.h)
        face.px(x, y, mix(shade(m.c["base"], lf), shade(m.c["dark"], lf), 0.55))


def paint_robe(face, m):
    base, fold = m.c["base"], m.c["fold"]
    light = face_light(face)

    def at(fx, fy):
        # vertical folds on the sides, a little speckle
        f = 1.0
        if face.name not in ("top", "bottom"):
            f = 0.86 if (fx + (fy // 6)) % 5 == 0 else 1.0
        n = 1.0 + (face.rng.random() - 0.5) * 0.08
        c = mix(base, fold, 0.0 if f == 1.0 else 0.6)
        return shade(c, light * n * (1.0 + 0.12 * (0.5 - fy / max(1, face.h - 1)) if face.name not in ("top", "bottom") else light * n))

    face.fill(at)
    # gold trim along the lower edge of the sides, and along the front edges
    if face.name not in ("top", "bottom") and "trim" in m.c:
        for fx in range(face.w):
            face.px(fx, face.h - 1, shade(m.c["trim"], 0.9))
            face.px(fx, face.h - 2, shade(m.c["trim"], 1.05 if fx % 3 else 0.8))
    if face.name == "front" and "trim" in m.c and m.c.get("opening"):
        mid = face.w // 2
        for fy in range(face.h):
            face.px(mid - 1, fy, m.c["trim"])
            face.px(mid, fy, shade(m.c["trim"], 0.8))


def paint_scales(face, m):
    base, edge, belly = m.c["base"], m.c["edge"], m.c["belly"]
    light = face_light(face)
    if face.name == m.c.get("belly_face", "bottom"):
        # the belly: pale plates across the body
        def plates(fx, fy):
            c = belly if fy % 4 else shade(belly, 0.75)
            return shade(c, light * (1.0 + (face.rng.random() - 0.5) * 0.06))
        face.fill(plates)
        return

    cells = {}

    def at(fx, fy):
        # overlapping scales: rows of 4 x 3, every other row shifted by 2, each a little lighter or darker
        row = fy // 3
        col = (fx + (2 if row % 2 else 0)) // 4
        sx = (fx + (2 if row % 2 else 0)) % 4
        sy = fy % 3
        tone = cells.setdefault((row, col), 0.84 + face.rng.random() * 0.3)
        c = base
        if sy == 0 and sx in (1, 2):
            c = edge
        elif sy == 2 or sx == 0:
            c = shade(base, 0.7)
        return shade(c, light * tone)

    face.fill(at)


def paint_hair(face, m):
    """Fur and hair: a mid tone with soft mottling, covered in short tufts (2-4 pixels, light at the root side, dark at
    the tip) that overlap row on row, so it reads as fur, not bark."""
    light, dark = m.c["light"], m.c["dark"]
    lf = face_light(face)
    mid = mix(dark, light, 0.5)
    coarse = value_noise(face, 5)

    def at(fx, fy):
        g = 1.0 - 0.18 * fy / max(1, face.h) if face.name not in ("top", "bottom") else 1.0
        return shade(mix(dark, mid, 0.6 + 0.4 * coarse(fx, fy)), lf * g)

    face.fill(at)
    for _ in range(max(2, face.w * face.h // 5)):
        x, y = face.rng.randrange(face.w), face.rng.randrange(face.h)
        length = face.rng.randint(2, 4)
        slant = face.rng.choice((-1, 0, 0, 1)) if face.name not in ("top", "bottom") else face.rng.choice((-1, 1))
        tone = face.rng.choice((0.75, 0.9, 1.0))
        for i in range(length):
            t = tone * (1.0 - i / (length + 1))
            face.px(x + (slant * i) // 2, y + i, shade(mix(dark, light, t), lf * (1.0 - 0.15 * (y + i) / max(1, face.h))))


def paint_metal(face, m):
    """Metal: lit along its top and left rims and dark along the others, with a soft sheen band across it and a little
    tarnish, so gold and iron read as polished, not speckled."""
    base, high, low = m.c["base"], m.c["high"], m.c["low"]
    lf = face_light(face)
    tarnish = value_noise(face, 5)

    def at(fx, fy):
        if fy == 0 or fx == 0:
            c = high
        elif fy == face.h - 1 or fx == face.w - 1:
            c = low
        else:
            # a sheen band running diagonally across the face
            band = abs(((fx + fy * 0.7) / max(4, face.w + face.h) * 3.0) % 1.0 - 0.5)
            c = mix(base, high, max(0.0, 0.45 - band) * 1.6)
            c = mix(c, low, max(0.0, tarnish(fx, fy)) * 0.3)
        return shade(c, lf)

    face.fill(at)


def paint_horn(face, m):
    """Pale at the root (the bottom of the face) to dark at the tip (the top), with growth rings."""
    root, tip = m.c["root"], m.c["tip"]
    lf = face_light(face)

    def at(fx, fy):
        t = 1.0 - fy / max(1, face.h - 1) if face.name not in ("top", "bottom") else (1.0 if face.name == "top" else 0.0)
        c = mix(root, tip, t)
        if face.name not in ("top", "bottom") and fy % 3 == 0:
            c = shade(c, 0.85)
        return shade(c, lf)

    face.fill(at)


def paint_flat(face, m):
    noisy(face, m.c["base"], 0.05, 0.1)


PAINTERS = {"skin": paint_skin, "robe": paint_robe, "scales": paint_scales, "hair": paint_hair, "metal": paint_metal,
            "horn": paint_horn, "flat": paint_flat}


def material(name, kind, **colours):
    return Material(name, PAINTERS[kind], **colours)


# ------------------------------------------------------------------------------------------------- the model

class Cube:
    def __init__(self, bone, name, frm, size, mat, inflate=0.0, rotation=None, origin=None, mirror=False, detail=None):
        assert all(float(s).is_integer() and s > 0 for s in size), f"{name}: box UV needs whole sizes, got {size}"
        self.bone = bone
        self.name = name
        self.frm = tuple(float(v) for v in frm)
        self.size = tuple(int(s) for s in size)
        self.material = mat
        self.inflate = inflate
        self.rotation = tuple(rotation) if rotation else (0.0, 0.0, 0.0)
        self.origin = tuple(origin) if origin else bone.pivot
        self.mirror = mirror
        self.detail = detail
        self.uv = None

    @property
    def to(self):
        return tuple(self.frm[i] + self.size[i] for i in range(3))

    def uv_size(self):
        w, h, d = self.size
        return 2 * (d + w), d + h

    def face_rects(self):
        u, v = self.uv
        w, h, d = self.size
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


class Bone:
    def __init__(self, model, name, pivot, rotation=(0, 0, 0), parent=None):
        assert name not in model.bone_names, f"bone {name} twice"
        model.bone_names[name] = self
        self.model = model
        self.name = name
        self.pivot = tuple(float(v) for v in pivot)
        self.rotation = tuple(float(v) for v in rotation)
        self.parent = parent
        self.cubes = []
        self.children = []

    def bone(self, name, pivot, rotation=(0, 0, 0)):
        child = Bone(self.model, name, pivot, rotation, self)
        self.children.append(child)
        return child

    def cube(self, name, frm, size, mat, **kw):
        c = Cube(self, name, frm, size, mat, **kw)
        self.cubes.append(c)
        self.model.cubes.append(c)
        return c


class Animation:
    def __init__(self, name, length, loop):
        self.name = name
        self.length = length
        self.loop = loop
        self.channels = {}  # (bone, channel) -> [(time, (x, y, z), interpolation)]
        self.events = []  # (time, name)

    def _key(self, bone, channel, keys, interp):
        lst = self.channels.setdefault((bone, channel), [])
        for t, v in keys:
            assert 0 <= t <= self.length + 1e-6, f"{self.name}: key at {t} past the end"
            lst.append((float(t), tuple(float(x) for x in v), interp))
        lst.sort(key=lambda k: k[0])
        return self

    def rot(self, bone, keys, interp="catmullrom"):
        return self._key(bone, "rotation", keys, interp)

    def pos(self, bone, keys, interp="catmullrom"):
        return self._key(bone, "position", keys, interp)

    def scale(self, bone, keys, interp="catmullrom"):
        return self._key(bone, "scale", keys, interp)

    def event(self, time, name):
        """A named moment the server times its blows by (Blockbench shows it as an effect keyframe)."""
        self.events.append((float(time), name))
        return self


class Model:
    def __init__(self, name, texture_width=256):
        self.name = name
        self.texture_width = texture_width
        self.roots = []
        self.cubes = []
        self.bone_names = {}
        self.animations = []
        self.texture_height = 0

    def bone(self, name, pivot, rotation=(0, 0, 0)):
        b = Bone(self, name, pivot, rotation)
        self.roots.append(b)
        return b

    def animation(self, name, length, loop=False):
        a = Animation(name, length, loop)
        self.animations.append(a)
        return a

    # --------------------------------------------------------------------------------------------- texture

    def pack(self):
        """Shelf-packs every cube's box UV patch, tallest first, into the texture's width."""
        order = sorted(self.cubes, key=lambda c: (-c.uv_size()[1], -c.uv_size()[0], c.name))
        x = y = shelf = 0
        for c in order:
            w, h = c.uv_size()
            assert w <= self.texture_width, f"{c.name} is wider than the texture"
            if x + w > self.texture_width:
                x, y, shelf = 0, y + shelf, 0
            c.uv = (x, y)
            x += w
            shelf = max(shelf, h)
        used = y + shelf
        height = 16
        while height < used:
            height *= 2
        self.texture_height = height

    def paint(self):
        canvas = Canvas(self.texture_width, self.texture_height)
        glow = Canvas(self.texture_width, self.texture_height)
        for c in self.cubes:
            faces = {}
            for name, (x, y, w, h) in c.face_rects().items():
                faces[name] = Face(c, name, x, y, w, h, canvas, glow)
                c.material.paint(faces[name])
            if c.detail:
                c.detail(faces)
        return canvas, glow

    # --------------------------------------------------------------------------------------------- output

    def _element(self, c, index):
        u, v = c.uv
        rects = c.face_rects()
        names = {"north": "front", "east": "right", "south": "back", "west": "left", "up": "top", "down": "bottom"}
        faces = {}
        for bb, ours in names.items():
            x, y, w, h = rects[ours]
            faces[bb] = {"uv": [x, y, x + w, y + h], "texture": 0}
        e = {"name": c.name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
             "allow_mirror_modeling": True, "from": list(c.frm), "to": list(c.to), "autouv": 0, "color": index % 8,
             "inflate": c.inflate, "origin": list(c.origin), "uv_offset": [u, v], "faces": faces, "type": "cube",
             "uuid": stable_uuid(self.name, "cube", c.name)}
        if any(c.rotation):
            e["rotation"] = list(c.rotation)
        if c.mirror:
            e["mirror_uv"] = True
        return e

    def _group(self, b):
        g = {"name": b.name, "origin": list(b.pivot), "color": 0, "uuid": stable_uuid(self.name, "bone", b.name), "export": True,
             "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
             "children": [stable_uuid(self.name, "cube", c.name) for c in b.cubes] + [self._group(ch) for ch in b.children]}
        if any(b.rotation):
            g["rotation"] = list(b.rotation)
        return g

    def _animation(self, a):
        animators = {}
        for (bone, channel), keys in sorted(a.channels.items()):
            assert bone in self.bone_names, f"{a.name}: no bone {bone}"
            buuid = stable_uuid(self.name, "bone", bone)
            entry = animators.setdefault(buuid, {"name": bone, "type": "bone", "keyframes": []})
            for i, (t, v, interp) in enumerate(keys):
                entry["keyframes"].append({"channel": channel, "data_points": [{"x": v[0], "y": v[1], "z": v[2]}],
                                           "uuid": stable_uuid(self.name, a.name, bone, channel, i), "time": t, "color": -1,
                                           "interpolation": interp})
        if a.events:
            animators["effects"] = {"name": "Effects", "type": "effect", "keyframes": [
                {"channel": "timeline", "data_points": [{"script": name}], "uuid": stable_uuid(self.name, a.name, "event", i),
                 "time": t, "color": -1, "interpolation": "linear"} for i, (t, name) in enumerate(sorted(a.events))]}
        return {"uuid": stable_uuid(self.name, "animation", a.name), "name": f"animation.{self.name}.{a.name}",
                "loop": "loop" if a.loop else "once", "override": False, "length": a.length, "snapping": 20, "selected": False,
                "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}

    def _texture(self, name, data, index):
        return {"path": "", "name": name, "folder": "", "namespace": "", "id": str(index), "width": self.texture_width,
                "height": self.texture_height, "uv_width": self.texture_width, "uv_height": self.texture_height, "particle": index == 0,
                "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
                "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                "uuid": stable_uuid(self.name, "texture", name), "source": "data:image/png;base64," + base64.b64encode(data).decode()}

    def write(self):
        self.pack()
        canvas, glow = self.paint()
        textures = [self._texture(self.name + ".png", canvas.png(), 0)]
        if any(p[3] for p in glow.pixels):
            textures.append(self._texture(self.name + "_glow.png", glow.png(), 1))
        top = max(c.to[1] for c in self.cubes)
        wide = max(max(abs(c.frm[0]), abs(c.to[0]), abs(c.frm[2]), abs(c.to[2])) for c in self.cubes)
        doc = {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
               "name": self.name, "model_identifier": "", "modded_entity_entity_class": "", "modded_entity_version": "1.17",
               "modded_entity_flip_y": True, "visible_box": [math.ceil(wide * 2 / 16), math.ceil(top / 16), 0],
               "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
               "resolution": {"width": self.texture_width, "height": self.texture_height},
               "elements": [self._element(c, i) for i, c in enumerate(self.cubes)],
               "outliner": [self._group(b) for b in self.roots],
               "textures": textures,
               "animations": [self._animation(a) for a in self.animations]}
        os.makedirs(ART, exist_ok=True)
        path = os.path.join(ART, self.name + ".bbmodel")
        with open(path, "w") as f:
            json.dump(doc, f, indent=1)
            f.write("\n")
        print(f"Wrote {os.path.relpath(path)}: {len(self.cubes)} cubes, {len(self.bone_names)} bones, "
              f"{len(self.animations)} animations, texture {self.texture_width}x{self.texture_height}")
        return path
