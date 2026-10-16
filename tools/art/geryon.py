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
from bbmodel import Model, material, shade, hexc  # noqa: E402

FACE = material("face", "skin", base="c8a888", dark="8a6a50")
HAIR = material("hair", "hair", light="b8b0a0", dark="6a6258")
FUR = material("fur", "hair", light="8a6a3a", dark="3a2a14")
CLAW = material("claw", "horn", root="3a3028", tip="0e0a08")
STING = material("sting", "horn", root="4a1a2a", tip="0a0406")

BODY = hexc("2a3a5a")
BELLY = hexc("c8b880")
KNOT = hexc("d8a83a")
RING = hexc("b8382a")
RING_LIGHT = hexc("f05a3a")
EYE = hexc("8ac8ff")


def painted(faces):
    """Nooses and circlets: knotted bands of gold and small red rings on a blue-black hide, a pale belly underneath."""
    for name, f in faces.items():
        if name == "bottom":
            for y in range(f.h):
                for x in range(f.w):
                    f.px(x, y, shade(BELLY, 0.9 if y % 3 == 0 else 1.0))
            continue
        for y in range(f.h):
            for x in range(f.w):
                c = BODY
                # a knotted band every few pixels
                if (x + y) % 7 == 0 or (x - y) % 7 == 0:
                    c = KNOT if (x * 3 + y) % 4 else shade(KNOT, 0.7)
                f.px(x, y, shade(c, 1.0 + ((x * 13 + y * 7) % 5 - 2) * 0.03))
        # circlets: small rings, their centres glowing faintly
        for cy in range(2, f.h - 1, 5):
            for cx in range(2 + (cy // 5) % 2 * 2, f.w - 1, 5):
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    f.px(cx + dx, cy + dy, RING)
                f.glow(cx, cy, RING_LIGHT)


def kindly(faces):
    """A just man's face: calm eyes (a cold blue gleam), a long beard, a gentle mouth."""
    f = faces["front"]
    w, h = f.w, f.h
    for cx in (2, w - 4):
        f.px(cx, 3, shade(FACE.c["dark"], 0.6))
        f.glow(cx + 1, 4, EYE)
    f.px(w // 2, 6, shade(FACE.c["dark"], 0.8))
    for x in range(w // 2 - 1, w // 2 + 2):
        f.px(x, 7, shade(FACE.c["dark"], 0.5))
    for y in range(h - 2, h):
        for x in range(w):
            f.px(x, y, HAIR.c["light"] if (x + y) % 2 else HAIR.c["dark"])


SEGMENTS = [("seg1", 11, 12), ("seg2", 10, 12), ("seg3", 9, 11), ("seg4", 8, 10)]


def build():
    m = Model("geryon", texture_width=256)
    root = m.bone("root", (0, 14, 0))
    chest = root.bone("chest", (0, 14, -10))
    chest.cube("chest", (-6, 8, -20), (12, 12, 12), FUR)
    neck = chest.bone("neck", (0, 18, -19))
    neck.cube("neck", (-3, 15, -23), (6, 6, 5), FACE)
    head = neck.bone("head", (0, 19, -23))
    head.cube("head", (-4, 16, -31), (8, 9, 8), FACE, detail=kindly)
    head.cube("hair", (-4.5, 21, -30), (9, 5, 9), HAIR, inflate=0.2)
    head.cube("beard", (-3.5, 12, -32), (7, 5, 3), HAIR)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = chest.bone(f"{side}_paw", (5 * s, 10, -16))
        leg.cube(f"{side}_leg", (x(3, 4), 0, -18), (4, 11, 4), FUR, mirror=s == -1)
        leg.cube(f"{side}_claws", (x(2.5, 5), -2, -20), (5, 2, 6), CLAW, mirror=s == -1)
    parent = chest
    z = -8
    for name, size, length in SEGMENTS:
        bone = parent.bone(name, (0, 14, z))
        bone.cube(name, (-size / 2, 14 - size / 2, z), (size, size, length), material(name, "flat", base="2a3a5a"), detail=painted)
        parent = bone
        z += length
    tail = parent.bone("tail", (0, 14, z))
    tail.cube("tail", (-3, 11, z), (6, 6, 10), material("tail", "flat", base="2a3a5a"), detail=painted)
    sting = tail.bone("sting", (0, 14, z + 10))
    sting.cube("sting_base", (-2, 12, z + 10), (4, 4, 6), STING)
    sting.cube("sting_hook", (-1, 16, z + 13), (2, 6, 2), STING)
    sting.cube("sting_barb", (-1, 21, z + 9), (2, 2, 5), STING)
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
        sting.rot(bone, [(0, ZERO), (0.6, (-35 - i * 5, 0, 0)), (1.0, (-40 - i * 5, 0, 0)), (1.15, (-50 - i * 8, 0, 0)), (1.8, ZERO)])
    sting.rot("sting", [(0, ZERO), (0.6, (-60, 0, 0)), (1.0, (-70, 0, 0)), (1.15, (-95, 0, 0)), (1.8, (30, 0, 0))])
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
