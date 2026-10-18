#!/usr/bin/env python3
"""The Furies (Inferno IX), on the burning tower over the gate of Dis: "three infernal Furies stained with blood, who
had the limbs of women and their mien, and with the greenest hydras were begirt; small serpents and cerastes were
their hair". Megaera, Alecto and Tisiphone: gaunt women in torn black, bloodied, a girdle of green water-snakes, a
nest of serpents for hair, and dark ragged wings.

Run from the repo root:  python3 tools/art/fury.py && python3 tools/gen_bosses.py

All three share this body: a starved woman's frame (ribs and collarbones showing, sunken cheeks under a hard brow, a
beak of a nose, a jaw that drops when she shrieks), long arms ending in clawed hands, a black dress torn into strips
over bony clawed feet, a living hydra knotted round her waist with its heads hanging at her hip, a nest of small
curling serpents for hair, and bat wings: an arm of bone along the leading edge, four long fingers and a membrane
scalloped between them. The outer half of each wing is its own bone, so the wingbeat follows through.

The events time their fight (FuriesFight): "flash" opens the window to block a Fury's talons and "hit" is when they
strike; "scream" their shriek; "lash" the crack of her serpent hair; "tear" when they rend their breasts and blood
rains; "call" when they call for Medusa; "rise" and "scream" their entrance.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, mix, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8c867a", dark="4e463f")
ROBE = material("robe", "robe", base="1e1a1c", fold="0e0a0c")
HYDRA = material("hydra", "scales", base="2a6a2a", edge="5aa03a", belly="a0b860")
SERPENT = material("serpent", "scales", base="3a4a2a", edge="6a7a3a", belly="8a8a50")
BONE = material("wing_bone", "horn", root="3a2626", tip="160c0c")
MEMBRANE = material("membrane", "flat", base="2e1a1c", edges=False)
CLAW = material("claw", "horn", root="4a3a34", tip="0a0606")
EYE = hexc("ff2a2a")
EYE_HOT = hexc("ffb0a0")
BLOOD = hexc("7a0a0a")
BLOOD_DARK = hexc("4a0606")
MOUTH = hexc("1a0606")
TOOTH = hexc("c8bca0")
VEIN = hexc("4a2a2c")
CLEAR = (0, 0, 0)


# ----------------------------------------------------------------------------------------------- geometry helpers

def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def add(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def mul(a, k):
    return tuple(v * k for v in a)


def norm(a):
    n = math.sqrt(sum(v * v for v in a)) or 1.0
    return tuple(v / n for v in a)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def turn(v, axis, deg):
    """v turned about a unit axis by deg degrees (Rodrigues)."""
    k = norm(axis)
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    kv = cross(k, v)
    kd = sum(k[i] * v[i] for i in range(3))
    return tuple(v[i] * c + kv[i] * s + k[i] * kd * (1 - c) for i in range(3))


def stick(bone, name, a, b, t, mat, depth=None, inflate=0.0, **kw):
    """A cube t wide (and depth deep) that stands on point a and reaches to point b: a bone, a finger, a segment of a
    snake. Its length is rounded to whole pixels (box UV); it turns about a."""
    d = sub(b, a)
    length = math.sqrt(sum(v * v for v in d)) or 1.0
    rx = math.degrees(math.asin(max(-1.0, min(1.0, d[2] / length))))
    rz = math.degrees(math.atan2(-d[0], d[1]))
    depth = depth or t
    return bone.cube(name, (a[0] - t / 2, a[1], a[2] - depth / 2), (t, max(1, round(length)), depth), mat, inflate=inflate,
                     rotation=(round(rx, 2), 0, round(rz, 2)), origin=a, **kw)


def side_x(s):
    """x(a, w): a cube's left edge for one that spans a .. a + w on the right side, mirrored for the left."""
    def x(a, w):
        return a if s == 1 else -(a + w)
    return x


# ----------------------------------------------------------------------------------------------- details

