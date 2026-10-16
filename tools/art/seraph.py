#!/usr/bin/env python3
"""The Seraph of the Primum Mobile, Paradiso's guardian and the last trial (Isaiah VI: "each one had six wings; with
twain he covered his face, and with twain he covered his feet, and with twain he did fly"; Paradiso XXVIII, the
seraphim burning nearest the point of light). A tall burning angel hovering over the crystal floor: a robe of white and
gold trailing into flame, a face of light behind two veiling wings, two more veiling its feet, two great wings spread
to fly, all of them full of eyes; a ring of fire behind its head, and in its right hand tongs holding a live coal from
the altar.

Run from the repo root:  python3 tools/art/seraph.py && python3 tools/gen_bosses.py

It is drawn 2.0 times its Blockbench size (about 6 blocks tall). The events time its fight (SeraphFight): "flash" opens
the window to block its wing-blow and "hit" is when it lands; "call" brings fire down from above; "throw" lets the coal
go; "unveil" and "blaze" are the Gloria (its face unveiled: get close to it); "turn" starts the turning heavens; "host"
calls the Angelic Guard; "flare" its half-health turn; "land" and "cry" its descent; "yield" and "rise" its end, when it
bows to you and goes up into the light.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

ROBE = material("robe", "robe", base="f2eee2", fold="c8bfa4", trim="e0b040")
GOLD = material("gold", "metal", base="d8b040", high="fff0a0", low="8a6418")
SKIN = material("skin", "skin", base="fff2d8", dark="e0c8a0")
WING = material("wing", "hair", light="fffaf0", dark="d8ccb0")
COAL = material("coal", "flat", base="2a1a14")
LIGHT = hexc("fff4c0")
FIRE = hexc("ffa030")
FIRE_HOT = hexc("ffe070")
EYE = hexc("4ab8ff")
EYE_HOT = hexc("d8f4ff")


def glowing(colour):
    def paint(faces):
        for name in ("front", "back", "left", "right", "top", "bottom"):
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    f.glow(x, y, colour)
    return paint


def face_of_light(faces):
    """A face too bright to look at: light, with two burning blue eyes."""
    for name in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, LIGHT if (x + y) % 3 else FIRE_HOT)
    f = faces["front"]
    for cx in (1, f.w - 3):
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE_HOT)


def eyed(faces):
    """Feathers full of eyes, their edges burning (the seraphim are the burning ones)."""
    for name in ("front", "back"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if y == 0 or x in (0, f.w - 1):
                    f.glow(x, y, FIRE if (x + y) % 2 else FIRE_HOT)
        for i in range(max(2, f.w * f.h // 40)):
            x = 1 + f.rng.randrange(max(1, f.w - 3))
            y = 1 + f.rng.randrange(max(1, f.h - 3))
            f.glow(x, y, EYE)
            f.glow(x + 1, y, EYE_HOT)
            f.px(x, y + 1, shade(WING.c["dark"], 0.7))
            f.px(x + 1, y + 1, shade(WING.c["dark"], 0.7))


def flame_hem(faces):
    """The robe's hem, trailing into flame."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if y > f.h - 4 or (y > f.h - 7 and (x * 3 + y) % 4 == 0):
                    f.glow(x, y, FIRE if (x + y) % 3 else FIRE_HOT)


