#!/usr/bin/env python3
"""The Seraph of the Primum Mobile, Paradiso's guardian and the last trial (Isaiah VI: "each one had six wings; with
twain he covered his face, and with twain he covered his feet, and with twain he did fly"; Paradiso XXVIII, the
seraphim burning nearest the point of light). A tall burning angel hovering over the crystal floor: a robe of white and
gold trailing into flame, a calm face behind two veiling wings, two more veiling its feet, two great wings spread to
fly, all of them full of eyes; a ring of fire behind its head, and in its right hand tongs holding a live coal from
the altar.

Run from the repo root:  python3 tools/art/seraph.py && python3 tools/gen_bosses.py

It is drawn 2.0 times its Blockbench size (about 6 blocks tall), with a man's proportions drawn long and slender
(about seven heads tall): a white-gold breastplate with pectorals and lames over a white robe whose panels flare and
burn away at the hem, layered pauldrons and gold vambraces, hands with a thumb and fingers, a calm face (brow, nose,
lips, a gold circlet) framed by pale gold hair. Every wing is built of feathers, each its own thin cube with a shaft,
a pointed tip and a burning edge: an arm of small coverts along the leading edge, a row of greater coverts (the ones
with eyes), then the long secondaries and primaries overlapping. The outer half of each great wing is its own bone,
so the wingbeat follows through.

The events time its fight (SeraphFight): "flash" opens the window to block its wing-blow and "hit" is when it lands;
"call" brings fire down from above; "throw" lets the coal go; "unveil" and "blaze" are the Gloria (its face unveiled:
get close to it); "turn" starts the turning heavens; "host" calls the Angelic Guard; "flare" its half-health turn;
"land" and "cry" its descent; "yield" and "rise" its end, when it bows to you and goes up into the light.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, mix, shade, hexc  # noqa: E402

ROBE = material("robe", "robe", base="f2eee2", fold="c8bfa4", trim="e0b040")
STOLE = material("stole", "robe", base="e8c860", fold="b89430", trim="fff0a0")
GOLD = material("gold", "metal", base="d8b040", high="fff0a0", low="8a6418")
ARMOUR = material("armour", "metal", base="eee6d2", high="ffffff", low="a89c80")
SKIN = material("skin", "skin", base="fbe8cc", dark="dcbc98")
HAIR = material("hair", "hair", light="fff4cc", dark="c8a456")
FEATHER = material("feather", "flat", base="fbf7ee", edges=False)
COAL = material("coal", "flat", base="2a1a14")
LIGHT = hexc("fff4c0")
FIRE = hexc("ffa030")
FIRE_HOT = hexc("ffe070")
EYE = hexc("4ab8ff")
EYE_HOT = hexc("d8f4ff")
CLEAR = (0, 0, 0)


# ----------------------------------------------------------------------------------------------- geometry helpers

def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def add(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def mul(a, k):
    return tuple(v * k for v in a)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def norm(a):
    n = math.sqrt(sum(v * v for v in a)) or 1.0
    return tuple(v / n for v in a)


def stick(bone, name, a, b, t, mat, depth=None, inflate=0.0, **kw):
    """A cube t wide (and depth deep) that stands on point a and reaches to point b: a limb, a lock of hair, a
    feather (its flat faces stay toward the front and back). Its length is rounded to whole pixels (box UV); it turns
    about a."""
    d = sub(b, a)
    length = math.sqrt(sum(v * v for v in d)) or 1.0
    rx = math.degrees(math.asin(max(-1.0, min(1.0, d[2] / length))))
    rz = math.degrees(math.atan2(-d[0], d[1]))
    depth = depth or t
    return bone.cube(name, (a[0] - t / 2, a[1], a[2] - depth / 2), (t, max(1, round(length)), depth), mat, inflate=inflate,
                     rotation=(round(rx, 2), 0, round(rz, 2)), origin=a, **kw)


def side_x(s):
    def x(a, w):
        return a if s == 1 else -(a + w)
    return x


# ----------------------------------------------------------------------------------------------- details

def glowing(colour):
    def paint(faces):
        for name in ("front", "back", "left", "right", "top", "bottom"):
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    f.glow(x, y, colour)
    return paint


def flame(faces):
    """A tongue of the ring of fire: hot at the core, orange at the rims."""
    for name in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, FIRE_HOT if (x + y) % 3 else FIRE)


def calm_face(faces):
    """A calm face, lit from within: clear brows, eyes of burning blue under lowered lids, a straight nose's shadow,
    a little warmth in the cheeks."""
    f = faces["front"]
    brow = shade(HAIR.c["dark"], 0.95)
    lid = shade(SKIN.c["dark"], 0.85)
    for cx in (0, f.w - 2):
        f.px(cx, 1, brow)
        f.px(cx + 1, 1, brow)
        f.px(cx, 2, lid)
        f.glow(cx + (1 if cx == 0 else 0), 2, EYE)
        f.glow(cx + (0 if cx == 0 else 1), 2, EYE_HOT)
        f.px(cx, 3, shade(SKIN.c["base"], 0.97))
    f.px(f.w // 2, 2, shade(SKIN.c["dark"], 1.02))
    f.px(f.w // 2, 3, shade(SKIN.c["dark"], 0.95))
    f.px(0, 4, mix(SKIN.c["base"], hexc("f0b098"), 0.4))
    f.px(f.w - 1, 4, mix(SKIN.c["base"], hexc("f0b098"), 0.4))
    for x in range(f.w):
        f.px(x, 0, shade(HAIR.c["light"], 0.95) if x % 2 else HAIR.c["light"])  # the fringe at the hairline


def lips(faces):
    f = faces["front"]
    mid = f.w // 2
    f.px(mid - 1, 0, mix(SKIN.c["base"], hexc("d89a88"), 0.45))
    f.px(mid, 0, mix(SKIN.c["base"], hexc("d89a88"), 0.45))
    faces["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.85))


def circlet(faces):
    f = faces["front"]
    f.glow(f.w // 2, 0, EYE_HOT)
    f.glow(f.w // 2 - 1, 0, EYE)


def breastplate(faces):
    """The cuirass: a gold rim round the neck and arms, a ridge down the middle, a sunburst at the heart."""
    f = faces["front"]
    gold = GOLD.c["base"]
    mid = f.w // 2
    for x in range(f.w):
        f.px(x, 0, GOLD.c["high"])
    for y in range(f.h):
        f.px(0, y, gold)
        f.px(f.w - 1, y, gold)
        f.px(mid, y, shade(ARMOUR.c["base"], 1.08) if y % 2 else ARMOUR.c["high"])
    for dx, dy in ((0, 2), (-1, 3), (1, 3), (0, 4), (-2, 3), (2, 3)):
        f.glow(mid + dx, dy, FIRE_HOT if dx == 0 else GOLD.c["high"])


def gold_rim(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.px(x, f.h - 1, GOLD.c["base"])
            f.px(x, 0, GOLD.c["high"])


def buckle(faces):
    f = faces["front"]
    f.glow(f.w // 2, f.h // 2, EYE_HOT)


def flame_hem(faces):
    """A panel of the robe, its hem burning: white above, the lowest rows fire, licking up in tongues, the very foot
    ragged with flame (cut away between the tongues)."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            tongue = 4 + (3 if (x + f.rng.randrange(2)) % 3 == 0 else f.rng.randrange(2))
            for y in range(f.h - tongue, f.h):
                f.glow(x, y, FIRE_HOT if y < f.h - 2 else FIRE)
            if x % 2 == 0:
                f.px(x, f.h - 1, CLEAR, alpha=0)
        if name == "front" and f.w >= 3:
            for y in range(f.h - 5):
                f.px(0, y, shade(ROBE.c["fold"], 0.95))  # the fold where panels meet
    faces["bottom"].fill(lambda x, y: None)
    for y in range(faces["bottom"].h):
        for x in range(faces["bottom"].w):
            faces["bottom"].px(x, y, CLEAR, alpha=0)


