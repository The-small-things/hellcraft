#!/usr/bin/env python3
"""Farinata degli Uberti remade at twice the detail (drawn at half his old scale): the Ghibelline captain risen in his
burning tomb, a lean old face with burning eyes under white brows, a hooked nose, a drooping moustache and a short
white beard; the rolled Florentine cap with its tail to his shoulder; a scarlet gown open over his mail and edged in
gold, a chain of office, slashed sleeves, a belt with its purse, the sword he never draws in a banded scabbard, and
his tomb's fire catching at his hem. His skeleton and animations are the old ones (body > torso > head, arms that
bend at the elbow).

    python3 tools/art/remake/farinata.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, shade  # noqa: E402
from paintkit import (ao, both, embers, fill, fingers, folds, hline, line, mail, round_all, tint, tone,  # noqa: E402
                      tone_all, tone_rect, trim, tuck, vline)

FACE = material("face", "skin", base="b08a6a", dark="7a5a44")
GOWN = material("gown", "robe", base="8a1a1a", fold="5a0e0e")
SLEEVE = material("sleeve", "robe", base="7e1616", fold="520c0c")
LINING = material("lining", "robe", base="c8a040", fold="8a6a20")
MAIL = material("mail", "metal", base="6a6a70", high="9a9aa0", low="3a3a40")
CAP = material("cap", "robe", base="6a1010", fold="3a0808")
HAIR = material("hair", "hair", light="d0c8b8", dark="8a8478")
STEEL = material("steel", "metal", base="8a8a90", high="d0d0d8", low="4a4a50")
BRASS = material("brass", "metal", base="b08a3a", high="e0c070", low="6a4a1a")
LEATHER = material("leather", "flat", base="3a2414")
TRIM = hexc("c8a040")
EYE = hexc("ffb040")
EYE_HOT = hexc("fff0a0")
EMBER = hexc("ff7a1a")
EMBER_HOT = hexc("ffd25a")
SOCKET = hexc("2a1810")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


# ----------------------------------------------------------------------------------------------- details

def face(fs):
    """A lean, stern old face: a lined forehead under the cap, eyes burning deep in their sockets, crow's feet,
    hollow cheeks and the lines from nose to mouth; cropped white hair round the back of the head."""
    f = fs["front"]
    w = f.w
    tone_rect(f, 0, 0, w, 2, 0.7)   # the brim's shadow
    for y in (3, 5):
        for x in range(2, w - 2):
            if (x + y) % 5:
                tone(f, x, y, 0.84)   # forehead lines
    for x0 in (1, w - 5):
        for y in range(7, 10):
            for x in range(x0, x0 + 4):
                f.px(x, y, SOCKET)
        f.glow(x0 + 1, 8, EYE)
        f.glow(x0 + 2, 8, EYE_HOT)
        f.px(x0, 10, shade(FACE.c["dark"], 0.8))   # the bag under the eye
        f.px(x0 + 1, 10, shade(FACE.c["dark"], 0.9))
        f.px(x0 + 2, 10, shade(FACE.c["dark"], 0.9))
    for y in (8, 9):   # crow's feet
        tone(f, 0, y, 0.75)
        tone(f, w - 1, y, 0.75)
    for y in range(10, 14):   # hollow cheeks
        for x in (0, 1, w - 2, w - 1):
            tone(f, x, y, 0.8)
    line(f, 3, 10, 2, 13, k=0.7)   # nose to mouth
    line(f, w - 4, 10, w - 3, 13, k=0.7)
    for n in ("left", "right"):
        g = fs[n]
        front = g.w - 1 if n == "right" else 0
        tone_rect(g, 0, 0, g.w, 2, 0.7)
        vline(g, front, 0.85)
        for y in range(2, g.h):   # cropped white hair over the back half of the head, round behind the ear
            for i in range(g.w // 2 - (0 if y < g.h - 3 else 2)):
                x = i if n == "right" else g.w - 1 - i
                if g.rng.random() < 0.9:
                    g.px(x, y, shade(HAIR.c["light"], 0.75 + 0.3 * g.rng.random()))
    b = fs["back"]
    for x in range(b.w):
        for y in range(2, b.h):
            if y < b.h - 3 or b.rng.random() < 0.5:
                b.px(x, y, shade(HAIR.c["light"], 0.75 + 0.3 * b.rng.random()) if b.rng.random() < 0.8 else HAIR.c["dark"])
    tone_all(fs["bottom"], 0.7)


def brow(fs):
    fill(fs["bottom"], SOCKET, 0.03)
    tone_all(fs["front"], 1.05)


def nose(fs):
    f = fs["front"]
    vline(f, 0, 1.1)
    vline(f, f.w - 1, 0.82)
    for n in ("left", "right"):
        tone_all(fs[n], 0.86)


def nose_tip(fs):
    b = fs["bottom"]
    fill(b, shade(FACE.c["dark"], 0.7), 0.03)
    b.px(0, b.h - 1, SOCKET)
    b.px(b.w - 1, b.h - 1, SOCKET)
    hline(fs["front"], fs["front"].h - 1, 0.85)


def lips(fs):
    f = fs["front"]
    fill(f, shade(FACE.c["dark"], 0.85), 0.04)
    hline(f, 0, 0.6)
    f.px(0, 0, shade(FACE.c["dark"], 0.5))
    f.px(f.w - 1, 0, shade(FACE.c["dark"], 0.5))


def ear(fs):
    for n in ("left", "right"):
        f = fs[n]
        for y in range(1, f.h - 1):
            for x in range(1, f.w - 1):
                tone(f, x, y, 0.7)
    tuck(fs, 1, 0.85)


def gown_front(fs):
    """The gown's front: gilt buttons down its closing, shadowed under the collar."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid - 1, y, shade(GOWN.c["fold"], 0.7))
        f.px(mid, y, shade(TRIM, 0.85))
        if y % 3 == 1:
            f.px(mid, y, BRASS.c["high"])
            f.px(mid + 1, y, BRASS.c["base"])
    tone_rect(f, 0, 0, f.w, 2, 0.72)
    ao(2, 0, 0.75)(fs)


