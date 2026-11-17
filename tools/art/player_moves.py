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
FP_GUARD_POS = fp_pos(-2.26, 2.8, 2.26)
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
    c.key(3, "out", fp_pos=fp_pos(-2, -15, 2), fp=fp_xyz(-40, 0, -20), camera=cam(9, 0, -3))
    c.key(8, "inout", fp_pos=fp_pos(-1.5, -14, 1.5), fp=fp_xyz(-25, 0, -12), camera=cam(4, 0, 2))
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
    c.key(20, "inout", fp_pos=fp_pos(-2.26, 2.97, 2.26), fp=fp_xyz(-101.25, 13.365, 78.05))
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
    c.key(0, fp_pos=fp_pos(-3.2, 2.4, 2.0), fp=fp_xyz(-98, 6, 86))
    c.key(20, "inout", fp_pos=fp_pos(-3.2, 2.55, 2.0), fp=fp_xyz(-97, 6, 86))
    c.key(40, "inout", fp_pos=fp_pos(-3.2, 2.4, 2.0), fp=fp_xyz(-98, 6, 86))
    return c


def parry():
    """A parry: from the guard the blade snaps out to meet the blow and springs back. Plays over the guard."""
    c = Clip("parry", 8)
    g = GUARD_R
    c.key(0, right_arm=g, body=(-6, 14, 0))
    c.key(2, "out5", right_arm=aim((0.35, 0.15, -1), blade=(0.5, 1, -0.2)), body=(-2, -16, 0))
    c.key(8, "softback", right_arm=g, body=(-6, 14, 0))
    c.key(0, fp_pos=FP_GUARD_POS, fp=FP_GUARD, camera=cam(0, 0, 0))
    c.key(2, "out5", fp_pos=fp_pos(-0.2, 4.1, -1.4), fp=fp_xyz(-80, -12, 48), camera=cam(-1.2, -1.6, 1.0))
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
    c.key(3, "out5", fp_pos=fp_pos(1.5, 6.0, 0.5), fp=fp_aim((0.7, 0.7, 0.1), flat=(0.1, 1, 0.2)), camera=cam(-5, 3, -4))
    c.key(10, "inout", fp_pos=fp_pos(1.0, 3.5, 0.5), fp=fp_aim((0.45, 0.85, 0.1), flat=(-0.5, 0.3, 0.8)), camera=cam(-1.5, 1, -1))
    c.key(16, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return c


# ----------------------------------------------------------------------------------------------- moves: getting about
# These play on every player, all the time (client/combat/anim/Locomotion): the walk, run and sneak are one stride each,
# read by the player's own footfalls (vanilla's walk position, so the feet keep pace with the ground), blended by speed
# and cross-faded into each other; the jump, the fall and the landing come and go by weight. Phase 0 is vanilla's: the
# right leg back, the left forward, the right arm forward. Signs as everywhere here: +X swings a limb forward and tips the
# body back; +Z takes a right limb outward (a left one inward); +Y turns the right shoulder forward.

def _stride(c, frames, ease="inout"):
    """Keys a looping stride from (tick, pose) pairs, the last copied to the end so it wraps without a seam."""
    for t, pose in frames:
        c.key(t, ease, **pose)
    c.key(c.ticks, ease, **frames[0][1])
    return c


def walk():
    """An unhurried walk: legs a third as far apart as vanilla's (which swings them 80 degrees), arms swinging easily
    against them, the hips riding up as a leg passes under and the shoulders turning with the forward arm."""
    c = Clip("walk", 20, loop=True)
    # first person: the hand dips a little on each footfall and sways towards the foot that lands (over vanilla's bob)
    contact = lambda s: dict(right_leg=(-28 * s, 0, 0.5), left_leg=(28 * s, 0, -0.5), right_arm=(20 * s, 0, 3), left_arm=(-20 * s, 0, -3),
                             body=(-2, 4 * s, 0), root_pos=(0, -0.45, 0), fp_pos=fp_pos(-0.4 * s, -0.45, 0), fp=fp_xyz(1.0, 0, 1.2 * s))
    passing = dict(right_leg=(0, 0, 0.5), left_leg=(0, 0, -0.5), right_arm=(0, 0, 3), left_arm=(0, 0, -3), body=(-2, 0, 0),
                   root_pos=(0, 0.35, 0), fp_pos=fp_pos(0, 0.2, 0), fp=fp_xyz(0, 0, 0))
    return _stride(c, [(0, contact(1)), (5, passing), (10, contact(-1)), (15, passing)])


def run():
    """A sprint: leaning into it, the front leg reaching, the arms pumping hard and a little across the body, the hips
    dropping on each footfall and lifting in the flight between."""
    c = Clip("run", 20, loop=True)
    contact = lambda s: dict(right_leg=((-42 if s > 0 else 55), 0, 1), left_leg=((55 if s > 0 else -42), 0, -1),
                             right_arm=((58 if s > 0 else -42), (-8 if s > 0 else 0), (6 if s > 0 else 10)),
                             left_arm=((-42 if s > 0 else 58), (0 if s > 0 else 8), (-10 if s > 0 else -6)),
                             body=(-13, 8 * s, 0), root_pos=(0, -1.1, 0),
                             fp_pos=fp_pos(-1.0 * s, -1.1, 0.2), fp=fp_xyz(-3, 4 * s, 5 * s))
    # first person: the weapon carried a little lower, swinging side to side with the arm and rocking on each stride
    flight = dict(right_leg=(10, 0, 1), left_leg=(8, 0, -1), right_arm=(8, 0, 8), left_arm=(8, 0, -8), body=(-12, 0, 0),
                  root_pos=(0, 0.9, 0), fp_pos=fp_pos(0, -0.2, 0.2), fp=fp_xyz(-1, 0, 0))
    return _stride(c, [(0, contact(1)), (5, flight), (10, contact(-1)), (15, flight)])


def sneak():
    """Sneaking: short careful steps under the bent back (vanilla bends the body), the arms forward and swinging little."""
    c = Clip("sneak", 20, loop=True)
    contact = lambda s: dict(right_leg=(-17 * s, 0, 0), left_leg=(17 * s, 0, 0), right_arm=(23 + 9 * s, 0, 4), left_arm=(23 - 9 * s, 0, -4),
                             root_pos=(0, -0.2, 0), fp_pos=fp_pos(-0.6 - 0.3 * s, -1.2, 0.8), fp=fp_xyz(3, 0, 0.8 * s))
    # first person: held low and close, barely moving
    passing = dict(right_leg=(0, 0, 0), left_leg=(0, 0, 0), right_arm=(23, 0, 4), left_arm=(23, 0, -4), root_pos=(0, 0.15, 0),
                   fp_pos=fp_pos(-0.6, -1.0, 0.8), fp=fp_xyz(3, 0, 0))
    return _stride(c, [(0, contact(1)), (5, passing), (10, contact(-1)), (15, passing)])


def idle():
    """Standing: slow breath, the chest lifting and the shoulders rising a hair. The arms are left to vanilla's sway."""
    c = Clip("idle", 80, loop=True, step=2.0)
    c.key(0, body=(0, 0, 0), root_pos=(0, 0, 0), fp_pos=fp_pos(0, 0, 0))
    c.key(36, "inout", body=(1.2, 0, 0), root_pos=(0, 0.18, 0), fp_pos=fp_pos(0, 0.25, 0))
    c.key(80, "inout", body=(0, 0, 0), root_pos=(0, 0, 0), fp_pos=fp_pos(0, 0, 0))
    return c


def jump():
    """Leaving the ground: a dip and push, then one knee up and the arms rising out for balance; it holds the last pose
    while in the air."""
    c = Clip("jump", 10)
    c.key(0, root_pos=(0, -1.2, 0), body=(-6, 0, 0), right_leg=(-6, 0, 1), left_leg=(6, 0, -1), right_arm=(-8, 0, 6), left_arm=(-8, 0, -6),
          fp_pos=fp_pos(0, -1.4, 0), fp=fp_xyz(4, 0, 0))
    c.key(3, "out", root_pos=(0, 0.6, 0), body=(-2, 0, 0), right_leg=(30, 0, 2), left_leg=(-14, 0, -2), right_arm=(28, 0, 16), left_arm=(18, 0, -16),
          fp_pos=fp_pos(0.3, 1.3, 0), fp=fp_xyz(-6, 0, -2))
    c.key(10, "inout", root_pos=(0, 0.2, 0), body=(-1, 0, 0), right_leg=(22, 0, 2), left_leg=(-10, 0, -3), right_arm=(24, 0, 20), left_arm=(14, 0, -20),
          fp_pos=fp_pos(0.3, 0.8, 0), fp=fp_xyz(-3, 0, -1))
    return c


def fall():
    """A long drop: arms out wide and working, legs apart and pedalling, leaning back from the ground coming up."""
    c = Clip("fall", 20, loop=True, step=2.0)
    a = dict(right_arm=(40, 0, 48), left_arm=(28, 0, -40), right_leg=(18, 0, 6), left_leg=(-14, 0, -6), body=(6, 0, 0),
             fp_pos=fp_pos(1.4, 2.0, -0.3), fp=fp_xyz(-10, 3, -5))
    b = dict(right_arm=(30, 0, 40), left_arm=(42, 0, -50), right_leg=(-12, 0, 6), left_leg=(20, 0, -6), body=(7, 0, 0),
             fp_pos=fp_pos(0.8, 2.6, -0.3), fp=fp_xyz(-13, -3, -2))
    return _stride(c, [(0, a), (10, b)])


def land():
    """Touching down: the knees give (the hips drop), the body folds forward over them and the arms go out in front.
    The client fades it in and out by how hard the landing was; this is the deepest moment, held."""
    c = Clip("land", 4)
    pose = dict(root_pos=(0, -2.6, 0.6), body=(-14, 0, 0), right_leg=(16, 0, 2), left_leg=(-6, 0, -2), right_arm=(32, 0, 10), left_arm=(26, 0, -10),
                fp_pos=fp_pos(-0.3, -2.8, 0.4), fp=fp_xyz(10, 0, 2))
    c.key(0, **pose)
    c.key(4, "inout", **pose)
    return c


# ----------------------------------------------------------------------------------------------- moves: bows, shields, food
# The bow, crossbow and trident poses lie OVER vanilla's own aiming arms (client/combat/anim/Clips.ADDITIVE): vanilla
# points the arms where the player looks, and these turns and shifts are added in each arm's own frame, so the aim stays.

def bow_draw():
    """Drawing a bow, read by how far it is drawn (0 to 20 ticks, a full draw). Vanilla holds the bow in the main hand
    (the right here) and draws with the other: the string hand reaches to the bow, then pulls back past the cheek while
    the bow arm locks out; the feet open into an archer's stance."""
    c = Clip("bow_draw", 20)
    c.key(0, left_arm=(6, -4, 0), left_arm_pos=(0, 0, -1.5), right_arm=(0, 0, 0), root_pos=(0, -0.3, 0), body=(1, 0, 0),
          right_leg=(-8, 0, 2), left_leg=(9, 0, -3))
    c.key(20, "out", left_arm=(-4, -14, 0), left_arm_pos=(0, 0, 2.5), right_arm=(-3, 4, 0), root_pos=(0, -0.7, 0), body=(3, 0, 0),
          right_leg=(-10, 0, 3), left_leg=(11, 0, -4))
    return c


def bow_release():
    """Loosing the arrow: the string hand flies back off the cheek and the bow arm kicks up, then both settle."""
    c = Clip("bow_release", 10)
    c.key(0, left_arm=(-4, -14, 0), left_arm_pos=(0, 0, 2.5), right_arm=(-3, 4, 0))
    c.key(2, "out5", left_arm=(-10, -30, -6), left_arm_pos=(-1, 0.5, 4.5), right_arm=(-9, 6, 0))
    c.key(10, "inout", left_arm=(0, 0, 0), left_arm_pos=(0, 0, 0), right_arm=(0, 0, 0))
    # first person: the bow kicks up as the string goes, and settles
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_xyz(0, 0, 0), camera=cam(0, 0, 0))
    c.key(2, "out5", fp_pos=fp_pos(0, 1.2, 1.0), fp=fp_xyz(-9, 0, 0), camera=cam(-0.8, 0, 0))
    c.key(10, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_xyz(0, 0, 0), camera=cam(0, 0, 0))
    return c