def build():
    m = Model("seraph", texture_width=256)
    root = m.bone("root", (0, 0, 0))
    body = root.bone("body", (0, 14, 0))
    body.cube("hem", (-6, 0, -5), (12, 8, 10), ROBE, detail=flame_hem)
    body.cube("robe", (-5, 8, -4), (10, 18, 8), ROBE)
    body.cube("girdle", (-5.5, 24, -4.5), (11, 2, 9), GOLD)
    chest = body.bone("chest", (0, 26, 0))
    chest.cube("chest", (-6, 26, -4), (12, 10, 8), ROBE)
    chest.cube("stole", (-2, 26, -4.5), (4, 10, 1), GOLD)
    head = chest.bone("head", (0, 36, 0))
    head.cube("head", (-4, 36, -4), (8, 8, 8), SKIN, detail=face_of_light)
    # the ring of fire behind its head
    halo = head.bone("halo", (0, 41, 5))
    halo.cube("halo_top", (-7, 48, 5), (14, 1, 1), GOLD, detail=glowing(FIRE_HOT))
    halo.cube("halo_bottom", (-7, 34, 5), (14, 1, 1), GOLD, detail=glowing(FIRE_HOT))
    halo.cube("halo_left", (-8, 34, 5), (1, 15, 1), GOLD, detail=glowing(FIRE))
    halo.cube("halo_right", (7, 34, 5), (1, 15, 1), GOLD, detail=glowing(FIRE))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = chest.bone(f"{side}_arm", (7 * s, 35, 0))
        arm.cube(f"{side}_arm", (x(6, 4), 23, -2), (4, 12, 4), ROBE, mirror=s == -1)
        arm.cube(f"{side}_hand", (x(6, 4), 20, -2), (4, 3, 4), SKIN, mirror=s == -1)
        # twain covered his face: the upper wings, folded across it (they open for the Gloria)
        face_wing = chest.bone(f"{side}_face_wing", (9 * s, 41, -5))
        face_wing.cube(f"{side}_face_wing", (x(0, 9), 35, -6), (9, 13, 1), WING, mirror=s == -1, detail=eyed)
        # twain covered his feet: the lower wings, across the hem
        foot_wing = body.bone(f"{side}_foot_wing", (8 * s, 12, -5))
        foot_wing.cube(f"{side}_foot_wing", (x(0, 8), 1, -6), (8, 14, 1), WING, mirror=s == -1, detail=eyed)
        # and with twain he did fly: the great wings, spread
        wing = chest.bone(f"{side}_wing", (4 * s, 34, 4), rotation=(0, 0, 12 * s))
        wing.cube(f"{side}_wing", (x(4, 28), 20, 4), (28, 18, 1), WING, mirror=s == -1, detail=eyed)
        wing.cube(f"{side}_wing_tip", (x(32, 6), 26, 4), (6, 10, 1), WING, mirror=s == -1, detail=eyed)
    # the tongs and the live coal from the altar (Isaiah VI 6), in its right hand
    coal = chest.bone("coal", (8, 21, 0))
    coal.cube("tongs", (7.5, 14, -0.5), (1, 7, 1), GOLD)
    coal.cube("coal", (6.5, 11, -1.5), (3, 3, 3), COAL, detail=glowing(FIRE))
    animations(m)
    return m


ZERO = (0, 0, 0)
HOVER = (0, 3, 0)
OPEN = 100


def flap(a, length, amount=14, start=0.0):
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(start, (0, 0, -6 * s)), (start + length * 0.5, (0, 0, amount * s)), (start + length, (0, 0, -6 * s))])


def unveil(a, keys):
    """The face wings swing out to its sides (to the angles given at each time: 0 veiled, 1 open)."""
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_face_wing", [(t, (0, OPEN * s * v, 0)) for t, v in keys])


