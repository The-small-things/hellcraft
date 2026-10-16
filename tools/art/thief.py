#!/usr/bin/env python3
"""The thieves (Malebolge's monster): "among this cruel and most dismal throng people were running naked and
affrighted, with serpents were their hands behind them bound" (Inferno XXIV). Lean grey souls with a serpent coiled
round their chest and hissing over a shoulder, a sack slung on the back. They snatch something from you and run;
kill one to get it back.

Run from the repo root:  python3 tools/art/thief.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="7a8070", dark="4a5044")
SERPENT = material("serpent", "scales", base="3a6a2a", edge="6a9a3a", belly="b0a060", belly_face="bottom")
SACK = material("sack", "robe", base="6a5a3a", fold="4a3a22")
HOOD = material("hood", "robe", base="2a2a26", fold="141410")
EYE = hexc("c8ff3a")
SNAKE_EYE = hexc("ffd23a")
FANG = hexc("f0ece0")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.glow(cx, 3, EYE)
    f.px(f.w // 2, f.h - 2, shade(SKIN.c["dark"], 0.6))


def snake_face(faces):
    f = faces["front"]
    f.glow(0, 0, SNAKE_EYE)
    f.glow(f.w - 1, 0, SNAKE_EYE)
    f.px(0, f.h - 1, FANG)
    f.px(f.w - 1, f.h - 1, FANG)


def build():
    m = Model("thief", texture_width=64)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_leg", (x(0, 3), 0, -1.5), (3, 12, 3), SKIN, mirror=s == -1)
    body = m.bone("body", (0, 12, 0), rotation=(-12, 0, 0))
    body.cube("torso", (-3.5, 12, -2), (7, 11, 4), SKIN)
    body.cube("coil_low", (-4, 14, -2.5), (8, 2, 5), SERPENT)
    body.cube("coil_high", (-4, 19, -2.5), (8, 2, 5), SERPENT)
    body.cube("sack", (-3, 15, 2), (6, 7, 4), SACK)
    serpent = body.bone("serpent", (3, 22, 0))
    serpent.cube("serpent_neck", (2, 22, -1), (2, 5, 2), SERPENT)
    serpent.cube("serpent_head", (1.5, 26, -3), (3, 2, 4), SERPENT, detail=snake_face)
    head = body.bone("head", (0, 23, 0))
    head.cube("head", (-3, 23, -3), (6, 6, 6), SKIN, detail=face)
    head.cube("hood", (-3.5, 25, -3), (7, 5, 7), HOOD, inflate=0.2)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (4.5 * s, 22, 0))
        arm.cube(f"{side}_arm", (x(3.5, 3), 11, -1.5), (3, 11, 3), SKIN, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # crouched and shifty, the serpent swaying
    idle = m.animation("idle", 1.4, loop=True)
    idle.rot("head", [(0, (0, -25, 0)), (0.7, (0, 25, 0)), (1.4, (0, -25, 0))])
    idle.rot("serpent", [(0, (0, 0, -10)), (0.7, (10, 0, 10)), (1.4, (0, 0, -10))])
    idle.rot("right_arm", [(0, (30, 0, 0)), (1.4, (30, 0, 0))])
    idle.rot("left_arm", [(0, (30, 0, 0)), (1.4, (30, 0, 0))])

    # a quick, low run
    walk = m.animation("walk", 0.5, loop=True)
    walk.rot("right_leg", [(0, (45, 0, 0)), (0.25, (-45, 0, 0)), (0.5, (45, 0, 0))])
    walk.rot("left_leg", [(0, (-45, 0, 0)), (0.25, (45, 0, 0)), (0.5, (-45, 0, 0))])
    walk.rot("right_arm", [(0, (45, 0, 0)), (0.25, (-30, 0, 0)), (0.5, (45, 0, 0))])
    walk.rot("left_arm", [(0, (-30, 0, 0)), (0.25, (45, 0, 0)), (0.5, (-30, 0, 0))])
    walk.rot("body", [(0, (-15, 0, 0)), (0.5, (-15, 0, 0))])

    # the snatch: a quick grab, while the serpent strikes
    attack = m.animation("attack", 0.5)
    attack.rot("right_arm", [(0, (30, 0, 0)), (0.15, (100, -20, 0)), (0.3, (20, 30, 0)), (0.5, (30, 0, 0))])
    attack.rot("serpent", [(0, ZERO), (0.15, (-40, 0, 0)), (0.25, (50, 0, 0)), (0.5, ZERO)])
    attack.event(0.25, "snatch")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (15, 0, 0)), (0.3, ZERO)])

    # off and away with the prize: the sack hugged close, a glance back
    flee = m.animation("flee", 0.8)
    flee.rot("head", [(0, ZERO), (0.3, (0, 150, 0)), (0.6, (0, 150, 0)), (0.8, ZERO)])
    flee.rot("right_arm", [(0, ZERO), (0.2, (60, 0, -40)), (0.8, (60, 0, -40))])
    flee.rot("left_arm", [(0, ZERO), (0.2, (60, 0, 40)), (0.8, (60, 0, 40))])

    # the serpent turns on him at the last
    death = m.animation("death", 1.2)
    death.rot("serpent", [(0, ZERO), (0.3, (60, 0, -40)), (0.6, (90, 0, -60)), (1.2, (90, 0, -60))])
    death.rot("body", [(0, ZERO), (0.6, (-50, 0, 0)), (1.2, (-80, 0, 0))])
    death.pos("body", [(0, ZERO), (0.6, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.rot("right_leg", [(0, ZERO), (1.2, (-50, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (1.2, (-45, 0, 0))])


if __name__ == "__main__":
    build().write()
