#!/usr/bin/env python3
"""Malacoda, Evil-Tail (Inferno XXI), captain of the Malebranche: taller than his devils, with great curled horns, a
ragged red cape, a long forked tail and a staff with a hook. He is all courtesy: he offers to lend you an escort to
the next bridge ("the sixth bridge is all in pieces; go on along this ridge, there's another"), and it's a lie, as
it was to Virgil. He leads you into his devils' ambush and slips away laughing.

Run from the repo root:  python3 tools/art/malacoda.py && python3 tools/gen_bosses.py

Animations: idle, walk, "talk" (a courtly gesture), "point" (the way: "this way"), "laugh" (the trap is sprung:
"signal" is when he gives it), hurt, and death (he isn't killed: he flaps off; we play this if he is).

He is built like his devils, only taller and finer: a beast's legs (forward thigh, backward hock, clawed feet), a
long hard torso under a belt with a gold buckle, gold-banded forearms, a long courtier's face with a hooked nose, a
pointed beard and a wide smile full of teeth (his jaw drops when he laughs), pointed ears, and great horns that sweep
up and curl back. The cape hangs from a standing collar and a gold chain, its hem in rags. The right hand points
(one long finger); the left holds the staff upright, a captain's crook of iron with a barbed hook.

The staff is its own bone (a child of the body, not the arm), so every animation that moves the left arm also keys
the staff from the same motion (carry(), sampled every tick): the staff stays in the fist.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

HIDE = material("hide", "skin", base="3a1e1e", dark="1a0a0a")
HORN = material("horn", "horn", root="c0b090", tip="2a2018")
CLAW = material("claw", "horn", root="c8b898", tip="2a2018")
CAPE = material("cape", "robe", base="7a1414", fold="4a0a0a", trim="c8a040")
LINING = material("lining", "robe", base="3a0808", fold="240404")
GOLD = material("gold", "metal", base="c8a040", high="f0d888", low="6a4a14")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
SHAFT = material("shaft", "hair", light="6a5a40", dark="2a2014")
BEARD = material("beard", "hair", light="3a2a26", dark="120a0a")
BIND = material("bind", "flat", base="5a1010")
EYE = hexc("ffa020")
EYE_HOT = hexc("ffe0a0")
GRIN = hexc("f0e8d0")
MOUTH = hexc("1a0606")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def side_x(a, w, s):
    """A cube from a to a + w on the right becomes -(a + w) to -a on the left."""
    return a if s == 1 else -(a + w)


def seg(bone, name, p0, p1, w, h, mat, extra=0.0, **kw):
    """A cube laid along the line p0 -> p1 (its length on its own Z axis), w across and h thick, turned about its own
    centre; extra lengthens it so that segments overlap at their joints."""
    d = [p1[i] - p0[i] for i in range(3)]
    length = math.sqrt(sum(v * v for v in d))
    size = max(1, math.ceil(length + extra - 1e-6))
    c = [round((p0[i] + p1[i]) / 2, 3) for i in range(3)]
    pitch = -math.degrees(math.asin(max(-1.0, min(1.0, d[1] / length))))
    yaw = math.degrees(math.atan2(d[0], d[2]))
    return bone.cube(name, (c[0] - w / 2, c[1] - h / 2, c[2] - size / 2), (w, h, size), mat,
                     rotation=(round(pitch, 3), round(yaw, 3), 0), origin=tuple(c), **kw)


def mul(face, fx, fy, f):
    """Darkens (f < 1) or lightens one already painted pixel."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def ao(bottom=0, top=0, dark=0.7, faces=SIDES):
    """Side faces shaded along their lowest rows (where a part sinks into shadow) and their highest."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            for i in range(min(bottom, f.h)):
                for fx in range(f.w):
                    mul(f, fx, f.h - 1 - i, dark + (1 - dark) * i / max(1, bottom))
            for i in range(min(top, f.h)):
                for fx in range(f.w):
                    mul(f, fx, i, dark + (1 - dark) * i / max(1, top))
    return detail


def both(*details):
    def detail(fs):
        for d in details:
            d(fs)
    return detail


# rotations as Blockbench and Minecraft apply them (Z, then Y, then X), and turning about a point
def rot3(deg):
    x, y, z = (math.radians(v) for v in deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat3(rz, mat3(ry, rx))


def mat3(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def about(deg, origin):
    """A rigid motion (R, t): turn by deg about origin."""
    r = rot3(deg)
    o = origin
    return r, [o[i] - sum(r[i][k] * o[k] for k in range(3)) for i in range(3)]


def compose(a, b):
    """a after b."""
    ra, ta = a
    rb, tb = b
    return mat3(ra, rb), [sum(ra[i][k] * tb[k] for k in range(3)) + ta[i] for i in range(3)]


def invert(a):
    r, t = a
    rt = [[r[j][i] for j in range(3)] for i in range(3)]
    return rt, [-sum(rt[i][k] * t[k] for k in range(3)) for i in range(3)]


def apply(a, p):
    r, t = a
    return [sum(r[i][k] * p[k] for k in range(3)) + t[i] for i in range(3)]


def euler(r):
    """The (x, y, z) degrees that rot3 turns back into r."""
    y = -math.asin(max(-1.0, min(1.0, r[2][0])))
    x = math.atan2(r[2][1], r[2][2])
    z = math.atan2(r[1][0], r[0][0])
    return [math.degrees(x), math.degrees(y), math.degrees(z)]


def spline(keys, t):
    """Catmull-Rom through (time, (x, y, z)) keys, as the game plays them."""
    if not keys:
        return (0.0, 0.0, 0.0)
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = max(j for j in range(len(keys)) if keys[j][0] <= t)
    a, b = keys[i][0], keys[i + 1][0]
    f = (t - a) / (b - a)
    p0, p1, p2, p3 = keys[max(i - 1, 0)][1], keys[i][1], keys[i + 1][1], keys[min(i + 2, len(keys) - 1)][1]
    return tuple(0.5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * f + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * f * f
                        + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * f ** 3) for c in range(3))


class Grip:
    """A weapon that is its own bone but rides in a hand: the arm (shoulder pivot and rest turn), the forearm (elbow
    pivot and rest turn) and the weapon's bone (its pivot, the fist, and rest turn)."""

    def __init__(self, arm, arm_rest, fore, fore_rest, fist, weapon_rest):
        self.arm, self.arm_rest = arm, arm_rest
        self.fore, self.fore_rest = fore, fore_rest
        self.weapon_rest = weapon_rest
        self.pivot = [round(v, 3) for v in apply(self.hand(arm_rest, fore_rest), fist)]

    def hand(self, a, f):
        return compose(about(a, self.arm), about(f, self.fore))

    def carry(self, anim, arm_bone, fore_bone, weapon_bone, arm_keys, fore_keys, wrist_keys=None, step=0.05):
        """Keys the arm, the forearm and the weapon together, sampled every step: the arm and forearm follow their
        (time, offset) keys; the weapon follows the fist (turned in it by the wrist keys, about the fist)."""
        hand_rest = self.hand(self.arm_rest, self.fore_rest)
        weapon_rest = about(self.weapon_rest, self.pivot)
        n = int(round(anim.length / step))
        prev = None
        arm_out, fore_out, rot_out, pos_out = [], [], [], []
        for i in range(n + 1):
            t = round(min(anim.length, i * step), 4)
            a = spline(arm_keys, t)
            f = spline(fore_keys, t)
            w = spline(wrist_keys or [], t)
            hand = self.hand([self.arm_rest[k] + a[k] for k in range(3)], [self.fore_rest[k] + f[k] for k in range(3)])
            motion = compose(compose(hand, invert(hand_rest)), compose(about(w, self.pivot), weapon_rest))
            e = euler(motion[0])
            if prev is not None:  # keep the angles continuous from one sample to the next
                e = [e[k] + 360 * round((prev[k] - e[k]) / 360) for k in range(3)]
            prev = e
            moved = apply(motion, self.pivot)
            arm_out.append((t, a))
            fore_out.append((t, f))
            rot_out.append((t, tuple(round(e[k] - self.weapon_rest[k], 3) for k in range(3))))
            pos_out.append((t, tuple(round(moved[k] - self.pivot[k], 3) for k in range(3))))
        anim.rot(arm_bone, [(t, tuple(round(v, 3) for v in a)) for t, a in arm_out], "linear")
        anim.rot(fore_bone, [(t, tuple(round(v, 3) for v in f)) for t, f in fore_out], "linear")
        anim.rot(weapon_bone, rot_out, "linear")
        anim.pos(weapon_bone, pos_out, "linear")




