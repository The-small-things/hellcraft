#!/usr/bin/env python3
"""Lucifer as he first shows himself (Inferno XXXIV; Isaiah's Morning Star): the Fallen Seraph, the creature that "was
so fair as now he foul is". Tall, pale and calm, in the rags of a white and gold robe under tarnished plate, six wings
(three pairs, the way of the seraphim) of long overlapping feathers gone grey and scorched toward their tips, a cracked
halo of cold light, and a long blade of the same light, the Morning Star.

Run from the repo root:  python3 tools/art/fallen_seraph.py && python3 tools/gen_bosses.py

He is drawn 1.6 times his Blockbench size (about 5 blocks tall). The events time his fight (LuciferFight):
"flash" opens the window to block his blade and "hit" is when it lands; "sweep" and "swept" bound his wings' sweep;
"call" brings the falling stars; "go" his lunge; "snap" the moment he stops pretending (his half-health turn);
"land" and "speak" his descent; "break" the moment his seraph form shatters.

The blade is the torso's child but turns about his right shoulder, keyed exactly as his right arm is, so it stays in
his hand whatever the arm does.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="e8dcd0", dark="a89888")
ROBE = material("robe", "robe", base="e8e4d8", fold="a8a08c", trim="c8a040")
UNDER = material("under", "robe", base="c8c2b4", fold="8a8474")
GOLD = material("gold", "metal", base="c8a040", high="f0d070", low="7a5a18")
TARNISH = material("tarnish", "metal", base="9a8040", high="d8c070", low="4a3818")
HAIR = material("hair", "hair", light="fbf6e4", dark="c4b48c")
FEATHER = material("feather", "flat", base="ece8e0", edges=False)
COVERT = material("covert", "hair", light="f4f0e8", dark="9a948a")
BONE = material("bone", "flat", base="d8d0c0")
BLADE = material("blade", "flat", base="d8f0ff")
GRIP = material("grip", "flat", base="3a2a20")
HALO = hexc("fff0b0")
LIGHT = hexc("c8f0ff")
EYE = hexc("8ad8ff")
SOCKET = hexc("6a5a58")
ASH = hexc("4a4440")
SCORCH = hexc("2a2420")
CRACK = hexc("3a1a1a")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- painting helpers

def tint(f, x, y, k, toward=None, t=0.0):
    """Darkens (k < 1) or lightens one pixel of a face, leaving transparent pixels be; optionally mixes toward a colour."""
    if 0 <= x < f.w and 0 <= y < f.h:
        c = f.canvas.get(f.x + x, f.y + y)
        if c[3]:
            col = shade(tuple(c[:3]), k)
            if toward is not None:
                col = mix(col, toward, t)
            f.px(x, y, col)


def combo(*fns):
    def paint(faces):
        for fn in fns:
            fn(faces)
    return paint


def occlude(top=0, bottom=0, k=0.72, sides=SIDES):
    """Shadow where a part tucks under another (top rows) or meets one below (bottom rows), fading over the rows."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for i in range(top):
                for x in range(f.w):
                    tint(f, x, i, k + (1 - k) * i / max(1, top))
            for i in range(bottom):
                for x in range(f.w):
                    tint(f, x, f.h - 1 - i, k + (1 - k) * i / max(1, bottom))
    return paint


def ragged(rows=2, sides=SIDES, scorch=False):
    """A torn hem: the lowest rows bitten away unevenly, the edge left scorched or grey."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for x in range(f.w):
                cut = f.rng.choice([0] + list(range(rows + 1)))
                for y in range(f.h - cut, f.h):
                    f.px(x, y, (0, 0, 0), alpha=0)
                for i in range(1, 3):
                    tint(f, x, f.h - cut - i, 0.8 if i == 1 else 0.92, ASH if scorch else None, 0.5 if scorch and i == 1 else 0)
    return paint


def stained(n=4, sides=("front", "back")):
    """Old grime on white cloth."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for _ in range(n):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                for dx, dy in ((0, 0), (1, 0), (0, 1)):
                    tint(f, x + dx, y + dy, 0.85, ASH, 0.15)
    return paint


def glowing(colour):
    def paint(faces):
        for name in ("front", "back", "left", "right", "top", "bottom"):
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    f.glow(x, y, colour)
    return paint


