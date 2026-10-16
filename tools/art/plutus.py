#!/usr/bin/env python3
"""Plutus, the Great Enemy (Inferno VII): the old god of riches, swollen with the gold of the fourth circle. A huge
bloated banker in a purple velvet waistcoat strained over a gold-skinned belly, a cloak sewn with coins, ringed
fists like money bags, and the head of the wolf Virgil calls him ("accursed wolf"), crowned with coins. He shrieks
"Pape Satàn, pape Satàn aleppe!"

Run from the repo root:  python3 tools/art/plutus.py && python3 tools/gen_bosses.py

The animations' events are what the server times Plutus's fight by (see PlutusFight): "flash" opens the window for
a shield block and "hit" is when his fist lands; "rain" when the gold falls; "leap" and "land" his lunge; "shriek"
Pape Satàn; "roar" and "mint" his fury at half health, when the great coins start to roll.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="c8a24e", dark="8a6a26")
VEST = material("vest", "robe", base="4a1a5a", fold="2a0c36", trim="d4a83a")
GOLD = material("gold", "metal", base="d4a83a", high="f6dc80", low="8a6a1e")
FUR = material("fur", "hair", light="8a7a62", dark="463a2a")
CLAW = material("claw", "flat", base="2a2018")
COINS = material("coins", "metal", base="c9982e", high="f1d27a", low="7d5a17")

EYE = hexc("ffe04a")
EYE_HOT = hexc("fffbe0")
MOUTH = hexc("3a1208")
TOOTH = hexc("efe6c8")


def coin_detail(faces):
    """Rows of coins sewn edge to edge."""
    for name in ("front", "back", "left", "right", "top"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if (x + (y // 3) * 2) % 4 == 0 or y % 3 == 0:
                    f.px(x, y, COINS.c["low"])
                elif (x * 3 + y) % 7 == 0:
                    f.px(x, y, COINS.c["high"])


def vest_detail(faces):
    """Gold buttons straining down the front."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(3, f.h - 2, 5):
        f.px(mid - 1, y, GOLD.c["high"])
        f.px(mid, y, GOLD.c["base"])
        f.px(mid - 1, y + 1, GOLD.c["base"])
        f.px(mid, y + 1, GOLD.c["low"])


