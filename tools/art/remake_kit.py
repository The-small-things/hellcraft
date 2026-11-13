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

    def cube(self, bone, name, frm, size, mat, inflate=0.0, rotation=None, origin=None, detail=None):
        size = tuple(int(round(s)) for s in size)
        assert all(s > 0 for s in size), f"{name}: size {size}"
        self.cubes.append(dict(bone=bone, name=name, frm=tuple(float(v) for v in frm), size=size, mat=mat,
                               inflate=inflate, rotation=tuple(rotation or ZERO), origin=origin, detail=detail))

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
        bb.new_model(self.model, self.tex_width, height, header=header)
        canvas, glow = self.paint(height)
        self.to_image(bpy.data.images[f"{self.model}.png"], canvas)
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