def skirt(fs):
    folds(fs, 6, 0.72, 1.12)
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid - 1, y, shade(GOWN.c["fold"], 0.6))
        f.px(mid, y, shade(TRIM, 0.9))
    ao(0, 2, 0.75)(fs)


def hem(fs):
    """The hem: a band of gold, and under it the tomb's fire catching in the cloth."""
    folds(fs, 6, 0.72, 1.12)
    trim(fs, TRIM, 2, "top", dash=4)
    embers(fs, EMBER, EMBER_HOT, 5)


def puff(fs):
    """A slashed sleeve: the gold lining showing through cuts in the scarlet."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(1, f.w - 1, 3):
            for y in range(1, f.h - 2):
                f.px(x, y, shade(TRIM, 1.0 if y % 2 else 0.8))
            tone(f, x + 1, 1, 0.75)
    tuck(fs, 2, 0.72, 1.1)


def cuff(fs):
    trim(fs, TRIM, 1, "bottom", dash=3)
    trim(fs, TRIM, 1, "top")
    ao(1, 0, 0.8)(fs)
    fill(fs["bottom"], SLEEVE.c["fold"], 0.05)


def hand(fs):
    fingers(fs, 4, 0.62, names=("right", "left"))
    tuck(fs, 1, 0.8)
    for n in ("front", "back"):
        hline(fs[n], 0, 0.8)


def cap_roll(fs):
    """The cap's rolled brim: twisted cloth, a dark line where each turn laps the next."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(f.w):
            for y in range(f.h):
                if (x + y * 2) % 5 == 0:
                    tone(f, x, y, 0.65)
                elif (x + y * 2) % 5 == 1:
                    tone(f, x, y, 1.15)
    tuck(fs, 1, 0.75, 1.12)


def chain(fs):
    for n in ("front", "left", "right", "back"):
        f = fs[n]
        for y in range(0, f.h, 2):
            hline(f, y, 0.6)


def pendant(fs):
    f = fs["front"]
    f.rect(1, 1, f.w - 2, f.h - 2, hexc("7a0e16"))   # a garnet
    f.px(1, 1, hexc("e05a5a"))


def scabbard(fs):
    for n in ("top", "bottom", "left", "right"):
        f = fs[n]
        for y in range(0, f.h, 5):
            hline(f, y, 0.8)
    tone_all(fs["bottom"], 0.8)


