#!/usr/bin/env python3
"""Antaeus (Inferno XXXI), the giant who was never chained because he took no part in the war on the gods: he stands
on the ice of Cocytus at the foot of the Well, as tall as the cliff, a lion's skin round his loins, and at Virgil's
asking takes the travellers in his hand and sets them down on the ice. "He set us down upon the bottom, and did not
linger thus bent, but like a mast upon a ship uprose."

Run from the repo root:  python3 tools/art/antaeus.py && python3 tools/gen_bosses.py

He is drawn eight times his Blockbench size (about 28 blocks tall). His right hand rests palm-up on the Well's rim in
his rest pose; "lower" carries whoever is in it down to the ice ("take" .. "set"), "low" holds it there, and "raise"
carries it back up ("take" .. "set"). The hand follows a path of points (LOWER_PATH, RAISE_PATH) and the server moves
whoever rides along the same points (LOWER_PATH and RAISE_PATH in AntaeusEntity), so keep the two in step.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="7a5a42", dark="4a3426")
LION = material("lion", "hair", light="b08a40", dark="5a4018")
HAIR = material("hair", "hair", light="3a2a1e", dark="120c08")
FROST = hexc("c8e8ff")
EYE = hexc("ffd890")


def face(faces):
    f = faces["front"]
    w = f.w
    for cx in (2, w - 4):
        f.px(cx, 4, shade(SKIN.c["dark"], 0.5))
        f.glow(cx + 1, 5, EYE)
    for x in range(2, w - 2):
        f.px(x, 8, shade(SKIN.c["dark"], 0.6))
    for y in range(f.h - 3, f.h):
        for x in range(1, w - 1):
            f.px(x, y, HAIR.c["light"] if (x + y) % 2 else HAIR.c["dark"])


def frosted(faces):
    """Rime on his shins, from standing in the ice."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h - 8, f.h):
            for x in range(f.w):
                if (x + y) % 3 == 0 or y > f.h - 3:
                    f.px(x, y, FROST)


def build():
    m = Model("antaeus", texture_width=256)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        leg = m.bone(f"{side}_leg", (5 * s, 24, 0))
        leg.cube(f"{side}_leg", (x(1, 9), 0, -4.5), (9, 24, 9), SKIN, mirror=s == -1, detail=frosted)
    body = m.bone("body", (0, 24, 0))
    body.cube("lion_skin", (-11, 20, -6), (22, 8, 12), LION)
    body.cube("torso", (-12, 26, -6), (24, 18, 12), SKIN)
    head = body.bone("head", (0, 44, 0))
    head.cube("head", (-6, 44, -6), (12, 12, 12), SKIN, detail=face)
    head.cube("hair", (-6.5, 52, -6), (13, 5, 13), HAIR, inflate=0.3)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = body.bone(f"{side}_arm", (12 * s, 42, 0), (-AT_RIM, 0, 0) if s == 1 else (0, 0, 0))
        arm.cube(f"{side}_arm", (x(10, 7), 18, -3.5), (7, 24, 7), SKIN, mirror=s == -1)
        arm.cube(f"{side}_hand", (x(9, 9), 14, -4.5), (9, 4, 9), SKIN, mirror=s == -1)
    animations(m)
    return m


ZERO = (0, 0, 0)
# His right arm rests on the rim of the Well (the mesh's own pose, AT_RIM); the animations move it from there. The
# reach below works in angles with "forward" negative; Blockbench's +X swings a hanging arm forward (and tips a body
# back), so every X angle is negated on its way into the model (keys and the mesh's pose alike).
AT_RIM = -110
# Where his right hand (its centre) goes, in pixels from his hips: (seconds, forward, up). The hand lifts you off the
# rim, then goes straight down the cleft his arm has worn in the cliff (Antaeus builds it) to the ice. AntaeusEntity moves whoever rides in it along the
# same points (LOWER_PATH and RAISE_PATH there): change both together.
LOWER_PATH = [(0.0, 24.43, 26.89), (0.5, 24.43, 26.89), (1.25, 22, 32), (3.5, 24.43, -20), (4.0, 24.43, -20)]
RAISE_PATH = [(0.0, 24.43, -20), (0.25, 24.43, -20), (2.4, 22, 32), (2.75, 24.43, 26.89), (3.0, 24.43, 26.89)]
SHOULDER = 18  # hips to shoulder
ARM = 26  # shoulder to the middle of the hand


