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
CLAW = material("claw", "flat", base="1a1612")
SKULL = material("skull", "hair", light="6a5640", dark="34281c")

EYE = hexc("ff2a1a")
EYE_HOT = hexc("ffc8a0")
MOUTH = hexc("3a0a08")
TOOTH = hexc("e6dcc0")


def head_detail(faces):
    """Red eyes set deep under a heavy brow."""
    f = faces["front"]
    for cx in (2, f.w - 4):
        f.px(cx, 3, shade(SKULL.c["dark"], 0.6))
        f.px(cx + 1, 3, shade(SKULL.c["dark"], 0.6))
        f.glow(cx, 4, EYE)
        f.glow(cx + 1, 4, EYE_HOT)
    for name in ("left", "right"):
        s = faces[name]
        s.glow(2, 4, EYE)


def snout_detail(faces):
    f = faces["front"]
    mid = f.w // 2
    f.px(mid - 1, 1, hexc("120c0a"))
    f.px(mid, 1, hexc("120c0a"))
    b = faces["bottom"]
    b.fill(lambda x, y: MOUTH)
    for x in range(0, f.w, 2):
        f.px(x, f.h - 1, TOOTH)


def jaw_detail(faces):
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    f = faces["front"]
    for x in range(0, f.w, 2):
        f.px(x, 0, TOOTH)
    for name in ("left", "right"):
        s = faces[name]
        for x in range(1, s.w, 3):
            s.px(x, 0, TOOTH)


def belly_detail(faces):
    """A bare, greasy underside in folds, and mud caked up the sides."""
    b = faces["bottom"]
    for y in range(b.h):
        for x in range(b.w):
            if (y % 6) == 0:
                b.px(x, y, hexc("5e5038"))
            elif (x * 5 + y * 3) % 11 == 0:
                b.px(x, y, MUD.c["dark"])
            else:
                b.px(x, y, hexc("8c7a58"))
    for name in ("left", "right", "front"):
        s = faces[name]
        for x in range(s.w):
            for y in range(s.h - 6, s.h):
                if (x + y) % 4 == 0:
                    s.px(x, y, MUD.c["light"])


