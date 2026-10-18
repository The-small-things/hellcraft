#!/usr/bin/env python3
"""The Malebranche, the Evil-Claws (Inferno XXI-XXII), the gang of devils who keep the fifth ditch of Malebolge, where
the grafters boil in pitch: black, leathery, horned and bat-winged, each with a long grappling hook ("more than a
hundred hooks") to drag back under whoever surfaces. Their captain is Malacoda (tools/art/malacoda.py).

Run from the repo root:  python3 tools/art/malebranche.py && python3 tools/gen_bosses.py

The attack is the hook: a swing that catches you and drags you in ("hook" is when it bites); the special is their
jeering (they "make a trumpet of their rumps", which we leave out) with their hooks raised.

The body is a lean, hard devil's: a deep chest over a narrow waist, ropy arms in segments ending in clawed hands, and
a beast's legs (forward thigh, backward hock, a long foot on three clawed toes). The head has a jutting muzzle, a
lower jaw full of teeth with two tusks, angry brows over glowing eyes, bat ears and horns that sweep back. The wings
are a bat's: an arm and three long fingers with leather stretched between them. A barbed tail hangs behind.

The hook is held upright in the right fist like a guard's pike. It is its own bone (a child of the body, not the arm),
so every animation that moves the right arm also keys the hook from the same motion (carry(), sampled every tick):
the pole stays in the fist.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

HIDE = material("hide", "skin", base="372a2e", dark="1a1013")
HORN = material("horn", "horn", root="8a7a60", tip="1a1410")
CLAW = material("claw", "horn", root="b8a888", tip="2a2018")
WING = material("wing", "skin", base="3a1c1e", dark="1e0c0e")
BONE = material("bone", "skin", base="3a2c2e", dark="1c1214")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
SHAFT = material("shaft", "hair", light="5a4a32", dark="2a2014")
BIND = material("bind", "flat", base="3a2a1a")
EYE = hexc("ffd23a")
EYE_HOT = hexc("fff2b0")
GRIN = hexc("e8dcc0")
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
    """Deep sockets under the brows with burning eyes, and the shadow the muzzle casts."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.1)
        mul(f, x, 1, 0.8)
    for cx in (0, f.w - 2):
        f.px(cx, 1, MOUTH)
        f.px(cx + 1, 1, MOUTH)
        f.glow(cx, 2, EYE)
        f.glow(cx + 1, 2, EYE_HOT if cx else EYE)
        f.px(cx, 3, shade(HIDE.c["dark"], 0.7))
        f.px(cx + 1, 3, shade(HIDE.c["dark"], 0.7))
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.7)


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.25)
        mul(f, x, f.h - 1, 0.6)
    fs["bottom"].fill(lambda x, y: MOUTH)


def muzzle_detail(fs):
    """The snout: a ridge of light down the top, flared nostrils, a lipless upper jaw with teeth along its edge."""
    f = fs["front"]
    f.px(0, 0, MOUTH)
    f.px(f.w - 1, 0, MOUTH)
    for x in range(f.w):
        f.px(x, f.h - 1, GRIN if x % 2 == 0 else MOUTH)
    for n in ("left", "right"):
        g = fs[n]
        for x in range(g.w):
            g.px(x, g.h - 1, GRIN if x % 2 else MOUTH)
    t = fs["top"]
    for y in range(t.h):
        mul(t, t.w // 2, y, 1.25)


def jaw_detail(fs):
    """The lower jaw: a row of teeth along its top edge, the dark of the open mouth behind them."""
    t = fs["top"]
    t.fill(lambda x, y: MOUTH)
    for x in range(t.w):
        t.px(x, t.h - 1, GRIN)
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, MOUTH if x % 2 == 0 else GRIN)
    for n in ("left", "right"):
        g = fs[n]
        for x in range(g.w):
            g.px(x, 0, GRIN if x % 2 else MOUTH)
    for n in ("bottom",):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                mul(g, x, y, 0.7)