def reach(fwd, up, near):
    """The bend of his body and the swing of his arm (degrees; forward bend positive, the arm's world angle negative
    when raised forward) that put his hand at (fwd, up): the solution nearest the body bend `near`."""
    best = None
    for i in range(-900, 901):
        b = i / 10
        sf = SHOULDER * math.sin(math.radians(b))
        su = SHOULDER * math.cos(math.radians(b))
        dist = math.hypot(fwd - sf, up - su)
        err = abs(dist - ARM)
        if err < 0.25:
            score = abs(b - near) + err * 40
            if best is None or score < best[0]:
                theta = math.degrees(math.atan2(-(fwd - sf), -(up - su)))
                best = (score, b, theta)
    assert best, f"out of reach: {fwd}, {up}"
    return best[1], best[2]


def follow(anim, path, start_bend):
    """Keys the body and right arm every quarter second along the hand's path (linear between keys)."""
    keys_body, keys_arm = [], []
    bend = start_bend
    t = 0.0
    end = path[-1][0]
    while t <= end + 1e-6:
        for (t0, f0, u0), (t1, f1, u1) in zip(path, path[1:]):
            if t0 <= t <= t1:
                k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                fwd, up = f0 + (f1 - f0) * k, u0 + (u1 - u0) * k
                break
        bend, theta = reach(fwd, up, bend)
        keys_body.append((round(t, 3), (round(bend, 2), 0, 0)))
        keys_arm.append((round(t, 3), (round(theta - bend - AT_RIM, 2), 0, 0)))
        t += 0.25
    anim.rot("body", [(t, (-b, y, z)) for t, (b, y, z) in keys_body], interp="linear")
    anim.rot("right_arm", [(t, (-a, y, z)) for t, (a, y, z) in keys_arm], interp="linear")


def animations(m):
    # waiting: breathing slowly, his right palm up on the rim of the Well
    idle = m.animation("idle", 5.0, loop=True)
    idle.rot("left_arm", [(0, (0, 0, -8)), (2.5, (0, 0, -10)), (5.0, (0, 0, -8))])
    idle.pos("body", [(0, ZERO), (2.5, (0, 0.6, 0)), (5.0, ZERO)])
    idle.rot("head", [(0, (10, 0, 0)), (2.5, (12, 6, 0)), (5.0, (10, 0, 0))])

    # carrying you down: the hand lifts you over the brink, he bends, and sets you on the ice
    lower = m.animation("lower", 4.0)
    follow(lower, LOWER_PATH, 0)
    lower.rot("head", [(0, ZERO), (1.25, (5, 0, 0)), (3.5, (-25, 0, 0)), (4.0, (-25, 0, 0))])
    lower.event(0.5, "take").event(3.5, "set")

    # holding his hand down at the ice, for whoever wants to go up
    low = m.animation("low", 2.0, loop=True)
    follow(low, [(0.0, 24.43, -20), (2.0, 24.43, -20)], 60)
    low.rot("head", [(0, (-25, 0, 0)), (2.0, (-25, 0, 0))])

    # and up again, like a mast on a ship
    raise_ = m.animation("raise", 3.0)
    follow(raise_, RAISE_PATH, 60)
    raise_.rot("head", [(0, (-25, 0, 0)), (2.4, (5, 0, 0)), (2.75, ZERO), (3.0, ZERO)])
    raise_.event(0.25, "take").event(2.75, "set")


if __name__ == "__main__":
    build().write()
