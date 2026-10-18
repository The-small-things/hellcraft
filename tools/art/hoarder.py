#!/usr/bin/env python3
"""The hoarder (Greed's monster): a hunched miser bent under a sack of coin, sallow-skinned, with eyes that shine
gold in the dark. Gold draws them: the more you carry, the more come, and their grasping blow steals it (they drop
it when killed). Dante's misers and prodigals roll great weights at each other with their chests; these carry theirs.

Run from the repo root:  python3 tools/art/hoarder.py && python3 tools/gen_bosses.py

Built like a starved old man: knees bent under the load, a narrow waist under a ragged tunic and a shawl, a scrawny
neck craning the hooded head up to look ahead, a hooked nose, sunken eyes and a jutting chin. The arms are long and
thin in two segments (the forearms are their own bones, a little bent at rest) with bony hands: a palm, a thumb and
three hooked fingers, a gold bangle at each wrist. The sack is lumpy burlap tied at the neck, bulging with coin, a
candlestick and a cup poking out of its mouth, held on by ropes over his shoulders.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="9c9868", dark="6b6844")
RAGS = material("rags", "robe", base="5a4a32", fold="3c3020")
TROUSERS = material("trousers", "robe", base="3e3428", fold="2a221a")
SHAWL = material("shawl", "robe", base="4e3a2a", fold="33251a")
HOOD = material("hood", "robe", base="5a4834", fold="3a2e1e")
WRAP = material("wrap", "robe", base="7a7058", fold="5a5040")
SACK = material("sack", "flat", base="a08a5c")
GOLD = material("gold", "metal", base="d4a83a", high="f6dc80", low="8a6a1e")
ROPE = material("rope", "flat", base="7a6040")
LEATHER = material("leather", "flat", base="4a3020")
CLAW = material("claw", "flat", base="2a2620")

EYE = hexc("ffd84a")
EYE_HOT = hexc("fff2b0")
SOCKET = hexc("2a2414")
MOUTH = hexc("2a1a10")
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
    """The side faces shaded along their lowest rows (where the part sinks into shadow) and their highest (where it
    tucks under the part above)."""
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
            if d:
                d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def face(fs):
    """Sunken sockets under the brow with gold eyes in them, hollow cheeks and the lines beside the nose."""
    f = fs["front"]
    w = f.w
    for x in range(w):
        mul(f, x, 2, 0.6)  # the shadow under the brow
    for cx in (1, w - 3):
        f.px(cx, 3, SOCKET)
        f.px(cx + 1, 3, SOCKET)
        f.glow(cx + (1 if cx < w // 2 else 0), 3, EYE)
        mul(f, cx, 4, 0.88)  # bags under the eyes
        mul(f, cx + 1, 4, 0.92)
    for y in (4, 5):  # hollow cheeks
        mul(f, 0, y, 0.7)
        mul(f, w - 1, y, 0.7)
    for n in ("left", "right"):  # the temples sink in
        g = fs[n]
        for y in range(2, 5):
            for x in range(g.w):
                mul(g, x, y, 0.85)


def brow(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.12))
    for x in (1, 2, f.w - 3, f.w - 2):
        f.px(x, 0, shade(SKIN.c["dark"], 0.7))  # wiry, scowling brows
    fs["bottom"].fill(lambda x, y: SOCKET)


def nose(fs):
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            mul(g, 0, y, 0.8)
    f = fs["front"]
    f.px(f.w // 2, 0, shade(SKIN.c["base"], 1.15))


def nose_tip(fs):
    f = fs["front"]
    f.px(0, 0, shade(SKIN.c["base"], 1.12))  # the light on the hooked tip
    mul(f, 1, f.h - 1, 0.8)
    b = fs["bottom"]
    b.fill(lambda x, y: shade(SKIN.c["dark"], 0.8))
    b.px(0, b.h - 1, MOUTH)  # the nostrils
    b.px(b.w - 1, b.h - 1, MOUTH)


def jaw(fs):
    """A thin-lipped mouth with a gold tooth, stubble on the chin, and shadow under the jaw."""
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, MOUTH)
    f.px(f.w // 2 - 1, 0, GOLD.c["high"])
    f.px(f.w // 2 + 1, 0, shade(hexc("c8b890"), 0.8))
    for x in range(0, f.w, 2):
        mul(f, x, 1, 0.85)
    for n in ("left", "right", "bottom"):
        g = fs[n]
        for x in range(g.w):
            for y in range(g.h):
                mul(g, x, y, 0.75)


def neck(fs):
    """Stringy tendons and an apple down the front of a scrawny neck."""
    f = fs["front"]
    for y in range(f.h):
        mul(f, 0, y, 0.7)
        mul(f, f.w - 1, y, 0.7)
    f.px(f.w // 2, 1, shade(SKIN.c["base"], 1.12))


def hood(fs):
    """The hood's opening in shadow, its folds darker toward the back."""
    for n in ("front",):
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                mul(f, x, y, 0.75 if y == f.h - 1 else 1.0)
    for n in ("left", "right"):
        g = fs[n]
        front = g.w - 1 if n == "right" else 0
        for y in range(g.h):
            mul(g, front, y, 0.8)


