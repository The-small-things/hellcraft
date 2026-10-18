#!/usr/bin/env python3
"""The wrathful (Wrath's monster): "naked people, all muddy, with looks of rage; they smote each other not only with
the hand, but with the head, and with the chest, and with the feet, tearing each other piecemeal with their teeth"
(Inferno VII). Hunched, mud-caked brawlers with wild hair and bared teeth, who fight anything near them, each other
included.

The body is a wiry, hunched man: tapered limbs (thick thighs over knees and shins, feet with heels and toes; a
deltoid, a bicep and a forearm bent at the elbow down to a clenched fist), a ribcage over a narrow waist, pecs and a
heavy back, the head thrust forward under a jutting brow, teeth bared in a snarling jaw (a bone of its own, so he can
roar), matted hair in tufts. Mud cakes his shins and loins and is spattered everywhere else.

Run from the repo root:  python3 tools/art/wrathful.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix, value_noise  # noqa: E402

SKIN = material("skin", "skin", base="8a6a52", dark="5a4232")
MUD = material("mud", "hair", light="4a3c2a", dark="241c12")
HAIR = material("hair", "hair", light="3a2a1e", dark="140e0a")
TEETH = material("teeth", "flat", base="d8ccae", edges=False)
EYE = hexc("ff3a1a")
EYE_HOT = hexc("ffb070")
TOOTH = hexc("e8dcc0")
MOUTH = hexc("2a0a06")
VEIN = hexc("6a4636")
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
    """The side faces shaded along their lowest rows (where a part sinks into shadow) and their highest (where it
    tucks under the part above)."""
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
            d(fs)
    return detail


# ----------------------------------------------------------------------------------------------- details

def mud(amount=0.35, low=0.0):
    """Mud spattered and dried on the skin in blotches (more of it toward the bottom of each face when low > 0), with
    a pale dried crust on the blotches' upper rims and a few drips running down."""
    def detail(fs):
        for name in SIDES + ("top",):
            f = fs[name]
            n = value_noise(f, 3)
            hit = set()
            for fy in range(f.h):
                for fx in range(f.w):
                    bias = low * (fy / max(1, f.h - 1)) if name != "top" else 0.0
                    if n(fx, fy) + bias > 1.0 - amount * 2:
                        hit.add((fx, fy))
            for fx, fy in hit:
                c = mix(MUD.c["dark"], MUD.c["light"], 0.4 + 0.6 * f.rng.random())
                if (fx, fy - 1) not in hit:
                    c = shade(MUD.c["light"], 1.25)
                f.px(fx, fy, c)
            for _ in range(max(1, f.w * f.h // 40)):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                if (x, y) in hit:
                    for i in range(f.rng.randint(1, 3)):
                        f.px(x, y + i, MUD.c["dark"])
    return detail


def face_detail(fs):
    """Under the jutting brow, deep sockets with eyes like embers; a furrowed forehead, flared cheeks, a dark mouth
    behind the bared teeth, mud smeared over one cheek."""
    f = fs["front"]
    dark = SKIN.c["dark"]
    for x in range(f.w):
        mul(f, x, 0, 0.92)
    for x in (2, 3):
        mul(f, x, 1, 0.75)          # the furrow between the brows
    for cx in (1, f.w - 2):
        for dx in (-1, 0, 1):
            f.px(cx + dx, 2, shade(dark, 0.55))
        f.glow(cx, 2, EYE)
        f.px(cx, 3, shade(dark, 0.8))
        f.px(cx - 1 if cx < f.w // 2 else cx + 1, 3, shade(SKIN.c["base"], 1.08))   # the cheekbone
    for x in range(f.w):
        mul(f, x, 4, 0.9)
    for x in range(1, f.w - 1):
        f.px(x, f.h - 1, MOUTH)
    f.px(0, 4, shade(MUD.c["light"], 1.1))
    f.px(0, 3, MUD.c["dark"])
    for name in ("left", "right"):
        s = fs[name]
        for y in range(2, 4):
            mul(s, s.w - 1 if name == "right" else 0, y, 0.7)


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, shade(SKIN.c["base"], 1.05))
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.5))


def nose_detail(fs):
    """A broken, flattened nose: the bridge lit, flared nostrils underneath."""
    f = fs["front"]
    f.px(0, 0, shade(SKIN.c["base"], 1.12))
    f.px(1, 0, shade(SKIN.c["base"], 1.05))
    b = fs["bottom"]
    b.fill(lambda x, y: shade(SKIN.c["dark"], 0.8))
    b.px(0, 0, MOUTH)
    b.px(b.w - 1, 0, MOUTH)


