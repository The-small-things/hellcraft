#!/usr/bin/env python3
"""The Cyclops remade at twice the detail (drawn at half his old scale). His body is carried over part for part with
a finer grain (tools/art/legacy/cyclops.py through remake_kit's port); new is his head: one great bloodshot eye with
a burning iris and a slit pupil between heavy lids, a lumpy brow, a flat broad nose, a jaw with a row of teeth
between two tusks, warts, a torn ear.

    python3 tools/art/remake/cyclops.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, shade  # noqa: E402
from paintkit import ao, blot, fill, hline, line, speckle, tint, tone, tone_all, tone_rect, tuck, vline  # noqa: E402

SKIN = material("skin", "skin", base="8a6c56", dark="5a4232")
TUSK = material("tusk", "horn", root="d8cba4", tip="f2e8cc", edges=False)
TOOTH = material("tooth", "flat", base="d8cba4")
EYEBALL = material("eyeball", "flat", base="e6dcc4", edges=False)
EYE = hexc("ffb020")
EYE_HOT = hexc("fff0a0")
PUPIL = hexc("3a1000")
SOCKET = hexc("2a1a12")
MOUTH = hexc("2a0e08")
BLOOD = hexc("b05040")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def face(fs):
    """The skull's front: the dark socket round the one eye, a furrowed forehead, deep lines from nose to mouth,
    warts and old scars."""
    f = fs["front"]
    w, cx = f.w, f.w // 2
    for y in range(7, 17):
        for x in range(cx - 8, cx + 8):
            edge = y in (7, 16) or x in (cx - 8, cx + 7)
            f.px(x, y, shade(SKIN.c["dark"], 0.75) if edge else SOCKET)
    for y in (1, 3, 5):
        for x in range(2, w - 2):
            if (x + y) % 5:
                tone(f, x, y, 0.8)
    line(f, cx - 6, 16, cx - 7, f.h - 3, k=0.7)
    line(f, cx + 5, 16, cx + 6, f.h - 3, k=0.7)
    for wx, wy in ((2, 12), (w - 4, 18), (3, 19)):
        blot(f, wx, wy, 1.5, shade(SKIN.c["dark"], 0.8), 0.7)
        tone(f, wx, wy - 1, 1.2)
    line(f, w - 6, 2, w - 3, 9, colour=hexc("b89684"), t=0.6)
    hline(f, f.h - 1, 0.5)
    tuck(fs, 2, 0.8)


def eye(fs):
    """The one great eye: a bloodshot white round a burning amber iris and a slit of pupil."""
    f = fs["front"]
    cx, cy = f.w // 2, f.h // 2
    hline(f, 0, 0.7)
    for x0, x1, y in ((0, 3, 1), (1, 3, 3), (f.w - 3, f.w, 4), (f.w - 4, f.w - 1, 2), (0, 2, 5)):
        for x in range(x0, x1):
            tint(f, x, y, BLOOD, 0.8)
    for y in range(f.h):
        for x in range(cx - 3, cx + 3):
            if (x - cx + 0.5) ** 2 + (y - cy + 0.5) ** 2 <= 10:
                f.glow(x, y, EYE if (x - cx + 0.5) ** 2 + (y - cy + 0.5) ** 2 > 3 else EYE_HOT)
    for y in range(cy - 2, cy + 2):
        f.glow(cx - 1 if y % 2 else cx, y, PUPIL)
        f.glow(cx - 1, y, PUPIL)
    f.px(cx + 1, cy - 2, hexc("ffffff"))


def lid(fs):
    f = fs["front"]
    hline(f, f.h - 1, 0.6)
    hline(f, 0, 1.1)


def brow(fs):
    fill(fs["bottom"], SOCKET, 0.03)
    f = fs["front"]
    hline(f, f.h - 1, 0.6)
    for x in range(1, f.w - 1, 4):
        tone(f, x, 0, 1.2)
        tone(f, x + 1, 0, 1.2)
        tone(f, x + 2, 1, 0.8)


def nose(fs):
    tuck(fs, 1, 0.75, 1.1)
    b = fs["bottom"]
    fill(b, shade(SKIN.c["dark"], 0.7), 0.04)
    b.rect(1, 0, 2, 2, SOCKET)
    b.rect(b.w - 3, 0, 2, 2, SOCKET)
    f = fs["front"]
    f.rect(1, f.h - 2, 2, 2, SOCKET)
    f.rect(f.w - 3, f.h - 2, 2, 2, SOCKET)


def jaw(fs):
    t = fs["top"]
    fill(t, MOUTH, 0.05)
    tuck(fs, 3, 0.7)
    f = fs["front"]
    hline(f, 0, 0.6)
    tone(f, f.w // 2, f.h - 3, 0.75)
    tone(f, f.w // 2 - 1, f.h - 3, 0.75)
    speckle(f, shade(SKIN.c["dark"], 0.7), 0.06)


def tooth(fs):
    for n in ("front", "back", "left", "right"):
        hline(fs[n], fs[n].h - 1, 0.75)


def ear(fs):
    for n in ("left", "right"):
        f = fs[n]
        for y in range(1, f.h - 1):
            for x in range(1, f.w - 1):
                tone(f, x, y, 0.65)
        f.px(f.w - 1, 0, (0, 0, 0), 0)   # a notch torn out of it
        f.px(f.w - 1, 1, (0, 0, 0), 0)


def build():
    b = kit.Remake("cyclops", tex_width=512)
    b.port("cyclops", skip=("brow", "eye", "lid_upper", "lid_lower", "nose", "nose_bridge", "tusk_right", "tusk_left",
                            "cheek_right", "cheek_left"),
           details={"skull": face, "jaw": jaw, "ear_right": ear, "ear_left": ear})
    b.cube("head", "eye", (-6, 96.4, -16.2), (12, 7, 2), EYEBALL, detail=eye)
    b.cube("head", "lid_upper", (-7, 102.4, -17), (14, 2, 3), SKIN, detail=lid)
    b.cube("head", "lid_lower", (-6, 95.4, -16.8), (12, 2, 2), SKIN, detail=lid)
    b.cube("head", "brow", (-11, 103.6, -18.4), (22, 4, 6), SKIN, detail=brow)
    b.cube("head", "brow_lump", (-5, 107, -17.4), (10, 2, 4), SKIN, detail=ao(0, 0))
    b.cube("head", "nose", (-4, 89.6, -18.4), (8, 6, 4), SKIN, detail=nose)
    b.cube("head", "nose_bridge", (-2, 94.4, -17.4), (4, 3, 2), SKIN)
    for side, s in SIDES:
        b.cube("head", f"nostril_{side}", (X(3, 3, s), 89.4, -18), (3, 3, 3), SKIN, detail=nose)
        b.cube("head", f"cheek_{side}", (X(6, 5, s), 90, -16.2), (5, 4, 2), SKIN, detail=ao(1, 0, 0.8))
        b.cube("head", f"tusk_{side}", (X(6, 2, s), 86.6, -18.2), (2, 5, 2), TUSK)
        b.cube("head", f"tusk_tip_{side}", (X(6.5, 1, s), 91, -18), (1, 3, 1), TUSK, rotation=(0, 0, -10 * s),
               origin=(X(6.5, 1, s) + 0.5, 91, -17.5))
    for k in range(4):
        b.cube("head", f"tooth_{k}", (-5 + k * 2.8, 87.6, -17.6), (2, 2, 1), TOOTH, detail=tooth)
    b.finish()


if __name__ == "__main__":
    build()
