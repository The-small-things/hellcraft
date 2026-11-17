#!/usr/bin/env python3
"""Antaeus remade at twice the detail (drawn at half AntaeusEntity.SCALE: he is still about 28 blocks tall, so every
model pixel is now a quarter of a block, not half). The old body is carried over part for part with a finer grain
(tools/art/legacy/antaeus.py through remake_kit's port), so the hand's path and the palm the riders stand on are
exactly where they were; new are his face (lamp eyes deep under a split brow, a broken nose, lips in the beard,
scars), a belly of six muscles, five toes with nails on each frozen foot, knuckles on the lion's paws and teeth in
its mouth, and more hair.

    python3 tools/art/remake/antaeus.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, shade  # noqa: E402
from paintkit import ao, fill, hline, line, tint, tone, tone_all, tone_rect, tuck, vline  # noqa: E402

SKIN = material("skin", "skin", base="7a5a42", dark="4a3426")
HAIR = material("hair", "hair", light="5e4632", dark="1a120c")
LION = material("lion", "hair", light="b08a40", dark="5a4018")
NAIL = material("nail", "flat", base="b8a088")
TOOTH = material("tooth", "flat", base="e0d8c0")
FROST = hexc("c8e8ff")
RIME = hexc("e8f6ff")
EYE = hexc("ffd890")
EYE_HOT = hexc("fff6d8")
SOCKET = hexc("24160e")
LIP = hexc("4a2a20")
SCAR = hexc("a88270")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def frost(fs):
    """Rime all over a part that stands in the ice."""
    for n, f in fs.items():
        for y in range(f.h):
            for x in range(f.w):
                if f.rng.random() < 0.85:
                    tint(f, x, y, RIME if f.rng.random() < 0.3 else FROST, 0.55 + 0.25 * f.rng.random())


def face(fs):
    """His face: deep sockets with eyes like lamps, crow's feet, broad cheeks, scars, the hollow of each temple."""
    f = fs["front"]
    w = f.w
    tone_rect(f, 0, 3, w, 3, 0.6)   # the brow's shadow
    for x0 in (1, w - 7):
        f.rect(x0, 6, 6, 4, SOCKET)
        for x in range(x0 + 1, x0 + 5):
            f.glow(x, 7, EYE)
            f.glow(x, 8, shade(EYE, 0.85))
        f.glow(x0 + 2, 7, EYE_HOT)
        f.glow(x0 + 3, 7, EYE_HOT)
        tone_rect(f, x0, 10, 6, 1, 0.7)
        tone_rect(f, x0 + 1, 11, 4, 1, 0.85)
    for y in range(6, 13):
        tone(f, 0, y, 0.75)
        tone(f, w - 1, y, 0.75)
    for x in (3, 4, w - 5, w - 4):
        tone(f, x, 12, 1.14)   # cheekbones catch the light
        tone(f, x, 14, 0.85)
    line(f, 5, 11, 4, 15, k=0.72)
    line(f, w - 6, 11, w - 5, 15, k=0.72)
    line(f, w - 5, 1, w - 3, 13, colour=SCAR, t=0.6)   # a scar down through his left eye
    for y in (1, 2):
        for x in range(3, w - 3):
            if (x + y * 3) % 7:
                tone(f, x, y, 0.86)   # forehead lines
    for n in ("left", "right"):
        s = fs[n]
        front = s.w - 1 if n == "right" else 0
        for y in range(5, 11):
            for i in range(4):
                tone(s, front - i if n == "right" else front + i, y, 0.8 + 0.04 * i)
    tuck(fs, 2, 0.75)


def brow(fs):
    tone_all(fs["bottom"], 0.5)
    f = fs["front"]
    hline(f, f.h - 1, 0.7)
    hline(f, 0, 1.1)