def buckle(fs):
    f = fs["front"]
    f.rect(1, 1, f.w - 2, f.h - 2, shade(LEATHER.c["base"], 0.8))
    for y in range(1, f.h - 1):
        f.px(f.w // 2, y, BRASS.c["high"])


def purse(fs):
    f = fs["front"]
    hline(f, 0, 0.7)
    hline(f, 1, 1.2)
    f.px(f.w // 2, 2, BRASS.c["high"])
    tuck(fs, 2, 0.7)


def belt(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        hline(f, 0, 1.25)
        hline(f, f.h - 1, 0.7)
        for x in range(2, f.w, 4):
            f.px(x, f.h // 2, BRASS.c["base"])


def collar(fs):
    mail(fs)
    ao(1, 0, 0.7)(fs)


# ----------------------------------------------------------------------------------------------- the model

def build():
    b = kit.Remake("farinata", tex_width=512)

    # the gown's skirt, flaring to a gilt hem that his tomb's fire has caught
    b.cube("body", "skirt_hem", (-12, 0, -8), (24, 6, 16), GOWN, detail=hem)
    b.cube("body", "skirt", (-11, 5, -7), (22, 8, 14), GOWN, detail=skirt)
    b.cube("body", "skirt_upper", (-10, 12, -6), (20, 11, 12), GOWN, detail=skirt)
    for k, (x0, w) in enumerate(((-9.5, 2), (-5, 1), (4, 1), (7.5, 2))):
        b.stick("body", f"pleat{k}", (x0 * 0.9, 22, -6.3), (x0 * 1.15, 1, -8.4), w, GOWN, depth=1, detail=ao(3, 0, 0.6))
    b.cube("body", "belt", (-10, 22, -6.5), (20, 3, 13), LEATHER, detail=belt)
    b.cube("body", "buckle", (-2, 21.5, -7.3), (4, 4, 1), BRASS, detail=buckle)
    b.cube("body", "belt_tail", (1.5, 16.5, -7.1), (2, 6, 1), LEATHER, detail=ao(1, 0, 0.7))
    b.cube("body", "purse", (4.6, 15.5, -8.0), (5, 6, 2), LEATHER, detail=purse)
    b.cube("body", "purse_strap", (6.2, 21, -7.2), (2, 2, 1), LEATHER)
    # the sword at his left side, never drawn
    sword = dict(rotation=(73.426, -18.435, 0), origin=(-12.0, 11.5, 2.8))
    b.cube("body", "sword", (-13, 9.5, -7.2), (2, 4, 20), LEATHER, detail=scabbard, **sword)
    b.cube("body", "sword_chape", (-13.5, 9.0, 10.3), (3, 5, 3), BRASS, **sword)
    b.cube("body", "sword_locket", (-13.5, 9.0, -7.4), (3, 5, 2), BRASS, **sword)
    b.cube("body", "sword_grip", (-12, 24.2, -4.9), (2, 2, 8), LEATHER, rotation=(73.928, -12.529, 0), origin=(-11, 25.2, -0.9),
           detail=chain)
    b.cube("body", "sword_guard", (-12, 20.8, -5.5), (2, 2, 11), STEEL, rotation=(8.531, 0, 0), origin=(-11, 21.8, 0))
    b.cube("body", "sword_guard_boss", (-12.5, 20.3, -1.5), (3, 3, 3), STEEL, rotation=(8.531, 0, 0), origin=(-11, 21.8, 0))
    b.cube("body", "sword_pommel", (-12.5, 27.6, -3.4), (3, 3, 3), BRASS)

    # the torso: the gown closed with gilt buttons, open at the throat over his mail, a chain of office across it
    b.cube("torso", "torso", (-8, 26, -5), (16, 10, 10), GOWN, detail=gown_front)
    b.cube("torso", "chest", (-9, 35, -6), (18, 12, 12), GOWN, detail=gown_front)
    b.cube("torso", "mail_bib", (-3, 42, -6.5), (6, 5, 1), MAIL, detail=mail)
    for side, s in SIDES:
        b.stick("torso", f"lapel_{side}", (0.8 * s, 40.5, -6.6), (5.2 * s, 47.5, -6.2), 2, LINING, depth=1)
        b.stick("torso", f"chain_{side}", (0.6 * s, 37.5, -6.9), (8.4 * s, 46.0, -6.3), 1, BRASS, depth=1, detail=chain)
        b.cube("torso", f"shoulder_{side}", (X(3, 8, s), 45, -5), (8, 4, 10), GOWN, rotation=(0, 0, -14 * s),
               origin=(3 * s, 47, 0), detail=ao(1, 0, 0.8))
        b.cube("torso", f"back_fold_{side}", (X(2, 2, s), 27, 4.6), (2, 19, 1), GOWN, detail=ao(2, 2, 0.75))
    b.cube("torso", "pendant", (-1.5, 35, -7.2), (3, 3, 1), BRASS, detail=pendant)
    b.cube("torso", "mail_collar", (-5, 46, -5), (10, 4, 10), MAIL, detail=collar)
    b.cube("torso", "mail_collar_back", (-4, 49, 1), (8, 2, 4), MAIL, detail=collar)
    b.cube("torso", "neck", (-3, 48, -3.6), (6, 4, 6), FACE, detail=ao(0, 2, 0.7))

    # the head
    b.cube("head", "head", (-6, 51, -6.6), (12, 14, 12), FACE, detail=face)
    for side, s in SIDES:
        b.cube("head", f"brow_{side}", (X(0.4, 5, s), 57.4, -7.8), (5, 2, 2), HAIR, rotation=(0, 0, 10 * s),
               origin=(0.4 * s, 58.4, -7), detail=brow)
        b.cube("head", f"cheekbone_{side}", (X(2.6, 3, s), 54.2, -7.2), (3, 2, 1), FACE, detail=ao(1, 0, 0.8))
        b.cube("head", f"moustache_{side}", (X(0.2, 4, s), 52.0, -8.0), (4, 2, 2), HAIR, rotation=(0, 0, 14 * s),
               origin=(0.2 * s, 53.4, -7))
        b.cube("head", f"beard_{side}", (X(4.6, 2, s), 50.2, -7.0), (2, 5, 8), HAIR)
        b.cube("head", f"sideburn_{side}", (X(5.4, 1, s), 54.5, -4.5), (1, 4, 3), HAIR)
        b.cube("head", f"ear_{side}", (X(5.6, 2, s), 55.5, -2), (2, 4, 3), FACE, detail=ear)
    b.stick("head", "nose", (0, 58.6, -7.0), (0, 54.6, -9.2), 2, FACE, depth=2, detail=nose)
    b.cube("head", "nose_tip", (-1.5, 53.6, -9.6), (3, 2, 3), FACE, detail=nose_tip)
    b.cube("head", "lips", (-2, 52.0, -7.2), (4, 1, 1), FACE, detail=lips)
    b.cube("head", "beard", (-4, 49.0, -7.8), (8, 3, 3), HAIR)
    b.cube("head", "beard_point", (-2, 47.6, -7.6), (4, 2, 2), HAIR)
    b.cube("head", "beard_under", (-4, 50.0, -5), (8, 1, 6), HAIR)
    b.cube("head", "hair_back", (-6.4, 51.5, 2.6), (13, 9, 3), HAIR)
    # the Florentine cap: a rolled brim, a soft crown fallen to one side, its tail hanging to his shoulder
    b.cube("head", "cap_brim", (-7.5, 62.2, -8.0), (15, 4, 15), CAP, detail=cap_roll)
    b.cube("head", "cap_brim_front", (-5, 62.8, -8.8), (10, 3, 1), CAP, detail=cap_roll)
    b.cube("head", "cap", (-6, 65.6, -6.6), (12, 4, 12), CAP, rotation=(0, 0, 7), origin=(0, 66, 0), detail=ao(1, 0, 0.75))
    b.cube("head", "cap_top", (-5, 68.6, -5.6), (10, 2, 10), CAP, rotation=(0, 0, 12), origin=(0, 66, 0))
    b.cube("head", "cap_fall", (2, 66.5, -4.5), (7, 4, 9), CAP, rotation=(0, 0, -24), origin=(5, 68, 0), detail=ao(1, 0, 0.75))
    b.cube("head", "cap_tail", (5.8, 56.7, -3.9), (4, 2, 16), CAP, rotation=(76.344, 47.49, 0), origin=(7.8, 57.7, 4.1),
           detail=folds)
    b.cube("head", "cap_tassel", (8.6, 47.6, 3.2), (2, 3, 2), LINING, detail=ao(2, 0, 0.7))

    # the arms: slashed sleeves showing their gold lining, wide gilt cuffs over the mail at his wrists
    for side, s in SIDES:
        arm, fore = f"{side}_arm", f"{side}_forearm"
        b.cube(arm, f"{side}_puff", (X(7, 9, s), 42, -4.5), (9, 7, 9), SLEEVE, detail=puff)
        b.cube(arm, f"{side}_puff_top", (X(8, 7, s), 48.5, -3.5), (7, 1, 7), SLEEVE)
        b.cube(arm, f"{side}_arm", (X(8.4, 6, s), 36, -3), (6, 7, 6), SLEEVE, detail=both(folds, ao(2, 2, 0.75)))
        b.cube(fore, f"{side}_elbow", (X(8, 7, s), 35, -3.5), (7, 3, 7), SLEEVE, detail=ao(1, 1, 0.75))
        b.cube(fore, f"{side}_sleeve", (X(8.4, 6, s), 29, -3), (6, 7, 6), SLEEVE, detail=both(folds, ao(1, 0, 0.75)))
        b.cube(fore, f"{side}_cuff", (X(7.4, 8, s), 26.4, -4), (8, 4, 8), SLEEVE, detail=cuff)
        b.cube(fore, f"{side}_mail", (X(8.8, 5, s), 24.6, -2.6), (5, 2, 5), MAIL, detail=mail)
        b.cube(fore, f"{side}_hand", (X(9.4, 3, s), 19.6, -3), (3, 5, 6), FACE, detail=hand)
        b.cube(fore, f"{side}_knuckles", (X(12.2, 1, s), 21.6, -3), (1, 3, 6), FACE, detail=ao(1, 0, 0.8))
        b.cube(fore, f"{side}_thumb", (X(9.2, 2, s), 21, -4.6), (2, 4, 2), FACE, detail=ao(1, 0, 0.75))
    b.finish()


if __name__ == "__main__":
    build()