def scratched(faces):
    """Tarnished plate: dents and scratches, the light gone out of the gold."""
    for name in ("front", "back", "left", "right", "top"):
        f = faces[name]
        for _ in range(max(1, f.w * f.h // 10)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            tint(f, x, y, f.rng.choice((0.7, 0.8, 1.2)))


def rimmed(faces):
    """A plate's rolled rim: lit along its top edge and dark under its lower one."""
    for name in SIDES:
        f = faces[name]
        for x in range(f.w):
            tint(f, x, 0, 1.2)
            tint(f, x, f.h - 1, 0.65)


# ----------------------------------------------------------------------------------------------- details

def face(faces):
    """A fine, cold face: shadowed sockets with eyes of cold light, a long straight nose's shadow, a thin mouth, and a
    crack of something dark running from one eye down the cheek (what he is becoming)."""
    f = faces["front"]
    w = f.w
    for x in range(w):
        tint(f, x, 1, 0.84)  # under the brow
    for cx in (0, w - 2):
        f.px(cx, 2, SOCKET)
        f.px(cx + 1, 2, SOCKET)
        f.glow(cx + (1 if cx == 0 else 0), 2, EYE)
        tint(f, cx, 3, 0.86)
        tint(f, cx + 1, 3, 0.9)
    tint(f, w // 2, 3, 0.86)  # beside the nose
    for x in range(1, w - 1):
        f.px(x, 4, shade(SKIN.c["dark"], 0.8))
    tint(f, w // 2, 4, 0.9)
    for x in range(w):
        tint(f, x, 5, 0.92)
    tint(f, w - 1, 3, 0.8, CRACK, 0.55)
    tint(f, w - 1, 4, 0.85, CRACK, 0.4)
    for name in ("left", "right"):
        s = faces[name]
        for y in range(s.h):
            tint(s, s.w - 1 if name == "right" else 0, y, 0.92)


def brow(faces):
    f = faces["front"]
    for x in range(f.w):
        tint(f, x, 0, 1.08)
    occlude(bottom=1, k=0.8)(faces)


def nose(faces):
    f = faces["front"]
    for y in range(f.h):
        tint(f, 0, y, 1.08)
    faces["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.8))


def strands(faces):
    """Long pale hair: strands down every side, darker underneath."""
    for name in SIDES:
        f = faces[name]
        for x in range(0, f.w, 2):
            for y in range(f.h):
                tint(f, x, y, 0.86)
        for x in range(f.w):
            tint(f, x, f.h - 1, 0.8)


def feather(scorch=0.5, fray=2):
    """A long flight feather: a pale shaft down its middle, barbs slanting off it, grey and then scorched toward the
    tip (`scorch`: how far up from the tip the burning reaches, as a share of its length), the tip frayed."""
    def paint(faces):
        for name in ("front", "back"):
            f = faces[name]
            mid = (f.w - 1) / 2
            for y in range(f.h):
                down = y / max(1, f.h - 1)
                for x in range(f.w):
                    c = FEATHER.c["base"]
                    if (x + y) % 3 == 0:
                        c = shade(c, 0.86)  # barbs
                    burn = max(0.0, (down - (1 - scorch)) / max(0.01, scorch))
                    c = mix(c, hexc("8a847a"), min(1.0, down * 1.1))
                    c = mix(c, SCORCH, burn * 0.85)
                    c = shade(c, 1.0 - 0.15 * abs(x - mid) / max(1, mid + 0.5))
                    f.px(x, y, c)
                if f.w >= 3:
                    f.px(int(mid), y, mix(BONE.c["base"], SCORCH, max(0.0, down - 0.6)))
            for x in range(f.w):
                cut = f.rng.randrange(0, fray + 1)
                for y in range(f.h - cut, f.h):
                    f.px(x, y, (0, 0, 0), alpha=0)
        for name in ("left", "right", "top", "bottom"):
            f = faces[name]
            f.fill(lambda x, y: shade(hexc("8a847a"), 0.8))
    return paint


def coverts(faces):
    """The small feathers over the wing's root: rows of scallops."""
    for name in ("front", "back"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if (y % 2 == 1 and (x + y // 2) % 3 == 0) or y == f.h - 1:
                    tint(f, x, y, 0.75)
        for x in range(f.w):
            tint(f, x, f.h - 1, 0.85, ASH, 0.4)


def blade_detail(faces):
    glowing(LIGHT)(faces)
    for name in ("left", "right"):
        f = faces[name]
        for y in range(f.h):
            f.glow(f.w // 2, y, hexc("ffffff"))  # the fuller burns brightest


def grip_detail(faces):
    for name in SIDES:
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 2 == 0:
                    tint(f, x, y, 1.4, GOLD.c["base"], 0.4)  # gold wire wound round it


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("fallen_seraph", texture_width=128)
    root = m.bone("root", (0, 0, 0))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mir = s == -1
        leg = root.bone(f"{side}_leg", (2 * s, 18, 0))
        # long legs in white hose under the robe, greaves on the shins, bare feet pointed down as he hovers
        leg.cube(f"{side}_thigh", (x(0.5, 3), 10, -1.5), (3, 8, 3), UNDER, mirror=mir)
        leg.cube(f"{side}_shin", (x(0.7, 3), 3, -1.3), (3, 7, 3), UNDER, mirror=mir, inflate=-0.2)
        leg.cube(f"{side}_greave", (x(0.6, 3), 4, -1.8), (3, 5, 1), TARNISH, mirror=mir, detail=combo(rimmed, scratched))
        leg.cube(f"{side}_ankle", (x(1, 2), 1.5, -1), (2, 2, 2), SKIN, mirror=mir)
        leg.cube(f"{side}_foot", (x(0.8, 2), 0, -3.2), (2, 2, 3), SKIN, mirror=mir, rotation=(-35, 0, 0), origin=(2 * s, 2, -1),
                 detail=occlude(top=1, k=0.85))
    body = root.bone("body", (0, 18, 0))
    # the robe: an under-robe, the white over-robe flaring to a torn hem, a tabard of gold-edged cloth hanging before
    # and behind, and a gold girdle with a hanging end
    body.cube("robe", (-4.5, 8, -2.5), (9, 10, 5), ROBE, detail=stained(3))
    body.cube("robe_hem", (-5, 4, -3), (10, 5, 6), ROBE, detail=combo(ragged(3, scorch=True), stained(2)))
    body.cube("tabard", (-2, 6, -3.6), (4, 12, 1), ROBE, detail=ragged(2, ("front", "back"), True))
    body.cube("tabard_back", (-2.5, 5, 2.6), (5, 13, 1), ROBE, detail=ragged(2, ("front", "back"), True))
    body.cube("girdle", (-4, 17, -2.5), (8, 2, 5), GOLD, inflate=0.2, detail=combo(rimmed, scratched))
    body.cube("girdle_clasp", (-1, 16.5, -3.2), (2, 3, 1), GOLD, detail=rimmed)
    body.cube("girdle_end", (2, 10, -2.9), (1, 7, 1), GOLD, rotation=(0, 0, 6), origin=(2.5, 17, -2.5))
    torso = body.bone("torso", (0, 19, 0))
    # a slim waist under a cuirass of overlapping plates, the chest and shoulders broad as a statue's, a high gorget
    torso.cube("torso", (-3, 19, -2), (6, 5, 4), ROBE, detail=occlude(top=1, k=0.75))
    torso.cube("chest", (-4, 24, -2.5), (8, 6, 5), ROBE)
    torso.cube("breastplate", (-3.5, 25, -3), (7, 5, 1), GOLD, detail=combo(rimmed, scratched))
    torso.cube("plate_low", (-3, 23, -2.8), (6, 2, 1), TARNISH, detail=combo(rimmed, scratched))
    torso.cube("backplate", (-3.5, 23, 2), (7, 7, 1), TARNISH, detail=combo(rimmed, scratched))
    torso.cube("gorget", (-2.5, 29.5, -2), (5, 1, 4), GOLD, inflate=0.2, detail=rimmed)
    torso.cube("neck", (-1, 30, -1), (2, 2, 2), SKIN, detail=occlude(bottom=1, k=0.8))
    head = torso.bone("head", (0, 31, 0))
    # a long fine face with depth: brow, a straight nose, the jaw; long pale hair falling to his shoulders
    head.cube("head", (-2.5, 32, -2.5), (5, 6, 5), SKIN, detail=face)
    head.cube("brow", (-2.5, 36, -2.8), (5, 1, 1), SKIN, inflate=-0.15, detail=brow)
    head.cube("nose", (-0.5, 33.6, -3.1), (1, 2, 1), SKIN, inflate=-0.1, detail=nose)
    head.cube("jaw", (-2, 31.4, -2.3), (4, 1, 4), SKIN, detail=occlude(bottom=1, k=0.8))
    head.cube("hair", (-3, 36.5, -3), (6, 2, 6), HAIR, inflate=0.2, detail=strands)
    head.cube("hair_back", (-3, 29.5, 1), (6, 8, 2), HAIR, inflate=0.1, detail=combo(strands, ragged(1, ("front", "back", "left", "right"))))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        head.cube(f"{side}_hair", (x(2.3, 1), 31, -2.5), (1, 6, 4), HAIR, mirror=s == -1, inflate=0.1, detail=strands)
    # the cracked halo: a ring of cold light behind his head, one piece broken out and another knocked askew
    halo = head.bone("halo", (0, 41, 1))
    cx, cy, r = 0.0, 38.5, 4.5
    for k in range(8):
        if k == 5:
            continue  # the piece broken out
        a = math.radians(90 + k * 45)
        px, py = cx + r * math.cos(a), cy + r * math.sin(a)
        askew = 18 if k == 6 else 0
        halo.cube(f"halo{k}", (px - 1.5, py - 0.5, 3.6), (3, 1, 1), BLADE, rotation=(0, 0, math.degrees(a) + 90 + askew),
                  origin=(px, py, 4.1), detail=glowing(HALO))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mir = s == -1
        arm = torso.bone(f"{side}_arm", (5 * s, 30, 0))
        # a layered pauldron, the robe's sleeve, a bare forearm in a vambrace, and a long hand
        arm.cube(f"{side}_pauldron", (x(3.8, 3), 28.8, -2), (3, 2, 4), GOLD, mirror=mir, inflate=0.3, detail=combo(rimmed, scratched))
        arm.cube(f"{side}_pauldron_lame", (x(4.1, 3), 27.2, -1.8), (3, 2, 4), TARNISH, mirror=mir, inflate=0.05, detail=rimmed)
        arm.cube(f"{side}_arm", (x(4.5, 2), 23, -1), (2, 6, 2), ROBE, mirror=mir, inflate=0.2)
        arm.cube(f"{side}_sleeve", (x(4, 3), 22, -1.5), (3, 6, 3), ROBE, mirror=mir, detail=ragged(2, scorch=True))
        arm.cube(f"{side}_forearm", (x(4.5, 2), 19, -1), (2, 4, 2), SKIN, mirror=mir)
        arm.cube(f"{side}_vambrace", (x(4.3, 2), 19.5, -1.2), (2, 3, 2), GOLD, mirror=mir, inflate=0.25, detail=rimmed)
        arm.cube(f"{side}_hand", (x(4.5, 2), 17, -1), (2, 2, 2), SKIN, mirror=mir)
        arm.cube(f"{side}_fingers", (x(4.5, 2), 16, -1), (2, 1, 2), SKIN, mirror=mir, inflate=-0.05, detail=occlude(top=1, k=0.8))
        arm.cube(f"{side}_thumb", (x(4.6, 1), 16.5, -1.6), (1, 2, 1), SKIN, mirror=mir)
        # three pairs of wings, high, middle and low: each a bony arm along its top, a layer of coverts over the
        # root, and long flight feathers overlapping down and out, longest toward the tip
        for i, (y, w, h, spread) in enumerate([(30, 16, 7, 20), (26, 18, 8, 0), (21, 14, 7, -20)]):
            wing = torso.bone(f"{side}_wing{i}", (2 * s, y, 2), rotation=(0, 0, spread * s))
            top = y + 2
            wing.cube(f"{side}_wing{i}", (x(2, w - 1), top - 1.5, 2), (w - 1, 2, 1), COVERT, mirror=mir, inflate=0.1)
            wing.cube(f"{side}_wing{i}_coverts", (x(2.5, w - 4), top - 3.5, 2.6), (w - 4, 3, 1), COVERT, mirror=mir, detail=coverts)
            n = (w - 2) // 3
            for k in range(n):
                fx = 2.5 + k * (w - 4.5) / max(1, n - 1)
                length = h - 2 + int(round(3 * k / max(1, n - 1)))
                tilt = (-8 + 22 * k / max(1, n - 1)) * s
                z = 2.15 if k % 2 == 0 else 2.45
                wing.cube(f"{side}_wing{i}_feather{k}", (x(fx, 3), top - 1 - length, z), (3, length, 1), FEATHER, mirror=mir,
                          rotation=(0, 0, tilt), origin=(fx + 1.5 if s == 1 else -(fx + 1.5), top - 1, z),
                          detail=feather(0.35 + 0.35 * k / max(1, n - 1)))
    # the Morning Star, held in his right hand: a long blade of light, a gold guard and a wire-bound grip
    blade = torso.bone("blade", BLADE_PIVOT)
    blade.cube("pommel", (5, 19, -0.5), (1, 1, 1), GOLD, inflate=0.15)
    blade.cube("hilt", (5, 15.5, -0.5), (1, 3, 1), GRIP, inflate=0.05, detail=grip_detail)
    blade.cube("guard", (5, 15, -2), (1, 1, 4), GOLD, inflate=0.1, detail=rimmed)
    blade.cube("blade", (5.1, 2, -1), (1, 13, 2), BLADE, inflate=-0.1, detail=blade_detail)
    blade.cube("blade_tip", (5.1, 0.5, -0.5), (1, 2, 1), BLADE, inflate=-0.15, detail=glowing(LIGHT))
    animations(m)
    return m


ZERO = (0, 0, 0)
ARM_R = (15, 0, 10)
ARM_L = (15, 0, -10)
BLADE_PIVOT = (5, 30, 0)  # his right shoulder: the blade turns with the arm


def beat(a, length, amount=12):
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            a.rot(f"{side}_wing{i}", [(0, (0, -10 * s, 0)), (length * 0.5, (0, 10 * s, amount * s)), (length, (0, -10 * s, 0))])


def right_arm(a, keys):
    """Keys his right arm, and the blade in its hand along with it."""
    a.rot("right_arm", keys)
    a.rot("blade", keys)


def animations(m):
    # hovering a little off the ice, calm, his wings breathing
    idle = m.animation("idle", 3.0, loop=True)
    beat(idle, 3.0, 8)
    idle.pos("root", [(0, (0, 2, 0)), (1.5, (0, 3, 0)), (3.0, (0, 2, 0))])
    right_arm(idle, [(0, ARM_R), (3.0, ARM_R)])
    idle.rot("left_arm", [(0, ARM_L), (3.0, ARM_L)])

    # the blade: raised high, a flash, and down across you
    slash = m.animation("slash", 1.6)
    right_arm(slash, [(0, ARM_R), (0.5, (170, 0, 20)), (0.85, (175, 0, 20)), (1.0, (40, 0, -20)), (1.6, ARM_R)])
    slash.rot("torso", [(0, ZERO), (0.5, (10, 20, 0)), (1.0, (-20, -30, 0)), (1.6, ZERO)])
    slash.event(0.5, "flash").event(0.95, "hit")

    # the wings sweep round him, low over the ice
    sweep = m.animation("sweep", 1.8)
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            sweep.rot(f"{side}_wing{i}", [(0, ZERO), (0.5, (0, -50 * s, -30 * s)), (1.3, (0, 60 * s, 40 * s)), (1.8, ZERO)])
    sweep.rot("root", [(0, ZERO), (0.5, (0, -60, 0)), (1.3, (0, 300, 0)), (1.8, (0, 360, 0))], interp="linear")
    sweep.event(0.6, "sweep").event(1.3, "swept")

    # the falling stars: his hand lifted, light called down
    stars = m.animation("stars", 2.0)
    beat(stars, 1.0, 20)
    stars.rot("left_arm", [(0, ARM_L), (0.6, (180, 0, -10)), (1.6, (180, 0, -10)), (2.0, ARM_L)])
    stars.rot("head", [(0, ZERO), (0.6, (35, 0, 0)), (1.6, (35, 0, 0)), (2.0, ZERO)])
    stars.event(0.7, "call")

    # the lunge: he draws back, then crosses the ice like light
    lunge = m.animation("lunge", 1.4)
    lunge.rot("body", [(0, ZERO), (0.6, (20, 0, 0)), (0.8, (-35, 0, 0)), (1.4, ZERO)])
    right_arm(lunge, [(0, ARM_R), (0.6, (60, 0, 30)), (0.8, (90, 0, 0)), (1.4, ARM_R)])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            lunge.rot(f"{side}_wing{i}", [(0, ZERO), (0.6, (0, 30 * s, 20 * s)), (0.8, (0, -50 * s, -20 * s)), (1.4, ZERO)])
    lunge.event(0.7, "go")

    # parried: he reels, blade low, wings askew
    stagger = m.animation("stagger", 2.0)
    stagger.rot("torso", [(0, ZERO), (0.3, (30, 0, 20)), (1.6, (30, 0, 20)), (2.0, ZERO)])
    right_arm(stagger, [(0, ARM_R), (0.3, (-10, 0, 60)), (1.6, (-10, 0, 60)), (2.0, ARM_R)])
    stagger.pos("root", [(0, (0, 2, 0)), (0.3, (0, -2, 0)), (1.6, (0, -2, 0)), (2.0, (0, 2, 0))])

    # half health: he stops pretending. The face hardens, the wings flare and the halo cracks wider.
    snap = m.animation("snap", 2.6)
    snap.rot("head", [(0, ZERO), (0.6, (-30, 0, 0)), (1.0, (40, 0, 0)), (2.2, (40, 0, 0)), (2.6, ZERO)])
    snap.rot("halo", [(0, ZERO), (1.0, (0, 0, 25)), (2.6, (0, 0, 25))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            snap.rot(f"{side}_wing{i}", [(0, ZERO), (1.0, (0, -40 * s, -40 * s)), (2.2, (0, -40 * s, -40 * s)), (2.6, ZERO)])
    right_arm(snap, [(0, ARM_R), (1.0, (20, 0, 80)), (2.2, (20, 0, 80)), (2.6, ARM_R)])
    snap.rot("left_arm", [(0, ARM_L), (1.0, (20, 0, -80)), (2.2, (20, 0, -80)), (2.6, ARM_L)])
    snap.event(1.0, "snap")

    # his descent: down out of the dark to the ice, wings folding, then he speaks
    intro = m.animation("intro", 5.0)
    intro.pos("root", [(0, (0, 60, 0)), (3.0, (0, 2, 0)), (5.0, (0, 2, 0))])
    beat(intro, 1.0, 25)
    intro.rot("head", [(0, (-20, 0, 0)), (3.0, (-20, 0, 0)), (3.6, ZERO), (5.0, ZERO)])
    intro.rot("left_arm", [(0, ARM_L), (3.6, ARM_L), (4.0, (90, 0, -30)), (5.0, (90, 0, -30))])
    intro.event(3.0, "land").event(3.6, "speak")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("torso", [(0, ZERO), (0.1, (15, 0, 8)), (0.3, ZERO)])

    # his seraph form breaks: arms flung out, head thrown back, then he falls to pieces (the server shows the shards)
    death = m.animation("death", 2.4)
    death.rot("head", [(0, ZERO), (0.8, (60, 0, 0)), (2.4, (60, 0, 0))])
    right_arm(death, [(0, ARM_R), (0.8, (20, 0, 100)), (2.4, (20, 0, 100))])
    death.rot("left_arm", [(0, ARM_L), (0.8, (20, 0, -100)), (2.4, (20, 0, -100))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            death.rot(f"{side}_wing{i}", [(0, ZERO), (0.8, (0, -60 * s, -50 * s)), (2.4, (0, -60 * s, -50 * s))])
    death.scale("root", [(0, (1, 1, 1)), (1.6, (1, 1, 1)), (2.4, (0.05, 0.05, 0.05))], interp="linear")
    death.event(1.6, "break")


if __name__ == "__main__":
    build().write()
