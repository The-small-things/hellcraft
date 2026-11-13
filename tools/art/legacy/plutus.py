#!/usr/bin/env python3
"""Plutus, the Great Enemy (Inferno VII): the old god of riches, swollen with the gold of the fourth circle. A huge
bloated banker in a purple velvet waistcoat strained over a gold-skinned belly, a cloak sewn with coins, ringed
fists like money bags, and the head of the wolf Virgil calls him ("accursed wolf"), crowned with coins. He shrieks
"Pape Satàn, pape Satàn aleppe!"

Run from the repo root:  python3 tools/art/plutus.py && python3 tools/gen_bosses.py

The animations' events are what the server times Plutus's fight by (see PlutusFight): "flash" opens the window for
a shield block and "hit" is when his fist lands; "rain" when the gold falls; "leap" and "land" his lunge; "shriek"
Pape Satàn; "roar" and "mint" his fury at half health, when the great coins start to roll.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="c09468", dark="7a5236")
LIP = material("lip", "skin", base="5e2e24", dark="341410")
VEST = material("vest", "robe", base="4a1a5a", fold="2a0c36", trim="d4a83a")
VELVET = material("velvet", "robe", base="4a1a5a", fold="2a0c36")
GOLD = material("gold", "metal", base="d4a83a", high="f6dc80", low="8a6a1e")
FUR = material("fur", "hair", light="8a7a62", dark="463a2a")
FUR_DARK = material("fur_dark", "hair", light="6a5a46", dark="2a2018")
MUZZLE = material("muzzle", "hair", light="a8987a", dark="5e5040")
NOSE = material("nose", "flat", base="1c1412")
CLAW = material("claw", "horn", root="4a3e30", tip="141010")
TOOTH = material("tooth", "horn", root="e6dcbc", tip="fff8e4", edges=False)
COINS = material("coins", "metal", base="c9982e", high="f1d27a", low="7d5a17")

EYE = hexc("ffe04a")
EYE_HOT = hexc("fffbe0")
SOCKET = hexc("1e1610")
MOUTH = hexc("3a1208")
TONGUE = hexc("8a2a2a")
RUBY = hexc("ff3a3a")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- details

def colour(f, x, y):
    """The colour already painted at (x, y) of a face, or None off the face."""
    if 0 <= x < f.w and 0 <= y < f.h:
        return f.canvas.get(f.x + x, f.y + y)[:3]
    return None


def darken(f, x, y, k):
    c = colour(f, x, y)
    if c is not None:
        f.px(x, y, shade(c, k))


def both(*details):
    """Several detail painters on one cube, in order."""
    def apply(faces):
        for d in details:
            d(faces)
    return apply


def rounded(edge=0.8, low=0.82, names=SIDES):
    """Shades a box as if it were round: its sides darken toward their edges (where a round form turns away from the
    light) and toward the bottom (the shadow where it meets what is under it)."""
    def apply(faces):
        for n in names:
            f = faces[n]
            for y in range(f.h):
                v = y / max(1, f.h - 1)
                for x in range(f.w):
                    u = abs(2 * x / max(1, f.w - 1) - 1)
                    darken(f, x, y, (1 - (1 - edge) * u ** 2) * (1.04 - (1.04 - low) * v ** 2))
    return apply


def under_shadow(rows=2, k=0.7, names=SIDES):
    """A crease of shadow along the top of the sides, where this part tucks under the one above it."""
    def apply(faces):
        for n in names:
            f = faces[n]
            for y in range(min(rows, f.h)):
                for x in range(f.w):
                    darken(f, x, y, k + (1 - k) * y / rows)
    return apply


def coin_rows(names=("front", "back", "left", "right", "top")):
    """Coins sewn on in overlapping rows, like scales: each coin lit along its upper rim, stamped in the middle, and
    throwing a dark line of shadow on the row below; each a slightly different gold."""
    def apply(faces):
        for n in names:
            f = faces[n]
            tones = {}
            for y in range(f.h):
                row = y // 3
                off = 2 if row % 2 else 0
                for x in range(f.w):
                    cx, cy = (x + off) % 4, y % 3
                    tone = tones.setdefault((row, (x + off) // 4), 0.85 + f.rng.random() * 0.3)
                    if cy == 2:
                        c = shade(COINS.c["low"], 0.7 if cx in (1, 2) else 0.55)
                    elif cx == 0 or (cx == 3 and cy == 0):
                        c = shade(COINS.c["low"], 0.85)
                    elif cy == 0:
                        c = COINS.c["high"] if cx in (1, 2) else COINS.c["base"]
                    else:
                        c = shade(COINS.c["base"], 0.86) if cx == 2 else COINS.c["base"]
                    f.px(x, y, shade(c, tone * (1.1 if n == "top" else 1.0)))
    return apply


def chain(faces):
    """A heavy gold chain: links alternately seen flat (a lit ring round a dark hole) and edge-on (a bright bar across
    the gap between its neighbours)."""
    for n in ("front", "back", "top", "bottom", "left", "right"):
        f = faces[n]
        along_x = f.w >= f.h
        length, across = (f.w, f.h) if along_x else (f.h, f.w)
        for i in range(length):
            phase = i % 6
            for j in range(across):
                x, y = (i, j) if along_x else (j, i)
                edge = j in (0, across - 1)
                if phase < 4:
                    if phase in (1, 2) and not edge:
                        c = shade(GOLD.c["low"], 0.45)  # the hole through a flat link
                    else:
                        c = GOLD.c["high"] if j == 0 or phase == 0 else GOLD.c["base"]
                else:
                    c = GOLD.c["high"] if not edge or across < 3 else shade(GOLD.c["low"], 0.55)
                f.px(x, y, c)


def rivets(faces):
    """A gold plate: rivets at its corners and a rim of shadow where it overlaps the plate below."""
    for n in ("front", "back", "left", "right", "top"):
        f = faces[n]
        if f.w < 5 or f.h < 4:
            continue
        for x, y in ((1, 1), (f.w - 2, 1), (1, f.h - 2), (f.w - 2, f.h - 2)):
            f.px(x, y, GOLD.c["high"])
            darken(f, x + 1, y + 1, 0.7)
        if n != "top":
            for x in range(f.w):
                darken(f, x, f.h - 1, 0.7)


def coin_face(faces):
    """One big coin: a raised rim, a darker field and a stamped wolf's head (a ruby for an eye)."""
    for n in ("front", "back"):
        f = faces[n]
        cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
        r = min(f.w, f.h) / 2
        for y in range(f.h):
            for x in range(f.w):
                d = math.hypot(x - cx, y - cy) / r
                if d > 1.0:
                    c = shade(COINS.c["low"], 0.8)
                elif d > 0.72:
                    c = COINS.c["high"] if y < cy else COINS.c["base"]
                else:
                    c = shade(COINS.c["base"], 0.85)
                f.px(x, y, c)
        if f.w >= 5:
            f.px(int(cx), int(cy), COINS.c["low"])
            f.px(int(cx) + 1, int(cy) - 1, COINS.c["low"])
            f.glow(int(cx), int(cy) - 1, RUBY)


