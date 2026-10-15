#!/usr/bin/env python3
"""The mimics (Malebolge's monster, part of Fraud's rule "nothing is what it seems"): chests that aren't. Shut, one
looks like any wooden chest on the ridges of the evil ditches; now and then its lid lifts a crack and settles, as if
breathing (the special animation, the tell). Come close or touch it and it gapes: a ring of teeth, a long tongue,
and it hops after you.

Run from the repo root:  python3 tools/art/mimic.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

WOOD = material("wood", "flat", base="9c6a2c")
TRIM = material("trim", "flat", base="5a3a14")
IRON = material("iron", "metal", base="8a8a8a", high="c8c8c8", low="4a4a4a")
GUM = material("gum", "skin", base="6a1a1a", dark="3a0a0a")
TONGUE = material("tongue", "skin", base="b04a5a", dark="6a2030")
TOOTH = hexc("ece4d0")


def planks(faces):
    """Chest boards: horizontal planks with dark seams, and a darker frame round every side."""
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                c = WOOD.c["base"]
                if name not in ("top", "bottom") and y % 4 == 3:
                    c = shade(c, 0.72)
                if name in ("top", "bottom") and x % 5 == 4:
                    c = shade(c, 0.78)
                if x == 0 or y == 0 or x == f.w - 1 or y == f.h - 1:
                    c = TRIM.c["base"]
                f.px(x, y, shade(c, 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.02))


def gums(faces):
    """The inside of the jaws: dark gums with a row of teeth on the outer edges."""
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                f.px(x, y, shade(GUM.c["base"], 1.0 - 0.2 * ((x + y) % 3 == 0)))
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            if x % 2 == 0:
                f.px(x, 0, TOOTH)
                f.px(x, f.h - 1, TOOTH)


def build():
    m = Model("mimic", texture_width=128)
    body = m.bone("body", (0, 0, 0))
    body.cube("base", (-7, 0, -7), (14, 10, 14), WOOD, detail=planks)
    body.cube("lower_jaw", (-6.5, 8, -6.5), (13, 2, 13), GUM, detail=gums)
    tongue = body.bone("tongue", (0, 9, 4))
    tongue.cube("tongue", (-2, 9, -5), (4, 1, 9), TONGUE)
    lid = body.bone("lid", (0, 10, 7))
    lid.cube("lid", (-7, 10, -7), (14, 5, 14), WOOD, detail=planks)
    lid.cube("upper_jaw", (-6.5, 9, -6.5), (13, 2, 13), GUM, detail=gums)
    lid.cube("latch", (-1, 8, -8), (2, 4, 1), IRON)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # shut and still: exactly a chest
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("lid", [(0, ZERO), (2.0, ZERO)])

    # hopping after you, jaws working
    walk = m.animation("walk", 0.6, loop=True)
    walk.pos("body", [(0, ZERO), (0.15, (0, 4, 0)), (0.3, ZERO), (0.45, (0, 4, 0)), (0.6, ZERO)])
    walk.rot("lid", [(0, (-20, 0, 0)), (0.15, (-45, 0, 0)), (0.3, (-20, 0, 0)), (0.45, (-45, 0, 0)), (0.6, (-20, 0, 0))])
    walk.rot("tongue", [(0, (10, 0, 0)), (0.3, (25, 10, 0)), (0.6, (10, 0, 0))])

    # gape wide, lunge, snap shut
    attack = m.animation("attack", 0.6)
    attack.rot("lid", [(0, (-20, 0, 0)), (0.2, (-85, 0, 0)), (0.32, (0, 0, 0)), (0.6, (-20, 0, 0))])
    attack.pos("body", [(0, ZERO), (0.2, (0, 2, -2)), (0.32, (0, 0, -5)), (0.6, ZERO)])
    attack.rot("tongue", [(0, ZERO), (0.2, (40, 0, 0)), (0.32, ZERO)])
    attack.event(0.32, "bite")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (-10, 0, 6)), (0.3, ZERO)])
    hurt.rot("lid", [(0, ZERO), (0.1, (-30, 0, 0)), (0.3, ZERO)])

    # the tell: the lid lifts a crack and settles, as if breathing
    breathe = m.animation("breathe", 1.6)
    breathe.rot("lid", [(0, ZERO), (0.5, (-8, 0, 0)), (0.9, (-8, 0, 0)), (1.3, (0, 0, 0)), (1.6, ZERO)])
    breathe.rot("tongue", [(0, ZERO), (0.6, (-10, 0, 0)), (1.3, ZERO)])

    # wakes: the lid flies open, the tongue lolls out
    death = m.animation("death", 1.0)
    death.rot("lid", [(0, ZERO), (0.3, (-110, 0, 0)), (1.0, (-100, 0, 0))])
    death.rot("body", [(0, ZERO), (0.6, (0, 0, 25)), (1.0, (0, 0, 90))])
    death.pos("body", [(0, ZERO), (1.0, (0, -2, 0))])
    death.rot("tongue", [(0, ZERO), (0.3, (60, 0, 0)), (1.0, (70, 0, 0))])


if __name__ == "__main__":
    build().write()
