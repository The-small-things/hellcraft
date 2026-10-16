#!/usr/bin/env python3
"""Vulcan, smith of the gods (Inferno XIV: "if he weary his smiths one after another at the black forge of
Mongibello"), at the Great Forge of Dis: a huge lame smith, soot-black skin flecked with embers, a leather apron, an
iron brace on his crippled left leg, a smouldering beard and eyes like coals, his great hammer in his right hand and a
coil of chain in his left.

Run from the repo root:  python3 tools/art/vulcan.py && python3 tools/gen_bosses.py

The animations' events are what the server times his fight by (VulcanFight): "flash" opens the window to block his
hammer and "hit" is when it lands; "leap" and "land" his Hammerfall; "pour" his molten rain; "throw" his chains; "ring"
the anvil-blow that calls his smiths; "plunge" and "roar" his rage at half health; "clang" and "roar" his entrance at
the anvil; "fall" his death.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="6a4232", dark="361e14")
LEATHER = material("leather", "robe", base="5a3a20", fold="3a2410", trim="8a6a3a")
BEARD = material("beard", "hair", light="3a2a24", dark="140c08")
IRON = material("iron", "metal", base="3e3e44", high="6e6e78", low="1e1e22")
HAMMER = material("hammer", "metal", base="2a2a30", high="5a5a64", low="121216")
HAFT = material("haft", "flat", base="6a4a2a")
EMBER = hexc("ff7a1a")
EMBER_HOT = hexc("ffd060")
EYE = hexc("ff5a10")
EYE_HOT = hexc("ffe080")


def embers(faces):
    """Soot-black skin with embers caught in it, glowing."""
    for name in ("front", "back", "left", "right"):
        f = faces.get(name)
        if f is None:
            continue
        for _ in range(max(1, f.w * f.h // 30)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            f.glow(x, y, EMBER if f.rng.random() < 0.7 else EMBER_HOT)


def face(faces):
    f = faces["front"]
    w = f.w
    # brows, then eyes like coals
    for x in range(1, w - 1):
        f.px(x, 2, shade(SKIN.c["dark"], 0.6))
    for cx in (2, w - 4):
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE_HOT)
    # a broken nose and a grim mouth
    f.px(w // 2, 4, shade(SKIN.c["dark"], 0.8))
    f.px(w // 2, 5, shade(SKIN.c["dark"], 0.8))


def beard(faces):
    """The beard smoulders at its tips."""
    for name in ("front", "left", "right"):
        f = faces.get(name)
        if f is None:
            continue
        for x in range(f.w):
            if (x * 3) % 4 == 0:
                f.glow(x, f.h - 1, EMBER)
            if (x * 5) % 7 == 0:
                f.glow(x, f.h - 2, shade(EMBER, 0.8))


def hammer_head(faces):
    """The hammer's faces glow white-hot."""
    for name in ("left", "right"):
        f = faces.get(name)
        if f is None:
            continue
        for y in range(1, f.h - 1):
            for x in range(1, f.w - 1):
                f.glow(x, y, EMBER_HOT if (x + y) % 3 else EMBER)


def apron(faces):
    f = faces["front"]
    # scorch marks
    for _ in range(6):
        x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
        f.px(x, y, shade(LEATHER.c["fold"], 0.5))