def belly_skin(faces):
    """The swollen belly: stretch marks fanning round it, dark veins, a trail of coarse hair and coins pressed into
    the flesh."""
    for n in ("front", "left", "right"):
        f = faces[n]
        pale = mix_skin(0.35)
        for k in range(max(2, f.w // 6)):
            x0 = f.rng.randrange(f.w)
            y0 = f.rng.randrange(max(1, f.h - 6))
            slant = f.rng.choice((-1, 1))
            for i in range(f.rng.randint(3, 6)):
                f.px(x0 + (i * slant) // 2, y0 + i, pale)
        for k in range(max(1, f.w // 10)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            for i in range(f.rng.randint(4, 8)):
                f.px(x, y, hexc("6a4a4a"))
                x += f.rng.choice((-1, 0, 1))
                y += 1


def mix_skin(t):
    """A paler skin tone, for stretch marks (t: how far toward pale)."""
    return tuple(int(SKIN.c["base"][i] + (255 - SKIN.c["base"][i]) * t * 0.5) for i in range(3))


def navel(faces):
    f = faces["front"]
    mid, y = f.w // 2, f.h // 2
    f.px(mid, y, shade(SKIN.c["dark"], 0.45))
    f.px(mid - 1, y, shade(SKIN.c["dark"], 0.6))
    f.px(mid, y + 1, shade(SKIN.c["dark"], 0.65))
    f.px(mid, y - 1, shade(SKIN.c["dark"], 0.8))
    for i in range(2, y - 1):  # the hair trail down from the chest
        if i % 2 == 0:
            f.px(mid + (i // 3) % 2 - 1, i, FUR.c["dark"])


def vest_panel(faces):
    """Velvet strained over the gut: a brocade of gold thread, folds pulled sideways toward the buttons, and a trim."""
    f = faces["front"]
    for y in range(f.h):
        for x in range(f.w):
            if (x + y) % 6 == 0 and (x - y) % 6 == 0:
                f.px(x, y, shade(VEST.c["trim"], 0.6))
            elif (x * 2 + y) % 9 == 0:
                darken(f, x, y, 0.8)  # the cloth pulled tight
    for y in range(f.h):
        f.px(0, y, shade(VEST.c["trim"], 0.9))
        f.px(f.w - 1, y, shade(VEST.c["trim"], 0.7))


def buttonholes(faces):
    """Buttons straining at their holes (the other panel's buttons sit on this edge)."""
    f = faces["front"]
    for y in range(3, f.h - 3, 6):
        for dx in (1, 2, 3):
            f.px(dx, y, shade(VEST.c["fold"], 0.6))


def chest_fur(faces):
    for n in SIDES:
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if y >= f.h - 2 and (x + y) % 2:
                    darken(f, x, y, 0.75)


def fur_fringe(faces):
    """The ragged lower edge of a ruff: tufts of dark tips."""
    for n in SIDES:
        f = faces[n]
        for x in range(f.w):
            if x % 3 != 1:
                f.px(x, f.h - 1, shade(FUR.c["dark"], 0.7))
            if x % 3 == 0:
                f.px(x, f.h - 2, shade(FUR.c["dark"], 0.85))


def wolf_face(faces):
    """Small greedy yellow eyes deep in their sockets under the brow, slanting up at the outer corners."""
    f = faces["front"]
    for cx, out in ((2, -1), (f.w - 4, 1)):
        for dx in range(-1, 3):
            for dy in (5, 6, 7, 8):
                f.px(cx + dx, dy, SOCKET)
        f.glow(cx, 7, EYE)
        f.glow(cx + 1, 7, EYE_HOT)
        f.glow(cx + (2 if out > 0 else -1), 6, shade(EYE, 0.8))
    for y in range(1, 5):  # a furrow up the forehead
        darken(f, f.w // 2, y, 0.75)
        darken(f, f.w // 2 - 1, y + 1, 0.85)


def muzzle_detail(faces):
    """The snout: paler underneath, darker along the bridge, rows of whisker pores along the sides."""
    for n in ("left", "right"):
        f = faces[n]
        for x in range(1, f.w - 1):
            if x % 2 == 0 and x > f.w // 3:
                f.px(x, f.h - 3, shade(MUZZLE.c["dark"], 0.6))
            if x % 3 == 1 and x > f.w // 3:
                f.px(x, f.h - 2, shade(MUZZLE.c["dark"], 0.7))
    t = faces["top"]
    for y in range(t.h):
        darken(t, t.w // 2, y, 0.8)
        darken(t, t.w // 2 - 1, y, 0.8)


def nose_detail(faces):
    f = faces["front"]
    f.px(1, f.h - 2, hexc("050303"))
    f.px(f.w - 2, f.h - 2, hexc("050303"))
    for x in range(1, f.w - 1):
        f.px(x, 0, hexc("4a3c38"))  # wet shine on top


def lip_detail(faces):
    """The upper lip: a row of teeth along its edge, the mouth's roof underneath."""
    for n in ("front", "left", "right"):
        f = faces[n]
        for x in range(f.w):
            if n == "front" or x < f.w * 2 // 3 if n == "left" else n == "front" or x > f.w // 3:
                f.px(x, f.h - 1, TOOTH.c["root"] if x % 2 == 0 else shade(TOOTH.c["root"], 0.75))
    faces["bottom"].fill(lambda x, y: MOUTH if (x + y) % 5 else shade(MOUTH, 1.4))


def jaw_detail(faces):
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    for y in range(1, t.h - 1):
        for x in range(2, t.w - 2):
            t.px(x, y, TONGUE if y > 1 else MOUTH)
    for x in range(t.w):
        t.px(x, 0, TOOTH.c["root"] if x % 2 == 0 else MOUTH)
    for n in ("front",):
        f = faces[n]
        for x in range(f.w):
            f.px(x, 0, TOOTH.c["root"] if x % 2 else shade(TOOTH.c["root"], 0.8))
    for n in ("left", "right"):
        f = faces[n]
        for x in range(f.w):
            if x % 2 == 0:
                f.px(x, 0, TOOTH.c["root"])


def jowl_detail(faces):
    """Fat folds hanging under the jaw."""
    for n in SIDES:
        f = faces[n]
        for x in range(f.w):
            darken(f, x, f.h // 2, 0.72)
            darken(f, x, f.h // 2 + 1, 0.88)
            darken(f, x, 0, 0.6)


def ear_detail(faces):
    """The inside of the ear: dark, with pale tufts at the rim."""
    f = faces["front"]
    for y in range(1, f.h):
        for x in range(1, f.w - 1):
            f.px(x, y, hexc("3a2420") if y > 1 else hexc("5a3a30"))
    for y in range(1, f.h, 2):
        f.px(1, y, MUZZLE.c["light"])


def ruby(faces):
    f = faces["front"]
    f.glow(f.w // 2, max(1, f.h // 3), RUBY)


def ring_gem(faces):
    """A ring with a ruby set in it."""
    for n in ("right", "left"):
        f = faces[n]
        f.glow(f.w // 2, f.h // 2, RUBY)
        f.px(f.w // 2, f.h // 2 - 1, GOLD.c["high"])


def knuckles(faces):
    """Finger joints: creases across the curled fingers, darker between them."""
    for n in ("front", "back", "bottom", "left", "right"):
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if n in ("front", "back") and x in (f.w // 3, 2 * f.w // 3):
                    darken(f, x, y, 0.72)
                if n == "bottom" and x in (f.w // 3, 2 * f.w // 3):
                    darken(f, x, y, 0.72)


def toe_creases(faces):
    f = faces["front"]
    for y in range(f.h):
        darken(f, 0, y, 0.7)
        darken(f, f.w - 1, y, 0.7)


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("plutus", texture_width=256)

    body = m.bone("body", (0, 26, 0))
    # the gut: a round barrel of a body with a belly sagging out in front of it and over the belt
    body.cube("belly", (-19, 15, -15), (38, 37, 29), SKIN, detail=both(rounded(0.75, 0.8), belly_skin))
    body.cube("belly_flanks", (-21, 19, -13), (42, 24, 24), SKIN, detail=both(rounded(0.72, 0.75), belly_skin))
    body.cube("belly_lower", (-18, 15, -19), (36, 26, 4), SKIN, detail=both(rounded(0.72, 0.62), belly_skin))
    body.cube("belly_bulge", (-14, 18, -22), (28, 19, 3), SKIN, detail=both(rounded(0.78, 0.7), belly_skin, navel))
    body.cube("belly_sag", (-10, 17, -23), (20, 13, 1), SKIN, detail=both(rounded(0.8, 0.7), belly_skin))
    # coins pressed into the flesh: the gold he has gorged on breaking out of him
    for i, (bx, by, bz, turn) in enumerate(((7, 25, -22.5, 20), (-10, 30, -22.4, -15), (14.5, 20, -19.5, 35),
                                             (-16, 36, -19.4, 10))):
        body.cube(f"belly_coin_{i}", (bx, by, bz), (3, 3, 1), COINS, rotation=(0, 0, turn), origin=(bx + 1.5, by + 1.5, bz),
                  detail=coin_face)
    # a gold chain for a belt, half buried under the gut, with a great coin for a buckle
    body.cube("belt", (-19.5, 13, -18.5), (39, 3, 32), GOLD, inflate=0.2, detail=chain)
    body.cube("buckle", (-3, 11.5, -19.6), (6, 6, 1), COINS, detail=coin_face)

    # the waistcoat: velvet round his back and sides, its two front panels pulled apart by the belly
    body.cube("vest", (-20, 30, -14), (40, 24, 28), VEST, inflate=0.3, detail=rounded(0.8, 0.85))
    body.cube("chest", (-17, 50, -15), (34, 16, 28), VEST, detail=rounded(0.8, 0.9))
    for side, s in (("r", 1), ("l", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        body.cube(f"vest_{side}", (x(5, 14), 32, -17), (14, 30, 3), VELVET, mirror=s == -1,
                  detail=both(vest_panel, rounded(0.85, 0.8, ("front",)), buttonholes if s == -1 else rounded(1.0, 1.0, ())))
        body.cube(f"lapel_{side}", (x(4, 6), 56, -17.6), (6, 7, 1), GOLD, rotation=(0, 0, -14 * s),
                  origin=(4 * s, 63, -17), mirror=s == -1)
    for i, by in enumerate((45, 51, 57)):
        body.cube(f"button_{i}", (4.2, by, -17.8), (2, 2, 1), GOLD, inflate=0.15)
    # the hair of his chest between the panels
    body.cube("chest_fur", (-5, 44, -16), (10, 16, 2), FUR, detail=chest_fur)

    # the wolf's ruff: shaggy layers round the neck and over the shoulders, longest at the back
    body.cube("mane_back", (-16, 54, -2), (32, 13, 13), FUR_DARK, rotation=(-10, 0, 0), origin=(0, 54, 5),
              detail=both(fur_fringe, rounded(0.8, 0.85)))
    body.cube("mane_front", (-14, 57, -18), (28, 6, 6), FUR, detail=both(fur_fringe, rounded(0.8, 0.9)))
    body.cube("mane_front_low", (-10, 52, -18.5), (20, 6, 4), FUR, detail=both(fur_fringe, rounded(0.8, 0.9)))
    body.cube("mane_front_tip", (-5, 47, -18.8), (10, 6, 3), FUR, detail=fur_fringe)
    for side, s in (("r", 1), ("l", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        body.cube(f"mane_{side}", (x(12, 7), 55, -14), (7, 12, 16), FUR, mirror=s == -1, detail=both(fur_fringe, rounded(0.8, 0.85)))
    # a heavy chain about the neck with a great coin hanging from it, and another slung from shoulder to hip
    body.cube("necklace", (-11, 55.5, -19.6), (22, 2, 1), GOLD, detail=chain)
    body.cube("medallion", (-3.5, 49, -20.2), (7, 7, 1), COINS, detail=coin_face)
    body.cube("sash_chain", (-1, 30, -19.4), (2, 30, 1), GOLD, rotation=(0, 0, -42), origin=(0, 46, -19), detail=chain)

    # the cloak of coins, in three tiers that overlap like scales, with a fringe of loose coins at its hem
    body.cube("cloak_top", (-18, 50, 13), (36, 17, 4), COINS, detail=coin_rows())
    body.cube("cloak_mid", (-19.5, 34, 14), (39, 18, 4), COINS, detail=both(coin_rows(), under_shadow(2, 0.6)))
    body.cube("cloak", (-21, 18, 15), (42, 18, 4), COINS, detail=both(coin_rows(), under_shadow(2, 0.6)))
    for i, fx in enumerate(range(-19, 19, 5)):
        body.cube(f"cloak_fringe_{i}", (fx, 15 - (i % 2), 17.2), (3, 4, 1), COINS, detail=coin_face)

    # ------------------------------------------------------------------------------------- the wolf's head
    head = body.bone("head", (0, 66, -2))
    head.cube("skull", (-8, 68, -12), (16, 13, 15), FUR, detail=both(wolf_face, rounded(0.82, 0.9)))
    head.cube("occiput", (-7, 69, 3), (14, 10, 3), FUR_DARK, detail=rounded(0.8, 0.85))
    for side, s in (("r", 1), ("l", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        # a heavy brow over each eye, angled down toward the middle in a scowl
        head.cube(f"brow_{side}", (x(0.5, 8), 75, -13.5), (8, 2, 3), FUR_DARK, rotation=(0, 0, 12 * s),
                  origin=(0.5 * s, 76, -12), mirror=s == -1)
        # ruffs of fur flaring from the cheeks
        head.cube(f"cheek_{side}", (x(6, 5), 63, -12), (5, 10, 10), FUR, rotation=(0, 0, 15 * s),
                  origin=(6.5 * s, 72, -7), mirror=s == -1, detail=fur_fringe)
        # tall pricked ears in two tapering pieces, leaning out and a little forward
        ear_o = (7 * s, 80, -4.5)
        head.cube(f"ear_{side}", (x(4, 6), 79, -6.5), (6, 6, 3), FUR, rotation=(-8, 0, -18 * s), origin=ear_o, mirror=s == -1,
                  detail=ear_detail)
        head.cube(f"ear_tip_{side}", (x(5.5, 4), 85, -6), (4, 3, 2), FUR_DARK, rotation=(-8, 0, -18 * s), origin=ear_o,
                  mirror=s == -1)
        head.cube(f"ear_point_{side}", (x(6.5, 2), 88, -5.5), (2, 2, 1), FUR_DARK, rotation=(-8, 0, -18 * s), origin=ear_o,
                  mirror=s == -1)
        # the upper canines, hanging past the lower jaw
        head.cube(f"fang_{side}", (x(4, 1), 63.5, -22.2), (1, 4, 1), TOOTH)
    head.cube("snout", (-5, 69, -23), (10, 6, 11), MUZZLE, detail=both(muzzle_detail, rounded(0.85, 0.9)))
    head.cube("snout_bridge", (-4, 75, -21), (8, 2, 9), FUR, detail=rounded(0.8, 1.0))
    head.cube("nose", (-2.5, 73.5, -24.5), (5, 3, 2), NOSE, detail=nose_detail)
    head.cube("lip", (-5.5, 67, -22.5), (11, 2, 10), LIP, detail=lip_detail)
    head.cube("jowls", (-8.5, 61, -13), (17, 6, 13), SKIN, detail=both(jowl_detail, rounded(0.75, 0.75)))
    # a crown of coins: a gold band round his head, coins standing up from its front, a ruby in the middle
    head.cube("crown", (-7, 80, -11), (14, 3, 12), GOLD, inflate=0.3, detail=rivets)
    for i, (cx, h) in enumerate(((-7.5, 4), (-4.5, 6), (-1.5, 8), (1.5, 6), (4.5, 4))):
        head.cube(f"crown_coin_{i}", (cx, 83, -11.6), (3, h, 1), COINS, rotation=(0, 0, (i - 2) * -6),
                  origin=(cx + 1.5, 83, -11), detail=ruby if i == 2 else coin_face)
    jaw = head.bone("jaw", (0, 66, -14))
    jaw.cube("jaw", (-4, 63, -21.5), (8, 4, 9), MUZZLE, detail=jaw_detail)
    jaw.cube("chin", (-3.5, 61.5, -20), (7, 2, 6), MUZZLE)
    for side, s in (("r", 1), ("l", -1)):
        jaw.cube(f"lower_fang_{side}", ((2.5 if s == 1 else -3.5), 66, -21.8), (1, 2, 1), TOOTH)

    # ------------------------------------------------------------------------------------- legs and arms
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        leg = body.bone(f"{side}_leg", (10 * s, 18, 0))
        # short legs in velvet breeches, a gold garter at the knee, then the wolf's furred shank and a broad paw
        leg.cube(f"{side}_leg", (x(5, 11), 9, -7), (11, 10, 13), VELVET, mirror=mirror, detail=rounded(0.75, 0.8))
        leg.cube(f"{side}_garter", (x(5, 11), 8, -7), (11, 2, 13), GOLD, inflate=0.25, mirror=mirror)
        leg.cube(f"{side}_shank", (x(6, 9), 3, -5), (9, 6, 10), FUR, mirror=mirror, detail=rounded(0.8, 0.85))
        leg.cube(f"{side}_heel", (x(6.5, 8), 1, 3), (8, 4, 3), FUR_DARK, mirror=mirror)
        leg.cube(f"{side}_foot", (x(5.5, 10), 0, -8), (10, 3, 11), FUR_DARK, mirror=mirror, detail=rounded(0.85, 0.75))
        for i in range(3):
            tx = 5.5 + i * 3.5
            leg.cube(f"{side}_toe_{i}", (x(tx, 3), 0, -11), (3, 3, 3), FUR_DARK, mirror=mirror, detail=toe_creases)
            leg.cube(f"{side}_claw_{i}", (x(tx + 1, 1), 0, -12.5), (1, 1, 2), CLAW, mirror=mirror)

        arm = body.bone(f"{side}_arm", (22 * s, 60, 0))
        # pauldrons of gold: a riveted plate, then two tiers of coins, each lower one further out
        arm.cube(f"{side}_shoulder", (x(17, 13), 60, -8), (13, 5, 16), GOLD, inflate=0.3, rotation=(0, 0, -12 * s),
                 origin=(22 * s, 62, 0), mirror=mirror, detail=rivets)
        arm.cube(f"{side}_pauldron_mid", (x(18, 13), 55.5, -8.5), (13, 5, 17), COINS, rotation=(0, 0, -14 * s),
                 origin=(22 * s, 58, 0), mirror=mirror, detail=both(coin_rows(), under_shadow(1, 0.6)))
        arm.cube(f"{side}_pauldron_low", (x(19.5, 12), 51.5, -9), (12, 5, 18), COINS, rotation=(0, 0, -16 * s),
                 origin=(22 * s, 54, 0), mirror=mirror, detail=both(coin_rows(), under_shadow(1, 0.6)))
        # a heavy upper arm with the wolf's fur down its outside, and a fat elbow
        arm.cube(f"{side}_upper", (x(20.5, 10), 41, -5.5), (10, 14, 11), SKIN, mirror=mirror, detail=rounded(0.72, 0.85))
        arm.cube(f"{side}_arm_fur", (x(29.5, 2), 42, -4), (2, 11, 8), FUR, mirror=mirror, detail=fur_fringe)
        arm.cube(f"{side}_elbow", (x(21, 9), 38, -3.5), (9, 5, 9), SKIN, mirror=mirror, detail=rounded(0.75, 0.8))
        fore = arm.bone(f"{side}_forearm", (25 * s, 41, 0), rotation=(20, 0, 0))
        # the forearm tapers to a thick wrist stacked with gold bangles
        fore.cube(f"{side}_fore", (x(20, 10), 32, -5), (10, 10, 10), SKIN, mirror=mirror, detail=rounded(0.72, 0.85))
        fore.cube(f"{side}_wrist", (x(20.5, 9), 24, -4.5), (9, 9, 9), SKIN, mirror=mirror, detail=rounded(0.75, 0.85))
        fore.cube(f"{side}_fore_fur", (x(28.5, 2), 30, -3), (2, 11, 7), FUR, mirror=mirror, detail=fur_fringe)
        fore.cube(f"{side}_cuff", (x(20, 10), 28, -5), (10, 2, 10), GOLD, inflate=0.3, mirror=mirror)
        fore.cube(f"{side}_bangle", (x(20, 10), 25, -5), (10, 2, 10), GOLD, inflate=0.35, mirror=mirror)
        hand = fore.bone(f"{side}_hand", (25 * s, 24, 0))
        # a fist like a money bag: the back of the hand, four fat curled fingers stepping up toward the little one,
        # a knuckle ridge with claws, a thumb across the front, and rings on the fingers
        hand.cube(f"{side}_fist", (x(19.5, 11), 16, -5.5), (11, 8, 11), SKIN, mirror=mirror, detail=rounded(0.8, 0.9))
        for i in range(4):
            fz = -6 + i * 3
            fw = 10 if i < 3 else 9
            hand.cube(f"{side}_finger_{i}", (x(19, fw), 12 + i * 0.5, fz), (fw, 5, 3), SKIN, mirror=mirror, detail=knuckles)
            hand.cube(f"{side}_knuckle_{i}", (x(29, 2), 14 + i * 0.5, fz + 0.5), (2, 3, 2), SKIN, mirror=mirror)
            hand.cube(f"{side}_claw_{i}", (x(18, 1), 13 + i * 0.5, fz + 1), (1, 2, 1), CLAW, mirror=mirror)
        hand.cube(f"{side}_thumb", (x(20, 6), 15, -8), (6, 4, 3), SKIN, rotation=(0, 0, 20 * s), origin=(25 * s, 17, -6.5),
                  mirror=mirror, detail=rounded(0.8, 0.85))
        for i, fi in enumerate((0, 2)):
            hand.cube(f"{side}_ring_{i}", (x(24, 2), 12 + fi * 0.5, -6 + fi * 3), (2, 5, 3), GOLD, inflate=0.35, mirror=mirror,
                      detail=ring_gem)
        hand.cube(f"{side}_signet", (x(21, 2), 14, -8.4), (2, 4, 3), GOLD, inflate=0.3, rotation=(0, 0, 20 * s),
                  origin=(25 * s, 17, -6.5), mirror=mirror, detail=ring_gem)

    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)


def mirror(v):
    return (v[0], -v[1], -v[2])


def arms(a, keys, forearm=None):
    a.rot("right_arm", keys)
    a.rot("left_arm", [(t, mirror(v)) for t, v in keys])
    if forearm:
        a.rot("right_forearm", forearm)
        a.rot("left_forearm", [(t, mirror(v)) for t, v in forearm])


def animations(m):
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("body", [(0, ZERO), (2, (0, 0.8, 0)), (4, ZERO)])
    idle.rot("head", [(0, ZERO), (1, (0, 12, 0)), (2, (-4, 0, 0)), (3, (0, -12, 0)), (4, ZERO)])
    idle.rot("jaw", [(0, ZERO), (1.0, (-6, 0, 0)), (1.3, ZERO), (2.5, (-8, 0, 0)), (2.8, ZERO), (4, ZERO)])
    # rubbing his fists together over his belly
    arms(idle, [(0, (20, 0, -10)), (1, (24, 0, -14)), (2, (20, 0, -10)), (3, (24, 0, -14)), (4, (20, 0, -10))],
         forearm=[(0, (40, 0, 0)), (1, (44, 0, 0)), (2, (40, 0, 0)), (3, (44, 0, 0)), (4, (40, 0, 0))])

    # a waddle: the belly rolls from side to side
    walk = m.animation("walk", 1.6, loop=True)
    walk.rot("body", [(0, (0, 0, 5)), (0.8, (0, 0, -5)), (1.6, (0, 0, 5))])
    walk.pos("body", [(0, ZERO), (0.4, (0, -1, 0)), (0.8, ZERO), (1.2, (0, -1, 0)), (1.6, ZERO)])
    walk.rot("right_leg", [(0, (25, 0, 0)), (0.8, (-25, 0, 0)), (1.6, (25, 0, 0))])
    walk.rot("left_leg", [(0, (-25, 0, 0)), (0.8, (25, 0, 0)), (1.6, (-25, 0, 0))])
    arms(walk, [(0, (-15, 0, 0)), (0.8, (15, 0, 0)), (1.6, (-15, 0, 0))])

    # his fist, raised high and brought down: the white flash is the moment to block
    fist = m.animation("fist", 1.6)
    fist.rot("right_arm", [(0, ZERO), (0.6, (170, 0, 15)), (0.95, (60, 0, 0)), (1.2, (55, 0, 0)), (1.6, ZERO)])
    fist.rot("right_forearm", [(0, ZERO), (0.6, (20, 0, 0)), (0.95, (-5, 0, 0)), (1.6, ZERO)])
    fist.rot("body", [(0, ZERO), (0.6, (6, -15, 0)), (0.95, (-10, 20, 0)), (1.6, ZERO)])
    fist.rot("head", [(0, ZERO), (0.6, (-10, 0, 0)), (0.95, (12, 0, 0)), (1.6, ZERO)])
    fist.rot("jaw", [(0, ZERO), (0.9, (-30, 0, 0)), (1.2, ZERO)])
    fist.event(0.45, "flash").event(0.95, "hit")

    # Weight of Gold: he flings handfuls of gold into the air, and it comes down where the red circles are
    gold = m.animation("gold", 2.4)
    arms(gold, [(0, ZERO), (0.5, (30, 0, 0)), (0.8, (160, 0, 30)), (1.2, (150, 0, 35)), (1.6, (40, 0, 10)), (2.4, ZERO)],
         forearm=[(0, ZERO), (0.5, (60, 0, 0)), (0.8, (0, 0, 0)), (2.4, ZERO)])
    gold.rot("head", [(0, ZERO), (0.8, (-30, 0, 0)), (1.4, (-25, 0, 0)), (2.4, ZERO)])
    gold.rot("jaw", [(0, ZERO), (0.8, (-35, 0, 0)), (1.4, (-30, 0, 0)), (1.8, ZERO)])
    gold.rot("body", [(0, ZERO), (0.8, (-8, 0, 0)), (1.6, (4, 0, 0)), (2.4, ZERO)])
    gold.event(0.8, "throw").event(1.6, "rain")

    # the Lunge: a crouch, then he hurls his bulk along the red line and lands on his belly
    lunge = m.animation("lunge", 1.8)
    lunge.rot("body", [(0, ZERO), (0.6, (12, 0, 0)), (0.75, (-35, 0, 0)), (1.2, (-40, 0, 0)), (1.8, ZERO)])
    lunge.pos("body", [(0, ZERO), (0.6, (0, -4, 0)), (0.75, (0, 3, 0)), (1.2, (0, -6, 0)), (1.8, ZERO)])
    arms(lunge, [(0, ZERO), (0.6, (-40, 0, 0)), (0.75, (150, 0, 20)), (1.2, (110, 0, 25)), (1.8, ZERO)])
    lunge.rot("right_leg", [(0, ZERO), (0.6, (40, 0, 0)), (0.75, (-30, 0, 0)), (1.8, ZERO)])
    lunge.rot("left_leg", [(0, ZERO), (0.6, (40, 0, 0)), (0.75, (-30, 0, 0)), (1.8, ZERO)])
    lunge.event(0.7, "leap").event(1.2, "land")

    # Pape Satàn: he swells, throws his head back, and shrieks (purple: it can't be blocked)
    pape = m.animation("pape", 2.6)
    pape.rot("head", [(0, ZERO), (0.8, (-35, 0, 0)), (1.2, (15, 0, 0)), (1.4, (10, 6, 0)), (1.6, (12, -6, 0)), (1.8, (10, 6, 0)), (2.0, (12, -6, 0)), (2.6, ZERO)])
    pape.rot("jaw", [(0, ZERO), (0.8, (-10, 0, 0)), (1.2, (-60, 0, 0)), (2.0, (-55, 0, 0)), (2.4, ZERO)])
    pape.pos("body", [(0, ZERO), (0.8, (0, 3, 0)), (1.2, (0, -1, 0)), (2.6, ZERO)])
    arms(pape, [(0, ZERO), (0.8, (10, 0, 60)), (1.2, (-10, 0, 85)), (2.0, (-10, 0, 80)), (2.6, ZERO)])
    pape.event(1.2, "shriek")

    stagger = m.animation("stagger", 2.0)
    stagger.rot("body", [(0, ZERO), (0.15, (-14, 0, 6)), (0.8, (-10, 0, -6)), (1.4, (-6, 0, 4)), (2.0, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.15, (-25, 0, 0)), (1.0, (-15, 15, 0)), (2.0, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.15, (-30, 0, 0)), (1.4, (-15, 0, 0)), (2.0, ZERO)])
    arms(stagger, [(0, ZERO), (0.15, (-30, 0, 50)), (1.4, (-20, 0, 40)), (2.0, ZERO)])

    # at half his health: he beats his belly like a drum and roars, and the great coins of the Mint start to roll
    phase = m.animation("phase", 3.2)
    arms(phase, [(0, ZERO), (0.4, (60, 0, 10)), (0.6, (20, 0, -10)), (0.8, (60, 0, 10)), (1.0, (20, 0, -10)),
                 (1.4, (-20, 0, 80)), (2.6, (-20, 0, 80)), (3.2, ZERO)],
         forearm=[(0, ZERO), (0.4, (60, 0, 0)), (1.0, (60, 0, 0)), (1.4, ZERO), (3.2, ZERO)])
    phase.rot("head", [(0, ZERO), (1.4, (-35, 0, 0)), (2.6, (-35, 0, 0)), (3.2, ZERO)])
    phase.rot("jaw", [(0, ZERO), (1.4, (-55, 0, 0)), (2.6, (-55, 0, 0)), (3.0, ZERO)])
    phase.pos("body", [(0, ZERO), (1.4, (0, 2, 0)), (2.6, (0, 2, 0)), (3.2, ZERO)])
    phase.event(1.4, "roar").event(2.2, "mint")

    # slumped over his hoard, he lifts his head, heaves himself up, and shrieks his welcome
    intro = m.animation("intro", 6.0)
    intro.rot("body", [(0, (30, 0, 0)), (0.8, (30, 0, 0)), (2.6, (-5, 0, 0)), (3.0, ZERO)])
    intro.pos("body", [(0, (0, -12, 0)), (0.8, (0, -12, 0)), (2.6, ZERO)])
    intro.rot("head", [(0, (35, 0, 0)), (1.2, (35, 0, 0)), (2.0, (0, 20, 0)), (2.6, (0, -20, 0)), (3.2, (-30, 0, 0)), (4.8, (-30, 0, 0)), (5.6, ZERO)])
    intro.rot("jaw", [(0, ZERO), (3.2, (-5, 0, 0)), (3.4, (-60, 0, 0)), (4.8, (-55, 0, 0)), (5.2, ZERO)])
    arms(intro, [(0, (60, 0, -20)), (1.2, (60, 0, -20)), (2.6, (20, 0, 0)), (3.4, (-15, 0, 80)), (4.8, (-15, 0, 78)), (5.6, ZERO)],
         forearm=[(0, (60, 0, 0)), (2.6, (20, 0, 0)), (5.6, ZERO)])
    intro.event(0.8, "rise").event(3.4, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("body", [(0, ZERO), (0.1, (-4, 0, 0)), (0.35, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (-8, 0, 0)), (0.35, ZERO)])

    # "as sails swollen by the wind fall in a heap when the mast breaks": he sags and collapses backward
    death = m.animation("death", 3.6)
    death.rot("body", [(0, ZERO), (0.6, (-8, 0, 0)), (1.8, (-70, 0, 6)), (3.6, (-82, 0, 8))])
    death.pos("body", [(0, ZERO), (1.8, (0, -8, 6)), (3.6, (0, -12, 8))])
    death.rot("head", [(0, ZERO), (0.6, (-30, 0, 0)), (1.8, (20, 0, 0)), (3.6, (25, 0, 0))])
    death.rot("jaw", [(0, ZERO), (0.6, (-50, 0, 0)), (3.6, (-30, 0, 0))])
    arms(death, [(0, ZERO), (0.6, (-30, 0, 60)), (1.8, (-10, 0, 70)), (3.6, (-5, 0, 75))])
    death.event(1.8, "fall")


if __name__ == "__main__":
    build().write()
