#!/usr/bin/env python3
"""Geryon, the Image of Fraud (Inferno XVII), swimming in the air at the brink of the great cliff: "his face was the
face of a just man, so benignant was its outward skin, and of a serpent all the trunk beside. Two paws he had, hairy
to the armpits; his back, and breast, and both his flanks were painted with nooses and with circlets... in the void
his tail was quivering, twisting upward the venomous fork that armed the point as in a scorpion."

Run from the repo root:  python3 tools/art/geryon.py && python3 tools/gen_bosses.py

His body is a chain of segments (seg1..seg4, then the tail and the sting), each turning a little against the one
before, so he undulates as he swims. The events time his fight (GeryonFight): "flash" opens the window to block the
sting and "hit" is when it lands; "split" is when his false faces appear; "squeeze" his coils; "go" his dive; "roar"
his fury; "rise" and "roar" his entrance; "bow" is when, beaten, he bows his head to carry you down.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

FACE = material("face", "skin", base="c8a888", dark="8a6a50")
HAIR = material("hair", "hair", light="b0a490", dark="4a4036")
FUR = material("fur", "hair", light="9a7a44", dark="3a2a14")
MANE = material("mane", "hair", light="7a5a2e", dark="2a1c0c")
CLAW = material("claw", "horn", root="3a3028", tip="0e0a08")
STING = material("sting", "horn", root="4a1a2a", tip="0a0406")
SERPENT = material("serpent", "flat", base="2a3a5a")

BODY = hexc("2a3a5a")
BODY_DARK = hexc("1a2440")
BELLY = hexc("c8b880")
KNOT = hexc("d8a83a")
RING = hexc("b8382a")
RING_LIGHT = hexc("f05a3a")
EYE = hexc("8ac8ff")
LIP = hexc("9a6a58")


# ----------------------------------------------------------------------------------------------- shading helpers

def tone(f, x, y, k):
    """Darkens (k < 1) or lights (k > 1) one pixel of a face, as painted so far."""
    if 0 <= x < f.w and 0 <= y < f.h:
        c = f.canvas.get(f.x + x, f.y + y)
        if c[3]:
            f.px(x, y, shade(c[:3], k), c[3])


def tuck(faces, rows=1, k=0.72, lit=1.08, names=("front", "back", "left", "right")):
    """Shadow along a part's lowest rows and light along its top: sells a rounded form."""
    for name in names:
        f = faces[name]
        for i in range(min(rows, f.h)):
            for x in range(f.w):
                tone(f, x, f.h - 1 - i, k + (1 - k) * i / rows)
        for x in range(1, f.w - 1):
            tone(f, x, 0, lit)


def ring_at(f, cx, cy, r, colour):
    """A small hollow ring of pixels (a circlet, or a noose's loop)."""
    pts = {(0, -r), (0, r), (-r, 0), (r, 0)}
    if r >= 2:
        pts |= {(-1, -2), (1, -2), (-1, 2), (1, 2), (-2, -1), (-2, 1), (2, -1), (2, 1)}
    for dx, dy in pts:
        f.px(cx + dx, cy + dy, colour)


# ----------------------------------------------------------------------------------------------- details

