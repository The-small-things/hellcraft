#!/usr/bin/env python3
"""Vulcan remade at twice the detail (drawn at half his old scale). The lame smith's body is carried over part for
part with a finer grain (tools/art/legacy/vulcan.py through remake_kit's port); new are his face (coal eyes deep
under a split, scowling brow, a nose broken to one side, soot in every line, burn scars), a moustache in two drooping
halves, iron rings braided into his beard, fingers on his hands, straps and a rivet band on his hammer.

    python3 tools/art/remake/vulcan.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, shade  # noqa: E402
from paintkit import ao, fill, hline, line, speckle, tint, tone, tone_all, tone_rect, tuck, vline  # noqa: E402

SKIN = material("skin", "skin", base="5e4032", dark="2e1c14")
BEARD = material("beard", "hair", light="5e4638", dark="1a100a")
IRON = material("iron", "metal", base="3e3e44", high="6e6e78", low="1e1e22")
EMBER = hexc("ff7a1a")
EYE = hexc("ff5a10")
EYE_HOT = hexc("ffe080")
SOCKET = hexc("140a06")
SCAR = hexc("7a5444")
SOOT = hexc("1a1210")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def face(fs):
    """A scowling smith's face: eyes like coals deep under the brow, hollow cheeks, soot in the lines, a burn scar."""
    f = fs["front"]
    w = f.w
    for y in (1, 3):
        for x in range(2, w - 2):
            if (x + y) % 6:
                tone(f, x, y, 0.75)   # the furrowed forehead
    for x0 in (2, w - 7):
        f.rect(x0, 6, 5, 4, SOCKET)
        f.glow(x0 + 1, 7, EYE)
        f.glow(x0 + 2, 7, EYE_HOT)
        f.glow(x0 + 3, 7, EYE)
        f.glow(x0 + 2, 8, EYE)
        tone_rect(f, x0, 10, 5, 1, 0.7)
        tone_rect(f, x0 + 1, 11, 3, 1, 0.85)
    for y in range(11, 16):   # hollow cheeks
        for x in (1, 2, w - 3, w - 2):
            tone(f, x, y, 0.75)
    line(f, 5, 11, 4, 15, k=0.65)
    line(f, w - 6, 11, w - 5, 15, k=0.65)
    for x, y in ((3, 0), (4, 1), (4, 2), (5, 3), (6, 4)):   # an old burn across the brow
        f.px(x, y, SCAR)
    f.glow(5, 2, shade(EMBER, 0.7))
    speckle(f, SOOT, 0.07, 0.6)
    tuck(fs, 2, 0.8)
    for n in ("left", "right"):
        speckle(fs[n], SOOT, 0.05, 0.5)


def brow(fs):
    fill(fs["bottom"], SOCKET, 0.03)
    f = fs["front"]
    hline(f, f.h - 1, 0.55)
    hline(f, 0, 1.12)


def nose(fs):
    f = fs["front"]
    vline(f, 0, 1.12)
    vline(f, f.w - 1, 0.8)
    hline(f, f.h // 2, 0.75)
    for n in ("left", "right"):
        tone_all(fs[n], 0.82)
    b = fs["bottom"]
    tone_all(b, 0.6)


def ring(fs):
    for n in ("front", "back", "left", "right"):
        hline(fs[n], 0, 1.3)


def finger(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        hline(f, f.h // 2, 0.75)
        hline(f, f.h - 1, 0.7)
    speckle(fs["front"], SOOT, 0.2, 0.6)


def build():
    b = kit.Remake("vulcan", tex_width=512)
    b.port("vulcan", skip=("brow", "nose", "moustache"), details={"skull": face})
    for side, s in SIDES:
        b.cube("head", f"brow_{side}", (X(0.4, 9, s), 109.6, -17), (9, 4, 4), SKIN, rotation=(0, 0, 10 * s),
               origin=(0.4 * s, 112, -15), detail=brow)
        b.cube("head", f"eyebrow_{side}", (X(1, 8, s), 112.6, -17.4), (8, 2, 2), BEARD, rotation=(0, 0, 10 * s),
               origin=(0.4 * s, 112, -15))
        b.cube("head", f"cheekbone_{side}", (X(4, 4, s), 104.4, -16), (4, 2, 1), SKIN, detail=ao(1, 0, 0.8))
        b.cube("head", f"moustache_{side}", (X(0.3, 7, s), 100, -18.6), (7, 3, 2), BEARD, rotation=(0, 0, 16 * s),
               origin=(0.3 * s, 103, -18))
        b.cube("head", f"beard_braid_{side}", (X(4, 3, s), 78, -18.4), (3, 12, 3), BEARD, detail=ao(3, 0, 0.7))
        b.cube("head", f"beard_ring_{side}", (X(3.5, 4, s), 82, -18.9), (4, 2, 4), IRON, detail=ring)
    b.stick("head", "nose", (0.6, 112, -16.6), (-0.4, 104.6, -19.4), 4, SKIN, depth=3, detail=nose)
    b.cube("head", "nose_tip", (-3, 102.6, -20.2), (5, 3, 4), SKIN, detail=nose)
    b.cube("head", "beard_ring", (-3, 79, -18.2), (6, 2, 6), IRON, detail=ring)
    b.finish()


if __name__ == "__main__":
    build()
