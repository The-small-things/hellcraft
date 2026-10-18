#!/usr/bin/env python3
"""A shade of Limbo: one of the great souls who live on in the Noble Castle (Homer, Aristotle, Saladin...), drawn
pale and drained of colour like everything in Limbo, save the green laurel on their brows. They float a little
above the ground, their robes trailing into nothing, and the client draws them see-through. They talk, trade and
hand out Codex pages (entity/npc/Shade).

A philosopher of old: a long tunic belted with a cord, a mantle (himation) thrown over the left shoulder, crossing
the chest and hanging down the back, a skirt that flares and trails into a fading hem; a calm face with a straight
nose under the brow, a layered beard tapering to a point, curled hair and a wreath of laurel leaves round the head.
Wide sleeves end in cuffs over hands with fingers and a thumb; the right hand holds a scroll tied with a ribbon.

Run from the repo root:  python3 tools/art/shade.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="d6d2c6", dark="a8a498")
ROBE = material("robe", "robe", base="c6c8c4", fold="8e908c")
MANTLE = material("mantle", "robe", base="b4b8b6", fold="7e8280", trim="d8dad4")
HEM = material("hem", "robe", base="a4a8a6", fold="72767a")
HAIR = material("hair", "hair", light="eeeee6", dark="b8b8b0")
LAUREL = material("laurel", "hair", light="7cbc4a", dark="3e7a24")
SCROLL = material("scroll", "flat", base="e2d6b0")
CORD = material("cord", "flat", base="9a9c98")
RIBBON = material("ribbon", "flat", base="8c94a4")

EYE = hexc("cfe8ff")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def side_x(a, w, s):
    """A cube from a to a + w on the right becomes -(a + w) to -a on the left."""
    return a if s == 1 else -(a + w)


def mul(face, fx, fy, f):
    """Darkens (f < 1) or lightens one pixel of a face that is already painted."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def ao(bottom=0, top=0, dark=0.7, faces=SIDES):
    """The side faces shaded along their lowest rows and their highest (where a part tucks under the one above)."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            for i in range(min(bottom, f.h)):
                for fx in range(f.w):
                    mul(f, fx, f.h - 1 - i, dark + (1 - dark) * i / bottom)
            for i in range(min(top, f.h)):
                for fx in range(f.w):
                    mul(f, fx, i, dark + (1 - dark) * i / top)
    return detail


def both(*details):
    def detail(fs):
        for d in details:
            d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def face(faces):
    """A calm, wise face: pale eyes that glow softly in the shadow of the brow, a lit cheekbone under each, the lines
    of age at the corners of the eyes, the mouth hidden in the beard."""
    f = faces["front"]
    dark = SKIN.c["dark"]
    for cx in (1, f.w - 2):
        f.px(cx, 3, shade(dark, 0.8))
        f.glow(cx, 4, EYE)
        f.px(cx, 5, shade(SKIN.c["base"], 1.06))
        f.px(cx + (-1 if cx < f.w // 2 else 1), 4, shade(dark, 0.9))
    for x in range(f.w):
        mul(f, x, 1, 0.94)          # a line across the brow
    for name in ("left", "right"):
        s = faces[name]
        for y in range(3, 5):
            mul(s, s.w - 1 if name == "right" else 0, y, 0.85)


def brow(fs):
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.7))
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.06)


def nose(fs):
    f = fs["front"]
    f.px(0, 0, shade(SKIN.c["base"], 1.08))
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.7))
    for name in ("left", "right"):
        mul(fs[name], 0, fs[name].h - 1, 0.85)


def ear(fs):
    for name in ("left", "right"):
        mul(fs[name], 0, 0, 0.8)


def beard(fs):
    """A long beard: strands running down it, darker toward the tip and between the locks."""
    for name in SIDES + ("bottom",):
        f = fs[name]
        for x in range(f.w):
            mul(f, x, 0, 1.08)
            if x % 2:
                for y in range(f.h):
                    mul(f, x, y, 0.93)


def curls(fs):
    """Hair in tight curls: little shaded rings over the whole of it."""
    for name in SIDES + ("top",):
        f = fs[name]
        for y in range(0, f.h, 2):
            for x in range((y // 2) % 2, f.w, 2):
                mul(f, x, y, 0.86)


def leaves(fs):
    """Laurel leaves: pointed, lit at the midrib, dark between."""
    for name in SIDES + ("top",):
        f = fs[name]
        for x in range(f.w):
            for y in range(f.h):
                if (x + y) % 3 == 0:
                    mul(f, x, y, 1.18)
                elif (x + 2 * y) % 4 == 3:
                    mul(f, x, y, 0.7)


def neck(fs):
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.8)


def tunic(fs):
    """The tunic's front: a neckline at the top, soft vertical folds."""
    f = fs["front"]
    mid = f.w // 2
    f.px(mid, 0, shade(SKIN.c["base"], 0.95))
    f.px(mid - 1, 0, shade(ROBE.c["fold"], 0.9))
    f.px(mid + 1, 0, shade(ROBE.c["fold"], 0.9))
    for y in range(1, f.h):
        mul(f, mid, y, 0.9)


