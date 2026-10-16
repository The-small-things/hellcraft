#!/usr/bin/env python3
"""Lucifer as he first shows himself (Inferno XXXIV; Isaiah's Morning Star): the Fallen Seraph, the creature that "was
so fair as now he foul is". Tall, pale and calm, in the rags of a white and gold robe, six wings (three pairs, the way
of the seraphim) gone grey at the edges, a cracked halo of cold light, and a long blade of the same light.

Run from the repo root:  python3 tools/art/fallen_seraph.py && python3 tools/gen_bosses.py

He is drawn 1.6 times his Blockbench size (about 5 blocks tall). The events time his fight (LuciferFight):
"flash" opens the window to block his blade and "hit" is when it lands; "sweep" and "swept" bound his wings' sweep;
"call" brings the falling stars; "go" his lunge; "snap" the moment he stops pretending (his half-health turn);
"land" and "speak" his descent; "break" the moment his seraph form shatters.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="e8dcd0", dark="a89888")
ROBE = material("robe", "robe", base="e8e4d8", fold="a8a08c")
GOLD = material("gold", "metal", base="c8a040", high="f0d070", low="7a5a18")
HAIR = material("hair", "hair", light="f0e8c8", dark="a89868")
WING = material("wing", "hair", light="f4f0e8", dark="8a8478")
BLADE = material("blade", "flat", base="d8f0ff")
HALO = hexc("fff0b0")
LIGHT = hexc("c8f0ff")
EYE = hexc("8ad8ff")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (1, w - 3):
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE)
    for x in range(2, w - 2):
        f.px(x, f.h - 2, shade(SKIN.c["dark"], 0.8))


def glowing(colour):
    def paint(faces):
        for name in ("front", "back", "left", "right", "top", "bottom"):
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    f.glow(x, y, colour)
    return paint


def frayed(faces):
    """Grey, ragged ends: what falling did to them."""
    for name in ("front", "back"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if y > f.h - 4 and (x + y) % 2 == 0:
                    f.px(x, y, (0, 0, 0), alpha=0)
                elif x > f.w - 4:
                    f.px(x, y, shade(WING.c["dark"], 0.8))


def build():
    m = Model("fallen_seraph", texture_width=128)
    root = m.bone("root", (0, 0, 0))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = root.bone(f"{side}_leg", (2 * s, 18, 0))
        leg.cube(f"{side}_leg", (x(0, 4), 0, -2), (4, 18, 4), ROBE, mirror=s == -1)
    body = root.bone("body", (0, 18, 0))
    body.cube("robe", (-5, 6, -3), (10, 12, 6), ROBE)
    body.cube("girdle", (-4.5, 17, -2.5), (9, 2, 5), GOLD, inflate=0.2)
    torso = body.bone("torso", (0, 19, 0))
    torso.cube("torso", (-4, 19, -2), (8, 12, 4), ROBE)
    torso.cube("breastplate", (-4, 23, -2.5), (8, 6, 1), GOLD)
    head = torso.bone("head", (0, 31, 0))
    head.cube("head", (-3, 31, -3), (6, 7, 6), SKIN, detail=face)
    head.cube("hair", (-3.5, 34, -3), (7, 5, 7), HAIR, inflate=0.2)
    # the cracked halo: four bars of cold light round his head, one broken short
    halo = head.bone("halo", (0, 41, 1))
    halo.cube("halo_top", (-4, 42, 1), (8, 1, 1), BLADE, detail=glowing(HALO))
    halo.cube("halo_left", (-5, 38, 1), (1, 4, 1), BLADE, detail=glowing(HALO))
    halo.cube("halo_right", (4, 39, 1), (1, 3, 1), BLADE, detail=glowing(HALO))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = torso.bone(f"{side}_arm", (5 * s, 30, 0))
        arm.cube(f"{side}_arm", (x(4, 3), 19, -1.5), (3, 11, 3), ROBE, mirror=s == -1)
        arm.cube(f"{side}_hand", (x(4, 3), 17, -1.5), (3, 2, 3), SKIN, mirror=s == -1)
        # three pairs of wings: high, middle, low
        for i, (y, w, h, spread) in enumerate([(30, 16, 7, 20), (26, 18, 8, 0), (21, 14, 7, -20)]):
            wing = torso.bone(f"{side}_wing{i}", (2 * s, y, 2), rotation=(0, 0, spread * s))
            wing.cube(f"{side}_wing{i}", (x(2, w), y - h + 2, 2), (w, h, 1), WING, mirror=s == -1, detail=frayed)
    # the blade of light, held in his right hand
    blade = torso.bone("blade", (6.5, 18, 0))
    blade.cube("hilt", (5.5, 16, -0.5), (2, 3, 1), GOLD)
    blade.cube("blade", (6, 2, -0.5), (1, 14, 1), BLADE, detail=glowing(LIGHT))
    animations(m)
    return m


ZERO = (0, 0, 0)
ARM_R = (-15, 0, 10)
ARM_L = (-15, 0, -10)


def beat(a, length, amount=12):
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            a.rot(f"{side}_wing{i}", [(0, (0, -10 * s, 0)), (length * 0.5, (0, 10 * s, amount * s)), (length, (0, -10 * s, 0))])


def animations(m):
    # hovering a little off the ice, calm, his wings breathing
    idle = m.animation("idle", 3.0, loop=True)
    beat(idle, 3.0, 8)
    idle.pos("root", [(0, (0, 2, 0)), (1.5, (0, 3, 0)), (3.0, (0, 2, 0))])
    idle.rot("right_arm", [(0, ARM_R), (3.0, ARM_R)])
    idle.rot("left_arm", [(0, ARM_L), (3.0, ARM_L)])
    idle.rot("blade", [(0, (-15, 0, 0)), (3.0, (-15, 0, 0))])

    # the blade: raised high, a flash, and down across you
    slash = m.animation("slash", 1.6)
    slash.rot("right_arm", [(0, ARM_R), (0.5, (-170, 0, 20)), (0.85, (-175, 0, 20)), (1.0, (-40, 0, -20)), (1.6, ARM_R)])
    slash.rot("blade", [(0, (-15, 0, 0)), (0.5, (-170, 0, 20)), (0.85, (-175, 0, 20)), (1.0, (-40, 0, -20)), (1.6, (-15, 0, 0))])
    slash.rot("torso", [(0, ZERO), (0.5, (-10, 20, 0)), (1.0, (20, -30, 0)), (1.6, ZERO)])
    slash.event(0.5, "flash").event(0.95, "hit")

    # the wings sweep round him, low over the ice
    sweep = m.animation("sweep", 1.8)
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            sweep.rot(f"{side}_wing{i}", [(0, ZERO), (0.5, (0, -50 * s, -30 * s)), (1.3, (0, 60 * s, 40 * s)), (1.8, ZERO)])
    sweep.rot("root", [(0, ZERO), (0.5, (0, -60, 0)), (1.3, (0, 300, 0)), (1.8, (0, 360, 0))], interp="linear")
    sweep.event(0.6, "sweep").event(1.3, "swept")

    # the falling stars: his hand lifted, light called down
    stars = m.animation("stars", 2.0)
    beat(stars, 1.0, 20)
    stars.rot("left_arm", [(0, ARM_L), (0.6, (-180, 0, -10)), (1.6, (-180, 0, -10)), (2.0, ARM_L)])
    stars.rot("head", [(0, ZERO), (0.6, (-35, 0, 0)), (1.6, (-35, 0, 0)), (2.0, ZERO)])
    stars.event(0.7, "call")

    # the lunge: he draws back, then crosses the ice like light
    lunge = m.animation("lunge", 1.4)
    lunge.rot("body", [(0, ZERO), (0.6, (-20, 0, 0)), (0.8, (35, 0, 0)), (1.4, ZERO)])
    lunge.rot("right_arm", [(0, ARM_R), (0.6, (-60, 0, 30)), (0.8, (-90, 0, 0)), (1.4, ARM_R)])
    lunge.rot("blade", [(0, (-15, 0, 0)), (0.6, (-60, 0, 30)), (0.8, (-90, 0, 0)), (1.4, (-15, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            lunge.rot(f"{side}_wing{i}", [(0, ZERO), (0.6, (0, 30 * s, 20 * s)), (0.8, (0, -50 * s, -20 * s)), (1.4, ZERO)])
    lunge.event(0.7, "go")

    # parried: he reels, blade low, wings askew
    stagger = m.animation("stagger", 2.0)
    stagger.rot("torso", [(0, ZERO), (0.3, (-30, 0, 20)), (1.6, (-30, 0, 20)), (2.0, ZERO)])
    stagger.rot("right_arm", [(0, ARM_R), (0.3, (10, 0, 60)), (1.6, (10, 0, 60)), (2.0, ARM_R)])
    stagger.pos("root", [(0, (0, 2, 0)), (0.3, (0, -2, 0)), (1.6, (0, -2, 0)), (2.0, (0, 2, 0))])

    # half health: he stops pretending. The face hardens, the wings flare and the halo cracks wider.
    snap = m.animation("snap", 2.6)
    snap.rot("head", [(0, ZERO), (0.6, (30, 0, 0)), (1.0, (-40, 0, 0)), (2.2, (-40, 0, 0)), (2.6, ZERO)])
    snap.rot("halo", [(0, ZERO), (1.0, (0, 0, 25)), (2.6, (0, 0, 25))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            snap.rot(f"{side}_wing{i}", [(0, ZERO), (1.0, (0, -40 * s, -40 * s)), (2.2, (0, -40 * s, -40 * s)), (2.6, ZERO)])
        snap.rot(f"{side}_arm", [(0, ARM_R if s == 1 else ARM_L), (1.0, (-20, 0, 80 * s)), (2.2, (-20, 0, 80 * s)), (2.6, ARM_R if s == 1 else ARM_L)])
    snap.event(1.0, "snap")

    # his descent: down out of the dark to the ice, wings folding, then he speaks
    intro = m.animation("intro", 5.0)
    intro.pos("root", [(0, (0, 60, 0)), (3.0, (0, 2, 0)), (5.0, (0, 2, 0))])
    beat(intro, 1.0, 25)
    intro.rot("head", [(0, (20, 0, 0)), (3.0, (20, 0, 0)), (3.6, ZERO), (5.0, ZERO)])
    intro.rot("left_arm", [(0, ARM_L), (3.6, ARM_L), (4.0, (-90, 0, -30)), (5.0, (-90, 0, -30))])
    intro.event(3.0, "land").event(3.6, "speak")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("torso", [(0, ZERO), (0.1, (-15, 0, 8)), (0.3, ZERO)])

    # his seraph form breaks: arms flung out, head thrown back, then he falls to pieces (the server shows the shards)
    death = m.animation("death", 2.4)
    death.rot("head", [(0, ZERO), (0.8, (-60, 0, 0)), (2.4, (-60, 0, 0))])
    death.rot("right_arm", [(0, ARM_R), (0.8, (-20, 0, 100)), (2.4, (-20, 0, 100))])
    death.rot("left_arm", [(0, ARM_L), (0.8, (-20, 0, -100)), (2.4, (-20, 0, -100))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            death.rot(f"{side}_wing{i}", [(0, ZERO), (0.8, (0, -60 * s, -50 * s)), (2.4, (0, -60 * s, -50 * s))])
    death.scale("root", [(0, (1, 1, 1)), (1.6, (1, 1, 1)), (2.4, (0.05, 0.05, 0.05))], interp="linear")
    death.event(1.6, "break")


if __name__ == "__main__":
    build().write()