def crossbow_fire():
    """A crossbow's kick: both arms thrown up and back, the shoulders rocking, then steady again."""
    c = Clip("crossbow_fire", 8)
    c.key(0, right_arm=(0, 0, 0), left_arm=(0, 0, 0), right_arm_pos=(0, 0, 0), left_arm_pos=(0, 0, 0), body=(0, 0, 0))
    c.key(1, "out5", right_arm=(-14, 0, 0), left_arm=(-14, 0, 0), right_arm_pos=(0, 0.5, 2), left_arm_pos=(0, 0.5, 2), body=(5, 0, 0))
    c.key(8, "inout", right_arm=(0, 0, 0), left_arm=(0, 0, 0), right_arm_pos=(0, 0, 0), left_arm_pos=(0, 0, 0), body=(0, 0, 0))
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_xyz(0, 0, 0), camera=cam(0, 0, 0))
    c.key(1, "out5", fp_pos=fp_pos(0, 1.0, 2.2), fp=fp_xyz(-12, 0, 0), camera=cam(-1.6, 0, 0))
    c.key(8, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_xyz(0, 0, 0), camera=cam(0, 0, 0))
    return c


def throw():
    """A trident thrown: from vanilla's arm cocked up and back, the arm whips over and forward, the body following it
    through and the left foot stepping in."""
    c = Clip("throw", 12)
    c.key(0, right_arm=aim((0.1, 1, 0.45)), body=(4, -12, 0), left_arm=aim((-0.3, -0.6, -0.7)), right_leg=(-10, 0, 2), left_leg=(8, 0, -2))
    c.key(2, "in", right_arm=aim((0.08, 0.35, -1)), body=(-6, 6, 0), left_arm=aim((-0.4, -0.9, 0.2)), right_leg=(-14, 0, 2), left_leg=(14, 0, -2))
    c.key(5, "out", right_arm=aim((-0.15, -0.7, -0.75)), body=(-12, 16, 0), left_arm=aim((-0.3, -1, 0.3)), right_leg=(-16, 0, 2), left_leg=(18, 0, -2))
    c.key(12, "inout", right_arm=READY_R, body=(-2, 4, 0), left_arm=READY_L, right_leg=(-4, 0, 1), left_leg=(5, 0, -1))
    # first person: the empty hand follows through low across the view; the view leans into the throw
    c.key(0, fp_pos=fp_pos(0, 3, 2), fp=fp_xyz(-20, 0, 0), camera=cam(-1.0, 0, 0))
    c.key(3, "in", fp_pos=fp_pos(-4, 1, -2), fp=fp_xyz(25, 0, 10), camera=cam(1.6, -0.8, 0.8))
    c.key(6, "out", fp_pos=fp_pos(-6, -4, -1), fp=fp_xyz(35, 0, 15), camera=cam(1.0, -0.6, 0.5))
    c.key(12, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_xyz(0, 0, 0), camera=cam(0, 0, 0))
    return c


