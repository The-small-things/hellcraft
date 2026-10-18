#!/usr/bin/env python3
"""The centaurs (the river of blood's monster): "round about the moat in thousands go, shooting with shafts whatever
soul uplifts itself out of the blood more than its guilt allotted" (Inferno XII). A bearded archer's body on a bay
horse's, with a bow in hand and a quiver on his back. They patrol the banks by the fords and shoot whoever they see
crossing; the special animation is the long look around that tells you when they're watching.

Run from the repo root:  python3 tools/art/centaur.py && python3 tools/gen_bosses.py

The horse is built as a horse: a deep chest, a barrel of ribs with the belly slung under it, the loins, and a rounded
croup over heavy haunches; front legs of forearm, knee, cannon, fetlock and hoof; hind legs of haunch, gaskin, the
hock jutting back, cannon, fetlock and hoof; the black points of a bay (lower legs, mane, tail). The man rises from
where the withers would be, a belt covering the join: an abdomen, a broad chest, trapezius and deltoids, arms in two
segments (the forearms are their own bones) with hands of a palm, gathered fingers and a thumb, and a leather bracer
on the bow arm. The head has a brow, a nose, ears, a headband, long hair and a layered beard. The bow is a recurved
stave held across the hand (its bone turns about the left shoulder, as the arm does, so the same keys keep it in the
hand); raised with the arm it stands upright with its string toward him.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="a07658", dark="6a4a34")
COAT = material("coat", "hair", light="7a4a2a", dark="3a2010")
POINTS = material("points", "hair", light="2e2018", dark="0e0a08")
MANE = material("mane", "hair", light="2a1a10", dark="0a0604")
HOOF = material("hoof", "horn", root="3a3028", tip="1a1410")
LEATHER = material("leather", "flat", base="4a3020")
BOW = material("bow", "horn", root="5a3a1a", tip="2a1a0a")
STRING = material("string", "flat", base="d8d0b8", edges=False)
FLETCH = material("fletch", "flat", base="8a2a1a")
EYE = hexc("ff8a2a")
SOCKET = hexc("2a1810")
MOUTH = hexc("2a120c")
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
    """Darkens (f < 1) or lightens one pixel of a face that is already painted."""
    if 0 <= fx < face.w and 0 <= fy < face.h:
        c = face.canvas.get(face.x + fx, face.y + fy)
        if c[3]:
            face.px(fx, fy, shade(c, f))


def ao(bottom=0, top=0, dark=0.7, faces=SIDES):
    """The side faces shaded along their lowest rows and their highest."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            for i in range(min(bottom, f.h)):
                for fx in range(f.w):
                    mul(f, fx, f.h - 1 - i, dark + (1 - dark) * i / bottom)
            for i in range(min(top, f.h)):
                for fx in range(f.w):
                    mul(f, fx, i, dark + (1 - dark) * i / top)
    return detail