def animations(m):
    # hovering over the floor, its great wings slowly beating
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("root", [(0, HOVER), (1.5, (0, 4.5, 0)), (3.0, HOVER)])
    flap(idle, 3.0, 10)
    idle.rot("halo", [(0, ZERO), (3.0, (0, 0, 360))], interp="linear")

    walk = m.animation("walk", 2.0, loop=True)
    walk.pos("root", [(0, HOVER), (1.0, (0, 4.5, 0)), (2.0, HOVER)])
    walk.rot("body", [(0, (-8, 0, 0)), (2.0, (-8, 0, 0))])
    flap(walk, 1.0, 18)
    flap(walk, 1.0, 18, start=1.0)

    # its descent: down out of the light, the face wings open as it cries "Holy, holy, holy"
    intro = m.animation("intro", 5.0)
    intro.pos("root", [(0, (0, 80, 0)), (3.0, HOVER), (5.0, HOVER)])
    flap(intro, 1.0, 30)
    flap(intro, 1.0, 30, start=1.0)
    flap(intro, 1.0, 30, start=2.0)
    unveil(intro, [(0, 0), (3.2, 0), (3.8, 1), (4.6, 1), (5.0, 0)])
    intro.rot("right_arm", [(0, ZERO), (3.4, ZERO), (3.8, (150, 0, 20)), (4.6, (150, 0, 20)), (5.0, ZERO)])
    intro.rot("left_arm", [(0, ZERO), (3.4, ZERO), (3.8, (150, 0, -20)), (4.6, (150, 0, -20)), (5.0, ZERO)])
    intro.event(3.0, "land").event(3.8, "cry")

    # the wing-blow: both great wings drawn back, a white flash, then swept forward across the front
    wings = m.animation("wings", 1.7)
    wings.pos("root", [(0, HOVER), (1.7, HOVER)])
    for side, s in (("right", 1), ("left", -1)):
        wings.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.6, (0, -45 * s, 30 * s)), (0.85, (0, -50 * s, 30 * s)), (1.0, (0, 75 * s, 0)),
                                   (1.2, (0, 75 * s, 0)), (1.7, (0, 0, 12 * s))])
    wings.rot("chest", [(0, ZERO), (0.6, (15, 0, 0)), (1.0, (-20, 0, 0)), (1.7, ZERO)])
    wings.event(0.55, "flash").event(0.95, "hit")

    # fire from above: both hands lifted, its head thrown back
    fire = m.animation("fire", 2.0)
    fire.pos("root", [(0, HOVER), (2.0, HOVER)])
    fire.rot("right_arm", [(0, ZERO), (0.6, (170, 0, 15)), (1.6, (170, 0, 15)), (2.0, ZERO)])
    fire.rot("left_arm", [(0, ZERO), (0.6, (170, 0, -15)), (1.6, (170, 0, -15)), (2.0, ZERO)])
    fire.rot("head", [(0, ZERO), (0.6, (30, 0, 0)), (1.6, (30, 0, 0)), (2.0, ZERO)])
    flap(fire, 1.0, 25)
    flap(fire, 1.0, 25, start=1.0)
    fire.event(0.8, "call")

    # the live coal: drawn back, then hurled
    coal = m.animation("coal", 1.6)
    coal.pos("root", [(0, HOVER), (1.6, HOVER)])
    coal.rot("right_arm", [(0, ZERO), (0.6, (-50, 0, 20)), (0.9, (120, 0, 0)), (1.6, ZERO)])
    coal.rot("chest", [(0, ZERO), (0.6, (0, -25, 0)), (0.9, (-10, 20, 0)), (1.6, ZERO)])
    coal.event(0.9, "throw")

    # the Gloria: it unveils its face, leans back and blazes; only those close beside it are spared
    gloria = m.animation("gloria", 3.2)
    gloria.pos("root", [(0, HOVER), (1.0, (0, 8, 0)), (2.4, (0, 8, 0)), (3.2, HOVER)])
    unveil(gloria, [(0, 0), (1.0, 1), (2.6, 1), (3.2, 0)])
    gloria.rot("chest", [(0, ZERO), (1.0, (20, 0, 0)), (2.4, (20, 0, 0)), (3.2, ZERO)])
    gloria.rot("right_arm", [(0, ZERO), (1.0, (30, 0, 80)), (2.4, (30, 0, 80)), (3.2, ZERO)])
    gloria.rot("left_arm", [(0, ZERO), (1.0, (30, 0, -80)), (2.4, (30, 0, -80)), (3.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        gloria.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (1.0, (0, 20 * s, 40 * s)), (2.4, (0, 20 * s, 40 * s)), (3.2, (0, 0, 12 * s))])
    gloria.event(1.0, "unveil").event(2.2, "blaze")

    # the turning heavens: it spins, wings outstretched, and a beam turns round the floor
    turning = m.animation("turning", 2.4)
    turning.pos("root", [(0, HOVER), (0.4, (0, 6, 0)), (2.0, (0, 6, 0)), (2.4, HOVER)])
    turning.rot("root", [(0, ZERO), (0.4, ZERO), (2.0, (0, 360, 0)), (2.4, (0, 360, 0))], interp="linear")
    for side, s in (("right", 1), ("left", -1)):
        turning.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.4, (0, 0, -5 * s)), (2.0, (0, 0, -5 * s)), (2.4, (0, 0, 12 * s))])
    turning.event(0.4, "turn")

    # the Angelic Guard: the great wings raised high
    host = m.animation("host", 2.0)
    host.pos("root", [(0, HOVER), (2.0, HOVER)])
    for side, s in (("right", 1), ("left", -1)):
        host.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.8, (0, 0, 60 * s)), (1.4, (0, 0, 60 * s)), (2.0, (0, 0, 12 * s))])
    host.rot("left_arm", [(0, ZERO), (0.8, (160, 0, -10)), (1.4, (160, 0, -10)), (2.0, ZERO)])
    host.event(1.0, "host")

    # parried: it reels, sinking, its wings askew
    stagger = m.animation("stagger", 2.0)
    stagger.pos("root", [(0, HOVER), (0.3, (0, -2, 3)), (1.6, (0, -2, 3)), (2.0, HOVER)])
    stagger.rot("chest", [(0, ZERO), (0.3, (25, 0, 15)), (1.6, (25, 0, 15)), (2.0, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        stagger.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (0.3, (0, 0, -30 * s)), (1.6, (0, 0, -30 * s)), (2.0, (0, 0, 12 * s))])

    # half its strength gone: all six wings flare, the ring of fire spins
    phase = m.animation("phase", 2.6)
    phase.pos("root", [(0, HOVER), (1.2, (0, 10, 0)), (2.2, (0, 10, 0)), (2.6, HOVER)])
    unveil(phase, [(0, 0), (1.2, 1), (2.2, 1), (2.6, 0)])
    for side, s in (("right", 1), ("left", -1)):
        phase.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (1.2, (0, 0, 55 * s)), (2.2, (0, 0, 55 * s)), (2.6, (0, 0, 12 * s))])
        phase.rot(f"{side}_foot_wing", [(0, ZERO), (1.2, (0, 70 * s, 0)), (2.2, (0, 70 * s, 0)), (2.6, ZERO)])
    phase.rot("halo", [(0, ZERO), (2.6, (0, 0, 720))], interp="linear")
    phase.event(1.2, "flare")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("chest", [(0, ZERO), (0.1, (12, 0, 6)), (0.3, ZERO)])

    # it yields: it sinks to the floor and bows to you, its face veiled, then rises into the light and is gone
    death = m.animation("death", 4.4)
    death.pos("root", [(0, HOVER), (1.2, (0, -4, 0)), (2.6, (0, -4, 0)), (4.4, (0, 90, 0))])
    death.rot("chest", [(0, ZERO), (1.2, (-35, 0, 0)), (2.6, (-35, 0, 0)), (3.2, ZERO)])
    death.rot("head", [(0, ZERO), (1.2, (-25, 0, 0)), (2.6, (-25, 0, 0)), (3.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, (0, 0, 12 * s)), (1.2, (0, 40 * s, -20 * s)), (2.6, (0, 40 * s, -20 * s)), (3.0, (0, 0, 30 * s)),
                                   (3.4, (0, 0, -10 * s)), (3.8, (0, 0, 30 * s)), (4.4, (0, 0, 0))])
    death.scale("root", [(0, (1, 1, 1)), (3.4, (1, 1, 1)), (4.4, (0.05, 0.05, 0.05))], interp="linear")
    death.event(1.2, "yield").event(2.6, "rise")


if __name__ == "__main__":
    build().write()
