#!/usr/bin/env python3
"""The angels of Paradiso: the heavens' own host, who wheel round every sphere's great island and fly in and out of
the Celestial Rose "like a swarm of bees" (Paradiso XXXI), and, as the Angelic Guard, test those who climb (Mars's
trial, the Seraph's fight). A tall slender figure in a white robe with a gold hem and girdle, a calm face under pale
gold hair, a gold ring of light behind the head and two great white wings, each built of feathers.

Run from the repo root:  python3 tools/art/angel.py && python3 tools/gen_bosses.py

It shares the Seraph's way of building (tools/art/seraph.py: its feathers, face and hands), drawn at its own size,
about 2.3 blocks tall. Animations: idle (hovering, slow wingbeats), fly (leaning into it, faster beats), attack (the
Guard's blow: wings drawn back, then a sweep of the right hand of light, "hit" when it lands), hurt, death (it folds
its wings and rises into the light, fading) and sing (arms raised, wings spread wide: the host round the Rose).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402
from seraph import FEATHER, stick, side_x, lerp, add, mul, norm, feather, calm_face, lips, palm, gold_rim  # noqa: E402

ROBE = material("robe", "robe", base="f6f2e8", fold="cfc6ae", trim="e2b648")
UNDER = material("under", "robe", base="e4dccb", fold="bdb299", trim="d8a83c")
GOLD = material("gold", "metal", base="d8b040", high="fff0a0", low="8a6418")
SKIN = material("skin", "skin", base="fbe8cc", dark="dcbc98")
HAIR = material("hair", "hair", light="fff4cc", dark="c8a456")
LIGHT = hexc("fff4c0")
LIGHT_HOT = hexc("ffffff")
CLEAR = (0, 0, 0)

ZERO = (0, 0, 0)
HOVER = (0, 2, 0)


# ----------------------------------------------------------------------------------------------- details

def gold_hem(faces):
    """A panel of the robe: white, a gold band at the hem with a line of light above it, the hem itself ragged with
    light (cut away every other pixel), as if the robe ends in brightness rather than cloth."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.px(x, f.h - 2, GOLD.c["base"])
            f.px(x, f.h - 3, GOLD.c["high"])
            if x % 2 == 0:
                f.px(x, f.h - 1, CLEAR, alpha=0)
            else:
                f.glow(x, f.h - 1, LIGHT)
        if name == "front" and f.w >= 3:
            for y in range(f.h - 4):
                f.px(0, y, shade(ROBE.c["fold"], 0.96))
    for y in range(faces["bottom"].h):
        for x in range(faces["bottom"].w):
            faces["bottom"].px(x, y, CLEAR, alpha=0)


def girdle(faces):
    f = faces["front"]
    f.glow(f.w // 2, f.h // 2, LIGHT_HOT)


def halo_light(faces):
    for name in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, LIGHT_HOT if (x + y) % 3 else LIGHT)


# ----------------------------------------------------------------------------------------------- the body

def robe_panel(body, name, phi, length, width, flare, top=21.0):
    """A panel of the robe hanging from the girdle at phi degrees round the waist (0 at the front), flaring a little."""
    a = math.radians(phi)
    r = 1.0 / math.sqrt((math.sin(a) / 3.9) ** 2 + (math.cos(a) / 3.0) ** 2)
    body.cube(name, (-width / 2, top - length, -r - 0.5), (width, length, 1), ROBE, rotation=(flare, phi, 0), origin=(0, top, 0),
              detail=gold_hem)


def plumage(bone, name, z, arm, feathers, coverts):
    """Feathers along a wing's arm, as the Seraph's (tools/art/seraph.py) but white to the tips: an angel of the host
    doesn't burn as the seraphim do."""
    for k in range(len(arm) - 1):
        stick(bone, f"{name}_arm{k}", arm[k], arm[k + 1], 2, FEATHER, depth=2, inflate=0.05 - 0.1 * k, detail=feather(burning=False))
    for k, (base, tip, width) in enumerate(feathers):
        dz = 0.07 * k
        stick(bone, f"{name}_feather{k}", (base[0], base[1], z + dz), (tip[0], tip[1], z + dz), width, FEATHER, depth=1,
              inflate=-0.3, detail=feather(burning=False))
    for k, (base, tip) in enumerate(coverts):
        stick(bone, f"{name}_covert{k}", (base[0], base[1], z), (tip[0], tip[1], z), 3, FEATHER, depth=1, inflate=-0.05,
              detail=feather(burning=False))


