#!/usr/bin/env python3
"""The harpies (the Wood of the Suicides' monster): "broad wings have they, and human necks and faces, feet with
claws, and their great belly feathered; they make lament upon the strange trees" (Inferno XIII). A gaunt grey
woman's head and chest on a bird's body, with ragged wings for arms, scaly legs and hooked talons. They roost in the
dead wood and come when the trees scream.

Run from the repo root:  python3 tools/art/harpy.py && python3 tools/gen_bosses.py

Built as a vulture with a woman's head: a great rounded belly of scalloped feathers, a ruff where the feathers meet
the bare, bony chest and collarbones, a long scrawny human neck, and a hag's face (a heavy brow over glowing eyes, a
hooked nose, a jaw hanging open on a wail, lank grey hair). The wings are broad and spread level from the shoulders:
a thick leading edge over a panel of secondaries with a ragged trailing edge, long tertials by the body, and at the
tip four primaries fanned out like fingers. The tail is a fan of three feathers. The legs are shaggy feathered
thighs, scaly shanks, and feet of three forward toes and a back one, each ending in a black hooked claw.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="8a8a7c", dark="5a5a50")
FEATHER = material("feather", "hair", light="6a5a48", dark="2a2018")
BELLY = material("belly", "hair", light="7a6a54", dark="3a2e22")
WING = material("wing", "hair", light="5a4c3c", dark="1c1610")
QUILL = material("quill", "hair", light="4a3e30", dark="141008")
HAIR = material("hair", "hair", light="4a4a44", dark="141412")
LEG = material("leg", "scales", base="6a5a3a", edge="8a7a50", belly="7a6a48")
TALON = material("talon", "horn", root="4a4038", tip="141210")
EYE = hexc("e8ff6a")
SOCKET = hexc("1a1a14")
MOUTH = hexc("2a0a0a")
TOOTH = hexc("d8d0b8")
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
    """The side faces shaded along their lowest rows and their highest."""
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

def scallops(faces=("front", "back", "left", "right", "bottom", "top")):
    """Overlapping body feathers: rows of rounded tips, each lit along its top and dark along its lower edge where it
    lies over the next, every other row shifted."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            for y in range(f.h):
                row = y // 3
                for x in range(f.w):
                    sx = (x + (2 if row % 2 else 0)) % 4
                    sy = y % 3
                    if sy == 2 and sx in (0, 3):
                        mul(f, x, y, 0.72)
                    elif sy == 2:
                        mul(f, x, y, 0.85)
                    elif sy == 0 and sx in (1, 2):
                        mul(f, x, y, 1.18)
    return detail


def quills(ragged=True):
    """Flight feathers: lengthwise vanes along the wing (its length runs across the face), a pale shaft down each, the
    trailing edge torn into notches."""
    def detail(fs):
        for n in ("top", "bottom"):
            f = fs[n]
            for y in range(f.h):
                for x in range(f.w):
                    if y % 2 == 0:
                        mul(f, x, y, 1.2 if x % 3 else 0.9)  # the shaft
                    else:
                        mul(f, x, y, 0.85)
            if ragged:
                back = f.h - 1 if n == "top" else 0  # the back edge: the top's last row, the bottom's first
                for x in range(f.w):
                    if f.rng.random() < 0.35:
                        f.px(x, back, (0, 0, 0), alpha=0)
        for n in ("front", "back", "left", "right"):
            f = fs[n]
            for x in range(f.w):
                if x % 3 == 0:
                    mul(f, x, 0, 0.75)
    return detail


def primary(fs):
    """A long primary: a pale shaft down its middle, darkening to a ragged tip."""
    for n in ("top", "bottom"):
        f = fs[n]
        for x in range(f.w):
            t = x / max(1, f.w - 1)
            for y in range(f.h):
                mul(f, x, y, 1.0 - 0.3 * t)
        for x in range(f.w - 1):
            mul(f, x, 0 if n == "top" else f.h - 1, 1.25)
        f.px(f.w - 1, f.rng.randrange(f.h), (0, 0, 0), alpha=0)