def palm(faces):
    f = faces["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["dark"], 0.95))


def feather(eye=False, burning=True):
    """A feather: white vanes with a warm shaft down the middle, faint barbs, a pointed tip (its corners cut away);
    a flight feather's tip burns (the seraphim are the burning ones), and some of the coverts carry an eye."""
    def paint(faces):
        for name in ("front", "back"):
            f = faces[name]
            mid = (f.w - 1) / 2
            for fy in range(f.h):
                base_t = fy / max(1, f.h - 1)
                for fx in range(f.w):
                    edge = abs(fx - mid) / max(0.5, mid + 0.5)
                    c = shade(FEATHER.c["base"], 1.06 - 0.1 * base_t - 0.05 * edge)
                    if (fy + int(abs(fx - mid))) % 3 == 0:
                        c = shade(c, 0.94)  # the barbs, slanting from the shaft
                    f.px(fx, fy, c)
                if f.w >= 2:
                    f.px(int(round(mid)), fy, mix(FEATHER.c["base"], hexc("e8d09a"), 0.55))
            if f.w >= 2:
                f.px(0, 0, CLEAR, alpha=0)
                f.px(f.w - 1, 0, CLEAR, alpha=0)
                if burning:
                    for fx in range(1, f.w - 1):
                        f.glow(fx, 0, FIRE_HOT)
                    f.glow(0, 1, FIRE)
                    f.glow(f.w - 1, 1, FIRE)
                else:
                    for fx in range(1, f.w - 1):
                        f.px(fx, 0, shade(FEATHER.c["base"], 1.08))
            if eye and f.w >= 2 and f.h >= 4:
                ey = f.h // 2 - 1
                ex = max(0, int(mid) - (1 if f.w > 2 else 0))
                f.px(ex, ey - 1, shade(FEATHER.c["base"], 0.8))
                f.px(ex + 1, ey - 1, shade(FEATHER.c["base"], 0.8))
                f.glow(ex, ey, EYE)
                f.glow(ex + 1, ey, EYE_HOT)
                f.px(ex, ey + 1, shade(FEATHER.c["base"], 0.8))
                f.px(ex + 1, ey + 1, shade(FEATHER.c["base"], 0.8))
        for name in ("left", "right", "bottom"):
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    f.px(x, y, shade(FEATHER.c["base"], 0.88))
        t = faces["top"]
        for y in range(t.h):
            for x in range(t.w):
                if burning:
                    t.glow(x, y, FIRE)
                else:
                    t.px(x, y, FEATHER.c["base"])
    return paint


# ----------------------------------------------------------------------------------------------- the wings

def plumage(bone, name, z, arm, feathers, coverts, eyes=True):
    """Feathers along a wing's arm. arm: points of the leading edge (the arm's bones, covered in small feathers);
    feathers: (base, tip, width) of the flight feathers, each a little in front of the one before, so they overlap;
    coverts: (base, tip) of the shorter feathers that cover their roots (thicker, so they show on both sides)."""
    for k in range(len(arm) - 1):
        stick(bone, f"{name}_arm{k}", arm[k], arm[k + 1], 2, FEATHER, depth=2, inflate=0.05 - 0.1 * k, detail=feather(burning=False))
    for k, (base, tip, width) in enumerate(feathers):
        dz = 0.07 * k
        stick(bone, f"{name}_feather{k}", (base[0], base[1], z + dz), (tip[0], tip[1], z + dz), width, FEATHER, depth=1,
              inflate=-0.3, detail=feather())
    for k, (base, tip) in enumerate(coverts):
        stick(bone, f"{name}_covert{k}", (base[0], base[1], z), (tip[0], tip[1], z), 3, FEATHER, depth=1, inflate=-0.05,
              detail=feather(eye=eyes and k % 2 == 0, burning=False))


def great_wing(chest, side, s):
    """A great wing, spread to fly: the arm rises from the shoulder blade to the wrist and on to the hand; the
    secondaries hang from the forearm, the primaries fan from the hand; greater coverts over their roots. The hand,
    its primaries and coverts are the wing tip's bone."""
    w = chest.bone(f"{side}_wing", (4 * s, 34, 4), rotation=(0, 0, 12 * s))
    z = 4.6
    R, E, W, H = (4.5 * s, 33.5, z), (13 * s, 36.5, z), (22 * s, 38.5, z), (31 * s, 36.5, z)
    feathers, coverts = [], []
    for k in range(7):
        t = 0.08 + k * 0.135
        base = lerp(R, E, t * 2) if t < 0.5 else lerp(E, W, (t - 0.5) * 2)
        d = norm(((0.1 + 0.12 * t) * s, -1, 0))
        feathers.append((base, add(base, mul(d, 12.5 + t)), 3))
        if k % 2 == 0 or k == 6:
            coverts.append((add(base, (0, 0.6, 0)), add(base, mul(norm(((0.25 + 0.2 * t) * s, -1, 0)), 5.5))))
    plumage(w, f"{side}_wing", z, [R, E, W], feathers, coverts)
    tip = w.bone(f"{side}_wing_tip", W)
    feathers, coverts = [], []
    for k in range(6):
        t = k / 5
        base = lerp(W, H, t)
        ang = math.radians(-70 + 62 * t)  # from steeply down at the wrist to nearly level at the tip
        d = (math.cos(ang) * s, math.sin(ang), 0)
        feathers.append((base, add(base, mul(d, 13 - 5.5 * t)), 3))
        if k < 5:
            coverts.append((add(base, (0, 0.6, 0)), add(base, mul(norm((math.cos(ang + 0.3) * s, math.sin(ang + 0.3), 0)), 4.5))))
    plumage(tip, f"{side}_wing_tip", z + 0.6, [W, H], feathers, coverts)


def veiling_wing(parent, name, hinge, z, arm_from, arm_to, lengths, s):
    """A veiling wing (over the face or the feet), folded across the front: its arm runs in from the hinge, and its
    feathers hang down from it, side by side and overlapping."""
    w = parent.bone(name, hinge)
    A, B = (arm_from[0] * s, arm_from[1], z), (arm_to[0] * s, arm_to[1], z)
    feathers, coverts = [], []
    n = len(lengths)
    for k, length in enumerate(lengths):
        base = lerp(A, B, k / (n - 1))
        d = norm((-0.14 * s, -1, 0))
        feathers.append((base, add(base, mul(d, length)), 3))
        if k < n - 1:
            mid = lerp(A, B, (k + 0.5) / (n - 1))
            coverts.append((add(mid, (0, 0.5, 0)), add(mid, mul(norm((-0.3 * s, -1, 0)), 4))))
    plumage(w, name, z, [A, B], feathers, coverts)
    return w


# ----------------------------------------------------------------------------------------------- the body

def robe_panel(body, name, phi, length, width, flare, top=24.2):
    """A panel of the robe hanging from the girdle at phi degrees round the waist (0 at the front), flaring a little."""
    a = math.radians(phi)
    r = 1.0 / math.sqrt((math.sin(a) / 4.6) ** 2 + (math.cos(a) / 3.6) ** 2)
    body.cube(name, (-width / 2, top - length, -r - 0.5), (width, length, 1), ROBE, rotation=(flare, phi, 0), origin=(0, top, 0),
              detail=flame_hem)


def build():
    m = Model("seraph", texture_width=256)
    root = m.bone("root", (0, 0, 0))
    body = root.bone("body", (0, 14, 0))

    # the robe: an underskirt, then panels round it, flaring and burning away at the hem; a gold stole down the front
    body.cube("underskirt", (-4.5, 3, -3.5), (9, 21, 7), ROBE, detail=flame_hem)
    for k, phi in enumerate(range(0, 360, 30)):
        robe_panel(body, f"robe{k}", phi, 22 + (k * 5) % 3, 4 if k % 3 else 3, 5 + (k * 3) % 4)
    stick(body, "stole", (0, 24.2, -4.4), (0, 4.0, -6.2), 3, STOLE, depth=1, inflate=0.05)
    body.cube("girdle", (-4.6, 23.4, -3.6), (9, 2, 7), GOLD, inflate=0.1)
    body.cube("buckle", (-1, 23.0, -4.2), (2, 3, 1), GOLD, inflate=0.05, detail=buckle)

    chest = body.bone("chest", (0, 26, 0))
    chest.cube("waist", (-3.5, 24.5, -2.6), (7, 4, 5), ROBE)
    chest.cube("lame0", (-4, 25.6, -2.9), (8, 1, 6), ARMOUR, inflate=0.1, detail=gold_rim)
    chest.cube("lame1", (-4.2, 26.6, -3.0), (8, 1, 6), ARMOUR, inflate=0.15, detail=gold_rim)
    chest.cube("breastplate", (-4.5, 27.5, -3), (9, 5, 6), ARMOUR, inflate=0.1, detail=breastplate)
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        chest.cube(f"{side}_pectoral", (x(0.3, 4), 30.0, -3.5), (4, 2, 1), ARMOUR, inflate=0.15, detail=gold_rim)
    chest.cube("shoulders", (-5.5, 32, -2.5), (11, 2, 5), ARMOUR, detail=gold_rim)
    chest.cube("backplate", (-4.5, 27, 2.6), (9, 6, 1), ARMOUR, inflate=0.1, detail=gold_rim)
    chest.cube("gorget", (-2, 33.6, -2.1), (4, 1, 4), GOLD, inflate=0.1)
    chest.cube("neck", (-1.5, 33.5, -1.5), (3, 3, 3), SKIN, inflate=-0.2)

    head = chest.bone("head", (0, 36, 0))
    head.cube("cranium", (-2.5, 37.5, -2.4), (5, 5, 5), SKIN, detail=calm_face)
    head.cube("lower_face", (-2, 36.2, -2.5), (4, 2, 4), SKIN, detail=lips)
    head.cube("chin", (-1, 36.0, -2.7), (2, 1, 1), SKIN, inflate=-0.05)
    head.cube("brow", (-2.5, 40.4, -2.65), (5, 1, 1), SKIN, inflate=-0.2)
    stick(head, "nose", (0, 40.2, -2.45), (0, 38.3, -3.25), 1, SKIN, inflate=0.0)
    head.cube("hair_cap", (-2.8, 41.6, -2.7), (6, 2, 6), HAIR, inflate=0.1)
    head.cube("hair_back", (-2.7, 36.5, 2.2), (5, 6, 1), HAIR, inflate=0.2)
    head.cube("circlet", (-2.9, 41.3, -2.95), (6, 1, 1), GOLD, inflate=-0.1, detail=circlet)
    for side, s in (("right", 1), ("left", -1)):
        stick(head, f"{side}_lock", (2.6 * s, 42.6, -1.6), (2.9 * s, 37.6, -0.6), 1, HAIR, depth=2, inflate=0.1)
        stick(head, f"{side}_hair", (1.6 * s, 42.8, 2.0), (2.6 * s, 34.6, 3.4), 2, HAIR, depth=1, inflate=0.05)
        stick(head, f"{side}_ear", (2.5 * s, 38.6, 0.2), (2.8 * s, 40.0, 0.8), 1, SKIN, inflate=-0.1)
    stick(head, "hair_nape", (0, 42.6, 2.6), (0, 34.2, 3.6), 2, HAIR, depth=1, inflate=0.1)

    # the ring of fire behind its head: a circle of flame, with tongues licking outward
    halo = head.bone("halo", (0, 41, 5))
    ring = 16
    for k in range(ring):
        a0, a1 = 2 * math.pi * k / ring, 2 * math.pi * (k + 1) / ring
        p0 = (7 * math.cos(a0), 41 + 7 * math.sin(a0), 5.5)
        p1 = (7 * math.cos(a1), 41 + 7 * math.sin(a1), 5.5)
        stick(halo, f"halo{k}", p0, p1, 1, GOLD, inflate=0.1, detail=flame)
        if k % 2 == 0:
            am = (a0 + a1) / 2
            q0 = (7.2 * math.cos(am), 41 + 7.2 * math.sin(am), 5.5)
            q1 = (8.6 * math.cos(am + 0.12), 41 + 8.6 * math.sin(am + 0.12), 5.5)
            stick(halo, f"halo_flame{k // 2}", q0, q1, 1, GOLD, inflate=-0.15, detail=flame)

    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        arm = chest.bone(f"{side}_arm", (7 * s, 35, 0))
        # layered pauldrons, each plate lower and further out than the one above
        for k in range(3):
            arm.cube(f"{side}_pauldron{k}", (x(5.2 + 0.7 * k, 4 - (k == 2)), 33.4 - 1.3 * k, -2.6), (4 - (k == 2), 2, 5), ARMOUR,
                     inflate=0.3 - 0.05 * k, rotation=(0, 0, -(14 + 10 * k) * s), origin=(7 * s, 35, 0), detail=gold_rim)
        stick(arm, f"{side}_upper_arm", (7.3 * s, 33.6, 0), (7.8 * s, 28.6, 0.3), 3, ROBE, inflate=-0.15)
        forearm = arm.bone(f"{side}_forearm", (7.8 * s, 28.6, 0.3))
        forearm.cube(f"{side}_elbow", (x(7.0, 2), 28.0, -0.5), (2, 2, 2), ROBE, inflate=0.05)
        stick(forearm, f"{side}_vambrace", (7.8 * s, 28.4, 0.3), (7.7 * s, 23.4, -0.8), 3, ARMOUR, inflate=-0.25, detail=gold_rim)
        stick(forearm, f"{side}_cuff", (7.75 * s, 24.6, -0.55), (7.7 * s, 23.5, -0.8), 3, GOLD, inflate=-0.1)
        wrist = (7.7 * s, 23.6, -0.8)
        stick(forearm, f"{side}_palm", wrist, (7.7 * s, 21.6, -1.2), 2, SKIN, depth=1, inflate=0.1, detail=palm)
        stick(forearm, f"{side}_thumb", (7.0 * s, 22.8, -1.4), (6.6 * s, 21.4, -1.9), 1, SKIN, inflate=-0.1)
        for k, dx in enumerate((-0.45, 0.45)):
            stick(forearm, f"{side}_fingers{k}", (7.7 * s + dx, 21.8, -1.2), (7.7 * s + dx * 1.1, 20.2, -1.5), 1, SKIN, inflate=-0.02)
        # twain covered his face: the upper wings, folded across it (they open for the Gloria)
        veiling_wing(chest, f"{side}_face_wing", (9 * s, 41, -5), -5.6 if s == 1 else -6.0, (9, 42.6), (0.6, 46.6),
                     (7.5, 8.5, 9.5, 10.5, 11), s)
        # twain covered his feet: the lower wings, across the hem
        veiling_wing(body, f"{side}_foot_wing", (8 * s, 12, -5), -7.0 if s == 1 else -7.4, (8.5, 14.2), (0.6, 15.6),
                     (12, 12.5, 13, 13.5, 14), s)
        # and with twain he did fly: the great wings, spread
        great_wing(chest, side, s)
    # the tongs and the live coal from the altar (Isaiah VI 6), in its right hand
    coal = chest.bone("coal", (8, 21, 0))
    coal.cube("tongs_hinge", (7.5, 19.6, -1.0), (1, 1, 1), GOLD, inflate=0.1)
    stick(coal, "tongs_jaw0", (7.7, 20.2, -0.9), (7.4, 14.2, -0.7), 1, GOLD, inflate=-0.2)
    stick(coal, "tongs_jaw1", (8.3, 20.2, -0.9), (8.6, 14.2, -0.7), 1, GOLD, inflate=-0.2)
    coal.cube("coal", (6.5, 11.4, -2.0), (3, 3, 3), COAL, inflate=-0.1, detail=glowing(FIRE))
    coal.cube("coal_core", (7.0, 12.0, -2.4), (2, 2, 1), COAL, inflate=0.05, detail=glowing(FIRE_HOT))
    animations(m)
    return m


ZERO = (0, 0, 0)
HOVER = (0, 3, 0)
OPEN = 100


def flap(a, length, amount=14, start=0.0):
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(start, (0, 0, -6 * s)), (start + length * 0.5, (0, 0, amount * s)), (start + length, (0, 0, -6 * s))])
        # the hand of the wing lags the arm: it trails as the wing rises and sweeps through as it falls
        a.rot(f"{side}_wing_tip", [(start, (0, 0, 4 * s)), (start + length * 0.3, (0, 0, -amount * 0.4 * s)),
                                   (start + length * 0.8, (0, 0, amount * 0.4 * s)), (start + length, (0, 0, 4 * s))])


