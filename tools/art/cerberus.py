#!/usr/bin/env python3
"""Cerberus, the Great Worm (Inferno VI): "a beast cruel and uncouth, with three gullets doglike barking over the
people that are there submerged." A huge three-headed hound gone to fat in the mire: a sagging belly, a greasy black
beard under each jaw, red eyes, clawed paws, and a tail like a worm. Each head is on its own neck with its own jaw,
so each can bite on its own (the fight names which).

Run from the repo root:  python3 tools/art/cerberus.py && python3 tools/gen_bosses.py

The animations' events are what the server times Cerberus's fight by (see CerberusFight): "flash" opens the window
for a shield block and "bite" is when a head's teeth close; "maw1".."maw3" are the three bites of Three Maws, "spew"
the filth, "howl" the howl, "roar" and "slam" his fury at half health.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

HIDE = material("hide", "hair", light="5c4a36", dark="2a2018")
BELLY = material("belly", "hair", light="6a5840", dark="34281c")
MUD = material("mud", "hair", light="3a3020", dark="1e180e")
BEARD = material("beard", "hair", light="2a2622", dark="0e0c0a")
RUFF = material("ruff", "hair", light="4a3c2c", dark="1a140e")
CLAW = material("claw", "horn", root="4a4038", tip="0e0c0a")
SKULL = material("skull", "hair", light="7a6448", dark="3a2c1e")
NOSE = material("nose", "skin", base="1e1816", dark="0a0808")
LIP = material("lip", "skin", base="2a1e1a", dark="120c0a")
TONGUE = material("tongue", "skin", base="8a3a34", dark="5a1e1a")
FANG = material("fang", "horn", root="e6dcc0", tip="a89a78")

EYE = hexc("ff2a1a")
EYE_HOT = hexc("ffc8a0")
MOUTH = hexc("3a0a08")
TOOTH = hexc("e6dcc0")
GUM = hexc("5a1a16")


# ----------------------------------------------------------------------------------------------- shading helpers

def tone(f, x, y, k):
    """Darkens (k < 1) or lights (k > 1) one pixel of a face, as painted so far."""
    if 0 <= x < f.w and 0 <= y < f.h:
        c = f.canvas.get(f.x + x, f.y + y)
        if c[3]:
            f.px(x, y, shade(c[:3], k), c[3])


def tone_all(f, k):
    for y in range(f.h):
        for x in range(f.w):
            tone(f, x, y, k)


def tuck(faces, rows=2, k=0.7, lit=1.08, names=("front", "back", "left", "right")):
    """Shadow where a part tucks under (its lowest rows darkest) and light along its top: sells a rounded form."""
    for name in names:
        f = faces[name]
        for i in range(min(rows, f.h)):
            for x in range(f.w):
                tone(f, x, f.h - 1 - i, k + (1 - k) * i / rows)
        for x in range(1, f.w - 1):
            tone(f, x, 0, lit)
    tone_all(faces["bottom"], 0.75)


def muddy(faces, rows=5):
    """Mire caked up the legs from the bottom: solid at the paw, in splashes higher up."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(max(0, f.h - rows), f.h):
            depth = (y - (f.h - rows)) / max(1, rows)
            for x in range(f.w):
                if f.rng.random() < 0.25 + 0.7 * depth:
                    f.px(x, y, MUD.c["light"] if f.rng.random() < 0.4 else MUD.c["dark"])


# ----------------------------------------------------------------------------------------------- details