def chest(fs):
    """A gaunt chest: collarbones and ribs under grey skin, the lower edge in shadow under the ruff."""
    f = fs["front"]
    for x in range(1, f.w - 1):
        f.px(x, 0, shade(SKIN.c["base"], 1.15))  # the collarbones
        mul(f, x, 1, 0.82)
    for y in range(2, f.h, 1):
        for x in range(f.w):
            if y % 2 == 1 and x not in (f.w // 2 - 1, f.w // 2):
                mul(f, x, y, 0.84)  # ribs
    for n in ("left", "right"):
        g = fs[n]
        for y in range(1, g.h, 2):
            for x in range(g.w):
                mul(g, x, y, 0.88)


def neck(fs):
    """A long, stringy neck: the tendons standing out, a hollow at its base."""
    f = fs["front"]
    for y in range(f.h):
        mul(f, 0, y, 0.75)
        mul(f, f.w - 1, y, 0.75)
    mul(f, f.w // 2, f.h - 1, 0.7)
    f.px(f.w // 2, 1, shade(SKIN.c["base"], 1.12))


def face(fs):
    """A hag's face: sunken sockets with glowing eyes, hollow cheeks, wrinkles round the eyes."""
    f = fs["front"]
    w = f.w
    for cx in (1, w - 3):
        f.px(cx, 2, SOCKET)
        f.px(cx + 1, 2, SOCKET)
        f.glow(cx + (1 if cx < w // 2 else 0), 2, EYE)
        f.px(cx + (0 if cx < w // 2 else 1), 2, shade(EYE, 0.6))
        mul(f, cx, 3, 0.8)
        mul(f, cx + 1, 3, 0.85)
    for x in range(w):
        mul(f, x, 1, 0.82)  # the brow's shadow
    for y in (3, 4, 5):
        mul(f, 0, y, 0.72)
        mul(f, w - 1, y, 0.72)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(1, 5):
            for x in range(g.w):
                mul(g, x, y, 0.88)


def brow(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.12))
    for x in (0, f.w - 1):
        mul(f, x, 0, 0.7)  # brows knitted in grief
    fs["bottom"].fill(lambda x, y: SOCKET)


def nose(fs):
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            mul(g, 0, y, 0.8)
    f = fs["front"]
    f.px(0, 0, shade(SKIN.c["base"], 1.15))
    fs["bottom"].fill(lambda x, y: MOUTH)


def jaw(fs):
    """The lower jaw hanging open on a wail: a row of teeth on its rim, the mouth dark above it."""
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, TOOTH if x % 2 else MOUTH)
    t = fs["top"]
    t.fill(lambda x, y: MOUTH)
    for n in ("left", "right", "bottom"):
        g = fs[n]
        for x in range(g.w):
            for y in range(g.h):
                mul(g, x, y, 0.78)


def mouth(fs):
    """The open mouth's upper lip, teeth along its lower edge."""
    f = fs["front"]
    for x in range(f.w):
        f.px(x, f.h - 1, TOOTH if x % 2 == 0 else MOUTH)
    fs["bottom"].fill(lambda x, y: MOUTH)


def lank(fs):
    """Lank, straggly hair: long strands, a few ends torn away."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if x % 2:
                for y in range(f.h):
                    mul(f, x, y, 0.8)
            if f.h > 2 and f.rng.random() < 0.4:
                f.px(x, f.h - 1, (0, 0, 0), alpha=0)


def scaly_toe(fs):
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, 0, 1.15)


def claw(fs):
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                f.px(x, y, shade(TALON.c["tip"], 1.0 + 0.4 * (y == 0)))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("harpy", texture_width=128)
    body = m.bone("body", (0, 14, 0), rotation=(25, 0, 0))
    # the great feathered belly, the ruff, and the bare bony chest above it
    body.cube("belly", (-4, 9, -3), (8, 7, 6), BELLY, detail=scallops())
    body.cube("belly_low", (-3, 8, -2.5), (6, 1, 5), BELLY, detail=scallops())
    body.cube("rump", (-3.5, 8, 2), (7, 7, 4), FEATHER, detail=scallops())
    body.cube("chest", (-3.5, 15.5, -2), (7, 4, 4), SKIN, detail=both(chest, ao(top=0)))
    body.cube("ruff", (-4, 14.5, -3.2), (8, 2, 6), FEATHER, inflate=0.2, detail=scallops())
    body.cube("shoulders", (-4, 19, -1.5), (8, 2, 4), FEATHER, detail=scallops())
    body.cube("back", (-3.5, 15, 1.5), (7, 5, 3), FEATHER, detail=scallops())

    tail = body.bone("tail", (0, 9, 3))
    tail.cube("tail", (-1.5, 7.5, 3), (3, 1, 8), QUILL, detail=quills())
    for side, s in (("right", 1), ("left", -1)):
        tail.cube(f"{side}_tail", (side_x(0.5, 2, s), 7.6, 3), (2, 1, 7), QUILL, detail=quills(),
                  rotation=(0, 18 * s, 0), origin=(1.5 * s, 8, 3))

    head = body.bone("head", (0, 21, -1), rotation=(-20, 0, 0))  # the face held level against the lean
    head.cube("neck", (-1.5, 18.5, -2.5), (3, 4, 3), SKIN, detail=neck)
    head.cube("head", (-3, 22, -4), (6, 6, 6), SKIN, detail=face)
    head.cube("brow", (-3, 25.5, -4.4), (6, 1, 1), SKIN, detail=brow)
    head.cube("nose", (-0.5, 23.4, -5), (1, 2, 1), SKIN, detail=nose)
    head.cube("mouth", (-2, 22.4, -4.3), (4, 1, 1), SKIN, detail=mouth)
    head.cube("jaw", (-2.5, 20.6, -3.8), (5, 2, 4), SKIN, detail=jaw, rotation=(-12, 0, 0), origin=(0, 22, 0))
    head.cube("hair", (-3.5, 26.5, -4.3), (7, 2, 7), HAIR, inflate=0.25, detail=lank)
    head.cube("locks", (-3.5, 18, 1.4), (7, 9, 2), HAIR, detail=lank)
    for side, s in (("right", 1), ("left", -1)):
        head.cube(f"{side}_lock", (side_x(3, 1, s), 19.5, -3.5), (1, 7, 4), HAIR, inflate=0.2, mirror=s == -1, detail=lank)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        # the wing spreads level from the shoulder, its chord running back
        wing = body.bone(f"{side}_wing", (4 * s, 20, 0))
        wing.cube(f"{side}_wing", (x(3.5, 12), 19, -1.5), (12, 2, 3), WING, mirror=s == -1, detail=scallops())
        wing.cube(f"{side}_secondaries", (x(4, 12), 19.4, 1.4), (12, 1, 6), QUILL, mirror=s == -1, detail=quills())
        wing.cube(f"{side}_tertials", (x(3.5, 4), 19.2, 1.4), (4, 1, 9), QUILL, mirror=s == -1, detail=quills())
        tip = wing.bone(f"{side}_wingtip", (16 * s, 20, 0))
        tip.cube(f"{side}_wingtip", (x(15, 4), 19.2, -1.2), (4, 1, 4), WING, mirror=s == -1, detail=scallops())
        # four primaries fanned out like fingers, the rearmost swept back most
        for i, (length, sweep) in enumerate(((10, 4), (10, -6), (9, -16), (8, -26))):
            tip.cube(f"{side}_primary{i}", (x(17, length), 19.4 - 0.15 * i, -1 + 1.6 * i), (length, 1, 2), QUILL,
                     mirror=s == -1, detail=primary, rotation=(0, sweep * s, 0), origin=(17 * s, 19.9, 1.6 * i))

        # the legs: shaggy feathered thighs, scaly shanks, three toes forward and one back, black hooked claws
        leg = m.bone(f"{side}_leg", (2 * s, 10, 1))
        leg.cube(f"{side}_thigh", (x(0.5, 3), 5, -0.5), (3, 6, 3), FEATHER, inflate=0.2, mirror=s == -1, detail=scallops())
        leg.cube(f"{side}_shin", (x(1, 2), 1, 0.5), (2, 5, 2), LEG, mirror=s == -1)
        foot = leg.bone(f"{side}_foot", (2.5 * s, 1, 1))
        foot.cube(f"{side}_talons", (x(1.5, 2), 0, 0), (2, 1, 2), LEG, mirror=s == -1)
        for i, yaw in enumerate((-22 * s, 0, 22 * s)):
            ox = (2 + (1 - i) * 0.9) * s
            foot.cube(f"{side}_toe{i}", (ox - 0.5, 0, -2), (1, 1, 3), LEG, mirror=s == -1, detail=scaly_toe,
                      rotation=(0, yaw, 0), origin=(ox, 0.5, 1))
            foot.cube(f"{side}_claw{i}", (ox - 0.5, -0.3, -3), (1, 1, 1), TALON, inflate=-0.1, detail=claw,
                      rotation=(0, yaw, 0), origin=(ox, 0.5, 1))
        foot.cube(f"{side}_hallux", (x(2, 1), 0, 1.5), (1, 1, 2), LEG, mirror=s == -1)
        foot.cube(f"{side}_spur", (x(2, 1), -0.2, 3), (1, 1, 1), TALON, inflate=-0.1, detail=claw)
    animations(m)
    return m


ZERO = (0, 0, 0)


def flap(a, length, up=35, down=-30, tip=20):
    """One wingbeat over `length` seconds: both wings up, then a hard stroke down, the tips lagging (bent down at
    the top of the stroke, up at the bottom)."""
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(0, (0, 0, up * s)), (length * 0.5, (0, 0, down * s)), (length, (0, 0, up * s))])
        a.rot(f"{side}_wingtip", [(0, (0, 0, -tip * s)), (length * 0.5, (0, 0, tip * s)), (length, (0, 0, -tip * s))])


def animations(m):
    # always aloft: the wings beat, the legs hang back, the body bobs with each stroke
    idle = m.animation("idle", 0.6, loop=True)
    flap(idle, 0.6)
    idle.pos("body", [(0, ZERO), (0.3, (0, 1.2, 0)), (0.6, ZERO)])
    idle.rot("right_leg", [(0, (-20, 0, 0)), (0.3, (-26, 0, 0)), (0.6, (-20, 0, 0))])
    idle.rot("left_leg", [(0, (-22, 0, 0)), (0.3, (-16, 0, 0)), (0.6, (-22, 0, 0))])
    idle.rot("head", [(0, ZERO), (0.3, (-4, 0, 0)), (0.6, ZERO)])

    # the stoop: wings swept back and up, talons thrown forward to rake, then the wings flare to brake
    attack = m.animation("attack", 0.6)
    for side, s in (("right", 1), ("left", -1)):
        attack.rot(f"{side}_wing", [(0, (0, 0, 35 * s)), (0.15, (0, -40 * s, 45 * s)), (0.35, (0, 20 * s, -20 * s)), (0.6, (0, 0, 35 * s))])
        attack.rot(f"{side}_leg", [(0, (-20, 0, 0)), (0.15, (30, 0, 0)), (0.3, (80, 0, 0)), (0.45, (-10, 0, 0)), (0.6, (-20, 0, 0))])
        attack.rot(f"{side}_foot", [(0, ZERO), (0.25, (-40, 0, 0)), (0.32, (30, 0, 0)), (0.6, ZERO)])
    attack.rot("body", [(0, ZERO), (0.2, (30, 0, 0)), (0.4, (-10, 0, 0)), (0.6, ZERO)])
    attack.event(0.3, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (20, 0, 8)), (0.3, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (25, 0, 0)), (0.3, ZERO)])

    # the lament: head thrown back, wings raised high and wide and shaking
    shriek = m.animation("shriek", 1.2)
    shriek.rot("head", [(0, ZERO), (0.2, (55, 0, 0)), (1.0, (55, 0, 0)), (1.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        shriek.rot(f"{side}_wing", [(0, ZERO), (0.2, (0, 0, 60 * s)), (0.3, (0, 0, 50 * s)), (0.4, (0, 0, 62 * s)), (0.5, (0, 0, 50 * s)),
                                    (0.6, (0, 0, 62 * s)), (1.0, (0, 0, 60 * s)), (1.2, ZERO)])
        shriek.rot(f"{side}_wingtip", [(0, ZERO), (0.2, (0, 0, 20 * s)), (1.0, (0, 0, 20 * s)), (1.2, ZERO)])
    shriek.event(0.2, "shriek")

    # falls out of the air, wings crumpled
    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.4, (-60, 0, 30)), (1.2, (-90, 0, 40))])
    death.pos("body", [(0, ZERO), (0.6, (0, -8, 0)), (1.2, (0, -11, 0))])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, ZERO), (0.4, (0, 0, 40 * s)), (1.2, (0, 0, 70 * s))])
        death.rot(f"{side}_leg", [(0, ZERO), (1.2, (60, 0, 0))])


if __name__ == "__main__":
    build().write()
