#!/usr/bin/env python3
"""The hoarder (Greed's monster): a hunched miser bent under a sack of coin, sallow-skinned, with eyes that shine
gold in the dark. Gold draws them: the more you carry, the more come, and their grasping blow steals it (they drop
it when killed). Dante's misers and prodigals roll great weights at each other with their chests; these carry theirs.

Run from the repo root:  python3 tools/art/hoarder.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="9c9868", dark="6b6844")
RAGS = material("rags", "robe", base="5a4a32", fold="3c3020")
HOOD = material("hood", "robe", base="4a3c28", fold="2e2618")
SACK = material("sack", "hair", light="b49a6a", dark="7a6644")
GOLD = material("gold", "metal", base="d4a83a", high="f6dc80", low="8a6a1e")
CLAW = material("claw", "flat", base="2a2620")

EYE = hexc("ffd84a")
MOUTH = hexc("2a1a10")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.glow(cx, 2, EYE)
        f.glow(cx + 1, 2, EYE)
        f.px(cx, 1, shade(SKIN.c["dark"], 0.7))
        f.px(cx + 1, 1, shade(SKIN.c["dark"], 0.7))
    for x in range(1, f.w - 1):
        f.px(x, f.h - 2, MOUTH)
    f.px(f.w // 2, f.h - 2, GOLD.c["high"])  # a gold tooth


def sack_detail(faces):
    """Coins showing at the sack's mouth, and the rope that ties it."""
    t = faces["top"]
    for y in range(t.h):
        for x in range(t.w):
            if (x * 7 + y * 3) % 5 < 3:
                t.px(x, y, GOLD.c["base"] if (x + y) % 3 else GOLD.c["high"])
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.px(x, 2, hexc("5a4020"))
        for x in range(0, f.w, 4):
            f.px(x, f.h // 2, GOLD.c["base"])


def build():
    m = Model("hoarder", texture_width=64)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_leg", (x(0.5, 3), 0, -1.5), (3, 12, 3), RAGS, mirror=s == -1)

    body = m.bone("body", (0, 12, 0), rotation=(-35, 0, 0))
    body.cube("torso", (-4, 12, -3), (8, 11, 6), RAGS)
    sack = body.bone("sack", (0, 20, 3))
    sack.cube("sack", (-6, 15, 2), (12, 12, 9), SACK, detail=sack_detail)
    head = body.bone("head", (0, 23, -1))
    head.cube("head", (-3.5, 23, -6), (7, 7, 7), SKIN, detail=face)
    head.cube("hood", (-3.5, 25, -5), (7, 6, 7), HOOD, inflate=0.6)
    head.cube("nose", (-1, 24, -8), (2, 3, 2), SKIN)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (5 * s, 21, -1))
        arm.cube(f"{side}_arm", (x(4, 3), 11, -2.5), (3, 11, 3), SKIN, mirror=s == -1)
        arm.cube(f"{side}_claws", (x(4, 3), 9, -3), (3, 2, 3), CLAW, mirror=s == -1)
        arm.cube(f"{side}_ring", (x(3.5, 4), 13, -3), (4, 1, 4), GOLD, mirror=s == -1)

    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("body", [(0, ZERO), (1, (2, 0, 0)), (2, ZERO)])
    idle.rot("head", [(0, ZERO), (0.5, (0, 12, 0)), (1, ZERO), (1.5, (0, -12, 0)), (2, ZERO)])
    idle.rot("sack", [(0, ZERO), (1, (-3, 0, 0)), (2, ZERO)])

    # driven by how fast he walks (the model scales it by his speed)
    walk = m.animation("walk", 1.0, loop=True)
    walk.rot("right_leg", [(0, (30, 0, 0)), (0.5, (-30, 0, 0)), (1, (30, 0, 0))])
    walk.rot("left_leg", [(0, (-30, 0, 0)), (0.5, (30, 0, 0)), (1, (-30, 0, 0))])
    walk.rot("right_arm", [(0, (-20, 0, 0)), (0.5, (20, 0, 0)), (1, (-20, 0, 0))])
    walk.rot("left_arm", [(0, (20, 0, 0)), (0.5, (-20, 0, 0)), (1, (20, 0, 0))])
    walk.pos("body", [(0, ZERO), (0.25, (0, -0.6, 0)), (0.5, ZERO), (0.75, (0, -0.6, 0)), (1, ZERO)])
    walk.rot("sack", [(0, (0, 0, 4)), (0.5, (0, 0, -4)), (1, (0, 0, 4))])

    # both claws snatch forward and draw the loot to his chest
    attack = m.animation("attack", 0.8)
    for arm in ("right_arm", "left_arm"):
        attack.rot(arm, [(0, ZERO), (0.25, (-30, 0, 0)), (0.4, (95, 0, 0)), (0.65, (40, 0, 0)), (0.8, ZERO)])
    attack.rot("body", [(0, ZERO), (0.25, (8, 0, 0)), (0.4, (-12, 0, 0)), (0.8, ZERO)])
    attack.rot("head", [(0, ZERO), (0.4, (15, 0, 0)), (0.8, ZERO)])
    attack.event(0.4, "grab")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (10, 0, 0)), (0.3, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (12, 0, 0)), (0.3, ZERO)])

    # he folds over his sack, and it tips and spills
    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.6, (-40, 0, 8)), (1.2, (-50, 0, 10))])
    death.pos("body", [(0, ZERO), (0.6, (0, -4, 0)), (1.2, (0, -6, 0))])
    death.rot("sack", [(0, ZERO), (0.8, (40, 0, 20)), (1.2, (55, 0, 25))])
    for arm in ("right_arm", "left_arm"):
        death.rot(arm, [(0, ZERO), (0.6, (20, 0, 0)), (1.2, (25, 0, 0))])
    death.rot("right_leg", [(0, ZERO), (0.6, (-20, 0, 0)), (1.2, (-25, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (0.6, (-15, 0, 0)), (1.2, (-20, 0, 0))])


if __name__ == "__main__":
    build().write()
