#!/usr/bin/env python3
"""The damned: the souls of each circle suffering its punishment, after Dante (entity/npc/Damned, hazard/Torments).
They are not monsters: they can't be hurt, pay you no mind and only cry out. Drawn as the shades of Limbo are
(tools/art/shade.py), pale and see-through, but stripped of everything: gaunt, ash-grey bodies with the ribs showing,
hollow sockets with a faint light deep in them, a mouth always a little open, stringy hair, and a torn shroud over one
shoulder and round the hips. The client tints each a little differently and draws it see-through.

Three props hang on the model and show only where the punishment calls for them (client/render/npc/DamnedModel):
the great weight a soul of Greed rolls with its chest (Inferno VII), the white banner the Vestibule's neutrals run
after (Inferno III), and the gilded cloak and hood of Malebolge's hypocrites (Inferno XXIII).

Each punishment has its own looping animation, which the server names (idle for Limbo's longing, run and lead in the
Vestibule, tumble in Lust's storm, wallow in Gluttony's mud, push in Greed, brawl in the Styx, writhe in Heresy's tombs,
bob in the river of blood, burn on the Burning Sands, march in Malebolge, shiver in the ice of Cocytus), and three
play over it: scream, sigh and flinch.

Run from the repo root:  python3 tools/art/damned.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="b6bac0", dark="7a7f8a")
RAG = material("rag", "robe", base="8e8a84", fold="5e5a55")
HAIR = material("hair", "hair", light="5c5c64", dark="24242a")
GILT = material("gilt", "metal", base="c9a23e", high="f4dc84", low="6a4e14")
WEIGHT = material("weight", "metal", base="cf9f2a", high="ffe286", low="70500e")
POLE = material("pole", "flat", base="6c5842")
BANNER = material("banner", "flat", base="e6e6de")

SOCKET = hexc("16141c")
MOUTH = hexc("120a0e")
EYE = hexc("a6c2e2")
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


def both(*details):
    def detail(fs):
        for d in details:
            d(fs)
    return detail


def ao(bottom=0, top=0, dark=0.72, faces=SIDES):
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


# ----------------------------------------------------------------------------------------------- details

def face(fs):
    """A gaunt face: deep sockets under the brow with a faint cold light in each, hollow cheeks, a mouth hanging open."""
    f = fs["front"]
    w = f.w
    for cx in (1, w - 2):
        f.px(cx, 2, SOCKET)
        f.px(cx, 3, SOCKET)
        f.px(cx + (-1 if cx < w // 2 else 1), 2, shade(SKIN.c["dark"], 0.7))
        f.glow(cx, 3, EYE)
    for x in range(w):
        mul(f, x, 1, 1.12)          # the brow catches the light
    f.px(w // 2 - 1, 4, shade(SKIN.c["dark"], 0.8))   # the shadow of the nose
    for x in (0, w - 1):            # the hollows under the cheekbones
        mul(f, x, 4, 0.72)
        mul(f, x, 5, 0.62)
    for x in (w // 2 - 1, w // 2):  # the open mouth
        f.px(x, 5, MOUTH)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(2, 5):
            for x in range(g.w):
                mul(g, x, y, 0.86)


def jaw(fs):
    """The slack lower jaw: the dark of the open mouth along its top, a few grey teeth."""
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, hexc("8a8478") if x % 2 else MOUTH)
    fs["top"].fill(lambda x, y: MOUTH)
    for n in ("left", "right", "bottom"):
        g = fs[n]
        for x in range(g.w):
            for y in range(g.h):
                mul(g, x, y, 0.8)


def ribs(fs):
    """The ribcage showing through the skin, the sternum down the middle, the spine down the back."""
    f = fs["front"]
    for y in range(f.h):
        for x in range(1, f.w - 1):
            if y % 2 == 1 and x != f.w // 2:
                mul(f, x, y, 0.74)
    for y in range(f.h):
        mul(f, f.w // 2, y, 1.12)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(1, g.h, 2):
            for x in range(g.w):
                mul(g, x, y, 0.8)
    b = fs["back"]
    for y in range(b.h):
        mul(b, b.w // 2, y, 1.15 if y % 2 else 0.8)


def belly(fs):
    """A pinched, sunken belly."""
    f = fs["front"]
    for y in range(f.h):
        mul(f, 0, y, 0.78)
        mul(f, f.w - 1, y, 0.78)
    for x in range(1, f.w - 1):
        mul(f, x, 0, 0.85)


def ragged(fs):
    """Torn cloth: the hem eaten into holes and tatters, stains above it."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            r = f.rng.random()
            if r < 0.45:
                f.px(x, f.h - 1, (0, 0, 0), alpha=0)
                if f.rng.random() < 0.4 and f.h > 3:
                    f.px(x, f.h - 2, (0, 0, 0), alpha=0)
            if f.h > 3 and f.rng.random() < 0.3:
                mul(f, x, f.rng.randrange(f.h - 1), 0.7)
        # a hole or two torn through
        if f.w * f.h > 20:
            for _ in range(f.w * f.h // 40):
                f.px(f.rng.randrange(f.w), f.rng.randrange(f.h - 1), (0, 0, 0), alpha=0)


def locks(fs):
    """Lank hair in strings, with gaps between them."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if f.rng.random() < 0.35:
                f.px(x, f.h - 1, (0, 0, 0), alpha=0)
            if x % 2 and f.h > 2:
                for y in range(f.h):
                    mul(f, x, y, 0.8)


def sparse(fs):
    """Thin hair on the crown: the scalp shows through."""
    for n in ("top",) + SIDES:
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                if f.rng.random() < 0.18:
                    f.px(x, y, shade(SKIN.c["base"], 0.85))


def neck(fs):
    """Stringy tendons."""
    for n in ("front", "left", "right"):
        f = fs[n]
        for y in range(f.h):
            mul(f, f.w // 2, y, 0.82)


def fingers(fs):
    for n in ("front", "back"):
        f = fs[n]
        for x in range(1, f.w, 2):
            for y in range(f.h):
                mul(f, x, y, 0.78)


def toes(fs):
    f = fs["front"]
    for x in range(1, f.w, 2):
        for y in range(f.h):
            mul(f, x, y, 0.7)


def knuckles(fs):
    """Bony joints: a lit ridge and a shadow under it."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            mul(f, x, 0, 1.12)
            mul(f, x, f.h - 1, 0.82)


def gilt(fs):
    """The hypocrites' cloaks: dazzling gold on the outside, and (Dante says) all lead within: the gold rubbed through
    to dull grey along the folds and the hem."""
    lead = hexc("5c5e66")
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            if (x + f.rng.randrange(2)) % 4 == 0:
                for y in range(f.h):
                    if f.rng.random() < 0.5:
                        mul(f, x, y, 0.7)
            if f.rng.random() < 0.5:
                f.px(x, f.h - 1, lead)
    for n in ("bottom",):
        fs[n].fill(lambda x, y: lead)


def weight_detail(fs):
    """A great weight of gold: dented, with coins and lumps pressed into it."""
    for n in ("front", "back", "left", "right", "top", "bottom"):
        f = fs[n]
        for _ in range(max(2, f.w * f.h // 14)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            mul(f, x, y, 1.25)
            mul(f, x + 1, y, 0.75)
            mul(f, x, y + 1, 0.75)


def banner_cloth(fs):
    """A plain white banner, tattered at its free edge, as blank as the neutrals' cause."""
    for n in ("left", "right"):
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                if y % 3 == 0:
                    mul(f, x, y, 0.92)
        # the free edge (the front of the sheet at rest) frays
        for y in range(f.h):
            fx = 0 if n == "right" else f.w - 1
            if f.rng.random() < 0.5:
                f.px(fx, y, (0, 0, 0), alpha=0)


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("damned", texture_width=128)
    root = m.bone("root", (0, 12, 0))

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mir = s == -1
        leg = root.bone(f"{side}_leg", (2 * s, 12, 0))
        leg.cube(f"{side}_thigh", (x(0.5, 3), 6.5, -1.5), (3, 6, 3), SKIN, mirror=mir, inflate=-0.25, detail=ao(top=1))
        shin = leg.bone(f"{side}_shin", (2 * s, 6.5, 0))
        shin.cube(f"{side}_knee", (x(0.5, 3), 5, -1.7), (3, 2, 3), SKIN, mirror=mir, inflate=-0.3, detail=knuckles)
        shin.cube(f"{side}_shin", (x(0.5, 3), 1.5, -1.5), (3, 5, 3), SKIN, mirror=mir, inflate=-0.45)
        shin.cube(f"{side}_foot", (x(0.5, 3), 0, -2.5), (3, 2, 4), SKIN, mirror=mir, inflate=-0.2, detail=toes)

    body = root.bone("body", (0, 12, 0))
    body.cube("pelvis", (-3.5, 10.5, -2), (7, 3, 4), SKIN, detail=ao(bottom=1))
    body.cube("loincloth", (-3, 6.5, -2.4), (6, 5, 1), RAG, detail=ragged)
    body.cube("loincloth_back", (-3, 7.5, 1.6), (6, 4, 1), RAG, detail=ragged)
    body.cube("belly", (-2.5, 13, -1.5), (5, 4, 3), SKIN, detail=belly)
    body.cube("chest", (-3.5, 16.5, -2), (7, 5, 4), SKIN, detail=both(ribs, ao(bottom=1, dark=0.8)))
    body.cube("shoulders", (-4.5, 20.5, -2), (9, 2, 4), SKIN, detail=ao(top=1, dark=0.9))
    # a torn shroud over the left shoulder, across the chest to the right hip, and down the back
    body.cube("shroud", (-1.5, 11.5, -2.6), (3, 12, 1), RAG, rotation=(0, 0, 30), origin=(0, 18, -2.3), detail=ragged)
    body.cube("shroud_back", (-4.4, 9.5, 1.7), (4, 13, 1), RAG, rotation=(0, 0, -6), origin=(-2.4, 22, 2), detail=ragged)

    head = body.bone("head", (0, 22, 0))
    head.cube("neck", (-1, 21.5, -1), (2, 3, 2), SKIN, detail=neck)
    head.cube("head", (-3, 24, -3), (6, 6, 6), SKIN, detail=face)
    jaw_bone = head.bone("jaw", (0, 25, -0.5), rotation=(-6, 0, 0))
    jaw_bone.cube("jaw", (-2.5, 23.2, -3.2), (5, 2, 4), SKIN, detail=jaw)
    head.cube("hair", (-3, 29.6, -3), (6, 1, 6), HAIR, inflate=0.25, detail=sparse)
    head.cube("hair_back", (-3, 23.5, 2.1), (6, 7, 1), HAIR, inflate=0.2, detail=locks)
    head.cube("right_hair", (2.5, 25.5, -2), (1, 5, 4), HAIR, inflate=0.15, detail=locks)
    head.cube("left_hair", (-3.5, 25.5, -2), (1, 5, 4), HAIR, mirror=True, inflate=0.15, detail=locks)
    # the hypocrite's hood: open at the face, gilded (shown only in Malebolge)
    hood = head.bone("hood", (0, 24, 0))
    hood.cube("hood_top", (-3.5, 30, -3.5), (7, 2, 7), GILT, inflate=0.2, detail=gilt)
    hood.cube("hood_back", (-3.5, 23.5, 2.5), (7, 7, 1), GILT, inflate=0.2, detail=gilt)
    hood.cube("hood_right", (3, 24, -3.3), (1, 6, 6), GILT, inflate=0.2, detail=gilt)
    hood.cube("hood_left", (-4, 24, -3.3), (1, 6, 6), GILT, mirror=True, inflate=0.2, detail=gilt)

    # the hypocrite's cloak: a heavy mantle on the shoulders and a long cape to the heels
    cloak = body.bone("cloak", (0, 22, 0))
    cloak.cube("mantle", (-5.5, 19.5, -2.6), (11, 3, 5), GILT, inflate=0.2, detail=gilt)
    cloak.cube("cape", (-5.5, 1, 2), (11, 20, 1), GILT, rotation=(6, 0, 0), origin=(0, 21, 2.4), detail=gilt)
    cloak.cube("cape_right", (5, 3, -2.2), (1, 18, 4), GILT, detail=gilt)
    cloak.cube("cape_left", (-6, 3, -2.2), (1, 18, 4), GILT, mirror=True, detail=gilt)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mir = s == -1
        arm = body.bone(f"{side}_arm", (4.5 * s, 21, 0))
        arm.cube(f"{side}_upper", (x(3.5, 3), 15.5, -1.5), (3, 6, 3), SKIN, mirror=mir, inflate=-0.3, detail=ao(top=1, dark=0.85))
        fore = arm.bone(f"{side}_forearm", (5 * s, 15.5, 0), rotation=(8, 0, 0))
        fore.cube(f"{side}_elbow", (x(3.5, 3), 14.5, -1.5), (3, 2, 3), SKIN, mirror=mir, inflate=-0.35, detail=knuckles)
        fore.cube(f"{side}_forearm", (x(3.5, 3), 10.5, -1.5), (3, 5, 3), SKIN, mirror=mir, inflate=-0.45)
        fore.cube(f"{side}_hand", (x(4, 2), 8.5, -1), (2, 2, 2), SKIN, mirror=mir)
        fore.cube(f"{side}_fingers", (x(4, 2), 7.5, -1.2), (2, 1, 2), SKIN, mirror=mir, inflate=-0.05, detail=fingers)
        if s == 1:
            # the neutrals' banner, its staff gripped in the right hand: it points down the arm, so held high it stands
            # up over the head and the cloth streams out behind (shown only for the Vestibule's leader)
            banner = fore.bone("banner", (5, 9.5, 0))
            banner.cube("staff", (4.5, -19, -0.5), (1, 29, 1), POLE)
            banner.cube("staff_knob", (4, -20, -1), (2, 1, 2), POLE)
            banner.cube("cloth", (4.5, -18.5, -10.5), (1, 9, 10), BANNER, detail=banner_cloth)

    # the great weight of Greed, rolled before the chest (shown only in Greed)
    weight = root.bone("weight", (0, 8, -14))
    weight.cube("weight", (-6, 2, -20), (12, 12, 12), WEIGHT, detail=weight_detail)
    weight.cube("weight_x", (-7, 3, -19), (14, 10, 10), WEIGHT, detail=weight_detail)
    weight.cube("weight_y", (-5, 0, -19), (10, 16, 10), WEIGHT, detail=weight_detail)
    weight.cube("weight_z", (-5, 3, -22), (10, 10, 16), WEIGHT, detail=weight_detail)

    animations(m)
    return m


ZERO = (0, 0, 0)


def loop(anim, bone, channel, period, values, interp="catmullrom"):
    """Keys evenly spread over one period, ending where they began."""
    n = len(values)
    keys = [(period * i / n, v) for i, v in enumerate(values)] + [(period, values[0])]
    getattr(anim, channel)(bone, keys, interp)


def animations(m):
    # Limbo's longing: standing, bowed, swaying a little, the head drooping
    idle = m.animation("idle", 4.0, loop=True)
    loop(idle, "body", "rot", 4.0, [(-6, 0, -2), (-8, 0, 2)])
    loop(idle, "head", "rot", 4.0, [(-14, 0, 0), (-10, 10, 0), (-16, 0, 0), (-10, -10, 0)])
    loop(idle, "right_arm", "rot", 4.0, [(4, 0, 3), (0, 0, 5)])
    loop(idle, "left_arm", "rot", 4.0, [(0, 0, -5), (4, 0, -3)])

    # the Vestibule: running after the banner, legs pumping, hands beating at the stinging wasps
    run = m.animation("run", 0.8, loop=True)
    loop(run, "body", "rot", 0.8, [(-16, 6, 0), (-20, 0, 0), (-16, -6, 0), (-20, 0, 0)])
    loop(run, "root", "pos", 0.8, [(0, 0, 0), (0, 1, 0), (0, 0, 0), (0, 1, 0)])
    loop(run, "right_leg", "rot", 0.8, [(40, 0, 0), (0, 0, 0), (-40, 0, 0), (0, 0, 0)])
    loop(run, "left_leg", "rot", 0.8, [(-40, 0, 0), (0, 0, 0), (40, 0, 0), (0, 0, 0)])
    loop(run, "right_shin", "rot", 0.8, [(-10, 0, 0), (-70, 0, 0), (-35, 0, 0), (-20, 0, 0)])
    loop(run, "left_shin", "rot", 0.8, [(-35, 0, 0), (-20, 0, 0), (-10, 0, 0), (-70, 0, 0)])
    loop(run, "right_arm", "rot", 0.8, [(140, 0, 30), (160, 0, 10), (120, 0, 40), (155, 0, 20)])
    loop(run, "right_forearm", "rot", 0.8, [(60, 0, 0), (100, 0, 0), (40, 0, 0), (90, 0, 0)])
    loop(run, "left_arm", "rot", 0.8, [(60, 0, -20), (130, 0, -40), (40, 0, -10), (110, 0, -30)])
    loop(run, "left_forearm", "rot", 0.8, [(80, 0, 0), (40, 0, 0), (90, 0, 0), (50, 0, 0)])
    loop(run, "head", "rot", 0.8, [(10, 20, 0), (0, 0, 8), (10, -20, 0), (0, 0, -8)])

    # the Vestibule's leader: running with the white banner held high, the other arm pumping
    lead = m.animation("lead", 0.8, loop=True)
    loop(lead, "body", "rot", 0.8, [(-12, 4, 0), (-15, 0, 0), (-12, -4, 0), (-15, 0, 0)])
    loop(lead, "root", "pos", 0.8, [(0, 0, 0), (0, 1, 0), (0, 0, 0), (0, 1, 0)])
    loop(lead, "right_leg", "rot", 0.8, [(40, 0, 0), (0, 0, 0), (-40, 0, 0), (0, 0, 0)])
    loop(lead, "left_leg", "rot", 0.8, [(-40, 0, 0), (0, 0, 0), (40, 0, 0), (0, 0, 0)])
    loop(lead, "right_shin", "rot", 0.8, [(-10, 0, 0), (-70, 0, 0), (-35, 0, 0), (-20, 0, 0)])
    loop(lead, "left_shin", "rot", 0.8, [(-35, 0, 0), (-20, 0, 0), (-10, 0, 0), (-70, 0, 0)])
    loop(lead, "right_arm", "rot", 0.8, [(168, 0, 6), (172, 0, 4)])
    loop(lead, "right_forearm", "rot", 0.8, [(4, 0, 0), (8, 0, 0)])
    loop(lead, "left_arm", "rot", 0.8, [(-30, 0, -6), (0, 0, 0), (40, 0, -6), (0, 0, 0)])
    loop(lead, "left_forearm", "rot", 0.8, [(50, 0, 0), (30, 0, 0), (60, 0, 0), (30, 0, 0)])
    loop(lead, "head", "rot", 0.8, [(14, 0, 0), (18, 0, 0)])

    # Heresy: standing in the burning tomb, twisting, arms flailing in the flames, the head thrown about
    writhe = m.animation("writhe", 1.6, loop=True)
    loop(writhe, "body", "rot", 1.6, [(-8, 25, 8), (-12, 0, -10), (-8, -25, 8), (-4, 0, -10)])
    loop(writhe, "right_arm", "rot", 1.6, [(40, 0, 30), (130, 0, 60), (20, 0, 20), (150, 0, 40)])
    loop(writhe, "right_forearm", "rot", 1.6, [(30, 0, 0), (90, 0, 0), (60, 0, 0), (20, 0, 0)])
    loop(writhe, "left_arm", "rot", 1.6, [(140, 0, -50), (30, 0, -20), (120, 0, -60), (50, 0, -30)])
    loop(writhe, "left_forearm", "rot", 1.6, [(80, 0, 0), (30, 0, 0), (90, 0, 0), (40, 0, 0)])
    loop(writhe, "head", "rot", 1.6, [(25, 0, 15), (-10, 15, 0), (30, 0, -15), (-5, -15, 0)])
    loop(writhe, "jaw", "rot", 1.6, [(-20, 0, 0), (-8, 0, 0)])
    loop(writhe, "right_leg", "rot", 1.6, [(15, 0, 4), (25, 0, 0)])
    loop(writhe, "right_shin", "rot", 1.6, [(-25, 0, 0), (-40, 0, 0)])
    loop(writhe, "left_leg", "rot", 1.6, [(25, 0, -4), (12, 0, 0)])
    loop(writhe, "left_shin", "rot", 1.6, [(-40, 0, 0), (-20, 0, 0)])

    # Lust: tossed by the storm, turning head over heels, limbs splayed and clawing at the air
    tumble = m.animation("tumble", 2.4, loop=True)
    tumble.rot("root", [(0, (0, 0, 0)), (0.6, (90, 20, 0)), (1.2, (180, 0, 0)), (1.8, (270, -20, 0)), (2.4, (360, 0, 0))], "linear")
    loop(tumble, "right_arm", "rot", 2.4, [(40, 0, 100), (-20, 0, 120), (60, 0, 80), (0, 0, 140)])
    loop(tumble, "left_arm", "rot", 2.4, [(0, 0, -130), (50, 0, -90), (-30, 0, -120), (30, 0, -100)])
    loop(tumble, "right_forearm", "rot", 2.4, [(20, 0, 0), (70, 0, 0), (30, 0, 0), (60, 0, 0)])
    loop(tumble, "left_forearm", "rot", 2.4, [(60, 0, 0), (20, 0, 0), (70, 0, 0), (30, 0, 0)])
    loop(tumble, "right_leg", "rot", 2.4, [(30, 0, 25), (-30, 0, 15), (20, 0, 30), (-20, 0, 10)])
    loop(tumble, "left_leg", "rot", 2.4, [(-30, 0, -15), (30, 0, -25), (-20, 0, -10), (20, 0, -30)])
    loop(tumble, "right_shin", "rot", 2.4, [(-30, 0, 0), (-60, 0, 0)])
    loop(tumble, "left_shin", "rot", 2.4, [(-60, 0, 0), (-30, 0, 0)])
    loop(tumble, "head", "rot", 2.4, [(25, 0, 0), (-10, 0, 15), (30, 0, 0), (-10, 0, -15)])
    loop(tumble, "jaw", "rot", 2.4, [(-22, 0, 0), (-12, 0, 0)])

    # Gluttony: lying on its back half-sunk in the mud, rolling, the arms clawing up out of it, the head straining
    wallow = m.animation("wallow", 3.0, loop=True)
    loop(wallow, "root", "rot", 3.0, [(88, 0, 10), (86, 0, -4), (88, 0, -12), (86, 0, 4)])
    loop(wallow, "root", "pos", 3.0, [(0, -10, 0), (0, -10.5, 0)])
    loop(wallow, "right_arm", "rot", 3.0, [(70, 0, 30), (150, 0, 10), (90, 0, 40), (130, 0, 20)])
    loop(wallow, "right_forearm", "rot", 3.0, [(40, 0, 0), (10, 0, 0), (60, 0, 0), (20, 0, 0)])
    loop(wallow, "left_arm", "rot", 3.0, [(140, 0, -15), (80, 0, -40), (160, 0, -10), (60, 0, -30)])
    loop(wallow, "left_forearm", "rot", 3.0, [(10, 0, 0), (50, 0, 0), (20, 0, 0), (60, 0, 0)])
    loop(wallow, "right_leg", "rot", 3.0, [(30, 0, 8), (5, 0, 4)])
    loop(wallow, "right_shin", "rot", 3.0, [(-50, 0, 0), (-15, 0, 0)])
    loop(wallow, "left_leg", "rot", 3.0, [(5, 0, -4), (35, 0, -8)])
    loop(wallow, "left_shin", "rot", 3.0, [(-15, 0, 0), (-55, 0, 0)])
    loop(wallow, "head", "rot", 3.0, [(-30, 15, 0), (-20, 0, 0), (-32, -15, 0), (-22, 0, 0)])
    loop(wallow, "jaw", "rot", 3.0, [(-20, 0, 0), (-10, 0, 0)])

    # Greed: rolling the great weight with the chest, bent double, legs driving, the weight turning
    push = m.animation("push", 3.0, loop=True)
    loop(push, "body", "rot", 3.0, [(-50, 0, 2), (-46, 0, -2)])
    loop(push, "root", "pos", 3.0, [(0, 0, 0), (0, -0.6, 0)])
    loop(push, "head", "rot", 3.0, [(40, 0, 0), (34, 0, 0)])
    loop(push, "right_arm", "rot", 3.0, [(62, 0, -12), (58, 0, -14)])
    loop(push, "left_arm", "rot", 3.0, [(58, 0, 12), (62, 0, 14)])
    loop(push, "right_forearm", "rot", 3.0, [(20, 0, 0), (26, 0, 0)])
    loop(push, "left_forearm", "rot", 3.0, [(26, 0, 0), (20, 0, 0)])
    loop(push, "right_leg", "rot", 3.0, [(10, 0, 0), (-10, 0, 0), (-40, 0, 0), (-10, 0, 0)])
    loop(push, "left_leg", "rot", 3.0, [(-40, 0, 0), (-10, 0, 0), (10, 0, 0), (-10, 0, 0)])
    loop(push, "right_shin", "rot", 3.0, [(-40, 0, 0), (-30, 0, 0), (-5, 0, 0), (-30, 0, 0)])
    loop(push, "left_shin", "rot", 3.0, [(-5, 0, 0), (-30, 0, 0), (-40, 0, 0), (-30, 0, 0)])
    push.rot("weight", [(0, (0, 0, 0)), (1.0, (-120, 0, 0)), (2.0, (-240, 0, 0)), (3.0, (-360, 0, 0))], "linear")

    # Wrath: fighting another soul in the shallows, both fists, the body twisting into each blow
    brawl = m.animation("brawl", 1.0, loop=True)
    brawl.rot("body", [(0, (-8, -20, 0)), (0.25, (-14, 25, 0)), (0.5, (-8, 20, 0)), (0.75, (-14, -25, 0)), (1.0, (-8, -20, 0))])
    brawl.rot("right_arm", [(0, (30, 0, 10)), (0.25, (95, 0, -10)), (0.5, (40, 0, 15)), (0.75, (35, 0, 15)), (1.0, (30, 0, 10))])
    brawl.rot("right_forearm", [(0, (90, 0, 0)), (0.25, (10, 0, 0)), (0.5, (80, 0, 0)), (1.0, (90, 0, 0))])
    brawl.rot("left_arm", [(0, (35, 0, -15)), (0.25, (40, 0, -15)), (0.5, (30, 0, -10)), (0.75, (95, 0, 10)), (1.0, (35, 0, -15))])
    brawl.rot("left_forearm", [(0, (80, 0, 0)), (0.5, (90, 0, 0)), (0.75, (10, 0, 0)), (1.0, (80, 0, 0))])
    brawl.rot("head", [(0, (-10, 15, 0)), (0.25, (-18, -10, 0)), (0.5, (-10, -15, 0)), (0.75, (-18, 10, 0)), (1.0, (-10, 15, 0))])
    loop(brawl, "jaw", "rot", 1.0, [(-16, 0, 0), (-6, 0, 0)])
    brawl.rot("right_leg", [(0, (20, 0, 4))])
    brawl.rot("left_leg", [(0, (-15, 0, -4))])
    brawl.rot("right_shin", [(0, (-20, 0, 0))])
    brawl.event(0.25, "right").event(0.75, "left")

    # the Burning Sands: hopping on the hot sand, hands beating at the flakes of fire on the head and shoulders
    burn = m.animation("burn", 0.8, loop=True)
    loop(burn, "root", "pos", 0.8, [(0, 0, 0), (0, 1.5, 0), (0, 0, 0), (0, 1.5, 0)])
    loop(burn, "body", "rot", 0.8, [(-6, 14, 0), (-10, 0, 6), (-6, -14, 0), (-10, 0, -6)])
    loop(burn, "right_arm", "rot", 0.8, [(40, 0, 15), (120, 0, 50), (60, 0, 10), (150, 0, 35)])
    loop(burn, "right_forearm", "rot", 0.8, [(60, 0, 0), (120, 0, 0), (80, 0, 0), (110, 0, 0)])
    loop(burn, "left_arm", "rot", 0.8, [(150, 0, -35), (60, 0, -10), (120, 0, -50), (40, 0, -15)])
    loop(burn, "left_forearm", "rot", 0.8, [(110, 0, 0), (80, 0, 0), (120, 0, 0), (60, 0, 0)])
    loop(burn, "right_leg", "rot", 0.8, [(25, 0, 0), (0, 0, 0), (-10, 0, 0), (0, 0, 0)])
    loop(burn, "left_leg", "rot", 0.8, [(-10, 0, 0), (0, 0, 0), (25, 0, 0), (0, 0, 0)])
    loop(burn, "right_shin", "rot", 0.8, [(-40, 0, 0), (-10, 0, 0)])
    loop(burn, "left_shin", "rot", 0.8, [(-10, 0, 0), (-40, 0, 0)])
    loop(burn, "head", "rot", 0.8, [(20, 10, 0), (-5, 0, 10), (20, -10, 0), (-5, 0, -10)])
    loop(burn, "jaw", "rot", 0.8, [(-20, 0, 0), (-10, 0, 0)])

    # the river of blood: sunk to the chest or the eyes, bobbing, both arms flailing over the surface, the head back
    bob = m.animation("bob", 2.4, loop=True)
    loop(bob, "root", "pos", 2.4, [(0, 0, 0), (0, 2.5, 0)])
    loop(bob, "body", "rot", 2.4, [(4, 0, 8), (0, 0, -8)])
    loop(bob, "right_arm", "rot", 2.4, [(160, 0, -20), (140, 0, -45), (170, 0, -10), (130, 0, -35)])
    loop(bob, "right_forearm", "rot", 2.4, [(20, 0, 0), (50, 0, 0), (10, 0, 0), (40, 0, 0)])
    loop(bob, "left_arm", "rot", 2.4, [(135, 0, 40), (165, 0, 15), (130, 0, 45), (170, 0, 10)])
    loop(bob, "left_forearm", "rot", 2.4, [(50, 0, 0), (20, 0, 0), (40, 0, 0), (10, 0, 0)])
    loop(bob, "head", "rot", 2.4, [(32, 0, 0), (22, 0, 8), (34, 0, 0), (22, 0, -8)])
    loop(bob, "jaw", "rot", 2.4, [(-24, 0, 0), (-12, 0, 0)])
    loop(bob, "right_leg", "rot", 2.4, [(20, 0, 0), (-20, 0, 0)])
    loop(bob, "left_leg", "rot", 2.4, [(-20, 0, 0), (20, 0, 0)])

    # Cocytus: frozen to the neck, only the head and shoulders free, shaking, the teeth chattering
    shiver = m.animation("shiver", 0.5, loop=True)
    loop(shiver, "head", "rot", 0.5, [(-12, 0, 4), (-10, 3, -4), (-12, 0, 4), (-10, -3, -4)], "linear")
    loop(shiver, "body", "rot", 0.5, [(0, 0, 1.5), (0, 0, -1.5)], "linear")
    loop(shiver, "jaw", "rot", 0.5, [(-12, 0, 0), (0, 0, 0), (-12, 0, 0), (0, 0, 0)], "linear")
    loop(shiver, "right_arm", "rot", 0.5, [(0, 0, -10), (0, 0, -8)])
    loop(shiver, "left_arm", "rot", 0.5, [(0, 0, 10), (0, 0, 8)])

    # Malebolge: a slow procession, bowed under the leaden cloak, small heavy steps
    march = m.animation("march", 2.4, loop=True)
    loop(march, "body", "rot", 2.4, [(-22, 0, 2), (-24, 0, 0), (-22, 0, -2), (-24, 0, 0)])
    loop(march, "root", "pos", 2.4, [(0, 0, 0), (0, -0.5, 0), (0, 0, 0), (0, -0.5, 0)])
    loop(march, "head", "rot", 2.4, [(-8, 0, 0), (-12, 0, 0)])
    loop(march, "right_leg", "rot", 2.4, [(18, 0, 0), (0, 0, 0), (-18, 0, 0), (0, 0, 0)])
    loop(march, "left_leg", "rot", 2.4, [(-18, 0, 0), (0, 0, 0), (18, 0, 0), (0, 0, 0)])
    loop(march, "right_shin", "rot", 2.4, [(-5, 0, 0), (-25, 0, 0), (-10, 0, 0), (-5, 0, 0)])
    loop(march, "left_shin", "rot", 2.4, [(-10, 0, 0), (-5, 0, 0), (-5, 0, 0), (-25, 0, 0)])
    loop(march, "right_arm", "rot", 2.4, [(-6, 0, 4), (6, 0, 4)])
    loop(march, "left_arm", "rot", 2.4, [(6, 0, -4), (-6, 0, -4)])

    # over any of them: a scream, the head thrown back, the jaw wide, both arms flung up
    scream = m.animation("scream", 1.6)
    scream.rot("head", [(0, ZERO), (0.2, (40, 0, 0)), (1.1, (34, 0, 0)), (1.6, ZERO)])
    scream.rot("jaw", [(0, ZERO), (0.15, (-28, 0, 0)), (1.2, (-24, 0, 0)), (1.6, ZERO)])
    scream.rot("body", [(0, ZERO), (0.25, (12, 0, 0)), (1.1, (10, 0, 0)), (1.6, ZERO)])
    scream.rot("right_arm", [(0, ZERO), (0.3, (155, 0, -30)), (1.1, (145, 0, -40)), (1.6, ZERO)])
    scream.rot("left_arm", [(0, ZERO), (0.3, (155, 0, 30)), (1.1, (145, 0, 40)), (1.6, ZERO)])
    scream.rot("right_forearm", [(0, ZERO), (0.3, (-10, 0, 0)), (1.1, (-4, 0, 0)), (1.6, ZERO)])
    scream.rot("left_forearm", [(0, ZERO), (0.3, (-10, 0, 0)), (1.1, (-4, 0, 0)), (1.6, ZERO)])
    scream.event(0.1, "scream")

    # Limbo's sigh: the shoulders lift and fall, the face lifts toward the light it will never see, then droops
    sigh = m.animation("sigh", 2.4)
    sigh.rot("body", [(0, ZERO), (1.0, (6, 0, 0)), (1.8, (-4, 0, 0)), (2.4, ZERO)])
    sigh.pos("body", [(0, ZERO), (1.0, (0, 0.5, 0)), (1.8, (0, -0.2, 0)), (2.4, ZERO)])
    sigh.rot("head", [(0, ZERO), (0.9, (16, 0, 0)), (1.8, (-12, 0, 0)), (2.4, ZERO)])
    sigh.rot("right_arm", [(0, ZERO), (0.9, (0, 0, 6)), (2.4, ZERO)])
    sigh.rot("left_arm", [(0, ZERO), (0.9, (0, 0, -6)), (2.4, ZERO)])
    sigh.event(0.2, "sigh")

    # a sting, a blow, the weights clashing: a jolt, a hand slapping at the neck
    flinch = m.animation("flinch", 0.5)
    flinch.rot("body", [(0, ZERO), (0.08, (-14, 10, 0)), (0.5, ZERO)])
    flinch.rot("head", [(0, ZERO), (0.08, (10, -20, 0)), (0.5, ZERO)])
    flinch.rot("right_arm", [(0, ZERO), (0.1, (100, 0, -40)), (0.5, ZERO)])
    flinch.rot("right_forearm", [(0, ZERO), (0.1, (60, 0, 0)), (0.5, ZERO)])


if __name__ == "__main__":
    build().write()
