#!/usr/bin/env python3
"""Farinata degli Uberti (Inferno X), the Ghibelline captain, risen in his burning tomb "from the girdle upward...
with his breast and his forehead, as if he held Hell in great disdain". A proud old soldier in a scarlet Florentine
gown and cap over his mail, his tomb's fire licking at his skirts, a sword at his side he never draws.

Run from the repo root:  python3 tools/art/farinata.py && python3 tools/gen_bosses.py

He stands in his tomb (the fight builds it round him: only his upper body shows over the rim). The events time his
fight (FarinataFight): "flash" opens the window to block his Disdain and "hit" is when his fist strikes; "blast" is
his Exile; "fire" is when he commands the tombs round him; "sink" is when he slumps back into his tomb; "roar" his
fury; "rise" and "speak" his entrance.

He is built at a man's proportions (the head about a third of him from the girdle up, the shoulders two heads
across): a lean old face with a strong nose, a short white beard, a rolled Florentine cap with its tail hanging to
his shoulder, the high collar of his mail under the gown, puffed sleeves that widen at the cuff, and arms that bend
at the elbow (the forearms are their own bones), so that he can fold them across his chest.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

FACE = material("face", "skin", base="b08a6a", dark="7a5a44")
GOWN = material("gown", "robe", base="8a1a1a", fold="5a0e0e")
HEM = material("hem", "robe", base="8a1a1a", fold="5a0e0e", trim="c8a040")
TRIM = hexc("c8a040")
SLEEVE = material("sleeve", "robe", base="7e1616", fold="520c0c")
MAIL = material("mail", "metal", base="6a6a70", high="9a9aa0", low="3a3a40")
CAP = material("cap", "robe", base="6a1010", fold="3a0808")
HAIR = material("hair", "hair", light="c8c0b0", dark="7a7468")
STEEL = material("steel", "metal", base="8a8a90", high="d0d0d8", low="4a4a50")
BRASS = material("brass", "metal", base="b08a3a", high="e0c070", low="6a4a1a")
LEATHER = material("leather", "flat", base="3a2414")
EYE = hexc("ffb040")
EMBER = hexc("ff7a1a")
EMBER_HOT = hexc("ffd25a")
SOCKET = hexc("3a2418")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- helpers

def side_x(a, w, s):
    """A cube from a to a + w on the right becomes -(a + w) to -a on the left."""
    return a if s == 1 else -(a + w)


def seg(bone, name, p0, p1, w, h, mat, extra=0.0, **kw):
    """A cube laid along the line p0 -> p1 (its length on its own Z axis), w across and h thick, turned about its own
    centre. extra lengthens it so that segments overlap at their joints instead of opening gaps."""
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
    """A detail: the side faces shaded along their lowest rows (where the part sinks into shadow) and their highest
    (where it tucks under the part above)."""
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

def face_detail(fs):
    """A lean, stern old face: the forehead lined under the cap, eyes burning deep under the brow, hollow cheeks,
    and the shadow of the cap's brim on the temples."""
    f = fs["front"]
    w = f.w
    dark = shade(FACE.c["dark"], 0.8)
    for x in range(w):
        mul(f, x, 0, 0.7)  # the brim's shadow
    for x in range(1, w - 1):
        if x != w // 2:
            f.px(x, 1, shade(FACE.c["base"], 0.92))
    for cx in (1, w - 2):
        f.px(cx, 3, SOCKET)
        f.glow(cx, 3, EYE)
        mul(f, cx, 4, 0.82)  # the hollow under the eye
    f.px(0, 3, shade(FACE.c["dark"], 0.7))
    f.px(w - 1, 3, shade(FACE.c["dark"], 0.7))
    for y in (4, 5):  # hollow cheeks
        mul(f, 0, y, 0.78)
        mul(f, w - 1, y, 0.78)
    f.px(1, 5, dark)  # lines from the nose to the mouth
    f.px(w - 2, 5, dark)
    for n in ("left", "right"):
        g = fs[n]
        for y in range(g.h):
            mul(g, g.w - 1 if n == "right" else 0, y, 0.85)
        for x in range(g.w):
            mul(g, x, 0, 0.7)
        for y in range(1, g.h - 1):  # cropped white hair over the back half of the head, round behind the ear
            for i in range(3 if y < g.h - 2 else 2):
                x = i if n == "right" else g.w - 1 - i
                if g.rng.random() < 0.85:
                    g.px(x, y, shade(HAIR.c["light"], 0.8 + 0.25 * g.rng.random()))
        g.px(3 if n == "right" else g.w - 4, 1, shade(HAIR.c["light"], 0.9))  # the temple
    b = fs["back"]
    for x in range(b.w):  # cropped white hair at the nape, under the cap, thinning to skin at the neck
        for y in range(1, b.h - 1):
            if y < b.h - 2 or b.rng.random() < 0.5:
                b.px(x, y, shade(HAIR.c["light"], 0.8 + 0.25 * b.rng.random()) if b.rng.random() < 0.75
                     else HAIR.c["dark"])