def drape(fs):
    """The mantle's folds: heavy diagonal creases and a lit edge."""
    for name in SIDES:
        f = fs[name]
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 4 == 0:
                    mul(f, x, y, 0.84)
        for y in range(f.h):
            mul(f, 0, y, 1.12)


def cord(fs):
    """A cord belt, twisted, its knot at the front."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.12 if x % 2 else 0.85)
    f.px(f.w // 2, 0, shade(CORD.c["base"], 0.7))


def skirt_folds(fs):
    """The long skirt falling in deep folds, with the shadow of the waist above."""
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            if x % 3 == 1:
                for y in range(f.h):
                    mul(f, x, y, 0.82)
            elif x % 3 == 2:
                for y in range(f.h):
                    mul(f, x, y, 1.06)


def hem_fade(faces):
    """The robe trails into nothing: the bottom rows grow faint (and glow a little, as a soul does)."""
    for name in ("front", "back", "left", "right", "bottom"):
        f = faces[name]
        rows = range(f.h) if name == "bottom" else range(max(0, f.h - 2), f.h)
        for y in rows:
            for x in range(f.w):
                if name == "bottom" or y == f.h - 1 or (x + y) % 2 == 0:
                    f.glow(x, y, hexc("b8c8d8"))


def cuff(fs):
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.8)
            mul(f, x, 0, 1.1)


def fingers(fs):
    """Long fingers: the gaps between them."""
    for name in ("front", "back"):
        f = fs[name]
        for x in range(1, f.w, 2):
            for y in range(f.h):
                mul(f, x, y, 0.85)


def scroll_detail(fs):
    """A rolled scroll: the spiral of the paper at each end, lines of writing along it."""
    for name in ("front", "back"):
        f = fs[name]
        f.fill(lambda x, y: SCROLL.c["base"])
        f.px(0, 0, shade(SCROLL.c["base"], 0.7))
        f.px(f.w - 1, f.h - 1, shade(SCROLL.c["base"], 0.6))
    for name in ("left", "right", "top", "bottom"):
        f = fs[name]
        for x in range(f.w):
            if x % 2:
                f.px(x, 0, shade(SCROLL.c["base"], 0.78))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("shade", texture_width=128)
    # no legs: the whole figure floats, its robe trailing
    body = m.bone("body", (0, 14, 0))
    body.cube("chest", (-3.5, 18, -2), (7, 6, 4), ROBE, detail=both(tunic, ao(bottom=1, dark=0.85)))
    body.cube("shoulders", (-4.5, 22, -2.2), (9, 2, 4), ROBE, detail=ao(top=1, dark=0.9))
    body.cube("waist", (-3, 14, -1.8), (6, 4, 4), ROBE, detail=tunic)
    body.cube("belt", (-3.5, 15.5, -2.2), (7, 1, 5), CORD, detail=cord)
    body.cube("neck", (-1, 23.5, -1), (2, 2, 2), SKIN, detail=neck)
    # the mantle: over the left shoulder, across the chest to the right hip, and down the back
    body.cube("mantle_shoulder", (-4.8, 21, -2.6), (3, 3, 5), MANTLE, detail=drape)
    body.cube("mantle_front", (-1.5, 13.5, -2.7), (3, 11, 1), MANTLE, rotation=(0, 0, 34), origin=(0, 19, -2.2), detail=drape)
    body.cube("mantle_back", (-4.4, 12, 1.7), (4, 11, 1), MANTLE, rotation=(0, 0, -6), origin=(-2.4, 23, 2), detail=drape)

    skirt = body.bone("skirt", (0, 14, 0))
    skirt.cube("skirt", (-4, 9, -2.5), (8, 5, 5), ROBE, detail=both(skirt_folds, ao(top=1, dark=0.8)))
    skirt.cube("skirt_lower", (-4.5, 4, -3), (9, 5, 6), ROBE, detail=skirt_folds)
    skirt.cube("mantle_fall", (-4.7, 6.5, -3.3), (6, 7, 1), MANTLE, rotation=(0, 0, 8), origin=(-1.7, 13, -3), detail=drape)
    skirt.cube("hem", (-4, 1, -2.5), (8, 3, 5), HEM, detail=hem_fade)
    skirt.cube("trail", (-2.5, 0.2, 0.5), (5, 3, 3), HEM, rotation=(-22, 0, 0), origin=(0, 3, 1), detail=hem_fade)

    head = body.bone("head", (0, 25, 0))
    head.cube("head", (-3, 25, -3), (6, 7, 6), SKIN, detail=face)
    head.cube("brow", (-3, 29.2, -3.5), (6, 1, 1), SKIN, detail=brow)
    head.cube("nose", (-0.5, 27, -3.8), (1, 2, 1), SKIN, rotation=(-8, 0, 0), origin=(0, 29, -3), detail=nose)
    head.cube("right_ear", (2.8, 27.5, -0.8), (1, 2, 1), SKIN, detail=ear)
    head.cube("left_ear", (-3.8, 27.5, -0.8), (1, 2, 1), SKIN, mirror=True, detail=ear)
    head.cube("hair", (-3, 31, -3), (6, 2, 6), HAIR, inflate=0.3, detail=curls)
    head.cube("hair_back", (-3, 26.5, 2.1), (6, 5, 1), HAIR, inflate=0.3, detail=curls)
    head.cube("right_hair", (2.6, 28.5, -2.4), (1, 3, 4), HAIR, inflate=0.2, detail=curls)
    head.cube("left_hair", (-3.6, 28.5, -2.4), (1, 3, 4), HAIR, mirror=True, inflate=0.2, detail=curls)
    # the beard in layers, tapering to a point on the chest
    head.cube("moustache", (-2, 26.1, -3.7), (4, 1, 1), HAIR, detail=beard)
    head.cube("beard", (-3, 24, -3.5), (6, 3, 2), HAIR, inflate=0.1, detail=beard)
    head.cube("beard_lower", (-2, 22.2, -3.4), (4, 2, 2), HAIR, detail=beard)
    head.cube("beard_tip", (-1, 20.8, -3.2), (2, 2, 1), HAIR, detail=beard)
    # the laurel wreath: a band of leaves round the head, sprigs rising at the front
    head.cube("laurel", (-3, 30.4, -3.5), (6, 1, 1), LAUREL, inflate=0.3, detail=leaves)
    head.cube("laurel_back", (-3, 30.4, 2.5), (6, 1, 1), LAUREL, inflate=0.3, detail=leaves)
    head.cube("laurel_right", (2.9, 30.4, -3), (1, 1, 6), LAUREL, inflate=0.3, detail=leaves)
    head.cube("laurel_left", (-3.9, 30.4, -3), (1, 1, 6), LAUREL, mirror=True, inflate=0.3, detail=leaves)
    head.cube("sprig_right", (0.4, 31, -3.9), (2, 1, 1), LAUREL, rotation=(0, 0, 25), origin=(0.4, 31, -3.4), detail=leaves)
    head.cube("sprig_left", (-2.4, 31, -3.9), (2, 1, 1), LAUREL, mirror=True, rotation=(0, 0, -25), origin=(-0.4, 31, -3.4),
              detail=leaves)

    arms = {}
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mir = s == -1
        arm = body.bone(f"{side}_arm", (5 * s, 24, 0))
        arm.cube(f"{side}_sleeve", (x(4, 3), 18.5, -1.6), (3, 6, 3), ROBE, mirror=mir, inflate=0.15, detail=skirt_folds)
        # the elbow bends a little at rest: the right more, to hold the scroll before him
        fore = arms[side] = arm.bone(f"{side}_forearm", (5.5 * s, 19, 0), rotation=(35 if s == 1 else 10, 0, 0))
        fore.cube(f"{side}_forearm", (x(4, 3), 15.5, -1.5), (3, 4, 3), ROBE, mirror=mir, detail=skirt_folds)
        fore.cube(f"{side}_cuff", (x(3.6, 4), 14, -2), (4, 2, 4), HEM, mirror=mir, detail=cuff)
        fore.cube(f"{side}_hand", (x(4.5, 2), 12.2, -1), (2, 2, 2), SKIN, mirror=mir)
        fore.cube(f"{side}_fingers", (x(4.5, 2), 11.2, -1.2), (2, 1, 2), SKIN, mirror=mir, detail=fingers)
        fore.cube(f"{side}_thumb", (x(4.1, 1), 12.4, -1.6), (1, 1, 1), SKIN, mirror=mir)
    # the scroll, rolled and tied, held in the right hand
    hand = arms["right"]
    hand.cube("scroll", (4.5, 11.8, -4.4), (2, 2, 6), SCROLL, detail=scroll_detail)
    hand.cube("scroll_ribbon", (4.5, 11.8, -2.4), (2, 2, 1), RIBBON, inflate=0.12)
    hand.cube("scroll_knob_front", (5, 12.3, -5), (1, 1, 1), CORD)
    hand.cube("scroll_knob_back", (5, 12.3, 1.6), (1, 1, 1), CORD)

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

    # speaking: an open hand raised before him, a nod
    talk = m.animation("talk", 2.0)
    talk.rot("left_arm", [(0, ZERO), (0.4, (60, 0, -15)), (1.2, (50, 0, -25)), (2, ZERO)])
    talk.rot("left_forearm", [(0, ZERO), (0.4, (40, 0, 0)), (1.2, (30, 0, 0)), (2, ZERO)])
    talk.rot("head", [(0, ZERO), (0.5, (10, 0, 0)), (0.8, (-4, 0, 0)), (1.2, (8, 0, 0)), (2, ZERO)])
    talk.rot("body", [(0, ZERO), (0.6, (4, 0, 0)), (2, ZERO)])


if __name__ == "__main__":
    build().write()
