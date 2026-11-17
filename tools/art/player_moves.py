#!/usr/bin/env python3
"""The player's combat moves (the combat overhaul, docs/COMBAT.md): every swing, the dodge roll and the guard stances,
as animations on a player-shaped rig. It writes versions/26.3/art/player_moves.bbmodel, which Blockbench opens (play the
animations there) and tools/art/render.py draws offline; tools/gen_bosses.py turns it into keyframes
(client/render/gen/PlayerMovesAnimations) and timings (entity/boss/gen/PlayerMovesAnim: lengths and the "hit" events the
server lands blows by). No mesh or texture is made from it: the game plays these on the real player model.

The rig is the vanilla player as a skeleton: root (hips) > body (turns about the hips) > head, right_arm > item, left_arm;
root > right_leg, left_leg. In the game the arms and head are no children of the body, so the client
(client/combat/anim/PlayerRig) works the hierarchy out itself; what render.py shows is what the game shows.
Three more bones carry no cubes: "fp" (the held item in first person: position in 1/16 blocks, x right, y up, z towards
the eye; rotation pitch, yaw, roll), "camera" (a nudge of the view: pitch, yaw, roll in degrees) and "item" (an extra turn
of the held weapon in the hand).

Smoothness comes from how the keys are made: a move is a few key poses, each reached with an easing curve ("out" for a
blow that lands hard and stops, "in" for a wind-up that gathers speed, "back" to overshoot and settle), limbs turn along
the shortest arc between poses (a quaternion slerp, so a slash is one clean curve), and the whole thing is baked to a key
every tick, which Minecraft joins with Catmull-Rom curves. Body leads, arm follows, weapon lags: give each bone its own
key times.

Run from the repo root:  python3 tools/art/player_moves.py && python3 tools/gen_bosses.py
Preview:  python3 tools/art/render.py player_moves --anim sword_1 --times 0,0.1,0.2,0.3 --views front,side,three
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material  # noqa: E402

TICK = 0.05

# directions in the rig's space (Blockbench: Y up, the player faces -Z, their right hand is at +X)
F, B, U, D, R, L = (0, 0, -1), (0, 0, 1), (0, 1, 0), (0, -1, 0), (1, 0, 0), (-1, 0, 0)


# ----------------------------------------------------------------------------------------------- rotations

def matrix(euler):
    """Blockbench's Euler turn (degrees; X first, then Y, then Z)."""
    x, y, z = (math.radians(v) for v in euler)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def euler(m, near=None):
    """The Euler turn of a matrix, the one of its equal forms nearest `near` (so a baked curve never jumps a full turn)."""
    sy = max(-1.0, min(1.0, -m[2][0]))
    y = math.asin(sy)
    if abs(sy) > 0.99999:
        x, z = math.atan2(-m[1][2], m[1][1]), 0.0
    else:
        x, z = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    a = [math.degrees(v) for v in (x, y, z)]
    if near is None:
        return tuple(a)
    b = [a[0] + 180, 180 - a[1], a[2] + 180]
    best = None
    for cand in (a, b):
        c = [cand[i] + 360 * round((near[i] - cand[i]) / 360) for i in range(3)]
        d = sum((c[i] - near[i]) ** 2 for i in range(3))
        if best is None or d < best[0]:
            best = (d, c)
    return tuple(best[1])


