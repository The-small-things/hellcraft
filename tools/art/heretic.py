#!/usr/bin/env python3
"""The heretics (Heresy's monster): "from within came forth such dire laments, that well they seemed to be of
wretched souls and tormented" (Inferno IX). Souls burnt black in their open tombs, embers glowing through the cracks
in their skin and fire still licking at their heads and shoulders. When a tomb blasts near you, one may climb out of
it (the special animation); their grip sets you alight.

Run from the repo root:  python3 tools/art/heretic.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

CHAR = material("char", "skin", base="2a2420", dark="0e0a08")
ROBE = material("robe", "robe", base="3a2a22", fold="1a120e")
FIRE = material("fire", "flat", base="ff7a1a")
EMBER = hexc("ff8a2a")
EMBER_HOT = hexc("ffd25a")


def cracks(faces):
    """Glowing cracks through the charred skin."""
    for name in ("front", "back", "left", "right", "top"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if (x * 5 + y * 3) % 11 == 0 or (x * 2 + y * 7) % 13 == 0:
                    f.glow(x, y, EMBER if (x + y) % 2 else EMBER_HOT)


def face(faces):
    cracks(faces)
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.glow(cx, 3, EMBER_HOT)
        f.glow(cx + 1, 3, EMBER_HOT)
    for x in range(2, f.w - 2):
        f.glow(x, f.h - 2, EMBER)


def flames(faces):
    """Flame tongues: bright at the root, ragged and gone at the top."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                height = f.h - y
                if (x * 3 + height) % 4 == 0 and height > f.h // 2:
                    f.px(x, y, (0, 0, 0), alpha=0)
                else:
                    f.glow(x, y, EMBER_HOT if height < f.h // 3 else EMBER)
    for name in ("top", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EMBER)


def build():
    m = Model("heretic", texture_width=64)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_leg", (x(0, 3), 0, -1.5), (3, 12, 3), CHAR, mirror=s == -1, detail=cracks)
    body = m.bone("body", (0, 12, 0), rotation=(-8, 0, 0))
    body.cube("torso", (-4, 12, -2), (8, 12, 4), ROBE, detail=cracks)
    body.cube("shoulder_fire", (-4.5, 23, -2.5), (9, 4, 5), FIRE, detail=flames)
    head = body.bone("head", (0, 24, 0))
    head.cube("head", (-3.5, 24, -3.5), (7, 7, 7), CHAR, detail=face)
    head.cube("head_fire", (-3, 31, -3), (6, 5, 6), FIRE, detail=flames)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (5.5 * s, 23, 0))
        arm.cube(f"{side}_arm", (x(4, 3), 11, -1.5), (3, 12, 3), CHAR, mirror=s == -1, detail=cracks)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # swaying, arms hanging, the flames flickering
    idle = m.animation("idle", 1.6, loop=True)
    idle.rot("body", [(0, ZERO), (0.8, (0, 0, 4)), (1.6, ZERO)])
    idle.rot("right_arm", [(0, (-10, 0, 0)), (0.8, (-14, 0, 6)), (1.6, (-10, 0, 0))])
    idle.rot("left_arm", [(0, (-12, 0, 0)), (0.8, (-8, 0, -6)), (1.6, (-12, 0, 0))])
    idle.scale("head", [(0, (1, 1, 1)), (0.4, (1.02, 1.04, 1.02)), (0.8, (1, 1, 1)), (1.2, (1.03, 1.02, 1.03)), (1.6, (1, 1, 1))])

    # a shamble, arms reaching
    walk = m.animation("walk", 1.0, loop=True)
    walk.rot("right_leg", [(0, (30, 0, 0)), (0.5, (-30, 0, 0)), (1.0, (30, 0, 0))])
    walk.rot("left_leg", [(0, (-30, 0, 0)), (0.5, (30, 0, 0)), (1.0, (-30, 0, 0))])
    walk.rot("right_arm", [(0, (-70, 0, 0)), (0.5, (-80, 0, 0)), (1.0, (-70, 0, 0))])
    walk.rot("left_arm", [(0, (-80, 0, 0)), (0.5, (-70, 0, 0)), (1.0, (-80, 0, 0))])

    # a burning embrace: both arms in
    attack = m.animation("attack", 0.7)
    attack.rot("right_arm", [(0, (-70, 0, 0)), (0.2, (-110, 0, 30)), (0.35, (-80, 0, -25)), (0.7, (-70, 0, 0))])
    attack.rot("left_arm", [(0, (-80, 0, 0)), (0.2, (-110, 0, -30)), (0.35, (-80, 0, 25)), (0.7, (-80, 0, 0))])
    attack.rot("body", [(0, ZERO), (0.35, (20, 0, 0)), (0.7, ZERO)])
    attack.event(0.35, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (-12, 0, 0)), (0.3, ZERO)])

    # climbing out of its tomb: up from below, hands on the rim, then up
    rise = m.animation("rise", 1.6)
    rise.pos("body", [(0, (0, -20, 0)), (0.6, (0, -10, 0)), (1.2, (0, -2, 0)), (1.6, ZERO)])
    rise.pos("right_leg", [(0, (0, -20, 0)), (1.2, (0, -4, 0)), (1.6, ZERO)])
    rise.pos("left_leg", [(0, (0, -20, 0)), (1.2, (0, -4, 0)), (1.6, ZERO)])
    rise.rot("right_arm", [(0, (-170, 0, 0)), (0.6, (-160, 0, 10)), (1.2, (-40, 0, 0)), (1.6, (-10, 0, 0))])
    rise.rot("left_arm", [(0, (-170, 0, 0)), (0.6, (-160, 0, -10)), (1.2, (-40, 0, 0)), (1.6, (-12, 0, 0))])

    # crumbles into ash
    death = m.animation("death", 1.0)
    death.pos("body", [(0, ZERO), (0.5, (0, -6, 0)), (1.0, (0, -14, 0))])
    death.scale("body", [(0, (1, 1, 1)), (1.0, (1.2, 0.3, 1.2))])
    death.rot("right_leg", [(0, ZERO), (1.0, (0, 0, 40))])
    death.rot("left_leg", [(0, ZERO), (1.0, (0, 0, -40))])


if __name__ == "__main__":
    build().write()