def shield_brace():
    """Behind a raised shield: the feet set one before the other, knees soft, leaning into it. (Vanilla raises the arm.)"""
    c = Clip("shield_brace", 40, loop=True, step=2.0)
    legs = dict(right_leg=(-12, 0, 3), left_leg=(13, 0, -3))
    c.key(0, body=(-5, -8, 0), root_pos=(0, -1.1, 0), **legs)
    c.key(20, "inout", body=(-6, -8, 0), root_pos=(0, -1.3, 0), **legs)
    c.key(40, "inout", body=(-5, -8, 0), root_pos=(0, -1.1, 0), **legs)
    return c


def shield_hit():
    """A blow taken on the shield: the body rocks back on its heels and comes forward again."""
    c = Clip("shield_hit", 8)
    c.key(0, body=(-5, -8, 0), root_pos=(0, -1.1, 0))
    c.key(1, "out5", body=(7, -10, 0), root_pos=(0, -1.3, 1.6))
    c.key(8, "softback", body=(-5, -8, 0), root_pos=(0, -1.1, 0))
    # first person: the view jolts back with the blow (the shield is usually in the other hand, which the fp bone leaves)
    c.key(0, camera=cam(0, 0, 0))
    c.key(1, "out5", camera=cam(-2.2, 0.6, -1.2))
    c.key(8, "softback", camera=cam(0, 0, 0))
    return c