def wing(chest, side, s):
    """A great wing, half spread: the arm rises from the shoulder blade to the wrist, the secondaries hang from the
    forearm, the primaries fan from the hand (the wing tip's own bone), coverts over their roots."""
    w = chest.bone(f"{side}_wing", (3 * s, 29, 3), rotation=(0, -18 * s, 18 * s))
    z = 3.6
    R, E, W, H = (3.5 * s, 28.5, z), (10 * s, 31.5, z), (17 * s, 33.0, z), (24 * s, 31.0, z)
    feathers, coverts = [], []
    for k in range(6):
        t = 0.1 + k * 0.16
        base = lerp(R, E, t * 2) if t < 0.5 else lerp(E, W, (t - 0.5) * 2)
        d = norm(((0.08 + 0.12 * t) * s, -1, 0))
        feathers.append((base, add(base, mul(d, 10.5 + 1.5 * t)), 3))
        if k % 2 == 0:
            coverts.append((add(base, (0, 0.6, 0)), add(base, mul(norm(((0.25 + 0.2 * t) * s, -1, 0)), 4.5))))
    plumage(w, f"{side}_wing", z, [R, E, W], feathers, coverts)
    tip = w.bone(f"{side}_wing_tip", W)
    feathers, coverts = [], []
    for k in range(5):
        t = k / 4
        base = lerp(W, H, t)
        ang = math.radians(-72 + 60 * t)
        d = (math.cos(ang) * s, math.sin(ang), 0)
        feathers.append((base, add(base, mul(d, 11 - 4.5 * t)), 3))
        if k < 4:
            coverts.append((add(base, (0, 0.6, 0)), add(base, mul(norm((math.cos(ang + 0.3) * s, math.sin(ang + 0.3), 0)), 3.8))))
    plumage(tip, f"{side}_wing_tip", z + 0.6, [W, H], feathers, coverts)