def both(*details):
    def detail(fs):
        for d in details:
            if d:
                d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def face(fs):
    """Deep-set eyes glowing under the brow, cheekbones, the shadow of the beard's edge."""
    f = fs["front"]
    w = f.w
    for cx in (1, w - 3):
        f.px(cx, 3, SOCKET)
        f.px(cx + 1, 3, SOCKET)
        f.glow(cx + (1 if cx < w // 2 else 0), 3, EYE)
        mul(f, cx, 4, 0.85)
        mul(f, cx + 1, 4, 0.85)
    for y in range(f.h):
        mul(f, 0, y, 0.85)
        mul(f, w - 1, y, 0.85)
    for x in range(w):
        mul(f, x, 2, 0.8)  # the brow's shadow


def brow(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.12))
    for x in (1, 2, f.w - 3, f.w - 2):
        f.px(x, 0, MANE.c["light"])  # heavy dark brows
    fs["bottom"].fill(lambda x, y: SOCKET)


def nose(fs):
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            mul(g, 0, y, 0.8)
    fs["front"].px(0, 0, shade(SKIN.c["base"], 1.15))
    fs["bottom"].fill(lambda x, y: MOUTH)


def beard(fs):
    """A coarse black beard: strands running down, lighter at the cheeks, the mouth a dark line in it."""
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if (x + f.rng.randrange(3)) % 3 == 0:
                    mul(f, x, y, 1.35)
    f = fs["front"]
    if f.w >= 4:
        for x in range(1, f.w - 1):
            f.px(x, 0, MOUTH)  # the mouth, under the moustache
        f.px(1, 0, shade(SKIN.c["dark"], 0.6))


def hair(fs):
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if (x * 2 + y) % 5 == 0:
                    mul(f, x, y, 1.4)


def headband(fs):
    f = fs["front"]
    mid = f.w // 2
    f.px(mid, 0, hexc("c8a040"))  # a bronze stud
    for n in SIDES:
        g = fs[n]
        for x in range(0, g.w, 3):
            mul(g, x, 0, 1.25)


def chest(fs):
    """The chest: the division between the pectorals, their lower edge in shadow, a scar or two."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.8)
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.78)
        mul(f, x, f.h - 2, 0.92)
    for i in range(3):
        f.px(1 + i, 1 + i, shade(SKIN.c["base"], 1.2))  # a pale old scar


def abdomen(fs):
    """The abdomen's muscles in rows, and the coat's hair creeping up its lowest row where man becomes horse."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h - 1):
        mul(f, mid, y, 0.85)
        if y % 2 == 1:
            for x in range(1, f.w - 1):
                mul(f, x, y, 0.88)
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            if (x % 2) == 0:
                g.px(x, g.h - 1, COAT.c["dark"])


def belt(fs):
    f = fs["front"]
    mid = f.w // 2
    f.px(mid, 0, hexc("c8a040"))
    f.px(mid - 1, 0, hexc("8a6a20"))


def strap(fs):
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if y % 3 == 0:
                    mul(f, x, y, 0.75)


def bracer(fs):
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, 0, 0.75)
            mul(g, x, g.h - 1, 0.75)
        for y in range(1, g.h - 1, 2):
            g.px(g.w // 2, y, hexc("c8a040"))  # laces


def hand(fs):
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, g.h - 1, 0.85)


def fingers(fs):
    """The gathered fingers: the gaps between them, and the nails."""
    for n in ("front", "back", "left", "right"):
        g = fs[n]
        for x in range(1, g.w, 2):
            for y in range(g.h):
                mul(g, x, y, 0.82)


def quiver(fs):
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, 0, 1.3)  # the rim
            mul(g, x, g.h - 1, 0.7)
        for y in range(2, g.h - 1, 3):
            for x in range(g.w):
                mul(g, x, y, 0.8)  # stitched bands


def fletching(fs):
    for f in fs.values():
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 2:
                    mul(f, x, y, 1.3)


def coat_body(fs):
    """A horse's short coat: the belly darker, a soft sheen along the top of the back, the ribs faintly showing."""
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                t = y / max(1, g.h - 1)
                mul(g, x, y, 1.12 - 0.3 * t)
        for x in range(2, g.w - 2, 3):
            for y in range(2, g.h - 3):
                mul(g, x, y, 0.92)
    b = fs["bottom"]
    for y in range(b.h):
        for x in range(b.w):
            mul(b, x, y, 0.85)


