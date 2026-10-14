#!/usr/bin/env python3
"""A shade of Limbo: one of the great souls who live on in the Noble Castle (Homer, Aristotle, Saladin...), drawn
pale and drained of colour like everything in Limbo, save the green laurel on their brows. They float a little
above the ground, their robes trailing into nothing, and the client draws them see-through. They talk, trade and
hand out Codex pages (entity/npc/Shade).

Run from the repo root:  python3 tools/art/shade.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="d6d2c6", dark="a8a498")
ROBE = material("robe", "robe", base="c6c8c4", fold="8e908c")
HEM = material("hem", "robe", base="a4a8a6", fold="72767a")
HAIR = material("hair", "hair", light="eeeee6", dark="b8b8b0")
LAUREL = material("laurel", "hair", light="7cbc4a", dark="3e7a24")
SCROLL = material("scroll", "flat", base="e2d6b0")

EYE = hexc("cfe8ff")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE)
        f.px(cx, 2, shade(SKIN.c["dark"], 0.8))
        f.px(cx + 1, 2, shade(SKIN.c["dark"], 0.8))
    f.px(f.w // 2, 4, shade(SKIN.c["dark"], 0.85))


def robe_trim(faces):
    """A darker band down the front of the robe, as a toga's fold."""
    f = faces["front"]
    for y in range(f.h):
        f.px(f.w // 2 - 1, y, shade(ROBE.c["fold"], 0.9))
        f.px(f.w // 2, y, shade(ROBE.c["fold"], 0.8))


def hem_fade(faces):
    """The robe trails into nothing: the bottom rows grow faint (and glow a little, as a soul does)."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.glow(x, f.h - 1, hexc("b8c8d8"))


def build():
    m = Model("shade", texture_width=64)
    # no legs: the whole figure floats, its robe trailing
    body = m.bone("body", (0, 14, 0))
    body.cube("chest", (-4, 14, -2.5), (8, 11, 5), ROBE, detail=robe_trim)
    skirt = body.bone("skirt", (0, 14, 0))
    skirt.cube("skirt", (-4.5, 4, -3), (9, 10, 6), ROBE, detail=robe_trim)
    skirt.cube("hem", (-4, 1, -2.5), (8, 3, 5), HEM, detail=hem_fade)
    head = body.bone("head", (0, 25, 0))
    head.cube("head", (-3.5, 25, -3.5), (7, 8, 7), SKIN, detail=face)
    head.cube("hair", (-3.5, 30, -3.5), (7, 3, 7), HAIR, inflate=0.4)
    head.cube("beard", (-2.5, 23, -4.5), (5, 4, 2), HAIR)
    head.cube("laurel", (-3.5, 30, -3.5), (7, 1, 7), LAUREL, inflate=0.9)
    arms = {}
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = arms[side] = body.bone(f"{side}_arm", (5 * s, 24, 0))
        arm.cube(f"{side}_sleeve", (x(4, 4), 15, -2), (4, 10, 4), ROBE, mirror=s == -1)
        arm.cube(f"{side}_hand", (x(4.5, 3), 13, -1.5), (3, 2, 3), SKIN, mirror=s == -1)
    arms["right"].cube("scroll", (5, 12, -4.5), (2, 2, 6), SCROLL)

    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # floating: the whole figure bobs, the robe sways after it
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("body", [(0, (0, 0, 0)), (2, (0, 1, 0)), (4, (0, 0, 0))])
    idle.rot("skirt", [(0, (2, 0, 0)), (2, (-2, 0, 0)), (4, (2, 0, 0))])
    idle.rot("head", [(0, ZERO), (1.5, (0, 8, 0)), (2.5, (0, 8, 0)), (4, ZERO)])
    idle.rot("right_arm", [(0, (-20, 0, 0)), (2, (-24, 0, 0)), (4, (-20, 0, 0))])
    idle.rot("left_arm", [(0, (0, 0, -4)), (2, (0, 0, -7)), (4, (0, 0, -4))])

    # gliding: the robe streams back, the arms barely move
    walk = m.animation("walk", 1.6, loop=True)
    walk.rot("skirt", [(0, (16, 0, 0)), (0.8, (12, 0, 0)), (1.6, (16, 0, 0))])
    walk.rot("body", [(0, (4, 0, 0)), (0.8, (6, 0, 0)), (1.6, (4, 0, 0))])
    walk.rot("left_arm", [(0, (8, 0, 0)), (0.8, (-8, 0, 0)), (1.6, (8, 0, 0))])

    # speaking: an open hand raised, a nod
    talk = m.animation("talk", 2.0)
    talk.rot("left_arm", [(0, ZERO), (0.4, (-70, 0, -15)), (1.2, (-60, 0, -25)), (2, ZERO)])
    talk.rot("head", [(0, ZERO), (0.5, (10, 0, 0)), (0.8, (-4, 0, 0)), (1.2, (8, 0, 0)), (2, ZERO)])
    talk.rot("body", [(0, ZERO), (0.6, (4, 0, 0)), (2, ZERO)])


if __name__ == "__main__":
    build().write()