def brow_detail(fs):
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, HAIR.c["light"] if x not in (f.w // 2 - 1, f.w // 2) else shade(FACE.c["base"], 1.05))
    fs["bottom"].fill(lambda x, y: SOCKET)


def nose_detail(fs):
    f = fs["front"]
    f.px(0, f.h - 1, shade(FACE.c["dark"], 0.7))
    f.px(0, 0, shade(FACE.c["base"], 1.12))


def beard_detail(fs):
    """A short white beard, trimmed close."""
    for n in SIDES + ("bottom",):
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                f.px(x, y, HAIR.c["light"] if (x * 3 + y) % 4 else shade(HAIR.c["dark"], 1.1))


def mouth_detail(fs):
    """Thin, set lips under a white moustache."""
    f = fs["front"]
    for x in range(f.w):
        f.px(x, 0, HAIR.c["light"] if x % 2 == 0 else shade(HAIR.c["dark"], 1.15))
    fs["top"].fill(lambda x, y: HAIR.c["light"])


def mail_detail(fs):
    """Riveted rings: a staggered pattern of dark links with bright tops."""
    for n in SIDES + ("top",):
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                if (x + (y % 2)) % 2 == 0:
                    mul(f, x, y, 0.65)
                elif y % 2 == 0:
                    mul(f, x, y, 1.15)


def chest_detail(fs):
    """The gown's front: closed with a row of gilt buttons down its opening, shadowed under the collar, with a few
    folds pulled toward the belt."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid - 1, y, shade(TRIM, 0.85))
        if y % 2:
            f.px(mid, y, BRASS.c["high"])
    for x in range(f.w):
        mul(f, x, 0, 0.72)
    for y in range(f.h - 3, f.h):
        mul(f, 1, y, 0.8)
        mul(f, f.w - 2, y, 0.8)


def opening(fs):
    """Where the gown closes down his front: a dark seam edged in gold."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid, y, shade(GOWN.c["fold"], 0.8))
        f.px(mid - 1, y, shade(TRIM, 0.85 if y % 2 else 1.0))


def belt_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(0, f.w, 3):
            f.px(x, f.h // 2, BRASS.c["base"])  # studs


def buckle_detail(fs):
    f = fs["front"]
    f.px(f.w // 2, f.h // 2, LEATHER.c["base"])


def scorched(fs):
    """The gown's hem glows where the tomb's fire licks it, the embers climbing it in tongues."""
    for name in SIDES:
        f = fs[name]
        for x in range(f.w):
            tongue = 3 + (2 if (x * 5) % 7 < 2 else 0) + (1 if x % 3 == 0 else 0)
            for y in range(max(0, f.h - tongue), f.h):
                depth = f.h - 1 - y
                if depth < 2 or (x + y) % 3 != 0:
                    f.glow(x, y, EMBER_HOT if depth == 0 or (x * 7 + y) % 5 == 0 else EMBER)
            if f.h - tongue - 1 >= 0:
                mul(f, x, f.h - tongue - 1, 0.55)  # charred above the flame
                mul(f, x, max(0, f.h - tongue - 2), 0.75)


def skirt_folds(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(1, f.w - 1, 3):
            for y in range(f.h):
                mul(f, x, y, 0.75)


def cuff_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            f.px(x, f.h - 1, TRIM)


def hand_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            mul(f, x, f.h - 1, 0.75)
    f = fs["front"]
    if f.w >= 2:
        f.px(0, f.h - 2, shade(FACE.c["dark"], 0.85))  # the knuckles' crease


def cap_detail(fs):
    """The rolled brim of the cap: twisted cloth, lit along its top."""
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            mul(f, x, (x // 2) % f.h, 1.15)
            mul(f, x, (x // 2 + 1) % f.h, 0.8)


def grip_detail(fs):
    for n in SIDES:
        f = fs[n]
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 2:
                    mul(f, x, y, 0.7)


def scabbard_detail(fs):
    for n in SIDES:
        f = fs[n]
        for x in range(f.w):
            f.px(x, 0, BRASS.c["base"])
            f.px(x, f.h - 1, STEEL.c["base"])  # the chape at its tip


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("farinata", texture_width=128)

    # below the girdle (mostly in the tomb): the gown's skirt flaring as it falls, its hem burning; a studded belt
    # and the sheathed sword on his left hip
    body = m.bone("body", (0, 12, 0))
    body.cube("skirt", (-5.5, 0, -3.5), (11, 6, 7), HEM, detail=both(skirt_folds, scorched))
    body.cube("skirt_upper", (-5, 5.5, -3), (10, 6, 6), GOWN, detail=both(skirt_folds, opening))
    body.cube("belt", (-4.5, 11, -3), (9, 2, 6), LEATHER, inflate=0.2, detail=belt_detail)
    body.cube("buckle", (-1, 11, -3.3), (2, 2, 1), BRASS, detail=buckle_detail)
    body.cube("purse", (2.2, 8, -3.4), (2, 3, 1), LEATHER)
    seg(body, "sword", (-5.6, 10, 0.2), (-6.4, 1.5, 2.6), 1, 2, LEATHER, extra=0.4, detail=scabbard_detail)
    seg(body, "sword_grip", (-5.4, 14.2, -0.9), (-5.6, 11, 0.0), 1, 1, LEATHER, extra=0.2, detail=grip_detail)
    seg(body, "sword_guard", (-5.5, 11.2, -2.0), (-5.5, 10.6, 2.0), 1, 1, STEEL, extra=0.6)
    body.cube("sword_pommel", (-6, 14, -1.5), (1, 1, 1), BRASS, inflate=0.15)

    # from the girdle up: a narrow waist, a broad old chest, sloping shoulders; the mail's collar at the neck
    torso = body.bone("torso", (0, 13, 0))
    torso.cube("torso", (-4, 13, -2.5), (8, 5, 5), GOWN, detail=both(opening, ao(top=1)))
    torso.cube("chest", (-4.5, 17.5, -3), (9, 6, 6), GOWN, detail=chest_detail)
    for s, side in ((1, "r"), (-1, "l")):
        torso.cube(f"shoulder_{side}", (side_x(1.5, 4, s), 22.5, -2.5), (4, 2, 5), GOWN, rotation=(0, 0, -14 * s),
                   origin=(1.5 * s, 23.5, 0), mirror=s == -1)
    torso.cube("mail_collar", (-2.5, 23, -2.5), (5, 2, 5), MAIL, inflate=0.2, detail=mail_detail)
    torso.cube("neck", (-1.5, 24, -1.8), (3, 2, 3), FACE)

    # the head: a lean old face with a strong nose and a short white beard, under a rolled Florentine cap whose tail
    # hangs to his shoulder
    head = torso.bone("head", (0, 25, 0))
    head.cube("head", (-3, 25.5, -3.3), (6, 7, 6), FACE, detail=face_detail)
    head.cube("brow", (-3, 29.4, -4), (6, 1, 1), FACE, detail=brow_detail)
    head.cube("nose", (-0.5, 27.6, -4.4), (1, 2, 1), FACE, rotation=(8, 0, 0), origin=(0, 29.6, -3.6),
              detail=nose_detail)
    head.cube("lips", (-1.5, 26.4, -3.7), (3, 1, 1), FACE, detail=mouth_detail)
    head.cube("beard", (-2, 25.2, -3.8), (4, 1, 1), HAIR, detail=beard_detail)
    for s, side in ((1, "r"), (-1, "l")):
        head.cube(f"beard_{side}", (side_x(2.4, 1, s), 25.4, -3.5), (1, 2, 3), HAIR, mirror=s == -1,
                  detail=beard_detail)
        head.cube(f"ear_{side}", (side_x(2.8, 1, s), 28, -1), (1, 2, 1), FACE, mirror=s == -1)
    head.cube("cap_brim", (-3.5, 31.3, -3.8), (7, 2, 7), CAP, inflate=0.25, detail=cap_detail)
    head.cube("cap", (-3, 32.8, -3.3), (6, 2, 6), CAP, rotation=(0, 0, 7), origin=(0, 33, 0))
    head.cube("cap_top", (-2.5, 34.3, -2.8), (5, 1, 5), CAP, rotation=(0, 0, 12), origin=(0, 33, 0))
    seg(head, "cap_tail", (3.3, 32.2, 1.5), (4.5, 25.5, 2.6), 2, 1, CAP, extra=0.5)

    # the arms: puffed shoulders, sleeves widening to a gilt cuff over the mail, bare hands closed in fists
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return side_x(a, w, s)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (5.5 * s, 24, 0))
        arm.cube(f"{side}_puff", (x(3.8, 4), 21.5, -2), (4, 3, 4), SLEEVE, inflate=0.1, mirror=mirror,
                 detail=ao(bottom=1))
        arm.cube(f"{side}_arm", (x(4.2, 3), 18, -1.5), (3, 5, 3), SLEEVE, mirror=mirror)
        fore = arm.bone(f"{side}_forearm", (5.7 * s, 18.5, 0))
        fore.cube(f"{side}_sleeve", (x(4.2, 3), 14.5, -1.5), (3, 4, 3), SLEEVE, mirror=mirror)
        fore.cube(f"{side}_cuff", (x(3.7, 4), 13.3, -2), (4, 2, 4), SLEEVE, mirror=mirror, detail=cuff_detail)
        fore.cube(f"{side}_mail", (x(4.4, 3), 12.6, -1.3), (3, 1, 3), MAIL, inflate=-0.1, detail=mail_detail)
        fore.cube(f"{side}_hand", (x(4.7, 2), 10.4, -1.5), (2, 3, 3), FACE, mirror=mirror, detail=hand_detail)
        fore.cube(f"{side}_thumb", (x(4.4, 1), 11, -2.2), (1, 2, 1), FACE, mirror=mirror)

    animations(m)
    return m


# ----------------------------------------------------------------------------------------------- animations

ZERO = (0, 0, 0)
# the right arm's pose as (upper arm, forearm): folded across his chest, the right forearm over the left; hanging
CROSSED = ((40, 20, -10), (62, 76, 0))
CROSSED_LEFT = ((40, -20, 10), (52, -84, 0))
HANG = (ZERO, (8, 0, 0))


def mirror(v):
    """The same pose for the left side: turns about Y and Z flip."""
    return (v[0], -v[1], -v[2])


def arm(a, side, keys):
    """keys: (time, (upper arm, forearm)) for the right arm; mirrored for the left, except the folded pose, which
    is each arm's own (one forearm lies over the other)."""
    a.rot(f"{side}_arm", [(t, pose[0] if side == "right" or pose is CROSSED_LEFT else mirror(pose[0]))
                          for t, pose in keys])
    a.rot(f"{side}_forearm", [(t, pose[1] if side == "right" or pose is CROSSED_LEFT else mirror(pose[1]))
                              for t, pose in keys])


def folded(a, side, keys):
    """Like arm(), with CROSSED standing for whichever arm this is."""
    arm(a, side, [(t, (CROSSED_LEFT if side == "left" and pose is CROSSED else pose)) for t, pose in keys])


def animations(m):
    # proud and still, arms folded, looking down on you
    idle = m.animation("idle", 4.0, loop=True)
    for side in ("right", "left"):
        folded(idle, side, [(0, CROSSED), (4.0, CROSSED)])
    idle.rot("head", [(0, (10, 0, 0)), (2.0, (12, 10, 0)), (4.0, (10, 0, 0))])
    idle.pos("torso", [(0, ZERO), (2.0, (0, 0.4, 0)), (4.0, ZERO)])

    # Disdain: he raises his fist, it flashes white, and he strikes the tomb's rim: fire runs along the ground at you
    disdain = m.animation("disdain", 1.6)
    folded(disdain, "right", [(0, CROSSED), (0.5, ((170, 0, 10), (25, 0, 0))), (0.95, ((175, 0, 10), (30, 0, 0))),
                              (1.1, ((40, 0, 0), (5, 0, 0))), (1.6, CROSSED)])
    folded(disdain, "left", [(0, CROSSED), (0.4, ((20, 0, 20), (40, 0, 0))), (1.6, CROSSED)])
    disdain.rot("torso", [(0, ZERO), (0.5, (10, 0, 0)), (1.1, (-25, 0, 0)), (1.6, ZERO)])
    disdain.event(0.5, "flash").event(1.1, "hit")

    # Exile: arms flung wide, the air round him blasts outward
    exile = m.animation("exile", 2.0)
    for side in ("right", "left"):
        folded(exile, side, [(0, CROSSED), (0.6, ((20, 0, 100), (20, 0, 0))), (1.0, ((10, 0, 110), (5, 0, 0))),
                             (1.6, ((10, 0, 110), (5, 0, 0))), (2.0, CROSSED)])
    exile.rot("head", [(0, ZERO), (0.6, (30, 0, 0)), (1.6, (30, 0, 0)), (2.0, ZERO)])
    exile.pos("torso", [(0, ZERO), (0.6, (0, 2, 0)), (1.6, (0, 2, 0)), (2.0, ZERO)])
    exile.event(1.0, "blast")

    # the command: he points, and the tombs round him answer
    command = m.animation("command", 1.6)
    folded(command, "right", [(0, CROSSED), (0.5, ((90, -20, 0), (0, 0, 0))), (1.2, ((90, 20, 0), (0, 0, 0))),
                              (1.6, CROSSED)])
    command.rot("torso", [(0, ZERO), (0.5, (0, -20, 0)), (1.2, (0, 20, 0)), (1.6, ZERO)])
    command.event(0.6, "fire")

    # blocked: he sinks back into his tomb, his cap knocked askew, his arms thrown up
    stagger = m.animation("stagger", 2.4)
    stagger.pos("body", [(0, ZERO), (0.4, (0, -14, 0)), (2.0, (0, -14, 0)), (2.4, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.4, (-20, 0, 20)), (2.0, (-20, 0, 20)), (2.4, ZERO)])
    for side in ("right", "left"):
        folded(stagger, side, [(0, CROSSED), (0.4, ((160, 0, 30), (35, 0, 0))), (2.0, ((160, 0, 30), (35, 0, 0))),
                               (2.4, CROSSED)])
    stagger.event(0.4, "sink")

    # his fury: he rises higher out of the fire and bellows at the city that exiled him, fists raised
    phase = m.animation("phase", 2.6)
    phase.pos("body", [(0, ZERO), (0.8, (0, 6, 0)), (2.2, (0, 6, 0)), (2.6, ZERO)])
    for side in ("right", "left"):
        folded(phase, side, [(0, CROSSED), (0.8, ((160, 0, -25), (30, 0, 0))), (2.2, ((160, 0, -25), (30, 0, 0))),
                             (2.6, CROSSED)])
    phase.rot("head", [(0, ZERO), (0.8, (40, 0, 0)), (2.2, (40, 0, 0)), (2.6, ZERO)])
    phase.event(1.0, "roar")

    # his entrance: he rises out of his tomb and asks who your ancestors were
    intro = m.animation("intro", 5.0)
    intro.pos("body", [(0, (0, -30, 0)), (1.0, (0, -30, 0)), (2.6, ZERO), (5.0, ZERO)])
    for side in ("right", "left"):
        folded(intro, side, [(0, HANG), (2.6, HANG), (3.4, CROSSED), (5.0, CROSSED)])
    intro.rot("head", [(0, (-20, 0, 0)), (2.6, (15, 0, 0)), (5.0, (10, 0, 0))])
    intro.event(1.2, "rise").event(3.4, "speak")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])

    # beaten, he sinks back into the fire, still proud
    death = m.animation("death", 3.0)
    death.pos("body", [(0, ZERO), (1.0, (0, -4, 0)), (3.0, (0, -32, 0))])
    death.rot("head", [(0, ZERO), (1.0, (20, 0, 0)), (3.0, (20, 0, 0))])
    death.event(1.4, "sink")


if __name__ == "__main__":
    build().write()