def skull_face(faces):
    """The front of her skull (5 x 5): eye sockets in the shadow of the brow, red eyes weeping blood, hollow cheeks,
    and the upper lip drawn back off her teeth."""
    f = faces["front"]
    dark = shade(SKIN.c["dark"], 0.75)
    hollow = shade(SKIN.c["dark"], 0.9)
    for x in range(f.w):
        f.px(x, 1, shade(SKIN.c["dark"], 1.05))  # the brow's shadow on the forehead's foot
    for cx in (1, f.w - 2):
        f.px(cx - (1 if cx == 1 else -1), 2, dark)
        f.glow(cx, 2, EYE)
        f.px(cx, 3, BLOOD)  # tears of blood
        f.px(cx, 4, BLOOD_DARK)
    f.px(0, 3, hollow)
    f.px(f.w - 1, 3, hollow)
    f.px(0, 4, hollow)
    f.px(f.w - 1, 4, hollow)
    for x in range(1, f.w - 1):
        f.px(x, f.h - 1, TOOTH if x != f.w // 2 else shade(TOOTH, 0.8))
    for name in ("left", "right"):
        s = faces[name]
        for y in range(2, s.h):
            s.px(s.w - 1 if name == "right" else 0, y, hollow)  # the cheek falls in beside the face
    top = faces["top"]
    for y in range(top.h):
        for x in range(top.w):
            if (x + y) % 2 == 0:
                top.px(x, y, mix(SKIN.c["dark"], SERPENT.c["base"], 0.5))  # where her serpents root


def jaw_detail(faces):
    f = faces["front"]
    for x in range(f.w):
        f.px(x, 0, TOOTH if 0 < x < f.w - 1 else MOUTH)
    for x in range(1, f.w - 1):
        f.px(x, 1, BLOOD_DARK if x % 2 else MOUTH)
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    for x in range(1, t.w - 1):
        t.px(x, t.h - 1, TOOTH)
    b = faces["bottom"]
    b.fill(lambda x, y: shade(SKIN.c["dark"], 0.8))  # the shadow under her jaw


def cheekbone(faces):
    for name in ("bottom", "left", "right"):
        f = faces[name]
        f.fill(lambda x, y: shade(SKIN.c["dark"], 0.85) if name == "bottom" else None)


def ribs(faces):
    """A starved ribcage: rib lines across the front and sides, the breastbone, and the blood she rent it with."""
    f = faces["front"]
    rib = shade(SKIN.c["dark"], 0.85)
    mid = f.w // 2
    for y in range(0, f.h, 2):
        for x in range(f.w):
            if x != mid:
                f.px(x, y, rib)
    for y in range(f.h):
        f.px(mid, y, shade(SKIN.c["base"], 1.08))
    for name in ("left", "right"):
        s = faces[name]
        for y in range(0, s.h, 2):
            for x in range(s.w):
                s.px(x, y, rib)
    # four bloody rake marks down her front
    for i, x in enumerate((1, 2, f.w - 3, f.w - 2)):
        for y in range(i % 2, f.h):
            if (y + x) % 3:
                f.px(x, y, BLOOD if y < f.h - 1 else BLOOD_DARK)


def collar(faces):
    """The top of her chest: collarbones standing out over a hollow, the notch between them."""
    f = faces["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.12))
        f.px(x, 1, shade(SKIN.c["dark"], 0.95) if x not in (0, f.w - 1) else SKIN.c["base"])
    f.px(f.w // 2, 0, shade(SKIN.c["dark"], 0.8))
    f.px(f.w // 2 - 1, 0, shade(SKIN.c["dark"], 0.9))


def spine(faces):
    f = faces["back"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid, y, shade(SKIN.c["base"], 1.12) if y % 2 == 0 else shade(SKIN.c["dark"], 0.9))
        f.px(mid - 1, y, shade(SKIN.c["dark"], 1.0))


def neck_detail(faces):
    f = faces["front"]
    for y in range(f.h):
        f.px(0, y, shade(SKIN.c["base"], 1.1))  # the tendons of her neck
        f.px(f.w - 1, y, shade(SKIN.c["base"], 1.1))
    for name in ("left", "right", "back"):
        g = faces[name]
        for x in range(g.w):
            g.px(x, g.h - 1, shade(SKIN.c["dark"], 0.85))


def bloodied_hand(faces):
    for name in ("front", "back", "left", "right", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if (x * 3 + y * 5 + len(name)) % 4 == 0:
                    f.px(x, y, BLOOD)


def torn(faces):
    """A dress torn into rags: the strip's foot ragged and bitten through, a few holes, old blood soaked into it."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            cut = f.rng.choice((0, 1, 1, 2, 3))
            for y in range(f.h - cut, f.h):
                f.px(x, y, CLEAR, alpha=0)
        if f.h > 6 and f.w > 1:
            hx, hy = f.rng.randrange(f.w), f.rng.randrange(2, f.h - 3)
            f.px(hx, hy, CLEAR, alpha=0)
        for _ in range(f.w * f.h // 14):
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), shade(BLOOD_DARK, 0.9))
    faces["bottom"].fill(lambda x, y: None)


def torn_top(faces):
    """The bodice's torn upper edge."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            if f.rng.random() < 0.45:
                f.px(x, 0, CLEAR, alpha=0)
        for _ in range(f.w * f.h // 8):
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), shade(BLOOD_DARK, 0.9))


def snake_head(faces):
    f = faces["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SERPENT.c["edge"], 1.1))
    if f.w >= 1 and f.h >= 1:
        f.glow(0, min(1, f.h - 1), hexc("e8d040"))


# ----------------------------------------------------------------------------------------------- the wings

def membrane_shape(s):
    """The outline of one wing's membrane in model space (x, y), as a polygon: up the body, along the arm to the
    wrist, out along the first finger, then scalloped between the finger tips back to the body."""
    S, E, W = (2.5 * s, 21.0), (7.0 * s, 23.6), (11.5 * s, 24.2)
    tips = [(16.0 * s, 19.0), (15.2 * s, 12.0), (11.0 * s, 9.6), (6.6 * s, 10.4), (2.5 * s, 12.6)]
    pts = [S, E, W]
    prev = W
    for i, tip in enumerate(tips):
        if i == 0:
            pts.append(tip)
        else:
            for k in range(1, 9):
                t = k / 8
                bx, by = prev[0] + (tip[0] - prev[0]) * t, prev[1] + (tip[1] - prev[1]) * t
                pull = 0.2 * math.sin(math.pi * t)
                pts.append((bx + (W[0] - bx) * pull, by + (W[1] - by) * pull))
        prev = tip
    return pts, W, tips


def inside(poly, x, y):
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        (xi, yi), (xj, yj) = poly[i], poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def membrane(s, part):
    """Paints a wing's membrane panel: leathery, veined from the wrist outward, darker near the bones, cut to the
    scalloped outline (the rest of the panel and its thin edges are left transparent). part is 'inner' (body side of
    the third finger) or 'outer'."""
    poly, W, tips = membrane_shape(s)
    split = tips[2]

    def side_of(x, y):
        # which side of the line from the wrist to the third finger's tip
        return (split[0] - W[0]) * (y - W[1]) - (split[1] - W[1]) * (x - W[0])

    def paint(faces):
        for name in ("top", "bottom", "left", "right"):
            faces[name].fill(lambda x, y: None)
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    f.px(x, y, CLEAR, alpha=0)
        for name in ("front", "back"):
            f = faces[name]
            c = f.cube
            x0, y0 = c.frm[0], c.frm[1]
            for fy in range(f.h):
                for fx in range(f.w):
                    mx = (x0 + c.size[0] - fx - 0.5) if name == "front" else (x0 + fx + 0.5)
                    my = y0 + c.size[1] - fy - 0.5
                    sd = side_of(mx, my) * s
                    mine = sd <= 0.6 if part == "inner" else sd >= -0.6
                    if not (mine and inside(poly, mx, my)):
                        f.px(fx, fy, CLEAR, alpha=0)
                        continue
                    # distance from the wrist: veins fan out along the fingers
                    dx, dy = mx - W[0], my - W[1]
                    ang = math.atan2(dy, dx)
                    vein = any(abs(math.sin(ang - math.atan2(t[1] - W[1], t[0] - W[0]))) < 0.05 for t in tips)
                    base = MEMBRANE.c["base"]
                    tone = 1.0 + (f.rng.random() - 0.5) * 0.12 - 0.15 * min(1.0, math.hypot(dx, dy) / 14)
                    col = shade(base, tone)
                    if vein:
                        col = VEIN
                    elif (fx * 7 + fy * 3) % 11 == 0:
                        col = shade(base, 0.7)  # creases in the leather
                    f.px(fx, fy, col)
    return paint


def wing(torso, side, s):
    """A bat wing on the Fury's back: arm bones along the leading edge, a hooked thumb, four fingers and the membrane
    between them. The wrist and the two outer fingers are the wing tip's own bone."""
    w = torso.bone(f"{side}_wing", (2 * s, 22, 2))
    z = 2.6
    S, E, W = (2.5 * s, 21.0, z), (7.0 * s, 23.6, z), (11.5 * s, 24.2, z)
    stick(w, f"{side}_wing_humerus", S, E, 2, BONE, inflate=-0.3)
    stick(w, f"{side}_wing_radius", E, W, 1, BONE, inflate=0.05)
    w.cube(f"{side}_wing_elbow", (E[0] - 0.75, E[1] - 0.75, z - 0.75), (2, 2, 2), BONE, inflate=-0.25)
    poly, _, tips = membrane_shape(s)
    w.cube(f"{side}_wing_membrane", (min(2 * s, 12.2 * s) if s == 1 else -12.2, 9, z - 0.5), (11, 16, 1), MEMBRANE, inflate=-0.4,
           detail=membrane(s, "inner"))
    for i, tip in ((2, tips[2]), (3, tips[3])):
        stick(w, f"{side}_wing_finger{i}", W, (tip[0], tip[1], z), 1, BONE, inflate=-0.15 - 0.05 * i)
    tip_bone = w.bone(f"{side}_wing_tip", W)
    tip_bone.cube(f"{side}_wing_membrane_tip", (10.4 if s == 1 else -17.0, 9, z - 0.5), (7, 16, 1), MEMBRANE, inflate=-0.4,
                  detail=membrane(s, "outer"))
    tip_bone.cube(f"{side}_wing_wrist", (W[0] - 0.75, W[1] - 0.75, z - 0.75), (2, 2, 2), BONE, inflate=-0.2)
    stick(tip_bone, f"{side}_wing_thumb", W, (W[0] + 0.6 * s, W[1] + 1.8, z - 0.4), 1, CLAW)
    for i, tip in ((0, tips[0]), (1, tips[1])):
        stick(tip_bone, f"{side}_wing_finger{i}", W, (tip[0], tip[1], z), 1, BONE, inflate=-0.1 - 0.05 * i)


# ----------------------------------------------------------------------------------------------- the serpents

# the serpents of her hair: (root on the scalp, first heading, curl in degrees per segment, segments); the first six
# keep the names the animations address (snake0 .. snake5), the rest are smaller ones among them
SNAKES = [
    ((-2.4, 29.4, -0.6), (-1.0, 0.7, -0.3), 34, 5),
    ((2.4, 29.4, -0.6), (1.0, 0.7, -0.3), -34, 5),
    ((-1.8, 29.2, 2.0), (-0.6, 0.6, 0.8), 30, 5),
    ((1.8, 29.2, 2.0), (0.6, 0.6, 0.8), -30, 5),
    ((0.0, 29.8, 2.4), (0.0, 0.5, 1.0), 28, 5),
    ((0.0, 30.6, -1.2), (0.0, 1.0, -0.4), -26, 4),
    ((-1.4, 30.6, 0.4), (-0.4, 1.0, 0.1), -36, 4),
    ((1.4, 30.6, 0.4), (0.4, 1.0, 0.1), 36, 4),
    ((-2.5, 28.0, 1.2), (-1.0, -0.1, 0.4), 40, 4),
    ((2.5, 28.0, 1.2), (1.0, -0.1, 0.4), -40, 4),
    ((-1.0, 28.6, 2.8), (-0.3, -0.4, 1.0), 32, 4),
    ((1.0, 28.6, 2.8), (0.3, -0.4, 1.0), -32, 4),
]


def serpent(head, i):
    """One serpent of her hair: a chain of tapering segments, each turned a little further than the last, so it curls;
    three bones (root, middle, tip) so it writhes along its length, and a small head with yellow eyes."""
    root, heading, curl, segs = SNAKES[i]
    d = norm(heading)
    axis = norm(cross(d, (0, 0, 1) if abs(d[2]) < 0.8 else (1, 0, 0)))
    if abs(d[0]) < 0.2:
        axis = (1, 0, 0)
    p = root
    bone = head.bone(f"snake{i}", root)
    widths = [(2, -0.3), (2, -0.45), (1, 0.05), (1, -0.1), (1, -0.2)]
    for k in range(segs):
        if k == 2:
            bone = bone.bone(f"snake{i}_mid", p)
        if k == segs - 1:
            bone = bone.bone(f"snake{i}_tip", p)
        q = add(p, mul(d, 1.6 if k < 2 else 1.3))
        t, inf = widths[min(k, len(widths) - 1)]
        if k == segs - 1:
            stick(bone, f"snake{i}_head", p, add(p, mul(d, 1.7)), 1, SERPENT, depth=1, inflate=0.12, detail=snake_head)
        else:
            stick(bone, f"snake{i}_{k}", p, q, t, SERPENT, inflate=inf)
        p = q
        d = norm(turn(d, axis, curl * (1 if k < 3 else -0.6)))


# ----------------------------------------------------------------------------------------------- the body

def skirt_strip(body, name, phi, length, width, flare, top=13.2):
    """A strip of her torn dress hanging from the hips at phi degrees round them (0 at the front), flaring out."""
    a = math.radians(phi)
    r = 1.0 / math.sqrt((math.sin(a) / 3.4) ** 2 + (math.cos(a) / 2.3) ** 2)
    body.cube(name, (-width / 2, top - length, -r - 0.5), (width, length, 1), ROBE, rotation=(flare, phi, 0), origin=(0, top, 0),
              detail=torn)


def hydra(body):
    """The green hydra she is girt with: a coil round the waist, knotted at the front, two heads hanging at the hip."""
    ring = [(math.sin(math.radians(a)) * 3.8, math.cos(math.radians(a)) * -2.7) for a in range(0, 360, 45)]
    for k in range(len(ring)):
        a, b = ring[k], ring[(k + 1) % len(ring)]
        y = 12.6 + 0.35 * math.sin(k * 0.8)
        stick(body, f"hydra_coil{k}", (a[0], y, a[1]), (b[0], y + 0.2, b[1]), 2, HYDRA, inflate=-0.3)
    body.cube("hydra_knot", (0.6, 11.8, -3.6), (2, 2, 2), HYDRA, inflate=0.05)
    for n, (start, pts) in enumerate((((1.6, 12.2, -3.4), [(2.6, 10.6, -3.6), (2.8, 8.6, -3.3), (2.2, 7.2, -3.6)]),
                                      ((2.4, 12.0, -3.0), [(3.6, 10.8, -2.8), (4.1, 9.4, -2.2), (4.6, 8.4, -2.6)]))):
        p = start
        for k, q in enumerate(pts):
            stick(body, f"hydra_neck{n}_{k}", p, q, 1 if k else 2, HYDRA, inflate=0.1 if k else -0.35)
            p = q
        d = norm(sub(pts[-1], pts[-2]))
        stick(body, f"hydra_head{n}", p, add(p, mul(norm(add(d, (0, 0, -0.8))), 2)), 2, HYDRA, depth=1, inflate=-0.2, detail=snake_head)


def build():
    m = Model("fury", texture_width=256)
    body = m.bone("body", (0, 14, 0), rotation=(-15, 0, 0))

    # hips and the dress: an underskirt, then strips torn from the skirt round it, flaring as she flies
    body.cube("hips", (-3.5, 10.5, -2.2), (7, 4, 4), ROBE, inflate=0.1)
    body.cube("underskirt", (-3, 4, -2), (6, 7, 4), ROBE, detail=torn)
    for k, phi in enumerate(range(0, 360, 30)):
        length = (11, 9, 12, 10, 8, 11, 12, 9, 10, 12, 8, 10)[k]
        skirt_strip(body, f"skirt{k}", phi, length, 3 if k % 3 else 2, 9 + (k * 7) % 8)
    hydra(body)

    # her legs dangle under the rags: bony shins, and long feet with clawed toes
    for side, s in (("right", 1), ("left", -1)):
        stick(body, f"{side}_shin", (1.3 * s, 5.5, 0), (1.5 * s, 1.6, 0.5), 2, SKIN, inflate=-0.45)
        stick(body, f"{side}_foot", (1.5 * s, 1.8, 0.4), (1.7 * s, 0.6, -1.6), 2, SKIN, depth=1, inflate=-0.2)
        for t, dx in enumerate((-0.4, 0.4)):
            stick(body, f"{side}_toe{t}", (1.7 * s + dx, 0.8, -1.5), (1.7 * s + dx * 1.4, 0.0, -2.6), 1, CLAW, inflate=-0.15)

    torso = body.bone("torso", (0, 14, 0))
    # a starved frame: a pinched waist in the bodice of her dress, ribs, collarbones, a knobbed spine
    torso.cube("waist", (-2.5, 13.5, -1.6), (5, 4, 3), SKIN, inflate=0.05)
    torso.cube("bodice", (-3, 13.5, -2), (6, 4, 4), ROBE, inflate=0.1, detail=torn_top)
    torso.cube("ribcage", (-3.5, 17, -2.1), (7, 4, 4), SKIN, detail=ribs)
    torso.cube("chest", (-4, 20.5, -1.8), (8, 2, 4), SKIN, detail=collar)
    torso.cube("back", (-3, 16.5, 1.6), (6, 6, 1), SKIN, inflate=-0.1, detail=spine)
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        torso.cube(f"{side}_shoulder_blade", (x(0.8, 2), 19, 1.9), (2, 3, 1), SKIN, inflate=-0.1,
                   rotation=(0, 0, -12 * s), origin=(1.8 * s, 20.5, 2.2))
        stick(torso, f"{side}_trapezius", (0.6 * s, 23.6, 0.2), (4.0 * s, 22.2, 0.2), 2, SKIN, inflate=-0.3)
    # what is left of the dress: a strap over one shoulder
    stick(torso, "strap", (-1.6, 17, -2.4), (-2.6, 22.8, -1.2), 1, ROBE, inflate=0.05)
    torso.cube("neck", (-1, 22, -0.8), (2, 3, 2), SKIN, inflate=-0.1, detail=neck_detail, rotation=(-8, 0, 0), origin=(0, 22, 0))

    head = torso.bone("head", (0, 24, 0))
    head.cube("skull", (-2.5, 25.5, -2.3), (5, 5, 5), SKIN, detail=skull_face)
    head.cube("crown", (-2, 30.2, -1.8), (4, 1, 4), SKIN, inflate=-0.1)
    head.cube("occiput", (-2, 26, 2.4), (4, 4, 1), SKIN, inflate=0.1)
    head.cube("brow", (-2.5, 28.2, -2.9), (5, 1, 1), SKIN, inflate=0.02)
    stick(head, "nose", (0, 28.3, -2.55), (0, 26.6, -3.35), 1, SKIN, inflate=-0.05)
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        head.cube(f"{side}_cheekbone", (x(1.8, 1), 26.6, -2.6), (1, 1, 2), SKIN, inflate=0.05, detail=cheekbone)
        stick(head, f"{side}_ear", (2.4 * s, 27.4, 0.2), (3.4 * s, 29.4, 1.0), 1, SKIN, depth=1, inflate=-0.1)
    jaw = head.bone("jaw", (0, 25.8, 0.6))
    jaw.cube("jaw", (-2, 24, -2.6), (4, 2, 4), SKIN, inflate=-0.2, detail=jaw_detail)
    jaw.cube("chin", (-0.5, 23.7, -2.65), (1, 1, 1), SKIN, inflate=0.05)
    for i in range(len(SNAKES)):
        serpent(head, i)

    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        arm = torso.bone(f"{side}_arm", (4.5 * s, 23, 0))
        arm.cube(f"{side}_deltoid", (x(3.4, 2), 21.2, -1.1), (2, 2, 2), SKIN, inflate=0.1)
        stick(arm, f"{side}_upper_arm", (4.6 * s, 22.4, 0), (5.0 * s, 17.8, 0.3), 2, SKIN, inflate=-0.35)
        arm.cube(f"{side}_elbow", (x(4.5, 1), 17.4, 0.6), (1, 1, 1), SKIN, inflate=0.05)
        forearm = arm.bone(f"{side}_forearm", (5.0 * s, 17.8, 0.3))
        stick(forearm, f"{side}_forearm", (5.0 * s, 17.8, 0.3), (5.1 * s, 13.6, -0.6), 2, SKIN, inflate=-0.45)
        stick(forearm, f"{side}_forearm_muscle", (5.0 * s, 17.6, 0.2), (5.05 * s, 15.6, -0.2), 2, SKIN, inflate=-0.3)
        # the hand: a long palm, a hooked thumb and three clawed fingers, bloody to the wrist
        wrist = (5.1 * s, 13.7, -0.6)
        stick(forearm, f"{side}_palm", wrist, (5.2 * s, 12.0, -1.0), 2, SKIN, depth=1, inflate=-0.1, detail=bloodied_hand)
        stick(forearm, f"{side}_thumb", (4.5 * s, 13.0, -1.0), (4.0 * s, 11.6, -1.9), 1, SKIN, inflate=-0.1)
        stick(forearm, f"{side}_thumb_claw", (4.0 * s, 11.7, -1.9), (4.1 * s, 10.9, -2.6), 1, CLAW, inflate=-0.2)
        for k, dx in enumerate((-0.6, 0.0, 0.6)):
            knuckle = (5.2 * s + dx * s, 12.2, -1.0)
            mid = (5.2 * s + dx * s * 1.3, 10.4, -1.3)
            stick(forearm, f"{side}_finger{k}", knuckle, mid, 1, SKIN, inflate=-0.12, detail=bloodied_hand)
            stick(forearm, f"{side}_claw{k}", mid, (5.2 * s + dx * s * 1.4, 9.0, -2.2), 1, CLAW, inflate=-0.18)
        wing(torso, side, s)
    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)


def flap(a, length, up=-30, down=25):
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(0, (0, -20 * s, up * s)), (length * 0.5, (0, 20 * s, down * s)), (length, (0, -20 * s, up * s))])
        # the outer wing lags behind the arm: it trails on the downstroke and flicks on the upstroke
        a.rot(f"{side}_wing_tip", [(0, (0, -10 * s, 12 * s)), (length * 0.25, (0, 0, -6 * s)), (length * 0.5, (0, 12 * s, -14 * s)),
                                   (length * 0.75, (0, 0, 10 * s)), (length, (0, -10 * s, 12 * s))])


def writhe(a, length, amount=15):
    for i in range(len(SNAKES)):
        sgn = 1 if i % 2 else -1
        if i < 6:
            a.rot(f"snake{i}", [(0, ZERO), (length * 0.25, (0, 0, amount * sgn)), (length * 0.75, (0, 0, -amount * sgn)), (length, ZERO)])
        else:
            a.rot(f"snake{i}", [(0, ZERO), (length * 0.25, (amount * sgn * 0.7, 0, 0)), (length * 0.75, (-amount * sgn * 0.7, 0, 0)), (length, ZERO)])
        # the wave runs along each serpent, a quarter beat behind at every joint
        a.rot(f"snake{i}_mid", [(0, (0, 0, -amount * sgn)), (length * 0.5, (0, 0, amount * sgn)), (length, (0, 0, -amount * sgn))])
        a.rot(f"snake{i}_tip", [(0, (amount * 1.4, 0, 0)), (length * 0.5, (-amount * 1.4, 0, 0)), (length, (amount * 1.4, 0, 0))])


def animations(m):
    # hovering, wings beating, the serpents of her hair never still
    idle = m.animation("idle", 0.8, loop=True)
    flap(idle, 0.8)
    writhe(idle, 0.8)
    idle.pos("body", [(0, ZERO), (0.4, (0, 1.5, 0)), (0.8, ZERO)])
    idle.rot("right_arm", [(0, (20, 0, 20)), (0.8, (20, 0, 20))])
    idle.rot("left_arm", [(0, (20, 0, -20)), (0.8, (20, 0, -20))])
    idle.rot("right_forearm", [(0, (25, 0, 0)), (0.8, (25, 0, 0))])
    idle.rot("left_forearm", [(0, (25, 0, 0)), (0.8, (25, 0, 0))])

    # her talons: she draws back, flashes white, and rakes down
    talons = m.animation("talons", 1.4)
    flap(talons, 0.7)
    talons.rot("right_arm", [(0, (20, 0, 20)), (0.5, (170, 0, 30)), (0.9, (175, 0, 30)), (1.0, (40, 0, 0)), (1.4, (20, 0, 20))])
    talons.rot("left_arm", [(0, (20, 0, -20)), (0.5, (170, 0, -30)), (0.9, (175, 0, -30)), (1.0, (40, 0, 0)), (1.4, (20, 0, -20))])
    for side in ("right", "left"):
        talons.rot(f"{side}_forearm", [(0, (25, 0, 0)), (0.5, (50, 0, 0)), (0.9, (55, 0, 0)), (1.0, (5, 0, 0)), (1.4, (25, 0, 0))])
    talons.rot("body", [(0, ZERO), (0.5, (20, 0, 0)), (1.0, (-40, 0, 0)), (1.4, ZERO)])
    talons.rot("jaw", [(0, ZERO), (0.9, (-10, 0, 0)), (1.0, (-30, 0, 0)), (1.4, ZERO)])
    talons.event(0.5, "flash").event(1.0, "hit")

    # the shriek: head back, jaw wide, wings and arms flung wide
    shriek = m.animation("shriek", 1.6)
    writhe(shriek, 0.4)
    shriek.rot("head", [(0, ZERO), (0.3, (50, 0, 0)), (1.3, (50, 0, 0)), (1.6, ZERO)])
    shriek.rot("jaw", [(0, ZERO), (0.3, (-40, 0, 0)), (1.3, (-40, 0, 0)), (1.6, ZERO)])
    shriek.rot("right_arm", [(0, (20, 0, 20)), (0.3, (20, 0, 110)), (1.3, (20, 0, 110)), (1.6, (20, 0, 20))])
    shriek.rot("left_arm", [(0, (20, 0, -20)), (0.3, (20, 0, -110)), (1.3, (20, 0, -110)), (1.6, (20, 0, -20))])
    for side, s in (("right", 1), ("left", -1)):
        shriek.rot(f"{side}_wing", [(0, ZERO), (0.3, (0, 0, -50 * s)), (1.3, (0, 0, -50 * s)), (1.6, ZERO)])
        shriek.rot(f"{side}_wing_tip", [(0, ZERO), (0.3, (0, 0, -15 * s)), (1.3, (0, 0, -15 * s)), (1.6, ZERO)])
        shriek.rot(f"{side}_forearm", [(0, (25, 0, 0)), (0.3, (10, 0, 0)), (1.3, (10, 0, 0)), (1.6, (25, 0, 0))])
    shriek.event(0.4, "scream")

    # the lash: she whips her head round and the serpents crack like a scourge
    lash = m.animation("lash", 1.2)
    flap(lash, 0.6)
    lash.rot("head", [(0, ZERO), (0.4, (0, 70, 0)), (0.6, (-20, -80, 0)), (1.2, ZERO)])
    lash.rot("torso", [(0, ZERO), (0.4, (0, 30, 0)), (0.6, (0, -40, 0)), (1.2, ZERO)])
    lash.rot("jaw", [(0, ZERO), (0.4, (-20, 0, 0)), (0.7, (-25, 0, 0)), (1.2, ZERO)])
    for i in range(len(SNAKES)):
        lash.rot(f"snake{i}", [(0, ZERO), (0.4, (0, 0, -40)), (0.6, (60, 0, 50)), (1.2, ZERO)])
        lash.rot(f"snake{i}_mid", [(0, ZERO), (0.45, (0, 0, -30)), (0.65, (30, 0, 35)), (1.2, ZERO)])
        lash.rot(f"snake{i}_tip", [(0, ZERO), (0.5, (-30, 0, -20)), (0.7, (40, 0, 30)), (1.2, ZERO)])
    lash.event(0.6, "lash")

    # rending their breasts: blood falls round you
    tear = m.animation("tear", 1.6)
    flap(tear, 0.8)
    tear.rot("right_arm", [(0, (20, 0, 20)), (0.5, (80, 40, 0)), (0.8, (60, -30, 0)), (1.1, (80, 40, 0)), (1.6, (20, 0, 20))])
    tear.rot("left_arm", [(0, (20, 0, -20)), (0.5, (80, -40, 0)), (0.8, (60, 30, 0)), (1.1, (80, -40, 0)), (1.6, (20, 0, -20))])
    for side in ("right", "left"):
        tear.rot(f"{side}_forearm", [(0, (25, 0, 0)), (0.5, (60, 0, 0)), (0.8, (40, 0, 0)), (1.1, (60, 0, 0)), (1.6, (25, 0, 0))])
    tear.rot("head", [(0, ZERO), (0.5, (30, 0, 0)), (1.3, (30, 0, 0)), (1.6, ZERO)])
    tear.rot("jaw", [(0, ZERO), (0.5, (-30, 0, 0)), (1.3, (-30, 0, 0)), (1.6, ZERO)])
    tear.event(0.8, "tear")

    # struck from the air: she tumbles, wings askew
    stagger = m.animation("stagger", 2.2)
    stagger.rot("body", [(0, ZERO), (0.3, (-60, 0, 40)), (1.8, (-70, 0, 40)), (2.2, ZERO)])
    stagger.pos("body", [(0, ZERO), (0.4, (0, -10, 0)), (1.8, (0, -10, 0)), (2.2, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.3, (-20, 0, 0)), (1.8, (-15, 0, 0)), (2.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        stagger.rot(f"{side}_wing", [(0, ZERO), (0.3, (0, 40 * s, 40 * s)), (1.8, (0, 40 * s, 40 * s)), (2.2, ZERO)])
        stagger.rot(f"{side}_wing_tip", [(0, ZERO), (0.3, (0, 30 * s, 25 * s)), (1.8, (0, 30 * s, 25 * s)), (2.2, ZERO)])

    # "Venga Medusa!": they turn to the wall and call her
    phase = m.animation("phase", 2.6)
    flap(phase, 0.65)
    phase.rot("right_arm", [(0, (20, 0, 20)), (0.6, (170, 0, -10)), (2.2, (170, 0, -10)), (2.6, (20, 0, 20))])
    phase.rot("left_arm", [(0, (20, 0, -20)), (0.6, (170, 0, 10)), (2.2, (170, 0, 10)), (2.6, (20, 0, -20))])
    phase.rot("head", [(0, ZERO), (0.6, (45, 0, 0)), (2.2, (45, 0, 0)), (2.6, ZERO)])
    phase.rot("jaw", [(0, ZERO), (0.7, (-35, 0, 0)), (2.2, (-35, 0, 0)), (2.6, ZERO)])
    phase.event(0.8, "call")

    # their entrance: up from behind the battlements, shrieking
    intro = m.animation("intro", 4.0)
    flap(intro, 0.5)
    intro.pos("body", [(0, (0, -30, 0)), (1.0, (0, -30, 0)), (2.0, ZERO), (4.0, ZERO)])
    intro.rot("head", [(0, ZERO), (2.2, (50, 0, 0)), (3.4, (50, 0, 0)), (4.0, ZERO)])
    intro.rot("jaw", [(0, ZERO), (2.0, ZERO), (2.2, (-40, 0, 0)), (3.4, (-40, 0, 0)), (4.0, ZERO)])
    writhe(intro, 1.0, 20)
    intro.event(1.0, "rise").event(2.2, "scream")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (20, 0, 10)), (0.3, ZERO)])
    hurt.rot("jaw", [(0, ZERO), (0.1, (-20, 0, 0)), (0.3, ZERO)])

    # she falls out of the air
    death = m.animation("death", 1.6)
    death.rot("body", [(0, ZERO), (0.6, (-80, 0, 60)), (1.6, (-90, 0, 80))])
    death.pos("body", [(0, ZERO), (0.8, (0, -10, 0)), (1.6, (0, -13, 0))])
    death.rot("jaw", [(0, ZERO), (0.4, (-35, 0, 0)), (1.6, (-25, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, ZERO), (0.6, (0, 30 * s, 70 * s)), (1.6, (0, 30 * s, 80 * s))])
        death.rot(f"{side}_wing_tip", [(0, ZERO), (0.6, (0, 40 * s, 30 * s)), (1.6, (0, 60 * s, 40 * s))])


if __name__ == "__main__":
    build().write()