def build():
    m = Model("vulcan", texture_width=256)
    hips = m.bone("hips", (0, 26, 0))
    hips.cube("apron_skirt", (-9, 16, -5), (18, 12, 10), LEATHER, detail=apron)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        thigh = hips.bone(f"{side}_thigh", (4.5 * s, 22, 0))
        thigh.cube(f"{side}_thigh", (x(1, 7), 11, -3.5), (7, 12, 7), SKIN, mirror=s == -1)
        shin = thigh.bone(f"{side}_shin", (4.5 * s, 12, 0))
        shin.cube(f"{side}_shin", (x(1.5, 6), 2, -3), (6, 11, 6), SKIN, mirror=s == -1)
        shin.cube(f"{side}_boot", (x(1, 7), 0, -4.5), (7, 4, 8), LEATHER, mirror=s == -1)
        if s == -1:
            # the crippled leg in its iron brace
            shin.cube("brace", (x(1, 7), 4, -3.5), (7, 2, 7), IRON, mirror=True)
            shin.cube("brace_bar", (x(7.5, 1), 2, -1), (1, 14, 2), IRON, mirror=True)
            thigh.cube("brace_upper", (x(0.5, 8), 15, -4), (8, 2, 8), IRON, mirror=True)
    torso = hips.bone("torso", (0, 28, 0))
    torso.cube("belly", (-9, 26, -5.5), (18, 9, 11), SKIN, detail=embers)
    torso.cube("chest", (-12, 35, -6.5), (24, 13, 13), SKIN, detail=embers)
    torso.cube("apron_bib", (-6, 30, -7), (12, 13, 1), LEATHER)
    torso.cube("shoulders", (-13, 44, -5), (26, 5, 11), SKIN)
    head = torso.bone("head", (0, 49, -2))
    head.cube("skull", (-5, 49, -8), (10, 10, 10), SKIN, detail=face)
    head.cube("beard", (-5.5, 42, -9), (11, 9, 4), BEARD, detail=beard)
    head.cube("hair", (-5.5, 55, -3), (11, 5, 6), BEARD)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = torso.bone(f"{side}_arm", (14 * s, 46, 0))
        arm.cube(f"{side}_arm", (x(11, 8), 33, -4), (8, 14, 8), SKIN, mirror=s == -1, detail=embers)
        forearm = arm.bone(f"{side}_forearm", (15 * s, 33, 0))
        forearm.cube(f"{side}_forearm", (x(11, 8), 20, -4), (8, 13, 8), SKIN, mirror=s == -1)
        forearm.cube(f"{side}_bracer", (x(10.5, 9), 22, -4.5), (9, 4, 9), LEATHER, mirror=s == -1)
        forearm.cube(f"{side}_fist", (x(11, 8), 14, -4), (8, 6, 8), SKIN, mirror=s == -1)
        if s == 1:
            # the hammer hangs from his fist, the haft through it, the head below
            hammer = forearm.bone("hammer", (15, 17, 0))
            hammer.cube("haft", (13.5, -2, -1.5), (3, 20, 3), HAFT)
            hammer.cube("hammer_head", (10, -9, -5), (10, 8, 10), HAMMER, detail=hammer_head)
        else:
            # a coil of chain hanging from the left fist
            chain = forearm.bone("chain", (-15, 14, 0))
            chain.cube("chain", (-16, 2, -1), (2, 12, 2), IRON)
            chain.cube("chain_hook", (-17.5, 0, -1.5), (5, 2, 3), IRON)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # heavy breathing, the hammer swaying, the crippled leg favoured
    idle = m.animation("idle", 3.2, loop=True)
    idle.pos("torso", [(0, ZERO), (1.6, (0, 0.8, 0)), (3.2, ZERO)])
    idle.rot("head", [(0, ZERO), (1.6, (4, 0, 0)), (3.2, ZERO)])
    idle.rot("right_arm", [(0, (8, 0, 6)), (1.6, (14, 0, 8)), (3.2, (8, 0, 6))])
    idle.rot("left_arm", [(0, (0, 0, -8)), (1.6, (4, 0, -10)), (3.2, (0, 0, -8))])
    idle.rot("left_thigh", [(0, (-6, 0, -6)), (3.2, (-6, 0, -6))])

    # a limping stride: the braced leg swings stiff, the body dips onto it
    walk = m.animation("walk", 1.4, loop=True)
    walk.rot("right_thigh", [(0, (-28, 0, 0)), (0.7, (28, 0, 0)), (1.4, (-28, 0, 0))])
    walk.rot("right_shin", [(0, (0, 0, 0)), (0.35, (-35, 0, 0)), (0.7, (0, 0, 0)), (1.4, (0, 0, 0))])
    walk.rot("left_thigh", [(0, (20, 0, -6)), (0.7, (-20, 0, -6)), (1.4, (20, 0, -6))])
    walk.pos("hips", [(0, ZERO), (0.35, (0, -0.5, 0)), (0.7, ZERO), (1.05, (0, -2, 0)), (1.4, ZERO)])
    walk.rot("torso", [(0, (0, 0, 0)), (1.05, (0, 0, -6)), (1.4, (0, 0, 0))])
    walk.rot("right_arm", [(0, (20, 0, 6)), (0.7, (-15, 0, 6)), (1.4, (20, 0, 6))])
    walk.rot("left_arm", [(0, (-15, 0, -8)), (0.7, (20, 0, -8)), (1.4, (-15, 0, -8))])

    # the Strike: the hammer up over his head (white flash), then down in front of him
    strike = m.animation("strike", 1.8)
    strike.rot("right_arm", [(0, (8, 0, 6)), (0.7, (165, 0, 10)), (1.0, (170, 0, 10)), (1.15, (45, 0, 6)), (1.8, (8, 0, 6))])
    strike.rot("right_forearm", [(0, ZERO), (0.7, (30, 0, 0)), (1.15, ZERO)])
    strike.rot("torso", [(0, ZERO), (0.7, (12, 0, 0)), (1.0, (14, 0, 0)), (1.15, (-28, 0, 0)), (1.8, ZERO)])
    strike.rot("left_arm", [(0, ZERO), (0.7, (-20, 0, -30)), (1.15, (30, 0, -20)), (1.8, ZERO)])
    strike.pos("hips", [(0, ZERO), (0.7, (0, 0, 2)), (1.15, (0, -2, -3)), (1.8, ZERO)])
    strike.event(0.6, "flash").event(1.15, "hit")

    # Hammerfall: he crouches, leaps with the hammer high, and comes down on his mark
    fall = m.animation("hammerfall", 2.4)
    fall.pos("hips", [(0, ZERO), (0.7, (0, -6, 0)), (1.0, (0, 14, 0)), (1.3, (0, 16, 0)), (1.55, (0, -4, 0)), (2.4, ZERO)])
    fall.rot("right_thigh", [(0, ZERO), (0.7, (60, 0, 0)), (1.0, (-10, 0, 0)), (1.55, (50, 0, 0)), (2.4, ZERO)])
    fall.rot("right_shin", [(0, ZERO), (0.7, (-70, 0, 0)), (1.0, ZERO), (1.55, (-50, 0, 0)), (2.4, ZERO)])
    fall.rot("left_thigh", [(0, ZERO), (0.7, (40, 0, 0)), (1.0, (-10, 0, 0)), (1.55, (30, 0, 0)), (2.4, ZERO)])
    fall.rot("right_arm", [(0, (8, 0, 6)), (0.7, (40, 0, 6)), (1.1, (175, 0, 10)), (1.55, (50, 0, 6)), (2.4, (8, 0, 6))])
    fall.rot("torso", [(0, ZERO), (0.7, (-20, 0, 0)), (1.1, (15, 0, 0)), (1.55, (-30, 0, 0)), (2.4, ZERO)])
    fall.event(1.0, "leap").event(1.55, "land")

    # Molten Rain: arms up, he upends an unseen crucible over the hall
    pour = m.animation("pour", 2.2)
    pour.rot("right_arm", [(0, (8, 0, 6)), (0.8, (150, 0, 25)), (1.4, (160, 0, 25)), (2.2, (8, 0, 6))])
    pour.rot("left_arm", [(0, ZERO), (0.8, (150, 0, -25)), (1.4, (160, 0, -25)), (2.2, ZERO)])
    pour.rot("torso", [(0, ZERO), (0.8, (10, 0, 0)), (1.4, (-15, 0, 0)), (2.2, ZERO)])
    pour.rot("head", [(0, ZERO), (0.8, (25, 0, 0)), (1.4, (-10, 0, 0)), (2.2, ZERO)])
    pour.event(1.2, "pour")

    # the Chains: the chain whirled round his head, then flung along the line
    throw = m.animation("throw", 1.8)
    throw.rot("left_arm", [(0, ZERO), (0.3, (150, 0, -60)), (0.5, (150, 90, -60)), (0.7, (150, 180, -60)), (0.9, (150, 270, -60)),
                           (1.05, (90, 0, -10)), (1.8, ZERO)])
    throw.rot("torso", [(0, ZERO), (0.9, (0, 25, 0)), (1.05, (-15, -20, 0)), (1.8, ZERO)])
    throw.event(1.05, "throw")

    # the anvil-blow that calls his smiths: the hammer down onto the floor three times
    summon = m.animation("summon", 2.2)
    summon.rot("right_arm", [(0, (8, 0, 6)), (0.35, (120, 0, 6)), (0.55, (40, 0, 6)), (0.9, (120, 0, 6)), (1.1, (40, 0, 6)),
                             (1.45, (120, 0, 6)), (1.65, (40, 0, 6)), (2.2, (8, 0, 6))])
    summon.rot("torso", [(0, ZERO), (0.55, (-15, 0, 0)), (1.1, (-15, 0, 0)), (1.65, (-20, 0, 0)), (2.2, ZERO)])
    summon.event(1.65, "ring")

    # reels after a blocked blow: down on his good knee, shaking his head
    stagger = m.animation("stagger", 2.4)
    stagger.pos("hips", [(0, ZERO), (0.3, (0, -8, 2)), (2.0, (0, -8, 2)), (2.4, ZERO)])
    stagger.rot("right_thigh", [(0, ZERO), (0.3, (80, 0, 0)), (2.0, (80, 0, 0)), (2.4, ZERO)])
    stagger.rot("right_shin", [(0, ZERO), (0.3, (-90, 0, 0)), (2.0, (-90, 0, 0)), (2.4, ZERO)])
    stagger.rot("torso", [(0, ZERO), (0.3, (-25, 0, 0)), (2.0, (-25, 0, 0)), (2.4, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.6, (-15, -25, 0)), (0.9, (-15, 25, 0)), (1.2, (-15, -25, 0)), (1.5, (-15, 25, 0)), (2.4, ZERO)])
    stagger.rot("right_arm", [(0, (8, 0, 6)), (0.3, (30, 0, 20)), (2.0, (30, 0, 20)), (2.4, (8, 0, 6))])

    # his rage at half health: the hammer driven into the floor, then a roar with arms flung wide
    phase = m.animation("phase", 3.2)
    phase.rot("right_arm", [(0, (8, 0, 6)), (0.8, (170, 0, 10)), (1.2, (30, 0, 6)), (1.9, (30, 0, 6)), (2.3, (20, 0, 70)),
                            (2.9, (20, 0, 70)), (3.2, (8, 0, 6))])
    phase.rot("left_arm", [(0, ZERO), (1.9, ZERO), (2.3, (20, 0, -70)), (2.9, (20, 0, -70)), (3.2, ZERO)])
    phase.rot("torso", [(0, ZERO), (0.8, (14, 0, 0)), (1.2, (-35, 0, 0)), (1.9, (-35, 0, 0)), (2.3, (15, 0, 0)), (2.9, (15, 0, 0)),
                        (3.2, ZERO)])
    phase.rot("head", [(0, ZERO), (2.3, (30, 0, 0)), (2.9, (30, 0, 0)), (3.2, ZERO)])
    phase.event(1.2, "plunge").event(2.3, "roar")

    # his entrance: bent over his anvil, hammering, then he straightens and roars
    intro = m.animation("intro", 5.0)
    intro.rot("torso", [(0, (-30, 0, 0)), (3.0, (-30, 0, 0)), (3.6, (15, 0, 0)), (4.4, (15, 0, 0)), (5.0, ZERO)])
    intro.rot("right_arm", [(0, (90, 0, 6)), (0.4, (140, 0, 6)), (0.6, (60, 0, 6)), (1.2, (140, 0, 6)), (1.4, (60, 0, 6)),
                            (2.0, (140, 0, 6)), (2.2, (60, 0, 6)), (3.0, (60, 0, 6)), (3.6, (30, 0, 60)), (4.4, (30, 0, 60)),
                            (5.0, (8, 0, 6))])
    intro.rot("left_arm", [(0, (50, 0, -10)), (3.0, (50, 0, -10)), (3.6, (30, 0, -60)), (4.4, (30, 0, -60)), (5.0, ZERO)])
    intro.rot("head", [(0, (-15, 0, 0)), (3.0, (-15, 0, 0)), (3.6, (30, 0, 0)), (4.4, (30, 0, 0)), (5.0, ZERO)])
    intro.event(0.6, "clang").event(1.4, "clang2").event(2.2, "clang3").event(3.6, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.12, (15, 0, 0)), (0.4, ZERO)])

    # he sinks to his knees, the hammer falls from his hand, and he topples forward
    death = m.animation("death", 3.4)
    death.pos("hips", [(0, ZERO), (0.9, (0, -10, 0)), (2.0, (0, -14, -6)), (3.4, (0, -20, -14))])
    death.rot("hips", [(0, ZERO), (0.9, ZERO), (2.0, (-50, 0, 0)), (3.4, (-80, 0, 0))])
    death.rot("right_thigh", [(0, ZERO), (0.9, (85, 0, 0)), (3.4, (85, 0, 0))])
    death.rot("left_thigh", [(0, ZERO), (0.9, (80, 0, 0)), (3.4, (80, 0, 0))])
    death.rot("right_shin", [(0, ZERO), (0.9, (-90, 0, 0)), (3.4, (-90, 0, 0))])
    death.rot("left_shin", [(0, ZERO), (0.9, (-85, 0, 0)), (3.4, (-85, 0, 0))])
    death.rot("right_arm", [(0, (8, 0, 6)), (0.9, (20, 0, 30)), (2.0, (140, 0, 20)), (3.4, (160, 0, 20))])
    death.rot("left_arm", [(0, ZERO), (2.0, (140, 0, -20)), (3.4, (160, 0, -20))])
    death.rot("hammer", [(0, ZERO), (0.9, (60, 0, 0)), (1.2, (80, 0, 0))])
    death.event(2.0, "fall")


if __name__ == "__main__":
    build().write()