def build():
    m = Model("cerberus", texture_width=256)

    body = m.bone("body", (0, 30, 0))
    body.cube("chest", (-15, 22, -30), (30, 26, 26), HIDE)
    body.cube("belly", (-18, 12, -8), (36, 30, 28), BELLY, detail=belly_detail)
    body.cube("haunch", (-15, 20, 18), (30, 24, 18), HIDE)
    body.cube("back", (-12, 42, -6), (24, 6, 26), HIDE)
    for i, z in enumerate((-26, -18, -10, -2, 6, 14, 22)):
        h = 6 - abs(i - 3)
        body.cube(f"spine_{i}", (-1.5, 46 + (0 if i < 2 else 2), z), (3, h + 2, 4), CLAW)

    # three necks from the chest: the middle one straight ahead, the others splayed out
    for name, x0, turn in (("mid", 0, 0), ("right", 9, -28), ("left", -9, 28)):
        neck = body.bone(f"neck_{name}", (x0, 40, -26), rotation=(18, turn, 0))
        neck.cube(f"neck_{name}", (x0 - 6, 34, -40), (12, 12, 16), HIDE)
        neck.cube(f"ruff_{name}", (x0 - 7, 33, -32), (14, 14, 6), MUD, inflate=0.3)
        head = neck.bone(f"head_{name}", (x0, 41, -40))
        head.cube(f"skull_{name}", (x0 - 6, 36, -52), (12, 11, 13), SKULL, detail=head_detail)
        head.cube(f"snout_{name}", (x0 - 4, 37, -61), (8, 6, 10), SKULL, detail=snout_detail)
        for side, s in (("r", 1), ("l", -1)):
            ex = x0 + (3 if s == 1 else -6)
            head.cube(f"ear_{name}_{side}", (ex, 46, -46), (3, 5, 3), CLAW, rotation=(-20, 0, -15 * s), origin=(ex + 1.5, 46, -44.5))
        jaw = head.bone(f"jaw_{name}", (x0, 37, -48))
        jaw.cube(f"jaw_{name}", (x0 - 4, 33, -60), (8, 4, 12), SKULL, detail=jaw_detail)
        jaw.cube(f"beard_{name}", (x0 - 3, 27, -54), (6, 6, 5), BEARD)

    # four short, thick legs with clawed paws (the front ones under the chest, the back ones under the haunch)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        front = body.bone(f"{side}_front_leg", (12 * s, 26, -20))
        front.cube(f"{side}_front_leg", (x(7, 10), 2, -25), (10, 24, 10), HIDE, mirror=mirror)
        front.cube(f"{side}_front_paw", (x(6, 12), 0, -28), (12, 4, 13), MUD, mirror=mirror)
        for i in range(3):
            front.cube(f"{side}_front_claw_{i}", (x(7 + i * 4, 2), 0, -31), (2, 3, 3), CLAW, mirror=mirror)
        back = body.bone(f"{side}_back_leg", (12 * s, 28, 26))
        back.cube(f"{side}_back_leg", (x(7, 10), 2, 21), (10, 26, 11), HIDE, mirror=mirror)
        back.cube(f"{side}_back_paw", (x(6, 12), 0, 19), (12, 4, 13), MUD, mirror=mirror)
        for i in range(3):
            back.cube(f"{side}_back_claw_{i}", (x(7 + i * 4, 2), 0, 16), (2, 3, 3), CLAW, mirror=mirror)

    # a tail like a worm, thinning to a point
    parent = body
    z = 36
    for i, (w, l) in enumerate([(12, 14), (10, 14), (8, 14), (6, 12), (4, 10)]):
        seg = parent.bone(f"tail_{i + 1}", (0, 30 - i, z), rotation=(-10 if i == 0 else 8, 0, 0))
        seg.cube(f"tail_{i + 1}", (-w / 2, 30 - i - w / 2, z), (w, w, l), HIDE if i < 3 else MUD)
        parent = seg
        z += l

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
    """One head draws back, its jaw gaping (the white flash), then snaps forward."""
    a = m.animation(f"bite_{name}", length)
    a.rot(f"neck_{name}", [(0, ZERO), (flash, (-25, 0, 0)), (snap, (22, 0, 0)), (snap + 0.25, (18, 0, 0)), (length, ZERO)])
    a.rot(f"head_{name}", [(0, ZERO), (flash, (-15, 0, 0)), (snap, (10, 0, 0)), (length, ZERO)])
    a.rot(f"jaw_{name}", [(0, ZERO), (flash, (40, 0, 0)), (snap - 0.05, (45, 0, 0)), (snap, ZERO), (length, ZERO)])
    a.rot("body", [(0, ZERO), (flash, (-4, 0, 0)), (snap, (5, 0, 0)), (length, ZERO)])
    a.pos("body", [(0, ZERO), (snap, (0, 0, -3)), (length, ZERO)])
    a.event(round(flash - 0.15, 3), "flash").event(snap, "bite")


