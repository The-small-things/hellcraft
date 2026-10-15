#!/usr/bin/env python3
"""The Malebranche, the Evil-Claws (Inferno XXI-XXII), the gang of devils who keep the fifth ditch of Malebolge, where
the grafters boil in pitch: black, leathery, horned and bat-winged, each with a long grappling hook ("more than a
hundred hooks") to drag back under whoever surfaces. Their captain is Malacoda (tools/art/malacoda.py).

Run from the repo root:  python3 tools/art/malebranche.py && python3 tools/gen_bosses.py

The attack is the hook: a swing that catches you and drags you in ("hook" is when it bites); the special is their
jeering (they "make a trumpet of their rumps", which we leave out) with their hooks raised.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

HIDE = material("hide", "skin", base="2a2224", dark="140e10")
HORN = material("horn", "horn", root="8a7a60", tip="1a1410")
WING = material("wing", "hair", light="3a2224", dark="140808")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
SHAFT = material("shaft", "hair", light="5a4a32", dark="2a2014")
EYE = hexc("ffd23a")
GRIN = hexc("e8dcc0")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (1, w - 3):
        f.glow(cx, 2, EYE)
    for x in range(1, w - 1):
        f.px(x, f.h - 2, GRIN if x % 2 else shade(HIDE.c["dark"], 0.5))


def build():
    m = Model("malebranche", texture_width=128)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_leg", (x(0, 3), 0, -1.5), (3, 12, 3), HIDE, mirror=s == -1)
    body = m.bone("body", (0, 12, 0), rotation=(-8, 0, 0))
    body.cube("torso", (-4, 12, -2), (8, 11, 4), HIDE)
    tail = body.bone("tail", (0, 13, 2))
    tail.cube("tail", (-0.5, 4, 2), (1, 9, 1), HIDE)
    head = body.bone("head", (0, 23, 0))
    head.cube("head", (-3, 23, -3), (6, 6, 6), HIDE, detail=face)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        horn = head.bone(f"{side}_horn", (2.5 * s, 29, -1))
        horn.cube(f"{side}_horn", (x(1.5, 2), 28, -2), (2, 4, 2), HORN, mirror=s == -1)
        wing = body.bone(f"{side}_wing", (2 * s, 21, 2))
        wing.cube(f"{side}_wing", (x(2, 10), 11, 2), (10, 10, 1), WING, mirror=s == -1)
        arm = body.bone(f"{side}_arm", (5 * s, 22, 0))
        arm.cube(f"{side}_arm", (x(4, 3), 11, -1.5), (3, 11, 3), HIDE, mirror=s == -1)
    # the hook, in the right hand: a long shaft, an iron hook at the top
    hook = body.bone("hook", (5.5, 12.5, 0))
    hook.cube("shaft", (5, 0, -7), (1, 1, 26), SHAFT)
    hook.cube("hook_bar", (5, 0, -10), (1, 4, 1), IRON)
    hook.cube("hook_tip", (5, 3, -9), (1, 1, 2), IRON)
    animations(m)
    return m


ZERO = (0, 0, 0)
HOLD = (-50, 0, 0)


def animations(m):
    idle = m.animation("idle", 1.6, loop=True)
    idle.rot("right_arm", [(0, HOLD), (1.6, HOLD)])
    idle.rot("hook", [(0, (-50, 0, 0)), (1.6, (-50, 0, 0))])
    idle.rot("tail", [(0, (30, 0, 0)), (0.8, (30, 20, 0)), (1.6, (30, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        idle.rot(f"{side}_wing", [(0, (0, -30 * s, 0)), (0.8, (0, -40 * s, 0)), (1.6, (0, -30 * s, 0))])

    walk = m.animation("walk", 0.8, loop=True)
    walk.rot("right_leg", [(0, (35, 0, 0)), (0.4, (-35, 0, 0)), (0.8, (35, 0, 0))])
    walk.rot("left_leg", [(0, (-35, 0, 0)), (0.4, (35, 0, 0)), (0.8, (-35, 0, 0))])
    walk.rot("left_arm", [(0, (30, 0, 0)), (0.4, (-30, 0, 0)), (0.8, (30, 0, 0))])

    # the hook: wound back over the shoulder, swung down, and yanked back
    attack = m.animation("attack", 1.0)
    attack.rot("right_arm", [(0, HOLD), (0.35, (-170, 0, 10)), (0.55, (-40, 0, 0)), (0.8, (-90, 0, 0)), (1.0, HOLD)])
    attack.rot("hook", [(0, (-50, 0, 0)), (0.35, (-150, 0, 0)), (0.55, (10, 0, 0)), (0.8, (-60, 0, 0)), (1.0, (-50, 0, 0))])
    attack.rot("body", [(0, ZERO), (0.35, (-15, 20, 0)), (0.55, (15, -10, 0)), (0.8, (-10, 0, 0)), (1.0, ZERO)])
    attack.event(0.55, "hook")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (-15, 0, 0)), (0.3, ZERO)])

    # the jeer: hooks raised, wings flared, cackling
    jeer = m.animation("jeer", 1.4)
    jeer.rot("right_arm", [(0, HOLD), (0.3, (-170, 0, 0)), (1.1, (-170, 0, 0)), (1.4, HOLD)])
    jeer.rot("hook", [(0, (-50, 0, 0)), (0.3, (-90, 0, 0)), (1.1, (-90, 0, 0)), (1.4, (-50, 0, 0))])
    jeer.rot("head", [(0, ZERO), (0.3, (-30, 0, 0)), (0.5, (-15, 0, 0)), (0.7, (-30, 0, 0)), (0.9, (-15, 0, 0)), (1.4, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        jeer.rot(f"{side}_wing", [(0, (0, -30 * s, 0)), (0.3, (0, 10 * s, -30 * s)), (1.1, (0, 10 * s, -30 * s)), (1.4, (0, -30 * s, 0))])

    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.5, (60, 0, 10)), (1.2, (80, 0, 10))])
    death.pos("body", [(0, ZERO), (0.5, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.rot("right_leg", [(0, ZERO), (1.2, (-50, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (1.2, (-45, 0, 0))])


if __name__ == "__main__":
    build().write()
