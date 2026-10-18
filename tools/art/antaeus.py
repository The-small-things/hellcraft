#!/usr/bin/env python3
"""Antaeus (Inferno XXXI), the giant who was never chained because he took no part in the war on the gods: he stands
on the ice of Cocytus at the foot of the Well, as tall as the cliff, a lion's skin round his loins, and at Virgil's
asking takes the travellers in his hand and sets them down on the ice. "He set us down upon the bottom, and did not
linger thus bent, but like a mast upon a ship uprose."

Run from the repo root:  python3 tools/art/antaeus.py && python3 tools/gen_bosses.py

He is drawn eight times his Blockbench size (about 28 blocks tall). His right hand rests palm-up on the Well's rim in
his rest pose; "lower" carries whoever is in it down to the ice ("take" .. "set"), "low" holds it there, and "raise"
carries it back up ("take" .. "set"). The hand follows a path of points (LOWER_PATH, RAISE_PATH) and the server moves
whoever rides along the same points (LOWER_PATH and RAISE_PATH in AntaeusEntity), so keep the two in step.

The hand itself (the right_hand bone, at the hand's middle) is kept level, palm up, all the way: its keys turn it
against the arm and lift or lower it a little, so the top of the palm is always where AntaeusEntity puts the riders'
feet (PALM: the palm's height above the hand's middle, by time).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SKIN = material("skin", "skin", base="7a5a42", dark="4a3426")
LION = material("lion", "hair", light="b08a40", dark="5a4018")
MANE = material("mane", "hair", light="8a6428", dark="3a2410")
HIDE = material("hide", "flat", base="6a4a24")
HAIR = material("hair", "hair", light="5e4632", dark="1a120c")
NAIL = material("nail", "flat", base="b8a088")
FROST = hexc("c8e8ff")
RIME = hexc("e8f6ff")
EYE = hexc("ffd890")
SOCKET = hexc("24160e")
LIP = hexc("4a2a20")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- painting helpers

def tint(f, x, y, k, toward=None, t=0.0):
    """Darkens (k < 1) or lightens one pixel of a face, keeping it transparent if it is; optionally mixes toward a colour."""
    if 0 <= x < f.w and 0 <= y < f.h:
        c = f.canvas.get(f.x + x, f.y + y)
        if c[3]:
            col = shade(tuple(c[:3]), k)
            if toward is not None:
                col = mix(col, toward, t)
            f.px(x, y, col)


def combo(*fns):
    def paint(faces):
        for fn in fns:
            fn(faces)
    return paint


def occlude(top=0, bottom=0, k=0.72, sides=SIDES):
    """Shadow where a part tucks under another (top rows) or meets one below (bottom rows), fading over the rows."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for i in range(top):
                for x in range(f.w):
                    tint(f, x, i, k + (1 - k) * i / max(1, top))
            for i in range(bottom):
                for x in range(f.w):
                    tint(f, x, f.h - 1 - i, k + (1 - k) * i / max(1, bottom))
    return paint


def rounded(k=0.82, sides=SIDES):
    """Rounds a limb: its side faces darker toward their outer columns, so a box reads as a cylinder of flesh."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for y in range(f.h):
                for x in range(f.w):
                    edge = abs(x - (f.w - 1) / 2) / max(1, (f.w - 1) / 2)
                    if edge > 0.6:
                        tint(f, x, y, 1 - (1 - k) * (edge - 0.6) / 0.4)
    return paint


def frosted(height):
    """Rime from standing in the ice, up to `height` pixels above the face's bottom, thinning as it climbs."""
    def paint(faces):
        for name in SIDES:
            f = faces[name]
            for y in range(max(0, f.h - height), f.h):
                up = f.h - 1 - y
                for x in range(f.w):
                    if up < 2 or f.rng.random() < 0.75 - 0.6 * up / max(1, height):
                        tint(f, x, y, 1.0, RIME if f.rng.random() < 0.3 else FROST, 0.75 - 0.3 * up / max(1, height))
        t = faces["top"]
        if height >= 99:
            for y in range(t.h):
                for x in range(t.w):
                    if t.rng.random() < 0.6:
                        tint(t, x, y, 1.0, FROST, 0.7)
    return paint


