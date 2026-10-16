#!/usr/bin/env python3
"""The Furies (Inferno IX), on the burning tower over the gate of Dis: "three infernal Furies stained with blood, who
had the limbs of women and their mien, and with the greenest hydras were begirt; small serpents and cerastes were
their hair". Megaera, Alecto and Tisiphone: gaunt women in torn black, bloodied, a girdle of green water-snakes, a
nest of serpents for hair, and dark ragged wings.

Run from the repo root:  python3 tools/art/fury.py && python3 tools/gen_bosses.py

All three share this body. The events time their fight (FuriesFight): "flash" opens the window to block a Fury's
talons and "hit" is when they strike; "scream" their shriek; "lash" the crack of her serpent hair; "tear" when they
rend their breasts and blood rains; "call" when they call for Medusa; "rise" and "scream" their entrance.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="9a8a7e", dark="5a4a44")
ROBE = material("robe", "robe", base="1e1a1c", fold="0e0a0c")
HYDRA = material("hydra", "scales", base="2a6a2a", edge="5aa03a", belly="a0b860")
SERPENT = material("serpent", "scales", base="3a4a2a", edge="6a7a3a", belly="8a8a50")
WING = material("wing", "hair", light="3a2a2a", dark="0e0808")
CLAW = material("claw", "horn", root="4a3a34", tip="0a0606")
EYE = hexc("ff2a2a")
BLOOD = hexc("7a0a0a")
MOUTH = hexc("1a0606")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (1, w - 3):
        f.glow(cx, 3, EYE)
        f.glow(cx + 1, 3, EYE)
        # tears of blood
        f.px(cx, 4, BLOOD)
        f.px(cx, 5, BLOOD)
    for x in range(2, w - 2):
        f.px(x, f.h - 2, MOUTH)


def bloodied(faces):
    f = faces["front"]
    for y in range(f.h):
        for x in range(f.w):
            if (x * 5 + y * 3) % 7 == 0:
                f.px(x, y, BLOOD)


def tattered(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            if x % 3 == 1:
                f.px(x, f.h - 1, (0, 0, 0), alpha=0)
                f.px(x, f.h - 2, (0, 0, 0), alpha=0)


def build():
    m = Model("fury", texture_width=128)
    body = m.bone("body", (0, 14, 0), rotation=(-15, 0, 0))
    body.cube("robe", (-4, 0, -2.5), (8, 14, 5), ROBE, detail=tattered)
    body.cube("hydra_girdle", (-4.5, 12, -3), (9, 2, 6), HYDRA, inflate=0.2)
    torso = body.bone("torso", (0, 14, 0))
    torso.cube("torso", (-4, 14, -2), (8, 10, 4), SKIN, detail=bloodied)
    head = torso.bone("head", (0, 24, 0))
    head.cube("head", (-3, 24, -3), (6, 7, 6), SKIN, detail=face)
    # the serpents of her hair, twisting out every way
    for i, (dx, dz, rx, rz) in enumerate([(-3, -1, 0, -40), (3, -1, 0, 40), (-2, 2, 40, -20), (2, 2, 40, 20), (0, 3, 50, 0), (0, -2, -10, 0)]):
        snake = head.bone(f"snake{i}", (dx, 30, dz), rotation=(rx, 0, rz))
        snake.cube(f"snake{i}", (dx - 0.5, 30, dz - 0.5), (1, 5, 1), SERPENT)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = torso.bone(f"{side}_arm", (4.5 * s, 23, 0))
        arm.cube(f"{side}_arm", (x(4, 2), 13, -1), (2, 10, 2), SKIN, mirror=s == -1)
        arm.cube(f"{side}_claws", (x(3.5, 3), 11, -1.5), (3, 2, 3), CLAW, mirror=s == -1)
        wing = torso.bone(f"{side}_wing", (2 * s, 22, 2))
        wing.cube(f"{side}_wing", (x(2, 14), 10, 2), (14, 12, 1), WING, mirror=s == -1, detail=tattered)
    animations(m)
    return m


ZERO = (0, 0, 0)


def flap(a, length, up=-30, down=25):
    for side, s in (("right", 1), ("left", -1)):
        a.rot(f"{side}_wing", [(0, (0, -20 * s, up * s)), (length * 0.5, (0, 20 * s, down * s)), (length, (0, -20 * s, up * s))])


def writhe(a, length):
    for i in range(6):
        a.rot(f"snake{i}", [(0, ZERO), (length * 0.25, (0, 0, 15 if i % 2 else -15)), (length * 0.75, (0, 0, -15 if i % 2 else 15)), (length, ZERO)])


def animations(m):
    # hovering, wings beating, the serpents of her hair never still
    idle = m.animation("idle", 0.8, loop=True)
    flap(idle, 0.8)
    writhe(idle, 0.8)
    idle.pos("body", [(0, ZERO), (0.4, (0, 1.5, 0)), (0.8, ZERO)])
    idle.rot("right_arm", [(0, (20, 0, 20)), (0.8, (20, 0, 20))])
    idle.rot("left_arm", [(0, (20, 0, -20)), (0.8, (20, 0, -20))])

    # her talons: she draws back, flashes white, and rakes down
    talons = m.animation("talons", 1.4)
    flap(talons, 0.7)
    talons.rot("right_arm", [(0, (20, 0, 20)), (0.5, (170, 0, 30)), (0.9, (175, 0, 30)), (1.0, (40, 0, 0)), (1.4, (20, 0, 20))])
    talons.rot("left_arm", [(0, (20, 0, -20)), (0.5, (170, 0, -30)), (0.9, (175, 0, -30)), (1.0, (40, 0, 0)), (1.4, (20, 0, -20))])
    talons.rot("body", [(0, ZERO), (0.5, (20, 0, 0)), (1.0, (-40, 0, 0)), (1.4, ZERO)])
    talons.event(0.5, "flash").event(1.0, "hit")

    # the shriek: head back, wings and arms flung wide
    shriek = m.animation("shriek", 1.6)
    writhe(shriek, 0.4)
    shriek.rot("head", [(0, ZERO), (0.3, (50, 0, 0)), (1.3, (50, 0, 0)), (1.6, ZERO)])
    shriek.rot("right_arm", [(0, (20, 0, 20)), (0.3, (20, 0, 110)), (1.3, (20, 0, 110)), (1.6, (20, 0, 20))])
    shriek.rot("left_arm", [(0, (20, 0, -20)), (0.3, (20, 0, -110)), (1.3, (20, 0, -110)), (1.6, (20, 0, -20))])
    for side, s in (("right", 1), ("left", -1)):
        shriek.rot(f"{side}_wing", [(0, ZERO), (0.3, (0, 0, -50 * s)), (1.3, (0, 0, -50 * s)), (1.6, ZERO)])
    shriek.event(0.4, "scream")

    # the lash: she whips her head round and the serpents crack like a scourge
    lash = m.animation("lash", 1.2)
    flap(lash, 0.6)
    lash.rot("head", [(0, ZERO), (0.4, (0, 70, 0)), (0.6, (-20, -80, 0)), (1.2, ZERO)])
    lash.rot("torso", [(0, ZERO), (0.4, (0, 30, 0)), (0.6, (0, -40, 0)), (1.2, ZERO)])
    for i in range(6):
        lash.rot(f"snake{i}", [(0, ZERO), (0.4, (0, 0, -40)), (0.6, (60, 0, 50)), (1.2, ZERO)])
    lash.event(0.6, "lash")

    # rending their breasts: blood falls round you
    tear = m.animation("tear", 1.6)
    flap(tear, 0.8)
    tear.rot("right_arm", [(0, (20, 0, 20)), (0.5, (80, 40, 0)), (0.8, (60, -30, 0)), (1.1, (80, 40, 0)), (1.6, (20, 0, 20))])
    tear.rot("left_arm", [(0, (20, 0, -20)), (0.5, (80, -40, 0)), (0.8, (60, 30, 0)), (1.1, (80, -40, 0)), (1.6, (20, 0, -20))])
    tear.rot("head", [(0, ZERO), (0.5, (30, 0, 0)), (1.3, (30, 0, 0)), (1.6, ZERO)])
    tear.event(0.8, "tear")

    # struck from the air: she tumbles, wings askew
    stagger = m.animation("stagger", 2.2)
    stagger.rot("body", [(0, ZERO), (0.3, (-60, 0, 40)), (1.8, (-70, 0, 40)), (2.2, ZERO)])
    stagger.pos("body", [(0, ZERO), (0.4, (0, -10, 0)), (1.8, (0, -10, 0)), (2.2, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        stagger.rot(f"{side}_wing", [(0, ZERO), (0.3, (0, 40 * s, 40 * s)), (1.8, (0, 40 * s, 40 * s)), (2.2, ZERO)])

    # "Venga Medusa!": they turn to the wall and call her
    phase = m.animation("phase", 2.6)
    flap(phase, 0.65)
    phase.rot("right_arm", [(0, (20, 0, 20)), (0.6, (170, 0, -10)), (2.2, (170, 0, -10)), (2.6, (20, 0, 20))])
    phase.rot("left_arm", [(0, (20, 0, -20)), (0.6, (170, 0, 10)), (2.2, (170, 0, 10)), (2.6, (20, 0, -20))])
    phase.rot("head", [(0, ZERO), (0.6, (45, 0, 0)), (2.2, (45, 0, 0)), (2.6, ZERO)])
    phase.event(0.8, "call")

    # their entrance: up from behind the battlements, shrieking
    intro = m.animation("intro", 4.0)
    flap(intro, 0.5)
    intro.pos("body", [(0, (0, -30, 0)), (1.0, (0, -30, 0)), (2.0, ZERO), (4.0, ZERO)])
    intro.rot("head", [(0, ZERO), (2.2, (50, 0, 0)), (3.4, (50, 0, 0)), (4.0, ZERO)])
    intro.event(1.0, "rise").event(2.2, "scream")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (20, 0, 10)), (0.3, ZERO)])

    # she falls out of the air
    death = m.animation("death", 1.6)
    death.rot("body", [(0, ZERO), (0.6, (-80, 0, 60)), (1.6, (-90, 0, 80))])
    death.pos("body", [(0, ZERO), (0.8, (0, -10, 0)), (1.6, (0, -13, 0))])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, ZERO), (0.6, (0, 30 * s, 70 * s)), (1.6, (0, 30 * s, 80 * s))])


if __name__ == "__main__":
    build().write()
