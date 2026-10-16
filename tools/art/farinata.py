#!/usr/bin/env python3
"""Farinata degli Uberti (Inferno X), the Ghibelline captain, risen in his burning tomb "from the girdle upward...
with his breast and his forehead, as if he held Hell in great disdain". A proud old soldier in a scarlet Florentine
gown and cap over his mail, his tomb's fire licking at his skirts, a sword at his side he never draws.

Run from the repo root:  python3 tools/art/farinata.py && python3 tools/gen_bosses.py

He stands in his tomb (the fight builds it round him: only his upper body shows over the rim). The events time his
fight (FarinataFight): "flash" opens the window to block his Disdain and "hit" is when his fist strikes; "blast" is
his Exile; "fire" is when he commands the tombs round him; "sink" is when he slumps back into his tomb; "roar" his
fury; "rise" and "speak" his entrance.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

FACE = material("face", "skin", base="b08a6a", dark="7a5a44")
GOWN = material("gown", "robe", base="8a1a1a", fold="5a0e0e", trim="c8a040")
MAIL = material("mail", "metal", base="6a6a70", high="9a9aa0", low="3a3a40")
CAP = material("cap", "robe", base="6a1010", fold="3a0808")
HAIR = material("hair", "hair", light="c8c0b0", dark="7a7468")
STEEL = material("steel", "metal", base="8a8a90", high="d0d0d8", low="4a4a50")
LEATHER = material("leather", "flat", base="3a2414")
EYE = hexc("ffb040")
EMBER = hexc("ff7a1a")
EMBER_HOT = hexc("ffd25a")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (1, w - 3):
        f.px(cx, 3, shade(FACE.c["dark"], 0.5))
        f.glow(cx + 1, 3, EYE)
    # the stern mouth, and a white beard
    for x in range(2, w - 2):
        f.px(x, 6, shade(FACE.c["dark"], 0.6))
    for y in range(f.h - 2, f.h):
        for x in range(1, w - 1):
            f.px(x, y, HAIR.c["light"] if (x + y) % 2 else HAIR.c["dark"])


def scorched(faces):
    """The gown's hem glows where the tomb's fire licks it."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            for y in range(f.h - 3, f.h):
                if (x + y) % 3 != 0:
                    f.glow(x, y, EMBER if (x * 7 + y) % 4 else EMBER_HOT)


def build():
    m = Model("farinata", texture_width=128)
    body = m.bone("body", (0, 12, 0))
    body.cube("skirt", (-5, 0, -3), (10, 12, 6), GOWN, detail=scorched)
    body.cube("belt", (-5, 11, -3), (10, 2, 6), LEATHER, inflate=0.2)
    torso = body.bone("torso", (0, 13, 0))
    torso.cube("torso", (-4.5, 13, -2.5), (9, 12, 5), GOWN)
    torso.cube("mail_collar", (-4, 22, -3), (8, 3, 6), MAIL, inflate=0.2)
    torso.cube("sword", (5, 4, -1), (1, 12, 2), STEEL)
    head = torso.bone("head", (0, 25, 0))
    head.cube("head", (-3.5, 25, -3.5), (7, 8, 7), FACE, detail=face)
    head.cube("cap", (-4, 31, -4), (8, 3, 8), CAP, inflate=0.2)
    head.cube("cap_brim", (-4.5, 30, -4.5), (9, 1, 9), CAP)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = torso.bone(f"{side}_arm", (5.5 * s, 24, 0))
        arm.cube(f"{side}_arm", (x(4.5, 3), 14, -1.5), (3, 11, 3), GOWN, mirror=s == -1)
        arm.cube(f"{side}_hand", (x(4.5, 3), 12, -1.5), (3, 2, 3), FACE, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)
CROSSED = {"right_arm": (70, -40, 0), "left_arm": (75, 40, 0)}


