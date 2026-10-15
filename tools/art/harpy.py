#!/usr/bin/env python3
"""The harpies (the Wood of the Suicides' monster): "broad wings have they, and human necks and faces, feet with
claws, and their great belly feathered; they make lament upon the strange trees" (Inferno XIII). A gaunt grey
woman's head and chest on a bird's body, with ragged wings for arms, scaly legs and hooked talons. They roost in the
dead wood and come when the trees scream.

Run from the repo root:  python3 tools/art/harpy.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8a8a7c", dark="5a5a50")
FEATHER = material("feather", "hair", light="6a5a48", dark="2a2018")
WING = material("wing", "hair", light="5a4c3c", dark="1c1610")
HAIR = material("hair", "hair", light="4a4a44", dark="141412")
LEG = material("leg", "scales", base="6a5a3a", edge="8a7a50", belly="7a6a48")
TALON = material("talon", "horn", root="4a4038", tip="141210")
EYE = hexc("e8ff6a")
MOUTH = hexc("2a0a0a")
TOOTH = hexc("d8d0b8")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.px(cx, 2, shade(SKIN.c["dark"], 0.5))
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE)
    # a wide, wailing mouth
    for x in range(2, f.w - 2):
        f.px(x, f.h - 3, MOUTH)
        f.px(x, f.h - 2, MOUTH)
    for x in range(2, f.w - 2, 2):
        f.px(x, f.h - 3, TOOTH)


def feathered_belly(faces):
    """The lower half of the chest is feathered."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h // 2, f.h):
            for x in range(f.w):
                c = FEATHER.c["light"] if (x + y // 2) % 3 else FEATHER.c["dark"]
                f.px(x, y, shade(c, 1.0 - 0.3 * (y - f.h // 2) / max(1, f.h // 2)))


def ragged(faces):
    """Wing feathers: dark tips along the trailing edge, and a few gaps."""
    for name in ("top", "bottom"):
        f = faces[name]
        for x in range(f.w):
            for y in range(f.h):
                if x % 3 == 0:
                    f.px(x, y, shade(WING.c["dark"], 0.8))
    for name in ("front", "back"):
        f = faces[name]
        for x in range(f.w):
            if x % 4 == 1:
                f.px(x, f.h - 1, (0, 0, 0), alpha=0)


def build():
    m = Model("harpy", texture_width=128)
    body = m.bone("body", (0, 14, 0), rotation=(25, 0, 0))
    body.cube("chest", (-4, 12, -2.5), (8, 9, 5), SKIN, detail=feathered_belly)
    body.cube("rump", (-3.5, 8, -2), (7, 5, 6), FEATHER)
    tail = body.bone("tail", (0, 9, 3))
    tail.cube("tail", (-3, 7, 3), (6, 2, 7), FEATHER)
    head = body.bone("head", (0, 21, -1))
    head.cube("head", (-3, 21, -4), (6, 7, 6), SKIN, detail=face)
    head.cube("hair", (-3.5, 24, -3), (7, 5, 7), HAIR, inflate=0.25)
    head.cube("locks", (-3.5, 17, 1), (7, 7, 2), HAIR)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        wing = body.bone(f"{side}_wing", (4 * s, 20, 0))
        wing.cube(f"{side}_wing", (x(4, 12), 12, -1), (12, 9, 1), WING, mirror=s == -1, detail=ragged)
        tip = wing.bone(f"{side}_wingtip", (16 * s, 20, 0))
        tip.cube(f"{side}_wingtip", (x(16, 10), 13, -1), (10, 8, 1), WING, mirror=s == -1, detail=ragged)
        leg = m.bone(f"{side}_leg", (2 * s, 10, 1))
        leg.cube(f"{side}_thigh", (x(1, 3), 5, 0), (3, 5, 3), FEATHER, mirror=s == -1)
        leg.cube(f"{side}_shin", (x(1.5, 2), 1, 1), (2, 4, 2), LEG, mirror=s == -1)
        foot = leg.bone(f"{side}_foot", (2.5 * s, 1, 1))
        foot.cube(f"{side}_talons", (x(0.5, 4), 0, -2), (4, 1, 4), TALON, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)


def flap(a, length, up=-35, down=30, tip=20):
    """One wingbeat over `length` seconds: both wings up, then a hard stroke down, the tips lagging."""
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(0, (0, 0, up * s)), (length * 0.5, (0, 0, down * s)), (length, (0, 0, up * s))])
        a.rot(f"{side}_wingtip", [(0, (0, 0, -tip * s)), (length * 0.5, (0, 0, tip * s)), (length, (0, 0, -tip * s))])


def animations(m):
    # always aloft: the wings beat, the legs hang, the body bobs with each stroke
    idle = m.animation("idle", 0.6, loop=True)
    flap(idle, 0.6)
    idle.pos("body", [(0, ZERO), (0.3, (0, 1.2, 0)), (0.6, ZERO)])
    idle.rot("right_leg", [(0, (20, 0, 0)), (0.3, (26, 0, 0)), (0.6, (20, 0, 0))])
    idle.rot("left_leg", [(0, (22, 0, 0)), (0.3, (16, 0, 0)), (0.6, (22, 0, 0))])
    idle.rot("head", [(0, (-20, 0, 0)), (0.6, (-20, 0, 0))])

    # the stoop: wings swept back, talons thrown forward to rake
    attack = m.animation("attack", 0.6)
    for side, s in (("right", 1), ("left", -1)):
        attack.rot(f"{side}_wing", [(0, (0, 0, -35 * s)), (0.15, (0, 40 * s, -60 * s)), (0.35, (0, 20 * s, 20 * s)), (0.6, (0, 0, -35 * s))])
        attack.rot(f"{side}_leg", [(0, (20, 0, 0)), (0.15, (-30, 0, 0)), (0.3, (-80, 0, 0)), (0.45, (10, 0, 0)), (0.6, (20, 0, 0))])
        attack.rot(f"{side}_foot", [(0, ZERO), (0.25, (-40, 0, 0)), (0.32, (30, 0, 0)), (0.6, ZERO)])
    attack.rot("body", [(0, ZERO), (0.2, (-30, 0, 0)), (0.4, (10, 0, 0)), (0.6, ZERO)])
    attack.event(0.3, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (-20, 0, 8)), (0.3, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (-25, 0, 0)), (0.3, ZERO)])

    # the lament: head thrown back, wings spread wide and shaking
    shriek = m.animation("shriek", 1.2)
    shriek.rot("head", [(0, ZERO), (0.2, (-55, 0, 0)), (1.0, (-55, 0, 0)), (1.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        shriek.rot(f"{side}_wing", [(0, ZERO), (0.2, (0, 0, -60 * s)), (0.3, (0, 0, -50 * s)), (0.4, (0, 0, -62 * s)), (0.5, (0, 0, -50 * s)),
                                    (0.6, (0, 0, -62 * s)), (1.0, (0, 0, -60 * s)), (1.2, ZERO)])
        shriek.rot(f"{side}_wingtip", [(0, ZERO), (0.2, (0, 0, -30 * s)), (1.0, (0, 0, -30 * s)), (1.2, ZERO)])
    shriek.event(0.2, "shriek")

    # falls out of the air, wings crumpled
    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.4, (60, 0, 30)), (1.2, (90, 0, 40))])
    death.pos("body", [(0, ZERO), (0.6, (0, -8, 0)), (1.2, (0, -11, 0))])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, ZERO), (0.4, (0, 0, 40 * s)), (1.2, (0, 0, 70 * s))])
        death.rot(f"{side}_leg", [(0, ZERO), (1.2, (-60, 0, 0))])


if __name__ == "__main__":
    build().write()
