#!/usr/bin/env python3
"""The lovers (Lust's monster): two souls blown together forever on the hurricane, "those two who go together, and seem
to be so light upon the wind" (Inferno V). Pale violet shapes trailing into wisps, turning round each other in the
air; they dive on pilgrims and throw them about with the wind. Drawn see-through and softly lit.

Each soul is a slender figure in a thin gown: a head with a brow, a nose and sorrowing eyes (his hair short and
curled, hers long and streaming back on the wind), a neck, shoulders over a narrow waist, hips where the gown flares,
and below them no legs, only a wisp that tapers and trails behind (a bone of its own, so it streams and ripples).
They hold each other (his arm round her waist, her hand on his shoulder, their heads leaning together) and each
reaches forward with the other arm, the way they dive.

Run from the repo root:  python3 tools/art/lovers.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SOUL_A = material("soul_a", "robe", base="c8a8e8", fold="8a6aaa")
SOUL_B = material("soul_b", "robe", base="e8a8c8", fold="aa6a8a")
SKIN_A = material("skin_a", "skin", base="d4c0ec", dark="9a82b8")
SKIN_B = material("skin_b", "skin", base="f0c6dc", dark="b88aa4")
HAIR_A = material("hair_a", "hair", light="6a4a8a", dark="3a2a5a")
HAIR_B = material("hair_b", "hair", light="8a4a6a", dark="5a2a3a")
EYE = hexc("ffffff")
WISP = hexc("e8d8ff")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def mul(face, fx, fy, f):
    """Darkens (f < 1) or lightens one pixel of a face that is already painted."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def both(*details):
    def detail(fs):
        for d in details:
            d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def face_of(skin):
    """A sorrowing face: shadowed sockets under the brow with pale glowing eyes, a nose, a small dark mouth, the
    cheeks lit, and a shadow under the jaw."""
    def detail(fs):
        f = fs["front"]
        dark = skin.c["dark"]
        for cx in (1, f.w - 2):
            f.px(cx, 1, shade(dark, 0.85))
            f.glow(cx, 2, EYE)
            f.px(cx, 3, shade(skin.c["base"], 1.08))
        f.px(f.w // 2, 2, shade(skin.c["base"], 1.05))
        f.px(f.w // 2, f.h - 1, shade(dark, 0.6))
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.9)
        fs["bottom"].fill(lambda x, y: shade(dark, 0.7))
    return detail


def nose(fs):
    fs["front"].px(0, 0, shade(fs["front"].canvas.get(fs["front"].x, fs["front"].y), 1.1))
    fs["bottom"].fill(lambda x, y: hexc("6a5a7a"))


def neck(fs):
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, 0, 0.75)


def gown(fs):
    """The thin gown's folds: soft vertical creases, a lit neckline, and the cloth gathered darker at the waist."""
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            if (x + (2 if name in ("left", "right") else 0)) % 3 == 1:
                for y in range(f.h):
                    mul(f, x, y, 0.88)
        for x in range(f.w):
            mul(f, x, 0, 1.08)