# ----------------------------------------------------------------------------------------------- details

def face_detail(fs):
    """Eyes like embers under the brows, the cheeks hollow, the smile lines either side of the mouth."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.1)
    for cx in (1, f.w - 3):
        f.px(cx, 2, MOUTH)
        f.px(cx + 1, 2, MOUTH)
        f.glow(cx, 3, EYE if cx > 1 else EYE_HOT)
        f.glow(cx + 1, 3, EYE_HOT if cx > 1 else EYE)
        mul(f, cx, 4, 0.7)
        mul(f, cx + 1, 4, 0.7)
    for y in (4, 5):
        mul(f, 0, y, 0.7)
        mul(f, f.w - 1, y, 0.7)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(3, g.h):
            mul(g, g.w - 2 if n == "right" else 1, y, 0.8)


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.25)
    fs["bottom"].fill(lambda x, y: MOUTH)


def nose_detail(fs):
    f = fs["front"]
    for y in range(f.h):
        mul(f, 0, y, 1.15)
    fs["bottom"].fill(lambda x, y: MOUTH)


def grin_detail(fs):
    """The courtly smile: a full row of teeth, the corners turned up."""
    f = fs["front"]
    f.fill(lambda x, y: GRIN if x % 2 == 0 else shade(GRIN, 0.82))
    f.px(0, 0, MOUTH)
    f.px(f.w - 1, 0, MOUTH)
    fs["bottom"].fill(lambda x, y: MOUTH)


def jaw_detail(fs):
    """The lower teeth along the jaw's top edge, the dark mouth behind."""
    t = fs["top"]
    t.fill(lambda x, y: MOUTH)
    for x in range(t.w):
        t.px(x, t.h - 1, GRIN)
    f = fs["front"]
    for x in range(1, f.w - 1):
        f.px(x, 0, GRIN if x % 2 else shade(GRIN, 0.8))
    b = fs["bottom"]
    for y in range(b.h):
        for x in range(b.w):
            mul(b, x, y, 0.7)


