#!/usr/bin/env python3
"""The lovers (Lust's monster): two souls blown together forever on the hurricane, "those two who go together, and seem
to be so light upon the wind" (Inferno V). Pale violet shapes trailing into wisps, turning round each other in the
air; they dive on pilgrims and throw them about with the wind. Drawn see-through and softly lit.

Run from the repo root:  python3 tools/art/lovers.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, hexc  # noqa: E402

SOUL_A = material("soul_a", "robe", base="c8a8e8", fold="8a6aaa")
SOUL_B = material("soul_b", "robe", base="e8a8c8", fold="aa6a8a")
HAIR_A = material("hair_a", "hair", light="6a4a8a", dark="3a2a5a")
HAIR_B = material("hair_b", "hair", light="8a4a6a", dark="5a2a3a")
EYE = hexc("ffffff")


def face(faces):
    f = faces["front"]
    for cx in (1, f.w - 2):
        f.glow(cx, 3, EYE)


def wisp(faces):
    for name in ("front", "back", "left", "right", "bottom"):
        f = faces[name]
        for x in range(f.w):
            for y in range(f.h):
                if y >= f.h - 3 or name == "bottom":
                    f.glow(x, y, hexc("e8d8ff"))


def soul(parent, name, x0, mat, hair):
    s = parent.bone(name, (x0, 16, 0))
    s.cube(f"{name}_body", (x0 - 3, 8, -2), (6, 10, 4), mat)
    s.cube(f"{name}_tail", (x0 - 2, 2, -1.5), (4, 6, 3), mat, detail=wisp)
    s.cube(f"{name}_head", (x0 - 2.5, 18, -2.5), (5, 5, 5), mat, detail=face)
    s.cube(f"{name}_hair", (x0 - 3, 20, -1), (6, 4, 4), hair, inflate=0.2)
    s.cube(f"{name}_arm", (x0 - 1 if x0 > 0 else x0 - 1, 12, -6), (2, 2, 6), mat)
    return s


def build():
    m = Model("lovers", texture_width=64)
    pair = m.bone("pair", (0, 14, 0))
    soul(pair, "soul_a", 3, SOUL_A, HAIR_A)
    soul(pair, "soul_b", -3, SOUL_B, HAIR_B)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # turning round each other on the wind, rising and falling
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("pair", [(0, (0, 0, 0)), (0.5, (0, 90, 0)), (1.0, (0, 180, 0)), (1.5, (0, 270, 0)), (2.0, (0, 360, 0))])
    idle.pos("pair", [(0, ZERO), (1.0, (0, 2, 0)), (2.0, ZERO)])
    idle.rot("soul_a", [(0, (0, 0, 8)), (1.0, (0, 0, 14)), (2.0, (0, 0, 8))])
    idle.rot("soul_b", [(0, (0, 0, -8)), (1.0, (0, 0, -14)), (2.0, (0, 0, -8))])

    # a dive: both lean in together and sweep through
    attack = m.animation("attack", 0.8)
    attack.rot("pair", [(0, ZERO), (0.3, (20, 0, 0)), (0.45, (-50, 0, 0)), (0.8, ZERO)])
    attack.pos("pair", [(0, ZERO), (0.3, (0, 2, 2)), (0.45, (0, -2, -6)), (0.8, ZERO)])
    attack.event(0.45, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("soul_a", [(0, ZERO), (0.1, (0, 0, 30)), (0.3, ZERO)])
    hurt.rot("soul_b", [(0, ZERO), (0.1, (0, 0, -30)), (0.3, ZERO)])

    # torn apart, they drift away from each other
    death = m.animation("death", 1.4)
    death.pos("soul_a", [(0, ZERO), (1.4, (8, 6, 0))])
    death.pos("soul_b", [(0, ZERO), (1.4, (-8, 6, 0))])
    death.rot("soul_a", [(0, ZERO), (1.4, (0, 0, 60))])
    death.rot("soul_b", [(0, ZERO), (1.4, (0, 0, -60))])


if __name__ == "__main__":
    build().write()