def painted(faces):
    """Nooses and circlets on a blue-black hide of small overlapping scales: gold loops with their cords trailing,
    red rings whose centres glow faintly, and a pale belly of broad plates underneath."""
    for name, f in faces.items():
        light = 1.12 if name == "top" else 0.72 if name == "bottom" else 1.0
        if name == "bottom":
            for y in range(f.h):
                for x in range(f.w):
                    c = BELLY if y % 3 else shade(BELLY, 0.72)
                    edge = x in (0, f.w - 1)
                    f.px(x, y, shade(c, (0.75 if edge else 1.0) * (1.0 + (f.rng.random() - 0.5) * 0.06)))
            continue
        for y in range(f.h):
            for x in range(f.w):
                # scales: rows of 3 x 2, every other row shifted
                sx = (x + (y // 2) % 2 * 1) % 3
                c = BODY if sx else BODY_DARK
                if y % 2 == 1 and sx == 1:
                    c = mix(BODY, hexc("4a6a9a"), 0.35)
                # darker toward the flanks' bottoms (the belly side)
                g = 1.0
                if name not in ("top", "bottom") and f.h > 1:
                    g = 1.08 - 0.25 * y / (f.h - 1)
                f.px(x, y, shade(c, light * g))
        # the pattern: motifs along the face, alternately a noose (a gold loop with its cord trailing) and a circlet
        # (a red ring with a glowing heart), set clear of the edges so none is cut in half
        rows = [f.h // 2] if f.h < 11 else [f.h // 3, 2 * f.h // 3 + 1]
        for ri, cy in enumerate(rows):
            for j, cx in enumerate(range(3 + 3 * (ri % 2), f.w - 2, 6)):
                if (j + ri + len(name)) % 2 == 0:
                    ring_at(f, cx, cy, 2, KNOT)
                    f.px(cx, cy - 2, shade(KNOT, 1.2))
                    f.px(cx + 2, cy + 2, shade(KNOT, 0.8))
                    f.px(cx + 3, cy + 3, shade(KNOT, 0.7))
                else:
                    ring_at(f, cx, cy, 1, RING)
                    f.px(cx - 1, cy - 1, shade(RING, 0.6))
                    f.px(cx + 1, cy + 1, shade(RING, 0.6))
                    f.glow(cx, cy, RING_LIGHT)
    # the light along the top edges of the sides
    for name in ("left", "right", "front", "back"):
        f = faces[name]
        for x in range(f.w):
            tone(f, x, 0, 1.15)


def kindly(faces):
    """A just man's face: calm eyes (a cold blue gleam) under an even brow, lines at their corners, a straight nose's
    shadow, high cheekbones and a gentle mouth over the beard."""
    f = faces["front"]
    w, h = f.w, f.h
    dark = shade(FACE.c["dark"], 0.8)
    for cx in (1, w - 3):
        f.px(cx, 3, shade(FACE.c["dark"], 0.65))
        f.px(cx + 1, 3, shade(FACE.c["dark"], 0.65))
        f.px(cx, 4, hexc("e8e0d8"))
        f.glow(cx + 1 if cx == 1 else cx, 4, EYE)
        f.px(cx + 1 if cx != 1 else cx, 4, hexc("e8e0d8"))
        f.px(cx, 5, shade(FACE.c["base"], 0.85))
    f.px(0, 4, dark)
    f.px(w - 1, 4, dark)
    for x in range(1, w - 1):
        tone(f, x, 2, 0.92)
    # cheekbones lit, hollows under them
    f.px(1, 5, shade(FACE.c["base"], 1.08))
    f.px(w - 2, 5, shade(FACE.c["base"], 1.08))
    f.px(1, 6, shade(FACE.c["base"], 0.85))
    f.px(w - 2, 6, shade(FACE.c["base"], 0.85))
    for x in range(w // 2 - 1, w // 2 + 2):
        f.px(x, h - 1, LIP)
    for name in ("left", "right"):
        s = faces[name]
        ex = s.w - 2 if name == "right" else 1
        s.px(ex, 4, shade(FACE.c["dark"], 0.7))
    tuck(faces, 1, 0.8)


def nose(faces):
    tuck(faces, 1, 0.7)
    f = faces["bottom"]
    f.px(0, 0, shade(FACE.c["dark"], 0.5))
    f.px(f.w - 1, 0, shade(FACE.c["dark"], 0.5))


def hair_detail(faces):
    """Long hair: streaks running down it, darker underneath."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(0, f.w, 2):
            for y in range(f.h):
                tone(f, x, y, 0.88)
    tuck(faces, 2, 0.7)


def beard(faces):
    """The beard: streaked, and tapering ragged at its end."""
    hair_detail(faces)
    for name in ("front", "left", "right", "back"):
        f = faces[name]
        for x in range(f.w):
            if f.rng.random() < 0.3:
                f.px(x, f.h - 1, (0, 0, 0), 0)


def fur(faces):
    tuck(faces, 2, 0.7, 1.1)


def ragged_fur(faces):
    """Shaggy fur round the shoulders: the bottom edge torn into tufts."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            cut = f.rng.choice((0, 1, 1, 2))
            for y in range(f.h - cut, f.h):
                f.px(x, y, (0, 0, 0), 0)
    tuck(faces, 2, 0.7)


def paw(faces):
    tuck(faces, 1, 0.65)
    b = faces["bottom"]
    for y in range(b.h):
        for x in range(b.w):
            b.px(x, y, hexc("2a1e18") if (x + y) % 3 else hexc("3a2a22"))


def sting(faces):
    """The venomous fork: glossy, with a wet sheen and a drop of venom glowing at the root."""
    for name in ("front", "left", "right", "top"):
        f = faces[name]
        for y in range(f.h):
            tone(f, 0, y, 1.25)
    f = faces["bottom"]
    f.px(f.w // 2, 0, hexc("6aa04a"))


def telson(faces):
    """The swollen venom bulb: ringed, with veins of a sickly green."""
    for name in ("left", "right", "top", "bottom", "back"):
        f = faces[name]
        for x in range(0, f.w, 2):
            f.px(x, f.h // 2, hexc("3a5a2a"))
    tuck(faces, 1, 0.7, 1.2)


# ----------------------------------------------------------------------------------------------- the model

SEGMENTS = [("seg1", 11, 12), ("seg2", 10, 12), ("seg3", 9, 11), ("seg4", 8, 10)]
# at rest the hind body lifts a little, length by length, so the tail rises toward the sting as a scorpion's does (-X
# raises a length's far end); the sting's keys take these back off so its strike lands where it always did
REST = {"seg1": 0, "seg2": 0, "seg3": -4, "seg4": -6, "tail": -8, "sting": -10}


def serpent(bone, name, w, length, z, centre=14, spines=False):
    """One length of his body, rounded: a cube a little flatter than it is wide crossed with one a little narrower
    than it is tall, reaching back one pixel into the length before so a turn never opens a gap; a row of small
    dorsal spines behind the riders' seats."""
    bone.cube(name, (-w / 2, centre - (w - 2) / 2, z - 1), (w, w - 2, length + 1), SERPENT, detail=painted)
    bone.cube(f"{name}_round", (-(w - 2) / 2, centre - w / 2, z - 0.5), (w - 2, w, length), SERPENT, detail=painted)
    if spines:
        for i, dz in enumerate(range(2, length - 1, 4)):
            bone.cube(f"{name}_spine_{i}", (-0.5, centre + w / 2 - 0.5, z + dz), (1, 2, 2), STING,
                      rotation=(-25, 0, 0), origin=(0, centre + w / 2, z + dz + 1))


def build():
    m = Model("geryon", texture_width=256)
    root = m.bone("root", (0, 14, 0))

    # the forebody: the serpent's broad shoulders, which carry his hairy forelegs and the man's neck
    chest = root.bone("chest", (0, 14, -10))
    serpent(chest, "chest", 12, 12, -19)
    chest.cube("mane", (-5, 13, -22), (10, 9, 4), MANE, inflate=0.2, detail=ragged_fur)

    # the just man's neck and head: long white hair over it, a beard down to the forelegs
    neck = chest.bone("neck", (0, 18, -19))
    neck.cube("neck", (-2.5, 15, -25), (5, 5, 6), FACE, detail=fur)
    neck.cube("nape_hair", (-4, 15.5, -25), (8, 9, 3), HAIR, detail=hair_detail)
    head = neck.bone("head", (0, 19, -23))
    head.cube("head", (-3.5, 18, -30), (7, 8, 7), FACE, detail=kindly)
    head.cube("brow", (-3.5, 22.3, -30.5), (7, 1, 1), FACE, detail=fur)
    head.cube("nose", (-0.5, 19.2, -31.4), (1, 3, 2), FACE, detail=nose)
    head.cube("hair", (-4, 24.5, -30.5), (8, 2, 8), HAIR, inflate=0.2, detail=hair_detail)
    head.cube("hair_back", (-4, 16, -24.5), (8, 9, 2), HAIR, detail=hair_detail)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):  # a cube from a to a + w on the right becomes -(a + w) to -a on the left
            return a if s == 1 else -(a + w)
        head.cube(f"{side}_locks", (x(3.3, 1), 17.5, -28), (1, 7, 5), HAIR, mirror=s == -1, detail=hair_detail)
        head.cube(f"{side}_ear", (x(3.4, 1), 20, -27.5), (1, 2, 1), FACE, mirror=s == -1)
    head.cube("moustache", (-2.5, 18.3, -30.8), (5, 1, 1), HAIR)
    head.cube("beard", (-3.5, 15, -30.6), (7, 4, 4), HAIR, detail=beard)
    head.cube("beard_mid", (-3, 12.5, -30.4), (6, 3, 3), HAIR, detail=beard)
    head.cube("beard_tip", (-2, 10.5, -30), (4, 2, 2), HAIR, detail=beard)

    # two paws, hairy to the armpits: a shaggy shoulder, a lion's foreleg and a broad paw with four clawed toes
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mr = s == -1
        leg = chest.bone(f"{side}_paw", (5 * s, 10, -16))
        leg.cube(f"{side}_shoulder", (x(2, 6), 6, -20), (6, 7, 7), FUR, inflate=0.2, mirror=mr, detail=ragged_fur)
        leg.cube(f"{side}_leg", (x(3, 4), 1, -19), (4, 7, 4), FUR, mirror=mr, detail=fur)
        leg.cube(f"{side}_elbow_tuft", (x(3.5, 3), 2, -15.6), (3, 4, 2), MANE, mirror=mr, detail=ragged_fur)
        leg.cube(f"{side}_wrist", (x(3.2, 4), -1, -19.6), (4, 3, 4), FUR, mirror=mr, detail=fur)
        leg.cube(f"{side}_claws", (x(2.5, 5), -2.5, -21), (5, 2, 6), FUR, mirror=mr, detail=paw)
        for i in range(4):
            leg.cube(f"{side}_toe_{i}", (x(2.5 + i * 1.33, 1), -2.5, -22.5), (1, 2, 2), FUR, mirror=mr, detail=fur)
            leg.cube(f"{side}_claw_{i}", (x(2.5 + i * 1.33, 1), -2.7, -23.6), (1, 1, 1), CLAW, mirror=mr)

    # the serpent's body in four lengths, each narrower, turning a little against the one before as he swims
    parent = chest
    z = -8
    for name, size, length in SEGMENTS:
        bone = parent.bone(name, (0, 14, z), rotation=(REST[name], 0, 0))
        serpent(bone, name, size, length, z, spines=name not in ("seg1", "seg2"))
        parent = bone
        z += length

    # the tail, and the sting: a swollen bulb and a hooked, forked point raised over it
    tail = parent.bone("tail", (0, 14, z), rotation=(REST["tail"], 0, 0))
    serpent(tail, "tail", 6, 10, z, spines=True)
    sting_bone = tail.bone("sting", (0, 14, z + 10), rotation=(REST["sting"], 0, 0))
    z0 = z + 10
    sting_bone.cube("sting_base", (-2.5, 11.5, z0 - 0.5), (5, 5, 6), STING, detail=telson)
    sting_bone.cube("sting_neck", (-1.5, 15, z0 + 2), (3, 3, 3), STING, detail=telson)
    sting_bone.cube("sting_hook", (-1, 16, z0 + 3), (2, 6, 2), STING, detail=sting, rotation=(-15, 0, 0), origin=(0, 16, z0 + 4))
    for side, s in (("right", 1), ("left", -1)):
        sting_bone.cube(f"sting_barb_{side}", (-0.5 + 0.7 * s, 20.5, z0 - 1.5), (1, 1, 5), STING, detail=sting,
                        rotation=(-12, -16 * s, 0), origin=(0.7 * s, 21, z0 + 3.5))
        sting_bone.cube(f"sting_point_{side}", (-0.5 + 2.0 * s, 19, z0 - 2), (1, 2, 1), STING, detail=sting,
                        rotation=(-12, -16 * s, 0), origin=(0.7 * s, 21, z0 + 3.5))
    animations(m)
    return m


ZERO = (0, 0, 0)
BODY_BONES = ["seg1", "seg2", "seg3", "seg4", "tail"]


def wave(a, length, amp=12, pitch=0, phase_step=0.25):
    """The swimming wave down the body: each segment swings a little after the one in front."""
    for i, bone in enumerate(BODY_BONES):
        keys = []
        for k in range(5):
            t = length * k / 4
            phase = (k / 4 - i * phase_step) % 1.0
            # a sine sampled at quarter turns (the curves between keys round it off)
            yaw = amp * [1, 0, -1, 0][int(phase * 4) % 4]
            keys.append((t, (pitch, yaw, 0)))
        a.rot(bone, keys)


def animations(m):
    # swimming in the air, the knotted body rippling, the sting quivering
    idle = m.animation("idle", 2.4, loop=True)
    wave(idle, 2.4)
    idle.pos("root", [(0, ZERO), (1.2, (0, 2, 0)), (2.4, ZERO)])
    idle.rot("sting", [(0, (30, 0, 0)), (0.6, (40, 10, 0)), (1.2, (30, 0, 0)), (1.8, (40, -10, 0)), (2.4, (30, 0, 0))])
    idle.rot("right_paw", [(0, (-20, 0, 0)), (1.2, (-30, 0, 0)), (2.4, (-20, 0, 0))])
    idle.rot("left_paw", [(0, (-30, 0, 0)), (1.2, (-20, 0, 0)), (2.4, (-30, 0, 0))])

    # carrying a soul down the cliff: slow, wide wheeling strokes
    carry = m.animation("carry", 4.0, loop=True)
    wave(carry, 4.0, amp=8)
    carry.rot("head", [(0, (-10, 0, 0)), (4.0, (-10, 0, 0))])

    # the sting: the tail arches up over his back, flashes, and strikes down in front of him
    sting = m.animation("sting", 1.8)
    for i, bone in enumerate(["seg2", "seg3", "seg4", "tail"]):
        # (each segment points back from the one before: -X curls it up, so the tail arches over and the sting whips forward)
        r = REST[bone]
        sting.rot(bone, [(0, ZERO), (0.6, (-35 - i * 5 - r, 0, 0)), (1.0, (-40 - i * 5 - r, 0, 0)), (1.15, (-50 - i * 8 - r, 0, 0)), (1.8, ZERO)])
    r = REST["sting"]
    sting.rot("sting", [(0, ZERO), (0.6, (-60 - r, 0, 0)), (1.0, (-70 - r, 0, 0)), (1.15, (-95 - r, 0, 0)), (1.8, (30, 0, 0))])
    sting.rot("chest", [(0, ZERO), (0.6, (10, 0, 0)), (1.15, (-15, 0, 0)), (1.8, ZERO)])
    sting.event(0.6, "flash").event(1.15, "hit")

    # the false face: he smiles, his head tilts, and the air shivers into copies of him
    false = m.animation("falseface", 2.0)
    false.rot("head", [(0, ZERO), (0.6, (0, 0, 20)), (1.2, (0, 0, -20)), (2.0, ZERO)])
    false.rot("neck", [(0, ZERO), (0.6, (15, 0, 0)), (2.0, ZERO)])
    wave(false, 2.0, amp=20, phase_step=0.15)
    false.event(1.0, "split")

    # the coils: he circles and tightens, the whole body bending into a ring
    coil = m.animation("coil", 2.2)
    for bone in BODY_BONES:
        coil.rot(bone, [(0, ZERO), (0.8, (0, 35, 0)), (1.4, (0, 40, 0)), (2.2, ZERO)])
    coil.rot("head", [(0, ZERO), (0.8, (0, -30, 0)), (2.2, ZERO)])
    coil.event(1.4, "squeeze")

    # the dive: he pitches down and shoots forward, paws out
    dive = m.animation("dive", 1.6)
    dive.rot("root", [(0, ZERO), (0.5, (20, 0, 0)), (0.8, (-25, 0, 0)), (1.6, ZERO)])
    dive.rot("right_paw", [(0, ZERO), (0.8, (70, 0, 0)), (1.6, ZERO)])
    dive.rot("left_paw", [(0, ZERO), (0.8, (70, 0, 0)), (1.6, ZERO)])
    for bone in BODY_BONES:
        dive.rot(bone, [(0, ZERO), (0.5, (10, 0, 0)), (0.8, (-6, 0, 0)), (1.6, ZERO)])
    dive.event(0.8, "go")

    # blocked: he sags to the ground, dazed, his head hanging
    stagger = m.animation("stagger", 2.6)
    stagger.pos("root", [(0, ZERO), (0.4, (0, -10, 0)), (2.2, (0, -10, 0)), (2.6, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.4, (-40, 0, 15)), (2.2, (-40, 0, -15)), (2.6, ZERO)])
    for i, bone in enumerate(BODY_BONES):
        stagger.rot(bone, [(0, ZERO), (0.4, (0, 15 * (-1) ** i, 0)), (2.2, (0, 15 * (-1) ** i, 0)), (2.6, ZERO)])

    # his fury: he rears up, every knot and circlet ablaze
    phase = m.animation("phase", 2.8)
    phase.rot("root", [(0, ZERO), (0.8, (50, 0, 0)), (2.2, (50, 0, 0)), (2.8, ZERO)])
    phase.rot("head", [(0, ZERO), (0.8, (-20, 0, 0)), (2.2, (-20, 0, 0)), (2.8, ZERO)])
    phase.rot("right_paw", [(0, ZERO), (0.8, (90, 0, 20)), (2.2, (90, 0, 20)), (2.8, ZERO)])
    phase.rot("left_paw", [(0, ZERO), (0.8, (90, 0, -20)), (2.2, (90, 0, -20)), (2.8, ZERO)])
    phase.event(1.0, "roar")

    # his entrance: up out of the abyss below the cliff, head first
    intro = m.animation("intro", 5.0)
    intro.pos("root", [(0, (0, -60, 0)), (1.5, (0, -60, 0)), (3.0, (0, 4, 0)), (3.6, ZERO), (5.0, ZERO)])
    intro.rot("root", [(0, (70, 0, 0)), (3.0, (40, 0, 0)), (3.6, ZERO)])
    wave(intro, 5.0, amp=15)
    intro.rot("head", [(0, ZERO), (3.6, (20, 0, 0)), (4.4, (20, 0, 0)), (5.0, ZERO)])
    intro.event(1.6, "rise").event(3.7, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("chest", [(0, ZERO), (0.12, (12, 0, 0)), (0.4, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.12, (20, 0, 0)), (0.4, ZERO)])

    # beaten, he doesn't die: he sinks to the ground and bows his kindly head, ready to carry you
    death = m.animation("death", 3.0)
    death.pos("root", [(0, ZERO), (1.2, (0, -10, 0)), (3.0, (0, -10, 0))])
    death.rot("head", [(0, ZERO), (1.6, (-45, 0, 0)), (3.0, (-45, 0, 0))])
    death.rot("neck", [(0, ZERO), (1.6, (-30, 0, 0)), (3.0, (-30, 0, 0))])
    for i, bone in enumerate(BODY_BONES):
        death.rot(bone, [(0, ZERO), (1.6, (0, 10 * (-1) ** i, 0)), (3.0, (0, 10 * (-1) ** i, 0))])
    death.event(1.6, "bow")


if __name__ == "__main__":
    build().write()