def animations(m):
    # proud and still, arms folded, looking down on you
    idle = m.animation("idle", 4.0, loop=True)
    idle.rot("right_arm", [(0, CROSSED["right_arm"]), (4.0, CROSSED["right_arm"])])
    idle.rot("left_arm", [(0, CROSSED["left_arm"]), (4.0, CROSSED["left_arm"])])
    idle.rot("head", [(0, (10, 0, 0)), (2.0, (12, 10, 0)), (4.0, (10, 0, 0))])
    idle.pos("torso", [(0, ZERO), (2.0, (0, 0.4, 0)), (4.0, ZERO)])

    # Disdain: he raises his fist, it flashes white, and he strikes the tomb's rim: fire runs along the ground at you
    disdain = m.animation("disdain", 1.6)
    disdain.rot("right_arm", [(0, CROSSED["right_arm"]), (0.5, (170, 0, 10)), (0.95, (175, 0, 10)), (1.1, (40, 0, 0)), (1.6, CROSSED["right_arm"])])
    disdain.rot("left_arm", [(0, CROSSED["left_arm"]), (0.4, (20, 0, -20)), (1.6, CROSSED["left_arm"])])
    disdain.rot("torso", [(0, ZERO), (0.5, (10, 0, 0)), (1.1, (-25, 0, 0)), (1.6, ZERO)])
    disdain.event(0.5, "flash").event(1.1, "hit")

    # Exile: arms flung wide, the air round him blasts outward
    exile = m.animation("exile", 2.0)
    exile.rot("right_arm", [(0, CROSSED["right_arm"]), (0.6, (20, 0, 100)), (1.0, (10, 0, 110)), (1.6, (10, 0, 110)), (2.0, CROSSED["right_arm"])])
    exile.rot("left_arm", [(0, CROSSED["left_arm"]), (0.6, (20, 0, -100)), (1.0, (10, 0, -110)), (1.6, (10, 0, -110)), (2.0, CROSSED["left_arm"])])
    exile.rot("head", [(0, ZERO), (0.6, (30, 0, 0)), (1.6, (30, 0, 0)), (2.0, ZERO)])
    exile.pos("torso", [(0, ZERO), (0.6, (0, 2, 0)), (1.6, (0, 2, 0)), (2.0, ZERO)])
    exile.event(1.0, "blast")

    # the command: he points, and the tombs round him answer
    command = m.animation("command", 1.6)
    command.rot("right_arm", [(0, CROSSED["right_arm"]), (0.5, (90, -20, 0)), (1.2, (90, 20, 0)), (1.6, CROSSED["right_arm"])])
    command.rot("torso", [(0, ZERO), (0.5, (0, -20, 0)), (1.2, (0, 20, 0)), (1.6, ZERO)])
    command.event(0.6, "fire")

    # blocked: he sinks back into his tomb, his cap knocked askew
    stagger = m.animation("stagger", 2.4)
    stagger.pos("body", [(0, ZERO), (0.4, (0, -14, 0)), (2.0, (0, -14, 0)), (2.4, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.4, (-20, 0, 20)), (2.0, (-20, 0, 20)), (2.4, ZERO)])
    stagger.rot("right_arm", [(0, CROSSED["right_arm"]), (0.4, (160, 0, 30)), (2.0, (160, 0, 30)), (2.4, CROSSED["right_arm"])])
    stagger.rot("left_arm", [(0, CROSSED["left_arm"]), (0.4, (160, 0, -30)), (2.0, (160, 0, -30)), (2.4, CROSSED["left_arm"])])
    stagger.event(0.4, "sink")

    # his fury: he rises higher out of the fire and bellows at the city that exiled him
    phase = m.animation("phase", 2.6)
    phase.pos("body", [(0, ZERO), (0.8, (0, 6, 0)), (2.2, (0, 6, 0)), (2.6, ZERO)])
    phase.rot("right_arm", [(0, CROSSED["right_arm"]), (0.8, (160, 0, 40)), (2.2, (160, 0, 40)), (2.6, CROSSED["right_arm"])])
    phase.rot("left_arm", [(0, CROSSED["left_arm"]), (0.8, (160, 0, -40)), (2.2, (160, 0, -40)), (2.6, CROSSED["left_arm"])])
    phase.rot("head", [(0, ZERO), (0.8, (40, 0, 0)), (2.2, (40, 0, 0)), (2.6, ZERO)])
    phase.event(1.0, "roar")

    # his entrance: he rises out of his tomb and asks who your ancestors were
    intro = m.animation("intro", 5.0)
    intro.pos("body", [(0, (0, -30, 0)), (1.0, (0, -30, 0)), (2.6, ZERO), (5.0, ZERO)])
    intro.rot("right_arm", [(0, ZERO), (2.6, ZERO), (3.4, CROSSED["right_arm"]), (5.0, CROSSED["right_arm"])])
    intro.rot("left_arm", [(0, ZERO), (2.6, ZERO), (3.4, CROSSED["left_arm"]), (5.0, CROSSED["left_arm"])])
    intro.rot("head", [(0, (-20, 0, 0)), (2.6, (15, 0, 0)), (5.0, (10, 0, 0))])
    intro.event(1.2, "rise").event(3.4, "speak")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])

    # beaten, he sinks back into the fire, still proud
    death = m.animation("death", 3.0)
    death.pos("body", [(0, ZERO), (1.0, (0, -4, 0)), (3.0, (0, -32, 0))])
    death.rot("head", [(0, ZERO), (1.0, (20, 0, 0)), (3.0, (20, 0, 0))])
    death.event(1.4, "sink")


if __name__ == "__main__":
    build().write()