def eat():
    """Eating: the food held to the mouth, the arm bobbing and the head nodding as they chew."""
    c = Clip("eat", 8, loop=True)
    up = aim((-0.42, 0.42, -0.8))
    up2 = aim((-0.42, 0.3, -0.85))
    c.key(0, right_arm=up, head=(4, 0, 0))
    c.key(4, "inout", right_arm=up2, head=(-3, 0, 0))
    c.key(8, "inout", right_arm=up, head=(4, 0, 0))
    return c


def drink():
    """Drinking: the bottle tipped up to the lips, the head back, a slow swallow."""
    c = Clip("drink", 16, loop=True)
    up = aim((-0.35, 0.75, -0.55))
    c.key(0, right_arm=up, head=(16, 0, 0), body=(3, 0, 0))
    c.key(8, "inout", right_arm=aim((-0.35, 0.8, -0.5)), head=(19, 0, 0), body=(4, 0, 0))
    c.key(16, "inout", right_arm=up, head=(16, 0, 0), body=(3, 0, 0))
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


def _finish(c, wind, hit, nxt):
    c.event(wind, "swing").event(hit, "hit").event(nxt, "next")
    return c


STAND = ((0, 0, 0), (0, 0, 0))


def diagonal(name, total, wind, hit, nxt, heft=1.0, two_handed=False):
    """A cut from high on the sword side down across the body. The shoulders wind back, the hips lead, the arm whips
    through and the blade trails it (the weapon's own turn lags the arm's), then it all settles. `heft` makes it bigger
    and deeper (an axe); two_handed brings the other hand to the haft."""
    c = Clip(name, total)
    f = hit + max(1, round((total - hit) * 0.25))
    up, up_i = hold((0.62, 0.72 + 0.1 * (heft - 1), 0.12 + 0.15 * (heft - 1)), (0.25, 0.85, 0.62 + 0.2 * (heft - 1)))
    mid, mid_i = hold((-0.25, -0.2, -1), (-0.55, -0.1 - 0.15 * (heft - 1), -1))
    low, low_i = hold((-0.78, -0.62 - 0.1 * (heft - 1), -0.42), (-1, -0.45 - 0.2 * (heft - 1), 0.05))
    tw = 24 * heft
    if two_handed:
        left = [(0, "inout", READY_L), (wind, "out", aim((0.5, 0.55, -0.45))), (hit, "in3", aim((0.15, -0.35, -1))),
                (f, "out", aim((-0.1, -0.75, -0.6))), (total, "inout", READY_L)]
    else:
        left = [(0, "inout", READY_L), (wind, "out", aim((-0.35, -0.8, -0.55))), (f, "inout", aim((-0.55, -0.8, 0.35))), (total, "inout", READY_L)]
    swing(c, total,
          arm=[(0, "inout", READY_R), (wind, "out", up), (hit, "in3", mid), (f, "out", low), (total, "inout", READY_R)],
          item=[(0, "inout", READY_ITEM), (wind, "out", up_i), (hit, "in", mid_i), (f + 1, "out", low_i), (total, "inout", READY_ITEM)],
          body=[(0, "inout", (0, 0, 0)), (wind, "out", (4 * heft, -tw, -3)), (hit, "in", (-7 * heft, tw * 0.75, 2)), (f + 1, "out", (-8 * heft, tw * 1.4, 3)),
                (total, "inout", (0, 0, 0))],
          left=left,
          legs=[(0, "inout", STAND), (wind, "out", ((-10, 0, -2), (10, 0, 2))), (f, "inout", ((14 * heft, 0, -2), (-12 * heft, 0, 2))), (total, "inout", STAND)],
          root=[(0, "inout", (0, 0, 0)), (wind, "out", (0, 0.4, 0.6)), (f, "inout", (0, -0.9 * heft, -1.2 * heft)), (total, "inout", (0, 0, 0))])
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    # first person: raised at the top right, swept down through the middle of the view, finishing low on the left, all of
    # it above the hotbar (tools/art/fp_preview.py draws it)
    c.key(wind, "out", fp_pos=fp_pos(-1.0, 7.0 + heft, 1.5), fp=fp_aim((0.25, 0.85, 0.45), flat=(-1, 0.1, 0.4)), camera=cam(-1.2 * heft, 1.6, -1.0))
    c.key(hit, "in3", fp_pos=fp_pos(-6.0, 5.0, -3.0), fp=fp_cut((-0.55, 0.25, -0.8), (-0.75, -0.6, -0.1)), camera=cam(1.4 * heft, -2.0, 1.6))
    c.key(f + 1, "out", fp_pos=fp_pos(-11.0, 4.0, -1.5), fp=fp_cut((-0.85, -0.2, -0.5), (-0.6, -0.7, 0.3)), camera=cam(1.8 * heft, -2.6, 2.2))
    c.key(total, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return _finish(c, wind, hit, nxt)


def backhand(name, total, wind, hit, nxt, heft=1.0, two_handed=False, rising=0.0):
    """The return cut: from the off side back across to the sword side, level (or, rising > 0, from low to high: an
    axe's upward rip). The body unwinds the other way."""
    c = Clip(name, total)
    f = hit + max(1, round((total - hit) * 0.25))
    r = rising
    back, back_i = hold((-0.85, -0.15 - 0.55 * r, -0.5), (-0.6, 0.2 - 0.5 * r, 0.75))
    mid, mid_i = hold((0.3, -0.1 + 0.1 * r, -1), (0.62, 0.0 + 0.3 * r, -0.8))
    out, out_i = hold((0.95, -0.2 + 0.75 * r, -0.1 + 0.1 * r), (0.75, 0.1 + 0.8 * r, 0.65))
    tw = 26 * heft
    if two_handed:
        left = [(0, "inout", READY_L), (wind, "out", aim((-0.55, -0.45 - 0.3 * r, -0.7))), (hit, "in3", aim((0.45, -0.3, -0.85))),
                (f, "out", aim((0.6, -0.1 + 0.5 * r, -0.6))), (total, "inout", READY_L)]
    else:
        left = [(0, "inout", READY_L), (wind, "out", aim((-0.5, -0.8, 0.3))), (f, "inout", aim((-0.3, -0.75, -0.6))), (total, "inout", READY_L)]
    swing(c, total,
          arm=[(0, "inout", READY_R), (wind, "out", back), (hit, "in3", mid), (f, "out", out), (total, "inout", READY_R)],
          item=[(0, "inout", READY_ITEM), (wind, "out", back_i), (hit, "in", mid_i), (f + 1, "out", out_i), (total, "inout", READY_ITEM)],
          body=[(0, "inout", (0, 0, 0)), (wind, "out", (-3 - 6 * r, tw, 2)), (hit, "in", (-4 + 4 * r, -tw * 0.7, -2)), (f + 1, "out", (-2 + 8 * r, -tw * 1.3, -3)),
                (total, "inout", (0, 0, 0))],
          left=left,
          legs=[(0, "inout", STAND), (wind, "out", ((12, 0, -2), (-10, 0, 2))), (f, "inout", ((-12 * heft, 0, -3), (12 * heft, 0, 2))), (total, "inout", STAND)],
          root=[(0, "inout", (0, 0, 0)), (wind, "out", (0, -0.5 - 1.0 * r, 0.3)), (f, "inout", (0, -0.3 + 1.0 * r, -1.0 * heft)), (total, "inout", (0, 0, 0))])
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    # first person: drawn back to the left at shoulder height (the hand rising first, so the blade never drags across the
    # hotbar on its way), swept across the middle, finishing high on the right
    c.key(max(1, wind // 2), "out", fp_pos=fp_pos(-4.0, 7.0, 0.5))
    c.key(wind, "out", fp_pos=fp_pos(-9.0, 6.5 - 1.0 * r, 0.0), fp=fp_aim((-0.85, 0.5 - 0.35 * r, -0.15), flat=(0.1, 1, 0.1)), camera=cam(0.8 * r, -1.8, 1.2))
    c.key(hit, "in3", fp_pos=fp_pos(-6.0, 5.0 + r, -3.0), fp=fp_cut((0.35, 0.2 + 0.2 * r, -0.85), (1, 0.05 + 0.6 * r, 0.1)), camera=cam(-0.6 * r, 1.8, -1.6))
    c.key(f + 1, "out", fp_pos=fp_pos(1.0, 5.5 + 2.5 * r, -0.5), fp=fp_cut((0.8, 0.45 + 0.3 * r, -0.3), (0.7, 0.1 + 0.6 * r, 0.6)), camera=cam(-1.4 * r, 2.6, -2.2))
    c.key(total, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return _finish(c, wind, hit, nxt)


def thrust(name, total, wind, hit, nxt):
    """A lunge: the hilt drawn back to the hip, then the whole body driven forward behind the point, the blade in line
    with the arm for reach. The front foot steps out; a small overshoot, and back."""
    c = Clip(name, total)
    back, back_i = hold((0.4, -0.55, 0.72), (-0.05, 0.08, -1))
    out, out_i = hold((-0.1, 0.04, -1), (-0.04, 0.02, -1))
    swing(c, total,
          arm=[(0, "inout", READY_R), (wind, "out", back), (hit, "in3", out), (hit + 2, "out", hold((-0.1, 0.0, -1), (-0.04, 0.0, -1))[0]),
               (total, "inout", READY_R)],
          item=[(0, "inout", READY_ITEM), (wind, "out", back_i), (hit, "in", out_i), (hit + 3, "inout", out_i), (total, "inout", READY_ITEM)],
          body=[(0, "inout", (0, 0, 0)), (wind, "out", (5, -30, 0)), (hit, "in3", (-13, 34, 0)), (hit + 2, "out", (-11, 30, 0)), (total, "inout", (0, 0, 0))],
          left=[(0, "inout", READY_L), (wind, "out", aim((-0.2, -0.5, -0.85))), (hit, "in3", aim((-0.5, -0.6, 0.65))), (total, "inout", READY_L)],
          legs=[(0, "inout", STAND), (wind, "out", ((-8, 0, -2), (6, 0, 2))), (hit, "in3", ((30, 0, -2), (-26, 0, 2))), (hit + 3, "inout", ((26, 0, -2), (-22, 0, 2))),
                (total, "inout", STAND)],
          root=[(0, "inout", (0, 0, 0)), (wind, "out", (0, -0.4, 1.0)), (hit, "in3", (0, -1.6, -3.2)), (hit + 3, "inout", (0, -1.4, -2.8)), (total, "inout", (0, 0, 0))])
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    # first person: drawn back low on the right, then driven straight into the middle of the view, the blade's length
    # still showing (driven too far it shrinks to a toy)
    c.key(wind, "out", fp_pos=fp_pos(1.5, 2.5, 2.5), fp=fp_aim((-0.25, 0.25, -1), flat=(-1, 0.3, 0.2)), camera=cam(-0.5, 0.8, 0))
    c.key(hit, "in3", fp_pos=fp_pos(-4.0, 4.5, -3.5), fp=fp_aim((-0.18, 0.14, -1), flat=(-1, 0.4, 0.1)), camera=cam(1.2, -0.6, 0))
    c.key(hit + 3, "out", fp_pos=fp_pos(-3.6, 4.2, -3.0), fp=fp_aim((-0.19, 0.14, -1), flat=(-1, 0.4, 0.1)), camera=cam(0.6, -0.3, 0))
    c.key(total, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return _finish(c, wind, hit, nxt)


def overhead(name, total, wind, hit, nxt, heft=1.0, hop=0.0):
    """Both hands, straight over the head and down: the body arches back as the weapon goes up (and hangs there: this
    is the charged blow), then folds forward behind it. `hop` lifts the feet for the heaviest (a mace's slam)."""
    c = Clip(name, total)
    top = max(2, wind - max(2, round(wind * 0.3)))
    f = hit + max(2, round((total - hit) * 0.25))
    up, up_i = hold((0.22, 0.95, 0.3 + 0.1 * heft), (0.0, 0.5, 0.86))
    high, high_i = hold((0.2, 0.97, 0.36 + 0.1 * heft), (0.0, 0.38, 0.92))
    down, down_i = hold((-0.02, -0.5, -0.86), (0.0, -0.32, -0.95))
    low, low_i = hold((-0.08, -0.86, -0.5), (0.0, -0.82, -0.57))
    swing(c, total,
          arm=[(0, "inout", READY_R), (top, "out", up), (wind, "inout", high), (hit, "in3", down), (f, "out", low), (total, "inout", READY_R)],
          item=[(0, "inout", READY_ITEM), (top, "out", up_i), (wind, "inout", high_i), (hit, "in", down_i), (f + 1, "out", low_i), (total, "inout", READY_ITEM)],
          body=[(0, "inout", (0, 0, 0)), (top, "out", (11 * heft, -6, 0)), (wind, "inout", (14 * heft, -8, 0)), (hit, "in3", (-20 * heft, 8, 0)),
                (f, "out", (-26 * heft, 10, 0)), (total, "inout", (0, 0, 0))],
          left=[(0, "inout", READY_L), (top, "out", aim((-0.1, 0.95, 0.3))), (wind, "inout", aim((-0.08, 0.97, 0.36))), (hit, "in3", aim((0.1, -0.45, -0.9))),
                (f, "out", aim((0.12, -0.85, -0.5))), (total, "inout", READY_L)],
          legs=[(0, "inout", STAND), (top, "out", ((-6, 0, -3), (6, 0, 3))), (wind, "inout", ((-8, 0, -3), (8, 0, 3))),
                (hit, "in3", ((24 * heft, 0, -3), (-20 * heft, 0, 3))), (total, "inout", STAND)],
          root=[(0, "inout", (0, 0, 0)), (top, "out", (0, 0.8 + hop * 0.6, 0.8)), (wind, "inout", (0, 1.0 + hop, 1.0)), (hit, "in3", (0, -1.8 * heft, -2.6)),
                (f, "out", (0, -2.2 * heft, -2.8)), (total, "inout", (0, 0, 0))])
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    c.key(top, "out", fp_pos=fp_pos(-5.5, 6.0, 0.4), fp=fp_aim((-0.1, 0.82, 0.56), flat=(-1, 0, 0)), camera=cam(-2.6 * heft, 0, 0))
    c.key(wind, "inout", fp_pos=fp_pos(-5.7, 6.6, 0.7), fp=fp_aim((-0.1, 0.74, 0.66), flat=(-1, 0, 0)), camera=cam(-3.2 * heft, 0, 0))
    # down through the middle and stopped low, the blade angled down ahead of you, still over the hotbar
    c.key(hit, "in3", fp_pos=fp_pos(-5.5, 8.5, -2.5), fp=fp_aim((-0.1, -0.7, -0.7), flat=(-1, 0.05, 0.1)), camera=cam(4.2 * heft, 0, 0))
    c.key(f, "out", fp_pos=fp_pos(-5.5, 6.5, -1.5), fp=fp_aim((-0.1, -0.85, -0.5), flat=(-1, 0.05, 0.1)), camera=cam(2.6 * heft, 0, 0))
    c.key(total, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return _finish(c, wind, hit, nxt)


def spin(name, total, wind, hit, nxt, two_handed=False, heft=1.0):
    """A full turn with the weapon held out: the whole body spins once to its left and the blade cuts everything round it.
    The turn is the root's (so the feet turn too); the arm stays out and level and the blade lies in line with it. In first
    person the view can't spin, so the blade sweeps across it twice, once each way round."""
    c = Clip(name, total)
    end = hit + max(3, round((total - hit) * 0.55))
    coil, coil_i = hold((0.75, -0.3, 0.55), (0.55, 0.05, 0.85))
    out, out_i = hold((0.92, -0.08, -0.38), (0.96, 0.0, -0.3))
    low, low_i = hold((0.6, -0.55, -0.55), (0.75, -0.35, -0.55))
    c.key(0, root=(0, 0, 0))
    c.key(wind, "out", root=(0, -38 * heft, 0))
    c.key(hit, "in", root=(0, 190, 0))
    c.key(end, "out", root=(0, 360, 0))
    c.key(total, "linear", root=(0, 360, 0))
    if two_handed:
        left = [(0, "inout", READY_L), (wind, "out", aim((0.35, -0.45, 0.2))), (hit, "in", aim((0.55, -0.3, -0.75))), (end, "out", aim((0.3, -0.6, -0.7))),
                (total, "inout", READY_L)]
    else:
        left = [(0, "inout", READY_L), (wind, "out", aim((-0.3, -0.7, -0.65))), (hit, "in", aim((-0.85, -0.45, 0.25))), (end, "out", aim((-0.5, -0.8, 0.2))),
                (total, "inout", READY_L)]
    swing(c, total,
          arm=[(0, "inout", READY_R), (wind, "out", coil), (hit, "in", out), (end, "out", low), (total, "inout", READY_R)],
          item=[(0, "inout", READY_ITEM), (wind, "out", coil_i), (hit, "in", out_i), (end, "out", low_i), (total, "inout", READY_ITEM)],
          body=[(0, "inout", (0, 0, 0)), (wind, "out", (-4, -22, 0)), (hit, "in", (-9, 20, 3)), (end, "out", (-6, 12, 2)), (total, "inout", (0, 0, 0))],
          left=left,
          legs=[(0, "inout", STAND), (wind, "out", ((-12, 0, -3), (10, 0, 3))), (hit, "in", ((16, 0, -5), (-14, 0, 5))), (end, "out", ((8, 0, -3), (-8, 0, 3))),
                (total, "inout", STAND)],
          root=[(0, "inout", (0, 0, 0)), (wind, "out", (0, -1.4 * heft, 0)), (hit, "in", (0, -0.6, 0)), (end, "out", (0, -0.9, 0)), (total, "inout", (0, 0, 0))])
    mid = (wind + hit) / 2.0
    c.key(0, fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    c.key(wind, "out", fp_pos=fp_pos(1.5, 5.5, 0.5), fp=fp_aim((0.8, 0.45, 0.2), flat=(0.1, 1, 0.1)), camera=cam(0.5, 3.5 * heft, -1.5))
    c.key(mid, "in", fp_pos=fp_pos(-7.0, 5.5, -3.0), fp=fp_cut((-0.2, 0.25, -0.95), (-1, 0, 0.1)), camera=cam(0.8, -3.0, 2.0))
    c.key(hit, "linear", fp_pos=fp_pos(-15.0, 5.5, 0.0), fp=fp_cut((-0.95, 0.15, -0.1), (-0.6, 0, 0.8)), camera=cam(0.8, -5.0 * heft, 3.0))
    c.key(hit + 0.01 if hit + 0.01 < end else end, "hold", fp_pos=fp_pos(6.0, 5.5, 1.5))
    c.key(end, "out", fp_pos=fp_pos(-5.0, 5.5, -2.4), fp=fp_cut((-0.35, 0.3, -0.85), (-1, -0.1, 0.1)), camera=cam(0.6, -1.5, 1.0))
    c.key(total, "inout", fp_pos=fp_pos(0, 0, 0), fp=fp_rot(0, 0, 0), camera=cam(0, 0, 0))
    return _finish(c, wind, hit, nxt)


# total, wind-up ends, the blow lands, the next move may begin (ticks, at the weapon's own attack speed)
def sword_1():
    return diagonal("sword_1", 13, 3, 5, 9)


def sword_2():
    return backhand("sword_2", 13, 3, 5, 9)


def sword_3():
    return thrust("sword_3", 17, 4, 6, 13)


def sword_heavy():
    return overhead("sword_heavy", 22, 8, 11, 19)


def axe_1():
    return diagonal("axe_1", 18, 5, 8, 13, heft=1.25, two_handed=True)


def axe_2():
    return backhand("axe_2", 18, 5, 8, 13, heft=1.2, two_handed=True, rising=0.85)


def axe_heavy():
    return overhead("axe_heavy", 26, 9, 13, 23, heft=1.25)


def mace_1():
    return overhead("mace_1", 24, 7, 11, 18, heft=1.1)


def mace_2():
    return backhand("mace_2", 24, 7, 11, 18, heft=1.35, two_handed=True)


def mace_heavy():
    return overhead("mace_heavy", 32, 11, 16, 28, heft=1.4, hop=2.5)


# the hell weapons' own: the Bloodletter is quick (four cuts, the third a spin, the last a deep thrust; its heavy blow a
# long lunge), the Reaper of Minos wide (two great sweeps and a full reaping turn), the Tithe Axe heavy
def blood_1():
    return diagonal("blood_1", 12, 3, 5, 8)


def blood_2():
    return backhand("blood_2", 12, 3, 5, 8)


def blood_3():
    return spin("blood_3", 15, 3, 7, 11)


def blood_4():
    return thrust("blood_4", 18, 4, 7, 14)


def blood_heavy():
    return thrust("blood_heavy", 22, 8, 11, 19)


def reaper_1():
    return backhand("reaper_1", 19, 5, 9, 14, heft=1.35, two_handed=True)


def reaper_2():
    return diagonal("reaper_2", 19, 5, 9, 14, heft=1.35, two_handed=True)


def reaper_3():
    return spin("reaper_3", 24, 6, 12, 19, two_handed=True, heft=1.3)


def reaper_heavy():
    return overhead("reaper_heavy", 27, 9, 14, 24, heft=1.3)


def tithe_1():
    return diagonal("tithe_1", 18, 5, 8, 13, heft=1.3, two_handed=True)


def tithe_2():
    return backhand("tithe_2", 18, 5, 8, 13, heft=1.25, two_handed=True, rising=0.9)


def tithe_heavy():
    return overhead("tithe_heavy", 28, 10, 14, 25, heft=1.4, hop=2.0)


CLIPS = [roll, guard, guard_two, parry, guard_break, walk, run, sneak, idle, jump, fall, land, bow_draw, bow_release, crossbow_fire,
         throw, shield_brace, shield_hit, eat, drink, sword_1, sword_2, sword_3, sword_heavy, axe_1, axe_2, axe_heavy,
         mace_1, mace_2, mace_heavy, blood_1, blood_2, blood_3, blood_4, blood_heavy, reaper_1, reaper_2, reaper_3,
         reaper_heavy, tithe_1, tithe_2, tithe_heavy]


def build():
    m = rig()
    for make in CLIPS:
        clip = make()
        anim = m.animation(clip.name, clip.ticks * TICK, clip.loop)
        clip.bake(anim)
    return m


if __name__ == "__main__":
    build().write()