def tusk_detail(fs):
    for n in fs:
        fs[n].fill(lambda x, y: shade(GRIN, 1.0 - 0.15 * y))


def chest_detail(fs):
    """A hard chest: the breastbone, ribs along the flanks."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid - 1, y, 0.75)
    for n in ("left", "right"):
        g = fs[n]
        for y in (1, 3):
            for x in range(g.w):
                mul(g, x, y, 0.75)


def pec_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.2)
        mul(f, x, f.h - 1, 0.78)


def abs_detail(fs):
    """Ridged belly muscles: the line down the middle, three rows across."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.7)
    for y in range(0, f.h, 2):
        for x in range(f.w):
            mul(f, x, y, 0.78)
    for y in range(f.h):
        mul(f, 0, y, 0.8)
        mul(f, f.w - 1, y, 0.8)


def muscle_detail(fs):
    """A lit ridge down the front of a limb, shadow at the back."""
    f = fs["front"]
    for y in range(f.h):
        mul(f, f.w // 2, y, 1.18)
    b = fs["back"]
    for y in range(b.h):
        for x in range(b.w):
            mul(b, x, y, 0.85)


def membrane_detail(fs):
    """Bat-wing leather: veins branching down it, a lighter, thinner edge."""
    for n in ("front", "back"):
        f = fs[n]
        for y in range(f.h):
            mul(f, (y // 3 + f.w // 2) % f.w, y, 0.65)
            if y % 4 == 2:
                mul(f, (y // 3 + f.w // 2 + 1) % f.w, y, 0.75)
        for x in range(f.w):
            mul(f, x, f.h - 1, 1.3)


def wing_bone_detail(fs):
    for n in SIDES:
        f = fs[n]
        for y in range(f.h):
            mul(f, 0, y, 1.2)


def ear_detail(fs):
    for n in ("left", "right", "front"):
        f = fs[n]
        for y in range(1, f.h - 1):
            mul(f, f.w // 2, y, 0.55)


def iron_hook_detail(fs):
    """Rust and a bright worn edge on the hook's point."""
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

ARM_REST = (20, 0, 0)       # the right arm a little forward,
FORE_REST = (80, 0, 0)      # the forearm raised: the fist at the chest, holding the pole upright
HOOK_REST = (8, 0, 0)       # (upright in the world: the body leans 8 degrees forward)
GRIP = Grip((5, 22, 0), ARM_REST, (5.5, 16.5, 0), FORE_REST, (5.5, 10.5, 0), HOOK_REST)


def build():
    m = Model("malebranche", texture_width=128)

    # --- the legs: a beast's, the thigh forward, the hock back, a long foot on three clawed toes
    for side, s in (("right", 1), ("left", -1)):
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        seg(leg, f"{side}_thigh", (2.1 * s, 13, 0.3), (2.4 * s, 7.6, -1.6), 3, 3, HIDE, extra=1, detail=muscle_detail)
        shin = leg.bone(f"{side}_shin", (2.4 * s, 7.5, -1.5))
        shin.cube(f"{side}_knee", (side_x(1.4, 2, s), 6.6, -2.8), (2, 2, 2), HIDE, mirror=s == -1)
        seg(shin, f"{side}_shin", (2.4 * s, 7.4, -1.6), (2.4 * s, 3.4, 1.7), 2, 2, HIDE, extra=1, detail=muscle_detail)
        seg(shin, f"{side}_hock", (2.4 * s, 3.6, 1.7), (2.4 * s, 0.9, -1.2), 2, 2, HIDE, extra=0.5)
        shin.cube(f"{side}_foot", (side_x(1, 3, s), 0, -3.4), (3, 1, 3), HIDE, mirror=s == -1, detail=ao(top=1, dark=0.8))
        for i, (tx, tz) in enumerate(((1.0, -4.6), (2.0, -5.0), (3.0, -4.6))):
            shin.cube(f"{side}_toe_{i}", (side_x(tx, 1, s), 0, tz), (1, 1, 2), CLAW, mirror=s == -1)

    # --- the body: narrow hips, a ridged belly, a deep chest, heavy shoulders; a row of spines down the back
    body = m.bone("body", (0, 12, 0), rotation=(-8, 0, 0))
    body.cube("pelvis", (-3, 11.5, -2), (6, 3, 4), HIDE, detail=ao(bottom=1))
    body.cube("belly", (-2.5, 14, -1.8), (5, 4, 3), HIDE, detail=abs_detail)
    body.cube("chest", (-4, 17.5, -2.3), (8, 4, 4), HIDE, detail=chest_detail)
    body.cube("back", (-3.5, 15, 0.8), (7, 6, 2), HIDE, rotation=(6, 0, 0), origin=(0, 18, 1.5))
    body.cube("traps", (-3, 20.8, -1.5), (6, 2, 3), HIDE, detail=ao(top=1, dark=0.85))
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_pec", (side_x(0, 4, s), 18.6, -3), (4, 3, 1), HIDE, mirror=s == -1,
                  rotation=(-8, 0, 0), origin=(0, 21, -2.5), detail=pec_detail)
    for i, y in enumerate((14.5, 17, 19.5)):
        body.cube(f"spine_{i}", (-0.5, y, 2.1), (1, 1, 1), CLAW, rotation=(45, 0, 0), origin=(0, y + 0.5, 2.6))
    tail = body.bone("tail", (0, 13, 2))
    seg(tail, "tail_root", (0, 13, 1.5), (0, 9.5, 4.2), 2, 2, HIDE, extra=1)
    seg(tail, "tail_mid", (0, 9.5, 4.2), (0, 6.5, 7.2), 1, 1, HIDE, extra=1)
    seg(tail, "tail_end", (0, 6.5, 7.2), (0, 4.5, 10.5), 1, 1, HIDE, extra=0.5)
    tail.cube("tail_barb", (-1, 3.8, 10.2), (2, 1, 2), CLAW, rotation=(-30, 45, 0), origin=(0, 4.3, 11.2))

    # --- the head: a jutting muzzle, a toothed jaw with tusks, angry brows over burning eyes, bat ears, swept horns
    head = body.bone("head", (0, 23, 0))
    head.cube("neck", (-1.5, 21.5, -1.6), (3, 3, 3), HIDE, detail=ao(top=1, dark=0.6))
    head.cube("skull", (-2.5, 24, -2.5), (5, 5, 5), HIDE, detail=face_detail)
    head.cube("muzzle", (-1.5, 24.4, -4.4), (3, 2, 2), HIDE, detail=muzzle_detail)
    head.cube("nose", (-1, 25.9, -4.2), (2, 1, 2), HIDE, rotation=(-14, 0, 0), origin=(0, 26.4, -2.5))
    jaw = head.bone("jaw", (0, 24.6, -1), rotation=(-10, 0, 0))
    jaw.cube("jaw", (-1.5, 23.4, -4.2), (3, 1, 4), HIDE, detail=jaw_detail)
    for side, s in (("right", 1), ("left", -1)):
        jaw.cube(f"{side}_tusk", (side_x(1, 1, s), 23.9, -4.3), (1, 2, 1), HIDE, inflate=-0.2, mirror=s == -1, detail=tusk_detail)
        head.cube(f"{side}_brow", (side_x(0, 3, s), 26.9, -3.3), (3, 1, 1), HIDE, mirror=s == -1,
                  rotation=(0, 0, 18 * s), origin=(1.5 * s, 27.4, -2.8), detail=brow_detail)
        seg(head, f"{side}_ear", (2.3 * s, 26.6, 0.4), (4.2 * s, 27.6, 1.8), 1, 2, HIDE, extra=0.3, detail=ear_detail)
        horn = head.bone(f"{side}_horn", (2.5 * s, 29, -1))
        seg(horn, f"{side}_horn", (1.7 * s, 28.4, -1.4), (3.2 * s, 29.9, 0.0), 2, 2, HORN, extra=1)
        seg(horn, f"{side}_horn_mid", (3.2 * s, 29.9, 0.0), (4.3 * s, 31.0, 1.6), 1, 1, HORN, extra=1)
        seg(horn, f"{side}_horn_tip", (4.3 * s, 31.0, 1.6), (4.6 * s, 30.8, 3.3), 1, 1, HORN, extra=0.3)

    # --- the wings: a bat's, an arm and three long fingers with the leather stretched between
    for side, s in (("right", 1), ("left", -1)):
        wing = body.bone(f"{side}_wing", (2 * s, 21, 2), rotation=(0, -25 * s, 0))
        z = 3.0
        root, elbow, wrist = (2.2 * s, 20.6, z), (6.2 * s, 24.2, z), (9.6 * s, 22.6, z)
        tips = ((12.0 * s, 12.6, z), (9.6 * s, 11.2, z), (6.6 * s, 12.8, z))
        seg(wing, f"{side}_wing_arm", root, elbow, 2, 2, BONE, extra=1, detail=wing_bone_detail)
        seg(wing, f"{side}_wing_fore", elbow, wrist, 1, 1, BONE, extra=1, detail=wing_bone_detail)
        for i, tip in enumerate(tips):
            seg(wing, f"{side}_wing_finger_{i}", wrist, tip, 1, 1, BONE, extra=0.5, detail=wing_bone_detail)
        seg(wing, f"{side}_wing_thumb", wrist, (10.2 * s, 24.4, z - 0.6), 1, 1, CLAW, extra=0.3)
        # the leather, in three panels from the body to the outer finger, their lower edges scalloped
        wing.cube(f"{side}_wing", (side_x(2.6, 4, s), 14, z - 0.4), (4, 8, 1), WING, mirror=s == -1, detail=membrane_detail)
        wing.cube(f"{side}_wing_mid", (side_x(6.4, 3, s), 12.2, z - 0.4), (3, 11, 1), WING, mirror=s == -1, detail=membrane_detail)
        wing.cube(f"{side}_wing_out", (side_x(9.4, 2, s), 13.2, z - 0.4), (2, 9, 1), WING, mirror=s == -1,
                  rotation=(0, 0, 8 * s), origin=(10.4 * s, 22, z), detail=membrane_detail)
        wing.cube(f"{side}_wing_top", (side_x(3.6, 3, s), 20.6, z - 0.4), (3, 2, 1), WING, mirror=s == -1,
                  rotation=(0, 0, 40 * s), origin=(4.2 * s, 22.4, z), detail=membrane_detail)

    # --- the arms: heavy shoulders, ropy forearms, clawed hands (the right one a fist round the pole)
    for side, s in (("right", 1), ("left", -1)):
        arm = body.bone(f"{side}_arm", (5 * s, 22, 0), rotation=ARM_REST if s == 1 else (6, 0, 0))
        arm.cube(f"{side}_deltoid", (side_x(3.6, 3, s), 19.4, -1.5), (3, 3, 3), HIDE, inflate=0.2, mirror=s == -1)
        arm.cube(f"{side}_arm", (side_x(4, 3, s), 16, -1.4), (3, 5, 3), HIDE, mirror=s == -1, detail=muscle_detail)
        fore = arm.bone(f"{side}_forearm", (5.5 * s, 16.5, 0), rotation=FORE_REST if s == 1 else (16, 0, 0))
        fore.cube(f"{side}_elbow", (side_x(4.5, 2, s), 15.6, 0.2), (2, 2, 2), HIDE, mirror=s == -1)
        fore.cube(f"{side}_forearm", (side_x(4, 3, s), 13, -1.5), (3, 4, 3), HIDE, mirror=s == -1, detail=muscle_detail)
        fore.cube(f"{side}_wrist", (side_x(4.5, 2, s), 11.6, -1), (2, 2, 2), HIDE, mirror=s == -1, detail=ao(bottom=1))
        if s == 1:
            fore.cube("right_fist", (4.4, 9.2, -1.5), (2, 3, 3), HIDE, inflate=0.1, detail=ao(bottom=1))
            fore.cube("right_knuckles", (6.2, 9.4, -1.5), (1, 2, 3), CLAW)
        else:
            fore.cube("left_palm", (-6.5, 9.8, -1.5), (2, 2, 3), HIDE)
            for i, fz in enumerate((-1.6, -0.5, 0.6)):
                fore.cube(f"left_claw_{i}", (-6.3, 7.6, fz), (1, 2, 1), CLAW, rotation=(-12 + 8 * i, 0, 0),
                          origin=(-5.8, 9.8, fz + 0.5))
            fore.cube("left_thumb", (-5.2, 8.6, -2.2), (1, 2, 1), CLAW, rotation=(25, 0, 0), origin=(-4.7, 10.4, -1.7))

    # --- the hook, upright in the right fist: a long pole, bound at the grip, an iron head with a barbed hook and a
    # back prong (built straight up from the fist, then tipped with the hand's rest turn)
    hook = body.bone("hook", tuple(GRIP.pivot), rotation=HOOK_REST)
    px, py, pz = GRIP.pivot
    hook.cube("shaft", (px - 0.5, py - 17, pz - 0.5), (1, 17, 1), SHAFT)
    hook.cube("shaft_top", (px - 0.5, py, pz - 0.5), (1, 11, 1), SHAFT)
    hook.cube("binding", (px - 0.5, py - 2.5, pz - 0.5), (1, 2, 1), BIND, inflate=0.25, detail=bind_detail)
    hook.cube("hook_socket", (px - 1, py + 10.5, pz - 1), (2, 2, 2), IRON)
    hook.cube("hook_spike", (px - 0.5, py + 12.5, pz - 0.5), (1, 2, 1), IRON, inflate=-0.1)
    hook.cube("hook_bar", (px - 0.5, py + 11, pz - 4), (1, 1, 3), IRON, detail=iron_hook_detail)
    hook.cube("hook_tip", (px - 0.5, py + 9, pz - 4), (1, 2, 1), IRON, detail=iron_hook_detail)
    hook.cube("hook_prong", (px - 0.5, py + 11.4, pz + 1), (1, 2, 1), IRON, rotation=(-35, 0, 0), origin=(px, py + 11.5, pz + 1))
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # at ease: the hook held upright, the wings folding and opening a little, the tail swaying
    idle = m.animation("idle", 1.6, loop=True)
    idle.rot("tail", [(0, (5, 0, 0)), (0.8, (5, 20, 0)), (1.6, (5, 0, 0))])
    idle.rot("head", [(0, ZERO), (0.8, (4, -8, 0)), (1.6, ZERO)])
    idle.rot("jaw", [(0, ZERO), (0.8, (-6, 0, 0)), (1.6, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        idle.rot(f"{side}_wing", [(0, (0, -5 * s, 0)), (0.8, (0, -15 * s, 0)), (1.6, (0, -5 * s, 0))])

    walk = m.animation("walk", 0.8, loop=True)
    walk.rot("right_leg", [(0, (35, 0, 0)), (0.4, (-35, 0, 0)), (0.8, (35, 0, 0))])
    walk.rot("left_leg", [(0, (-35, 0, 0)), (0.4, (35, 0, 0)), (0.8, (-35, 0, 0))])
    walk.rot("right_shin", [(0, (5, 0, 0)), (0.2, (-10, 0, 0)), (0.4, (-35, 0, 0)), (0.6, (-15, 0, 0)), (0.8, (5, 0, 0))])
    walk.rot("left_shin", [(0, (-35, 0, 0)), (0.2, (-15, 0, 0)), (0.4, (5, 0, 0)), (0.6, (-10, 0, 0)), (0.8, (-35, 0, 0))])
    walk.rot("left_arm", [(0, (30, 0, 0)), (0.4, (-30, 0, 0)), (0.8, (30, 0, 0))])
    walk.rot("tail", [(0, (5, -12, 0)), (0.4, (5, 12, 0)), (0.8, (5, -12, 0))])

    # the hook: wound back over the shoulder, swung over and down at you (thrown), and yanked back
    attack = m.animation("attack", 1.0)
    GRIP.carry(attack, "right_arm", "right_forearm", "hook",
               [(0, ZERO), (0.35, (150, 0, 8)), (0.55, (0, 0, 0)), (0.8, (-45, 0, 0)), (1.0, ZERO)],
               [(0, ZERO), (0.35, (-68, 0, 0)), (0.55, (-70, 0, 0)), (0.8, (-30, 0, 0)), (1.0, ZERO)])
    attack.rot("body", [(0, ZERO), (0.35, (12, 20, 0)), (0.55, (-15, -10, 0)), (0.8, (10, 0, 0)), (1.0, ZERO)])
    attack.rot("jaw", [(0, ZERO), (0.35, (-25, 0, 0)), (0.55, (-5, 0, 0)), (1.0, ZERO)])
    attack.event(0.55, "hook")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (15, 0, 0)), (0.3, ZERO)])
    hurt.rot("jaw", [(0, ZERO), (0.1, (-25, 0, 0)), (0.3, ZERO)])

    # the jeer: hook raised high, wings flared, cackling
    jeer = m.animation("jeer", 1.4)
    GRIP.carry(jeer, "right_arm", "right_forearm", "hook",
               [(0, ZERO), (0.3, (140, 0, 0)), (0.5, (130, 0, 0)), (0.7, (140, 0, 0)), (0.9, (130, 0, 0)), (1.1, (140, 0, 0)), (1.4, ZERO)],
               [(0, ZERO), (0.3, (-80, 0, 0)), (0.5, (-65, 0, 0)), (0.7, (-80, 0, 0)), (0.9, (-65, 0, 0)), (1.1, (-80, 0, 0)), (1.4, ZERO)],
               [(0, ZERO), (0.3, (-75, 0, 0)), (1.1, (-75, 0, 0)), (1.4, ZERO)])
    jeer.rot("head", [(0, ZERO), (0.3, (30, 0, 0)), (0.5, (15, 0, 0)), (0.7, (30, 0, 0)), (0.9, (15, 0, 0)), (1.4, ZERO)])
    jeer.rot("jaw", [(0, ZERO), (0.3, (-30, 0, 0)), (0.4, (-10, 0, 0)), (0.5, (-30, 0, 0)), (0.6, (-10, 0, 0)),
                     (0.7, (-30, 0, 0)), (0.8, (-10, 0, 0)), (0.9, (-30, 0, 0)), (1.4, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        jeer.rot(f"{side}_wing", [(0, ZERO), (0.3, (0, 35 * s, 30 * s)), (1.1, (0, 35 * s, 30 * s)), (1.4, ZERO)])

    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.5, (60, 0, 10)), (1.2, (80, 0, 10))])
    death.pos("body", [(0, ZERO), (0.5, (0, -6, -2)), (1.2, (0, -10, -3))])
    # it goes down on its back: the legs fold with the hips and kick out in front
    death.rot("right_leg", [(0, ZERO), (0.5, (40, 0, 0)), (1.2, (82, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (0.5, (30, 0, 0)), (1.2, (74, 0, 0))])
    death.pos("right_leg", [(0, ZERO), (0.5, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.pos("left_leg", [(0, ZERO), (0.5, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.rot("right_shin", [(0, ZERO), (0.5, (-40, 0, 0)), (1.2, (10, 0, 0))])
    death.rot("left_shin", [(0, ZERO), (0.5, (-50, 0, 0)), (1.2, (0, 0, 0))])
    death.rot("tail", [(0, ZERO), (0.5, (35, 0, 0)), (1.2, (70, 25, 0))])
    death.rot("jaw", [(0, ZERO), (0.5, (-30, 0, 0)), (1.2, (-20, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        death.rot(f"{side}_wing", [(0, ZERO), (0.5, (0, 20 * s, -20 * s)), (1.2, (0, 30 * s, -40 * s))])


if __name__ == "__main__":
    build().write()
