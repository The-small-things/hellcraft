#!/usr/bin/env python3
"""Phlegyas's ferry (Inferno VIII: "the ancient prow goes cutting more of the water than it is wont"): a long, black,
flat-bottomed boat with a high prow and a beacon-flame lantern, that carries pilgrims across the Fen of Styx to the
gates of Dis (entity/boss/Ferry). Its front is -Z.

Run from the repo root:  python3 tools/art/ferry.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, hexc  # noqa: E402

HULL = material("hull", "hair", light="3a2e24", dark="1a140e")
DECK = material("deck", "hair", light="5a4632", dark="2e2418")
IRON = material("iron", "metal", base="3a3a3e", high="6a6a70", low="1a1a1e")
FLAME = material("flame", "flat", base="ff9a3a")


def flame_detail(faces):
    for name in ("front", "back", "left", "right", "top"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, hexc("ffd27a") if (x + y) % 2 else hexc("ff8a2a"))


def build():
    m = Model("ferry", texture_width=512)
    hull = m.bone("hull", (0, 8, 0))
    hull.cube("bottom", (-22, 0, -72), (44, 4, 144), HULL)
    hull.cube("deck", (-20, 4, -70), (40, 1, 140), DECK)
    hull.cube("side_r", (20, 4, -72), (4, 12, 144), HULL)
    hull.cube("side_l", (-24, 4, -72), (4, 12, 144), HULL)
    hull.cube("stern", (-22, 4, 70), (44, 14, 4), HULL)
    # the high prow, rising and narrowing
    hull.cube("prow_1", (-18, 2, -84), (36, 16, 12), HULL)
    hull.cube("prow_2", (-12, 8, -94), (24, 16, 10), HULL)
    hull.cube("prow_3", (-6, 16, -102), (12, 16, 8), HULL)
    hull.cube("prow_4", (-3, 26, -108), (6, 14, 6), HULL)
    for i, z in enumerate((-50, -10, 30)):
        hull.cube(f"bench_{i}", (-20, 8, z), (40, 3, 8), DECK)
    lantern = hull.bone("lantern", (0, 40, -105))
    lantern.cube("post", (-1, 38, -106), (2, 12, 2), IRON)
    lantern.cube("cage", (-3, 50, -108), (6, 8, 6), IRON)
    lantern.cube("flame", (-2, 51, -107), (4, 5, 4), FLAME, detail=flame_detail)

    bob = m.animation("float", 4.0, loop=True)
    bob.pos("hull", [(0, (0, 0, 0)), (2, (0, -1, 0)), (4, (0, 0, 0))])
    bob.rot("hull", [(0, (1, 0, 1)), (1, (0, 0, -1)), (2, (-1, 0, 0)), (3, (0, 0, 1)), (4, (1, 0, 1))])
    bob.rot("lantern", [(0, (0, 0, 3)), (2, (0, 0, -3)), (4, (0, 0, 3))])
    return m


if __name__ == "__main__":
    build().write()