def jaw_detail(fs):
    """The chin and jaw: stubble and grime, the underside in shadow."""
    for name in SIDES:
        f = fs[name]
        for _ in range(f.w * f.h // 4):
            mul(f, f.rng.randrange(f.w), f.rng.randrange(f.h), 0.78)
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.55))
    t = fs["top"]
    t.fill(lambda x, y: MOUTH)


def teeth_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, TOOTH if x in (0, f.w - 1) else shade(TOOTH, 0.78 if x % 2 else 0.9))
    for name in ("back", "top", "bottom"):
        fs[name].fill(lambda x, y: MOUTH)


def ear_detail(fs):
    for name in ("left", "right"):
        f = fs[name]
        f.px(0, 1, shade(SKIN.c["dark"], 0.6))


def neck_detail(fs):
    """Cords of the neck standing out in rage."""
    f = fs["front"]
    for y in range(f.h):
        f.px(0, y, shade(SKIN.c["base"], 1.08))
        f.px(f.w - 1, y, shade(SKIN.c["base"], 1.08))
        f.px(1, y, shade(SKIN.c["dark"], 0.85))


def ribs_detail(fs):
    """The ribs showing through on the sides, the breastbone down the middle, the heavy back with its spine."""
    for name in ("left", "right"):
        f = fs[name]
        for y in range(1, f.h, 2):
            for x in range(f.w):
                mul(f, x, y, 0.8)
    f = fs["front"]
    for y in range(f.h):
        mul(f, f.w // 2, y, 0.82)
    for x in range(f.w):
        mul(f, x, f.h - 1, 0.7)
    b = fs["back"]
    for y in range(b.h):
        mul(b, b.w // 2, y, 0.75)
        mul(b, b.w // 2 - 1, y, 1.08)


def pec_detail(fs):
    """Each pec lit on top, a deep shadow under it."""
    f = fs["front"]
    for x in range(f.w):
        mul(f, x, 0, 1.1)
        mul(f, x, f.h - 1, 0.62)
    mul(f, f.w // 2, f.h // 2 + 1, 0.6)           # the nipple
    fs["bottom"].fill(lambda x, y: shade(SKIN.c["dark"], 0.5))


def abs_detail(fs):
    """A hard belly: the six blocks of the abdominals, the line down the middle, the hollow above the navel."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.72)
    for y in range(1, f.h, 2):
        for x in range(1, f.w - 1):
            if x != mid:
                mul(f, x, y, 0.84)
    mul(f, mid, f.h - 1, 0.5)


def back_detail(fs):
    """The muscles of the back, a groove down the spine, shoulder blades standing out."""
    f = fs["back"]
    mid = f.w // 2
    for y in range(f.h):
        mul(f, mid, y, 0.72)
    for x in (1, f.w - 2):
        mul(f, x, 1, 1.12)
        mul(f, x, 2, 0.82)


def muscle(fs):
    """A limb's muscle: the front lit, the back and lower rows shaded so the parts swell."""
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.82)
        if name == "back":
            for y in range(f.h):
                for x in range(f.w):
                    mul(f, x, y, 0.9)


def veins(fs):
    """Veins standing out on a forearm."""
    for name in ("front", "left", "right"):
        f = fs[name]
        x = f.rng.randrange(f.w)
        for y in range(f.h):
            if f.rng.random() < 0.3:
                x = max(0, min(f.w - 1, x + f.rng.choice((-1, 1))))
            f.px(x, y, VEIN)


def knuckles(fs):
    """Split, bloody knuckles."""
    for name in ("front", "bottom"):
        f = fs[name]
        for x in range(f.w):
            mul(f, x, 0 if name == "front" else f.h - 1, 1.12)
        for x in range(0, f.w, 2):
            f.px(x, f.h - 1 if name == "front" else 0, hexc("7a2a1a"))


def fist_detail(fs):
    """The curled fingers' creases across the front of the fist."""
    for name in ("front", "left", "right"):
        f = fs[name]
        for y in range(1, f.h, 1):
            for x in range(f.w):
                if (x + y) % 3 == 0:
                    mul(f, x, y, 0.75)


def toes(fs):
    f = fs["front"]
    for x in range(f.w):
        if x % 2:
            mul(f, x, 0, 0.6)


def hair_detail(fs):
    """Matted hair, darker underneath and where it meets the head, a few clumps of mud in it."""
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.65)
        for _ in range(f.w * f.h // 16 + 1):
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), MUD.c["light"])


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("wrathful", texture_width=128)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        leg = m.bone(f"{side}_leg", (2 * s, 12, 0))
        mir = s == -1
        # a thick thigh, the kneecap, a lean shin with the calf behind it, an ankle, a foot with heel and toes
        leg.cube(f"{side}_thigh", (x(0, 4), 6.5, -2.2), (4, 6, 4), SKIN, mirror=mir, detail=both(muscle, mud(0.18), ao(top=2)))
        leg.cube(f"{side}_hip", (x(0.5, 3), 11.5, -1.8), (3, 2, 4), SKIN, mirror=mir, detail=mud(0.4))
        leg.cube(f"{side}_knee", (x(1, 2), 5, -2.5), (2, 2, 1), SKIN, mirror=mir, detail=mud(0.3))
        leg.cube(f"{side}_shin", (x(0.5, 3), 2, -1.6), (3, 5, 3), SKIN, mirror=mir, detail=mud(0.5, 0.6))
        leg.cube(f"{side}_calf", (x(0.5, 3), 3.5, 0.9), (3, 3, 1), SKIN, mirror=mir, detail=mud(0.4, 0.4))
        leg.cube(f"{side}_mud", (x(0.4, 3), 1, -1.7), (3, 2, 3), MUD, mirror=mir, inflate=0.25)
        leg.cube(f"{side}_foot", (x(0.5, 3), 0, -3.2), (3, 1, 5), SKIN, mirror=mir, detail=mud(0.6, 0.6))
        leg.cube(f"{side}_instep", (x(0.5, 3), 1, -2.2), (3, 1, 3), SKIN, mirror=mir, detail=mud(0.5))
        leg.cube(f"{side}_toes", (x(0.2, 3), 0, -4.2), (3, 1, 1), SKIN, mirror=mir, detail=both(toes, mud(0.4)))

    # hunched forward, his weight over his fists
    body = m.bone("body", (0, 12, 0), rotation=(-15, 0, 0))
    body.cube("loins", (-4, 11.5, -2.4), (8, 3, 5), MUD, inflate=0.2)
    body.cube("hips", (-4, 12, -2.2), (8, 3, 4), SKIN, detail=mud(0.3))
    body.cube("waist", (-3.5, 14.5, -2), (7, 4, 4), SKIN, detail=both(abs_detail, mud(0.15)))
    body.cube("ribs", (-4.5, 18, -2.4), (9, 4, 5), SKIN, detail=both(ribs_detail, mud(0.12)))
    body.cube("right_pec", (0, 19.6, -3.1), (4, 3, 1), SKIN, detail=pec_detail)
    body.cube("left_pec", (-4, 19.6, -3.1), (4, 3, 1), SKIN, mirror=True, detail=pec_detail)
    body.cube("back", (-4, 17.5, 2), (8, 5, 1), SKIN, detail=both(back_detail, mud(0.15)))
    body.cube("shoulders", (-5, 21.5, -2.2), (10, 2, 5), SKIN, detail=both(ao(bottom=1, dark=0.8), mud(0.12)))
    body.cube("traps", (-3, 22.5, -1), (6, 2, 4), SKIN, rotation=(-12, 0, 0), origin=(0, 23, 1), detail=mud(0.1))
    body.cube("neck", (-1.5, 22.5, -2.6), (3, 3, 3), SKIN, rotation=(20, 0, 0), origin=(0, 23, -1), detail=neck_detail)

    head = body.bone("head", (0, 24, -1))
    head.cube("skull", (-3, 24.5, -4), (6, 6, 6), SKIN, detail=both(face_detail, mud(0.12)))
    head.cube("crown", (-2.5, 25, 1.8), (5, 5, 1), SKIN)
    head.cube("right_brow", (0, 28.4, -4.7), (3, 1, 1), SKIN, rotation=(0, 0, 14), origin=(0, 28.9, -4.2), detail=brow_detail)
    head.cube("left_brow", (-3, 28.4, -4.7), (3, 1, 1), SKIN, mirror=True, rotation=(0, 0, -14), origin=(0, 28.9, -4.2), detail=brow_detail)
    head.cube("nose", (-1, 26.4, -4.9), (2, 2, 1), SKIN, rotation=(-10, 0, 0), origin=(0, 27, -4), detail=nose_detail)
    head.cube("cheeks", (-3, 25.6, -4.4), (6, 1, 1), SKIN, inflate=0.05)
    head.cube("teeth", (-2, 24.5, -4.35), (4, 1, 1), TEETH, detail=teeth_detail)
    head.cube("right_ear", (2.7, 26, -1.5), (1, 2, 1), SKIN, detail=ear_detail)
    head.cube("left_ear", (-3.7, 26, -1.5), (1, 2, 1), SKIN, mirror=True, detail=ear_detail)
    # matted, wild hair: a mass over the crown and the back of the head, locks over the ears, tufts standing up
    head.cube("hair", (-3.5, 29.5, -4.2), (7, 2, 7), HAIR, inflate=0.2, detail=hair_detail)
    head.cube("hair_back", (-3.5, 25, 1.6), (7, 5, 2), HAIR, inflate=0.1, detail=hair_detail)
    head.cube("right_lock", (2.8, 26.5, -3), (1, 4, 4), HAIR, rotation=(0, 0, 8), origin=(3, 30, -1), detail=hair_detail)
    head.cube("left_lock", (-3.8, 26.5, -3), (1, 4, 4), HAIR, mirror=True, rotation=(0, 0, -8), origin=(-3, 30, -1), detail=hair_detail)
    head.cube("tuft_front", (-1.5, 31, -4), (3, 2, 2), HAIR, rotation=(-25, 0, 10), origin=(0, 31, -3), detail=hair_detail)
    head.cube("tuft_right", (0.8, 30.8, -1.8), (2, 2, 3), HAIR, rotation=(10, 0, -25), origin=(2, 31, 0), detail=hair_detail)
    head.cube("tuft_left", (-2.8, 30.8, -0.8), (2, 2, 3), HAIR, rotation=(15, 0, 25), origin=(-2, 31, 0), detail=hair_detail)
    head.cube("tuft_back", (-1.5, 30, 1.5), (3, 2, 2), HAIR, rotation=(35, 0, 0), origin=(0, 30, 2), detail=hair_detail)
    # the snarling jaw, a little open even at rest so the teeth show
    jaw = head.bone("jaw", (0, 25, 0.5), rotation=(-6, 0, 0))
    jaw.cube("jaw", (-2.5, 22.9, -4.1), (5, 2, 5), SKIN, detail=jaw_detail)
    jaw.cube("lower_teeth", (-2, 24.1, -4.2), (4, 1, 1), TEETH, detail=teeth_detail)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mir = s == -1
        arm = body.bone(f"{side}_arm", (6 * s, 22, 0))
        arm.cube(f"{side}_shoulder", (x(4.4, 4), 19.5, -2.1), (4, 4, 4), SKIN, mirror=mir, inflate=0.1,
                 detail=both(muscle, mud(0.15)))
        arm.cube(f"{side}_upper_arm", (x(4.9, 3), 15.5, -1.5), (3, 5, 3), SKIN, mirror=mir, detail=both(muscle, mud(0.2)))
        arm.cube(f"{side}_bicep", (x(5.1, 2), 16.5, -2.3), (2, 3, 1), SKIN, mirror=mir, detail=muscle)
        # the elbow bends a little at rest; it swings the forearm and fist forward into a boxer's guard
        fore = arm.bone(f"{side}_forearm", (6.4 * s, 16, 0), rotation=(18, 0, 0))
        fore.cube(f"{side}_elbow", (x(5.1, 3), 15, -1.2), (3, 2, 3), SKIN, mirror=mir, detail=mud(0.2))
        fore.cube(f"{side}_forearm", (x(4.9, 3), 11.5, -1.6), (3, 4, 3), SKIN, mirror=mir, inflate=0.1,
                  detail=both(veins, mud(0.25, 0.3)))
        fore.cube(f"{side}_wrist", (x(5.4, 2), 10.6, -1.1), (2, 1, 2), SKIN, mirror=mir)
        fore.cube(f"{side}_fist", (x(4.8, 3), 8.4, -1.5), (3, 3, 3), SKIN, mirror=mir, inflate=0.15,
                  detail=both(fist_detail, mud(0.2)))
        fore.cube(f"{side}_knuckles", (x(4.8, 3), 8.1, -2.1), (3, 1, 2), SKIN, mirror=mir, detail=knuckles)
        fore.cube(f"{side}_thumb", (x(4.6 if s == 1 else 6.4, 1), 9.6, -2.3), (1, 1, 2), SKIN, mirror=mir)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # a brawler's stance: fists up, bouncing on his feet
    idle = m.animation("idle", 1.2, loop=True)
    idle.pos("body", [(0, ZERO), (0.3, (0, -0.5, 0)), (0.6, ZERO), (0.9, (0, -0.5, 0)), (1.2, ZERO)])
    idle.rot("right_arm", [(0, (60, 0, -10)), (0.6, (66, 0, -12)), (1.2, (60, 0, -10))])
    idle.rot("left_arm", [(0, (70, 0, 10)), (0.6, (64, 0, 12)), (1.2, (70, 0, 10))])
    idle.rot("head", [(0, ZERO), (0.6, (0, 10, 0)), (1.2, ZERO)])
    idle.rot("jaw", [(0, ZERO), (0.6, (-4, 0, 0)), (1.2, ZERO)])

    walk = m.animation("walk", 0.8, loop=True)
    walk.rot("right_leg", [(0, (35, 0, 0)), (0.4, (-35, 0, 0)), (0.8, (35, 0, 0))])
    walk.rot("left_leg", [(0, (-35, 0, 0)), (0.4, (35, 0, 0)), (0.8, (-35, 0, 0))])
    walk.rot("right_arm", [(0, (50, 0, -10)), (0.4, (70, 0, -10)), (0.8, (50, 0, -10))])
    walk.rot("left_arm", [(0, (70, 0, 10)), (0.4, (50, 0, 10)), (0.8, (70, 0, 10))])

    # a wild haymaker (the elbow cocked, then the arm thrown straight), then the head comes in too, teeth bared
    attack = m.animation("attack", 0.7)
    attack.rot("right_arm", [(0, (60, 0, -10)), (0.15, (20, -20, -30)), (0.3, (100, 20, 10)), (0.7, (60, 0, -10))])
    attack.rot("right_forearm", [(0, ZERO), (0.15, (40, 0, 0)), (0.3, (-14, 0, 0)), (0.7, ZERO)])
    attack.rot("body", [(0, ZERO), (0.15, (0, 20, 0)), (0.3, (-10, -25, 0)), (0.45, (-20, 0, 0)), (0.7, ZERO)])
    attack.rot("head", [(0, ZERO), (0.3, (0, 0, 0)), (0.45, (-30, 0, 0)), (0.7, ZERO)])
    attack.rot("jaw", [(0, ZERO), (0.25, (-18, 0, 0)), (0.45, (-22, 0, 0)), (0.7, ZERO)])
    attack.event(0.3, "hit")

    hurt = m.animation("hurt", 0.3)
    hurt.rot("body", [(0, ZERO), (0.1, (15, 0, 0)), (0.3, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.1, (20, 0, 0)), (0.3, ZERO)])

    # beating his chest in a rage, roaring
    rage = m.animation("rage", 1.2)
    rage.rot("right_arm", [(0, (60, 0, -10)), (0.2, (80, 0, 40)), (0.35, (60, 0, -10)), (0.65, (80, 0, 40)), (0.8, (60, 0, -10)), (1.2, (60, 0, -10))])
    rage.rot("left_arm", [(0, (70, 0, 10)), (0.35, (90, 0, -40)), (0.5, (70, 0, 10)), (0.8, (90, 0, -40)), (0.95, (70, 0, 10)), (1.2, (70, 0, 10))])
    rage.rot("right_forearm", [(0, ZERO), (0.2, (30, 0, 0)), (0.35, ZERO), (0.65, (30, 0, 0)), (0.8, ZERO)])
    rage.rot("left_forearm", [(0, ZERO), (0.35, (30, 0, 0)), (0.5, ZERO), (0.8, (30, 0, 0)), (0.95, ZERO)])
    rage.rot("head", [(0, ZERO), (0.3, (30, 0, 0)), (0.9, (30, 0, 0)), (1.2, ZERO)])
    rage.rot("jaw", [(0, ZERO), (0.3, (-28, 0, 0)), (0.9, (-28, 0, 0)), (1.2, ZERO)])

    death = m.animation("death", 1.2)
    death.rot("body", [(0, ZERO), (0.5, (-60, 0, 10)), (1.2, (-75, 0, 12))])
    death.pos("body", [(0, ZERO), (0.5, (0, -6, -2)), (1.2, (0, -10, -3))])
    death.rot("right_leg", [(0, ZERO), (0.5, (-40, 0, 0)), (1.2, (-50, 0, 0))])
    death.rot("left_leg", [(0, ZERO), (0.5, (-30, 0, 0)), (1.2, (-45, 0, 0))])
    death.rot("jaw", [(0, ZERO), (0.5, (-20, 0, 0)), (1.2, (-14, 0, 0))])
    death.rot("right_forearm", [(0, ZERO), (1.2, (-14, 0, 0))])
    death.rot("left_forearm", [(0, ZERO), (1.2, (-14, 0, 0))])


if __name__ == "__main__":
    build().write()