def hood_side(fs):
    """The inner edge by the face, deep in shadow, and the cloth's folds sweeping back."""
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                if (x + y) % 3 == 0:
                    mul(g, x, y, 0.85)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            mul(g, g.w - 1 if n == "right" else 0, y, 0.85)
    f = fs["front"]
    for y in range(f.h):
        for x in range(f.w):
            mul(f, x, y, 0.7)


def rags(bottom_rows=1):
    """A ragged hem: the lowest row of the sides torn into tatters (some pixels gone), stitched patches above."""
    def detail(fs):
        for n in SIDES:
            f = fs[n]
            for x in range(f.w):
                r = f.rng.random()
                if r < 0.35:
                    f.px(x, f.h - 1, (0, 0, 0), alpha=0)
                elif r < 0.6:
                    mul(f, x, f.h - 1, 0.7)
            if f.w >= 4 and f.h >= 4 and f.rng.random() < 0.6:
                px_, py = f.rng.randrange(0, f.w - 2), f.rng.randrange(0, max(1, f.h - 3))
                patch = shade(f.cube.material.c["base"], 1.2)
                for yy in range(2):
                    for xx in range(2):
                        f.px(px_ + xx, py + yy, patch)
                f.px(px_ + 2, py, hexc("2a2014"))  # a stitch
    return detail