def nose(fs):
    f = fs["front"]
    vline(f, 0, 1.12)
    vline(f, f.w - 1, 0.8)
    hline(f, f.h // 2, 0.8)   # where it was broken
    for n in ("left", "right"):
        tone_all(fs[n], 0.82)


def nostrils(fs):
    b = fs["bottom"]
    fill(b, shade(SKIN.c["dark"], 0.7), 0.04)
    b.rect(0, 0, 2, b.h - 1, SOCKET)
    b.rect(b.w - 2, 0, 2, b.h - 1, SOCKET)
    tuck(fs, 1, 0.75, 1.1)


def lips(fs):
    f = fs["front"]
    fill(f, LIP, 0.05)
    hline(f, 0, 0.6)
    hline(f, f.h - 1, 1.2)


def ab(fs):
    tuck(fs, 1, 0.68, 1.12)
    f = fs["front"]
    vline(f, 0, 0.8)
    vline(f, f.w - 1, 0.8)


def toe(fs):
    frost(fs)
    f = fs["front"]
    f.rect(0, 0, f.w, 2, NAIL.c["base"])
    hline(f, 2, 0.6)
    t = fs["top"]
    hline(t, t.h // 2, 0.7)
    for y in range(t.h - 2, t.h):
        for x in range(1, t.w - 1):
            t.px(x, y, shade(NAIL.c["base"], 1.05 if y == t.h - 2 else 0.9))


def fang(fs):
    for n in ("front", "left", "right", "back"):
        hline(fs[n], fs[n].h - 1, 0.8)


def build():
    b = kit.Remake("antaeus", tex_width=512)
    b.port("antaeus", skip=("head", "brow", "nose", "right_cheek", "left_cheek", "moustache", "right_toes", "left_toes",
                            "belly"))

    # the head
    b.cube("head", "head", (-9, 90, -10), (18, 20, 20), SKIN, detail=face)
    for side, s in SIDES:
        b.cube("head", f"brow_{side}", (X(0.6, 9, s), 102, -12), (9, 3, 3), SKIN, rotation=(0, 0, 9 * s),
               origin=(0.6 * s, 104, -10), detail=brow)
        b.cube("head", f"eyebrow_{side}", (X(1.2, 8, s), 104.4, -12.4), (8, 2, 2), HAIR, rotation=(0, 0, 9 * s),
               origin=(0.6 * s, 104, -10))
        b.cube("head", f"{side}_cheek", (X(4, 5, s), 96, -11.2), (5, 3, 2), SKIN, detail=ao(1, 0, 0.8))
        b.cube("head", f"moustache_{side}", (X(0.4, 7, s), 92.6, -12.8), (7, 3, 2), HAIR, rotation=(0, 0, 14 * s),
               origin=(0.4 * s, 95, -12))
        b.cube("head", f"nostril_{side}", (X(1.6, 3, s), 95, -12.6), (3, 3, 2), SKIN, detail=nostrils)
        b.cube("head", f"temple_hair_{side}", (X(8.4, 2, s), 100, -8), (2, 9, 8), HAIR)
    b.stick("head", "nose", (0, 103.6, -10.6), (0, 96.6, -13.4), 4, SKIN, depth=3, detail=nose)
    b.cube("head", "nose_tip", (-2.5, 95.2, -14.4), (5, 3, 4), SKIN, detail=nostrils)
    b.cube("head", "lips", (-4, 91.4, -11.2), (8, 2, 2), SKIN, detail=lips)
    b.cube("head", "lower_teeth", (-3, 91.0, -10.8), (6, 1, 1), TOOTH, detail=fang)
    b.cube("head", "forelock_tuft", (-3, 107, -12.6), (7, 4, 2), HAIR)
    b.cube("head", "hair_top", (-6, 113, -8), (12, 2, 12), HAIR)

    # a belly of six muscles
    for row, y in enumerate((53.4, 57.6, 61.8)):
        for side, s in SIDES:
            b.cube("body", f"ab_{row}_{side}", (X(0.5, 7, s), y, -11), (7, 4, 2), SKIN, detail=ab)

    # the lion's skin: teeth in its mouth, claws on its paws
    for side, s in SIDES:
        b.cube("body", f"lion_fang_{side}", (X(1.4, 1, s), 34.4, -17.6), (1, 3, 1), TOOTH, detail=fang)
        b.cube("body", f"lion_ear_{side}", (X(4, 3, s), 45, -16), (3, 3, 2), LION)
        for k in range(3):
            b.cube("body", f"lion_claw_{side}_{k}", (X(9 + k * 1.4, 1, s), 26, -13.6), (1, 3, 1), NAIL)

    # the feet: five toes each, nails and all, rimed over
    for side, s in SIDES:
        leg = f"{side}_leg"
        x0 = 3.0   # from the inside of the foot outward: the big toe first
        for k, (w, length) in enumerate(((5, 8), (3, 6), (3, 6), (3, 5), (2, 4))):
            b.cube(leg, f"{side}_toe_{k}", (X(x0, w, s), 0, -6.5 - length), (w, 4 if k == 0 else 3, length), SKIN, detail=toe)
            x0 += w + 0.2
    b.finish()


if __name__ == "__main__":
    build()
