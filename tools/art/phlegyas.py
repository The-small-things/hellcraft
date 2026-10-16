#!/usr/bin/env python3
"""Phlegyas, the Ferryman of the Styx (Inferno VIII): a king who burned Apollo's temple in his rage, now the boatman
of the fifth circle, a drowned-grey giant wading waist-deep in the marsh with a great oar. Wild hair and beard
plastered with weed, a ragged cloak, chains of the sullen hanging off him, and eyes like the beacon flames that call
him.

Run from the repo root:  python3 tools/art/phlegyas.py && python3 tools/gen_bosses.py

The animations' events are what the server times Phlegyas's fight by (see PhlegyasFight): "flash" opens the window
for a shield block and "hit" is when the oar lands; "sweep" .. "swept" is the oar's pass over the water, "slam" the
wave he raises, "grab" and "throw" his reach for a soul, "roar" his fury at half health.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="7e8a80", dark="4e5a52")
CLOAK = material("cloak", "robe", base="2e3a34", fold="1a221e")
HAIR = material("hair", "hair", light="5e6a58", dark="26302a")
WEED = material("weed", "hair", light="4a6a2a", dark="22341a")
WOOD = material("wood", "hair", light="6a5236", dark="3a2a1a")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")

EYE = hexc("ff8a2a")
EYE_HOT = hexc("ffe0a0")
MOUTH = hexc("1a0e0a")
TOOTH = hexc("d8d0b0")


def face(faces):
    f = faces["front"]
    w = f.w
    for x in range(1, w - 1):
        f.px(x, 5, shade(SKIN.c["dark"], 0.7))
    for cx in (3, w - 5):
        f.px(cx - 1, 6, hexc("1a1e1a"))
        f.px(cx + 2, 6, hexc("1a1e1a"))
        f.glow(cx, 6, EYE)
        f.glow(cx + 1, 6, EYE_HOT)
    for x in range(3, w - 3):
        f.px(x, f.h - 2, MOUTH)
    for x in range(4, w - 4, 2):
        f.px(x, f.h - 3, TOOTH)


def chest(faces):
    """Gaunt ribs under drowned skin."""
    f = faces["front"]
    mid = f.w // 2
    dark = shade(SKIN.c["dark"], 0.85)
    for y in range(2, f.h - 4):
        f.px(mid, y, dark)
    for y in range(6, f.h - 4, 3):
        for x in list(range(3, mid - 2)) + list(range(mid + 3, f.w - 3)):
            if (x + y) % 2:
                f.px(x, y, dark)


def chain_detail(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h):
            f.px(f.w // 2, y, IRON.c["high"] if y % 2 else IRON.c["low"])


def blade_detail(faces):
    for name in ("left", "right"):
        f = faces[name]
        for y in range(f.h):
            f.px(0, y, shade(WOOD.c["dark"], 0.8))
            f.px(f.w - 1, y, shade(WOOD.c["dark"], 0.8))


def build():
    m = Model("phlegyas", texture_width=256)

    # he wades: the hips sit at the water line, the legs below it
    hips = m.bone("hips", (0, 40, 0))
    hips.cube("hips", (-13, 32, -8), (26, 12, 16), CLOAK)
    hips.cube("cloak_back", (-14, 10, 7), (28, 34, 3), CLOAK)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = hips.bone(f"{side}_leg", (7 * s, 34, 0))
        leg.cube(f"{side}_leg", (x(2, 10), 0, -5), (10, 34, 10), SKIN, mirror=s == -1)

    torso = hips.bone("torso", (0, 44, 0))
    torso.cube("chest", (-15, 44, -8), (30, 26, 16), SKIN, detail=chest)
    torso.cube("mantle", (-16, 62, -9), (32, 9, 18), CLOAK, inflate=0.5)
    torso.cube("weed_r", (8, 50, -9), (5, 12, 2), WEED)
    torso.cube("weed_l", (-12, 46, -9), (4, 14, 2), WEED)
    torso.cube("chain", (-2, 42, -10), (4, 22, 2), IRON, detail=chain_detail)

    head = torso.bone("head", (0, 71, -1))
    head.cube("skull", (-8, 71, -10), (16, 15, 16), SKIN, detail=face)
    head.cube("hair", (-9, 76, -8), (18, 13, 18), HAIR, inflate=0.3)
    head.cube("hair_back", (-9, 58, 5), (18, 22, 4), HAIR)
    head.cube("brow", (-8, 79, -11), (16, 2, 3), SKIN)
    jaw = head.bone("jaw", (0, 72, -4))
    jaw.cube("jaw", (-7, 67, -10), (14, 5, 12), SKIN)
    jaw.cube("beard", (-8, 54, -11), (16, 14, 4), HAIR)
    jaw.cube("beard_weed", (-3, 48, -10), (6, 7, 3), WEED)

    arms = {}
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (17 * s, 66, 0))
        arm.cube(f"{side}_shoulder", (x(14, 10), 58, -6), (10, 12, 12), CLOAK, inflate=0.4, mirror=mirror)
        arm.cube(f"{side}_upper", (x(15, 8), 46, -4), (8, 18, 8), SKIN, mirror=mirror)
        fore = arm.bone(f"{side}_forearm", (19 * s, 47, 0), rotation=(30, 0, 0))
        fore.cube(f"{side}_fore", (x(15.5, 7), 30, -3.5), (7, 18, 7), SKIN, mirror=mirror)
        fore.cube(f"{side}_shackle", (x(15, 8), 32, -4), (8, 4, 8), IRON, inflate=0.2, mirror=mirror)
        hand = fore.bone(f"{side}_hand", (19 * s, 30, 0))
        hand.cube(f"{side}_hand", (x(15, 8), 22, -4), (8, 8, 8), SKIN, mirror=mirror)
        arms[side] = hand

    # the great oar, held in the right hand: a long shaft, the blade below
    oar = arms["right"].bone("oar", (19, 26, 0))
    oar.cube("shaft", (17.5, -30, -1.5), (3, 90, 3), WOOD)
    oar.cube("blade", (16, -54, -5), (6, 26, 10), WOOD, detail=blade_detail)
    oar.cube("grip", (17, 58, -2), (4, 4, 4), IRON)

    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)


def mirror(v):
    return (v[0], -v[1], -v[2])


def animations(m):
    # wading in place: he sways with the water, the oar dipping
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("hips", [(0, ZERO), (2, (0, -1, 0)), (4, ZERO)])
    idle.rot("torso", [(0, ZERO), (1, (0, 4, 1)), (2, ZERO), (3, (0, -4, -1)), (4, ZERO)])
    idle.rot("head", [(0, ZERO), (1.5, (5, 10, 0)), (2.5, (5, -10, 0)), (4, ZERO)])
    idle.rot("right_arm", [(0, (-20, 0, 0)), (2, (-26, 0, 0)), (4, (-20, 0, 0))])
    idle.rot("left_arm", [(0, (-10, 0, 0)), (2, (-6, 0, 0)), (4, (-10, 0, 0))])
    idle.rot("oar", [(0, (10, 0, 0)), (2, (14, 0, 0)), (4, (10, 0, 0))])

    walk = m.animation("walk", 2.0, loop=True)
    walk.rot("right_leg", [(0, (20, 0, 0)), (1, (-20, 0, 0)), (2, (20, 0, 0))])
    walk.rot("left_leg", [(0, (-20, 0, 0)), (1, (20, 0, 0)), (2, (-20, 0, 0))])
    walk.rot("torso", [(0, (0, 6, 0)), (1, (0, -6, 0)), (2, (0, 6, 0))])

    # the oar raised overhead and brought down on the landing: the white flash is the moment to block
    oar = m.animation("oar", 1.8)
    oar.rot("right_arm", [(0, ZERO), (0.7, (175, 0, -10)), (1.05, (70, 0, 0)), (1.3, (65, 0, 0)), (1.8, ZERO)])
    oar.rot("left_arm", [(0, ZERO), (0.7, (165, 0, 20)), (1.05, (60, 0, 10)), (1.8, ZERO)])
    oar.rot("torso", [(0, ZERO), (0.7, (14, 0, 0)), (1.05, (-22, 0, 0)), (1.3, (-20, 0, 0)), (1.8, ZERO)])
    oar.rot("head", [(0, ZERO), (0.7, (12, 0, 0)), (1.05, (-15, 0, 0)), (1.8, ZERO)])
    oar.rot("jaw", [(0, ZERO), (0.9, (30, 0, 0)), (1.3, ZERO)])
    oar.rot("oar", [(0, (10, 0, 0)), (0.7, (30, 0, 0)), (1.05, (-10, 0, 0)), (1.3, (-5, 0, 0)), (1.8, (10, 0, 0))])
    oar.event(0.55, "flash").event(1.05, "hit")

    # the oar swept round over the water in a full circle (purple: it can't be blocked, jump it)
    sweep = m.animation("sweep", 2.6)
    sweep.rot("torso", [(0, ZERO), (1.0, (0, 50, 0)), (1.2, (0, 50, 0)), (2.0, (0, -310, 0)), (2.6, (0, -360, 0))])
    sweep.rot("right_arm", [(0, ZERO), (1.0, (40, 0, -70)), (2.0, (40, 0, -70)), (2.6, ZERO)])
    sweep.rot("oar", [(0, (10, 0, 0)), (1.0, (80, 0, 0)), (2.0, (80, 0, 0)), (2.6, (10, 0, 0))])
    sweep.event(1.2, "sweep").event(2.0, "swept")

    # the wave: both fists brought down on the water; it rolls out over the landing
    wave = m.animation("wave", 2.0)
    for side, s in (("right", 1), ("left", -1)):
        keys = [(0, ZERO), (0.6, (170, 0, 25)), (1.0, (40, 0, 5)), (1.4, (40, 0, 5)), (2.0, ZERO)]
        wave.rot(f"{side}_arm", keys if s == 1 else [(t, mirror(v)) for t, v in keys])
    wave.rot("torso", [(0, ZERO), (0.6, (12, 0, 0)), (1.0, (-30, 0, 0)), (1.4, (-28, 0, 0)), (2.0, ZERO)])
    wave.rot("jaw", [(0, ZERO), (0.8, (35, 0, 0)), (1.4, ZERO)])
    wave.rot("oar", [(0, (10, 0, 0)), (0.6, (-30, 0, 0)), (2.0, (10, 0, 0))])
    wave.event(1.0, "slam")

    # he reaches for a soul with his free hand and hurls it into the deep
    grab = m.animation("grab", 2.2)
    grab.rot("left_arm", [(0, ZERO), (0.8, (95, -20, 25)), (1.1, (100, -25, 30)), (1.6, (150, 0, 10)), (1.9, (40, 0, -20)), (2.2, ZERO)])
    grab.rot("left_forearm", [(0, ZERO), (0.8, (-20, 0, 0)), (1.6, (10, 0, 0)), (2.2, ZERO)])
    grab.rot("torso", [(0, ZERO), (0.8, (20, -25, 0)), (1.6, (-10, 15, 0)), (1.9, (10, 25, 0)), (2.2, ZERO)])
    grab.rot("head", [(0, ZERO), (0.8, (15, -20, 0)), (1.6, (-10, 0, 0)), (2.2, ZERO)])
    grab.event(1.1, "grab").event(1.9, "throw")

    stagger = m.animation("stagger", 2.0)
    stagger.rot("torso", [(0, ZERO), (0.15, (-18, 0, 6)), (0.8, (-12, 0, -5)), (1.4, (-8, 0, 4)), (2.0, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.15, (-30, 0, 0)), (1.0, (-15, 15, 0)), (2.0, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.15, (30, 0, 0)), (1.4, (15, 0, 0)), (2.0, ZERO)])
    stagger.rot("right_arm", [(0, ZERO), (0.15, (-30, 0, 40)), (2.0, ZERO)])
    stagger.rot("left_arm", [(0, ZERO), (0.15, (-30, 0, -40)), (2.0, ZERO)])

    # at half his health: he roars up at the burning city and beats the water with his oar
    phase = m.animation("phase", 3.0)
    phase.rot("torso", [(0, ZERO), (0.8, (25, 0, 0)), (2.0, (25, 0, 0)), (2.4, (-15, 0, 0)), (3.0, ZERO)])
    phase.rot("head", [(0, ZERO), (0.8, (-30, 0, 0)), (2.0, (-30, 0, 0)), (3.0, ZERO)])
    phase.rot("jaw", [(0, ZERO), (0.8, (55, 0, 0)), (2.0, (55, 0, 0)), (2.4, ZERO)])
    phase.rot("right_arm", [(0, ZERO), (0.8, (170, 0, -20)), (2.0, (170, 0, -20)), (2.4, (60, 0, 0)), (3.0, ZERO)])
    phase.rot("left_arm", [(0, ZERO), (0.8, (-20, 0, -80)), (2.0, (-20, 0, -80)), (3.0, ZERO)])
    phase.event(0.8, "roar").event(2.4, "slam")

    # up out of the deep: he rises from the water, shakes the weed from his face and roars
    intro = m.animation("intro", 6.0)
    intro.pos("hips", [(0, (0, -60, 0)), (0.8, (0, -60, 0)), (3.0, (0, -4, 0)), (3.4, ZERO)])
    intro.rot("torso", [(0, (30, 0, 0)), (3.0, (10, 0, 0)), (3.6, (0, 0, 8)), (3.9, (0, 0, -8)), (4.2, (-20, 0, 0)), (5.2, (-20, 0, 0)), (6.0, ZERO)])
    intro.rot("head", [(0, (30, 0, 0)), (3.0, (10, 0, 0)), (4.2, (-25, 0, 0)), (5.2, (-25, 0, 0)), (6.0, ZERO)])
    intro.rot("jaw", [(0, ZERO), (4.1, (5, 0, 0)), (4.3, (55, 0, 0)), (5.2, (50, 0, 0)), (5.6, ZERO)])
    intro.rot("right_arm", [(0, (-20, 0, 0)), (4.2, (160, 0, -15)), (5.2, (160, 0, -15)), (6.0, (-20, 0, 0))])
    intro.event(0.8, "rise").event(4.3, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("torso", [(0, ZERO), (0.1, (6, 0, 0)), (0.35, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (-8, 0, 0)), (0.35, ZERO)])

    # he sinks back into the Styx, his oar left floating
    death = m.animation("death", 3.6)
    death.rot("torso", [(0, ZERO), (0.6, (-20, 0, 0)), (1.8, (30, 0, 10)), (3.6, (40, 0, 12))])
    death.pos("hips", [(0, ZERO), (1.0, (0, -4, 0)), (3.6, (0, -70, 0))])
    death.rot("jaw", [(0, ZERO), (0.6, (50, 0, 0)), (3.6, (30, 0, 0))])
    death.rot("right_arm", [(0, ZERO), (0.6, (120, 0, -20)), (1.8, (60, 0, 0)), (3.6, (40, 0, 0))])
    death.rot("oar", [(0, (10, 0, 0)), (1.8, (70, 0, 0)), (3.6, (85, 0, 0))])
    death.event(1.8, "sink")


if __name__ == "__main__":
    build().write()
