#!/usr/bin/env python3
"""The mimics (Malebolge's monster, part of Fraud's rule "nothing is what it seems"): chests that aren't. Shut, one
looks like any wooden chest on the ridges of the evil ditches; now and then its lid lifts a crack and settles, as if
breathing (the special animation, the tell). Come close or touch it and it gapes: a ring of teeth, a long tongue,
and it hops after you.

Run from the repo root:  python3 tools/art/mimic.py && python3 tools/gen_bosses.py

Shut, it is a sturdy plank chest: boards with dark seams, iron-capped corners, a hasp with a keyhole, hinges at the
back and a handle on each side. The box is hollow and the lid solid, so everything that gives it away hides inside
when it is shut: the lower teeth stand up from the rim into the lid, the upper teeth hang from the lid into the box.
Open, it is a mouth: wet gums, two rows of crooked teeth, eyes on the inside of the lid (not lit: nothing about a
mimic glows), and a long tongue that curls (its tip is a bone of its own).

The lid opens upward (positive X about its hinge at the back).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

WOOD = material("wood", "flat", base="9c6a2c")
TRIM = material("trim", "flat", base="5a3a14")
IRON = material("iron", "metal", base="5a5a5e", high="9a9a9e", low="2e2e32")
GUM = material("gum", "skin", base="6a1a1a", dark="3a0a0a")
TONGUE = material("tongue", "skin", base="b04a5a", dark="6a2030")
TOOTH_M = material("tooth", "horn", root="d8ccb0", tip="f4eedc")
TOOTH = hexc("ece4d0")
EYE = hexc("e8d040")
PUPIL = hexc("140808")
SIDES = ("front", "back", "left", "right")


def planks(faces):
    """Chest boards: horizontal planks with dark seams and grain, and a darker frame round every side."""
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                c = WOOD.c["base"]
                if name not in ("top", "bottom") and y % 4 == 3:
                    c = shade(c, 0.72)
                if name in ("top", "bottom") and x % 5 == 4:
                    c = shade(c, 0.78)
                if (x * 3 + y * 11 + len(name)) % 13 == 0:
                    c = shade(c, 0.88)  # grain
                if x == 0 or y == 0 or x == f.w - 1 or y == f.h - 1:
                    c = TRIM.c["base"]
                f.px(x, y, shade(c, 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.02))


def wall(inner):
    """A wall of the box: planks outside; inside (the face named inner) flesh, darker toward the floor."""
    def detail(faces):
        planks(faces)
        f = faces[inner]
        for y in range(f.h):
            for x in range(f.w):
                f.px(x, y, shade(GUM.c["base"], 0.75 - 0.35 * y / max(1, f.h - 1) + 0.05 * ((x + y) % 2)))
        faces["top"].fill(lambda x, y: shade(TRIM.c["base"], 0.9))
    return detail


def gums(faces):
    """The inside of the mouth: wet dark gums, ridged."""
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                f.px(x, y, shade(GUM.c["base"], 1.0 - 0.2 * ((x + y) % 3 == 0) + (0.15 if (x * 5 + y * 3) % 11 == 0 else 0)))


def palate(faces):
    """The lid: planks outside; underneath, the roof of the mouth, with two eyes in it."""
    planks(faces)
    f = faces["bottom"]
    for y in range(f.h):
        for x in range(f.w):
            f.px(x, y, shade(GUM.c["base"], 0.85 - 0.15 * ((x + 2 * y) % 3 == 0)))
    cy = f.h // 2 - 1
    for cx in (3, f.w - 6):
        for dx in range(3):
            for dy in range(2):
                f.px(cx + dx, cy + dy, EYE)
        f.px(cx + 1, cy, PUPIL)
        f.px(cx + 1, cy + 1, PUPIL)
        for dx in range(-1, 4):
            f.px(cx + dx, cy - 1, shade(GUM.c["dark"], 0.7))
            f.px(cx + dx, cy + 2, shade(GUM.c["dark"], 0.7))


def keyhole(faces):
    f = faces["front"]
    f.px(f.w // 2, 1, (16, 12, 10))
    f.px(f.w // 2, 2, (16, 12, 10))


def tongue_detail(faces):
    """A groove down the middle of the tongue."""
    t = faces["top"]
    for y in range(t.h):
        t.px(t.w // 2, y, shade(TONGUE.c["dark"], 0.9))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("mimic", texture_width=128)
    body = m.bone("body", (0, 0, 0))
    # the box: a floor and four walls, hollow, so the teeth hide inside it when it is shut
    body.cube("base", (-7, 0, -7), (14, 1, 14), WOOD, detail=planks)
    body.cube("wall_front", (-7, 1, -7), (14, 9, 1), WOOD, detail=wall("back"))
    body.cube("wall_back", (-7, 1, 6), (14, 9, 1), WOOD, detail=wall("front"))
    body.cube("wall_right", (6, 1, -6), (1, 9, 12), WOOD, detail=wall("left"))
    body.cube("wall_left", (-7, 1, -6), (1, 9, 12), WOOD, detail=wall("right"))
    body.cube("lower_jaw", (-6, 7.5, -6), (12, 1, 12), GUM, detail=gums)
    for i, (cx, cz) in enumerate(((-7, -7), (5, -7), (-7, 5), (5, 5))):
        body.cube(f"corner_{i}", (cx, 0, cz), (2, 10, 2), IRON, inflate=0.06)
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_handle", (7 * s - 0.5, 6.5, -2), (1, 1, 4), IRON, inflate=-0.1)
    body.cube("hinge_right", (2.5, 8, 6.6), (2, 2, 1), IRON)
    body.cube("hinge_left", (-4.5, 8, 6.6), (2, 2, 1), IRON)
    # the lower teeth, crooked, standing up from the rim (inside the lid when it is shut)
    for i, x in enumerate((-5, -2.8, -0.5, 1.8, 4)):
        body.cube(f"tooth_low_front_{i}", (x, 8.5, -5.9 + 0.2 * (i % 2)), (1, 2 + (i % 2), 1), TOOTH_M,
                  rotation=(-8 + 4 * (i % 3), 0, 6 - 4 * (i % 4)), origin=(x + 0.5, 9.5, -5.4))
    for side, s in (("right", 1), ("left", -1)):
        for i, z in enumerate((-3, 0.5, 3.5)):
            body.cube(f"tooth_low_{side}_{i}", (5 * s - 0.5 + (-0.1 * s), 8.5, z), (1, 2, 1), TOOTH_M,
                      rotation=(0, 0, -8 * s), origin=(5 * s, 9.5, z + 0.5))
    tongue = body.bone("tongue", (0, 9, 4))
    tongue.cube("tongue", (-2, 8.5, -1), (4, 1, 5), TONGUE, detail=tongue_detail)
    tongue.cube("tongue_mid", (-1.5, 8.6, -5), (3, 1, 4), TONGUE, detail=tongue_detail)
    tip = tongue.bone("tongue_tip", (0, 9, -5))
    tip.cube("tongue_tip", (-1, 8.7, -8), (2, 1, 3), TONGUE, detail=tongue_detail)
    tip.cube("tongue_fork_right", (0.2, 8.8, -9.2), (1, 1, 2), TONGUE, inflate=-0.15)
    tip.cube("tongue_fork_left", (-1.2, 8.8, -9.2), (1, 1, 2), TONGUE, inflate=-0.15)
    # the lid, hinged at the back: solid, its underside the roof of the mouth
    lid = body.bone("lid", (0, 10, 7))
    lid.cube("lid", (-7, 10, -7), (14, 5, 14), WOOD, detail=palate)
    for i, (cx, cz) in enumerate(((-7, -7), (5, -7), (-7, 5), (5, 5))):
        lid.cube(f"lid_corner_{i}", (cx, 10, cz), (2, 5, 2), IRON, inflate=0.06)
    lid.cube("lid_band", (-1, 10, -7), (2, 5, 14), IRON, inflate=0.04)
    lid.cube("latch", (-1, 8, -7.8), (2, 4, 1), IRON, detail=keyhole)
    lid.cube("hinge_leaf_right", (2.5, 10, 6.6), (2, 2, 1), IRON)
    lid.cube("hinge_leaf_left", (-4.5, 10, 6.6), (2, 2, 1), IRON)
    # the upper teeth, hanging from the roof of the mouth (inside the box when it is shut)
    for i, x in enumerate((-4, -1.6, 0.6, 3)):
        lid.cube(f"tooth_high_front_{i}", (x, 8 - (i % 2), -5.9), (1, 2 + (i % 2), 1), TOOTH_M,
                 rotation=(8 - 4 * (i % 3), 0, -6 + 4 * (i % 4)), origin=(x + 0.5, 9.8, -5.4))
    for side, s in (("right", 1), ("left", -1)):
        for i, z in enumerate((-1.5, 2)):
            lid.cube(f"tooth_high_{side}_{i}", (5 * s - 0.5 + (-0.1 * s), 8, z), (1, 2, 1), TOOTH_M,
                     rotation=(0, 0, 8 * s), origin=(5 * s, 9.8, z + 0.5))
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # shut and still: exactly a chest
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("lid", [(0, ZERO), (2.0, ZERO)])

    # hopping after you, jaws working, the tongue flapping
    walk = m.animation("walk", 0.6, loop=True)
    walk.pos("body", [(0, ZERO), (0.15, (0, 4, 0)), (0.3, ZERO), (0.45, (0, 4, 0)), (0.6, ZERO)])
    walk.rot("lid", [(0, (20, 0, 0)), (0.15, (45, 0, 0)), (0.3, (20, 0, 0)), (0.45, (45, 0, 0)), (0.6, (20, 0, 0))])
    walk.rot("tongue", [(0, (10, 0, 0)), (0.3, (25, 10, 0)), (0.6, (10, 0, 0))])
    walk.rot("tongue_tip", [(0, (-20, 0, 0)), (0.15, (-50, 0, 0)), (0.3, (-20, 0, 0)), (0.45, (-50, 0, 0)), (0.6, (-20, 0, 0))])

    # gape wide, lunge, snap shut
    attack = m.animation("attack", 0.6)
    attack.rot("lid", [(0, (20, 0, 0)), (0.2, (85, 0, 0)), (0.32, (0, 0, 0)), (0.6, (20, 0, 0))])
    attack.pos("body", [(0, ZERO), (0.2, (0, 2, -2)), (0.32, (0, 0, -5)), (0.6, ZERO)])
    attack.rot("tongue", [(0, ZERO), (0.2, (40, 0, 0)), (0.32, ZERO)])
    attack.rot("tongue_tip", [(0, ZERO), (0.2, (-30, 0, 0)), (0.32, ZERO)])
    attack.event(0.32, "bite")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (-10, 0, 6)), (0.3, ZERO)])
    hurt.rot("lid", [(0, ZERO), (0.1, (30, 0, 0)), (0.3, ZERO)])

    # the tell: the lid lifts a crack and settles, as if breathing
    breathe = m.animation("breathe", 1.6)
    breathe.rot("lid", [(0, ZERO), (0.5, (8, 0, 0)), (0.9, (8, 0, 0)), (1.3, (0, 0, 0)), (1.6, ZERO)])
    breathe.rot("tongue", [(0, ZERO), (0.6, (-10, 0, 0)), (1.3, ZERO)])

    # dies: the lid flies open, it topples onto its side, the tongue lolls out
    death = m.animation("death", 1.0)
    death.rot("lid", [(0, ZERO), (0.3, (110, 0, 0)), (1.0, (100, 0, 0))])
    death.rot("body", [(0, ZERO), (0.6, (0, 0, 25)), (1.0, (0, 0, 90))])
    death.pos("body", [(0, ZERO), (0.6, (1.5, 1, 0)), (1.0, (7, 7, 0))])
    death.rot("tongue", [(0, ZERO), (0.3, (60, 0, 0)), (1.0, (70, 0, 0))])
    death.rot("tongue_tip", [(0, ZERO), (0.3, (-40, 0, 0)), (1.0, (-70, 0, 0))])


if __name__ == "__main__":
    build().write()
