#!/usr/bin/env python3
"""The Minotaur, the Infamy of Crete (Inferno XII), on the shattered slope down into the seventh circle: a giant with a
bull's head and a man's body, on a bull's legs and hooves, with a ring through his nose and the broken cuffs of his
chains on his wrists. "When he beheld us, he bit himself, like one whom anger inwardly consumes."

Run from the repo root:  python3 tools/art/minotaur.py && python3 tools/gen_bosses.py

The animations' events are what the server times his fight by (MinotaurFight): "flash" opens the window to block
his horns and "hit" is when they strike; "go" sends him charging; "slam" is his stomp; "spin" .. "spun" his frenzy;
"bite" and "roar" his fury at half health; "rise" and "roar" his entrance; "fall" his death.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

HIDE = material("hide", "hair", light="5a3a26", dark="2a1a10")
SKIN = material("skin", "skin", base="8a5a40", dark="5a3424")
MUZZLE = material("muzzle", "skin", base="3a2a24", dark="1a1210")
HORN = material("horn", "horn", root="d8ccb0", tip="2a2420")
HOOF = material("hoof", "horn", root="3a3430", tip="141210")
CLOTH = material("cloth", "robe", base="5a2a1a", fold="3a160c")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
EYE = hexc("ff3a1a")
EYE_HOT = hexc("ffb070")
NOSTRIL = hexc("0a0606")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (1, w - 3):
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE_HOT)
    for x in range(w):
        f.px(x, 1, shade(HIDE.c["dark"], 0.8))


def snout(faces):
    f = faces["front"]
    f.px(2, 2, NOSTRIL)
    f.px(f.w - 3, 2, NOSTRIL)


def chest_fur(faces):
    f = faces["front"]
    for y in range(f.h // 3):
        for x in range(2, f.w - 2):
            if (x + y) % 2 == 0:
                f.px(x, y, HIDE.c["light"] if (x * 3 + y) % 5 else HIDE.c["dark"])
    for x in range(f.w // 2 - 1, f.w // 2 + 1):
        for y in range(f.h // 3, f.h):
            f.px(x, y, shade(SKIN.c["dark"], 0.9))


def build():
    m = Model("minotaur", texture_width=256)
    hips = m.bone("hips", (0, 30, 0))
    hips.cube("loincloth", (-7, 24, -4), (14, 8, 8), CLOTH)
    tail = hips.bone("tail", (0, 30, 4))
    tail.cube("tail", (-1, 16, 4), (2, 14, 2), HIDE)
    tail.cube("tuft", (-1.5, 13, 3.5), (3, 4, 3), HIDE)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        thigh = hips.bone(f"{side}_thigh", (4 * s, 28, 0))
        thigh.cube(f"{side}_thigh", (x(1, 7), 17, -3.5), (7, 11, 7), HIDE, mirror=s == -1)
        shin = thigh.bone(f"{side}_shin", (4.5 * s, 18, 0))
        shin.cube(f"{side}_shin", (x(2, 5), 5, -1), (5, 13, 5), HIDE, mirror=s == -1)
        shin.cube(f"{side}_hoof", (x(1.5, 6), 0, -2.5), (6, 5, 7), HOOF, mirror=s == -1)
    torso = hips.bone("torso", (0, 32, 0))
    torso.cube("belly", (-7, 32, -4.5), (14, 6, 9), SKIN)
    torso.cube("chest", (-9, 38, -5.5), (18, 12, 11), SKIN, detail=chest_fur)
    torso.cube("hump", (-7, 46, -2), (14, 5, 9), HIDE)
    head = torso.bone("head", (0, 50, -3))
    head.cube("skull", (-5, 48, -11), (10, 10, 10), HIDE, detail=face)
    head.cube("muzzle", (-4, 48, -16), (8, 6, 6), MUZZLE, detail=snout)
    head.cube("ring", (-1.5, 46, -16.5), (3, 3, 1), IRON)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        horn = head.bone(f"{side}_horn", (5 * s, 56, -6))
        horn.cube(f"{side}_horn_root", (x(4, 7), 54, -7.5), (7, 3, 3), HORN, mirror=s == -1)
        horn.cube(f"{side}_horn_tip", (x(9, 3), 56, -8), (3, 7, 3), HORN, mirror=s == -1)
        ear = head.bone(f"{side}_ear", (5 * s, 55, -4))
        ear.cube(f"{side}_ear", (x(4, 4), 52, -4.5), (4, 3, 1), HIDE, mirror=s == -1)
        arm = torso.bone(f"{side}_arm", (11 * s, 47, 0))
        arm.cube(f"{side}_arm", (x(8, 6), 35, -3), (6, 13, 6), SKIN, mirror=s == -1)
        forearm = arm.bone(f"{side}_forearm", (11 * s, 35, 0))
        forearm.cube(f"{side}_forearm", (x(8, 6), 23, -3), (6, 12, 6), SKIN, mirror=s == -1)
        forearm.cube(f"{side}_cuff", (x(7.5, 7), 25, -3.5), (7, 3, 7), IRON, mirror=s == -1)
        forearm.cube(f"{side}_fist", (x(7.5, 7), 17, -3.5), (7, 6, 7), SKIN, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # breathing hard, snorting, the tail lashing
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("torso", [(0, ZERO), (1.5, (0, 0.8, 0)), (3.0, ZERO)])
    idle.rot("head", [(0, (5, 0, 0)), (0.8, (-5, 0, 0)), (1.0, (8, 0, 0)), (3.0, (5, 0, 0))])
    idle.rot("tail", [(0, (20, 0, 0)), (1.0, (20, 20, 0)), (2.0, (20, -20, 0)), (3.0, (20, 0, 0))])
    idle.rot("right_arm", [(0, (0, 0, 10)), (1.5, (0, 0, 14)), (3.0, (0, 0, 10))])
    idle.rot("left_arm", [(0, (0, 0, -10)), (1.5, (0, 0, -14)), (3.0, (0, 0, -10))])

    # heavy strides on the hooves
    walk = m.animation("walk", 1.2, loop=True)
    walk.rot("right_thigh", [(0, (30, 0, 0)), (0.6, (-30, 0, 0)), (1.2, (30, 0, 0))])
    walk.rot("left_thigh", [(0, (-30, 0, 0)), (0.6, (30, 0, 0)), (1.2, (-30, 0, 0))])
    walk.rot("right_shin", [(0, (-10, 0, 0)), (0.3, (35, 0, 0)), (0.6, (0, 0, 0)), (1.2, (-10, 0, 0))])
    walk.rot("left_shin", [(0, (0, 0, 0)), (0.6, (-10, 0, 0)), (0.9, (35, 0, 0)), (1.2, (0, 0, 0))])
    walk.rot("right_arm", [(0, (-25, 0, 10)), (0.6, (25, 0, 10)), (1.2, (-25, 0, 10))])
    walk.rot("left_arm", [(0, (25, 0, -10)), (0.6, (-25, 0, -10)), (1.2, (25, 0, -10))])
    walk.pos("hips", [(0, ZERO), (0.3, (0, -1, 0)), (0.6, ZERO), (0.9, (0, -1, 0)), (1.2, ZERO)])

    # the horns: head drawn back, a white flash, then the thrust up through you
    gore = m.animation("gore", 1.6)
    gore.rot("torso", [(0, ZERO), (0.6, (-20, 0, 0)), (0.95, (-25, 0, 0)), (1.1, (35, 0, 0)), (1.6, ZERO)])
    gore.rot("head", [(0, ZERO), (0.6, (-25, 0, 0)), (1.0, (-30, 0, 0)), (1.1, (40, 0, 0)), (1.25, (-30, 0, 0)), (1.6, ZERO)])
    gore.rot("right_arm", [(0, ZERO), (0.6, (-40, 0, 40)), (1.1, (20, 0, 20)), (1.6, ZERO)])
    gore.rot("left_arm", [(0, ZERO), (0.6, (-40, 0, -40)), (1.1, (20, 0, -20)), (1.6, ZERO)])
    gore.pos("hips", [(0, ZERO), (0.6, (0, -3, 4)), (1.1, (0, -1, -6)), (1.6, ZERO)])
    gore.event(0.55, "flash").event(1.1, "hit")

    # the charge: he paws the ground, lowers his horns, and goes
    charge = m.animation("charge", 1.8)
    charge.rot("right_thigh", [(0, ZERO), (0.25, (-30, 0, 0)), (0.45, (10, 0, 0)), (0.7, (-30, 0, 0)), (0.9, (10, 0, 0)), (1.8, ZERO)])
    charge.rot("torso", [(0, ZERO), (1.0, (40, 0, 0)), (1.8, (40, 0, 0))])
    charge.rot("head", [(0, ZERO), (1.0, (20, 0, 0)), (1.8, (20, 0, 0))])
    charge.rot("right_arm", [(0, ZERO), (1.0, (40, 0, 30)), (1.8, (40, 0, 30))])
    charge.rot("left_arm", [(0, ZERO), (1.0, (40, 0, -30)), (1.8, (40, 0, -30))])
    charge.pos("hips", [(0, ZERO), (1.0, (0, -4, 0)), (1.8, (0, -4, 0))])
    charge.event(1.2, "go")

    # the stomp: up on one hoof, then down, both fists with it
    stomp = m.animation("stomp", 1.8)
    stomp.rot("right_thigh", [(0, ZERO), (0.7, (-70, 0, 0)), (1.0, (-75, 0, 0)), (1.15, (10, 0, 0)), (1.8, ZERO)])
    stomp.rot("right_shin", [(0, ZERO), (0.7, (60, 0, 0)), (1.15, ZERO)])
    stomp.rot("right_arm", [(0, ZERO), (0.8, (-160, 0, 20)), (1.15, (-20, 0, 10)), (1.8, ZERO)])
    stomp.rot("left_arm", [(0, ZERO), (0.8, (-160, 0, -20)), (1.15, (-20, 0, -10)), (1.8, ZERO)])
    stomp.rot("torso", [(0, ZERO), (0.8, (-15, 0, 0)), (1.15, (25, 0, 0)), (1.8, ZERO)])
    stomp.pos("hips", [(0, ZERO), (0.8, (0, 3, 0)), (1.15, (0, -4, 0)), (1.8, ZERO)])
    stomp.event(1.15, "slam")

    # the frenzy: arms flung wide, he whirls right round
    frenzy = m.animation("frenzy", 2.6)
    frenzy.rot("right_arm", [(0, ZERO), (0.6, (0, 0, 85)), (2.2, (0, 0, 85)), (2.6, ZERO)])
    frenzy.rot("left_arm", [(0, ZERO), (0.6, (0, 0, -85)), (2.2, (0, 0, -85)), (2.6, ZERO)])
    frenzy.rot("hips", [(0, ZERO), (0.9, (0, -30, 0)), (1.3, (0, -150, 0)), (1.7, (0, -270, 0)), (2.0, (0, -360, 0)), (2.6, (0, -360, 0))],
               interp="linear")
    frenzy.rot("head", [(0, ZERO), (0.6, (-20, 0, 0)), (2.0, (-20, 0, 0)), (2.6, ZERO)])
    frenzy.event(0.9, "spin").event(2.0, "spun")

    # reels after a blocked thrust or a charge into the rock: on one knee, shaking his head
    stagger = m.animation("stagger", 2.4)
    stagger.pos("hips", [(0, ZERO), (0.3, (0, -9, 2)), (2.0, (0, -9, 2)), (2.4, ZERO)])
    stagger.rot("right_thigh", [(0, ZERO), (0.3, (-80, 0, 0)), (2.0, (-80, 0, 0)), (2.4, ZERO)])
    stagger.rot("right_shin", [(0, ZERO), (0.3, (90, 0, 0)), (2.0, (90, 0, 0)), (2.4, ZERO)])
    stagger.rot("torso", [(0, ZERO), (0.3, (30, 0, 0)), (2.0, (30, 0, 0)), (2.4, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.6, (20, -25, 0)), (0.9, (20, 25, 0)), (1.2, (20, -25, 0)), (1.5, (20, 25, 0)), (2.4, ZERO)])
    stagger.rot("right_arm", [(0, ZERO), (0.3, (-40, 0, 10)), (2.0, (-40, 0, 10)), (2.4, ZERO)])

    # his fury at half health: he bites his own arm, then bellows
    phase = m.animation("phase", 3.0)
    phase.rot("left_arm", [(0, ZERO), (0.5, (-110, 30, -10)), (1.4, (-110, 30, -10)), (1.8, ZERO)])
    phase.rot("left_forearm", [(0, ZERO), (0.5, (-60, 0, 0)), (1.4, (-60, 0, 0)), (1.8, ZERO)])
    phase.rot("head", [(0, ZERO), (0.5, (30, -30, 0)), (0.8, (40, -35, 0)), (1.1, (30, -30, 0)), (1.4, (40, -35, 0)), (1.9, (-45, 0, 0)),
                       (2.7, (-45, 0, 0)), (3.0, ZERO)])
    phase.rot("torso", [(0, ZERO), (1.9, (-20, 0, 0)), (2.7, (-20, 0, 0)), (3.0, ZERO)])
    phase.rot("right_arm", [(0, ZERO), (1.9, (-30, 0, 70)), (2.7, (-30, 0, 70)), (3.0, ZERO)])
    phase.event(0.6, "bite").event(1.9, "roar")

    # his entrance: crouched over a carcass, he rears up and bellows
    intro = m.animation("intro", 5.0)
    intro.pos("hips", [(0, (0, -10, 3)), (2.0, (0, -10, 3)), (2.8, ZERO), (5.0, ZERO)])
    intro.rot("torso", [(0, (50, 0, 0)), (2.0, (50, 0, 0)), (2.8, (-15, 0, 0)), (4.2, (-15, 0, 0)), (5.0, ZERO)])
    intro.rot("right_thigh", [(0, (-70, 0, 0)), (2.0, (-70, 0, 0)), (2.8, ZERO)])
    intro.rot("right_shin", [(0, (80, 0, 0)), (2.0, (80, 0, 0)), (2.8, ZERO)])
    intro.rot("left_thigh", [(0, (-50, 0, 0)), (2.0, (-50, 0, 0)), (2.8, ZERO)])
    intro.rot("left_shin", [(0, (60, 0, 0)), (2.0, (60, 0, 0)), (2.8, ZERO)])
    intro.rot("head", [(0, (20, 0, 0)), (1.0, (20, 20, 0)), (2.0, (20, 20, 0)), (2.8, (-40, 0, 0)), (4.2, (-40, 0, 0)), (5.0, ZERO)])
    intro.rot("right_arm", [(0, (-60, 0, 0)), (2.0, (-60, 0, 0)), (2.8, (-20, 0, 80)), (4.2, (-20, 0, 80)), (5.0, ZERO)])
    intro.rot("left_arm", [(0, (-60, 0, 0)), (2.0, (-60, 0, 0)), (2.8, (-20, 0, -80)), (4.2, (-20, 0, -80)), (5.0, ZERO)])
    intro.event(2.0, "rise").event(2.9, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (-10, 0, 0)), (0.4, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.12, (-15, 0, 0)), (0.4, ZERO)])

    # he topples forward onto his horns
    death = m.animation("death", 3.0)
    death.pos("hips", [(0, ZERO), (0.8, (0, -8, 0)), (1.8, (0, -18, -12)), (3.0, (0, -24, -18))])
    death.rot("hips", [(0, ZERO), (0.8, (10, 0, 0)), (1.8, (70, 0, 0)), (3.0, (85, 0, 0))])
    death.rot("right_thigh", [(0, ZERO), (0.8, (-70, 0, 0)), (3.0, (-80, 0, 0))])
    death.rot("left_thigh", [(0, ZERO), (0.8, (-60, 0, 0)), (3.0, (-75, 0, 0))])
    death.rot("right_arm", [(0, ZERO), (1.8, (-150, 0, 20)), (3.0, (-160, 0, 20))])
    death.rot("left_arm", [(0, ZERO), (1.8, (-150, 0, -20)), (3.0, (-160, 0, -20))])
    death.event(1.8, "fall")


if __name__ == "__main__":
    build().write()