def unveil(a, keys):
    """The face wings swing out to its sides (to the angles given at each time: 0 veiled, 1 open)."""
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_face_wing", [(t, (0, OPEN * s * v, 0)) for t, v in keys])


def animations(m):
    # hovering over the floor, its great wings slowly beating
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("root", [(0, HOVER), (1.5, (0, 4.5, 0)), (3.0, HOVER)])
    flap(idle, 3.0, 10)
    idle.rot("halo", [(0, ZERO), (3.0, (0, 0, 360))], interp="linear")

    walk = m.animation("walk", 2.0, loop=True)
    walk.pos("root", [(0, HOVER), (1.0, (0, 4.5, 0)), (2.0, HOVER)])
    walk.rot("body", [(0, (-8, 0, 0)), (2.0, (-8, 0, 0))])
    flap(walk, 1.0, 18)
    flap(walk, 1.0, 18, start=1.0)

    # its descent: down out of the light, the face wings open as it cries "Holy, holy, holy"
    intro = m.animation("intro", 5.0)
    intro.pos("root", [(0, (0, 80, 0)), (3.0, HOVER), (5.0, HOVER)])
    flap(intro, 1.0, 30)
    flap(intro, 1.0, 30, start=1.0)
    flap(intro, 1.0, 30, start=2.0)
    unveil(intro, [(0, 0), (3.2, 0), (3.8, 1), (4.6, 1), (5.0, 0)])
    intro.rot("right_arm", [(0, ZERO), (3.4, ZERO), (3.8, (150, 0, 20)), (4.6, (150, 0, 20)), (5.0, ZERO)])
    intro.rot("left_arm", [(0, ZERO), (3.4, ZERO), (3.8, (150, 0, -20)), (4.6, (150, 0, -20)), (5.0, ZERO)])
    intro.event(3.0, "land").event(3.8, "cry")

    # the wing-blow: both great wings drawn back, a white flash, then swept forward across the front
    wings = m.animation("wings", 1.7)
    wings.pos("root", [(0, HOVER), (1.7, HOVER)])
    for side, s in (("right", 1), ("left", -1)):
        wings.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.6, (0, -45 * s, 30 * s)), (0.85, (0, -50 * s, 30 * s)), (1.0, (0, 75 * s, 0)),
                                   (1.2, (0, 75 * s, 0)), (1.7, (0, 0, 12 * s))])
    wings.rot("chest", [(0, ZERO), (0.6, (15, 0, 0)), (1.0, (-20, 0, 0)), (1.7, ZERO)])
    wings.event(0.55, "flash").event(0.95, "hit")

    # fire from above: both hands lifted, its head thrown back
    fire = m.animation("fire", 2.0)
    fire.pos("root", [(0, HOVER), (2.0, HOVER)])
    fire.rot("right_arm", [(0, ZERO), (0.6, (170, 0, 15)), (1.6, (170, 0, 15)), (2.0, ZERO)])
    fire.rot("left_arm", [(0, ZERO), (0.6, (170, 0, -15)), (1.6, (170, 0, -15)), (2.0, ZERO)])
    fire.rot("head", [(0, ZERO), (0.6, (30, 0, 0)), (1.6, (30, 0, 0)), (2.0, ZERO)])
    flap(fire, 1.0, 25)
    flap(fire, 1.0, 25, start=1.0)
    fire.event(0.8, "call")

    # the live coal: drawn back, then hurled
    coal = m.animation("coal", 1.6)
    coal.pos("root", [(0, HOVER), (1.6, HOVER)])
    coal.rot("right_arm", [(0, ZERO), (0.6, (-50, 0, 20)), (0.9, (120, 0, 0)), (1.6, ZERO)])
    coal.rot("chest", [(0, ZERO), (0.6, (0, -25, 0)), (0.9, (-10, 20, 0)), (1.6, ZERO)])
    coal.event(0.9, "throw")

    # the Gloria: it unveils its face, leans back and blazes; only those close beside it are spared
    gloria = m.animation("gloria", 3.2)
    gloria.pos("root", [(0, HOVER), (1.0, (0, 8, 0)), (2.4, (0, 8, 0)), (3.2, HOVER)])
    unveil(gloria, [(0, 0), (1.0, 1), (2.6, 1), (3.2, 0)])
    gloria.rot("chest", [(0, ZERO), (1.0, (20, 0, 0)), (2.4, (20, 0, 0)), (3.2, ZERO)])
    gloria.rot("right_arm", [(0, ZERO), (1.0, (30, 0, 80)), (2.4, (30, 0, 80)), (3.2, ZERO)])
    gloria.rot("left_arm", [(0, ZERO), (1.0, (30, 0, -80)), (2.4, (30, 0, -80)), (3.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        gloria.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (1.0, (0, 20 * s, 40 * s)), (2.4, (0, 20 * s, 40 * s)), (3.2, (0, 0, 12 * s))])
    gloria.event(1.0, "unveil").event(2.2, "blaze")

    # the turning heavens: it spins, wings outstretched, and a beam turns round the floor
    turning = m.animation("turning", 2.4)
    turning.pos("root", [(0, HOVER), (0.4, (0, 6, 0)), (2.0, (0, 6, 0)), (2.4, HOVER)])
    turning.rot("root", [(0, ZERO), (0.4, ZERO), (2.0, (0, 360, 0)), (2.4, (0, 360, 0))], interp="linear")
    for side, s in (("right", 1), ("left", -1)):
        turning.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.4, (0, 0, -5 * s)), (2.0, (0, 0, -5 * s)), (2.4, (0, 0, 12 * s))])
    turning.event(0.4, "turn")

    # the Angelic Guard: the great wings raised high
    host = m.animation("host", 2.0)
    host.pos("root", [(0, HOVER), (2.0, HOVER)])
    for side, s in (("right", 1), ("left", -1)):
        host.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.8, (0, 0, 60 * s)), (1.4, (0, 0, 60 * s)), (2.0, (0, 0, 12 * s))])
    host.rot("left_arm", [(0, ZERO), (0.8, (160, 0, -10)), (1.4, (160, 0, -10)), (2.0, ZERO)])
    host.event(1.0, "host")

    # parried: it reels, sinking, its wings askew
    stagger = m.animation("stagger", 2.0)
    stagger.pos("root", [(0, HOVER), (0.3, (0, -2, 3)), (1.6, (0, -2, 3)), (2.0, HOVER)])
    stagger.rot("chest", [(0, ZERO), (0.3, (25, 0, 15)), (1.6, (25, 0, 15)), (2.0, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        stagger.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.3, (0, 0, -30 * s)), (1.6, (0, 0, -30 * s)), (2.0, (0, 0, 12 * s))])

    # half its strength gone: all six wings flare, the ring of fire spins
    phase = m.animation("phase", 2.6)
    phase.pos("root", [(0, HOVER), (1.2, (0, 10, 0)), (2.2, (0, 10, 0)), (2.6, HOVER)])
    unveil(phase, [(0, 0), (1.2, 1), (2.2, 1), (2.6, 0)])
    for side, s in (("right", 1), ("left", -1)):
        phase.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (1.2, (0, 0, 55 * s)), (2.2, (0, 0, 55 * s)), (2.6, (0, 0, 12 * s))])
        phase.rot(f"{side}_foot_wing", [(0, ZERO), (1.2, (0, 70 * s, 0)), (2.2, (0, 70 * s, 0)), (2.6, ZERO)])
    phase.rot("halo", [(0, ZERO), (2.6, (0, 0, 720))], interp="linear")
    phase.event(1.2, "flare")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("chest", [(0, ZERO), (0.1, (12, 0, 6)), (0.3, ZERO)])

    # it yields: it sinks to the floor and bows to you, its face veiled, then rises into the light and is gone
    death = m.animation("death", 4.4)
    death.pos("root", [(0, HOVER), (1.2, (0, -4, 0)), (2.6, (0, -4, 0)), (4.4, (0, 90, 0))])
    death.rot("chest", [(0, ZERO), (1.2, (-35, 0, 0)), (2.6, (-35, 0, 0)), (3.2, ZERO)])
    death.rot("head", [(0, ZERO), (1.2, (-25, 0, 0)), (2.6, (-25, 0, 0)), (3.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (1.2, (0, 40 * s, -20 * s)), (2.6, (0, 40 * s, -20 * s)), (3.0, (0, 0, 30 * s)),
                                   (3.4, (0, 0, -10 * s)), (3.8, (0, 0, 30 * s)), (4.4, (0, 0, 0))])
    death.scale("root", [(0, (1, 1, 1)), (3.4, (1, 1, 1)), (4.4, (0.05, 0.05, 0.05))], interp="linear")
    death.event(1.2, "yield").event(2.6, "rise")


if __name__ == "__main__":
    build().write()