def build():
    m = Model("angel", texture_width=256)
    root = m.bone("root", (0, 0, 0))
    body = root.bone("body", (0, 12, 0))

    # the robe: an underskirt, panels round it flaring to a hem of light, a gold girdle
    body.cube("underskirt", (-3.5, 2, -2.8), (7, 19, 6), UNDER, detail=gold_hem)
    for k, phi in enumerate(range(0, 360, 36)):
        robe_panel(body, f"robe{k}", phi, 19 + (k * 5) % 3, 3 if k % 2 else 4, 4 + (k * 3) % 4)
    body.cube("girdle", (-3.7, 20.3, -2.9), (7, 1, 6), GOLD, inflate=0.15, detail=girdle)
    stick(body, "girdle_end", (1.2, 20.6, -3.1), (1.6, 15.6, -3.6), 1, GOLD, inflate=-0.05)

    chest = body.bone("chest", (0, 22, 0))
    chest.cube("waist", (-3, 21, -2.2), (6, 3, 4), ROBE)
    chest.cube("bodice", (-3.5, 24, -2.4), (7, 4, 5), ROBE, inflate=0.05)
    chest.cube("shoulders", (-4.5, 27.5, -2.2), (9, 2, 4), ROBE)
    chest.cube("collar", (-2, 28.8, -2.0), (4, 1, 4), GOLD, inflate=0.05)
    chest.cube("neck", (-1, 29, -1), (2, 2, 2), SKIN, inflate=-0.05)
    stick(chest, "stole_right", (1.8, 29.2, -2.4), (1.4, 17.4, -3.6), 1, GOLD, depth=1, inflate=0.05)
    stick(chest, "stole_left", (-1.8, 29.2, -2.4), (-1.4, 17.4, -3.6), 1, GOLD, depth=1, inflate=0.05)

    head = chest.bone("head", (0, 31, 0))
    head.cube("cranium", (-2.5, 32.4, -2.4), (5, 5, 5), SKIN, detail=calm_face)
    head.cube("lower_face", (-2, 31.2, -2.4), (4, 2, 4), SKIN, detail=lips)
    head.cube("chin", (-1, 31.0, -2.6), (2, 1, 1), SKIN, inflate=-0.05)
    head.cube("brow", (-2.5, 35.3, -2.65), (5, 1, 1), SKIN, inflate=-0.2)
    stick(head, "nose", (0, 35.1, -2.45), (0, 33.3, -3.2), 1, SKIN)
    head.cube("hair_cap", (-2.8, 36.4, -2.7), (6, 2, 6), HAIR, inflate=0.1)
    head.cube("hair_back", (-2.7, 31.5, 2.1), (5, 6, 1), HAIR, inflate=0.2)
    for side, s in (("right", 1), ("left", -1)):
        stick(head, f"{side}_curl", (2.6 * s, 37.4, -1.4), (2.9 * s, 33.0, -0.4), 1, HAIR, depth=2, inflate=0.1)
        stick(head, f"{side}_hair", (1.6 * s, 37.6, 2.0), (2.4 * s, 30.4, 3.0), 2, HAIR, depth=1, inflate=0.05)
        stick(head, f"{side}_ear", (2.5 * s, 33.6, 0.2), (2.8 * s, 35.0, 0.8), 1, SKIN, inflate=-0.1)

    # the ring of light behind the head
    halo = head.bone("halo", (0, 36, 4))
    ring = 12
    for k in range(ring):
        a0, a1 = 2 * math.pi * k / ring, 2 * math.pi * (k + 1) / ring
        p0 = (4.6 * math.cos(a0), 36 + 4.6 * math.sin(a0), 4.4)
        p1 = (4.6 * math.cos(a1), 36 + 4.6 * math.sin(a1), 4.4)
        stick(halo, f"halo{k}", p0, p1, 1, GOLD, inflate=0.05, detail=halo_light)

    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        arm = chest.bone(f"{side}_arm", (4.6 * s, 28.6, 0))
        # a wide sleeve, gathered at the shoulder and falling open at the wrist
        stick(arm, f"{side}_upper_arm", (4.8 * s, 28.8, 0), (5.2 * s, 24.2, 0.2), 2, ROBE, depth=3, inflate=0.1)
        forearm = arm.bone(f"{side}_forearm", (5.2 * s, 24.2, 0.2))
        forearm.cube(f"{side}_elbow", (x(4.3, 2), 23.6, -0.6), (2, 2, 2), ROBE, inflate=0.05)
        stick(forearm, f"{side}_sleeve", (5.2 * s, 24.0, 0.2), (5.4 * s, 19.6, -0.6), 3, ROBE, depth=3, inflate=0.05, detail=gold_rim)
        wrist = (5.4 * s, 19.8, -0.6)
        stick(forearm, f"{side}_palm", wrist, (5.4 * s, 18.0, -0.9), 2, SKIN, depth=1, inflate=0.05, detail=palm)
        stick(forearm, f"{side}_thumb", (4.8 * s, 19.0, -1.0), (4.5 * s, 17.8, -1.4), 1, SKIN, inflate=-0.12)
        for k, dx in enumerate((-0.45, 0.45)):
            stick(forearm, f"{side}_fingers{k}", (5.4 * s + dx, 18.2, -0.9), (5.4 * s + dx * 1.1, 16.8, -1.2), 1, SKIN, inflate=-0.05)
        wing(chest, side, s)
    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

def flap(a, length, amount=14, start=0.0):
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(start, (0, -18 * s, 10 * s)), (start + length * 0.5, (0, -18 * s, (18 + amount) * s)),
                               (start + length, (0, -18 * s, 10 * s))])
        # the hand of the wing lags the arm: it trails as the wing rises and sweeps through as it falls
        a.rot(f"{side}_wing_tip", [(start, (0, 0, 4 * s)), (start + length * 0.3, (0, 0, -amount * 0.4 * s)),
                                   (start + length * 0.8, (0, 0, amount * 0.4 * s)), (start + length, (0, 0, 4 * s))])


