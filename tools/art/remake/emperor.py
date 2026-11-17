#!/usr/bin/env python3
"""The Emperor of the Dolorous Realm remade at twice the detail (drawn at half EmperorEntity.SCALE: he is as huge as
before, each model pixel now a third of a block). His old body is carried over part for part with a finer grain
(tools/art/legacy/emperor.py through remake_kit's port), so his hands, faces and weak points come down exactly where
EmperorEntity expects. New: each of his three faces is furrowed and veined, its lips cracked and bloody, with rows
of teeth above and below the sinner it chews, icicles of frozen tears on its cheeks and spikes over its brows; ribs
stand out under his chest, his knuckles are knobbed, and smaller horns ring the great ones.

    python3 tools/art/remake/emperor.py && python3 tools/gen_bosses.py

(The old script's reach() still prints the numbers EmperorEntity copies: python3 tools/art/legacy/emperor.py does
not need running again, since nothing moved.)
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, shade  # noqa: E402
from paintkit import ao, cracks, hline, line, speckle, tint, tone, tone_rect, tuck, veins, vline  # noqa: E402

RED = material("red", "skin", base="8a1a14", dark="4a0806")
PALE = material("pale", "skin", base="c8b878", dark="7a6a38")
BLACK = material("black", "skin", base="1a1414", dark="060404")
HIDE = material("hide", "skin", base="3a2a2e", dark="1a1214")
FROST = material("frost", "flat", base="c8e0f0")
HORN = material("horn", "horn", root="4a3a30", tip="0e0a08")
FANG = material("fang", "horn", root="d8ccb0", tip="8a7a60")
GUM = hexc("4a0a0a")
BLOOD = hexc("6a0a0a")
RIME = hexc("e8f6ff")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def visage(name, vein):
    """More on a face than the old paint had room for: fine furrows fanning from the brows, crow's feet, veins at
    the temples, cracked and bloody lips round the mouth, rime in the creases."""
    def detail(fs):
        f = fs[name]
        w, h = f.w, f.h
        mid = w // 2
        for i in range(3):   # furrows between the brows
            line(f, mid - 2 - i * 2, 9 + i, mid - 1 - i * 2, 4 - i, k=0.72)
            line(f, mid + 1 + i * 2, 9 + i, mid + i * 2, 4 - i, k=0.72)
        for x0, dx in ((2, -1), (w - 3, 1)):   # crow's feet
            for i in range(3):
                line(f, x0, 13 + i * 2, x0 + dx * 2, 12 + i * 3, k=0.75)
        veins(f, 3, vein, 0.35)
        y0 = int(h * 0.62)
        for x in range(5, w - 5):   # the lips: cracked, bloody where they split
            if f.rng.random() < 0.45:
                tint(f, x, y0 - 1, BLOOD, 0.7)
            if f.rng.random() < 0.3:
                tint(f, x, y0 - 2, BLOOD, 0.4)
            if f.rng.random() < 0.25:
                tone(f, x, y0 - 3, 0.7)
        speckle(f, RIME, 0.02, 0.5)
        cracks(f, 3, 0.7, 5)
    return detail


def tooth(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        hline(f, 0, 0.7)
        if f.h > 2:
            tint(f, f.rng.randrange(f.w), 1, GUM, 0.5)


def rib(fs):
    tuck(fs, 1, 0.6, 1.15)


def knob(fs):
    tuck(fs, 1, 0.7, 1.2)
    speckle(fs["top"], RIME, 0.3, 0.6)


def icicle(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        vline(f, 0, 1.1)
        for y in range(f.h):
            tone(f, f.w - 1, y, 0.85)


def build():
    b = kit.Remake("emperor", tex_width=1024)
    b.port("emperor", also={"head": lambda fs: (visage("front", hexc("3a0604"))(fs)),
                            "face_right": visage("right", hexc("6a5a28")),
                            "face_left": visage("left", hexc("3a2a2a"))})

    # teeth: a row above and below in each of the three mouths
    for k in range(7):
        x = -13 + k * 4
        if abs(x + 1) > 9:
            continue   # the great fangs stand at the corners
        b.cube("head", f"tooth_front_up{k}", (x, 53, -21.6), (2, 3, 1), FANG, detail=tooth)
    for k in range(8):
        x = -15 + k * 4
        b.cube("head", f"tooth_front_low{k}", (x, 44, -23.4), (2, 2 + (k % 2), 1), FANG, detail=tooth)
    for side, s in SIDES:
        for k in range(5):
            z = -9 + k * 4
            if z in (-9, 7):
                continue
            b.cube("head", f"tooth_{side}_up{k}", (X(21.4, 1, s), 51, z), (1, 3, 2), FANG, detail=tooth)
        for k in range(6):
            z = -11 + k * 4
            b.cube("head", f"tooth_{side}_low{k}", (X(26.6, 1, s), 46, z), (1, 2 + (k % 2), 2), FANG, detail=tooth)
    # frozen tears hanging from the cheeks of each face, spikes over each brow, horns round the crown
    for side, s in SIDES:
        for k, (dx, ln) in enumerate(((9, 5), (12.5, 3))):
            b.cube("head", f"tear_front_{side}{k}", (X(dx, 1, s), 58 - ln, -23.4), (1, ln, 1), FROST, detail=icicle)
        for k, (dz, ln) in enumerate(((-9, 4), (6, 5))):
            b.cube("head", f"tear_{side}{k}", (X(23.2, 1, s), 58 - ln, dz), (1, ln, 1), FROST, detail=icicle)
        for k, (dx, ln) in enumerate(((5, 4), (10, 6), (15, 4))):
            b.cube("head", f"brow_spike_front_{side}{k}", (X(dx, 2, s), 75 + k, -23), (2, ln, 2), HORN, rotation=(-24, 0, -10 * s),
                   origin=(X(dx, 2, s) + 1, 75 + k, -22))
        for k, dz in enumerate((-10, -2, 6)):
            b.cube("head", f"brow_spike_{side}{k}", (X(23, 2, s), 73.5, dz), (2, 5, 2), HORN, rotation=(0, 0, -28 * s),
                   origin=(X(23, 2, s) + 1, 73.5, dz + 1))
        for k, (dx, dz, ln) in enumerate(((7, 12, 9), (15, 6, 7), (5, -14, 6))):
            b.cube("head", f"crown_horn_{side}{k}", (X(dx, 3, s), 82, dz), (3, ln, 3), HORN, rotation=(8 if dz > 0 else -12, 0, -16 * s),
                   origin=(X(dx, 3, s) + 1.5, 82, dz + 1.5))
        b.cube("head", f"chin_spike_{side}", (X(4, 2, s), 32, -23), (2, 5, 2), HORN, rotation=(20, 0, 0),
               origin=(X(4, 2, s) + 1, 36, -22))

    # ribs standing out under his chest, knobbed knuckles
    for side, s in SIDES:
        for k, y in enumerate((5.5, 9.0)):
            b.cube("upper", f"{side}_rib{k}", (X(5, 26 + 4 * k, s), y, -21.4), (26 + 4 * k, 2, 3), HIDE, rotation=(0, 0, -5 * s),
                   origin=(5 * s, y + 1, -20), detail=rib)
        for k in range(4):
            fx = 37 + k * 6
            b.cube(f"{side}_arm", f"{side}_knuckle{k}", (X(fx, 4, s), -29.5, -11), (4, 3, 4), HIDE, detail=knob)
    b.finish()


if __name__ == "__main__":
    build()