def veins(n=2, sides=("front", "left", "right")):
    """A few raised veins wandering down a limb."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for _ in range(n):
                x = f.rng.randrange(1, max(2, f.w - 1))
                for y in range(f.rng.randrange(0, 2), f.h - 1):
                    tint(f, x, y, 0.84)
                    if f.rng.random() < 0.3:
                        x = max(1, min(f.w - 2, x + f.rng.choice((-1, 1))))
    return paint


def scars(n=3, sides=("front", "back")):
    """Old pale scars from fights with lions."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for _ in range(n):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                d = f.rng.choice((-1, 1))
                for i in range(f.rng.randint(2, 4)):
                    tint(f, x + i, y + d * i, 1.25)
    return paint


# ----------------------------------------------------------------------------------------------- details

def face(faces):
    """His face: a heavy brow over deep sockets with eyes like lamps, broad cheeks, a mouth half lost in the beard."""
    f = faces["front"]
    w = f.w
    dark = SKIN.c["dark"]
    for x in range(w):
        tint(f, x, 2, 0.6)  # the shadow under the brow
    for cx in (1, w - 3):
        for dx in range(3):
            f.px(cx + dx, 3, SOCKET)
            tint(f, cx + dx, 4, 0.6)
        f.glow(cx + 1, 3, EYE)
    for x in (2, w - 3):
        tint(f, x, 5, 1.12)  # cheekbones catch the light
    for y in range(3, 7):
        tint(f, 0, y, 0.8)
        tint(f, w - 1, y, 0.8)
    for x in range(2, w - 2):
        f.px(x, 7, LIP)
    for x in range(3, w - 3):
        f.px(x, 8, shade(dark, 0.7))
    for y in range(4, 7):
        tint(f, w // 2 - 1, y, 0.85)  # the shadow beside his nose
    for name in ("left", "right"):
        s = faces[name]
        for y in range(2, 5):
            for x in range(s.w):
                if s.rng.random() < 0.3:
                    tint(s, x, y, 0.85)


def brow(faces):
    f = faces["front"]
    for x in range(f.w):
        tint(f, x, 0, 0.9)
    for x in (f.w // 2 - 1, f.w // 2):
        tint(f, x, 0, 0.8)  # a frown between the brows
    occlude(bottom=1, k=0.75)(faces)


def nose(faces):
    f = faces["front"]
    for y in range(f.h):
        tint(f, 0, y, 0.85)
        tint(f, f.w - 1, y, 0.85)
    for x in range(f.w):
        tint(f, x, 0, 1.1)
    b = faces["bottom"]
    for x in (0, b.w - 1):
        b.px(x, 0, SOCKET)  # nostrils


def ear(faces):
    for name in ("left", "right"):
        f = faces[name]
        for y in range(1, f.h - 1):
            tint(f, f.w // 2, y, 0.62)


def lion_face(faces):
    """The lion's head on his hips: dead amber eyes, a broad nose and the dark of its mouth."""
    f = faces["front"]
    w = f.w
    for cx in (1, w - 2):
        f.px(cx, 1, hexc("1a1008"))
        f.px(cx, 2, hexc("8a6a20"))
    for y in range(1, 4):
        f.px(w // 2, y, shade(LION.c["dark"], 0.8))
    f.px(w // 2 - 1, 3, hexc("2a1a10"))
    f.px(w // 2, 3, hexc("2a1a10"))
    for x in range(1, w - 1):
        f.px(x, f.h - 1, hexc("200c08"))
    f.px(1, f.h - 1, hexc("e8dcc0"))
    f.px(w - 2, f.h - 1, hexc("e8dcc0"))


def ragged(faces):
    """A torn hem: the lowest rows bitten away, unevenly."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            cut = f.rng.choice((0, 0, 1, 1, 2, 3))
            for y in range(f.h - cut, f.h):
                f.px(x, y, (0, 0, 0), alpha=0)
            if cut < f.h:
                tint(f, x, f.h - cut - 1, 0.75)


def belt(faces):
    """A thong of twisted hide."""
    for name in SIDES:
        f = faces[name]
        for x in range(f.w):
            for y in range(f.h):
                if (x + y) % 3 == 0:
                    tint(f, x, y, 0.7)


def torso_front(faces):
    """The ribcage: shadow under the pectorals, the line of the sternum, ribs along the flanks."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        tint(f, mid, y, 0.82)
    for name in ("left", "right"):
        s = faces[name]
        for y in range(2, s.h - 1, 2):
            for x in range(1, s.w - 1):
                if (x + y) % 4 < 2:
                    tint(s, x, y, 0.82)
    for x in range(f.w):
        tint(f, x, f.h - 1, 0.78)
    b = faces["back"]
    for y in range(b.h):
        tint(b, b.w // 2, y, 0.78)  # the spine's groove


def pec(faces):
    f = faces["front"]
    for x in range(f.w):
        tint(f, x, f.h - 1, 0.65)
        tint(f, x, 0, 1.12)
    for name in ("left", "right", "bottom"):
        for y in range(faces[name].h):
            for x in range(faces[name].w):
                tint(faces[name], x, y, 0.75)
    f.px(f.w // 2, f.h - 2, shade(SKIN.c["dark"], 0.7))  # a nipple


def abs_(faces):
    """The belly's muscles in three pairs, and the navel."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        tint(f, mid, y, 0.75)
        tint(f, mid - 1, y, 0.9)
    for y in (1, 3):
        for x in range(1, f.w - 1):
            tint(f, x, y, 0.8)
    f.px(mid, f.h - 1, SOCKET)
    for name in ("left", "right"):
        s = faces[name]
        for y in range(s.h):
            for x in range(s.w):
                if (x - y) % 3 == 0:
                    tint(s, x, y, 0.85)  # the obliques


def back_detail(faces):
    """Shoulder blades and the spine's groove."""
    f = faces["back"]
    mid = f.w // 2
    for y in range(f.h):
        tint(f, mid, y, 0.72)
    for cx in (3, f.w - 4):
        for dy in range(4):
            tint(f, cx + (dy // 2) * (1 if cx < mid else -1), 1 + dy, 0.78)
    for x in range(f.w):
        tint(f, x, f.h - 1, 0.78)


def kneecap(faces):
    f = faces["front"]
    for x in range(f.w):
        tint(f, x, f.h - 1, 0.7)
        tint(f, x, 0, 1.12)


def knuckles(faces):
    f = faces["front"]
    for x in range(0, f.w, 2):
        tint(f, x, 0, 1.15)
    for x in range(1, f.w, 2):
        tint(f, x, 1, 0.8)


def palm_detail(faces):
    """Creases across the palm (its top, as it lies level) and its edges darker where the fingers part."""
    f = faces["front"]
    for x in range(1, f.w - 1):
        tint(f, x, 2, 0.8)
    for y in range(2, f.h - 1):
        tint(f, 2 + (y // 2), y, 0.82)
    for x in range(0, f.w, 2):
        tint(f, x, f.h - 1, 0.85)


def nail(faces):
    f = faces["front"]
    for x in range(f.w):
        f.px(x, f.h - 1, NAIL.c["base"])


def beard_detail(faces):
    """Strands running down the beard."""
    for name in SIDES:
        f = faces[name]
        for x in range(0, f.w, 2):
            for y in range(f.h):
                tint(f, x, y, 0.82)


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("antaeus", texture_width=256)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mir = s == -1
        leg = m.bone(f"{side}_leg", (5 * s, 24, 0))
        # a giant's legs: heavy thighs, a kneecap, a shin with the calf behind it, ankles and broad bare feet in the ice
        leg.cube(f"{side}_thigh", (x(1, 9), 14, -4.5), (9, 10, 9), SKIN, mirror=mir,
                 detail=combo(rounded(0.85), veins(1, ("front",)), occlude(bottom=2, k=0.8)))
        leg.cube(f"{side}_quad", (x(2, 7), 15, -5.3), (7, 7, 1), SKIN, mirror=mir, detail=occlude(bottom=2, k=0.75))
        leg.cube(f"{side}_knee", (x(1.5, 8), 10, -4), (8, 4, 8), SKIN, mirror=mir, detail=rounded(0.82))
        leg.cube(f"{side}_kneecap", (x(3, 4), 10.5, -4.8), (4, 3, 1), SKIN, mirror=mir, detail=kneecap)
        leg.cube(f"{side}_shin", (x(2, 7), 3, -3.5), (7, 7, 6), SKIN, mirror=mir, detail=combo(rounded(0.82), frosted(5)))
        leg.cube(f"{side}_calf", (x(2.5, 6), 5, 2), (6, 6, 2), SKIN, mirror=mir, detail=combo(occlude(top=1, k=0.8), frosted(3)))
        leg.cube(f"{side}_ankle", (x(2.5, 6), 1, -2.5), (6, 3, 5), SKIN, mirror=mir, detail=frosted(99))
        leg.cube(f"{side}_heel", (x(1.5, 8), 0, -3), (8, 2, 6), SKIN, mirror=mir, detail=frosted(99))
        leg.cube(f"{side}_toes", (x(1.5, 8), 0, -6.5), (8, 2, 4), SKIN, mirror=mir, detail=combo(knuckles, frosted(99)))
    body = m.bone("body", (0, 24, 0))
    # the trunk: hips, a narrower waist, a deep ribcage under broad pectorals, a back of slabs, the trapezius rising
    # to a thick neck
    body.cube("hips", (-9, 20, -4.5), (18, 7, 9), SKIN)
    body.cube("waist", (-8, 26, -4.5), (16, 7, 9), SKIN, detail=occlude(top=2, k=0.7))
    body.cube("belly", (-4, 26.5, -5.4), (8, 6, 1), SKIN, detail=abs_)
    body.cube("ribcage", (-10, 32, -5), (20, 9, 10), SKIN, detail=combo(torso_front, scars(2)))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        body.cube(f"{side}_pec", (x(0.5, 8), 35, -6), (8, 5, 2), SKIN, mirror=s == -1, detail=pec)
        body.cube(f"{side}_lat", (x(6, 4), 31, -4), (4, 8, 8), SKIN, mirror=s == -1, detail=occlude(bottom=3, k=0.78))
        body.cube(f"{side}_trapezius", (x(1, 9), 39.5, -3.5), (9, 3, 7), SKIN, mirror=s == -1,
                  rotation=(0, 0, -16 * s), origin=(s, 41, 0), detail=occlude(bottom=1, k=0.8))
    body.cube("collar", (-8, 40, -4.5), (16, 2, 8), SKIN, detail=occlude(bottom=1, k=0.75))
    body.cube("back", (-9, 30, 4.5), (18, 11, 2), SKIN, detail=back_detail)
    body.cube("neck", (-3.5, 41, -3), (7, 5, 7), SKIN, detail=combo(occlude(top=2, k=0.65), rounded(0.8)))
    # the lion's skin: a wrap round his hips tied with a thong of hide, the beast's head over his loins with its
    # forepaws knotted beside it, a ragged flap before and behind, and the tail
    body.cube("lion_skin", (-9.5, 18.5, -5.5), (19, 7, 11), LION, inflate=0.25, detail=occlude(top=1, k=0.75))
    body.cube("thong", (-10, 24.2, -6), (20, 1, 12), HIDE, inflate=0.1, detail=belt)
    body.cube("lion_mane", (-4.5, 16.5, -6.7), (9, 8, 1), MANE, detail=ragged)
    body.cube("lion_head", (-3, 18, -7.8), (6, 5, 2), LION, detail=lion_face)
    body.cube("lion_muzzle", (-1.5, 18, -8.6), (3, 2, 1), LION)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        body.cube(f"lion_paw_{side}", (x(4.5, 2), 14, -6.6), (2, 7, 1), LION, mirror=s == -1, rotation=(0, 0, 8 * s),
                  origin=(5.5 * s, 21, -6), detail=ragged)
        body.cube(f"lion_flank_{side}", (x(8.5, 2), 13, -4), (2, 7, 8), LION, mirror=s == -1, detail=ragged)
    body.cube("lion_flap", (-4, 10, -6.2), (8, 8, 1), LION, detail=ragged)
    body.cube("lion_rump", (-6, 10, 5.3), (12, 9, 1), LION, detail=ragged)
    body.cube("lion_tail", (-0.5, 9, 6.2), (1, 9, 1), LION)
    body.cube("lion_tuft", (-1, 7, 5.8), (2, 3, 2), MANE)

    head = body.bone("head", (0, 44, 0))
    # the skull, a heavy brow over deep sockets, a broad nose, cheekbones, ears, and a wild mane and beard
    head.cube("head", (-4.5, 45, -5), (9, 10, 10), SKIN, detail=face)
    head.cube("brow", (-4.5, 51.5, -5.8), (9, 1, 1), SKIN, detail=brow)
    head.cube("nose", (-1, 48, -6.2), (2, 3, 1), SKIN, detail=nose)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        head.cube(f"{side}_cheek", (x(2.2, 2), 48.5, -5.5), (2, 1, 1), SKIN, mirror=s == -1)
        head.cube(f"{side}_ear", (x(4.4, 1), 48, -0.5), (1, 3, 2), SKIN, mirror=s == -1, detail=ear)
        head.cube(f"{side}_locks", (x(3.8, 2), 43, -1), (2, 10, 6), HAIR, mirror=s == -1, inflate=0.2, detail=ragged)
    head.cube("moustache", (-3, 46.8, -6.3), (6, 1, 1), HAIR, inflate=0.2)
    head.cube("beard", (-4.5, 42.5, -6.2), (9, 5, 4), HAIR, inflate=0.2, detail=beard_detail)
    head.cube("beard_mid", (-3.5, 39.5, -6.6), (7, 4, 3), HAIR, detail=combo(beard_detail, ragged))
    head.cube("beard_tip", (-2, 37, -6.8), (4, 3, 2), HAIR, detail=ragged)
    head.cube("hair", (-5, 53.5, -5.3), (10, 3, 10), HAIR, inflate=0.3)
    head.cube("hair_crown", (-3.5, 56, -3.5), (7, 1, 7), HAIR)
    head.cube("forelock", (-4.5, 52.5, -5.9), (9, 2, 1), HAIR, detail=ragged)
    head.cube("hair_back", (-5, 42, 2.5), (10, 12, 3), HAIR, inflate=0.2, detail=ragged)
    head.cube("hair_fall", (-4, 36, 4), (8, 7, 2), HAIR, detail=ragged)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mir = s == -1
        arm = body.bone(f"{side}_arm", (12 * s, 42, 0), (-AT_RIM, 0, 0) if s == 1 else (0, 0, 0))
        # a great round shoulder, the upper arm with its biceps, the elbow, the forearm tapering to the wrist
        arm.cube(f"{side}_shoulder", (x(9.5, 8), 35, -4), (8, 8, 8), SKIN, mirror=mir, detail=rounded(0.85))
        arm.cube(f"{side}_upper_arm", (x(10.5, 6), 27, -3.5), (6, 9, 7), SKIN, mirror=mir,
                 detail=combo(rounded(0.82), occlude(top=1, k=0.8)))
        arm.cube(f"{side}_biceps", (x(11.5, 4), 29, -4.3), (4, 5, 1), SKIN, mirror=mir, detail=occlude(bottom=1, k=0.8))
        if s == 1:
            # his carrying arm is straight from shoulder to hand (the reach below counts on it)
            arm.cube("right_elbow", (11, 25, -3), (5, 4, 6), SKIN, detail=rounded(0.8))
            arm.cube("right_forearm", (10.5, 20.5, -3), (6, 5, 6), SKIN, detail=combo(rounded(0.82), veins(2)))
            arm.cube("right_wrist", (11, 17, -2.5), (5, 4, 5), SKIN, detail=rounded(0.8))
            hand = arm.bone("right_hand", HAND, (AT_LEVEL, 0, 0))
            build_hand(hand)
        else:
            fore = arm.bone("left_forearm", (-13.5, 27, 0), (14, 0, 0))
            fore.cube("left_elbow", (-16, 25, -3), (5, 5, 6), SKIN, mirror=True, detail=rounded(0.8))
            fore.cube("left_forearm", (-16.5, 20.5, -3), (6, 5, 6), SKIN, mirror=True, detail=combo(rounded(0.82), veins(2)))
            fore.cube("left_wrist", (-16, 17, -2.5), (5, 4, 5), SKIN, mirror=True, detail=rounded(0.8))
            hand = fore.bone("left_hand", (-13.5, 17.5, 0), (6, 0, 0))
            # a heavy hand hanging loose: the palm toward his thigh, fingers half curled, the thumb in front
            hand.cube("left_palm", (-15.5, 11.5, -3.5), (4, 6, 7), SKIN, mirror=True, detail=rounded(0.85, ("front", "back")))
            for i, z in enumerate((-3.5, -1.5, 0.5, 2.5)):
                hand.cube(f"left_finger{i}", (-14.8, 7.5 + (1 if i in (0, 3) else 0), z), (3, 4 - (1 if i in (0, 3) else 0), 2), SKIN,
                          mirror=True, rotation=(0, 0, 12), origin=(-13.5, 11.5, 0), detail=nail)
            hand.cube("left_thumb", (-14.5, 11, -5), (2, 4, 2), SKIN, mirror=True, rotation=(15, 0, 0), origin=(-13.5, 15, -3.5),
                      detail=nail)
    animations(m)
    return m


def build_hand(hand):
    """His right hand, drawn level and palm up (it is turned so whatever his arm does): the top of the palm sits PALM_REST
    above the hand's middle, which is where the riders stand. Fingers point forward and curl a little at the tips;
    the thumb lies along the outside."""
    hx, hy, hz = HAND
    top = hz - PALM_REST  # the palm's top, as the hand hangs in its own frame (local -Z is up once level)
    hand.cube("right_palm", (hx - 4.5, hy - 3.5, top), (9, 7, 3), SKIN, detail=palm_detail)
    hand.cube("right_palm_heel", (hx - 3.5, hy + 3.5, top + 1), (7, 2, 4), SKIN, detail=rounded(0.85))
    hand.cube("right_back", (hx - 4, hy - 3, top + 3), (8, 6, 1), SKIN, detail=veins(2, ("back",)))
    for i, (fx, length) in enumerate(((-4.5, 3), (-2.25, 4), (0, 4), (2.25, 3))):
        tip_len = 2 if length == 4 else 1
        hand.cube(f"right_finger{i}", (hx + fx, hy - 3.5 - length, top + 0.5), (2, length, 2), SKIN, inflate=0.1)
        hand.cube(f"right_fingertip{i}", (hx + fx, hy - 3.5 - length - tip_len, top + 0.5), (2, tip_len, 2), SKIN,
                  rotation=(30, 0, 0), origin=(hx + fx + 1, hy - 3.5 - length, top + 1.5), detail=nail)
    hand.cube("right_thumb", (hx + 4, hy - 1, top + 0.5), (2, 4, 2), SKIN, rotation=(0, 0, 15),
              origin=(hx + 4, hy + 3, top + 1.5), detail=nail)


ZERO = (0, 0, 0)
# His right arm rests on the rim of the Well (the mesh's own pose, AT_RIM); the animations move it from there. The
# reach below works in angles with "forward" negative; Blockbench's +X swings a hanging arm forward (and tips a body
# back), so every X angle is negated on its way into the model (keys and the mesh's pose alike).
AT_RIM = -110
# Where his right hand (its centre) goes, in pixels from his hips: (seconds, forward, up). The hand lifts you off the
# rim, then goes straight down the cleft his arm has worn in the cliff (Antaeus builds it) to the ice. AntaeusEntity moves whoever rides in it along the
# same points (LOWER_PATH and RAISE_PATH there): change both together.
LOWER_PATH = [(0.0, 24.43, 26.89), (0.5, 24.43, 26.89), (1.25, 22, 32), (3.5, 24.43, -20), (4.0, 24.43, -20)]
RAISE_PATH = [(0.0, 24.43, -20), (0.25, 24.43, -20), (2.4, 22, 32), (2.75, 24.43, 26.89), (3.0, 24.43, 26.89)]
# How far the top of the palm is above the hand's centre at those points (the fourth number in AntaeusEntity's paths)
LOWER_PALM = [(0.0, 4.91), (0.5, 4.91), (1.25, 4.87), (3.5, 3.16), (4.0, 3.16)]
RAISE_PALM = [(0.0, 3.16), (0.25, 3.16), (2.4, 4.87), (2.75, 4.91), (3.0, 4.91)]
SHOULDER = 18  # hips to shoulder
ARM = 26  # shoulder to the middle of the hand
HAND = (13.5, 16, 0)  # the middle of his right hand (the right_hand bone's pivot), as the arm hangs
PALM_REST = 4.91  # the palm's top above the hand's middle as drawn (its height in the rest pose)
AT_LEVEL = 90 + AT_RIM  # the hand's own turn at rest: the arm's 110 degrees less a level hand's 90


def reach(fwd, up, near):
    """The bend of his body and the swing of his arm (degrees; forward bend positive, the arm's world angle negative
    when raised forward) that put his hand at (fwd, up): the solution nearest the body bend `near`."""
    best = None
    for i in range(-900, 901):
        b = i / 10
        sf = SHOULDER * math.sin(math.radians(b))
        su = SHOULDER * math.cos(math.radians(b))
        dist = math.hypot(fwd - sf, up - su)
        err = abs(dist - ARM)
        if err < 0.25:
            score = abs(b - near) + err * 40
            if best is None or score < best[0]:
                theta = math.degrees(math.atan2(-(fwd - sf), -(up - su)))
                best = (score, b, theta)
    assert best, f"out of reach: {fwd}, {up}"
    return best[1], best[2]


def palm_at(palm, t):
    """The palm's height above the hand's middle at time t, interpolated as AntaeusEntity does."""
    for (t0, p0), (t1, p1) in zip(palm, palm[1:]):
        if t0 <= t <= t1:
            return p0 if t1 == t0 else p0 + (p1 - p0) * (t - t0) / (t1 - t0)
    return palm[-1][1]


def follow(anim, path, start_bend, palm):
    """Keys the body and right arm every quarter second along the hand's path (linear between keys), and the hand:
    turned to stay level, palm up, and moved so the top of the palm is where AntaeusEntity has the riders stand."""
    keys_body, keys_arm = [], []
    bend = start_bend
    t = 0.0
    end = path[-1][0]
    while t <= end + 1e-6:
        for (t0, f0, u0), (t1, f1, u1) in zip(path, path[1:]):
            if t0 <= t <= t1:
                k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                fwd, up = f0 + (f1 - f0) * k, u0 + (u1 - u0) * k
                break
        bend, theta = reach(fwd, up, bend)
        keys_body.append((round(t, 3), (round(bend, 2), 0, 0)))
        keys_arm.append((round(t, 3), (round(theta - bend - AT_RIM, 2), 0, 0)))
        t += 0.25
    anim.rot("body", [(t, (-b, y, z)) for t, (b, y, z) in keys_body], interp="linear")
    anim.rot("right_arm", [(t, (-a, y, z)) for t, (a, y, z) in keys_arm], interp="linear")
    hand_rot, hand_pos = [], []
    for (t, (b, _, _)), (_, (a, _, _)) in zip(keys_body, keys_arm):
        world = -b - a - AT_RIM  # the arm's angle in the world (Blockbench's X)
        lift = palm_at(palm, t) - PALM_REST  # up (in the world) from where the palm sits as drawn
        w = math.radians(world)
        hand_rot.append((t, (round(b + a, 2), 0, 0)))
        hand_pos.append((t, (0, round(lift * math.cos(w), 3), round(-lift * math.sin(w), 3))))
    anim.rot("right_hand", hand_rot, interp="linear")
    anim.pos("right_hand", hand_pos, interp="linear")


def animations(m):
    # waiting: breathing slowly, his right palm up on the rim of the Well
    idle = m.animation("idle", 5.0, loop=True)
    idle.rot("left_arm", [(0, (0, 0, -8)), (2.5, (0, 0, -10)), (5.0, (0, 0, -8))])
    idle.pos("body", [(0, ZERO), (2.5, (0, 0.6, 0)), (5.0, ZERO)])
    idle.rot("head", [(0, (10, 0, 0)), (2.5, (12, 6, 0)), (5.0, (10, 0, 0))])
    idle.rot("left_hand", [(0, ZERO), (2.5, (4, 0, -4)), (5.0, ZERO)])

    # carrying you down: the hand lifts you over the brink, he bends, and sets you on the ice
    lower = m.animation("lower", 4.0)
    follow(lower, LOWER_PATH, 0, LOWER_PALM)
    lower.rot("head", [(0, ZERO), (1.25, (5, 0, 0)), (3.5, (-25, 0, 0)), (4.0, (-25, 0, 0))])
    lower.event(0.5, "take").event(3.5, "set")

    # holding his hand down at the ice, for whoever wants to go up
    low = m.animation("low", 2.0, loop=True)
    follow(low, [(0.0, 24.43, -20), (2.0, 24.43, -20)], 60, [(0.0, 3.16), (2.0, 3.16)])
    low.rot("head", [(0, (-25, 0, 0)), (2.0, (-25, 0, 0))])

    # and up again, like a mast on a ship
    raise_ = m.animation("raise", 3.0)
    follow(raise_, RAISE_PATH, 60, RAISE_PALM)
    raise_.rot("head", [(0, (-25, 0, 0)), (2.4, (5, 0, 0)), (2.75, ZERO), (3.0, ZERO)])
    raise_.event(0.25, "take").event(2.75, "set")


if __name__ == "__main__":
    build().write()
