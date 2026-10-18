#!/usr/bin/env python3
"""The heretics (Heresy's monster): "from within came forth such dire laments, that well they seemed to be of
wretched souls and tormented" (Inferno IX). Souls burnt black in their open tombs, embers glowing through the cracks
in their skin and fire still licking at their heads and shoulders. When a tomb blasts near you, one may climb out of
it (the special animation); their grip sets you alight.

Run from the repo root:  python3 tools/art/heretic.py && python3 tools/gen_bosses.py

Built as a man burnt to the bone: a skull-like head with a heavy brow over hollow, glowing sockets, a slack jaw (its
own bone) open on a throat full of fire, a stringy neck; a gaunt ribcage with embers between the ribs, a pinched
waist, the scorched rags of a cleric's robe hanging at the hips and down the back. The arms and legs are thin, in
tapered segments (the forearms are their own bones) with knees, bony hands of a palm, a thumb and two clawed finger
groups, and feet with toes. The charred skin is cracked all over, and the cracks glow; flames rise in layered tongues
from the head and both shoulders.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

CHAR = material("char", "skin", base="2a2420", dark="0e0a08")
ASH = material("ash", "skin", base="3a322c", dark="1a1410")
ROBE = material("robe", "robe", base="3a2a22", fold="1a120e")
FIRE = material("fire", "flat", base="ff7a1a", edges=False)
EMBER = hexc("ff8a2a")
EMBER_HOT = hexc("ffd25a")
EMBER_DEEP = hexc("c03a10")
FLAME_CORE = hexc("fff0a0")
SOCKET = hexc("080504")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def side_x(a, w, s):
    """A cube from a to a + w on the right becomes -(a + w) to -a on the left."""
    return a if s == 1 else -(a + w)


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

def cracks(per=24, faces=("front", "back", "left", "right", "top")):
    """Glowing cracks through the charred skin: short jagged lines that wander and fork, hottest at their middles,
    with the skin around them scorched a little lighter (one crack for about every `per` pixels of face)."""
    def detail(fs):
        for n in faces:
            f = fs[n]
            count = max(1, f.w * f.h // per)
            narrow = min(f.w, f.h) <= 2
            for _ in range(count):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                length = f.rng.randint(2, 5)
                dx = f.rng.choice((-1, 1))
                for i in range(length):
                    hot = 0 < i < length - 1 and not narrow
                    f.glow(x, y, EMBER_HOT if hot and f.rng.random() < 0.3 else (EMBER if hot else EMBER_DEEP))
                    for ox, oy in ((1, 0), (-1, 0)):
                        if f.rng.random() < 0.3:
                            mul(f, x + ox, y + oy, 1.25)
                    if f.rng.random() < 0.6:
                        y += 1
                    if f.rng.random() < 0.5:
                        x += dx
                    if f.rng.random() < 0.15:
                        dx = -dx
    return detail


def ribs(fs):
    """The ribcage: dark ribs curving round, the fire glowing between them, the sternum down the middle."""
    f = fs["front"]
    for y in range(f.h):
        for x in range(f.w):
            if y % 2 == 1 and x not in (f.w // 2 - 1, f.w // 2) and 0 < x < f.w - 1:
                f.glow(x, y, EMBER if (x + y) % 3 else EMBER_DEEP)
    for y in range(f.h):
        f.px(f.w // 2 - 1, y, shade(CHAR.c["base"], 1.2))
        f.px(f.w // 2, y, shade(CHAR.c["base"], 1.05))
    for n in ("left", "right"):
        g = fs[n]
        for y in range(1, g.h, 2):
            for x in range(1, g.w - 1):
                if (x + y) % 2:
                    g.glow(x, y, EMBER_DEEP)
    b = fs["back"]
    for y in range(b.h):
        b.px(b.w // 2, y, shade(CHAR.c["base"], 1.3) if y % 2 else shade(CHAR.c["base"], 0.8))  # the spine


def face(fs):
    """The skull's face: hollow sockets burning under the brow, the nose burnt away to a hole, the cheekbones bare."""
    f = fs["front"]
    w = f.w
    for cx in (0, w - 2):
        for dx in (0, 1):
            f.px(cx + dx, 1, SOCKET)
            f.px(cx + dx, 2, SOCKET)
        f.glow(cx + (1 if cx < w // 2 else 0), 2, EMBER_HOT)  # a coal deep in each socket
        f.glow(cx + (0 if cx < w // 2 else 1), 2, EMBER_DEEP)
    f.px(w // 2 - 1, 3, SOCKET)  # the nose hole
    f.px(w // 2, 3, SOCKET)
    f.px(w // 2 - 1, 4, shade(SOCKET, 2.0))
    for x in (0, w - 1):  # the hollows under the cheekbones
        mul(f, x, 3, 0.7)
        mul(f, x, 4, 0.6)
    for x in range(1, w - 1):
        mul(f, x, f.h - 1, 0.8)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(1, 4):
            for x in range(g.w):
                mul(g, x, y, 0.85)


def brow(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(CHAR.c["base"], 1.35))
    fs["bottom"].fill(lambda x, y: SOCKET)


def jaw(fs):
    """The slack lower jaw: a row of blackened teeth along its top, shadow beneath."""
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, hexc("6a5a48") if x % 2 else hexc("4a3e32"))
    t = fs["top"]
    t.fill(lambda x, y: EMBER_DEEP if y > 0 else hexc("5a4a3a"))
    for n in ("left", "right", "bottom"):
        g = fs[n]
        for x in range(g.w):
            for y in range(g.h):
                mul(g, x, y, 0.75)


def throat(fs):
    """The fire inside the mouth."""
    for f in fs.values():
        f.fill(lambda x, y: None)
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EMBER_HOT if (x + y) % 2 else EMBER)


def teeth(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, f.h - 1, hexc("6a5a48") if x % 2 == 0 else SOCKET)


def neck(fs):
    """Stringy tendons, glowing between."""
    f = fs["front"]
    for y in range(f.h):
        f.glow(f.w // 2, y, EMBER_DEEP)


def flames(fs):
    """Flame tongues: white-gold at the root, orange above, ragged at the top, where the tips burn away."""
    for name in SIDES:
        f = fs[name]
        for y in range(f.h):
            for x in range(f.w):
                height = f.h - y  # from the bottom
                t = height / f.h
                ragged = (x * 3 + height + f.rng.randrange(2)) % 4 == 0
                if ragged and t > 0.55:
                    f.px(x, y, (0, 0, 0), alpha=0)
                elif t < 0.3:
                    f.glow(x, y, FLAME_CORE if (x + y) % 3 else EMBER_HOT)
                elif t < 0.65:
                    f.glow(x, y, EMBER_HOT if (x + height) % 3 else EMBER)
                else:
                    f.glow(x, y, EMBER if (x + height) % 4 else EMBER_DEEP)
    for name in ("top", "bottom"):
        f = fs[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EMBER if name == "top" else FLAME_CORE)


def robe(fs):
    """Scorched rags: the hem burnt ragged, glowing at the burnt edge, scorch marks above."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            r = f.rng.random()
            if r < 0.4:
                f.px(x, f.h - 1, (0, 0, 0), alpha=0)
                f.glow(x, f.h - 2, EMBER_DEEP)
            elif r < 0.6:
                f.glow(x, f.h - 1, EMBER)
            if f.h > 4 and f.rng.random() < 0.25:
                mul(f, x, f.rng.randrange(f.h - 2), 0.6)


def palm(fs):
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            mul(g, x, g.h - 1, 0.85)
    fs["left"].glow(1, 0, EMBER_DEEP)


def finger(fs):
    """A burnt finger ending in a claw, its knuckle glowing."""
    for n in SIDES:
        g = fs[n]
        for x in range(g.w):
            g.px(x, g.h - 1, hexc("0a0605"))
        g.glow(0, 0, EMBER_DEEP)
    fs["bottom"].fill(lambda x, y: hexc("0a0605"))


def toes(fs):
    f = fs["front"]
    for x in range(1, f.w, 2):
        for y in range(f.h):
            mul(f, x, y, 0.6)


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("heretic", texture_width=128)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_leg", (x(0.3, 3), 6.5, -1.5), (3, 6, 3), CHAR, mirror=s == -1, detail=both(cracks(40), ao(top=1)))
        leg.cube(f"{side}_knee", (x(0.8, 2), 5.6, -1.9), (2, 2, 2), ASH, mirror=s == -1, detail=cracks(8))
        leg.cube(f"{side}_shin", (x(0.8, 2), 1.5, -1), (2, 6, 2), CHAR, mirror=s == -1, detail=cracks(40))
        leg.cube(f"{side}_foot", (x(0.5, 3), 0, -1.6), (3, 2, 3), CHAR, mirror=s == -1, detail=cracks(40))
        leg.cube(f"{side}_toes", (x(0.5, 3), 0, -2.6), (3, 1, 1), ASH, mirror=s == -1, detail=toes)

    body = m.bone("body", (0, 12, 0), rotation=(-8, 0, 0))
    body.cube("pelvis", (-3.5, 11.5, -2), (7, 3, 4), CHAR, detail=cracks())
    body.cube("loincloth", (-3.5, 9.5, -2.3), (7, 5, 1), ROBE, detail=robe)
    body.cube("robe_back", (-3.5, 7.5, 1.4), (7, 13, 1), ROBE, detail=robe)
    body.cube("torso", (-2.5, 14, -1.5), (5, 4, 3), CHAR, detail=both(cracks(), ao(top=1)))
    body.cube("ribcage", (-3.5, 17.5, -2), (7, 5, 4), CHAR, detail=both(ribs, ao(bottom=1)))
    body.cube("shoulders", (-4, 21.5, -1.5), (8, 2, 3), CHAR, detail=cracks())
    body.cube("sash", (-3.7, 15, -2.2), (2, 9, 1), ROBE, detail=robe, rotation=(0, 0, -30), origin=(-2.7, 19.5, -1.7))
    for side, s in (("right", 1), ("left", -1)):
        body.cube(f"{side}_shoulder_fire", (side_x(2.5, 3, s), 22.5, -1.5), (3, 3, 3), FIRE, detail=flames)
        body.cube(f"{side}_shoulder_tongue", (side_x(3, 2, s), 24.5, -1), (2, 3, 2), FIRE, detail=flames)

    head = body.bone("head", (0, 24, 0))
    head.cube("neck", (-1, 22.5, -1), (2, 3, 2), CHAR, detail=neck)
    head.cube("head", (-3, 26, -3.5), (6, 5, 6), CHAR, detail=both(cracks(14, ("back", "left", "right", "top")), face))
    head.cube("brow", (-3, 29.5, -3.9), (6, 1, 1), CHAR, detail=brow)
    head.cube("cheeks", (-2.5, 25.5, -3.2), (5, 1, 5), CHAR, detail=teeth)
    head.cube("throat", (-2, 24.6, -2.9), (4, 1, 3), FIRE, detail=throat)
    jaw_bone = head.bone("jaw", (0, 25.5, 0), rotation=(-12, 0, 0))
    jaw_bone.cube("jaw", (-2.5, 24, -3.5), (5, 2, 4), CHAR, detail=jaw)
    head.cube("head_fire", (-3, 30.5, -3.5), (6, 2, 6), FIRE, detail=flames)
    head.cube("head_flame", (-2, 32, -2.5), (4, 3, 4), FIRE, detail=flames)
    head.cube("head_tip", (-1, 34.5, -1.5), (2, 2, 2), FIRE, detail=flames)
    head.cube("head_tongue_r", (1.2, 32.5, -0.5), (1, 2, 1), FIRE, detail=flames)
    head.cube("head_tongue_l", (-2.4, 32, -2), (1, 3, 1), FIRE, detail=flames)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        arm = body.bone(f"{side}_arm", (5.5 * s, 23, 0))
        arm.cube(f"{side}_arm", (x(4, 3), 20, -1.5), (3, 3, 3), CHAR, mirror=s == -1, detail=cracks(40))
        arm.cube(f"{side}_upper", (x(4.5, 2), 16.5, -1), (2, 4, 2), CHAR, mirror=s == -1, detail=cracks(40))
        fore = arm.bone(f"{side}_forearm", (5.5 * s, 17, 0), rotation=(10, 0, 0))
        fore.cube(f"{side}_forearm", (x(4.5, 2), 13, -1), (2, 4, 2), CHAR, inflate=-0.1, mirror=s == -1, detail=cracks(40))
        fore.cube(f"{side}_hand", (x(5, 1), 11.5, -1.5), (1, 2, 3), CHAR, inflate=0.1, mirror=s == -1, detail=palm)
        for i, z in enumerate((-1.5, -0.5)):
            fore.cube(f"{side}_finger{i}", (x(5, 1), 10.2, z), (1, 2, 1), CHAR, mirror=s == -1, detail=finger,
                      rotation=(0, 0, -15 * s), origin=(5.5 * s, 11.5, z + 0.5))
        fore.cube(f"{side}_thumb", (x(4.4, 1), 10.8, -2.1), (1, 2, 1), CHAR, mirror=s == -1, detail=finger,
                  rotation=(20, 0, 0), origin=(4.9 * s, 12, -1.6))
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # swaying, arms hanging, the flames flickering, the jaw working in a lament
    idle = m.animation("idle", 1.6, loop=True)
    idle.rot("body", [(0, ZERO), (0.8, (0, 0, 4)), (1.6, ZERO)])
    idle.rot("right_arm", [(0, (10, 0, 0)), (0.8, (14, 0, 6)), (1.6, (10, 0, 0))])
    idle.rot("left_arm", [(0, (12, 0, 0)), (0.8, (8, 0, -6)), (1.6, (12, 0, 0))])
    idle.scale("head", [(0, (1, 1, 1)), (0.4, (1.02, 1.04, 1.02)), (0.8, (1, 1, 1)), (1.2, (1.03, 1.02, 1.03)), (1.6, (1, 1, 1))])
    idle.rot("jaw", [(0, ZERO), (0.4, (-10, 0, 0)), (0.8, ZERO), (1.2, (-14, 0, 0)), (1.6, ZERO)])

    # a shamble, arms reaching
    walk = m.animation("walk", 1.0, loop=True)
    walk.rot("right_leg", [(0, (30, 0, 0)), (0.5, (-30, 0, 0)), (1.0, (30, 0, 0))])
    walk.rot("left_leg", [(0, (-30, 0, 0)), (0.5, (30, 0, 0)), (1.0, (-30, 0, 0))])
    walk.rot("right_arm", [(0, (70, 0, 0)), (0.5, (80, 0, 0)), (1.0, (70, 0, 0))])
    walk.rot("left_arm", [(0, (80, 0, 0)), (0.5, (70, 0, 0)), (1.0, (80, 0, 0))])
    # the hands claw at the air
    walk.rot("right_forearm", [(0, ZERO), (0.5, (15, 0, 0)), (1.0, ZERO)])
    walk.rot("left_forearm", [(0, (15, 0, 0)), (0.5, ZERO), (1.0, (15, 0, 0))])

    # a burning embrace: both arms in
    attack = m.animation("attack", 0.7)
    attack.rot("right_arm", [(0, (70, 0, 0)), (0.2, (110, 0, 30)), (0.35, (80, 0, -25)), (0.7, (70, 0, 0))])
    attack.rot("left_arm", [(0, (80, 0, 0)), (0.2, (110, 0, -30)), (0.35, (80, 0, 25)), (0.7, (80, 0, 0))])
    attack.rot("body", [(0, ZERO), (0.35, (-20, 0, 0)), (0.7, ZERO)])
    attack.rot("jaw", [(0, ZERO), (0.2, (-25, 0, 0)), (0.5, (-25, 0, 0)), (0.7, ZERO)])
    attack.event(0.35, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (12, 0, 0)), (0.3, ZERO)])

    # climbing out of its tomb: up from below, hands on the rim, then up
    rise = m.animation("rise", 1.6)
    rise.pos("body", [(0, (0, -20, 0)), (0.6, (0, -10, 0)), (1.2, (0, -2, 0)), (1.6, ZERO)])
    rise.pos("right_leg", [(0, (0, -20, 0)), (1.2, (0, -4, 0)), (1.6, ZERO)])
    rise.pos("left_leg", [(0, (0, -20, 0)), (1.2, (0, -4, 0)), (1.6, ZERO)])
    rise.rot("right_arm", [(0, (170, 0, 0)), (0.6, (160, 0, 10)), (1.2, (40, 0, 0)), (1.6, (10, 0, 0))])
    rise.rot("left_arm", [(0, (170, 0, 0)), (0.6, (160, 0, -10)), (1.2, (40, 0, 0)), (1.6, (12, 0, 0))])
    # the hands bend over the rim and haul
    rise.rot("right_forearm", [(0, ZERO), (0.6, (-50, 0, 0)), (1.2, (-10, 0, 0)), (1.6, ZERO)])
    rise.rot("left_forearm", [(0, ZERO), (0.6, (-50, 0, 0)), (1.2, (-10, 0, 0)), (1.6, ZERO)])
    rise.rot("jaw", [(0, (-25, 0, 0)), (1.2, (-25, 0, 0)), (1.6, ZERO)])

    # crumbles into ash
    death = m.animation("death", 1.0)
    death.pos("body", [(0, ZERO), (0.5, (0, -6, 0)), (1.0, (0, -14, 0))])
    death.scale("body", [(0, (1, 1, 1)), (1.0, (1.2, 0.3, 1.2))])
    death.rot("right_leg", [(0, ZERO), (1.0, (0, 0, 40))])
    death.rot("left_leg", [(0, ZERO), (1.0, (0, 0, -40))])


if __name__ == "__main__":
    build().write()
