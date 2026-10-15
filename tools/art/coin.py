#!/usr/bin/env python3
"""A great coin of the Mint (Inferno VII: the misers and the wasters "rolling weights by main force of chest"): a gold
coin as tall as a house, stamped with Plutus's wolf head, that rolls across Greed (entity/boss/RollingCoin). The disc
stands in the model's YZ plane and rolls about its X axis.

Run from the repo root:  python3 tools/art/coin.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, hexc  # noqa: E402

GOLD = material("gold", "metal", base="d4a83a", high="f6dc80", low="8a6a1e")
RIM = material("rim", "metal", base="b8862a", high="e8c060", low="6a4a12")
STAMP = material("stamp", "metal", base="e8bc4a", high="fff0a0", low="9a7420")

R = 40  # the coin's radius, in pixels (five blocks across)


def stamp_detail(faces):
    """The wolf's head stamped on both faces: two eyes and a snout in relief."""
    for name in ("left", "right"):
        f = faces[name]
        cx, cy = f.w // 2, f.h // 2
        for dx in (-5, 4):
            for x in range(cx + dx, cx + dx + 2):
                f.px(x, cy - 4, hexc("6a4a12"))
        for y in range(cy - 1, cy + 6):
            f.px(cx - 1, y, hexc("8a6a1e"))
            f.px(cx, y, hexc("8a6a1e"))


def build():
    m = Model("coin", texture_width=256)
    coin = m.bone("coin", (0, R, 0))
    # an octagon of slabs makes the disc; a thinner, wider ring makes the rim; a raised boss the stamp
    for i, angle in enumerate((0, 45, 90, 135)):
        coin.cube(f"disc_{i}", (-4, 0, -16.5), (8, 2 * R, 33), GOLD, rotation=(angle, 0, 0), origin=(0, R, 0))
        coin.cube(f"rim_{i}", (-3, -1, -17), (6, 2 * R + 2, 34), RIM, rotation=(angle + 22.5, 0, 0), origin=(0, R, 0))
    coin.cube("stamp", (-5, R - 16, -16), (10, 32, 32), STAMP, detail=stamp_detail)

    roll = m.animation("roll", 2.0, loop=True)
    roll.rot("coin", [(0, (0, 0, 0)), (0.5, (90, 0, 0)), (1.0, (180, 0, 0)), (1.5, (270, 0, 0)), (2.0, (360, 0, 0))])
    return m


if __name__ == "__main__":
    build().write()