def tunic(fs):
    """The tunic: creased at the waist, worn shiny on the front, the edges of the patches stitched."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if x % 3 == 1:
                mul(f, x, 0, 0.85)


def belt(fs):
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid, y, GOLD.c["low"])  # a tarnished buckle
    f.px(mid - 1, 0, GOLD.c["base"])
    f.px(mid + 1, 0, GOLD.c["base"])


def purse(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            f.px(x, 0, GOLD.c["base"] if x % 2 else GOLD.c["low"])  # the drawstring
    fs["top"].fill(lambda x, y: GOLD.c["high"] if (x + y) % 2 else GOLD.c["base"])


def rope(fs):
    """Twisted rope: a diagonal lay of light and dark."""
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 2 == 0:
                    mul(f, x, y, 0.75)
                else:
                    mul(f, x, y, 1.1)


def burlap(fs):
    """Coarse burlap: a weave of lighter and darker threads, a seam, a sewn patch, and coins showing through a tear,
    darker where it sags into the ground and where it rests against his back."""
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if (x % 2 == 0) != (y % 2 == 0):
                    mul(f, x, y, 0.88)
                if y % 4 == 3 and x % 2:
                    mul(f, x, y, 0.92)
        if f.name in ("left", "right", "back") and f.w >= 6 and f.h >= 6:
            seam = f.w // 2 + (f.rng.randrange(3) - 1)
            for y in range(f.h):
                f.px(seam, y, shade(SACK.c["base"], 0.62 if y % 2 else 0.75))
            # a patch, its stitches
            px_, py = f.rng.randrange(1, f.w - 4), f.rng.randrange(1, f.h - 4)
            for yy in range(3):
                for xx in range(3):
                    f.px(px_ + xx, py + yy, shade(hexc("8a7448"), 1.0 - 0.06 * yy))
            for xx in range(-1, 4):
                f.px(px_ + xx, py - 1, hexc("3a2c18") if xx % 2 else shade(hexc("8a7448"), 0.8))
            # a tear with coin behind it
            tx, ty = f.rng.randrange(1, f.w - 2), f.rng.randrange(f.h // 2, f.h - 2)
            f.px(tx, ty, GOLD.c["high"])
            f.px(tx + 1, ty, GOLD.c["base"])
            f.px(tx, ty + 1, GOLD.c["low"])
        if f.name in SIDES:
            for x in range(f.w):
                for i in range(2):
                    mul(f, x, f.h - 1 - i, 0.75 + 0.12 * i)


def sack_top(fs):
    """Where the sack gathers at its neck: deep folds drawing in."""
    burlap(fs)
    t = fs["top"]
    cx, cy = t.w / 2, t.h / 2
    for y in range(t.h):
        for x in range(t.w):
            if int(abs(x - cx) + abs(y - cy)) % 2 == 0:
                mul(t, x, y, 0.8)


def coins(fs):
    """A heap of coin: round faces, bright rims and dark gaps between them."""
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                k = (x * 7 + y * 3 + f.rng.randrange(2)) % 5
                f.px(x, y, (GOLD.c["high"], GOLD.c["base"], GOLD.c["base"], GOLD.c["low"], shade(GOLD.c["low"], 0.7))[k])


def palm(fs):
    """Knuckles and tendons on the back of the hand."""
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            mul(g, g.w // 2, y, 0.85)
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, g.h - 1, 0.85)


def finger(fs):
    """A bony finger ending in a long, dirty nail."""
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            g.px(x, g.h - 1, CLAW.c["base"])
            mul(g, x, 0, 0.85)
    fs["bottom"].fill(lambda x, y: CLAW.c["base"])


def limb(fs):
    """A thin limb: a tendon down the inside, the knobbly joint at its end."""
    for n in ("front", "back"):
        g = fs[n]
        for y in range(g.h):
            mul(g, 0, y, 0.85)
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, g.h - 1, 0.85)


def toes(fs):
    f = fs["front"]
    for x in range(f.w):
        if x % 1 == 0 and x < f.w:
            f.px(x, f.h - 1, CLAW.c["base"])  # the long yellow nails, dirty
    for x in range(1, f.w, 2):
        for y in range(f.h):
            mul(f, x, y, 0.75)
    fs["top"].fill(lambda x, y: shade(SKIN.c["base"], 0.95 if x % 2 else 0.8))


def foot(fs):
    """Feet bound in filthy rag wrappings: the bands run round the foot, darker toward the ground."""
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if (x + y * 2) % 4 == 0:
                    mul(f, x, y, 0.72)
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, g.h - 1, 0.75)


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("hoarder", texture_width=128)
    # the legs bend at the knee under the weight: the thigh angles forward, the shin stands upright again (its bone
    # turns back by as much), so the feet sit under the knees, flat on the ground
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0), rotation=(20, 0, 0))
        leg.cube(f"{side}_leg", (x(0.5, 3), 6, -1.5), (3, 7, 3), TROUSERS, mirror=s == -1, detail=ao(top=1, dark=0.75))
        leg.cube(f"{side}_knee", (x(0.7, 3), 5, -1.9), (3, 2, 2), TROUSERS, inflate=-0.2, mirror=s == -1, detail=ao(bottom=1))
        shin = leg.bone(f"{side}_shin", (2 * s, 6, 0), rotation=(-20, 0, 0))
        shin.cube(f"{side}_shin", (x(1, 2), 0.5, -1), (2, 6, 2), SKIN, mirror=s == -1, detail=limb)
        shin.cube(f"{side}_hem", (x(0.5, 3), 3.6, -1.5), (3, 2, 3), TROUSERS, mirror=s == -1, detail=rags())
        shin.cube(f"{side}_foot", (x(0.5, 3), -0.36, -3.2), (3, 2, 4), WRAP, mirror=s == -1, detail=foot)
        shin.cube(f"{side}_toes", (x(0.5, 3), -0.36, -4.2), (3, 1, 1), SKIN, mirror=s == -1, detail=toes)

    body = m.bone("body", (0, 12, 0), rotation=(-35, 0, 0))
    # a narrow waist under the tunic, the gaunt chest above, the shawl over the shoulders
    body.cube("hips", (-3.5, 10.5, -2.5), (7, 4, 5), RAGS, detail=both(rags(), ao(top=1)))
    body.cube("belt", (-3.5, 13.5, -2.5), (7, 1, 5), LEATHER, inflate=0.2, detail=belt)
    body.cube("purse", (1.5, 11.2, -3.6), (2, 3, 1), LEATHER, inflate=0.2, detail=purse)
    body.cube("torso", (-3, 14.5, -2), (6, 4, 4), RAGS, detail=both(tunic, ao(bottom=1)))
    body.cube("chest", (-3.5, 17.5, -2.4), (7, 4, 5), RAGS, detail=both(tunic, ao(bottom=1)))
    body.cube("shawl", (-4.5, 20.5, -2.6), (9, 3, 5), SHAWL, detail=rags())
    # the ropes that hold the sack, down the chest and over the shoulders
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_rope", (side_x(1.6, 1, s), 15, -3.1), (1, 9, 1), ROPE, detail=rope)
        body.cube(f"{side}_rope_over", (side_x(1.6, 1, s), 23.2, -3.1), (1, 1, 7), ROPE, detail=rope)

    sack = body.bone("sack", (0, 20, 3))
    sack.cube("sack", (-5.5, 14, 2.2), (11, 10, 9), SACK, detail=burlap)
    sack.cube("sack_bottom", (-4.5, 13, 3), (9, 1, 7), SACK, detail=burlap)
    sack.cube("sack_upper", (-4.5, 23.6, 2.8), (9, 2, 8), SACK, detail=sack_top)
    sack.cube("sack_neck", (-2, 25.2, 4.5), (4, 2, 4), SACK, detail=sack_top)
    sack.cube("sack_tie", (-2, 25.6, 4.5), (4, 1, 4), ROPE, inflate=0.3, detail=rope)
    sack.cube("sack_coins", (-1.5, 27, 5), (3, 1, 3), GOLD, detail=coins)
    sack.cube("sack_bulge", (-4.5, 15, 10.6), (9, 8, 1), SACK, detail=burlap)
    for side, s in (("right", 1), ("left", -1)):
        sack.cube(f"{side}_sack_lump", (side_x(5.2, 1, s), 15.5, 4), (1, 6, 5), SACK, detail=burlap)
    sack.cube("candlestick", (0.5, 26.2, 6), (1, 5, 1), GOLD, rotation=(-15, 0, -20), origin=(1, 27, 6.5))
    sack.cube("candle_cup", (0, 30.3, 5.5), (2, 1, 2), GOLD, rotation=(-15, 0, -20), origin=(1, 27, 6.5))
    sack.cube("chalice", (-3.2, 26, 5.6), (2, 2, 2), GOLD, rotation=(10, 0, 18), origin=(-2.2, 26, 6.6))

    # the head: a scrawny neck craning the hooded face up to see ahead (the bone tips it back against the stoop)
    head = body.bone("head", (0, 23, -1), rotation=(30, 0, 0))
    head.cube("neck", (-1.5, 21.5, -3), (3, 3, 3), SKIN, detail=neck)
    head.cube("head", (-3, 24, -6), (6, 6, 6), SKIN, detail=face)
    head.cube("brow", (-3, 27.5, -6.5), (6, 1, 1), SKIN, detail=brow)
    head.cube("nose", (-0.5, 25.4, -7.2), (1, 2, 2), SKIN, detail=nose)
    head.cube("nose_tip", (-1, 24.4, -8), (2, 2, 2), SKIN, detail=nose_tip)
    head.cube("jaw", (-2.5, 23, -6.3), (5, 2, 4), SKIN, detail=jaw)
    head.cube("chin", (-1, 22.4, -6.7), (2, 1, 2), SKIN, detail=ao(bottom=1))
    head.cube("hood", (-3.5, 28.5, -6.6), (7, 2, 7), HOOD, inflate=0.2, detail=hood)
    for side, s in (("right", 1), ("left", -1)):
        head.cube(f"{side}_hood", (side_x(3, 1, s), 24, -6.4), (1, 5, 5), HOOD, inflate=0.2, mirror=s == -1, detail=hood_side)
    head.cube("hood_back", (-3.5, 24.5, -1.6), (7, 5, 2), HOOD, inflate=0.2, detail=ao(bottom=1))
    head.cube("hood_drape", (-3, 21.5, -1.2), (6, 4, 2), HOOD, detail=rags())

    # long thin arms: a sleeved upper arm, a bare forearm (its own bone, bent a little), a bony clawed hand
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        arm = body.bone(f"{side}_arm", (5 * s, 21, -1), rotation=(25, 0, 0))
        arm.cube(f"{side}_arm", (x(4.5, 2), 15, -2), (2, 7, 2), SKIN, mirror=s == -1, detail=limb)
        arm.cube(f"{side}_sleeve", (x(4, 3), 17.5, -2.5), (3, 5, 3), RAGS, mirror=s == -1, detail=rags())
        fore = arm.bone(f"{side}_forearm", (5.5 * s, 15.5, -1), rotation=(15, 0, 0))
        fore.cube(f"{side}_forearm", (x(4.5, 2), 10.5, -2), (2, 5, 2), SKIN, inflate=-0.1, mirror=s == -1, detail=limb)
        fore.cube(f"{side}_ring", (x(4.5, 2), 11, -2), (2, 1, 2), GOLD, inflate=0.3, mirror=s == -1)
        fore.cube(f"{side}_hand", (x(5, 1), 8.6, -2.5), (1, 2, 3), SKIN, inflate=0.1, mirror=s == -1, detail=palm)
        for i, z in enumerate((-2.5, -1.5, -0.5)):
            fore.cube(f"{side}_claw{i}", (x(5, 1), 6.8 + 0.3 * abs(i - 1), z), (1, 2, 1), SKIN, mirror=s == -1, detail=finger,
                      rotation=(0, 0, -18 * s), origin=(5.5 * s, 8.7, z + 0.5))
        fore.cube(f"{side}_thumb", (x(4.4, 1), 7.6, -3.1), (1, 2, 1), SKIN, mirror=s == -1, detail=finger,
                  rotation=(20, 0, 0), origin=(4.9 * s, 9.5, -2.6))

    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("body", [(0, ZERO), (1, (2, 0, 0)), (2, ZERO)])
    idle.rot("head", [(0, ZERO), (0.5, (0, 12, 0)), (1, ZERO), (1.5, (0, -12, 0)), (2, ZERO)])
    idle.rot("sack", [(0, ZERO), (1, (-3, 0, 0)), (2, ZERO)])
    # the fingers twitch, as if counting
    idle.rot("right_forearm", [(0, ZERO), (1, (6, 0, 0)), (2, ZERO)])
    idle.rot("left_forearm", [(0, (4, 0, 0)), (1, ZERO), (2, (4, 0, 0))])

    # driven by how fast he walks (the model scales it by his speed)
    walk = m.animation("walk", 1.0, loop=True)
    walk.rot("right_leg", [(0, (30, 0, 0)), (0.5, (-30, 0, 0)), (1, (30, 0, 0))])
    walk.rot("left_leg", [(0, (-30, 0, 0)), (0.5, (30, 0, 0)), (1, (-30, 0, 0))])
    # the knee bends as the foot comes forward again
    walk.rot("right_shin", [(0, ZERO), (0.5, (-5, 0, 0)), (0.75, (-35, 0, 0)), (1, ZERO)])
    walk.rot("left_shin", [(0, (-5, 0, 0)), (0.25, (-35, 0, 0)), (0.5, ZERO), (1, (-5, 0, 0))])
    walk.rot("right_arm", [(0, (-20, 0, 0)), (0.5, (20, 0, 0)), (1, (-20, 0, 0))])
    walk.rot("left_arm", [(0, (20, 0, 0)), (0.5, (-20, 0, 0)), (1, (20, 0, 0))])
    walk.pos("body", [(0, ZERO), (0.25, (0, -0.6, 0)), (0.5, ZERO), (0.75, (0, -0.6, 0)), (1, ZERO)])
    walk.rot("sack", [(0, (0, 0, 4)), (0.5, (0, 0, -4)), (1, (0, 0, 4))])

    # both claws snatch forward and draw the loot to his chest
    attack = m.animation("attack", 0.8)
    for arm in ("right", "left"):
        attack.rot(f"{arm}_arm", [(0, ZERO), (0.25, (-30, 0, 0)), (0.4, (95, 0, 0)), (0.65, (40, 0, 0)), (0.8, ZERO)])
        # the elbow cocks, straightens into the snatch, then folds to pull it in
        attack.rot(f"{arm}_forearm", [(0, ZERO), (0.25, (25, 0, 0)), (0.4, (-12, 0, 0)), (0.65, (55, 0, 0)), (0.8, ZERO)])
    attack.rot("body", [(0, ZERO), (0.25, (8, 0, 0)), (0.4, (-12, 0, 0)), (0.8, ZERO)])
    attack.rot("head", [(0, ZERO), (0.4, (15, 0, 0)), (0.8, ZERO)])
    attack.event(0.4, "grab")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (10, 0, 0)), (0.3, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (12, 0, 0)), (0.3, ZERO)])

    # he folds over his sack, and it tips and spills
    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.6, (-40, 0, 8)), (1.2, (-50, 0, 10))])
    death.pos("body", [(0, ZERO), (0.6, (0, -4, 0)), (1.2, (0, -6, 0))])
    death.rot("sack", [(0, ZERO), (0.8, (40, 0, 20)), (1.2, (55, 0, 25))])
    for arm in ("right_arm", "left_arm"):
        death.rot(arm, [(0, ZERO), (0.6, (20, 0, 0)), (1.2, (25, 0, 0))])
    death.rot("right_leg", [(0, ZERO), (0.6, (-20, 0, 0)), (1.2, (-25, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (0.6, (-15, 0, 0)), (1.2, (-20, 0, 0))])


if __name__ == "__main__":
    build().write()
