#!/usr/bin/env python3
"""Lucifer's true form (Inferno XXXIV): "The Emperor of the kingdom dolorous from his mid-breast forth issued from the
ice", a colossus with three faces on one head (the front one red, the right between white and yellow, the left black),
under each face two great wings, "not feathered, but like a bat's", whose beating freezes Cocytus; in each mouth a
sinner. He weeps with six eyes.

Run from the repo root:  python3 tools/art/emperor.py && python3 tools/gen_bosses.py

He is drawn 10 times his Blockbench size: chest-deep in the ice his chest is 25 blocks across, his head 12, and his
wings reach about 60 blocks up. His wings turn slowly in the fog like a windmill's sails (IDLE, under everything):
what Dante saw first. Asleep (DORMANT) his head is bowed and his eyes shut. The events time his fight (LuciferFight / EmperorAttacks):
"slam" when a hand comes down and "lift" when it rises again (between them the hand lies on the ice, a weak point);
"flash" opens the window to block a bite, "bite" when it lands and "rise" when the face lifts again (between them the
face is down within reach); "beat" his wingbeat; "weep" his tears; "open" the moment his eyes open; "freeze" his end.

Where the hands and faces come down is worked out here (reach()) and copied into EmperorEntity: keep them in step.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SCALE = 10.0
RED = material("red", "skin", base="8a1a14", dark="4a0806")
PALE = material("pale", "skin", base="c8b878", dark="7a6a38")
BLACK = material("black", "skin", base="1a1414", dark="060404")
HIDE = material("hide", "skin", base="3a2a2e", dark="1a1214")
FROST = material("frost", "flat", base="c8e0f0")
WING = material("wing", "hair", light="2a1e22", dark="0a0608")
HORN = material("horn", "horn", root="4a3a30", tip="0e0a08")
EYE = hexc("ffe8a0")
MOUTH = hexc("0a0202")
TEAR = hexc("a8d8ff")

# the shapes the animations and reach() share (pixels)
SHOULDER_Y = 14
SHOULDER_X = 22
ARM = 27  # shoulder to the middle of the hand
NECK_Y = 22
HEAD = 10  # neck to the middle of the head
AT_REST = 30  # the arms' rest: forward-down into the ice
SLAM = 66  # a hand on the ice in front of him
BEND = 55  # how far he bends to bite
NOD = 35  # and his neck
# (Blockbench's +X swings a hanging arm forward and tips a body or head back: the slams use +, the bites -.)


def face_painter(material_, eye_row=6):
    def paint(faces):
        f = faces["front"]
        for y in range(f.h):
            for x in range(f.w):
                # a gaping mouth with a sinner's legs kicking in it
                if f.h - 7 <= y <= f.h - 3 and 4 <= x <= f.w - 5:
                    f.px(x, y, MOUTH if not (y == f.h - 3 and x in (8, 11)) else shade(HIDE.c["base"], 1.4))
        for cx in (3, f.w - 6):
            for dy in range(2):
                f.px(cx, eye_row + dy, shade(material_.c["dark"], 0.5))
                f.px(cx + 1, eye_row + dy, shade(material_.c["dark"], 0.5))
                f.px(cx + 2, eye_row + dy, shade(material_.c["dark"], 0.5))
            # tears frozen on his cheeks
            for dy in range(2, 6):
                f.px(cx + 1, eye_row + dy, TEAR)
    return paint


def eye_glow(faces):
    for name in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EYE)


def frosted(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if y > f.h - 6 and (x + 2 * y) % 3 == 0:
                    f.px(x, y, FROST.c["base"])


def ribbed(faces):
    for name in ("front", "back"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if x % 10 == 0 or y == 0:
                    f.px(x, y, shade(WING.c["light"], 1.5))
                elif y > f.h - 3 and x % 10 > 6:
                    f.px(x, y, (0, 0, 0), alpha=0)


def build():
    m = Model("emperor", texture_width=256)
    root = m.bone("root", (0, 0, 0))
    upper = root.bone("upper", (0, 0, 0))
    upper.cube("chest", (-20, -8, -10), (40, 28, 20), HIDE, detail=frosted)
    upper.cube("shoulders", (-24, 12, -8), (48, 8, 16), HIDE)
    neck = upper.bone("neck", (0, NECK_Y, 0))
    neck.cube("neck", (-6, 18, -6), (12, 6, 12), HIDE)
    head = neck.bone("head", (0, NECK_Y, 0))
    # one head, three faces: red in front, pale on the right, black on the left (his right is +X)
    head.cube("head", (-10, 22, -10), (20, 20, 20), RED, detail=face_painter(RED))
    head.cube("face_right", (10, 23, -8), (1, 18, 16), PALE, detail=face_painter(PALE))
    head.cube("face_left", (-11, 23, -8), (1, 18, 16), BLACK, detail=face_painter(BLACK))
    for side, s in (("right", 1), ("left", -1)):
        head.cube(f"{side}_horn", (6 * s - 1.5, 42, -2), (3, 8, 3), HORN, rotation=(0, 0, -20 * s), origin=(6 * s, 42, 0))
    # his six eyes (shown once they open)
    eyes = head.bone("eyes", (0, 30, 0))
    for x in (-7, 3):
        eyes.cube(f"eye_front{x}", (x, 34, -10.5), (4, 2, 1), FROST, detail=eye_glow)
    for z in (-6, 2):
        eyes.cube(f"eye_right{z}", (10.5, 34, z), (1, 2, 4), FROST, detail=eye_glow)
        eyes.cube(f"eye_left{z}", (-11.5, 34, z), (1, 2, 4), FROST, detail=eye_glow)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = upper.bone(f"{side}_arm", (SHOULDER_X * s, SHOULDER_Y, 0), rotation=(AT_REST, 0, 0))
        arm.cube(f"{side}_arm", (x(20, 8), -10, -4), (8, 24, 8), HIDE, mirror=s == -1, detail=frosted)
        arm.cube(f"{side}_hand", (x(18, 12), -16, -6), (12, 6, 12), HIDE, mirror=s == -1)
        arm.cube(f"{side}_claws", (x(18, 12), -18, -7), (12, 2, 3), HORN, mirror=s == -1)
        # three great bat's wings on each side, rising behind him
        for i, (y, spread) in enumerate([(20, 55), (16, 20), (12, -15)]):
            wing = upper.bone(f"{side}_wing{i}", (16 * s, y, 8), rotation=(0, -15 * s, spread * s))
            wing.cube(f"{side}_wing{i}", (x(16, 44), y - 2, 8), (44, 30, 1), WING, mirror=s == -1, detail=ribbed)
    animations(m)
    return m


ZERO = (0, 0, 0)


def windmill(a, length, amount=25, phase=0.0):
    """His wings turning: each pair beats a little after the one above it."""
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            t0 = (phase + i * 0.15) * length
            keys = [(0, (0, 0, 0)), (length * 0.5, (0, 0, -amount * s)), (length, (0, 0, 0))]
            a.rot(f"{side}_wing{i}", [(t, (vx, vy, vz)) for t, (vx, vy, vz) in keys])
            del t0


def rest_arms(a, length):
    for side in ("right", "left"):
        a.rot(f"{side}_arm", [(0, ZERO), (length, ZERO)])


def animations(m):
    # always (under everything else): breathing, the wings turning slow and cold like a windmill's sails
    idle = m.animation("idle", 5.0, loop=True)
    windmill(idle, 5.0, 25)
    idle.pos("upper", [(0, ZERO), (2.5, (0, 1, 0)), (5.0, ZERO)])

    # asleep: head bowed (his eyes are shut: the game hides them), the wings still turning in the fog
    dormant = m.animation("dormant", 8.0, loop=True)
    dormant.rot("head", [(0, (-15, 0, 0)), (8.0, (-15, 0, 0))])

    # a hand raised high and brought down on the ice; it lies there until it lifts (a weak point)
    for side, s in (("right", 1), ("left", -1)):
        slam = m.animation(f"slam_{side}", 5.0)
        slam.rot(f"{side}_arm", [(0, ZERO), (1.2, (170 - AT_REST, 0, 10 * s)), (1.6, (SLAM - AT_REST, 0, 0)), (4.2, (SLAM - AT_REST, 0, 0)),
                                 (5.0, ZERO)], interp="linear")
        slam.rot("upper", [(0, ZERO), (1.2, (8, 8 * s, 0)), (1.6, (-12, -6 * s, 0)), (4.2, (-12, -6 * s, 0)), (5.0, ZERO)])
        slam.event(1.6, "slam").event(4.2, "lift")

    # a bite: he bends, a face comes down to the ice, flashes, bites, and stays low a moment (a weak point)
    for name, yaw in (("bite", 0), ("bite_right", 90), ("bite_left", -90)):
        bite = m.animation(name, 5.0)
        bite.rot("upper", [(0, ZERO), (1.4, (-BEND, 0, 0)), (4.0, (-BEND, 0, 0)), (5.0, ZERO)])
        bite.rot("neck", [(0, ZERO), (1.4, (-NOD, 0, 0)), (4.0, (-NOD, 0, 0)), (5.0, ZERO)])
        bite.rot("head", [(0, ZERO), (0.8, (0, yaw, 0)), (1.5, (10, yaw, 0)), (1.6, (-10, yaw, 0)), (4.0, (-10, yaw, 0)), (5.0, ZERO)])
        bite.event(1.0, "flash").event(1.5, "bite").event(4.0, "rise")

    # his wingbeat: the cold wind across the whole pit
    beat = m.animation("wingbeat", 3.0)
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            beat.rot(f"{side}_wing{i}", [(0, ZERO), (1.2, (0, -30 * s, 40 * s)), (1.6, (0, 30 * s, -50 * s)), (3.0, ZERO)])
    beat.rot("upper", [(0, ZERO), (1.2, (10, 0, 0)), (1.6, (-8, 0, 0)), (3.0, ZERO)])
    beat.event(1.6, "beat")

    # half health: he weeps with six eyes, and the tears fall as frozen blood
    weep = m.animation("weep", 4.0)
    windmill(weep, 2.0, 30)
    weep.rot("head", [(0, ZERO), (1.0, (25, 0, 0)), (3.0, (25, 0, 0)), (4.0, ZERO)])
    weep.event(1.5, "weep")

    # the reveal: the head lifts and six eyes open
    reveal = m.animation("reveal", 6.0)
    windmill(reveal, 3.0, 40)
    reveal.rot("head", [(0, (-15, 0, 0)), (2.0, (-15, 0, 0)), (3.0, (20, 0, 0)), (6.0, ZERO)])
    reveal.rot("upper", [(0, ZERO), (3.0, (10, 0, 0)), (6.0, ZERO)])
    reveal.event(2.5, "open").event(4.0, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("upper", [(0, ZERO), (0.15, (3, 0, 0)), (0.4, ZERO)])

    # his end: he freezes, head bowed, the wings folding down, and weeps
    death = m.animation("death", 6.0)
    death.rot("head", [(0, ZERO), (3.0, (-35, 0, 0)), (6.0, (-35, 0, 0))])
    death.rot("upper", [(0, ZERO), (3.0, (-10, 0, 0)), (6.0, (-10, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            death.rot(f"{side}_wing{i}", [(0, ZERO), (3.0, (0, 0, -60 * s)), (6.0, (0, 0, -60 * s))])
    death.event(3.0, "freeze")


def reach():
    """Where a slammed hand and a biting head come to rest, in blocks from his feet (forward, up, to his right)."""
    px = SCALE / 16.0
    th = math.radians(SLAM)
    hand_fwd = ARM * math.sin(th)
    hand_up = SHOULDER_Y - ARM * math.cos(th)
    print("slam hand: forward %.2f up %.2f side %.2f" % (hand_fwd * px, hand_up * px, (SHOULDER_X + 2) * px))
    b = math.radians(BEND)
    n = math.radians(BEND + NOD)
    head_fwd = NECK_Y * math.sin(b) + HEAD * math.sin(n)
    head_up = NECK_Y * math.cos(b) + HEAD * math.cos(n)
    print("bite head: forward %.2f up %.2f" % (head_fwd * px, head_up * px))


if __name__ == "__main__":
    build().write()
    reach()
