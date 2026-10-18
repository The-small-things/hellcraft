#!/usr/bin/env python3
"""The glutton (Gluttony's monster): a soul swollen past its shape, sunk in the mire under the rain "like a dog that
barking craves" (Inferno VI). It waits half-buried and heaves itself up out of the mud when someone comes near; its
blow is a slam of its belly that leaves you sick and starving, and it bursts into filth when it dies.

The body is one great rounded gut (a core with a slab on every side, the paunch sagging low and forward), sagging
breasts and sloping shoulders, a small head lost in jowls and a double chin, piggy eyes, an upturned snout and a
gaping, drooling mouth, a few greasy strands combed over the scalp. Flabby arms bent at the elbow end in pudgy hands
with fingers and a thumb; stubby legs vanish into the mud they stand in.

Run from the repo root:  python3 tools/art/glutton.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix, value_noise  # noqa: E402

SKIN = material("skin", "skin", base="8a8a5a", dark="5a5a36")
BELLY = material("belly", "skin", base="a49a6a", dark="6a6440")
MUD = material("mud", "hair", light="3e3220", dark="1e180e")
HAIR = material("hair", "hair", light="3a3a26", dark="16160c")
LIP = material("lip", "flat", base="8a5a4a")
EYE = hexc("e8f05a")
MOUTH = hexc("2a1a0a")
DROOL = hexc("c8d0a0")
VEIN = hexc("6a7a6a")
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
    """The side faces shaded along their lowest rows (where a part sinks into shadow) and their highest (where it
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
            d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def mud(amount=0.35, low=0.0, faces=SIDES):
    """The mire it lies in, dried on it in blotches (more toward the bottom of each face when low > 0), wet and dark
    below, a paler crust on top, drips running down."""
    def detail(fs):
        for name in faces:
            f = fs[name]
            n = value_noise(f, 3)
            hit = set()
            for fy in range(f.h):
                for fx in range(f.w):
                    bias = low * (fy / max(1, f.h - 1)) if name not in ("top", "bottom") else 0.0
                    if n(fx, fy) + bias > 1.0 - amount * 2:
                        hit.add((fx, fy))
            for fx, fy in hit:
                c = mix(MUD.c["dark"], MUD.c["light"], 0.3 + 0.7 * f.rng.random())
                if (fx, fy - 1) not in hit:
                    c = shade(MUD.c["light"], 1.3)
                f.px(fx, fy, c)
            for _ in range(max(1, f.w * f.h // 30)):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                if (x, y) in hit:
                    for i in range(f.rng.randint(1, 4)):
                        f.px(x, y + i, MUD.c["dark"])
    return detail


def stretched(fs):
    """Skin stretched over the gut: pale stretch marks in arcs, blue veins, a greasy sheen near the top, and the
    creases where the fat folds under (darker along the bottom)."""
    for name in SIDES:
        f = fs[name]
        for _ in range(f.w // 3):
            x, y = f.rng.randrange(1, max(2, f.w - 3)), f.rng.randrange(1, max(2, f.h - 2))
            for i in range(3):
                f.px(x + i, y + (1 if i == 1 else 0), shade(BELLY.c["base"], 1.16))
        x = f.rng.randrange(f.w)
        for y in range(f.h // 3, f.h):
            if f.rng.random() < 0.4:
                x = max(0, min(f.w - 1, x + f.rng.choice((-1, 1))))
            if f.rng.random() < 0.7:
                f.px(x, y, mix(VEIN, BELLY.c["base"], 0.35))
        for x in range(f.w):
            mul(f, x, 0, 1.08)


def soft(fs):
    """Undoes the rim shading the material gives every face, so the slabs that round off the gut blend into one
    body instead of reading as plates."""
    for f in fs.values():
        if f.w < 3 or f.h < 3:
            continue
        for fy in range(f.h):
            for fx in range(f.w):
                rim = fx == 0 or fy == f.h - 1 or fx == f.w - 1 or (fy == 0 and f.name in ("top", "bottom"))
                top = fy == 0 and f.name not in ("top", "bottom")
                if rim and fx in (0, f.w - 1) and fy in (0, f.h - 1):
                    mul(f, fx, fy, 1 / (0.8 * 0.92))
                elif rim:
                    mul(f, fx, fy, 1 / 0.8)
                elif top:
                    mul(f, fx, fy, 1 / 1.1)


def rounded(fs):
    """Shades a face of the gut as if it curved away: lit in the middle, darker toward its sides and its underside."""
    for name in SIDES:
        f = fs[name]
        c = (f.w - 1) / 2
        for fx in range(f.w):
            d = abs(fx - c) / max(1.0, c)
            for fy in range(f.h):
                v = fy / max(1, f.h - 1)
                mul(f, fx, fy, 1.06 - 0.22 * d * d - 0.12 * max(0.0, v - 0.6) / 0.4)


def navel(fs):
    f = fs["front"]
    cx, cy = f.w // 2, f.h // 2 + 1
    f.px(cx, cy, shade(BELLY.c["dark"], 0.5))
    f.px(cx, cy - 1, shade(BELLY.c["dark"], 0.85))
    f.px(cx - 1, cy, shade(BELLY.c["dark"], 0.9))
    f.px(cx + 1, cy, shade(BELLY.c["dark"], 0.9))


def fold_under(fs):
    """A fat fold: the bottom face and lowest row in deep shadow where it hangs over what is below."""
    fs["bottom"].fill(lambda x, y: shade(BELLY.c["dark"], 0.5))
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.62)


def back_rolls(fs):
    """Rolls of fat across the back, each with a dark crease under it."""
    f = fs["back"]
    for y in range(2, f.h - 1, 3):
        for x in range(1, f.w - 1):
            mul(f, x, y, 0.65)
            mul(f, x, y - 1, 1.1)


def breast(fs):
    """Sagging breasts, lit above, a dark crease below."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.1)
        mul(f, x, f.h - 1, 0.6)
    f.px(f.w // 2, f.h - 2, shade(SKIN.c["dark"], 0.7))
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.45))


def face_detail(fs):
    """Little eyes sunk in fat, glinting yellow, with puffy lids; a sweaty forehead."""
    f = fs["front"]
    dark = SKIN.c["dark"]
    for cx in (1, f.w - 2):
        f.px(cx - 1 if cx < f.w // 2 else cx + 1, 2, shade(dark, 0.7))
        f.px(cx, 1, shade(SKIN.c["base"], 1.1))   # the swollen lid
        f.glow(cx, 2, EYE)
    for x in range(f.w):
        mul(f, x, 0, 1.08)
    for _ in range(3):
        f.px(f.rng.randrange(f.w), 0, shade(SKIN.c["base"], 1.25))     # sweat
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.8)


def jowls_detail(fs):
    """The jowls and the gaping mouth in them: dark inside, a drooling lip, the cheeks puffed and lit on top."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.12)
    for x in range(2, f.w - 2):
        f.px(x, f.h - 2, MOUTH)
        f.px(x, f.h - 1, MOUTH)
    f.px(2, f.h - 2, shade(LIP.c["base"], 0.8))
    f.px(f.w - 3, f.h - 2, shade(LIP.c["base"], 0.8))
    for name in ("left", "right"):
        s = fs[name]
        for y in range(s.h):
            mul(s, s.w // 2, y, 0.9)
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.5))


def chin_detail(fs):
    """The double chin: drool running down it, a crease under it."""
    f = fs["front"]
    for y in range(f.h):
        f.px(f.w // 2, y, DROOL)
    f.px(f.w // 2 - 1, 0, DROOL)
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.45))
    for name in SIDES:
        s = fs[name]
        for x in range(s.w):
            mul(s, x, s.h - 1, 0.7)


def lip_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(LIP.c["base"], 1.15))
    f.px(1, 0, DROOL)
    fs["top"].fill(lambda x, y: MOUTH)


def snout(fs):
    """An upturned snout, its nostrils facing out."""
    f = fs["front"]
    f.fill(lambda x, y: shade(SKIN.c["base"], 1.05))
    f.px(0, f.h - 1, MOUTH)
    f.px(f.w - 1, f.h - 1, MOUTH)


def brow(fs):
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.55))