def skull_detail(faces):
    """Red eyes set deep under the brow, either side of the muzzle; the forehead's furrow; shadow under the jaw line."""
    f = faces["front"]
    for cx in (1, f.w - 3):
        for dx in range(2):
            f.px(cx + dx, 2, shade(SKULL.c["dark"], 0.45))
            f.px(cx + dx, 5, shade(SKULL.c["dark"], 0.6))
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE_HOT if cx == 1 else EYE)
        f.glow(cx, 4, EYE)
        f.glow(cx + 1, 4, EYE)
    for y in range(0, 3):
        tone(f, f.w // 2, y, 0.65)
    for name, ex in (("right", faces["right"].w - 2), ("left", 0)):
        s = faces[name]
        s.px(ex, 2, shade(SKULL.c["dark"], 0.45))
        s.px(ex + 1, 2, shade(SKULL.c["dark"], 0.45))
        s.glow(ex, 3, EYE)
        s.glow(ex + 1, 3, EYE)
    tuck(faces, 2, 0.7)


def brow_detail(faces):
    tone_all(faces["bottom"], 0.4)
    for name in ("front", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            tone(f, x, f.h - 1, 0.6)


def snout_detail(faces):
    """The muzzle: shorter hair, darker toward the nose, whisker pits along the sides, the mouth's red underneath."""
    for name in ("left", "right", "top"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                t = x / max(1, f.w - 1) if name == "right" else 1 - x / max(1, f.w - 1) if name == "left" else 1 - y / max(1, f.h - 1)
                tone(f, x, y, 1.0 - 0.35 * t)
        if name != "top":
            for x in range(1, f.w - 1, 2):
                f.px(x, f.h - 2, shade(BEARD.c["dark"], 1.2))
    faces["bottom"].fill(lambda x, y: MOUTH)
    tuck(faces, 1, 0.75)


def lip_detail(faces):
    """The flews: black lips hanging over the teeth, drawn back in a snarl toward the front, where the teeth and the
    gums above them show."""
    f = faces["front"]
    for x in range(f.w):
        tone(f, x, f.h - 1, 0.6)
    for name in ("left", "right"):
        s = faces[name]
        for i in range(min(6, s.w)):
            x = s.w - 1 - i if name == "right" else i
            s.px(x, s.h - 1, TOOTH if i % 2 == 0 else shade(TOOTH, 0.8))
            if i < 4:
                s.px(x, s.h - 2, GUM)
        for i in range(6, s.w):
            x = s.w - 1 - i if name == "right" else i
            tone(s, x, s.h - 1, 0.6)
    faces["bottom"].fill(lambda x, y: MOUTH)


def nose_detail(faces):
    f = faces["front"]
    f.px(0, 1, (0, 0, 0))
    f.px(f.w - 1, 1, (0, 0, 0))
    for x in range(1, f.w - 1):
        f.px(x, 0, hexc("4a4240"))
    f.px(f.w // 2, 2, (0, 0, 0))
    f.px(f.w // 2 - 1, 2, (0, 0, 0))


def jaw_detail(faces):
    """The lower jaw: gums and teeth round its top edge, the red mouth inside."""
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    for y in range(t.h):
        t.px(0, y, TOOTH if y % 2 else shade(TOOTH, 0.8))
        t.px(t.w - 1, y, TOOTH if y % 2 else shade(TOOTH, 0.8))
    for x in range(t.w):
        t.px(x, t.h - 1, TOOTH if x % 2 else shade(TOOTH, 0.8))
    f = faces["front"]
    for x in range(f.w):
        f.px(x, 0, GUM)
    for name in ("left", "right"):
        s = faces[name]
        for x in range(s.w):
            s.px(x, 0, LIP.c["base"])
    tuck(faces, 1, 0.6, names=("front", "left", "right"))


def tongue_detail(faces):
    f = faces["top"]
    for y in range(f.h):
        f.px(f.w // 2, y, shade(TONGUE.c["dark"], 0.9))


def ear_detail(faces):
    f = faces["front"]
    for y in range(1, f.h):
        for x in range(1, f.w - 1):
            f.px(x, y, hexc("4a2a22") if (x + y) % 3 else hexc("3a1e18"))


def chest_detail(faces):
    """The deep chest: ribs under the hide along the flanks, a dark keel underneath."""
    for name in ("left", "right"):
        f = faces[name]
        for x in range(2, f.w - 2, 3):
            for y in range(f.h // 3, f.h - 2):
                tone(f, x, y, 0.82)
    tuck(faces, 3, 0.65)


def belly_detail(faces):
    """A bare, greasy underside in folds, and mud caked up the sides."""
    b = faces["bottom"]
    for y in range(b.h):
        for x in range(b.w):
            if (y % 5) == 0:
                b.px(x, y, hexc("5e5038"))
            elif (x * 5 + y * 3) % 11 == 0:
                b.px(x, y, MUD.c["dark"])
            else:
                b.px(x, y, hexc("8c7a58"))
    for name in ("left", "right", "front", "back"):
        s = faces[name]
        for x in range(s.w):
            for y in range(max(0, s.h - 2), s.h):
                if (x + y) % 3 == 0:
                    s.px(x, y, MUD.c["light"])
        for x in range(s.w):
            tone(s, x, 0, 0.75)


def flank_detail(faces):
    """The loins: the hide drawn in where the waist tucks up behind the ribs."""
    for name in ("left", "right"):
        f = faces[name]
        for y in range(f.h):
            tone(f, 0, y, 0.75)
            tone(f, f.w - 1, y, 0.75)
    tuck(faces, 3, 0.6)


def muscle(faces):
    tuck(faces, 2, 0.7, 1.12)


def leg_detail(faces):
    tuck(faces, 1, 0.75)
    muddy(faces, 3)


def paw_detail(faces):
    muddy(faces, 4)
    tone_all(faces["bottom"], 0.5)


def toe_detail(faces):
    muddy(faces, 3)
    f = faces["front"]
    for y in range(f.h):
        tone(f, 0, y, 0.6)


def ruff_detail(faces):
    """Long fur hanging round the neck: tips darker, ragged at the bottom."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            cut = f.rng.choice((0, 0, 1, 1, 2))
            for y in range(f.h - cut, f.h):
                f.px(x, y, (0, 0, 0), 0)
    tuck(faces, 3, 0.65)


def tail_detail(faces):
    """The worm's tail: ringed like a worm's body, bare and greasy underneath."""
    for name in ("left", "right", "top"):
        f = faces[name]
        for x in range(0, f.w, 3):
            for y in range(f.h):
                if name == "top":
                    tone(f, x, y, 0.8)
        if name != "top":
            for x in range(0, f.w, 3):
                for y in range(f.h):
                    tone(f, x, y, 0.8)
    faces["bottom"].fill(lambda x, y: shade(hexc("5e5038"), 0.85 if x % 3 == 0 else 1.0))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("cerberus", texture_width=256)

    # the body: a deep ribcage and heavy shoulders, the waist tucked up behind them over a greasy paunch, and
    # broad haunches; hackles bristle along the spine
    body = m.bone("body", (0, 30, 0))
    body.cube("chest", (-13, 24, -30), (26, 22, 24), HIDE, detail=chest_detail)
    body.cube("brisket", (-10, 19, -30), (20, 6, 15), HIDE, detail=muscle)
    body.cube("breast", (-11, 23, -32), (22, 17, 3), RUFF, inflate=0.2, detail=ruff_detail)
    body.cube("withers", (-11, 44, -28), (22, 5, 18), HIDE, detail=muscle)
    body.cube("waist", (-10, 30, -8), (20, 14, 22), HIDE, detail=flank_detail)
    body.cube("belly", (-8, 28, -10), (16, 3, 12), BELLY, detail=belly_detail)
    body.cube("back", (-9, 43, -8), (18, 4, 22), HIDE, detail=muscle)
    body.cube("haunch", (-12, 30, 12), (24, 16, 20), HIDE, detail=muscle)
    body.cube("rump", (-10, 45, 14), (20, 2, 16), HIDE, detail=muscle)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):  # a cube from a to a + w on the right becomes -(a + w) to -a on the left
            return a if s == 1 else -(a + w)
        body.cube(f"{side}_shoulder", (x(9, 6), 26, -29), (6, 18, 12), HIDE, mirror=s == -1, detail=muscle)
        body.cube(f"{side}_ribs", (x(10, 4), 26, -16), (4, 15, 9), HIDE, mirror=s == -1, detail=chest_detail)
    for i, z in enumerate((-26, -18, -10, -2, 6, 14, 22)):
        h = 3 + (3 - abs(i - 2)) if i < 5 else 2
        y = 48 if i < 2 else 46.5 if i < 5 else 46.5
        body.cube(f"spine_{i}", (-2, y, z), (4, max(2, h), 6), BEARD, inflate=0.2, detail=ruff_detail)

    # three necks from the chest: the middle one straight ahead, the others splayed out
    for name, x0, turn in (("mid", 0, 0), ("right", 9, -28), ("left", -9, 28)):
        def hx(a, w, s):  # the same, about this head's own middle
            return x0 + a if s == 1 else x0 - (a + w)
        neck = body.bone(f"neck_{name}", (x0, 40, -26), rotation=(18, turn, 0))
        neck.cube(f"neck_base_{name}", (x0 - 6.5, 33, -33), (13, 14, 9), HIDE, detail=muscle)
        neck.cube(f"neck_{name}", (x0 - 5.5, 34, -41), (11, 12, 9), HIDE, detail=muscle)
        neck.cube(f"crest_{name}", (x0 - 3, 44, -38), (6, 3, 11), BEARD, inflate=0.2, detail=ruff_detail)
        neck.cube(f"ruff_{name}", (x0 - 7.5, 31, -32), (15, 17, 4), RUFF, inflate=0.3, detail=ruff_detail)
        neck.cube(f"ruff_low_{name}", (x0 - 6.5, 31, -38), (13, 6, 6), RUFF, inflate=0.2, detail=ruff_detail)
        head = neck.bone(f"head_{name}", (x0, 41, -40), rotation=(-12, 0, 0))
        head.cube(f"skull_{name}", (x0 - 5.5, 38, -49), (11, 10, 10), SKULL, detail=skull_detail)
        head.cube(f"brow_{name}", (x0 - 5, 45.5, -50), (10, 2, 2), SKULL, detail=brow_detail)
        head.cube(f"cheeks_{name}", (x0 - 6.5, 37, -48), (13, 5, 7), SKULL, detail=muscle)
        head.cube(f"snout_{name}", (x0 - 3.5, 40, -60), (7, 4, 11), SKULL, detail=snout_detail)
        head.cube(f"bridge_{name}", (x0 - 2.5, 44, -58), (5, 1, 9), SKULL)
        head.cube(f"nose_{name}", (x0 - 2, 42, -61.5), (4, 3, 2), NOSE, detail=nose_detail)
        head.cube(f"lips_{name}", (x0 - 4, 37, -59.5), (8, 3, 11), LIP, detail=lip_detail)
        for side, s in (("r", 1), ("l", -1)):
            head.cube(f"fang_{name}_{side}", (hx(2.2, 1, s), 35.5, -58.6), (1, 2, 1), FANG)
            # pointed ears, laid back and out
            ex = x0 + 3.5 * s
            head.cube(f"ear_{name}_{side}", (hx(1.5, 4, s), 47, -43), (4, 4, 1), SKULL, detail=ear_detail,
                      rotation=(-35, 0, -16 * s), origin=(ex, 47, -42.5))
            head.cube(f"ear_tip_{name}_{side}", (hx(2.5, 2, s), 50.5, -43), (2, 3, 1), SKULL,
                      rotation=(-35, 0, -16 * s), origin=(ex, 47, -42.5))
        jaw = head.bone(f"jaw_{name}", (x0, 37, -48))
        jaw.cube(f"jaw_{name}", (x0 - 3, 34, -59), (6, 3, 11), SKULL, detail=jaw_detail)
        jaw.cube(f"jaw_hinge_{name}", (x0 - 4.5, 33.5, -49), (9, 4, 5), SKULL, detail=muscle)
        jaw.cube(f"tongue_{name}", (x0 - 2, 36.6, -57.5), (4, 1, 9), TONGUE, detail=tongue_detail)
        for side, s in (("r", 1), ("l", -1)):
            jaw.cube(f"tusk_{name}_{side}", (hx(1.6, 1, s), 36.5, -58.4), (1, 2, 1), FANG)
        jaw.cube(f"beard_{name}", (x0 - 3, 30, -55), (6, 4, 7), BEARD, detail=ruff_detail)
        jaw.cube(f"beard_tip_{name}", (x0 - 2, 27.5, -53.5), (4, 3, 4), BEARD, detail=ruff_detail)

    # four legs: the front ones from the shoulder down past the elbow to a sloped pastern, the hind ones bent at the
    # stifle and the hock; four toes and black claws on every paw, all caked in mud
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mr = s == -1
        front = body.bone(f"{side}_front_leg", (12 * s, 26, -20))
        front.cube(f"{side}_front_leg", (x(7, 9), 14, -25), (9, 14, 10), HIDE, mirror=mr, detail=muscle)
        front.cube(f"{side}_front_elbow", (x(8, 6), 13, -16.5), (6, 5, 3), HIDE, mirror=mr, detail=muscle)
        front.cube(f"{side}_front_forearm", (x(8.5, 7), 5, -23.5), (7, 10, 7), HIDE, mirror=mr, detail=leg_detail)
        front.cube(f"{side}_front_pastern", (x(9, 6), 2, -24), (6, 5, 6), MUD, mirror=mr, detail=leg_detail,
                   rotation=(14, 0, 0), origin=(12 * s, 7, -21))
        front.cube(f"{side}_front_paw", (x(8, 8), 0, -27), (8, 3, 7), MUD, mirror=mr, detail=paw_detail)
        front.cube(f"{side}_front_dewclaw", (x(8, 1), 4, -25), (1, 2, 1), CLAW, mirror=mr)
        back = body.bone(f"{side}_back_leg", (12 * s, 28, 26))
        back.cube(f"{side}_back_leg", (x(6, 11), 18, 17), (11, 14, 16), HIDE, mirror=mr, detail=muscle)
        back.cube(f"{side}_back_stifle", (x(7, 9), 13, 15), (9, 6, 8), HIDE, mirror=mr, detail=muscle)
        back.cube(f"{side}_back_gaskin", (x(8, 7), 8, 19), (7, 11, 6), HIDE, mirror=mr, detail=leg_detail,
                  rotation=(-32, 0, 0), origin=(12 * s, 18, 22))
        back.cube(f"{side}_back_hock", (x(8.5, 6), 4, 25), (6, 6, 6), MUD, mirror=mr, detail=leg_detail)
        back.cube(f"{side}_back_hock_point", (x(9, 5), 7, 30), (5, 3, 2), MUD, mirror=mr, detail=leg_detail)
        back.cube(f"{side}_back_pastern", (x(9, 5), 2, 23), (5, 4, 5), MUD, mirror=mr, detail=leg_detail)
        back.cube(f"{side}_back_paw", (x(8, 8), 0, 21), (8, 3, 7), MUD, mirror=mr, detail=paw_detail)
        for leg, z0 in (("front", -27), ("back", 21)):
            bone = front if leg == "front" else back
            for i in range(4):
                reach = 1 if i in (1, 2) else 0
                bone.cube(f"{side}_{leg}_toe_{i}", (x(8 + 2 * i, 2), 0, z0 - 3 - reach), (2, 3, 4), MUD, mirror=mr, detail=toe_detail)
                bone.cube(f"{side}_{leg}_claw_{i}", (x(8.5 + 2 * i, 1), 0, z0 - 5 - reach), (1, 2, 2), CLAW, mirror=mr)

    # a tail like a worm, ringed, thinning to a point and a last ragged tuft
    parent = body
    z = 31
    y = 41
    for i, (w, l) in enumerate([(8, 13), (7, 13), (6, 13), (5, 12), (4, 11)]):
        seg = parent.bone(f"tail_{i + 1}", (0, y, z), rotation=(8 if i == 0 else 5, 0, 0))
        seg.cube(f"tail_{i + 1}", (-w / 2, y - w / 2, z - 1), (w, w, l + 1), HIDE if i < 3 else MUD, detail=tail_detail)
        parent = seg
        z += l
    parent.cube("tail_tuft", (-2.5, y - 2.5, z - 3), (5, 5, 6), BEARD, inflate=0.2, detail=ruff_detail)

    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)
NECKS = ["neck_mid", "neck_right", "neck_left"]
TAIL = [f"tail_{i}" for i in range(1, 6)]


def wave(a, bone, length, amplitude, phase, axis=1, points=8):
    keys = []
    for i in range(points + 1):
        t = length * i / points
        v = [0.0, 0.0, 0.0]
        v[axis] = round(amplitude * math.sin(2 * math.pi * i / points - phase), 3)
        keys.append((round(t, 4), tuple(v)))
    a.rot(bone, keys)


def bite(m, name, flash, snap, length):
    """One head draws back and up, its jaw gaping (the white flash), then snaps forward and down."""
    a = m.animation(f"bite_{name}", length)
    a.rot(f"neck_{name}", [(0, ZERO), (flash, (25, 0, 0)), (snap, (-22, 0, 0)), (snap + 0.25, (-18, 0, 0)), (length, ZERO)])
    a.rot(f"head_{name}", [(0, ZERO), (flash, (15, 0, 0)), (snap, (-10, 0, 0)), (length, ZERO)])
    a.rot(f"jaw_{name}", [(0, ZERO), (flash, (-40, 0, 0)), (snap - 0.05, (-45, 0, 0)), (snap, ZERO), (length, ZERO)])
    a.rot("body", [(0, ZERO), (flash, (4, 0, 0)), (snap, (-5, 0, 0)), (length, ZERO)])
    a.pos("body", [(0, ZERO), (snap, (0, 0, -3)), (length, ZERO)])
    a.event(round(flash - 0.15, 3), "flash").event(snap, "bite")


def animations(m):
    # (Blockbench's rotations: +X tips a body, neck or head back and up, -X forward and down; a jaw opens with -X)
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("body", [(0, ZERO), (1.5, (0, -0.8, 0)), (3.0, ZERO)])
    for i, neck in enumerate(NECKS):
        wave(idle, neck, 3.0, 7, 1.9 * i, axis=1)
        # panting: each jaw drops open and shuts on its own beat
        t0 = 0.3 + 0.6 * i
        idle.rot(neck.replace("neck", "jaw"), [(0, ZERO), (t0, ZERO), (t0 + 0.35, (-12, 0, 0)), (t0 + 0.7, (-3, 0, 0)), (t0 + 1.0, (-10, 0, 0)),
                                                (t0 + 1.4, ZERO), (3.0, ZERO)])
    for i, bone in enumerate(TAIL):
        wave(idle, bone, 3.0, 6 + 2 * i, 0.8 * i)

    # a heavy, rolling walk: diagonal pairs of legs together, the heads bobbing
    walk = m.animation("walk", 1.2, loop=True)
    for leg, phase in (("right_front_leg", 0), ("left_back_leg", 0), ("left_front_leg", math.pi), ("right_back_leg", math.pi)):
        walk.rot(leg, [(round(1.2 * i / 8, 4), (round(28 * math.sin(2 * math.pi * i / 8 + phase), 3), 0, 0)) for i in range(9)])
    walk.pos("body", [(0, ZERO), (0.3, (0, -1.2, 0)), (0.6, ZERO), (0.9, (0, -1.2, 0)), (1.2, ZERO)])
    walk.rot("body", [(0, (0, 0, 2)), (0.6, (0, 0, -2)), (1.2, (0, 0, 2))])
    for i, neck in enumerate(NECKS):
        walk.rot(neck, [(0, (4, 0, 0)), (0.3, (-4, 0, 0)), (0.6, (4, 0, 0)), (0.9, (-4, 0, 0)), (1.2, (4, 0, 0))])
    for i, bone in enumerate(TAIL):
        wave(walk, bone, 1.2, 8 + 2 * i, 0.9 * i)

    # one head bites (white: block it and that head reels)
    for name in ("mid", "right", "left"):
        bite(m, name, 0.5, 0.75, 1.3)

    # Three Maws: all three draw back together (the red cone), then bite one after another
    maws = m.animation("maws", 2.2)
    for i, name in enumerate(("mid", "right", "left")):
        snap = 0.9 + 0.3 * i
        maws.rot(f"neck_{name}", [(0, ZERO), (0.6, (30, 0, 0)), (snap - 0.1, (30, 0, 0)), (snap, (-25, 0, 0)), (snap + 0.3, (-15, 0, 0)), (2.2, ZERO)])
        maws.rot(f"jaw_{name}", [(0, ZERO), (0.6, (-40, 0, 0)), (snap - 0.05, (-45, 0, 0)), (snap, ZERO), (2.2, ZERO)])
    maws.rot("body", [(0, ZERO), (0.6, (6, 0, 0)), (0.9, (-6, 0, 0)), (1.5, (-6, 0, 0)), (2.2, ZERO)])
    maws.pos("body", [(0, ZERO), (0.6, (0, 0, 4)), (0.9, (0, 0, -4)), (1.5, (0, 0, -6)), (2.2, ZERO)])
    maws.event(0.9, "maw1").event(1.2, "maw2").event(1.5, "maw3")

    # Filth: the middle head lowers and retches the mire over the ground; the others turn away
    filth = m.animation("filth", 2.2)
    filth.rot("neck_mid", [(0, ZERO), (0.5, (15, 0, 0)), (0.9, (-30, 0, 0)), (1.0, (-40, 0, 0)), (1.3, (-35, 0, 0)), (1.6, (-40, 0, 0)), (2.2, ZERO)])
    filth.rot("jaw_mid", [(0, ZERO), (0.9, (-10, 0, 0)), (1.0, (-50, 0, 0)), (1.7, (-50, 0, 0)), (2.2, ZERO)])
    filth.rot("neck_right", [(0, ZERO), (0.8, (0, -25, 0)), (1.8, (0, -25, 0)), (2.2, ZERO)])
    filth.rot("neck_left", [(0, ZERO), (0.8, (0, 25, 0)), (1.8, (0, 25, 0)), (2.2, ZERO)])
    filth.rot("body", [(0, ZERO), (0.5, (5, 0, 0)), (1.0, (-6, 0, 0)), (2.2, ZERO)])
    filth.event(1.0, "spew")

    # the Howl: three heads thrown back, three throats at once (purple: it can't be blocked)
    howl = m.animation("howl", 2.6)
    for i, name in enumerate(("mid", "right", "left")):
        howl.rot(f"neck_{name}", [(0, ZERO), (0.5, (-10, 0, 0)), (1.0, (45, 0, 0)), (1.9, (48, 0, 0)), (2.6, ZERO)])
        howl.rot(f"head_{name}", [(0, ZERO), (0.5, (-5, 0, 0)), (1.0, (12, 0, 0)), (1.9, (12, 0, 0)), (2.6, ZERO)])
        howl.rot(f"jaw_{name}", [(0, ZERO), (0.9, (-10, 0, 0)), (1.0, (-55, 0, 0)), (1.9, (-55, 0, 0)), (2.3, ZERO)])
    howl.rot("body", [(0, ZERO), (0.5, (-4, 0, 0)), (1.0, (10, 0, 0)), (1.9, (10, 0, 0)), (2.6, ZERO)])
    howl.event(0.4, "breath").event(1.0, "howl")

    # in his fury he charges: heads down, a lunge
    charge = m.animation("charge", 1.0, loop=True)
    for leg, phase in (("right_front_leg", 0), ("left_front_leg", 0.4), ("right_back_leg", math.pi), ("left_back_leg", math.pi + 0.4)):
        charge.rot(leg, [(round(0.5 * i / 4, 4), (round(45 * math.sin(2 * math.pi * i / 4 + phase), 3), 0, 0)) for i in range(5)])
    charge.rot("body", [(0, (-6, 0, 0)), (0.25, (-2, 0, 0)), (0.5, (-6, 0, 0)), (0.75, (-2, 0, 0)), (1.0, (-6, 0, 0))])
    for name in ("mid", "right", "left"):
        charge.rot(f"neck_{name}", [(0, (-20, 0, 0)), (1.0, (-20, 0, 0))])
        charge.rot(f"jaw_{name}", [(0, (-35, 0, 0)), (0.5, (-25, 0, 0)), (1.0, (-35, 0, 0))])

    # parried: the head that bit reels back, and the whole beast with it
    stagger = m.animation("stagger", 2.0)
    stagger.rot("body", [(0, ZERO), (0.15, (12, 0, 6)), (0.8, (8, 0, -6)), (1.4, (6, 0, 4)), (2.0, ZERO)])
    stagger.pos("body", [(0, ZERO), (0.15, (0, 0, 6)), (2.0, ZERO)])
    for i, name in enumerate(("mid", "right", "left")):
        stagger.rot(f"neck_{name}", [(0, ZERO), (0.15, (35, 12 * (i - 1), 0)), (1.0, (20, -10 * (i - 1), 0)), (2.0, ZERO)])
        stagger.rot(f"jaw_{name}", [(0, ZERO), (0.15, (-35, 0, 0)), (1.2, (-20, 0, 0)), (2.0, ZERO)])

    # at half his health: he rears up on his hind legs (they stay planted), roars with all three throats, and slams
    # down onto his forepaws
    phase = m.animation("phase", 3.0)
    phase.rot("body", [(0, ZERO), (0.8, (30, 0, 0)), (2.0, (33, 0, 0)), (2.3, (-6, 0, 0)), (3.0, ZERO)])
    phase.pos("body", [(0, ZERO), (0.8, (0, 12, 0)), (2.0, (0, 13, 0)), (2.3, (0, -2, 0)), (3.0, ZERO)])
    for leg in ("right_back_leg", "left_back_leg"):
        phase.rot(leg, [(0, ZERO), (0.8, (-30, 0, 0)), (2.0, (-33, 0, 0)), (2.3, (6, 0, 0)), (3.0, ZERO)])
    for leg in ("right_front_leg", "left_front_leg"):
        phase.rot(leg, [(0, ZERO), (0.8, (40, 0, 0)), (1.2, (10, 0, 0)), (1.6, (45, 0, 0)), (2.0, (20, 0, 0)), (2.3, ZERO)])
    for name in ("mid", "right", "left"):
        phase.rot(f"neck_{name}", [(0, ZERO), (1.0, (-12, 0, 0)), (2.0, (-12, 0, 0)), (2.3, (-20, 0, 0)), (3.0, ZERO)])
        phase.rot(f"jaw_{name}", [(0, ZERO), (1.0, (-55, 0, 0)), (2.0, (-55, 0, 0)), (2.4, ZERO)])
    phase.rot("tail_1", [(0, ZERO), (0.8, (-28, 0, 0)), (2.0, (-30, 0, 0)), (2.4, ZERO)])
    phase.event(1.1, "roar").event(2.3, "slam")

    # out of the mud: sunk to the shoulders, heads down, he heaves himself up, shakes and roars
    intro = m.animation("intro", 6.0)
    intro.pos("body", [(0, (0, -34, 0)), (0.8, (0, -34, 0)), (2.8, (0, -4, 0)), (3.2, ZERO)])
    intro.rot("body", [(0, (-12, 0, 0)), (0.8, (-12, 0, 0)), (2.8, (8, 0, 0)), (3.2, ZERO), (3.6, (0, 0, 8)), (3.9, (0, 0, -8)), (4.2, ZERO)])
    for i, name in enumerate(("mid", "right", "left")):
        intro.rot(f"neck_{name}", [(0, (-35, 0, 0)), (2.8, (-10, 0, 0)), (4.2, (35, 0, 0)), (5.2, (35, 0, 0)), (6.0, ZERO)])
        intro.rot(f"jaw_{name}", [(0, ZERO), (4.1, (-5, 0, 0)), (4.3, (-55, 0, 0)), (5.2, (-50, 0, 0)), (5.6, ZERO)])
    intro.event(0.8, "rise").event(4.3, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("body", [(0, ZERO), (0.1, (4, 0, 0)), (0.35, ZERO)])
    for name in ("mid", "right", "left"):
        hurt.rot(f"neck_{name}", [(0, ZERO), (0.1, (10, 0, 0)), (0.35, ZERO)])

    # the great worm falls: legs give, he rolls onto his side, and the heads drop one by one
    death = m.animation("death", 3.6)
    death.rot("body", [(0, ZERO), (0.6, (10, 0, 0)), (1.6, (0, 0, 40)), (3.6, (0, 0, 46))])
    death.pos("body", [(0, ZERO), (1.6, (0, -10, 0)), (3.6, (0, -12, 0))])
    for leg in ("right_front_leg", "left_front_leg", "right_back_leg", "left_back_leg"):
        death.rot(leg, [(0, ZERO), (0.8, (-30, 0, 0)), (1.6, (-50, 0, 0)), (3.6, (-50, 0, 0))])
    for i, name in enumerate(("right", "mid", "left")):
        drop = 1.6 + 0.5 * i
        death.rot(f"neck_{name}", [(0, ZERO), (0.6, (40, 0, 0)), (drop, (30, 0, 0)), (drop + 0.4, (-45, 0, 0)), (3.6, (-45, 0, 0))])
        death.rot(f"jaw_{name}", [(0, ZERO), (0.6, (-50, 0, 0)), (drop + 0.4, (-20, 0, 0)), (3.6, (-20, 0, 0))])
    death.event(1.6, "fall")


if __name__ == "__main__":
    build().write()