def croup(fs):
    """The rump: the croup lit along its top, the buttocks parting at the back in shadow."""
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            for x in range(g.w):
                mul(g, x, y, 1.12 - 0.3 * y / max(1, g.h - 1))
    b = fs["back"]
    for y in range(b.h):
        mul(b, b.w // 2, y, 0.65)
        mul(b, b.w // 2 - 1, y, 0.85)


def muscle(fs):
    """A big muscle: lit on top, its lower edge in shadow where it rounds under."""
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, 0, 1.12)
            mul(g, x, g.h - 1, 0.72)
            if g.h > 3:
                mul(g, x, g.h - 2, 0.88)


def knee(fs):
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, g.h - 1, 0.8)
    fs["front"].px(fs["front"].w // 2, 0, shade(COAT.c["light"], 1.1))


def hoof(fs):
    """The hoof: the coronet band at its top, growth lines, a dark sole."""
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            g.px(x, 0, POINTS.c["light"])
    fs["bottom"].fill(lambda x, y: hexc("100c08"))


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("centaur", texture_width=128)
    horse = m.bone("horse", (0, 14, 0))
    # the horse's trunk: chest, the barrel of ribs, the belly slung under, the loins, the croup
    horse.cube("breast", (-4.5, 12.5, -10), (9, 9, 4), COAT, detail=both(coat_body, ao(bottom=1)))
    horse.cube("barrel", (-5, 12, -7), (10, 10, 13), COAT, detail=coat_body)
    horse.cube("belly", (-4, 11, -5), (8, 1, 10), COAT, detail=ao(bottom=1, dark=0.8))
    horse.cube("loins", (-4.5, 13, 5), (9, 8, 4), COAT, detail=coat_body)
    horse.cube("croup", (-5, 12.5, 8), (10, 9, 6), COAT, detail=croup)
    horse.cube("croup_top", (-4, 21.4, 8.5), (8, 1, 5), COAT)
    horse.cube("withers", (-4, 21.5, -8.5), (8, 1, 6), COAT)
    for side, s in (("right", 1), ("left", -1)):
        horse.cube(f"{side}_breast", (side_x(0.4, 4, s), 13.5, -10.6), (4, 7, 1), COAT, mirror=s == -1, detail=muscle)

    tail = horse.bone("tail", (0, 21, 13))
    tail.cube("tail_dock", (-1, 18.5, 13), (2, 3, 2), COAT)
    seg(tail, "tail", (0, 19.5, 14), (0, 12, 15.2), 3, 3, MANE, extra=1, detail=hair)
    seg(tail, "tail_end", (0, 13, 15), (0, 8, 15.4), 4, 2, MANE, extra=1, detail=hair)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        # a front leg: the elbow's muscle, forearm, knee, cannon, fetlock, hoof
        z = -6
        leg = horse.bone(f"front_{side}_leg", (3 * s, 13, z))
        leg.cube(f"front_{side}_leg", (x(1, 4), 9, z - 2.5), (4, 5, 4), COAT, mirror=s == -1, detail=muscle)
        leg.cube(f"front_{side}_forearm", (x(1.5, 3), 6, z - 1.8), (3, 4, 3), COAT, mirror=s == -1)
        leg.cube(f"front_{side}_knee", (x(1.5, 3), 4.5, z - 2), (3, 2, 3), POINTS, mirror=s == -1, detail=knee)
        leg.cube(f"front_{side}_cannon", (x(2, 2), 2, z - 1.5), (2, 3, 2), POINTS, mirror=s == -1)
        leg.cube(f"front_{side}_fetlock", (x(1.75, 2), 1.2, z - 2), (2, 2, 3), POINTS, inflate=0.2, mirror=s == -1)
        leg.cube(f"front_{side}_hoof", (x(1.5, 3), 0, z - 2.6), (3, 2, 3), HOOF, mirror=s == -1, detail=hoof)
        # a hind leg: the haunch, the gaskin sloping back to the hock, then the cannon straight down
        z = 9
        leg = horse.bone(f"back_{side}_leg", (3 * s, 13, z))
        leg.cube(f"back_{side}_leg", (x(1.6, 4), 8, z - 3), (4, 7, 6), COAT, inflate=0.3, mirror=s == -1, detail=muscle)
        seg(leg, f"back_{side}_gaskin", (3.4 * s, 8.5, z + 0.5), (3.4 * s, 4.5, z + 2.2), 3, 3, COAT, extra=1)
        leg.cube(f"back_{side}_hock", (x(1.9, 3), 3.5, z + 1), (3, 2, 3), POINTS, mirror=s == -1, detail=knee)
        leg.cube(f"back_{side}_cannon", (x(2.4, 2), 1.5, z + 1), (2, 3, 2), POINTS, mirror=s == -1)
        leg.cube(f"back_{side}_fetlock", (x(2.1, 2), 1, z + 0.4), (2, 2, 3), POINTS, inflate=0.2, mirror=s == -1)
        leg.cube(f"back_{side}_hoof", (x(1.9, 3), 0, z - 0.2), (3, 2, 3), HOOF, mirror=s == -1, detail=hoof)

    # the man's half rises from the front of the barrel, a belt hiding the join
    torso = horse.bone("torso", (0, 21, -7))
    torso.cube("torso", (-3.5, 21, -9.5), (7, 4, 5), SKIN, detail=abdomen)
    torso.cube("belt", (-4, 21, -10), (8, 1, 6), LEATHER, inflate=0.2, detail=belt)
    torso.cube("chest", (-4, 25, -10), (8, 5, 5), SKIN, detail=both(chest, ao(top=1, dark=0.85)))
    torso.cube("trapezius", (-4.5, 29.5, -9.5), (9, 2, 4), SKIN, detail=ao(bottom=1, dark=0.8))
    torso.cube("strap", (-0.5, 21.5, -10.7), (1, 12, 1), LEATHER, detail=strap, rotation=(0, 0, -38), origin=(0, 27, -10.2))
    torso.cube("strap_back", (-0.5, 21.5, -4.8), (1, 12, 1), LEATHER, detail=strap, rotation=(0, 0, -38), origin=(0, 27, -4.3))
    # the quiver slung across his back, fletched shafts showing
    torso.cube("quiver", (0.5, 24, -4.5), (3, 9, 3), LEATHER, detail=quiver, rotation=(0, 0, -20), origin=(2, 28.5, -3))
    for i, (dx, dy) in enumerate(((0.6, 33), (1.8, 33.6), (2.6, 32.8))):
        torso.cube(f"arrow{i}", (dx, dy, -3.8 + 0.5 * i), (1, 2, 1), FLETCH, detail=fletching, rotation=(0, 0, -20), origin=(2, 28.5, -3))

    head = torso.bone("head", (0, 32, -7))
    head.cube("neck", (-1.5, 31, -8.5), (3, 3, 3), SKIN, detail=ao(bottom=1))
    head.cube("head", (-3, 33, -10), (6, 6, 6), SKIN, detail=face)
    head.cube("brow", (-3, 36.4, -10.4), (6, 1, 1), SKIN, detail=brow)
    head.cube("nose", (-0.5, 34.4, -10.9), (1, 2, 1), SKIN, detail=nose)
    head.cube("beard", (-3, 32, -10.6), (6, 2, 4), MANE, detail=beard)
    head.cube("beard_point", (-2, 30.5, -10.6), (4, 2, 3), MANE, detail=beard)
    head.cube("beard_tip", (-1, 29.6, -10.4), (2, 1, 2), MANE, detail=beard)
    for side, s in (("right", 1), ("left", -1)):
        head.cube(f"{side}_ear", (side_x(3, 1, s), 34.5, -7.5), (1, 2, 1), SKIN, mirror=s == -1)
    head.cube("hair", (-3.5, 37.3, -10.4), (7, 2, 7), MANE, inflate=0.2, detail=hair)
    head.cube("hair_back", (-3.5, 31.5, -4.6), (7, 6, 2), MANE, detail=hair)
    head.cube("headband", (-3.5, 36.6, -10.5), (7, 1, 7), LEATHER, inflate=0.15, detail=headband)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        arm = torso.bone(f"{side}_arm", (5.5 * s, 31, -7))
        arm.cube(f"{side}_arm", (x(4, 3), 28, -9), (3, 3, 4), SKIN, mirror=s == -1, detail=muscle)
        arm.cube(f"{side}_upper", (x(4.2, 3), 25, -8.5), (3, 4, 3), SKIN, inflate=-0.1, mirror=s == -1, detail=ao(bottom=1, dark=0.85))
        fore = arm.bone(f"{side}_forearm", (5.5 * s, 25.5, -7))
        fore.cube(f"{side}_forearm", (x(4.3, 3), 21.5, -8.5), (3, 4, 3), SKIN, inflate=-0.3, mirror=s == -1)
        if s == -1:
            fore.cube(f"{side}_bracer", (x(4.3, 3), 21.6, -8.5), (3, 3, 3), LEATHER, mirror=True, detail=bracer)
        fore.cube(f"{side}_hand", (x(4.6, 2), 19.8, -8.3), (2, 2, 3), SKIN, mirror=s == -1, detail=hand)
        fore.cube(f"{side}_fingers", (x(4.4, 2), 19.3, -8.6), (2, 1, 2), SKIN, mirror=s == -1, detail=fingers)
        fore.cube(f"{side}_thumb", (x(4.2, 1), 20, -9.2), (1, 2, 1), SKIN, mirror=s == -1)

    # the bow: a recurved stave across the left hand, its string on the shoulder's side (behind it once raised)
    bow = torso.bone("bow", (-5.5, 31, -7))
    bow.cube("bow", (-6, 19.6, -8), (1, 2, 2), LEATHER, inflate=0.1)
    for end, k in (("front", -1), ("back", 1)):
        seg(bow, f"bow_{end}", (-5.5, 20.4, -7 + 1.0 * k), (-5.5, 21.6, -7 + 4.5 * k), 1, 1, BOW, extra=0.5)
        seg(bow, f"bow_{end}_limb", (-5.5, 21.6, -7 + 4.5 * k), (-5.5, 23.4, -7 + 7.6 * k), 1, 1, BOW, extra=0.5)
        seg(bow, f"bow_{end}_tip", (-5.5, 23.4, -7 + 7.6 * k), (-5.5, 23.0, -7 + 8.4 * k), 1, 1, BOW, extra=0.3)
    bow.cube("string", (-6, 22.7, -14.8), (1, 1, 16), STRING, inflate=-0.35)
    animations(m)
    return m


ZERO = (0, 0, 0)
# the bow held low and a little forward, across the body; the right hand hanging easy
ARMS_READY = {"right_arm": ZERO, "left_arm": (40, 0, 10)}
BOW_ARM = (90, 15, 0)        # the bow arm raised straight at the target (the torso turned a little to its left side)
REACH = (90, 40, 0)          # the right hand across to the string
DRAW = ((90, -20, 0), (10, 0, -130))   # upper arm and forearm with the string drawn to the cheek
LOOSE = ((80, -45, 0), (10, 0, -90))   # the hand flicks back off the string


def animations(m):
    idle = m.animation("idle", 2.0, loop=True)
    idle.rot("tail", [(0, (-20, 0, 0)), (1.0, (-25, 8, 0)), (2.0, (-20, 0, 0))])  # lifted out behind, swishing
    idle.pos("torso", [(0, ZERO), (1.0, (0, 0.4, 0)), (2.0, ZERO)])
    idle.rot("left_arm", [(0, ARMS_READY["left_arm"]), (2.0, ARMS_READY["left_arm"])])
    idle.rot("bow", [(0, ARMS_READY["left_arm"]), (2.0, ARMS_READY["left_arm"])])
    idle.rot("left_forearm", [(0, (20, 0, 0)), (2.0, (20, 0, 0))])
    idle.rot("right_forearm", [(0, (10, 0, 0)), (1.0, (16, 0, 0)), (2.0, (10, 0, 0))])

    # a four-beat walk
    walk = m.animation("walk", 1.0, loop=True)
    for leg, off in (("front_right", 0.0), ("back_left", 0.25), ("front_left", 0.5), ("back_right", 0.75)):
        keys = []
        for t in (0.0, 0.25, 0.5, 0.75, 1.0):
            phase = (t - off) % 1.0
            angle = 30 if phase < 0.25 else (-10 if phase < 0.5 else (-30 if phase < 0.75 else 10))
            keys.append((t, (angle, 0, 0)))
        walk.rot(f"{leg}_leg", keys)
    walk.rot("tail", [(0, (-25, 0, 0)), (0.5, (-30, 0, 0)), (1.0, (-25, 0, 0))])
    walk.pos("horse", [(0, ZERO), (0.25, (0, 0.5, 0)), (0.5, ZERO), (0.75, (0, 0.5, 0)), (1.0, ZERO)])

    # draw and loose: the bow comes up level at the target, the right hand takes the string and draws it to the cheek,
    # then flicks back as the arrow goes
    attack = m.animation("attack", 1.0)
    for bone in ("left_arm", "bow"):
        attack.rot(bone, [(0, ARMS_READY["left_arm"]), (0.3, BOW_ARM), (0.75, BOW_ARM), (1.0, ARMS_READY["left_arm"])])
    attack.rot("left_forearm", [(0, (20, 0, 0)), (0.3, ZERO), (0.75, ZERO), (1.0, (20, 0, 0))])
    attack.rot("right_arm", [(0, ARMS_READY["right_arm"]), (0.3, REACH), (0.6, DRAW[0]), (0.65, LOOSE[0]),
                             (1.0, ARMS_READY["right_arm"])])
    attack.rot("right_forearm", [(0, ZERO), (0.3, ZERO), (0.6, DRAW[1]), (0.65, LOOSE[1]), (1.0, ZERO)])
    attack.rot("torso", [(0, ZERO), (0.3, (0, -15, 0)), (0.75, (0, -15, 0)), (1.0, ZERO)])
    attack.rot("head", [(0, ZERO), (0.3, (0, 15, 0)), (0.75, (0, 15, 0)), (1.0, ZERO)])
    attack.event(0.62, "loose")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (-15, 0, 0)), (0.4, ZERO)])
    hurt.rot("horse", [(0, ZERO), (0.12, (-6, 0, 0)), (0.4, ZERO)])

    # the long look round: the archer turns from one bank to the other, shading his eyes
    look = m.animation("look", 3.0)
    look.rot("torso", [(0, ZERO), (0.6, (0, 55, 0)), (1.4, (0, 55, 0)), (2.0, (0, -55, 0)), (2.6, (0, -55, 0)), (3.0, ZERO)])
    look.rot("head", [(0, ZERO), (0.6, (0, 20, 0)), (1.4, (0, 20, 0)), (2.0, (0, -20, 0)), (2.6, (0, -20, 0)), (3.0, ZERO)])
    look.rot("right_arm", [(0, ARMS_READY["right_arm"]), (0.4, (130, 0, -20)), (2.6, (130, 0, -20)), (3.0, ARMS_READY["right_arm"])])
    look.rot("right_forearm", [(0, ZERO), (0.4, (0, 0, -75)), (2.6, (0, 0, -75)), (3.0, ZERO)])

    # buckles and goes down on its side
    death = m.animation("death", 1.4)
    death.rot("horse", [(0, ZERO), (0.5, (0, 0, 10)), (1.4, (0, 0, 85))])
    death.pos("horse", [(0, ZERO), (0.5, (0, -4, 0)), (1.4, (0, -9, 0))])
    for leg in ("front_right", "front_left"):
        death.rot(f"{leg}_leg", [(0, ZERO), (0.4, (-60, 0, 0)), (1.4, (-20, 0, 0))])
    death.rot("torso", [(0, ZERO), (1.4, (-40, 0, 0))])  # the archer slumps forward


if __name__ == "__main__":
    build().write()