def greasy(fs):
    """Greasy strands combed over the scalp, the skin showing between them."""
    t = fs["top"]
    for x in range(t.w):
        if x % 2:
            for y in range(t.h):
                t.px(x, y, shade(SKIN.c["base"], 1.05))
            t.px(x, t.rng.randrange(t.h), shade(HAIR.c["light"], 1.2))


def ear(fs):
    for name in ("left", "right"):
        f = fs[name]
        f.px(f.w // 2, f.h // 2, shade(SKIN.c["dark"], 0.6))


def flab(fs):
    """A flabby limb: the underside hanging in shadow, the top lit, a dimpled skin."""
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, 0, 1.08)
            mul(f, x, f.h - 1, 0.72)
        for _ in range(f.w * f.h // 10):
            mul(f, f.rng.randrange(f.w), f.rng.randrange(f.h), 0.9)


def fingers(fs):
    """Fat fingers: the gaps between them, dirty nails at the tips."""
    for name in ("front", "back", "bottom"):
        f = fs[name]
        for x in range(1, f.w, 1):
            if x % 1 == 0 and name != "bottom":
                mul(f, x, 0, 0.9)
        for x in range(f.w):
            if name == "front":
                f.px(x, f.h - 1, hexc("6a6a4a") if x % 2 == 0 else shade(SKIN.c["dark"], 0.7))
    f = fs["front"]
    for x in range(1, f.w, 2):
        for y in range(f.h):
            mul(f, x, y, 0.7)


def toes(fs):
    f = fs["front"]
    for x in range(f.w):
        if x % 2:
            mul(f, x, 0, 0.6)
        f.px(x, f.h - 1, shade(MUD.c["light"], 1.1))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("glutton", texture_width=128)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mir = s == -1
        # stubby legs, fat thighs under the gut, sunk to the shins in mud; only the toes peek out in front
        leg = m.bone(f"{side}_leg", (4 * s, 6, 0))
        leg.cube(f"{side}_thigh", (x(1.5, 5), 3, -2.5), (5, 4, 5), SKIN, mirror=mir, inflate=0.1, detail=both(flab, mud(0.2, 0.5)))
        leg.cube(f"{side}_shin", (x(2, 4), 1, -2), (4, 3, 4), SKIN, mirror=mir, detail=mud(0.6, 0.8))
        leg.cube(f"{side}_mud", (x(1.5, 5), 0, -2.6), (5, 2, 5), MUD, mirror=mir, inflate=0.25)
        leg.cube(f"{side}_foot", (x(1.8, 4), 0, -3.8), (4, 1, 5), SKIN, mirror=mir, detail=mud(0.5))
        leg.cube(f"{side}_toes", (x(1.8, 4), 0, -4.6), (4, 1, 1), SKIN, mirror=mir, detail=toes)

    body = m.bone("body", (0, 6, 0))
    # the gut: a core with a slab on each side so it reads round, the paunch sagging low and forward over the thighs
    body.cube("belly", (-7, 5, -6), (14, 14, 12), BELLY, detail=both(soft, rounded, stretched, mud(0.1, 0.75)))
    body.cube("belly_front", (-6, 5.5, -7), (12, 13, 1), BELLY, detail=both(soft, rounded, stretched, mud(0.08, 0.7)))
    body.cube("belly_bulge", (-4.5, 6, -8), (9, 10, 1), BELLY, detail=both(soft, rounded, stretched, navel, mud(0.08, 0.8)))
    body.cube("paunch", (-3, 6.5, -8.8), (6, 5, 1), BELLY, detail=both(soft, fold_under, mud(0.15, 1.0)))
    body.cube("belly_back", (-6, 6, 6), (12, 12, 1), SKIN, detail=both(soft, rounded, back_rolls, mud(0.1, 0.75)))
    body.cube("rump", (-4.5, 8, 7), (9, 9, 1), SKIN, detail=both(soft, rounded, back_rolls, mud(0.1, 0.75)))
    for side, s in (("right", 1), ("left", -1)):
        mir = s == -1
        body.cube(f"belly_{side}", (side_x(7, 1, s), 5.5, -5), (1, 13, 10), BELLY, mirror=mir,
                  detail=both(soft, rounded, stretched, mud(0.1, 0.75)))
        body.cube(f"flank_{side}", (side_x(8, 1, s), 7, -3.5), (1, 10, 7), BELLY, mirror=mir,
                  detail=both(soft, rounded, stretched, mud(0.1, 0.75)))
        # the gut's vertical edges, rounded off by a block turned a quarter on each corner
        for end, e in (("front", -1), ("back", 1)):
            cx, cz = 6.2 * s, 5.2 * e
            body.cube(f"belly_{side}_{end}", (cx - 1.5, 5.5, cz - 1.5), (3, 12, 3), BELLY if end == "front" else SKIN,
                      mirror=mir, rotation=(0, 45, 0), origin=(cx, 11, cz), detail=both(soft, stretched, mud(0.1, 0.75)))
    body.cube("belly_under", (-6, 4, -5), (12, 1, 10), MUD)
    # chest, sagging breasts and sloping shoulders; a fat roll at the nape
    body.cube("chest", (-6.5, 18.5, -5.5), (13, 5, 11), SKIN, detail=both(ao(bottom=1, dark=0.75), stretched))
    body.cube("right_breast", (0.5, 17.5, -6.6), (5, 3, 2), SKIN, detail=breast)
    body.cube("left_breast", (-5.5, 17.5, -6.6), (5, 3, 2), SKIN, mirror=True, detail=breast)
    body.cube("shoulders", (-7.5, 20, -4), (15, 3, 8), SKIN, detail=both(flab, mud(0.1)))
    body.cube("nape", (-4.5, 22, -1), (9, 2, 5), SKIN, detail=flab)

    head = body.bone("head", (0, 24, -2))
    head.cube("head", (-3.5, 23.5, -6), (7, 5, 6), SKIN, detail=face_detail)
    head.cube("scalp", (-3, 28.5, -5.5), (6, 1, 5), SKIN, detail=greasy)
    head.cube("hair", (-3.5, 26.5, -1), (7, 2, 2), HAIR, inflate=0.1)
    head.cube("brow", (-3, 26.6, -6.5), (6, 1, 1), SKIN, detail=brow)
    head.cube("snout", (-1, 25.2, -7), (2, 1, 1), SKIN, rotation=(15, 0, 0), origin=(0, 25.5, -6.5), detail=snout)
    head.cube("right_ear", (3.4, 24.6, -3.5), (1, 2, 2), SKIN, detail=ear)
    head.cube("left_ear", (-4.4, 24.6, -3.5), (1, 2, 2), SKIN, mirror=True, detail=ear)
    # jowls hanging off the face, the double chin and the slack lower lip
    head.cube("jowls", (-4.5, 22.5, -6.5), (9, 3, 6), SKIN, detail=jowls_detail)
    head.cube("chins", (-3.5, 21, -6.8), (7, 2, 6), BELLY, detail=chin_detail)
    head.cube("lip", (-2, 22.2, -6.9), (4, 1, 1), LIP, detail=lip_detail)
    head.cube("neck_roll", (-4.5, 21.5, -5.5), (9, 2, 7), SKIN, detail=fold_under)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mir = s == -1
        arm = body.bone(f"{side}_arm", (9 * s, 20, -1))
        arm.cube(f"{side}_shoulder", (x(7.5, 5), 16.5, -3.5), (5, 5, 5), SKIN, mirror=mir, inflate=0.1, detail=flab)
        arm.cube(f"{side}_upper_arm", (x(8, 4), 13.5, -3), (4, 4, 4), SKIN, mirror=mir, detail=flab)
        arm.cube(f"{side}_flab", (x(8.5, 3), 13, -0.5), (3, 3, 1), SKIN, mirror=mir, detail=flab)
        fore = arm.bone(f"{side}_forearm", (10 * s, 14, -1), rotation=(12, 0, 0))
        fore.cube(f"{side}_forearm", (x(8.4, 3), 11, -2.5), (3, 4, 3), SKIN, mirror=mir, inflate=0.1, detail=flab)
        fore.cube(f"{side}_hand", (x(8.3, 3), 9.2, -2.6), (3, 2, 3), SKIN, mirror=mir, detail=flab)
        fore.cube(f"{side}_fingers", (x(8.3, 3), 7.8, -2.3), (3, 2, 2), SKIN, mirror=mir, rotation=(10, 0, 0),
                  origin=(9.8 * s, 9.2, -1.3), detail=fingers)
        fore.cube(f"{side}_thumb", (x(8, 1) if s == 1 else x(8, 1), 9, -3.1), (1, 2, 1), SKIN, mirror=mir)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    idle = m.animation("idle", 2.0, loop=True)
    idle.pos("body", [(0, ZERO), (1, (0, -0.6, 0)), (2, ZERO)])
    idle.rot("head", [(0, ZERO), (1, (-8, 0, 0)), (2, ZERO)])
    idle.rot("right_arm", [(0, (0, 0, 10)), (1, (0, 0, 16)), (2, (0, 0, 10))])
    idle.rot("left_arm", [(0, (0, 0, -10)), (1, (0, 0, -16)), (2, (0, 0, -10))])
    idle.rot("right_forearm", [(0, ZERO), (1, (8, 0, 0)), (2, ZERO)])
    idle.rot("left_forearm", [(0, ZERO), (1, (8, 0, 0)), (2, ZERO)])

    walk = m.animation("walk", 1.4, loop=True)
    walk.rot("body", [(0, (0, 0, 6)), (0.7, (0, 0, -6)), (1.4, (0, 0, 6))])
    walk.rot("right_leg", [(0, (25, 0, 0)), (0.7, (-25, 0, 0)), (1.4, (25, 0, 0))])
    walk.rot("left_leg", [(0, (-25, 0, 0)), (0.7, (25, 0, 0)), (1.4, (-25, 0, 0))])

    # it throws its whole belly forward, arms flung out after it
    attack = m.animation("attack", 1.0)
    attack.rot("body", [(0, ZERO), (0.35, (18, 0, 0)), (0.5, (-25, 0, 0)), (0.75, (-20, 0, 0)), (1.0, ZERO)])
    attack.pos("body", [(0, ZERO), (0.35, (0, 0, 2)), (0.5, (0, 0, -4)), (1.0, ZERO)])
    attack.rot("right_arm", [(0, ZERO), (0.35, (60, 0, 20)), (0.5, (80, 0, 10)), (1.0, ZERO)])
    attack.rot("left_arm", [(0, ZERO), (0.35, (60, 0, -20)), (0.5, (80, 0, -10)), (1.0, ZERO)])
    attack.rot("right_forearm", [(0, ZERO), (0.35, (30, 0, 0)), (0.5, (-10, 0, 0)), (1.0, ZERO)])
    attack.rot("left_forearm", [(0, ZERO), (0.35, (30, 0, 0)), (0.5, (-10, 0, 0)), (1.0, ZERO)])
    attack.event(0.5, "hit")

    # heaving itself up out of the mud, clawing at the air
    emerge = m.animation("emerge", 2.0)
    emerge.pos("body", [(0, (0, -22, 0)), (0.3, (0, -22, 0)), (1.6, (0, 1, 0)), (2.0, ZERO)])
    emerge.pos("right_leg", [(0, (0, -22, 0)), (0.3, (0, -22, 0)), (1.6, ZERO)])
    emerge.pos("left_leg", [(0, (0, -22, 0)), (0.3, (0, -22, 0)), (1.6, ZERO)])
    emerge.rot("head", [(0, (-30, 0, 0)), (1.6, (15, 0, 0)), (2.0, ZERO)])
    emerge.rot("right_arm", [(0, (150, 0, 20)), (1.0, (120, 0, 30)), (2.0, ZERO)])
    emerge.rot("left_arm", [(0, (150, 0, -20)), (1.0, (120, 0, -30)), (2.0, ZERO)])
    emerge.rot("right_forearm", [(0, (20, 0, 0)), (0.6, (40, 0, 0)), (1.0, (10, 0, 0)), (2.0, ZERO)])
    emerge.rot("left_forearm", [(0, (30, 0, 0)), (0.7, (10, 0, 0)), (1.1, (40, 0, 0)), (2.0, ZERO)])
    emerge.event(0.3, "rise")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (8, 0, 0)), (0.3, ZERO)])

    # it slumps and splits
    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.6, (30, 0, 15)), (1.2, (40, 0, 20))])
    death.pos("body", [(0, ZERO), (0.6, (0, -3, 0)), (1.2, (0, -6, 0))])
    death.rot("head", [(0, ZERO), (0.6, (12, 0, 0)), (1.2, (20, 0, -10))])
    death.rot("right_forearm", [(0, ZERO), (1.2, (-12, 0, 0))])
    death.rot("left_forearm", [(0, ZERO), (1.2, (-12, 0, 0))])
    death.event(0.6, "burst")


if __name__ == "__main__":
    build().write()