def bodice(fs):
    """The bodice: a curved neckline showing the skin at the collarbone."""
    f = fs["front"]
    for x in range(1, f.w - 1):
        mul(f, x, 0, 1.15)
    f.px(f.w // 2, 1, shade(f.canvas.get(f.x + f.w // 2, f.y + 1), 0.8))


def sash(fs):
    """A sash knotted at the waist, one end hanging."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.12)
        mul(f, x, f.h - 1, 0.7)
    f.px(f.w // 2, 0, shade(f.canvas.get(f.x + f.w // 2, f.y), 0.75))


def wisp(fade_from=0.0):
    """The gown dissolving into the wind: the lower part glows pale, brightest at the very tip, with streaks."""
    def detail(fs):
        for name in ("front", "back", "left", "right", "bottom"):
            f = fs[name]
            for y in range(f.h):
                t = y / max(1, f.h - 1) if name != "bottom" else 1.0
                t = fade_from + (1 - fade_from) * t
                for x in range(f.w):
                    if t > 0.55 or (t > 0.25 and (x + y) % 3 == 0):
                        c = f.canvas.get(f.x + x, f.y + y)
                        f.glow(x, y, mix(tuple(c[:3]), WISP, min(1.0, t)))
    return detail


def hand(fs):
    for name in ("front", "bottom"):
        f = fs[name]
        for x in range(1, f.w, 2):
            for y in range(f.h):
                mul(f, x, y, 0.85)


def curls(fs):
    """Short curls: little dark rings among the tufts."""
    for name in SIDES + ("top",):
        f = fs[name]
        for _ in range(f.w * f.h // 6 + 1):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            mul(f, x, y, 0.7)
            mul(f, x + 1, y, 1.2)


def strands(fs):
    """Long hair: strands running down its length, lighter along the top where the light catches."""
    for name in SIDES + ("top", "bottom"):
        f = fs[name]
        for x in range(f.w):
            if x % 2:
                for y in range(f.h):
                    mul(f, x, y, 0.84)
            mul(f, x, 0, 1.15)


# ----------------------------------------------------------------------------------------------- the model

def soul(parent, name, x0, mat, skin, hair, long_hair):
    """One of the pair, centred on x0; o is the side away from the other soul (+1 on the right, -1 on the left)."""
    o = 1 if x0 > 0 else -1
    s = parent.bone(name, (x0, 16, 0))
    mir = o == -1
    # the torso: shoulders and chest, a narrow waist, hips where the gown flares
    s.cube(f"{name}_chest", (x0 - 2.5, 14, -1.8), (5, 4, 4), mat, mirror=mir, detail=both(gown, bodice))
    s.cube(f"{name}_shoulders", (x0 - 3, 16.6, -1.5), (6, 1, 3), mat, mirror=mir)
    s.cube(f"{name}_waist", (x0 - 2, 11, -1.4), (4, 3, 3), mat, mirror=mir, detail=gown)
    s.cube(f"{name}_sash", (x0 - 2, 11.5, -1.6), (4, 1, 3), hair, inflate=0.1, mirror=mir, detail=sash)
    s.cube(f"{name}_hips", (x0 - 2.5, 8, -2), (5, 3, 4), mat, mirror=mir, detail=gown)
    s.cube(f"{name}_neck", (x0 - 1, 17.6, -1), (2, 1, 2), skin, mirror=mir, detail=neck)

    # no legs: below the hips the gown thins into a wisp that streams back on the wind
    tail = s.bone(f"{name}_tail", (x0, 8.5, 0), rotation=(-18, 0, 0))
    tail.cube(f"{name}_tail", (x0 - 2, 5, -1.5), (4, 4, 3), mat, mirror=mir, detail=both(gown, wisp(0.0)))
    tail.cube(f"{name}_wisp", (x0 - 1.5, 2.5, -0.8), (3, 3, 2), mat, mirror=mir, detail=wisp(0.4))
    tail.cube(f"{name}_tip", (x0 - 1 + 0.5 * o, 0.5, -0.2), (1, 2, 1), mat, mirror=mir, detail=wisp(0.8))
    tail.cube(f"{name}_streamer", (x0 - 0.5 - 1.5 * o, 2, 0.3), (1, 3, 1), mat, mirror=mir, detail=wisp(0.6))

    # the head, leaning toward the other's
    head = s.bone(f"{name}_head", (x0, 18.5, 0), rotation=(0, 10 * o, 4 * o))
    head.cube(f"{name}_head", (x0 - 2.5, 18.5, -2.5), (5, 5, 5), skin, mirror=mir, detail=face_of(skin))
    head.cube(f"{name}_nose", (x0 - 0.5, 20, -3.2), (1, 1, 1), skin, detail=nose)
    head.cube(f"{name}_brow", (x0 - 2.5, 21.4, -2.8), (5, 1, 1), skin, mirror=mir)
    head.cube(f"{name}_hair", (x0 - 2.5, 22.2, -2.6), (5, 2, 5), hair, inflate=0.25, mirror=mir,
              detail=strands if long_hair else curls)
    head.cube(f"{name}_hair_back", (x0 - 2.5, 19, 1.6), (5, 4, 1), hair, inflate=0.25, mirror=mir,
              detail=strands if long_hair else curls)
    if long_hair:
        # her hair falls past her shoulders and streams out behind on the wind
        head.cube(f"{name}_locks", (x0 - 2.5, 16.5, 1.4), (5, 4, 2), hair, mirror=mir, rotation=(-20, 0, 0),
                  origin=(x0, 20, 2), detail=strands)
        head.cube(f"{name}_stream", (x0 - 2, 15.5, 3.2), (4, 2, 4), hair, mirror=mir, rotation=(-35, 0, 0),
                  origin=(x0, 18, 3), detail=strands)
        head.cube(f"{name}_side_lock", (x0 + 2.5 * o - (1 if o == 1 else 0) + (0.2 * o), 18.5, -2.2), (1, 4, 2), hair,
                  mirror=mir, detail=strands)
    else:
        head.cube(f"{name}_fringe", (x0 - 2.5, 21.6, -2.9), (5, 1, 1), hair, inflate=0.15, mirror=mir, detail=curls)
        head.cube(f"{name}_ear", (x0 + 2.5 * o - (1 if o == 1 else 0) + 0.3 * o, 19.8, -0.5), (1, 2, 1), skin, mirror=mir)

    # the outer arm reaches forward (bent at the elbow), the hand open
    reach = s.bone(f"{name}_reach", (x0 + 2.8 * o, 16.6, 0), rotation=(70, 0, -8 * o))
    ax = x0 + 2.8 * o - 1
    reach.cube(f"{name}_upper_arm", (ax, 12.8, -1), (2, 4, 2), mat, inflate=0.05, mirror=mir, detail=gown)
    fore = reach.bone(f"{name}_forearm", (x0 + 2.8 * o, 13, 0), rotation=(20, 0, 0))
    fore.cube(f"{name}_forearm", (ax + 0.1, 9.5, -0.9), (2, 4, 2), skin, inflate=-0.1, mirror=mir)
    fore.cube(f"{name}_hand", (ax + 0.1, 7.8, -0.7), (2, 2, 1), skin, mirror=mir, detail=hand)
    fore.cube(f"{name}_thumb", (ax + 0.1 + (-0.6 if o == 1 else 1.6), 8.6, -1.3), (1, 1, 1), skin, mirror=mir)
    # the inner arm holds the other: his round her waist, hers over his shoulder
    if long_hair:
        s.cube(f"{name}_embrace", (x0 + 1.5, 16.2, -1.2), (6, 1, 2), mat, inflate=0.05)
        s.cube(f"{name}_embrace_hand", (x0 + 6.6, 16.6, -1.6), (2, 1, 2), skin, detail=hand)
    else:
        s.cube(f"{name}_embrace", (x0 - 7.2, 12, 0.6), (6, 1, 2), mat, inflate=0.05)
        s.cube(f"{name}_embrace_hand", (x0 - 7.8, 11.6, -1.6), (1, 2, 3), skin, detail=hand)
    return s


def build():
    m = Model("lovers", texture_width=64)
    pair = m.bone("pair", (0, 14, 0))
    soul(pair, "soul_a", 3, SOUL_A, SKIN_A, HAIR_A, False)
    soul(pair, "soul_b", -3, SOUL_B, SKIN_B, HAIR_B, True)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # turning round each other on the wind, rising and falling, their wisps rippling behind them
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("pair", [(0, (0, 0, 0)), (0.5, (0, 90, 0)), (1.0, (0, 180, 0)), (1.5, (0, 270, 0)), (2.0, (0, 360, 0))])
    idle.pos("pair", [(0, ZERO), (1.0, (0, 2, 0)), (2.0, ZERO)])
    idle.rot("soul_a", [(0, (0, 0, 8)), (1.0, (0, 0, 14)), (2.0, (0, 0, 8))])
    idle.rot("soul_b", [(0, (0, 0, -8)), (1.0, (0, 0, -14)), (2.0, (0, 0, -8))])
    idle.rot("soul_a_tail", [(0, ZERO), (0.5, (-12, 0, 6)), (1.0, ZERO), (1.5, (-12, 0, -6)), (2.0, ZERO)])
    idle.rot("soul_b_tail", [(0, (-12, 0, -6)), (0.5, ZERO), (1.0, (-12, 0, 6)), (1.5, ZERO), (2.0, (-12, 0, -6))])

    # a dive: both lean in together and sweep through, the wisps streaming back
    attack = m.animation("attack", 0.8)
    attack.rot("pair", [(0, ZERO), (0.3, (20, 0, 0)), (0.45, (-50, 0, 0)), (0.8, ZERO)])
    attack.pos("pair", [(0, ZERO), (0.3, (0, 2, 2)), (0.45, (0, -2, -6)), (0.8, ZERO)])
    attack.rot("soul_a_tail", [(0, ZERO), (0.3, (10, 0, 0)), (0.45, (-30, 0, 0)), (0.8, ZERO)])
    attack.rot("soul_b_tail", [(0, ZERO), (0.3, (10, 0, 0)), (0.45, (-30, 0, 0)), (0.8, ZERO)])
    attack.rot("soul_a_reach", [(0, ZERO), (0.3, (-20, 0, 0)), (0.45, (20, 0, 0)), (0.8, ZERO)])
    attack.rot("soul_b_reach", [(0, ZERO), (0.3, (-20, 0, 0)), (0.45, (20, 0, 0)), (0.8, ZERO)])
    attack.event(0.45, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("soul_a", [(0, ZERO), (0.1, (0, 0, 30)), (0.3, ZERO)])
    hurt.rot("soul_b", [(0, ZERO), (0.1, (0, 0, -30)), (0.3, ZERO)])

    # torn apart, they drift away from each other, still reaching back
    death = m.animation("death", 1.4)
    death.pos("soul_a", [(0, ZERO), (1.4, (8, 6, 0))])
    death.pos("soul_b", [(0, ZERO), (1.4, (-8, 6, 0))])
    death.rot("soul_a", [(0, ZERO), (1.4, (0, 0, 60))])
    death.rot("soul_b", [(0, ZERO), (1.4, (0, 0, -60))])
    death.rot("soul_a_tail", [(0, ZERO), (1.4, (-30, 0, 0))])
    death.rot("soul_b_tail", [(0, ZERO), (1.4, (-30, 0, 0))])


if __name__ == "__main__":
    build().write()