def quat(m):
    t = m[0][0] + m[1][1] + m[2][2]
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = (0.25 * s, (m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s)
    elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
        s = math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2
        q = ((m[2][1] - m[1][2]) / s, 0.25 * s, (m[0][1] + m[1][0]) / s, (m[0][2] + m[2][0]) / s)
    elif m[1][1] > m[2][2]:
        s = math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2
        q = ((m[0][2] - m[2][0]) / s, (m[0][1] + m[1][0]) / s, 0.25 * s, (m[1][2] + m[2][1]) / s)
    else:
        s = math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2
        q = ((m[1][0] - m[0][1]) / s, (m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s, 0.25 * s)
    return np.array(q)


def quat_matrix(q):
    w, x, y, z = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def slerp(a, b, t):
    d = float(np.dot(a, b))
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        q = a + (b - a) * t
        return q / np.linalg.norm(q)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)


def unit(v):
    v = np.array(v, dtype=float)
    return v / np.linalg.norm(v)


def aim(point, blade=None, twist=0.0):
    """The Euler turn of an arm that points along `point` with the held blade (the arm's own forward) along `blade`:
    at rest an arm hangs down (0, -1, 0) and its blade points ahead (0, 0, -1). Without `blade` the arm takes the
    shortest turn from rest. `twist` then turns it about its own length (degrees)."""
    a = unit(point)
    if blade is None:
        a0 = np.array([0.0, -1.0, 0.0])
        axis = np.cross(a0, a)
        s, c = np.linalg.norm(axis), float(np.dot(a0, a))
        if s < 1e-6:
            m = np.eye(3) if c > 0 else matrix((180, 0, 0))
        else:
            k = axis / s
            kx = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
            m = np.eye(3) + kx * s + kx @ kx * (1 - c)
    else:
        b = np.array(blade, dtype=float)
        b = unit(b - a * float(np.dot(a, b)))
        c = np.cross(a, b)
        # rest frame: arm (0,-1,0), blade (0,0,-1), third = arm x blade = (1,0,0)
        target = np.column_stack([c, -a, -b])  # the images of X, Y and Z
        m = target
    if twist:
        t = math.radians(twist)
        k = a
        kx = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
        m = (np.eye(3) + kx * math.sin(t) + kx @ kx * (1 - math.cos(t))) @ m
    return euler(m)


def hold(point, blade):
    """An arm pointing along `point` holding a blade that points exactly along `blade`: the arm's Euler turn and the
    held item's (the "item" bone turns the weapon in the hand about the arm's side-to-side axis). At rest a blade
    stands 80 degrees off the arm (ahead of a hanging arm, 10 degrees down); a blade in line with the arm, for reach,
    is item (-80, 0, 0)."""
    a, b = unit(point), unit(blade)
    cos = max(-1.0, min(1.0, float(np.dot(a, b))))
    perp = b - a * cos
    if np.linalg.norm(perp) < 1e-4:
        perp = np.array([0.0, 0.0, -1.0]) - a * float(np.dot(a, (0, 0, -1)))
    return aim(point, blade=perp), (math.degrees(math.acos(cos)) - 80.0, 0.0, 0.0)


def fp_cut(blade, motion):
    """A first-person turn for a blade pointing along `blade` while it cuts along `motion`: the edge leads, so the flat
    faces across the cut."""
    d, m = unit(blade), unit(motion)
    return fp_aim(blade, flat=np.cross(d, m))


# ----------------------------------------------------------------------------------------------- easing

def _back(t, s=1.70158):
    t -= 1
    return t * t * ((s + 1) * t + s) + 1


EASES = {
    "linear": lambda t: t,
    "in": lambda t: t * t,
    "in3": lambda t: t * t * t,
    "out": lambda t: 1 - (1 - t) ** 2,
    "out3": lambda t: 1 - (1 - t) ** 3,
    "out5": lambda t: 1 - (1 - t) ** 5,
    "inout": lambda t: t * t * (3 - 2 * t),
    "inout5": lambda t: t * t * t * (t * (6 * t - 15) + 10),
    "back": _back,
    "softback": lambda t: _back(t, 0.9),
    "hold": lambda t: 0.0 if t < 1 else 1.0,
}

# which bones turn by slerp (limbs, whose poses are far apart) and which by plain numbers (so a roll can turn 360)
SLERPED = {"right_arm", "left_arm", "item", "head", "fp"}
BONES = ("root", "body", "head", "right_arm", "left_arm", "right_leg", "left_leg", "item", "fp", "camera")


class Clip:
    """One move: key poses per bone, baked to a key every tick."""

    def __init__(self, name, ticks, loop=False, step=1.0):
        self.name, self.ticks, self.loop, self.step = name, ticks, loop, step
        self.rot = {}  # bone -> [(tick, euler, ease)]
        self.pos = {}
        self.events = []

    def key(self, tick, ease="inout", **bones):
        """Poses reached at `tick`, eased from each bone's key before. A bone's value is its Euler turn; `<bone>_pos`
        moves it (1/16 blocks)."""
        for name, value in bones.items():
            if name.endswith("_pos"):
                bone, table = name[:-4], self.pos
            else:
                bone, table = name, self.rot
            assert bone in BONES, f"{self.name}: no bone {bone}"
            assert 0 <= tick <= self.ticks, f"{self.name}: key at {tick} past the end ({self.ticks})"
            table.setdefault(bone, []).append((float(tick), tuple(float(v) for v in value), ease))
        return self

    def event(self, tick, name):
        self.events.append((tick, name))
        return self

    def _sample(self, bone, keys, tick, rotation):
        keys = sorted(keys, key=lambda k: k[0])
        if tick <= keys[0][0]:
            return keys[0][1]
        if tick >= keys[-1][0]:
            return keys[-1][1]
        i = max(j for j in range(len(keys)) if keys[j][0] <= tick)
        (t0, v0, _), (t1, v1, ease) = keys[i], keys[i + 1]
        f = EASES[ease]((tick - t0) / (t1 - t0))
        if rotation and bone in SLERPED:
            return quat_matrix(slerp(quat(matrix(v0)), quat(matrix(v1)), f))
        return tuple(v0[k] + (v1[k] - v0[k]) * f for k in range(3))

    def bake(self, anim):
        n = int(round(self.ticks / self.step))
        times = [min(self.ticks, i * self.step) for i in range(n + 1)]
        for bone, keys in self.rot.items():
            out, last = [], None
            for t in times:
                v = self._sample(bone, keys, t, True)
                if isinstance(v, np.ndarray):
                    v = euler(v, last if last is not None else sorted(keys)[0][1])
                last = v
                out.append((t * TICK, v))
            anim.rot(bone, out)
        for bone, keys in self.pos.items():
            anim.pos(bone, [(t * TICK, self._sample(bone, keys, t, False)) for t in times])
        for tick, name in self.events:
            anim.event(tick * TICK, name)


# ----------------------------------------------------------------------------------------------- the rig

SKIN = material("skin", "skin", base="c69a7a", dark="8e6a50")
SHIRT = material("shirt", "robe", base="3c8a8c", fold="285e60")
TROUSERS = material("trousers", "robe", base="46488c", fold="2e3060")
STEEL = material("steel", "metal", base="b8bcc4", high="f0f2f6", low="6a6e78")


def rig():
    m = Model("player_moves", texture_width=64)
    root = m.bone("root", (0, 12, 0))
    body = root.bone("body", (0, 12, 0))
    body.cube("torso", (-4, 12, -2), (8, 12, 4), SHIRT)
    head = body.bone("head", (0, 24, 0))
    head.cube("skull", (-4, 24, -4), (8, 8, 8), SKIN)
    head.cube("nose", (-1, 26, -5), (2, 2, 1), SKIN)
    right_arm = body.bone("right_arm", (5, 22, 0))
    right_arm.cube("right_arm", (4, 12, -2), (4, 12, 4), SKIN)
    # the game holds a sword pointing ahead of a hanging arm and 10 degrees down (worked out from vanilla's item-in-hand
    # transforms), so the rig does too
    item = right_arm.bone("item", (6, 13, 0), rotation=(-10, 0, 0))
    item.cube("blade", (5.5, 12.5, -15), (1, 1, 13), STEEL)
    item.cube("guard", (4.5, 12, -3), (3, 2, 1), STEEL)
    left_arm = body.bone("left_arm", (-5, 22, 0))
    left_arm.cube("left_arm", (-8, 12, -2), (4, 12, 4), SKIN)
    right_leg = root.bone("right_leg", (1.9, 12, 0))
    right_leg.cube("right_leg", (0, 0, -2), (4, 12, 4), TROUSERS)
    left_leg = root.bone("left_leg", (-1.9, 12, 0))
    left_leg.cube("left_leg", (-4, 0, -2), (4, 12, 4), TROUSERS)
    m.bone("fp", (0, 0, 0))
    m.bone("camera", (0, 0, 0))
    return m


def fp_pos(x, y, z):
    """First-person item offset as the game reads it (right, up, towards the eye; 1/16 blocks)."""
    return (-x, -y, z)


def fp_rot(pitch, yaw, roll):
    """First-person item turn as the game reads it (degrees)."""
    return (-pitch, -yaw, roll)


def _axis(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def fp_matrix(m):
    x, y, z = euler(m)
    return fp_rot(x, y, z)


def fp_xyz(x, y, z):
    """A first-person turn given the way vanilla writes them: about X, then Y, then Z, each in the frame the last left
    (poseStack.rotate X, Y, Z in that order)."""
    return fp_matrix(_axis("x", x) @ _axis("y", y) @ _axis("z", z))


# how the game holds a sword in first person before any move: the blade up and a little towards the eye, its flat
# facing left (from the handheld model's first-person display transform)
FP_BLADE = unit((0, 0.940, 0.342))
FP_FLAT = np.array([-1.0, 0.0, 0.0])


def fp_aim(blade, flat=(-1, 0, 0)):
    """A first-person turn that points the blade along `blade` with its flat facing `flat` (view space: x right, y up,
    z towards the eye; so a blade pointing away from you has negative z)."""
    d = unit(blade)
    n = np.array(flat, dtype=float)
    n = unit(n - d * float(np.dot(d, n)))
    rest = np.column_stack([FP_BLADE, FP_FLAT, np.cross(FP_BLADE, FP_FLAT)])
    now = np.column_stack([d, n, np.cross(d, n)])
    return fp_matrix(now @ rest.T)


def cam(pitch, yaw, roll):
    """The view's nudge as the game reads it (degrees: pitch down, yaw right, roll)."""
    return (-pitch, -yaw, roll)


# the arms' rest when holding a weapon ready: the pose every swing starts from and returns to
READY_R = aim((0.15, -1, -0.32), blade=(0, 0.45, -1))
READY_L = aim((-0.12, -1, -0.12))


# the guard: the arm low and forward across the body, the blade slanting up over the chest; the free hand close
GUARD_R = aim((-0.38, -0.72, -0.72), blade=(-0.62, 0.8, -0.05))
GUARD_R2 = aim((-0.38, -0.69, -0.72), blade=(-0.62, 0.8, -0.03))
GUARD_L = aim((0.28, -0.9, -0.42))


# in first person: the sword block Minecraft had before shields (the numbers vanilla still uses for a blocking item
# that isn't a shield): the blade lies across the view, slanting up to the left
FP_GUARD_POS = fp_pos(-2.26, 1.28, 2.26)
FP_GUARD = fp_xyz(-102.25, 13.365, 78.05)


# ----------------------------------------------------------------------------------------------- moves: dodge and guard

def roll():
    """The dodge roll: a forward roll over the shoulders in 12 ticks. The client turns its axis to the way the player
    moves, so one clip rolls every way. Blows miss from the dive to the landing.

    The player model has no knees or waist, so a tight ball can't be made; instead the hips' height follows what a real
    roll does to keep every part off the ground: a dip into the dive, up and over the shoulders (hips highest when the
    body is upside down), down onto the back, and a low crouch to stand out of. The legs push off behind, tuck as they
    come over and plant in front."""
    c = Clip("roll", 12)
    c.key(0, root=(0, 0, 0), root_pos=(0, 0, 0))
    c.key(2, "in", root=(-45, 0, 0), root_pos=(0, -3, 0))
    c.key(4, "linear", root=(-110, 0, 0), root_pos=(0, 2, 0))
    c.key(6, "linear", root=(-190, 0, 0), root_pos=(0, 5, 0))
    c.key(8, "linear", root=(-265, 0, 0), root_pos=(0, -8, 0))
    c.key(10, "linear", root=(-325, 0, 0), root_pos=(0, -5, 0))
    c.key(12, "out", root=(-360, 0, 0), root_pos=(0, 0, 0))
    c.key(0, body=(0, 0, 0), head=(0, 0, 0), right_leg=(0, 0, 0), left_leg=(0, 0, 0))
    c.key(2, "out", body=(-15, 0, 0), head=(-20, 0, 0), right_leg=(38, 0, -3), left_leg=(46, 0, 3))
    c.key(4, "inout", body=(-10, 0, 0), head=(-45, 0, 0), right_leg=(74, 0, -4), left_leg=(86, 0, 4))
    c.key(6, "inout", body=(-28, 0, 0), head=(-40, 0, 0), right_leg=(104, 0, -4), left_leg=(96, 0, 4))
    c.key(8, "inout", body=(-30, 0, 0), head=(-35, 0, 0), right_leg=(56, 0, -3), left_leg=(66, 0, 3))
    c.key(10, "inout", body=(-18, 0, 0), head=(-15, 0, 0), right_leg=(6, 0, -2), left_leg=(16, 0, 2))
    c.key(12, "out", body=(0, 0, 0), head=(0, 0, 0), right_leg=(0, 0, 0), left_leg=(0, 0, 0))
    # the arms reach for the ground, wrap in over the top and push off
    c.key(0, right_arm=READY_R, left_arm=READY_L)
    c.key(3, "out", right_arm=aim((-0.15, 0.55, -1), blade=(1, 0.1, -0.1)), left_arm=aim((0.2, 0.6, -1)))
    c.key(7, "inout", right_arm=aim((-0.3, -0.2, -1), blade=(1, 0.2, -0.2)), left_arm=aim((0.3, -0.2, -1)))
    c.key(10, "inout", right_arm=aim((0.1, -0.8, -0.6), blade=(0.3, 0.3, -1)), left_arm=aim((-0.1, -0.8, -0.6)))
    c.key(12, "inout", right_arm=READY_R, left_arm=READY_L)
    # first person: the weapon drops out of sight and comes back; the view dips and rights itself
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    c.key(3, "out", fp_pos=fp_pos(-1.5, -7, 1), fp=fp_xyz(-40, 0, -20), camera=cam(9, 0, -3))
    c.key(8, "inout", fp_pos=fp_pos(-1, -6, 1), fp=fp_xyz(-25, 0, -12), camera=cam(4, 0, 2))
    c.key(12, "softback", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    c.event(2, "evade").event(9, "recover")
    return c


def guard():
    """Guarding with a one-handed weapon: the blade raised across the body, the free hand braced behind it, knees soft.
    A loop: it breathes."""
    c = Clip("guard", 40, loop=True, step=2.0)
    arm, arm2, off = GUARD_R, GUARD_R2, GUARD_L
    for t, a in ((0, arm), (20, arm2), (40, arm)):
        c.key(t, "inout", right_arm=a)
    c.key(0, left_arm=off, body=(-6, 14, 0), root_pos=(0, -1.0, 0), right_leg=(-10, 0, -3), left_leg=(12, 0, 3))
    c.key(20, "inout", left_arm=off, body=(-7, 14, 0), root_pos=(0, -1.2, 0), right_leg=(-10, 0, -3), left_leg=(12, 0, 3))
    c.key(40, "inout", left_arm=off, body=(-6, 14, 0), root_pos=(0, -1.0, 0), right_leg=(-10, 0, -3), left_leg=(12, 0, 3))
    # first person: the blade comes up across the view, slanting from low right to high left
    c.key(0, fp_pos=FP_GUARD_POS, fp=FP_GUARD)
    c.key(20, "inout", fp_pos=fp_pos(-2.26, 1.45, 2.26), fp=fp_xyz(-101.25, 13.365, 78.05))
    c.key(40, "inout", fp_pos=FP_GUARD_POS, fp=FP_GUARD)
    return c


def guard_two():
    """Guarding with a two-handed weapon (an axe, the Reaper): the haft held across the body in both hands, knees bent."""
    c = Clip("guard_two", 40, loop=True, step=2.0)
    arm = aim((-0.3, -0.62, -0.72), blade=(-1, 0.42, 0.05))
    arm2 = aim((-0.3, -0.59, -0.72), blade=(-1, 0.42, 0.07))
    off = aim((0.5, -0.5, -0.78))
    off2 = aim((0.5, -0.47, -0.78))
    legs = dict(right_leg=(-12, 0, -4), left_leg=(12, 0, 4))
    c.key(0, right_arm=arm, left_arm=off, body=(-8, 8, 0), root_pos=(0, -1.2, 0), **legs)
    c.key(20, "inout", right_arm=arm2, left_arm=off2, body=(-9, 8, 0), root_pos=(0, -1.4, 0), **legs)
    c.key(40, "inout", right_arm=arm, left_arm=off, body=(-8, 8, 0), root_pos=(0, -1.2, 0), **legs)
    c.key(0, fp_pos=fp_pos(-3.2, 0.9, 2.0), fp=fp_xyz(-98, 6, 86))
    c.key(20, "inout", fp_pos=fp_pos(-3.2, 1.05, 2.0), fp=fp_xyz(-97, 6, 86))
    c.key(40, "inout", fp_pos=fp_pos(-3.2, 0.9, 2.0), fp=fp_xyz(-98, 6, 86))
    return c


def parry():
    """A parry: from the guard the blade snaps out to meet the blow and springs back. Plays over the guard."""
    c = Clip("parry", 8)
    g = GUARD_R
    c.key(0, right_arm=g, body=(-6, 14, 0))
    c.key(2, "out5", right_arm=aim((0.35, 0.15, -1), blade=(0.5, 1, -0.2)), body=(-2, -16, 0))
    c.key(8, "softback", right_arm=g, body=(-6, 14, 0))
    c.key(0, fp_pos=FP_GUARD_POS, fp=FP_GUARD, camera=cam(0, 0, 0))
    c.key(2, "out5", fp_pos=fp_pos(-0.2, 2.6, -1.4), fp=fp_xyz(-80, -12, 48), camera=cam(-1.2, -1.6, 1.0))
    c.key(8, "softback", fp_pos=FP_GUARD_POS, fp=FP_GUARD, camera=cam(0, 0, 0))
    c.event(1, "clash")
    return c


def guard_break():
    """The guard broken: the weapon is beaten wide, the body rocks back on its heels and recovers."""
    c = Clip("guard_break", 16)
    c.key(0, right_arm=GUARD_R, left_arm=GUARD_L, body=(-6, 14, 0), head=(0, 0, 0),
          root_pos=(0, -1, 0), right_leg=(-10, 0, -3), left_leg=(12, 0, 3))
    c.key(3, "out5", right_arm=aim((1, 0.1, 0.25), blade=(0.3, 1, 0.3)), left_arm=aim((-1, -0.35, 0.1)), body=(16, -12, 3), head=(12, 0, 0),
          root_pos=(0, -1.5, 2.5), right_leg=(16, 0, -3), left_leg=(-14, 0, 3))
    c.key(10, "inout", right_arm=aim((0.7, -0.6, 0.0), blade=(0.2, 0.6, -1)), left_arm=aim((-0.6, -0.8, 0)), body=(8, -6, 1), head=(6, 0, 0),
          root_pos=(0, -1, 1.5), right_leg=(8, 0, -2), left_leg=(-6, 0, 2))
    c.key(16, "inout", right_arm=READY_R, left_arm=READY_L, body=(0, 0, 0), head=(0, 0, 0), root_pos=(0, 0, 0), right_leg=(0, 0, 0),
          left_leg=(0, 0, 0))
    c.key(0, fp_pos=FP_GUARD_POS, fp=FP_GUARD, camera=cam(0, 0, 0))
    c.key(3, "out5", fp_pos=fp_pos(4, -1.5, 1.5), fp=fp_xyz(25, -35, -40), camera=cam(-5, 3, -4))
    c.key(10, "inout", fp_pos=fp_pos(2, -3, 1), fp=fp_xyz(10, -12, -14), camera=cam(-1.5, 1, -1))
    c.key(16, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return c


# ----------------------------------------------------------------------------------------------- moves: the sword

READY_ITEM = (0, 0, 0)


def swing(c, ticks, arm, item, body, left, legs, root=None):
    """Lays a swing's body keys: each argument a list of (tick, ease, value)."""
    for t, e, v in arm:
        c.key(t, e, right_arm=v)
    for t, e, v in item:
        c.key(t, e, item=v)
    for t, e, v in body:
        c.key(t, e, body=v)
    for t, e, v in left:
        c.key(t, e, left_arm=v)
    for t, e, (r, l) in legs:
        c.key(t, e, right_leg=r, left_leg=l)
    for t, e, v in root or []:
        c.key(t, e, root_pos=v)


def sword_1():
    """The first cut: from high on the right, down across to the left. The shoulders wind back, the hips lead, the arm
    whips through and the blade trails it (the item's turn lags the arm), then everything settles."""
    c = Clip("sword_1", 12)
    up, up_i = hold((0.62, 0.72, 0.12), (0.25, 0.85, 0.62))
    mid, mid_i = hold((-0.25, -0.2, -1), (-0.55, -0.1, -1))
    low, low_i = hold((-0.78, -0.62, -0.42), (-1, -0.45, 0.05))
    swing(c, 12,
          arm=[(0, "inout", READY_R), (3, "out", up), (5, "in3", mid), (6, "out", low), (12, "inout", READY_R)],
          item=[(0, "inout", READY_ITEM), (3, "out", up_i), (5, "in", mid_i), (7, "out", low_i), (12, "inout", READY_ITEM)],
          body=[(0, "inout", (0, 0, 0)), (3, "out", (4, -24, -3)), (5, "in", (-7, 18, 2)), (7, "out", (-8, 34, 3)), (12, "inout", (0, 0, 0))],
          left=[(0, "inout", READY_L), (3, "out", aim((-0.35, -0.8, -0.55))), (6, "inout", aim((-0.55, -0.8, 0.35))), (12, "inout", READY_L)],
          legs=[(0, "inout", ((0, 0, 0), (0, 0, 0))), (3, "out", ((-10, 0, -2), (10, 0, 2))), (6, "inout", ((14, 0, -2), (-12, 0, 2))),
                (12, "inout", ((0, 0, 0), (0, 0, 0)))],
          root=[(0, "inout", (0, 0, 0)), (3, "out", (0, 0.4, 0.6)), (6, "inout", (0, -0.9, -1.2)), (12, "inout", (0, 0, 0))])
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    c.key(3, "out", fp_pos=fp_pos(1.2, 2.6, 1.6), fp=fp_aim((0.3, 0.8, 0.55), flat=(-1, 0.25, 0.2)), camera=cam(-1.2, 1.6, -1.0))
    c.key(5, "in3", fp_pos=fp_pos(-6.5, -0.8, -2.6), fp=fp_cut((-0.45, 0.25, -0.9), (-0.8, -0.55, -0.2)), camera=cam(1.4, -2.0, 1.6))
    c.key(7, "out", fp_pos=fp_pos(-11.5, -4.2, -0.6), fp=fp_cut((-0.9, -0.4, -0.2), (-0.7, -0.6, 0.3)), camera=cam(1.8, -2.6, 2.2))
    c.key(12, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    c.event(3, "swing").event(5, "hit").event(9, "next")
    return c


CLIPS = [roll, guard, guard_two, parry, guard_break, sword_1]


def build():
    m = rig()
    for make in CLIPS:
        clip = make()
        anim = m.animation(clip.name, clip.ticks * TICK, clip.loop)
        clip.bake(anim)
    return m


if __name__ == "__main__":
    build().write()
