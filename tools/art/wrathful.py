#!/usr/bin/env python3
"""The wrathful (Wrath's monster): "naked people, all muddy, with looks of rage; they smote each other not only with
the hand, but with the head, and with the chest, and with the feet, tearing each other piecemeal with their teeth"
(Inferno VII). Hunched, mud-caked brawlers with wild hair and bared teeth, who fight anything near them, each other
included.

Run from the repo root:  python3 tools/art/wrathful.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8a6a52", dark="5a4232")
MUD = material("mud", "hair", light="4a3c2a", dark="241c12")
HAIR = material("hair", "hair", light="3a2a1e", dark="140e0a")
EYE = hexc("ff3a1a")
TOOTH = hexc("e8dcc0")
MOUTH = hexc("2a0a06")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.px(cx - 1 if cx > 1 else cx, 2, shade(SKIN.c["dark"], 0.6))
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE)
    for x in range(1, f.w - 1):
        f.px(x, f.h - 2, MOUTH)
    for x in range(1, f.w - 1, 2):
        f.px(x, f.h - 2, TOOTH)


def mud_spatter(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if (x * 7 + y * 5) % 9 == 0 or y >= f.h - 3 and (x + y) % 2 == 0:
                    f.px(x, y, MUD.c["light"] if (x + y) % 3 else MUD.c["dark"])


def build():
    m = Model("wrathful", texture_width=64)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_leg", (x(0, 4), 0, -2), (4, 12, 4), SKIN, mirror=s == -1, detail=mud_spatter)
    body = m.bone("body", (0, 12, 0), rotation=(-15, 0, 0))
    body.cube("torso", (-4.5, 12, -2.5), (9, 12, 5), SKIN, detail=mud_spatter)
    head = body.bone("head", (0, 24, -1))
    head.cube("head", (-3.5, 24, -4.5), (7, 7, 7), SKIN, detail=face)
    head.cube("hair", (-4, 28, -4), (8, 5, 8), HAIR, inflate=0.3)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (6 * s, 22, 0))
        arm.cube(f"{side}_arm", (x(4.5, 4), 11, -2), (4, 12, 4), SKIN, mirror=s == -1, detail=mud_spatter)
        arm.cube(f"{side}_fist", (x(4.25, 5), 9, -2.5), (5, 3, 5), SKIN, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # a brawler's stance: fists up, bouncing on his feet
    idle = m.animation("idle", 1.2, loop=True)
    idle.pos("body", [(0, ZERO), (0.3, (0, -0.5, 0)), (0.6, ZERO), (0.9, (0, -0.5, 0)), (1.2, ZERO)])
    idle.rot("right_arm", [(0, (-60, 0, -10)), (0.6, (-66, 0, -12)), (1.2, (-60, 0, -10))])
    idle.rot("left_arm", [(0, (-70, 0, 10)), (0.6, (-64, 0, 12)), (1.2, (-70, 0, 10))])
    idle.rot("head", [(0, ZERO), (0.6, (0, 10, 0)), (1.2, ZERO)])

    walk = m.animation("walk", 0.8, loop=True)
    walk.rot("right_leg", [(0, (35, 0, 0)), (0.4, (-35, 0, 0)), (0.8, (35, 0, 0))])
    walk.rot("left_leg", [(0, (-35, 0, 0)), (0.4, (35, 0, 0)), (0.8, (-35, 0, 0))])
    walk.rot("right_arm", [(0, (-50, 0, -10)), (0.4, (-70, 0, -10)), (0.8, (-50, 0, -10))])
    walk.rot("left_arm", [(0, (-70, 0, 10)), (0.4, (-50, 0, 10)), (0.8, (-70, 0, 10))])

    # a wild haymaker, then the head comes in too
    attack = m.animation("attack", 0.7)
    attack.rot("right_arm", [(0, (-60, 0, -10)), (0.15, (-20, -20, -30)), (0.3, (-100, 20, 10)), (0.7, (-60, 0, -10))])
    attack.rot("body", [(0, ZERO), (0.15, (0, 20, 0)), (0.3, (10, -25, 0)), (0.45, (20, 0, 0)), (0.7, ZERO)])
    attack.rot("head", [(0, ZERO), (0.3, (0, 0, 0)), (0.45, (30, 0, 0)), (0.7, ZERO)])
    attack.event(0.3, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (-15, 0, 0)), (0.3, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (-20, 0, 0)), (0.3, ZERO)])

    # beating his chest in a rage
    rage = m.animation("rage", 1.2)
    rage.rot("right_arm", [(0, (-60, 0, -10)), (0.2, (-80, 0, 40)), (0.35, (-60, 0, -10)), (0.65, (-80, 0, 40)), (0.8, (-60, 0, -10)), (1.2, (-60, 0, -10))])
    rage.rot("left_arm", [(0, (-70, 0, 10)), (0.35, (-90, 0, -40)), (0.5, (-70, 0, 10)), (0.8, (-90, 0, -40)), (0.95, (-70, 0, 10)), (1.2, (-70, 0, 10))])
    rage.rot("head", [(0, ZERO), (0.3, (-30, 0, 0)), (0.9, (-30, 0, 0)), (1.2, ZERO)])

    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.5, (60, 0, 10)), (1.2, (75, 0, 12))])
    death.pos("body", [(0, ZERO), (0.5, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.rot("right_leg", [(0, ZERO), (0.5, (-40, 0, 0)), (1.2, (-50, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (0.5, (-30, 0, 0)), (1.2, (-45, 0, 0))])


if __name__ == "__main__":
    build().write()