def animations(m):
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("body", [(0, ZERO), (1.5, (0, -0.8, 0)), (3.0, ZERO)])
    for i, neck in enumerate(NECKS):
        wave(idle, neck, 3.0, 7, 1.9 * i, axis=1)
        wave(idle, neck.replace("neck", "jaw"), 3.0, 6, 1.3 * i + 1, axis=0)
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
        maws.rot(f"neck_{name}", [(0, ZERO), (0.6, (-30, 0, 0)), (snap - 0.1, (-30, 0, 0)), (snap, (25, 0, 0)), (snap + 0.3, (15, 0, 0)), (2.2, ZERO)])
        maws.rot(f"jaw_{name}", [(0, ZERO), (0.6, (40, 0, 0)), (snap - 0.05, (45, 0, 0)), (snap, ZERO), (2.2, ZERO)])
    maws.rot("body", [(0, ZERO), (0.6, (-6, 0, 0)), (0.9, (6, 0, 0)), (1.5, (6, 0, 0)), (2.2, ZERO)])
    maws.pos("body", [(0, ZERO), (0.6, (0, 0, 4)), (0.9, (0, 0, -4)), (1.5, (0, 0, -6)), (2.2, ZERO)])
    maws.event(0.9, "maw1").event(1.2, "maw2").event(1.5, "maw3")

    # Filth: the middle head lowers and retches the mire over the ground; the others turn away
    filth = m.animation("filth", 2.2)
    filth.rot("neck_mid", [(0, ZERO), (0.5, (-15, 0, 0)), (0.9, (30, 0, 0)), (1.0, (40, 0, 0)), (1.3, (35, 0, 0)), (1.6, (40, 0, 0)), (2.2, ZERO)])
    filth.rot("jaw_mid", [(0, ZERO), (0.9, (10, 0, 0)), (1.0, (50, 0, 0)), (1.7, (50, 0, 0)), (2.2, ZERO)])
    filth.rot("neck_right", [(0, ZERO), (0.8, (0, -25, 0)), (1.8, (0, -25, 0)), (2.2, ZERO)])
    filth.rot("neck_left", [(0, ZERO), (0.8, (0, 25, 0)), (1.8, (0, 25, 0)), (2.2, ZERO)])
    filth.rot("body", [(0, ZERO), (0.5, (-5, 0, 0)), (1.0, (6, 0, 0)), (2.2, ZERO)])
    filth.event(1.0, "spew")

    # the Howl: three heads thrown back, three throats at once (purple: it can't be blocked)
    howl = m.animation("howl", 2.6)
    for i, name in enumerate(("mid", "right", "left")):
        howl.rot(f"neck_{name}", [(0, ZERO), (0.5, (10, 0, 0)), (1.0, (-55, 0, 0)), (1.9, (-58, 0, 0)), (2.6, ZERO)])
        howl.rot(f"jaw_{name}", [(0, ZERO), (0.9, (10, 0, 0)), (1.0, (55, 0, 0)), (1.9, (55, 0, 0)), (2.3, ZERO)])
    howl.rot("body", [(0, ZERO), (0.5, (4, 0, 0)), (1.0, (-10, 0, 0)), (1.9, (-10, 0, 0)), (2.6, ZERO)])
    howl.event(0.4, "breath").event(1.0, "howl")

    # in his fury he charges: head down, a lunge
    charge = m.animation("charge", 1.0, loop=True)
    for leg, phase in (("right_front_leg", 0), ("left_front_leg", 0.4), ("right_back_leg", math.pi), ("left_back_leg", math.pi + 0.4)):
        charge.rot(leg, [(round(0.5 * i / 4, 4), (round(45 * math.sin(2 * math.pi * i / 4 + phase), 3), 0, 0)) for i in range(5)])
    charge.rot("body", [(0, (6, 0, 0)), (0.25, (2, 0, 0)), (0.5, (6, 0, 0)), (0.75, (2, 0, 0)), (1.0, (6, 0, 0))])
    for name in ("mid", "right", "left"):
        charge.rot(f"neck_{name}", [(0, (20, 0, 0)), (1.0, (20, 0, 0))])
        charge.rot(f"jaw_{name}", [(0, (35, 0, 0)), (0.5, (25, 0, 0)), (1.0, (35, 0, 0))])

    # parried: the head that bit reels, and the whole beast with it
    stagger = m.animation("stagger", 2.0)
    stagger.rot("body", [(0, ZERO), (0.15, (-12, 0, 6)), (0.8, (-8, 0, -6)), (1.4, (-6, 0, 4)), (2.0, ZERO)])
    stagger.pos("body", [(0, ZERO), (0.15, (0, 0, 6)), (2.0, ZERO)])
    for i, name in enumerate(("mid", "right", "left")):
        stagger.rot(f"neck_{name}", [(0, ZERO), (0.15, (-35, 12 * (i - 1), 0)), (1.0, (-20, -10 * (i - 1), 0)), (2.0, ZERO)])
        stagger.rot(f"jaw_{name}", [(0, ZERO), (0.15, (35, 0, 0)), (1.2, (20, 0, 0)), (2.0, ZERO)])

    # at half his health: he rears up on his hind legs, roars with all three throats, and slams down
    phase = m.animation("phase", 3.0)
    phase.rot("body", [(0, ZERO), (0.8, (-32, 0, 0)), (2.0, (-35, 0, 0)), (2.3, (6, 0, 0)), (3.0, ZERO)])
    phase.pos("body", [(0, ZERO), (0.8, (0, 8, 6)), (2.0, (0, 9, 6)), (2.3, (0, -2, 0)), (3.0, ZERO)])
    for leg in ("right_front_leg", "left_front_leg"):
        phase.rot(leg, [(0, ZERO), (0.8, (-40, 0, 0)), (1.2, (-10, 0, 0)), (1.6, (-45, 0, 0)), (2.0, (-20, 0, 0)), (2.3, ZERO)])
    for name in ("mid", "right", "left"):
        phase.rot(f"neck_{name}", [(0, ZERO), (1.0, (-30, 0, 0)), (2.0, (-30, 0, 0)), (2.3, (20, 0, 0)), (3.0, ZERO)])
        phase.rot(f"jaw_{name}", [(0, ZERO), (1.0, (55, 0, 0)), (2.0, (55, 0, 0)), (2.4, ZERO)])
    phase.event(1.1, "roar").event(2.3, "slam")

    # out of the mud: sunk to the shoulders, heads down, he heaves himself up, shakes and roars
    intro = m.animation("intro", 6.0)
    intro.pos("body", [(0, (0, -34, 0)), (0.8, (0, -34, 0)), (2.8, (0, -4, 0)), (3.2, ZERO)])
    intro.rot("body", [(0, (12, 0, 0)), (0.8, (12, 0, 0)), (2.8, (-8, 0, 0)), (3.2, ZERO), (3.6, (0, 0, 8)), (3.9, (0, 0, -8)), (4.2, ZERO)])
    for i, name in enumerate(("mid", "right", "left")):
        intro.rot(f"neck_{name}", [(0, (35, 0, 0)), (2.8, (10, 0, 0)), (4.2, (-35, 0, 0)), (5.2, (-35, 0, 0)), (6.0, ZERO)])
        intro.rot(f"jaw_{name}", [(0, ZERO), (4.1, (5, 0, 0)), (4.3, (55, 0, 0)), (5.2, (50, 0, 0)), (5.6, ZERO)])
    intro.event(0.8, "rise").event(4.3, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("body", [(0, ZERO), (0.1, (-4, 0, 0)), (0.35, ZERO)])
    for name in ("mid", "right", "left"):
        hurt.rot(f"neck_{name}", [(0, ZERO), (0.1, (-10, 0, 0)), (0.35, ZERO)])

    # the great worm falls: legs give, he rolls onto his side, and the heads drop one by one
    death = m.animation("death", 3.6)
    death.rot("body", [(0, ZERO), (0.6, (-10, 0, 0)), (1.6, (0, 0, 40)), (3.6, (0, 0, 46))])
    death.pos("body", [(0, ZERO), (1.6, (0, -10, 0)), (3.6, (0, -12, 0))])
    for leg in ("right_front_leg", "left_front_leg", "right_back_leg", "left_back_leg"):
        death.rot(leg, [(0, ZERO), (0.8, (-30, 0, 0)), (1.6, (-50, 0, 0)), (3.6, (-50, 0, 0))])
    for i, name in enumerate(("right", "mid", "left")):
        drop = 1.6 + 0.5 * i
        death.rot(f"neck_{name}", [(0, ZERO), (0.6, (-40, 0, 0)), (drop, (-30, 0, 0)), (drop + 0.4, (45, 0, 0)), (3.6, (45, 0, 0))])
        death.rot(f"jaw_{name}", [(0, ZERO), (0.6, (50, 0, 0)), (drop + 0.4, (20, 0, 0)), (3.6, (20, 0, 0))])
    death.event(1.6, "fall")


if __name__ == "__main__":
    build().write()