def animations(m):
    # hovering, its wings slowly beating, the halo turning
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("root", [(0, HOVER), (1.5, (0, 3.5, 0)), (3.0, HOVER)])
    flap(idle, 3.0, 10)
    idle.rot("halo", [(0, ZERO), (3.0, (0, 0, 360))], interp="linear")
    idle.rot("right_arm", [(0, (8, 0, 6)), (3.0, (8, 0, 6))])
    idle.rot("left_arm", [(0, (8, 0, -6)), (3.0, (8, 0, -6))])

    # flying: leaning into the flight, the robe streaming back, quick strong beats
    fly = m.animation("fly", 1.2, loop=True)
    fly.pos("root", [(0, HOVER), (0.6, (0, 3, 0)), (1.2, HOVER)])
    fly.rot("body", [(0, (-28, 0, 0)), (1.2, (-28, 0, 0))])
    fly.rot("head", [(0, (22, 0, 0)), (1.2, (22, 0, 0))])
    fly.rot("right_arm", [(0, (-20, 0, 10)), (1.2, (-20, 0, 10))])
    fly.rot("left_arm", [(0, (-20, 0, -10)), (1.2, (-20, 0, -10))])
    flap(fly, 0.6, 22)
    flap(fly, 0.6, 22, start=0.6)

    # the Guard's blow: wings drawn back and the right hand raised (light gathering), then swept down and forward
    attack = m.animation("attack", 0.9)
    attack.pos("root", [(0, HOVER), (0.9, HOVER)])
    attack.rot("right_arm", [(0, ZERO), (0.3, (160, 0, 20)), (0.5, (60, 0, 0)), (0.9, ZERO)])
    attack.rot("right_forearm", [(0, ZERO), (0.3, (20, 0, 0)), (0.5, ZERO), (0.9, ZERO)])
    attack.rot("chest", [(0, ZERO), (0.3, (10, 0, 0)), (0.5, (-18, 0, 0)), (0.9, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        attack.rot(f"{side}_wing", [(0, (0, -18 * s, 10 * s)), (0.3, (0, -40 * s, 40 * s)), (0.5, (0, 20 * s, 0)), (0.9, (0, -18 * s, 10 * s))])
    attack.event(0.5, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("chest", [(0, ZERO), (0.1, (14, 0, 6)), (0.3, ZERO)])

    # it is called home: it folds its wings, bows, and rises into the light, fading to nothing
    death = m.animation("death", 1.6)
    death.pos("root", [(0, HOVER), (0.4, HOVER), (1.6, (0, 48, 0))])
    death.rot("chest", [(0, ZERO), (0.4, (-20, 0, 0)), (1.0, ZERO)])
    death.rot("head", [(0, ZERO), (0.4, (-25, 0, 0)), (1.0, (20, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, (0, -18 * s, 10 * s)), (0.4, (0, 40 * s, -10 * s)), (1.6, (0, 0, 40 * s))])
    death.scale("root", [(0, (1, 1, 1)), (0.8, (1, 1, 1)), (1.6, (0.05, 0.05, 0.05))], interp="linear")

    # the host's song round the Rose: arms raised, wings spread wide, the head lifted
    sing = m.animation("sing", 3.0)
    sing.pos("root", [(0, HOVER), (1.5, (0, 4, 0)), (3.0, HOVER)])
    sing.rot("right_arm", [(0, ZERO), (0.6, (150, 0, 25)), (2.4, (150, 0, 25)), (3.0, ZERO)])
    sing.rot("left_arm", [(0, ZERO), (0.6, (150, 0, -25)), (2.4, (150, 0, -25)), (3.0, ZERO)])
    sing.rot("head", [(0, ZERO), (0.6, (-20, 0, 0)), (2.4, (-20, 0, 0)), (3.0, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        sing.rot(f"{side}_wing", [(0, (0, -18 * s, 10 * s)), (0.6, (0, 0, 40 * s)), (2.4, (0, 0, 40 * s)), (3.0, (0, -18 * s, 10 * s))])


if __name__ == "__main__":
    build().write()