def belly_detail(faces):
    """The belly bulging out between the waistcoat's sides, with a navel."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(2, y, VEST.c["base"])
        f.px(3, y, VEST.c["fold"])
        f.px(f.w - 3, y, VEST.c["base"])
        f.px(f.w - 4, y, VEST.c["fold"])
    f.px(mid, f.h // 2, shade(SKIN.c["dark"], 0.7))
    f.px(mid - 1, f.h // 2, shade(SKIN.c["dark"], 0.8))


def wolf_face(faces):
    """Small greedy yellow eyes under a fur brow."""
    f = faces["front"]
    for cx in (3, f.w - 5):
        f.px(cx - 1, 5, FUR.c["dark"])
        f.px(cx, 5, FUR.c["dark"])
        f.px(cx + 1, 5, FUR.c["dark"])
        f.glow(cx, 6, EYE)
        f.glow(cx + 1, 6, EYE_HOT)


def snout_detail(faces):
    f = faces["front"]
    mid = f.w // 2
    f.px(mid - 1, 1, hexc("1a1210"))
    f.px(mid, 1, hexc("1a1210"))
    b = faces["bottom"]
    b.fill(lambda x, y: MOUTH)
    for x in range(1, f.w - 1, 2):
        f.px(x, f.h - 1, TOOTH)


def jaw_detail(faces):
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    f = faces["front"]
    for x in range(1, f.w - 1, 2):
        f.px(x, 0, TOOTH)


def ring_detail(faces):
    for name in ("front", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            f.px(x, 2, GOLD.c["high"])
            f.px(x, 6, GOLD.c["base"])
        f.glow(f.w // 2, 2, hexc("ff3a3a"))


def build():
    m = Model("plutus", texture_width=256)

    body = m.bone("body", (0, 26, 0))
    body.cube("belly", (-21, 14, -19), (42, 36, 34), SKIN, detail=belly_detail)
    body.cube("vest", (-21.5, 30, -12), (43, 22, 27), VEST, inflate=0.4, detail=vest_detail)
    body.cube("chest", (-18, 50, -12), (36, 16, 26), VEST, detail=vest_detail)
    body.cube("cloak", (-20, 18, 14), (40, 48, 4), COINS, detail=coin_detail)
    body.cube("collar", (-19, 64, -13), (38, 4, 28), COINS, detail=coin_detail)
    body.cube("belt", (-22, 28, -20), (44, 4, 36), GOLD)

    head = body.bone("head", (0, 66, -2))
    head.cube("skull", (-9, 66, -13), (18, 16, 17), FUR, detail=wolf_face)
    head.cube("jowls", (-11, 62, -14), (22, 7, 15), SKIN)
    head.cube("snout", (-5, 67, -24), (10, 8, 11), FUR, detail=snout_detail)
    head.cube("nose", (-2, 74, -25), (4, 2, 3), CLAW)
    for side, s in (("r", 1), ("l", -1)):
        ex = 5 if s == 1 else -9
        head.cube(f"ear_{side}", (ex, 81, -8), (4, 7, 3), FUR, rotation=(-10, 0, -12 * s), origin=(ex + 2, 81, -6.5))
    head.cube("crown", (-10, 82, -14), (20, 4, 19), GOLD, inflate=0.5)
    for i, cx in enumerate((-8, -3, 2, 7)):
        head.cube(f"crown_coin_{i}", (cx - 1.5, 86, -15), (3, 4 + (i % 2) * 2, 1), COINS)
    jaw = head.bone("jaw", (0, 66, -14))
    jaw.cube("jaw", (-5, 62, -24), (10, 5, 11), FUR, detail=jaw_detail)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        leg = body.bone(f"{side}_leg", (10 * s, 18, 0))
        leg.cube(f"{side}_leg", (x(5, 11), 2, -7), (11, 16, 13), VEST, mirror=mirror)
        leg.cube(f"{side}_foot", (x(4.5, 12), 0, -10), (12, 4, 16), CLAW, mirror=mirror)
        arm = body.bone(f"{side}_arm", (22 * s, 60, 0))
        arm.cube(f"{side}_shoulder", (x(18, 12), 52, -8), (12, 12, 16), COINS, inflate=0.4, mirror=mirror, detail=coin_detail)
        arm.cube(f"{side}_upper", (x(20, 10), 40, -5), (10, 14, 10), SKIN, mirror=mirror)
        fore = arm.bone(f"{side}_forearm", (25 * s, 41, 0), rotation=(20, 0, 0))
        fore.cube(f"{side}_fore", (x(20.5, 9), 24, -4.5), (9, 17, 9), SKIN, mirror=mirror)
        fore.cube(f"{side}_cuff", (x(20, 10), 34, -5), (10, 4, 10), GOLD, inflate=0.2, mirror=mirror)
        hand = fore.bone(f"{side}_hand", (25 * s, 24, 0))
        hand.cube(f"{side}_fist", (x(18.5, 13), 12, -6.5), (13, 12, 13), SKIN, mirror=mirror)
        hand.cube(f"{side}_rings", (x(18.5, 13), 14, -6.5), (13, 8, 13), GOLD, inflate=0.3, mirror=mirror, detail=ring_detail)

    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)


def mirror(v):
    return (v[0], -v[1], -v[2])


def arms(a, keys, forearm=None):
    a.rot("right_arm", keys)
    a.rot("left_arm", [(t, mirror(v)) for t, v in keys])
    if forearm:
        a.rot("right_forearm", forearm)
        a.rot("left_forearm", [(t, mirror(v)) for t, v in forearm])


def animations(m):
    idle = m.animation("idle", 4.0, loop=True)
    idle.pos("body", [(0, ZERO), (2, (0, 0.8, 0)), (4, ZERO)])
    idle.rot("head", [(0, ZERO), (1, (0, 12, 0)), (2, (-4, 0, 0)), (3, (0, -12, 0)), (4, ZERO)])
    idle.rot("jaw", [(0, ZERO), (1.0, (6, 0, 0)), (1.3, ZERO), (2.5, (8, 0, 0)), (2.8, ZERO), (4, ZERO)])
    # rubbing his fists together over his belly
    arms(idle, [(0, (20, 0, -10)), (1, (24, 0, -14)), (2, (20, 0, -10)), (3, (24, 0, -14)), (4, (20, 0, -10))],
         forearm=[(0, (40, 0, 0)), (1, (44, 0, 0)), (2, (40, 0, 0)), (3, (44, 0, 0)), (4, (40, 0, 0))])

    # a waddle: the belly rolls from side to side
    walk = m.animation("walk", 1.6, loop=True)
    walk.rot("body", [(0, (0, 0, 5)), (0.8, (0, 0, -5)), (1.6, (0, 0, 5))])
    walk.pos("body", [(0, ZERO), (0.4, (0, -1, 0)), (0.8, ZERO), (1.2, (0, -1, 0)), (1.6, ZERO)])
    walk.rot("right_leg", [(0, (25, 0, 0)), (0.8, (-25, 0, 0)), (1.6, (25, 0, 0))])
    walk.rot("left_leg", [(0, (-25, 0, 0)), (0.8, (25, 0, 0)), (1.6, (-25, 0, 0))])
    arms(walk, [(0, (-15, 0, 0)), (0.8, (15, 0, 0)), (1.6, (-15, 0, 0))])

    # his fist, raised high and brought down: the white flash is the moment to block
    fist = m.animation("fist", 1.6)
    fist.rot("right_arm", [(0, ZERO), (0.6, (170, 0, 15)), (0.95, (60, 0, 0)), (1.2, (55, 0, 0)), (1.6, ZERO)])
    fist.rot("right_forearm", [(0, ZERO), (0.6, (20, 0, 0)), (0.95, (-5, 0, 0)), (1.6, ZERO)])
    fist.rot("body", [(0, ZERO), (0.6, (6, -15, 0)), (0.95, (-10, 20, 0)), (1.6, ZERO)])
    fist.rot("head", [(0, ZERO), (0.6, (-10, 0, 0)), (0.95, (12, 0, 0)), (1.6, ZERO)])
    fist.rot("jaw", [(0, ZERO), (0.9, (30, 0, 0)), (1.2, ZERO)])
    fist.event(0.45, "flash").event(0.95, "hit")

    # Weight of Gold: he flings handfuls of gold into the air, and it comes down where the red circles are
    gold = m.animation("gold", 2.4)
    arms(gold, [(0, ZERO), (0.5, (30, 0, 0)), (0.8, (160, 0, 30)), (1.2, (150, 0, 35)), (1.6, (40, 0, 10)), (2.4, ZERO)],
         forearm=[(0, ZERO), (0.5, (60, 0, 0)), (0.8, (0, 0, 0)), (2.4, ZERO)])
    gold.rot("head", [(0, ZERO), (0.8, (-30, 0, 0)), (1.4, (-25, 0, 0)), (2.4, ZERO)])
    gold.rot("jaw", [(0, ZERO), (0.8, (35, 0, 0)), (1.4, (30, 0, 0)), (1.8, ZERO)])
    gold.rot("body", [(0, ZERO), (0.8, (-8, 0, 0)), (1.6, (4, 0, 0)), (2.4, ZERO)])
    gold.event(0.8, "throw").event(1.6, "rain")

    # the Lunge: a crouch, then he hurls his bulk along the red line and lands on his belly
    lunge = m.animation("lunge", 1.8)
    lunge.rot("body", [(0, ZERO), (0.6, (12, 0, 0)), (0.75, (-35, 0, 0)), (1.2, (-40, 0, 0)), (1.8, ZERO)])
    lunge.pos("body", [(0, ZERO), (0.6, (0, -4, 0)), (0.75, (0, 3, 0)), (1.2, (0, -6, 0)), (1.8, ZERO)])
    arms(lunge, [(0, ZERO), (0.6, (-40, 0, 0)), (0.75, (150, 0, 20)), (1.2, (110, 0, 25)), (1.8, ZERO)])
    lunge.rot("right_leg", [(0, ZERO), (0.6, (40, 0, 0)), (0.75, (-30, 0, 0)), (1.8, ZERO)])
    lunge.rot("left_leg", [(0, ZERO), (0.6, (40, 0, 0)), (0.75, (-30, 0, 0)), (1.8, ZERO)])
    lunge.event(0.7, "leap").event(1.2, "land")

    # Pape Satàn: he swells, throws his head back, and shrieks (purple: it can't be blocked)
    pape = m.animation("pape", 2.6)
    pape.rot("head", [(0, ZERO), (0.8, (-35, 0, 0)), (1.2, (15, 0, 0)), (1.4, (10, 6, 0)), (1.6, (12, -6, 0)), (1.8, (10, 6, 0)), (2.0, (12, -6, 0)), (2.6, ZERO)])
    pape.rot("jaw", [(0, ZERO), (0.8, (10, 0, 0)), (1.2, (60, 0, 0)), (2.0, (55, 0, 0)), (2.4, ZERO)])
    pape.pos("body", [(0, ZERO), (0.8, (0, 3, 0)), (1.2, (0, -1, 0)), (2.6, ZERO)])
    arms(pape, [(0, ZERO), (0.8, (10, 0, 60)), (1.2, (-10, 0, 85)), (2.0, (-10, 0, 80)), (2.6, ZERO)])
    pape.event(1.2, "shriek")

    stagger = m.animation("stagger", 2.0)
    stagger.rot("body", [(0, ZERO), (0.15, (-14, 0, 6)), (0.8, (-10, 0, -6)), (1.4, (-6, 0, 4)), (2.0, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.15, (-25, 0, 0)), (1.0, (-15, 15, 0)), (2.0, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.15, (30, 0, 0)), (1.4, (15, 0, 0)), (2.0, ZERO)])
    arms(stagger, [(0, ZERO), (0.15, (-30, 0, 50)), (1.4, (-20, 0, 40)), (2.0, ZERO)])

    # at half his health: he beats his belly like a drum and roars, and the great coins of the Mint start to roll
    phase = m.animation("phase", 3.2)
    arms(phase, [(0, ZERO), (0.4, (60, 0, 10)), (0.6, (20, 0, -10)), (0.8, (60, 0, 10)), (1.0, (20, 0, -10)),
                 (1.4, (-20, 0, 80)), (2.6, (-20, 0, 80)), (3.2, ZERO)],
         forearm=[(0, ZERO), (0.4, (60, 0, 0)), (1.0, (60, 0, 0)), (1.4, ZERO), (3.2, ZERO)])
    phase.rot("head", [(0, ZERO), (1.4, (-35, 0, 0)), (2.6, (-35, 0, 0)), (3.2, ZERO)])
    phase.rot("jaw", [(0, ZERO), (1.4, (55, 0, 0)), (2.6, (55, 0, 0)), (3.0, ZERO)])
    phase.pos("body", [(0, ZERO), (1.4, (0, 2, 0)), (2.6, (0, 2, 0)), (3.2, ZERO)])
    phase.event(1.4, "roar").event(2.2, "mint")

    # slumped over his hoard, he lifts his head, heaves himself up, and shrieks his welcome
    intro = m.animation("intro", 6.0)
    intro.rot("body", [(0, (30, 0, 0)), (0.8, (30, 0, 0)), (2.6, (-5, 0, 0)), (3.0, ZERO)])
    intro.pos("body", [(0, (0, -12, 0)), (0.8, (0, -12, 0)), (2.6, ZERO)])
    intro.rot("head", [(0, (35, 0, 0)), (1.2, (35, 0, 0)), (2.0, (0, 20, 0)), (2.6, (0, -20, 0)), (3.2, (-30, 0, 0)), (4.8, (-30, 0, 0)), (5.6, ZERO)])
    intro.rot("jaw", [(0, ZERO), (3.2, (5, 0, 0)), (3.4, (60, 0, 0)), (4.8, (55, 0, 0)), (5.2, ZERO)])
    arms(intro, [(0, (60, 0, -20)), (1.2, (60, 0, -20)), (2.6, (20, 0, 0)), (3.4, (-15, 0, 80)), (4.8, (-15, 0, 78)), (5.6, ZERO)],
         forearm=[(0, (60, 0, 0)), (2.6, (20, 0, 0)), (5.6, ZERO)])
    intro.event(0.8, "rise").event(3.4, "roar")

    hurt = m.animation("hurt", 0.35)
    hurt.rot("body", [(0, ZERO), (0.1, (-4, 0, 0)), (0.35, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (-8, 0, 0)), (0.35, ZERO)])

    # "as sails swollen by the wind fall in a heap when the mast breaks": he sags and collapses backward
    death = m.animation("death", 3.6)
    death.rot("body", [(0, ZERO), (0.6, (-8, 0, 0)), (1.8, (-70, 0, 6)), (3.6, (-82, 0, 8))])
    death.pos("body", [(0, ZERO), (1.8, (0, -8, 6)), (3.6, (0, -12, 8))])
    death.rot("head", [(0, ZERO), (0.6, (-30, 0, 0)), (1.8, (20, 0, 0)), (3.6, (25, 0, 0))])
    death.rot("jaw", [(0, ZERO), (0.6, (50, 0, 0)), (3.6, (30, 0, 0))])
    arms(death, [(0, ZERO), (0.6, (-30, 0, 60)), (1.8, (-10, 0, 70)), (3.6, (-5, 0, 75))])
    death.event(1.8, "fall")


if __name__ == "__main__":
    build().write()
