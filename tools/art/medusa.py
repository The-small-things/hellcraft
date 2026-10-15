#!/usr/bin/env python3
"""Medusa (Inferno IX), called by the Furies: "Venga Medusa: sì 'l farem di smalto" (let Medusa come, and we'll make
him stone). A colossal head and shoulders risen over the battlements of Dis, grey as the stone she makes, a mane of
writhing serpents round a face whose eyes open in a terrible light. Look at her while her eyes are open and you turn
to stone; Virgil's advice to Dante was to turn round and cover his eyes.

Run from the repo root:  python3 tools/art/medusa.py && python3 tools/gen_bosses.py

Her open eyes are a separate glowing part ("eyes") that is only there (scaled up from nothing) while she gazes, so the
gaze is plain to see. Only the gaze keys them: animations add up in the game, so the renderer hides the part outright
whenever the gaze isn't playing (AnimatedBossModel.onlyDuring). The events time her (FuriesFight): "open" and "close" bound her gaze; "rise" her coming.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

STONE = material("stone", "skin", base="8a8a84", dark="5a5a56")
SERPENT = material("serpent", "scales", base="3a5a3a", edge="6a8a4a", belly="9aa070")
ROBE = material("robe", "robe", base="4a4a46", fold="2a2a28")
EYE = hexc("e8fff0")
EYE_EDGE = hexc("8affc0")
LID = hexc("4a4a46")


def face(faces):
    f = faces["front"]
    w = f.w
    # shut eyes: dark lids; a cold, still mouth
    for cx in (2, w - 5):
        for dx in range(3):
            f.px(cx + dx, 4, LID)
    for x in range(w // 2 - 2, w // 2 + 2):
        f.px(x, 8, shade(STONE.c["dark"], 0.6))


def open_eyes(faces):
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EYE if (x + y) % 2 == 0 else EYE_EDGE)


def build():
    m = Model("medusa", texture_width=128)
    bust = m.bone("bust", (0, 0, 0))
    bust.cube("shoulders", (-10, 0, -4), (20, 8, 8), ROBE)
    bust.cube("chest", (-7, 8, -3.5), (14, 6, 7), STONE)
    head = bust.bone("head", (0, 14, 0))
    head.cube("head", (-5, 14, -5), (10, 12, 10), STONE, detail=face)
    eyes = head.bone("eyes", (0, 18, -5.5))
    eyes.cube("eyes", (-4, 17, -5.6), (8, 2, 1), STONE, detail=open_eyes)
    # her serpents, a ring of them round her head, each its own bone so they writhe
    snakes = [(-5, 24, -3, 0, 0, 50), (5, 24, -3, 0, 0, -50), (-5, 22, 3, -30, 0, 60), (5, 22, 3, -30, 0, -60), (0, 26, 4, -60, 0, 0),
              (-3, 26, -4, 30, 0, 30), (3, 26, -4, 30, 0, -30), (0, 26, 0, 0, 0, 0)]
    for i, (x, y, z, rx, ry, rz) in enumerate(snakes):
        s = head.bone(f"snake{i}", (x, y, z), rotation=(rx, ry, rz))
        s.cube(f"snake{i}", (x - 1, y, z - 1), (2, 8, 2), SERPENT)
        s.cube(f"snake{i}_head", (x - 1.5, y + 8, z - 2), (3, 2, 3), SERPENT)
    animations(m)
    return m


ZERO = (0, 0, 0)
HIDDEN = (0.01, 0.01, 0.01)
SHOWN = (1, 1, 1)


def writhe(a, length, amount=20):
    for i in range(8):
        s = 1 if i % 2 else -1
        a.rot(f"snake{i}", [(0, ZERO), (length * 0.25, (amount * s, 0, amount * s)), (length * 0.75, (-amount * s, 0, -amount * s)), (length, ZERO)])


def animations(m):
    # eyes shut, the serpents writhing
    idle = m.animation("idle", 2.0, loop=True)
    writhe(idle, 2.0)
    idle.rot("head", [(0, ZERO), (1.0, (0, 6, 0)), (2.0, ZERO)])

    # the gaze: her head lifts, her eyes open in a cold light, and hold
    gaze = m.animation("gaze", 4.0)
    writhe(gaze, 1.0, 35)
    gaze.scale("eyes", [(0, HIDDEN), (0.75, HIDDEN), (0.8, SHOWN), (3.3, SHOWN), (3.35, HIDDEN), (4.0, HIDDEN)], interp="linear")
    gaze.rot("head", [(0, ZERO), (0.75, (-15, 0, 0)), (3.3, (-15, 0, 0)), (4.0, ZERO)])
    gaze.event(0.8, "open").event(3.3, "close")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("head", [(0, ZERO), (0.12, (-10, 0, 8)), (0.4, ZERO)])

    # she rises over the battlements
    intro = m.animation("intro", 3.0)
    intro.pos("bust", [(0, (0, -40, 0)), (2.2, ZERO), (3.0, ZERO)])
    writhe(intro, 1.5, 30)
    intro.event(0.4, "rise")

    # she sinks back behind the wall
    death = m.animation("death", 2.0)
    death.pos("bust", [(0, ZERO), (2.0, (0, -40, 0))])
    writhe(death, 0.5, 45)


if __name__ == "__main__":
    build().write()
