#!/usr/bin/env python3
"""Medusa (Inferno IX), called by the Furies: "Venga Medusa: sì 'l farem di smalto" (let Medusa come, and we'll make
him stone). A colossal head and shoulders risen over the battlements of Dis, grey as the stone she makes, a mane of
writhing serpents round a face whose eyes open in a terrible light. Look at her while her eyes are open and you turn
to stone; Virgil's advice to Dante was to turn round and cover his eyes.

Run from the repo root:  python3 tools/art/medusa.py && python3 tools/gen_bosses.py

She is a bust come alive on its plinth: a moulded base, the chest and shoulders of a woman in a carved chiton (its
folds falling from a brooch on her left shoulder), a long neck, and a face that was beautiful: high cheekbones, a
straight nose, full lips, a strong chin, closed lids under a heavy brow. Her hair is a mass of coiled serpents with
sixteen more rising out of it, each a chain of tapering segments (root, middle and tip are their own bones, so they
writhe along their length) ending in a head with a pale mouth and fangs.

Her open eyes are a separate glowing part ("eyes", one eye in each socket) that is only there (scaled up from
nothing) while she gazes, so the gaze is plain to see. Only the gaze keys them: animations add up in the game, so
the renderer hides the part outright whenever the gaze isn't playing (AnimatedBossModel.onlyDuring). The gaze comes
from 18 pixels up the model (MedusaEntity.eyeY): the eyes sit there. The events time her (FuriesFight): "open" and
"close" bound her gaze; "rise" her coming.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, mix, shade, hexc  # noqa: E402

STONE = material("stone", "skin", base="8e8e86", dark="5c5c56")
LIPS = material("lips", "skin", base="76716c", dark="54504c")
PLINTH = material("plinth", "skin", base="6c6a64", dark="46443f")
SERPENT = material("serpent", "scales", base="3a5a3a", edge="6a8a4a", belly="9aa070")
COILS = material("coils", "scales", base="2e4a30", edge="5a7a42", belly="8a9060")
ROBE = material("robe", "robe", base="6a6862", fold="48463f")
EYE = hexc("e8fff0")
EYE_EDGE = hexc("8affc0")
LID = hexc("4a4a46")
LASH = hexc("2e2e2c")
FANG = hexc("e8e4d0")
MOUTH = hexc("3a1e22")
SNAKE_EYE = hexc("c8b030")


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
    """A cube t wide (and depth deep) that stands on point a and reaches to point b: a fold, a brow, a segment of a
    serpent. Its length is rounded to whole pixels (box UV); it turns about a."""
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

def upper_face(faces):
    """Her eyes' sockets and the shut lids in them: a dark crease under the brow, the curve of each lid, its lashes."""
    f = faces["front"]
    shadow = shade(STONE.c["dark"], 0.8)
    for x in range(f.w):
        f.px(x, 0, shadow)  # the brow's shadow
    for cx in (1, f.w - 3):
        for dx in range(3):
            f.px(cx + dx - (0 if cx == 1 else 0), 1, LID)
            f.px(cx + dx, 2, LASH if dx != 1 else shade(LASH, 1.3))
        f.px(cx + 1, 1, shade(LID, 1.15))
    mid = f.w // 2
    for y in range(0, f.h):
        f.px(mid, y, shade(STONE.c["base"], 1.06))  # the light on the bridge of her nose
    f.px(0, f.h - 1, shade(STONE.c["dark"], 0.9))
    f.px(f.w - 1, f.h - 1, shade(STONE.c["dark"], 0.9))


def forehead(faces):
    f = faces["front"]
    for x in range(f.w):
        f.px(x, f.h - 1, shade(STONE.c["base"], 1.08))
    # a faint crack running down from her hairline: she is stone
    for y, x in ((0, 2), (1, 2), (1, 3), (2, 3)):
        f.px(x, y, shade(STONE.c["dark"], 0.85))