def ear_detail(fs):
    for n in ("left", "right", "front"):
        f = fs[n]
        for y in range(1, f.h - 1):
            mul(f, f.w // 2, y, 0.55)


def chest_detail(fs):
    """A hard chest: the breastbone, ribs along the flanks."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.75)
    for n in ("left", "right"):
        g = fs[n]
        for y in (1, 3):
            for x in range(g.w):
                mul(g, x, y, 0.78)


def pec_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.2)
        mul(f, x, f.h - 1, 0.75)


def abs_detail(fs):
    """Ridged belly muscles: the line down the middle, rows across."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.72)
    for y in range(0, f.h, 2):
        for x in range(f.w):
            mul(f, x, y, 0.8)


def muscle_detail(fs):
    """A lit ridge down the front of a limb, shadow at the back."""
    f = fs["front"]
    for y in range(f.h):
        mul(f, f.w // 2, y, 1.18)
    b = fs["back"]
    for y in range(b.h):
        for x in range(b.w):
            mul(b, x, y, 0.85)


def rags(fs):
    """A ragged hem: notches torn out of the lowest rows, a few holes higher up."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(f.w):
            depth = (x * 7 + f.w * 3) % 4
            for i in range(depth):
                f.px(x, f.h - 1 - i, (0, 0, 0), alpha=0)
        if f.h > 10 and f.w > 4:
            f.px(f.w // 3, f.h - 7, (0, 0, 0), alpha=0)


def collar_detail(fs):
    for n in ("front", "back"):
        f = fs[n]
        for x in range(f.w):
            f.px(x, 0, GOLD.c["base"])


def beard_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.7)


def iron_hook_detail(fs):
    """Rust on the iron."""
    for n in SIDES:
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                if (x * 5 + y * 3) % 7 == 0:
                    f.px(x, y, hexc("5a3020"))


def bind_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            for y in range(f.h):
                if (x + y) % 2:
                    mul(f, x, y, 0.7)


# ----------------------------------------------------------------------------------------------- the model

ARM_REST = (10, 0, 0)       # the left arm a little forward,
FORE_REST = (75, 0, 0)      # the forearm level: the fist out in front, holding the staff upright
GRIP = Grip((-6, 27, 0), ARM_REST, (-6, 20.5, 0), FORE_REST, (-6, 13.5, 0), (0, 0, 0))


def build():
    m = Model("malacoda", texture_width=128)

    # --- the legs: a beast's, the thigh forward, the hock back, a long foot on three clawed toes
    for side, s in (("right", 1), ("left", -1)):
        leg = m.bone(f"{side}_leg", (2 * s, 15, 0))
        seg(leg, f"{side}_thigh", (2.2 * s, 16, 0.3), (2.5 * s, 9.6, -2.3), 4, 4, HIDE, extra=1, detail=muscle_detail)
        shin = leg.bone(f"{side}_shin", (2.5 * s, 9.5, -2.2))
        shin.cube(f"{side}_knee", (side_x(1, 3, s), 8.6, -3.9), (3, 2, 2), HIDE, mirror=s == -1)
        seg(shin, f"{side}_shin", (2.5 * s, 9.4, -2.2), (2.5 * s, 4.4, 2.0), 3, 3, HIDE, extra=1, detail=muscle_detail)
        seg(shin, f"{side}_hock", (2.5 * s, 4.6, 2.0), (2.5 * s, 1.0, -1.6), 2, 2, HIDE, extra=0.5)
        shin.cube(f"{side}_spur", (side_x(2, 1, s), 4, 2.5), (1, 1, 1), CLAW, mirror=s == -1)
        shin.cube(f"{side}_foot", (side_x(0.5, 4, s), 0, -4.2), (4, 1, 4), HIDE, mirror=s == -1)
        for i, (tx, tz) in enumerate(((0.6, -5.6), (2.0, -6.0), (3.4, -5.6))):
            shin.cube(f"{side}_toe_{i}", (side_x(tx, 1, s), 0, tz), (1, 1, 2), CLAW, mirror=s == -1)

    # --- the body: narrow hips under a gold-buckled belt, a ridged belly, a deep chest; the loincloth
    body = m.bone("body", (0, 15, 0))
    body.cube("pelvis", (-3.5, 14, -2.5), (7, 4, 5), HIDE)
    body.cube("belly", (-3, 17.5, -2.2), (6, 4, 4), HIDE, detail=abs_detail)
    body.cube("torso", (-4.5, 21.5, -2.6), (9, 5, 5), HIDE, detail=chest_detail)
    body.cube("traps", (-3.5, 26, -1.8), (7, 2, 4), HIDE, detail=ao(top=1, dark=0.85))
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_pec", (side_x(0, 4, s), 22.8, -3.3), (4, 3, 1), HIDE, mirror=s == -1,
                  rotation=(-6, 0, 0), origin=(0, 25.8, -2.8), detail=pec_detail)
    body.cube("belt", (-3.5, 17.4, -2.5), (7, 1, 5), CAPE, inflate=0.25)
    body.cube("buckle", (-1, 17, -3.2), (2, 2, 1), GOLD)
    body.cube("loin_front", (-2, 11.6, -3), (4, 6, 1), CAPE, rotation=(-5, 0, 0), origin=(0, 17.6, -2.6))
    body.cube("loin_back", (-2, 12.6, 2.1), (4, 5, 1), CAPE, rotation=(6, 0, 0), origin=(0, 17.6, 2.6))

    # --- the cape: a standing collar, a gold chain across the chest, ragged red falling to the calves
    body.cube("collar", (-4, 26.6, 2.2), (8, 4, 1), CAPE, rotation=(18, 0, 0), origin=(0, 27, 2.6), detail=collar_detail)
    body.cube("chain", (-3.5, 26.6, -3), (7, 1, 1), GOLD, inflate=-0.15)
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_clasp", (side_x(2.6, 2, s), 26.1, -3.4), (2, 2, 1), GOLD, mirror=s == -1)
        body.cube(f"{side}_cape", (side_x(3.6, 3, s), 7.6, 2.4), (3, 19, 1), CAPE, mirror=s == -1,
                  rotation=(-6, 20 * s, 0), origin=(4.6 * s, 26.6, 2.8), detail=rags)
    body.cube("cape", (-4, 6.6, 2.8), (8, 21, 1), CAPE, rotation=(-6, 0, 0), origin=(0, 27.4, 3.2), detail=rags)
    body.cube("cape_lining", (-3.5, 9.6, 2.3), (7, 17, 1), LINING, rotation=(-6, 0, 0), origin=(0, 27.4, 3.2))

    # --- the long forked tail, out from under the cape and curling to one side
    tail = body.bone("tail", (0, 16, 2))
    seg(tail, "tail", (0, 16, 2), (0, 12.6, 4.6), 2, 2, HIDE, extra=1)
    seg(tail, "tail_mid", (0, 12.6, 4.6), (0.8, 9, 6.8), 2, 2, HIDE, extra=1)
    seg(tail, "tail_low", (0.8, 9, 6.8), (2.6, 5.6, 9.4), 1, 1, HIDE, extra=1)
    seg(tail, "tail_end", (2.6, 5.6, 9.4), (4.8, 3.6, 12), 1, 1, HIDE, extra=1)
    seg(tail, "tail_fork", (4.8, 3.6, 12), (5.4, 3.2, 14.4), 1, 1, CLAW, extra=0.3)
    seg(tail, "tail_fork_out", (4.8, 3.6, 12), (7.2, 3.4, 13.4), 1, 1, CLAW, extra=0.3)

    # --- the head: a long courtier's face, hooked nose, pointed beard, the smile; great curled horns
    head = body.bone("head", (0, 28, 0))
    head.cube("neck", (-1.5, 26.5, -1.8), (3, 3, 3), HIDE, detail=ao(top=1, dark=0.6))
    head.cube("head", (-3, 29, -3), (6, 6, 6), HIDE, detail=face_detail)
    head.cube("nose", (-0.5, 30.4, -3.9), (1, 2, 1), HIDE, detail=nose_detail)
    head.cube("nose_tip", (-0.5, 30.1, -4.4), (1, 1, 1), HIDE, inflate=-0.1)
    head.cube("grin", (-2, 29.6, -3.5), (4, 1, 1), HIDE, detail=grin_detail)
    jaw = head.bone("jaw", (0, 29.6, -1), rotation=(-6, 0, 0))
    jaw.cube("jaw", (-2.5, 28.6, -3.4), (5, 1, 4), HIDE, detail=jaw_detail)
    jaw.cube("beard", (-1, 26.2, -3.6), (2, 3, 1), BEARD, rotation=(-12, 0, 0), origin=(0, 28.6, -3.1), detail=beard_detail)
    jaw.cube("beard_tip", (-0.5, 25.2, -3.2), (1, 1, 1), BEARD)
    for side, s in (("right", 1), ("left", -1)):
        head.cube(f"{side}_brow", (side_x(0.2, 3, s), 32.1, -3.6), (3, 1, 1), HIDE, mirror=s == -1,
                  rotation=(0, 0, 14 * s), origin=(1.7 * s, 32.6, -3.1), detail=brow_detail)
        head.cube(f"{side}_cheek", (side_x(1.8, 1, s), 30.2, -3.3), (1, 1, 1), HIDE, mirror=s == -1)
        seg(head, f"{side}_ear", (2.8 * s, 31.6, 0.2), (5.0 * s, 33.0, 1.8), 1, 2, HIDE, extra=0.3, detail=ear_detail)
        horn = head.bone(f"{side}_horn", (3 * s, 34, -1))
        seg(horn, f"{side}_horn", (2.4 * s, 33.6, -1.0), (4.6 * s, 35.2, -0.6), 2, 2, HORN, extra=1)
        seg(horn, f"{side}_horn_rise", (4.6 * s, 35.2, -0.6), (6.2 * s, 37.4, 0.2), 2, 2, HORN, extra=1)
        seg(horn, f"{side}_horn_tip", (6.2 * s, 37.4, 0.2), (6.6 * s, 38.8, 2.0), 1, 1, HORN, extra=1)
        seg(horn, f"{side}_horn_curl", (6.6 * s, 38.8, 2.0), (6.2 * s, 38.2, 3.6), 1, 1, HORN, extra=0.3)

    # --- the arms: heavy shoulders, gold-banded forearms; the right hand points, the left holds the staff
    for side, s in (("right", 1), ("left", -1)):
        arm = body.bone(f"{side}_arm", (6 * s, 27, 0), rotation=(0, 0, 0) if s == 1 else ARM_REST)
        arm.cube(f"{side}_shoulder", (side_x(4, 4, s), 24, -2), (4, 4, 4), HIDE, inflate=0.1, mirror=s == -1)
        arm.cube(f"{side}_arm", (side_x(4.5, 3, s), 20, -1.5), (3, 6, 3), HIDE, mirror=s == -1, detail=muscle_detail)
        fore = arm.bone(f"{side}_forearm", (6 * s, 20.5, 0), rotation=(4, 0, 0) if s == 1 else FORE_REST)
        fore.cube(f"{side}_elbow", (side_x(5, 2, s), 19.4, 0.4), (2, 2, 2), HIDE, mirror=s == -1)
        fore.cube(f"{side}_forearm", (side_x(4.5, 3, s), 16.4, -1.5), (3, 4, 3), HIDE, mirror=s == -1, detail=muscle_detail)
        fore.cube(f"{side}_bracer", (side_x(4.5, 3, s), 16.6, -1.5), (3, 2, 3), GOLD, inflate=0.3, mirror=s == -1)
        fore.cube(f"{side}_wrist", (side_x(5, 2, s), 14.8, -1), (2, 2, 2), HIDE, mirror=s == -1)
        if s == 1:
            fore.cube("right_hand", (5, 12.8, -1.5), (2, 2, 3), HIDE)
            fore.cube("right_finger", (5.2, 9.8, -1.6), (1, 3, 1), HIDE, detail=ao(bottom=1, dark=0.5))
            fore.cube("right_curled", (5.2, 12, -0.6), (1, 1, 2), HIDE)
            fore.cube("right_thumb", (4.4, 12.2, -1.8), (1, 2, 1), HIDE, rotation=(15, 0, 0), origin=(4.9, 14, -1.3))
        else:
            fore.cube("left_fist", (-7, 12.2, -1.5), (2, 3, 3), HIDE, inflate=0.1)
            fore.cube("left_knuckles", (-8, 12.4, -1.5), (1, 2, 3), HIDE)

    # --- the staff, upright in the left fist: a long shaft bound in red, a gold socket, an iron crook with a barb and a
    # spike (built straight up from the fist; the fist's rest pose already holds it upright)
    staff = body.bone("staff", tuple(GRIP.pivot))
    px, py, pz = GRIP.pivot
    staff.cube("staff", (px - 0.5, py - 19, pz - 0.5), (1, 19, 1), SHAFT)
    staff.cube("staff_top", (px - 0.5, py, pz - 0.5), (1, 14, 1), SHAFT)
    staff.cube("staff_foot", (px - 0.5, py - 19.5, pz - 0.5), (1, 1, 1), IRON, inflate=0.15)
    staff.cube("staff_binding", (px - 0.5, py + 2, pz - 0.5), (1, 3, 1), BIND, inflate=0.25, detail=bind_detail)
    staff.cube("staff_socket", (px - 1, py + 13.5, pz - 1), (2, 2, 2), GOLD)
    staff.cube("staff_spike", (px - 0.5, py + 15.5, pz - 0.5), (1, 3, 1), IRON, inflate=-0.1)
    staff.cube("staff_hook", (px - 0.5, py + 14.5, pz - 4), (1, 1, 3), IRON, detail=iron_hook_detail)
    staff.cube("staff_barb", (px - 0.5, py + 12.4, pz - 4), (1, 2, 1), IRON, detail=iron_hook_detail)
    staff.cube("staff_prong", (px - 0.5, py + 14.8, pz + 1), (1, 2, 1), IRON, rotation=(-35, 0, 0), origin=(px, py + 15, pz + 1))
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    idle = m.animation("idle", 2.4, loop=True)
    idle.rot("tail", [(0, (5, 0, 0)), (1.2, (5, 25, 0)), (2.4, (5, 0, 0))])
    idle.rot("head", [(0, ZERO), (1.2, (0, 12, 4)), (2.4, ZERO)])
    idle.rot("right_forearm", [(0, (10, 0, 0)), (1.2, (18, 0, 0)), (2.4, (10, 0, 0))])

    walk = m.animation("walk", 1.0, loop=True)
    walk.rot("right_leg", [(0, (30, 0, 0)), (0.5, (-30, 0, 0)), (1.0, (30, 0, 0))])
    walk.rot("left_leg", [(0, (-30, 0, 0)), (0.5, (30, 0, 0)), (1.0, (-30, 0, 0))])
    walk.rot("right_shin", [(0, (5, 0, 0)), (0.25, (-10, 0, 0)), (0.5, (-30, 0, 0)), (0.75, (-15, 0, 0)), (1.0, (5, 0, 0))])
    walk.rot("left_shin", [(0, (-30, 0, 0)), (0.25, (-15, 0, 0)), (0.5, (5, 0, 0)), (0.75, (-10, 0, 0)), (1.0, (-30, 0, 0))])
    walk.rot("right_arm", [(0, (20, 0, 0)), (0.5, (-20, 0, 0)), (1.0, (20, 0, 0))])
    walk.rot("right_forearm", [(0, (20, 0, 0)), (0.5, (8, 0, 0)), (1.0, (20, 0, 0))])
    walk.rot("tail", [(0, (5, -15, 0)), (0.5, (5, 15, 0)), (1.0, (5, -15, 0))])

    # a courtly gesture: hand to the breast, a little bow
    talk = m.animation("talk", 2.0)
    talk.rot("right_arm", [(0, ZERO), (0.4, (70, 40, 0)), (1.4, (70, 40, 0)), (2.0, ZERO)])
    talk.rot("right_forearm", [(0, ZERO), (0.4, (55, 0, 0)), (1.4, (55, 0, 0)), (2.0, ZERO)])
    talk.rot("body", [(0, ZERO), (0.6, (-15, 0, 0)), (1.2, (-15, 0, 0)), (2.0, ZERO)])
    talk.rot("head", [(0, ZERO), (0.6, (-10, 0, 0)), (2.0, ZERO)])
    talk.rot("jaw", [(0, ZERO), (0.5, (-12, 0, 0)), (0.7, ZERO), (0.9, (-12, 0, 0)), (1.1, ZERO), (1.3, (-10, 0, 0)), (2.0, ZERO)])

    # "this way": a long arm pointing down the ridge
    point = m.animation("point", 1.6)
    point.rot("right_arm", [(0, ZERO), (0.4, (95, 0, 0)), (1.2, (95, 0, 0)), (1.6, ZERO)])
    point.rot("right_forearm", [(0, ZERO), (0.4, (-4, 0, 0)), (1.2, (-4, 0, 0)), (1.6, ZERO)])
    point.rot("head", [(0, ZERO), (0.4, (0, -20, 0)), (1.2, (0, -20, 0)), (1.6, ZERO)])

    # the trap springs: he throws his head back and laughs, staff raised high to signal his devils
    laugh = m.animation("laugh", 2.4)
    laugh.rot("head", [(0, ZERO), (0.3, (40, 0, 0)), (0.5, (25, 0, 0)), (0.7, (40, 0, 0)), (0.9, (25, 0, 0)), (1.1, (40, 0, 0)), (2.4, ZERO)])
    laugh.rot("jaw", [(0, ZERO), (0.3, (-30, 0, 0)), (0.4, (-15, 0, 0)), (0.5, (-30, 0, 0)), (0.6, (-15, 0, 0)), (0.7, (-30, 0, 0)),
                      (0.8, (-15, 0, 0)), (0.9, (-30, 0, 0)), (1.1, (-30, 0, 0)), (2.4, ZERO)])
    GRIP.carry(laugh, "left_arm", "left_forearm", "staff",
               [(0, ZERO), (0.6, (150, 0, 0)), (1.0, (140, 0, 0)), (1.4, (150, 0, 0)), (1.8, (150, 0, 0)), (2.4, ZERO)],
               [(0, ZERO), (0.6, (-60, 0, 0)), (1.8, (-60, 0, 0)), (2.4, ZERO)],
               [(0, ZERO), (0.6, (-90, 0, 0)), (1.8, (-90, 0, 0)), (2.4, ZERO)])
    laugh.rot("body", [(0, ZERO), (0.3, (10, 0, 0)), (2.0, (10, 0, 0)), (2.4, ZERO)])
    laugh.event(0.6, "signal")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (12, 0, 0)), (0.3, ZERO)])
    hurt.rot("jaw", [(0, ZERO), (0.1, (-20, 0, 0)), (0.3, ZERO)])

    death = m.animation("death", 1.4)
    death.rot("body", [(0, ZERO), (0.6, (-70, 0, 0)), (1.4, (-85, 0, 0))])
    death.pos("body", [(0, ZERO), (0.6, (0, -8, -2)), (1.4, (0, -13, -3))])
    # the legs go down with the hips and trail out behind him
    for side, k in (("right", 1.0), ("left", 0.9)):
        death.rot(f"{side}_leg", [(0, ZERO), (0.6, (-40 * k, 0, 0)), (1.4, (-80 * k, 0, 0))])
        death.pos(f"{side}_leg", [(0, ZERO), (0.6, (0, -8, -2)), (1.4, (0, -13, -3))])
        death.rot(f"{side}_shin", [(0, ZERO), (0.6, (-20, 0, 0)), (1.4, (-5, 0, 0))])
    death.rot("jaw", [(0, ZERO), (0.6, (-25, 0, 0)), (1.4, (-15, 0, 0))])


if __name__ == "__main__":
    build().write()