def lips_detail(faces):
    f = faces["front"]
    f.px(f.w // 2, 0, shade(LIPS.c["dark"], 0.9))  # the cupid's bow
    faces["bottom"].fill(lambda x, y: MOUTH)  # the line of her shut mouth, under the upper lip


def lower_lip(faces):
    f = faces["front"]
    f.px(f.w // 2, 0, shade(LIPS.c["base"], 1.15))
    faces["top"].fill(lambda x, y: MOUTH)


def nostrils(faces):
    b = faces["bottom"]
    b.fill(lambda x, y: shade(STONE.c["dark"], 0.7))
    if b.w >= 3:
        b.px(0, 0, hexc("2a2a28"))
        b.px(b.w - 1, 0, hexc("2a2a28"))


def under_jaw(faces):
    faces["bottom"].fill(lambda x, y: shade(STONE.c["dark"], 0.75))


def open_eyes(faces):
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EYE if (x + y) % 2 == 0 else EYE_EDGE)


def pectorals(faces):
    """Her chest above the chiton: the line of the collarbones and the hollow at the throat."""
    f = faces["front"]
    mid = f.w // 2
    f.px(mid, 0, shade(STONE.c["dark"], 0.8))
    f.px(mid - 1, 0, shade(STONE.c["dark"], 0.9))
    for x in range(f.w):
        f.px(x, f.h - 1, shade(STONE.c["dark"], 0.85))


def carved_folds(faces):
    """Drapery carved in stone: deep vertical folds, cut a little deeper toward the hem, chips on the edges."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            if x % 3 == 0:
                for y in range(f.h):
                    f.px(x, y, shade(ROBE.c["fold"], 0.85 if y > f.h // 2 else 1.0))
            elif x % 3 == 1:
                for y in range(f.h):
                    f.px(x, y, shade(ROBE.c["base"], 1.12))
        for _ in range(f.w // 4):
            f.px(f.rng.randrange(f.w), 0, shade(ROBE.c["fold"], 0.7))


def moulding(faces):
    """The plinth's moulding: a lit fillet over a shadowed cove, worn and chipped."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.px(x, 0, shade(PLINTH.c["base"], 1.2))
            if f.h > 2:
                f.px(x, f.h - 1, shade(PLINTH.c["dark"], 0.8))
        for _ in range(max(1, f.w // 6)):
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), shade(PLINTH.c["dark"], 0.7))


def plinth_detail(faces):
    """The base: a band of carved serpents' scales across its front, the stone cracked and stained."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.px(x, 0, shade(PLINTH.c["base"], 1.15))
        for x in range(0, f.w, 2):
            f.px(x, 1, shade(PLINTH.c["dark"], 0.85))
        cx = f.rng.randrange(f.w)
        for y in range(f.h):
            f.px(cx + (y % 2), y, shade(PLINTH.c["dark"], 0.7))  # a crack


def brooch(faces):
    f = faces["front"]
    f.rect(0, 0, f.w, f.h, shade(PLINTH.c["dark"], 0.9))
    f.px(f.w // 2, f.h // 2, SERPENT.c["edge"])


def snake_head(faces):
    """A serpent's head (its snout is the stick's end): a dark mouth across the snout with two pale fangs, yellow eyes
    near the snout on each side, a lighter scale on the crown."""
    t = faces["top"]
    mid = t.h // 2
    for x in range(t.w):
        t.px(x, mid, MOUTH)
    t.px(0, min(t.h - 1, mid + 1), FANG)
    t.px(t.w - 1, min(t.h - 1, mid + 1), FANG)
    for name in ("left", "right"):
        s = faces[name]
        s.px(s.w // 2, 0, SNAKE_EYE)
    f = faces["front"]
    f.px(f.w // 2, 0, shade(SERPENT.c["edge"], 1.2))
    faces["back"].fill(lambda x, y: shade(SERPENT.c["belly"], 0.9) if y < 2 else None)


# ----------------------------------------------------------------------------------------------- the serpents

# her serpents: (root, first heading, curl in degrees per segment, segments). The first eight keep the names the
# animations address (snake0 .. snake7); the rest rise among them.
SNAKES = [
    ((-4.2, 21.0, -2.2), (-1.0, 0.5, -0.3), 30, 5),
    ((4.2, 21.0, -2.2), (1.0, 0.5, -0.3), -30, 5),
    ((-4.0, 19.0, 3.0), (-1.0, 0.2, 0.4), 34, 5),
    ((4.0, 19.0, 3.0), (1.0, 0.2, 0.4), -34, 5),
    ((0.0, 23.5, 3.5), (0.0, 0.8, 0.6), 28, 5),
    ((-2.4, 24.0, -2.4), (-0.4, 1.0, -0.3), -26, 5),
    ((2.4, 24.0, -2.4), (0.4, 1.0, -0.3), 26, 5),
    ((0.0, 24.6, 0.6), (0.0, 1.0, 0.1), 22, 5),
    ((-4.6, 16.5, 0.8), (-1.0, -0.5, 0.2), -38, 5),
    ((4.6, 16.5, 0.8), (1.0, -0.5, 0.2), 38, 5),
    ((-2.6, 24.0, 2.4), (-0.6, 0.9, 0.5), 32, 4),
    ((2.6, 24.0, 2.4), (0.6, 0.9, 0.5), -32, 4),
    ((-4.4, 22.6, 0.8), (-1.0, 0.8, 0.0), -24, 4),
    ((4.4, 22.6, 0.8), (1.0, 0.8, 0.0), 24, 4),
    ((-2.0, 18.0, 5.4), (-0.5, -0.8, 0.5), 36, 4),
    ((2.0, 18.0, 5.4), (0.5, -0.8, 0.5), -36, 4),
]


def serpent(head, i):
    """One serpent: a chain of tapering segments, each turned further than the last so it curls (and back again, an
    S), split over three bones so it writhes along its length; a head at the end, a little wider than the neck."""
    root, heading, curl, segs = SNAKES[i]
    d = norm(heading)
    # it curls in the upright plane it grows in: up and over, then back the other way
    axis = cross(d, (0, 1, 0))
    axis = norm(axis) if math.sqrt(sum(v * v for v in axis)) > 0.3 else (1, 0, 0)
    curl = abs(curl)
    p = root
    bone = head.bone(f"snake{i}", root)
    widths = [(3, -0.5), (2, 0.0), (2, -0.2), (2, -0.4), (1, 0.15)]
    big = i < 8
    for k in range(segs):
        if k == 2:
            bone = bone.bone(f"snake{i}_mid", p)
        if k == segs - 1:
            bone = bone.bone(f"snake{i}_tip", p)
        if k == segs - 1:
            # the head rears: it turns a little back toward the front, jaw slightly open
            hd = norm(add(d, (0, 0.3, -0.25)))
            stick(bone, f"snake{i}_head", p, add(p, mul(hd, 3 if big else 2)), 2, SERPENT, depth=2,
                  inflate=-0.15 if big else -0.35, detail=snake_head)
        else:
            t, inf = widths[min(k, len(widths) - 1)]
            if not big:
                inf -= 0.25
            q = add(p, mul(d, 2.3 if big else 1.9))
            stick(bone, f"snake{i}_{k}", p, q, t, SERPENT, inflate=inf)
            p = q
        d = norm(turn(d, axis, curl * (1.3 if k < 2 else -1.1)))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("medusa", texture_width=256)
    bust = m.bone("bust", (0, 0, 0))

    # the plinth: a moulded base, a cove, a narrower socle under the bust
    bust.cube("plinth", (-10, 0, -6), (20, 2, 12), PLINTH, detail=plinth_detail)
    bust.cube("plinth_moulding", (-9.5, 2, -5.5), (19, 1, 11), PLINTH, inflate=0.1, detail=moulding)

    # the chest in a carved chiton: the drapery round her, a heavy fold falling from the brooch on her left shoulder
    bust.cube("chiton", (-8, 3, -4.5), (16, 5, 9), ROBE, detail=carved_folds)
    bust.cube("chest", (-6.5, 7.5, -4), (13, 3, 8), STONE, detail=pectorals)
    bust.cube("chiton_back", (-8, 7.5, 1.5), (16, 3, 3), ROBE, inflate=0.1, detail=carved_folds)
    stick(bust, "chiton_sash", (-6.5, 10.2, -4.2), (5.0, 3.4, -5.6), 2, ROBE, depth=1, inflate=0.1, detail=carved_folds)
    stick(bust, "chiton_sash_back", (-6.5, 10.6, 3.6), (5.5, 3.4, 4.6), 3, ROBE, depth=1, inflate=0.1, detail=carved_folds)
    bust.cube("brooch", (-7.4, 9.4, -4.9), (2, 2, 1), PLINTH, detail=brooch)
    for k, x0 in enumerate((-5.5, -2.5, 0.5, 3.5, 6.0)):
        # the folds fall from the neckline, fanning a little toward the hem
        stick(bust, f"fold{k}", (x0, 7.8, -4.6), (x0 * 1.12, 3.2, -5.0), 1, ROBE, inflate=-0.05 if k % 2 else -0.2)
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        # rounded shoulders, cut off above the arm as a bust is
        bust.cube(f"{side}_shoulder", (x(6.3, 3), 4.5, -3), (3, 5, 6), STONE, inflate=0.3)
        bust.cube(f"{side}_breast", (x(0.7, 4), 4.8, -5.2), (4, 3, 1), ROBE, inflate=0.25, detail=carved_folds)
        bust.cube(f"{side}_arm_stump", (x(7.2, 2), 3.2, -2.5), (2, 3, 5), STONE, inflate=0.2)
        stick(bust, f"{side}_trapezius", (2.0 * s, 13.0, 0.6), (7.8 * s, 9.4, 0.2), 3, STONE, depth=5, inflate=-0.2)
        stick(bust, f"{side}_collarbone", (0.9 * s, 10.5, -4.1), (6.4 * s, 10.0, -3.1), 1, STONE, inflate=0.05)
        # the long muscle of her neck, from behind the ear to the throat
        stick(bust, f"{side}_neck_muscle", (2.3 * s, 15.0, 0.4), (0.8 * s, 10.6, -2.8), 1, STONE, depth=2, inflate=0.05)
    bust.cube("neck", (-2.5, 10, -2.4), (5, 5, 5), STONE)

    head = bust.bone("head", (0, 14, 0))
    head.cube("cranium", (-4, 15.5, -3.8), (8, 8, 9), STONE, inflate=-0.05)
    head.cube("upper_face", (-3.5, 15, -4.6), (7, 4, 1), STONE, detail=upper_face)
    head.cube("forehead", (-3.5, 19, -4.4), (7, 3, 1), STONE, detail=forehead)
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        stick(head, f"{side}_brow", (0.5 * s, 19.3, -4.9), (3.5 * s, 18.9, -4.75), 1, STONE, inflate=0.02)
        head.cube(f"{side}_cheekbone", (x(1.8, 2), 15.6, -5.0), (2, 1, 1), STONE, inflate=0.08)
        stick(head, f"{side}_jaw", (3.2 * s, 16.0, 1.0), (1.8 * s, 12.6, -3.3), 1, STONE, depth=2, inflate=0.05)
    stick(head, "nose", (0, 19.0, -4.7), (0, 15.8, -5.9), 2, STONE, inflate=-0.3)
    head.cube("nostrils", (-1.5, 15.0, -6.0), (3, 1, 2), STONE, inflate=-0.2, detail=nostrils)
    head.cube("upper_lip", (-1.5, 14.0, -5.1), (3, 1, 1), LIPS, inflate=0.02, detail=lips_detail)
    head.cube("lower_lip", (-1.5, 13.15, -5.0), (3, 1, 1), LIPS, inflate=-0.08, detail=lower_lip)
    head.cube("lower_face", (-3, 12.5, -4.4), (6, 3, 7), STONE, detail=under_jaw)
    head.cube("chin", (-1.5, 11.9, -4.8), (3, 2, 1), STONE, inflate=0.05, detail=under_jaw)
    # the coiled serpents her hair grows from: a cap over the skull, locks over the ears, a mass at the back
    head.cube("coils_cap", (-4.5, 21.5, -3.4), (9, 3, 9), COILS, inflate=0.25)
    head.cube("coils_back", (-4, 14.5, 4.4), (8, 8, 2), COILS, inflate=0.2)
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        head.cube(f"{side}_coils", (x(3.7, 2), 15.5, -1.5), (2, 7, 6), COILS, inflate=0.15)
    eyes = head.bone("eyes", (0, 18, -5.5))
    for side, s in (("right", 1), ("left", -1)):
        x = side_x(s)
        eyes.cube(f"{side}_eye", (x(0.8, 2), 17.3, -5.15), (2, 1, 1), STONE, inflate=0.05, detail=open_eyes)
    for i in range(len(SNAKES)):
        serpent(head, i)
    animations(m)
    return m


ZERO = (0, 0, 0)
HIDDEN = (0.01, 0.01, 0.01)
SHOWN = (1, 1, 1)


def writhe(a, length, amount=20):
    for i in range(len(SNAKES)):
        s = 1 if i % 2 else -1
        a.rot(f"snake{i}", [(0, ZERO), (length * 0.25, (amount * s, 0, amount * s)), (length * 0.75, (-amount * s, 0, -amount * s)), (length, ZERO)])
        # the wave travels out along each serpent
        a.rot(f"snake{i}_mid", [(0, (0, 0, -amount * s)), (length * 0.5, (0, 0, amount * s)), (length, (0, 0, -amount * s))])
        a.rot(f"snake{i}_tip", [(0, (amount * s, 0, 0)), (length * 0.5, (-amount * s, 0, 0)), (length, (amount * s, 0, 0))])


def animations(m):
    # eyes shut, the serpents writhing
    idle = m.animation("idle", 2.0, loop=True)
    writhe(idle, 2.0)
    idle.rot("head", [(0, ZERO), (1.0, (0, 6, 0)), (2.0, ZERO)])

    # the gaze: her head lifts, her eyes open in a cold light, and hold
    gaze = m.animation("gaze", 4.0)
    writhe(gaze, 1.0, 35)
    gaze.scale("eyes", [(0, HIDDEN), (0.75, HIDDEN), (0.8, SHOWN), (3.3, SHOWN), (3.35, HIDDEN), (4.0, HIDDEN)], interp="linear")
    gaze.rot("head", [(0, ZERO), (0.75, (-15, 0, 0)), (3.3, (-15, 0, 0)), (4.0, ZERO)])
    gaze.event(0.8, "open").event(3.3, "close")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("head", [(0, ZERO), (0.12, (-10, 0, 8)), (0.4, ZERO)])

    # she rises over the battlements
    intro = m.animation("intro", 3.0)
    intro.pos("bust", [(0, (0, -40, 0)), (2.2, ZERO), (3.0, ZERO)])
    writhe(intro, 1.5, 30)
    intro.event(0.4, "rise")

    # she sinks back behind the wall
    death = m.animation("death", 2.0)
    death.pos("bust", [(0, ZERO), (2.0, (0, -40, 0))])
    writhe(death, 0.5, 45)


if __name__ == "__main__":
    build().write()
