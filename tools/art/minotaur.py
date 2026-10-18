#!/usr/bin/env python3
"""The Minotaur, the Infamy of Crete (Inferno XII), on the shattered slope down into the seventh circle: a giant with a
bull's head and a man's body, on hairy legs that end in cloven hooves, with a ring through his nose and the broken cuffs
of his chains on his wrists. "When he beheld us, he bit himself, like one whom anger inwardly consumes."

Run from the repo root:  python3 tools/art/minotaur.py && python3 tools/gen_bosses.py

He is built like a wrestler: a deep chest and heavy shoulders over a narrower waist, a bull's hump of muscle behind
his neck, arms in tapered segments (deltoid, biceps, forearm, a fist with its thumb and knuckles), thighs thicker than
his shins, and fetlocks of shaggy hair over his hooves. The bull's head has a heavy brow over its eyes, a long face
down to a broad wet muzzle, a jaw that opens when he bellows, and horns that sweep out, up and forward in segments.

The animations' events are what the server times his fight by (MinotaurFight): "flash" opens the window to block
his horns and "hit" is when they strike; "go" sends him charging; "slam" is his stomp; "spin" .. "spun" his frenzy;
"bite" and "roar" his fury at half health; "rise" and "roar" his entrance; "fall" his death.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

HIDE = material("hide", "hair", light="5a3a26", dark="2a1a10")
HEAD = material("head", "hair", light="5e3c26", dark="26180e")
CURLS = material("curls", "hair", light="3a2618", dark="140c06")
SKIN = material("skin", "skin", base="8a5a40", dark="5a3424")
MUZZLE = material("muzzle", "skin", base="5a4640", dark="2a1e1a")
HORN = material("horn", "horn", root="d8ccb0", tip="2a2420")
HOOF = material("hoof", "horn", root="3a3430", tip="141210")
CLOTH = material("cloth", "robe", base="5a2a1a", fold="3a160c")
LEATHER = material("leather", "flat", base="3a2416")
IRON = material("iron", "metal", base="4a4a4e", high="7a7a80", low="26262a")
EYE = hexc("ff3a1a")
EYE_HOT = hexc("ffb070")
NOSTRIL = hexc("0a0606")
WET = hexc("6a5a54")
MOUTH = hexc("3a0e0a")
TOOTH = hexc("d8ccb0")
VEIN = hexc("5e3426")
INNER_EAR = hexc("8a5048")


# ----------------------------------------------------------------------------------------------- shading helpers

def tone(f, x, y, k):
    """Darkens (k < 1) or lights (k > 1) one pixel of a face, as painted so far."""
    if 0 <= x < f.w and 0 <= y < f.h:
        c = f.canvas.get(f.x + x, f.y + y)
        if c[3]:
            f.px(x, y, shade(c[:3], k), c[3])


def tuck(faces, rows=2, k=0.7, lit=1.08, names=("front", "back", "left", "right")):
    """Shadow where a part tucks under (its lowest rows darkest) and light along its top: sells a rounded form."""
    for name in names:
        f = faces[name]
        for i in range(min(rows, f.h)):
            for x in range(f.w):
                tone(f, x, f.h - 1 - i, k + (1 - k) * i / rows)
        for x in range(1, f.w - 1):
            tone(f, x, 0, lit)
    tone_all(faces["bottom"], 0.8)


def tone_all(f, k):
    for y in range(f.h):
        for x in range(f.w):
            tone(f, x, y, k)


def veins(f, count, seed=0):
    """A few raised veins wandering down a face."""
    rng = f.rng
    for _ in range(count):
        x = rng.randrange(1, max(2, f.w - 1))
        for y in range(rng.randrange(0, 2), f.h - 1):
            f.px(x, y, VEIN)
            if rng.random() < 0.35:
                x = max(0, min(f.w - 1, x + rng.choice((-1, 1))))


# ----------------------------------------------------------------------------------------------- details

def upper_face(faces):
    """The bull's eyes, set in the sides of his face under the brow and burning red, and a pale blaze down the
    middle of his face."""
    f = faces["front"]
    for x in range(f.w):
        tone(f, x, 0, 0.6)
    for y in range(f.h):
        f.px(f.w // 2, y, mix(HEAD.c["light"], hexc("8a7058"), 0.5))
    for cx in (0, f.w - 2):
        f.px(cx, 1, shade(HEAD.c["dark"], 0.5))
        f.px(cx + 1, 1, shade(HEAD.c["dark"], 0.5))
        f.glow(cx, 2, EYE)
        f.glow(cx + 1, 2, EYE)
    for name, ex in (("right", faces["right"].w - 2), ("left", 0)):
        s = faces[name]
        for x in range(s.w):
            tone(s, x, 0, 0.6)
        s.px(ex, 1, shade(HEAD.c["dark"], 0.4))
        s.px(ex + 1, 1, shade(HEAD.c["dark"], 0.4))
        s.glow(ex, 2, EYE)
        s.glow(ex + 1, 2, EYE_HOT)
        s.px(ex, 3, shade(HEAD.c["dark"], 0.5))
        s.px(ex + 1, 3, shade(HEAD.c["dark"], 0.5))
    tuck(faces, 1, 0.75)


def brow(faces):
    """The heavy brow: a dark underside that throws the eyes into shadow."""
    tone_all(faces["bottom"], 0.45)
    for name in ("front", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            tone(f, x, f.h - 1, 0.6)


def nose_bridge(faces):
    f = faces["front"]
    for y in range(f.h):
        f.px(f.w // 2, y, mix(HEAD.c["light"], hexc("8a7058"), 0.4))
    tuck(faces, 1, 0.8)


def muzzle(faces):
    """A broad wet muzzle: two flared nostrils with a shine above them, and the mouth line under."""
    f = faces["front"]
    w = f.w
    for nx, out in ((1, 0), (w - 2, w - 1)):
        f.px(nx, 1, NOSTRIL)
        f.px(nx, 2, NOSTRIL)
        f.px(out, 2, shade(MUZZLE.c["dark"], 0.7))
        f.px(nx, 0, WET)
        f.px(out, 1, WET)
    for x in range(w):
        f.px(x, f.h - 1, shade(MUZZLE.c["dark"], 0.6))
    f.px(w // 2, 1, shade(MUZZLE.c["dark"], 0.7))
    f.px(w // 2, 2, shade(MUZZLE.c["dark"], 0.7))
    b = faces["bottom"]
    b.fill(lambda x, y: MOUTH)
    for x in range(0, b.w, 2):
        b.px(x, 0, TOOTH)
    for name in ("left", "right"):
        s = faces[name]
        for x in range(s.w):
            s.px(x, s.h - 1, shade(MUZZLE.c["dark"], 0.55))


def jaw(faces):
    """The lower jaw: a dark mouth on top, his flat teeth along its edge, a hairy chin."""
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    for x in range(t.w):
        t.px(x, t.h - 1, TOOTH if x % 2 else shade(TOOTH, 0.8))
    for y in range(t.h):
        t.px(0, y, TOOTH if y % 2 else shade(TOOTH, 0.8))
        t.px(t.w - 1, y, TOOTH if y % 2 else shade(TOOTH, 0.8))
    tuck(faces, 1, 0.65, names=("front", "left", "right"))


def ear(faces):
    f = faces["front"]
    for x in range(1, f.w):
        for y in range(f.h):
            f.px(x, y, INNER_EAR if (x + y) % 3 else shade(INNER_EAR, 0.8))


def ring(faces):
    """The iron ring through his nose: hollow in the middle."""
    for name in ("front", "back"):
        f = faces[name]
        f.px(f.w // 2, f.h // 2, (0, 0, 0), 0)


def pecs(faces):
    """A hairy chest: two slabs of muscle with a groove between them and a shadow under each."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid - 1, y, shade(SKIN.c["dark"], 0.85))
        f.px(mid, y, shade(SKIN.c["dark"], 0.85))
    for x in range(f.w):
        tone(f, x, f.h - 1, 0.62)
    for y in range(0, 3):
        for x in range(mid - 4 + y, mid + 4 - y):
            if (x * 7 + y * 3) % 3:
                f.px(x, y, HIDE.c["light"] if (x + y) % 2 else HIDE.c["dark"])
    f.px(2, 2, shade(SKIN.c["dark"], 0.7))
    f.px(f.w - 3, 2, shade(SKIN.c["dark"], 0.7))


def abs_(faces):
    """The belly: a column of muscles, a line of hair down the middle and the navel."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid, y, shade(HIDE.c["dark"], 1.1))
    for y in (1, 3, 5):
        for x in range(1, f.w - 1):
            if x != mid:
                tone(f, x, y, 0.8)
    for x in range(1, f.w - 1):
        tone(f, x, 0, 0.75)
    f.px(mid, f.h - 2, shade(SKIN.c["dark"], 0.5))


def torso_skin(faces):
    """Ribs and the line of the spine, shadow under the chest."""
    f = faces["front"]
    for x in range(f.w):
        tone(f, x, 0, 0.7)
    for name in ("left", "right"):
        s = faces[name]
        for y in range(1, s.h, 2):
            for x in range(1, s.w - 1):
                tone(s, x, y, 0.88)
    b = faces["back"]
    for y in range(b.h):
        tone(b, b.w // 2, y, 0.7)
    tuck(faces, 1, 0.8)


def back(faces):
    """His back: the spine's groove and the shoulder blades' shadows."""
    b = faces["back"]
    for y in range(b.h):
        tone(b, b.w // 2, y, 0.68)
        tone(b, b.w // 2 - 1, y, 0.82)
    for x in range(b.w):
        tone(b, x, b.h - 1, 0.72)
    torso_skin_front_shadow(faces)


def torso_skin_front_shadow(faces):
    for name in ("left", "right"):
        s = faces[name]
        for y in range(s.h):
            tone(s, 0, y, 0.85)
            tone(s, s.w - 1, y, 0.85)


def deltoid(faces):
    tuck(faces, 2, 0.72, 1.12)


def limb(faces):
    """An arm's segment: shadowed where it meets the next, with a vein or two."""
    tuck(faces, 1, 0.75)
    for name in ("front", "left", "right"):
        veins(faces[name], 1)


def forearm(faces):
    tuck(faces, 1, 0.78)
    for name in ("front", "back", "left", "right"):
        veins(faces[name], 2)


def fist(faces):
    """Knuckles along the front of his curled fingers, and the cracks between them."""
    for name in ("front", "bottom", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            tone(f, x, 0, 0.8)
    f = faces["front"]
    for x in range(f.w):
        tone(f, x, 1, 1.12)
    for name in ("left", "right"):
        s = faces[name]
        for x in range(1, s.w, 2):
            for y in range(s.h):
                tone(s, x, y, 0.82)


def cuff(faces):
    """The cuff's broken edge: a bright scrape and rust."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            if f.rng.random() < 0.35:
                f.px(x, f.h - 1, hexc("6a3a20"))
        f.px(f.rng.randrange(f.w), 1, hexc("a0a0a8"))


def belt(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(0, f.w, 3):
            f.px(x, 0, hexc("5a3a24"))
            f.px(x + 1, f.h - 1, hexc("1a1008"))


def ragged(faces):
    """A torn hem: the lowest rows bitten away here and there."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            cut = f.rng.choice((0, 0, 1, 1, 2))
            for y in range(f.h - cut, f.h):
                f.px(x, y, (0, 0, 0), 0)
            if cut < f.h:
                tone(f, x, f.h - cut - 1, 0.7)
    tone_all(faces["bottom"], 0.7)


def knee(faces):
    tuck(faces, 1, 0.7, 1.1)


def fetlock(faces):
    tuck(faces, 1, 0.65)


def hoof(faces):
    """A cloven hoof: the split down the front, a pale wear line along the bottom edge."""
    f = faces["front"]
    for y in range(f.h):
        f.px(f.w // 2, y, shade(HOOF.c["tip"], 0.6))
    for name in ("front", "left", "right", "back"):
        s = faces[name]
        for x in range(s.w):
            s.px(x, s.h - 1, hexc("5a524a"))


def horn(faces):
    """Ridged horn: a dark ring every other pixel along it, pale at the root."""
    for name in ("front", "back", "top", "bottom"):
        f = faces[name]
        for x in range(0, f.w, 2):
            for y in range(f.h):
                tone(f, x, y, 0.85)


def chain_link(faces):
    for name in ("front", "back"):
        f = faces[name]
        f.px(f.w // 2, f.h // 2, (0, 0, 0), 0)


# ----------------------------------------------------------------------------------------------- the model

def horn_chain(bone, side, start, segments, mat):
    """A horn swept out, up and forward, built from straight segments: each (length, thickness, yaw forward, lift up)
    leaves where the last one ended. Mirrored for the left horn."""
    s = 1 if side == "right" else -1
    px, py, pz = start
    for i, (length, thick, yaw, lift) in enumerate(segments):
        ry, rz = math.radians(yaw), math.radians(lift)
        d = (math.cos(rz) * math.cos(ry), math.sin(rz) * math.cos(ry), -math.sin(ry))
        x0 = px if s == 1 else -px - length
        bone.cube(f"{side}_horn_{i}", (x0, py - thick / 2, pz - thick / 2), (length, thick, thick), mat,
                  rotation=(0, yaw * s, lift * s), origin=(px * s, py, pz), mirror=s == -1, detail=horn)
        # the next segment starts a little inside this one's end, so the joint never opens
        px, py, pz = px + d[0] * (length - 1), py + d[1] * (length - 1), pz + d[2] * (length - 1)


HORN_SEGMENTS = [(4, 4, -8, 6), (4, 3, 4, 24), (4, 3, 20, 52), (3, 2, 36, 76), (3, 2, 46, 92)]


def build():
    m = Model("minotaur", texture_width=256)

    # ---- hips: the pelvis under a leather belt and a torn loincloth, and his tail
    hips = m.bone("hips", (0, 30, 0))
    hips.cube("pelvis", (-7, 24, -4), (14, 7, 8), SKIN)
    hips.cube("loincloth", (-7, 23, -4), (14, 6, 8), CLOTH, inflate=0.3)
    hips.cube("belt", (-7.5, 29, -4.5), (15, 2, 9), LEATHER, detail=belt)
    hips.cube("buckle", (-1.5, 28.5, -5.2), (3, 3, 1), IRON)
    hips.cube("flap_front", (-4, 14, -4.8), (8, 10, 1), CLOTH, detail=ragged)
    hips.cube("flap_back", (-4, 16, 4.1), (8, 8, 1), CLOTH, detail=ragged)
    tail = hips.bone("tail", (0, 30, 4))
    tail.cube("tail", (-1, 22, 4), (2, 8, 2), HIDE)
    tail.cube("tail_end", (-0.5, 15, 4.5), (1, 8, 1), HIDE)
    tail.cube("tuft", (-1.5, 11, 3.5), (3, 5, 3), CURLS, detail=fetlock)

    # ---- legs: heavy hairy thighs, a knee, a calf behind the shin, shaggy fetlocks and cloven hooves
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):  # a cube from a to a + w on the right becomes -(a + w) to -a on the left
            return a if s == 1 else -(a + w)
        mr = s == -1
        thigh = hips.bone(f"{side}_thigh", (4 * s, 28, 0))
        thigh.cube(f"{side}_thigh", (x(0, 8), 21, -4), (8, 8, 8), HIDE, mirror=mr)
        thigh.cube(f"{side}_thigh_low", (x(0.5, 7), 17, -3.5), (7, 5, 7), HIDE, mirror=mr, detail=knee)
        thigh.cube(f"{side}_knee", (x(1.5, 5), 16, -4.3), (5, 4, 2), HIDE, mirror=mr, detail=knee)
        shin = thigh.bone(f"{side}_shin", (4.5 * s, 18, 0))
        shin.cube(f"{side}_shin", (x(1.5, 6), 11, -2.5), (6, 7, 5), HIDE, mirror=mr)
        shin.cube(f"{side}_calf", (x(2, 5), 11, 1.5), (5, 6, 2), HIDE, mirror=mr, detail=knee)
        shin.cube(f"{side}_ankle", (x(2, 5), 5, -2), (5, 7, 4), HIDE, mirror=mr)
        shin.cube(f"{side}_fetlock", (x(1.5, 6), 3, -2.5), (6, 3, 5), CURLS, inflate=0.25, mirror=mr, detail=fetlock)
        shin.cube(f"{side}_hoof", (x(1, 7), 0, -3), (7, 3, 6), HOOF, mirror=mr, detail=hoof)
        shin.cube(f"{side}_toe_in", (x(1, 3), 0, -4.5), (3, 2, 2), HOOF, mirror=mr)
        shin.cube(f"{side}_toe_out", (x(5, 3), 0, -4.5), (3, 2, 2), HOOF, mirror=mr)

    # ---- the torso: a narrow waist, ribs, a deep chest, traps sloping up to a bull's hump behind the neck
    torso = hips.bone("torso", (0, 32, 0))
    torso.cube("waist", (-6.5, 31, -4), (13, 6, 8), SKIN, detail=torso_skin)
    torso.cube("abs", (-4.5, 31, -4.8), (9, 7, 1), SKIN, detail=abs_)
    torso.cube("ribs", (-8, 36, -4.5), (16, 6, 9), SKIN, detail=torso_skin)
    torso.cube("chest", (-9.5, 41, -5), (19, 6, 10), SKIN, detail=back)
    torso.cube("pecs", (-8, 41.5, -6), (16, 5, 1), SKIN, detail=pecs)
    torso.cube("lats", (-8.5, 37, 3.2), (17, 9, 2), SKIN, detail=back)
    torso.cube("traps", (-7, 46, -3.5), (14, 3, 7), SKIN, detail=deltoid)
    torso.cube("throat", (-4, 46, -6.5), (8, 5, 5), HEAD)
    torso.cube("hump", (-5.5, 47, -1.5), (11, 5, 7), HEAD, detail=deltoid)
    torso.cube("mane", (-4.5, 50, -0.5), (9, 3, 5), CURLS, inflate=0.2)

    # ---- the bull's head
    head = torso.bone("head", (0, 50, -3))
    head.cube("skull", (-5, 52, -9), (10, 6, 8), HEAD)
    head.cube("forelock", (-4, 56.5, -9.5), (8, 2, 5), CURLS, inflate=0.2)
    head.cube("brow", (-5.5, 54, -11), (11, 2, 3), HEAD, detail=brow)
    head.cube("face", (-4.5, 49, -12), (9, 6, 8), HEAD, detail=upper_face)
    head.cube("cheeks", (-5.5, 48, -10), (11, 4, 6), HEAD, detail=deltoid)
    head.cube("bridge", (-3.5, 47, -15), (7, 5, 4), HEAD, detail=nose_bridge)
    head.cube("muzzle", (-4.5, 45, -18), (9, 5, 5), MUZZLE, detail=muzzle)
    head.cube("ring", (-1.5, 42.5, -18.6), (3, 3, 1), IRON, detail=ring)
    jaw_bone = head.bone("jaw", (0, 47, -8))
    jaw_bone.cube("jaw", (-3.5, 43, -16), (7, 2, 9), HEAD, detail=jaw)
    jaw_bone.cube("chin", (-3, 42, -16.4), (6, 2, 4), MUZZLE)
    jaw_bone.cube("throat_tuft", (-3, 42, -9), (6, 4, 5), CURLS, inflate=0.2, detail=fetlock)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mr = s == -1
        horn_bone = head.bone(f"{side}_horn", (5 * s, 56, -6))
        horn_bone.cube(f"{side}_horn_boss", (x(3.5, 3), 54.5, -7.5), (3, 3, 3), HORN, mirror=mr)
        horn_chain(horn_bone, side, (5, 56, -6), HORN_SEGMENTS, HORN)
        ear_bone = head.bone(f"{side}_ear", (5 * s, 55, -4))
        ear_bone.cube(f"{side}_ear", (x(4.5, 3), 53, -5), (3, 2, 2), HEAD, mirror=mr)
        ear_bone.cube(f"{side}_ear_tip", (x(7, 3), 52.5, -4.8), (3, 2, 1), HEAD, mirror=mr, detail=ear,
                      rotation=(0, 0, -18 * s), origin=(7 * s, 53.5, -4.3))

    # ---- arms: deltoid, upper arm and biceps, forearm (elbows a little bent), the cuff, a fist
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mr = s == -1
        arm = torso.bone(f"{side}_arm", (11 * s, 47, 0))
        arm.cube(f"{side}_deltoid", (x(8, 6), 41, -3.5), (6, 7, 7), SKIN, mirror=mr, detail=deltoid)
        arm.cube(f"{side}_arm", (x(8.5, 5), 34, -2.5), (5, 8, 5), SKIN, mirror=mr, detail=limb)
        arm.cube(f"{side}_biceps", (x(9, 4), 35, -3.5), (4, 5, 2), SKIN, mirror=mr, detail=deltoid)
        forearm_bone = arm.bone(f"{side}_forearm", (11 * s, 35, 0), rotation=(10, 0, 0))
        forearm_bone.cube(f"{side}_elbow", (x(9, 4), 32, 1), (4, 4, 2), SKIN, mirror=mr, detail=deltoid)
        forearm_bone.cube(f"{side}_forearm", (x(8.5, 5), 29, -2.5), (5, 6, 5), SKIN, mirror=mr, detail=forearm)
        forearm_bone.cube(f"{side}_wrist", (x(9, 4), 25, -2), (4, 5, 4), SKIN, mirror=mr, detail=forearm)
        forearm_bone.cube(f"{side}_cuff", (x(8.5, 5), 24, -2.5), (5, 3, 5), IRON, inflate=0.25, mirror=mr, detail=cuff)
        forearm_bone.cube(f"{side}_link_0", (x(10.5, 1), 21.5, 2.6), (1, 3, 1), IRON, mirror=mr, detail=chain_link)
        forearm_bone.cube(f"{side}_link_1", (x(10.5, 1), 19, 2.4), (1, 3, 1), IRON, mirror=mr, detail=chain_link,
                          rotation=(0, 90, 0), origin=(11 * s, 20.5, 2.9))
        forearm_bone.cube(f"{side}_palm", (x(9.5, 3), 19, -2.5), (3, 5, 5), SKIN, mirror=mr, detail=limb)
        forearm_bone.cube(f"{side}_fingers", (x(9, 4), 16, -2.6), (4, 3, 2), SKIN, mirror=mr, detail=fist)
        forearm_bone.cube(f"{side}_fingers_low", (x(9, 4), 16, -0.4), (4, 3, 2), SKIN, mirror=mr, detail=fist)
        forearm_bone.cube(f"{side}_little", (x(9.3, 3), 16.5, 1.7), (3, 3, 1), SKIN, mirror=mr, detail=fist)
        forearm_bone.cube(f"{side}_thumb", (x(8.4, 2), 18, -3.6), (2, 4, 2), SKIN, mirror=mr, detail=fist)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # (Blockbench's rotations: +X tips the torso or head back and swings a hanging limb forward; the jaw opens with -X)
    # breathing hard, snorting, the tail lashing
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("torso", [(0, ZERO), (1.5, (0, 0.8, 0)), (3.0, ZERO)])
    idle.rot("head", [(0, (-5, 0, 0)), (0.8, (5, 0, 0)), (1.0, (-8, 0, 0)), (3.0, (-5, 0, 0))])
    idle.rot("jaw", [(0, ZERO), (0.8, ZERO), (1.0, (-6, 0, 0)), (1.3, ZERO), (3.0, ZERO)])
    idle.rot("tail", [(0, (20, 0, 0)), (1.0, (20, 20, 0)), (2.0, (20, -20, 0)), (3.0, (20, 0, 0))])
    idle.rot("right_arm", [(0, (0, 0, 10)), (1.5, (0, 0, 14)), (3.0, (0, 0, 10))])
    idle.rot("left_arm", [(0, (0, 0, -10)), (1.5, (0, 0, -14)), (3.0, (0, 0, -10))])

    # heavy strides on the hooves
    walk = m.animation("walk", 1.2, loop=True)
    walk.rot("right_thigh", [(0, (-30, 0, 0)), (0.6, (30, 0, 0)), (1.2, (-30, 0, 0))])
    walk.rot("left_thigh", [(0, (30, 0, 0)), (0.6, (-30, 0, 0)), (1.2, (30, 0, 0))])
    walk.rot("right_shin", [(0, (10, 0, 0)), (0.3, (-35, 0, 0)), (0.6, (0, 0, 0)), (1.2, (10, 0, 0))])
    walk.rot("left_shin", [(0, (0, 0, 0)), (0.6, (10, 0, 0)), (0.9, (-35, 0, 0)), (1.2, (0, 0, 0))])
    walk.rot("right_arm", [(0, (25, 0, 10)), (0.6, (-25, 0, 10)), (1.2, (25, 0, 10))])
    walk.rot("left_arm", [(0, (-25, 0, -10)), (0.6, (25, 0, -10)), (1.2, (-25, 0, -10))])
    walk.pos("hips", [(0, ZERO), (0.3, (0, -1, 0)), (0.6, ZERO), (0.9, (0, -1, 0)), (1.2, ZERO)])

    # the horns: head drawn back, a white flash, then the thrust up through you
    gore = m.animation("gore", 1.6)
    gore.rot("torso", [(0, ZERO), (0.6, (20, 0, 0)), (0.95, (25, 0, 0)), (1.1, (-35, 0, 0)), (1.6, ZERO)])
    gore.rot("head", [(0, ZERO), (0.6, (25, 0, 0)), (1.0, (30, 0, 0)), (1.1, (-40, 0, 0)), (1.25, (30, 0, 0)), (1.6, ZERO)])
    gore.rot("jaw", [(0, ZERO), (0.6, (-12, 0, 0)), (1.0, (-18, 0, 0)), (1.1, (-4, 0, 0)), (1.6, ZERO)])
    gore.rot("right_arm", [(0, ZERO), (0.6, (40, 0, 40)), (1.1, (-20, 0, 20)), (1.6, ZERO)])
    gore.rot("left_arm", [(0, ZERO), (0.6, (40, 0, -40)), (1.1, (-20, 0, -20)), (1.6, ZERO)])
    gore.pos("hips", [(0, ZERO), (0.6, (0, -3, 4)), (1.1, (0, -1, -6)), (1.6, ZERO)])
    gore.event(0.55, "flash").event(1.1, "hit")

    # the charge: he paws the ground, lowers his horns, and goes
    charge = m.animation("charge", 1.8)
    # pawing the ground, then running: both legs, in step
    charge.rot("right_thigh", [(0, ZERO), (0.25, (30, 0, 0)), (0.45, (-10, 0, 0)), (0.7, (30, 0, 0)), (0.9, (-10, 0, 0)), (1.2, (40, 0, 0)),
                               (1.4, (-40, 0, 0)), (1.6, (40, 0, 0)), (1.8, ZERO)])
    charge.rot("left_thigh", [(0, ZERO), (1.0, ZERO), (1.2, (-40, 0, 0)), (1.4, (40, 0, 0)), (1.6, (-40, 0, 0)), (1.8, ZERO)])
    charge.rot("torso", [(0, ZERO), (1.0, (-40, 0, 0)), (1.8, (-40, 0, 0))])
    charge.rot("head", [(0, ZERO), (1.0, (-20, 0, 0)), (1.8, (-20, 0, 0))])
    charge.rot("jaw", [(0, ZERO), (1.0, (-10, 0, 0)), (1.8, (-10, 0, 0))])
    charge.rot("right_arm", [(0, ZERO), (1.0, (-40, 0, 30)), (1.8, (-40, 0, 30))])
    charge.rot("left_arm", [(0, ZERO), (1.0, (-40, 0, -30)), (1.8, (-40, 0, -30))])
    charge.pos("hips", [(0, ZERO), (1.0, (0, -4, 0)), (1.8, (0, -4, 0))])
    charge.event(1.2, "go")

    # the stomp: up on one hoof, then down, both fists with it
    stomp = m.animation("stomp", 1.8)
    stomp.rot("right_thigh", [(0, ZERO), (0.7, (70, 0, 0)), (1.0, (75, 0, 0)), (1.15, (-10, 0, 0)), (1.8, ZERO)])
    stomp.rot("right_shin", [(0, ZERO), (0.7, (-60, 0, 0)), (1.15, ZERO)])
    stomp.rot("right_arm", [(0, ZERO), (0.8, (160, 0, 20)), (1.15, (20, 0, 10)), (1.8, ZERO)])
    stomp.rot("left_arm", [(0, ZERO), (0.8, (160, 0, -20)), (1.15, (20, 0, -10)), (1.8, ZERO)])
    stomp.rot("torso", [(0, ZERO), (0.8, (15, 0, 0)), (1.15, (-25, 0, 0)), (1.8, ZERO)])
    stomp.rot("jaw", [(0, ZERO), (0.8, (-8, 0, 0)), (1.15, (-22, 0, 0)), (1.8, ZERO)])
    stomp.pos("hips", [(0, ZERO), (0.8, (0, 3, 0)), (1.15, (0, -4, 0)), (1.8, ZERO)])
    stomp.event(1.15, "slam")

    # the frenzy: arms flung wide, he whirls right round
    frenzy = m.animation("frenzy", 2.6)
    frenzy.rot("right_arm", [(0, ZERO), (0.6, (0, 0, 85)), (2.2, (0, 0, 85)), (2.6, ZERO)])
    frenzy.rot("left_arm", [(0, ZERO), (0.6, (0, 0, -85)), (2.2, (0, 0, -85)), (2.6, ZERO)])
    frenzy.rot("hips", [(0, ZERO), (0.9, (0, -30, 0)), (1.3, (0, -150, 0)), (1.7, (0, -270, 0)), (2.0, (0, -360, 0)), (2.6, (0, -360, 0))],
               interp="linear")
    frenzy.rot("head", [(0, ZERO), (0.6, (20, 0, 0)), (2.0, (20, 0, 0)), (2.6, ZERO)])
    frenzy.rot("jaw", [(0, ZERO), (0.6, (-20, 0, 0)), (2.0, (-20, 0, 0)), (2.6, ZERO)])
    frenzy.event(0.9, "spin").event(2.0, "spun")

    # reels after a blocked thrust or a charge into the rock: on one knee, shaking his head
    stagger = m.animation("stagger", 2.4)
    stagger.pos("hips", [(0, ZERO), (0.3, (0, -9, 2)), (2.0, (0, -9, 2)), (2.4, ZERO)])
    stagger.rot("right_thigh", [(0, ZERO), (0.3, (80, 0, 0)), (2.0, (80, 0, 0)), (2.4, ZERO)])
    stagger.rot("right_shin", [(0, ZERO), (0.3, (-90, 0, 0)), (2.0, (-90, 0, 0)), (2.4, ZERO)])
    stagger.rot("torso", [(0, ZERO), (0.3, (-30, 0, 0)), (2.0, (-30, 0, 0)), (2.4, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.6, (-20, -25, 0)), (0.9, (-20, 25, 0)), (1.2, (-20, -25, 0)), (1.5, (-20, 25, 0)), (2.4, ZERO)])
    stagger.rot("jaw", [(0, ZERO), (0.3, (-14, 0, 0)), (2.0, (-10, 0, 0)), (2.4, ZERO)])
    stagger.rot("right_arm", [(0, ZERO), (0.3, (40, 0, 10)), (2.0, (40, 0, 10)), (2.4, ZERO)])

    # his fury at half health: he bites his own arm, then bellows
    phase = m.animation("phase", 3.0)
    phase.rot("left_arm", [(0, ZERO), (0.5, (110, 30, -10)), (1.4, (110, 30, -10)), (1.8, ZERO)])
    phase.rot("left_forearm", [(0, ZERO), (0.5, (60, 0, 0)), (1.4, (60, 0, 0)), (1.8, ZERO)])
    phase.rot("head", [(0, ZERO), (0.5, (-30, -30, 0)), (0.8, (-40, -35, 0)), (1.1, (-30, -30, 0)), (1.4, (-40, -35, 0)), (1.9, (45, 0, 0)),
                       (2.7, (45, 0, 0)), (3.0, ZERO)])
    phase.rot("jaw", [(0, ZERO), (0.45, (-25, 0, 0)), (0.6, (-2, 0, 0)), (0.95, (-22, 0, 0)), (1.1, (-2, 0, 0)), (1.6, ZERO), (1.9, (-35, 0, 0)),
                      (2.7, (35, 0, 0)), (3.0, ZERO)])
    phase.rot("torso", [(0, ZERO), (1.9, (20, 0, 0)), (2.7, (20, 0, 0)), (3.0, ZERO)])
    phase.rot("right_arm", [(0, ZERO), (1.9, (30, 0, 70)), (2.7, (30, 0, 70)), (3.0, ZERO)])
    phase.event(0.6, "bite").event(1.9, "roar")

    # his entrance: crouched over a carcass, he rears up and bellows
    intro = m.animation("intro", 5.0)
    intro.pos("hips", [(0, (0, -10, 3)), (2.0, (0, -10, 3)), (2.8, ZERO), (5.0, ZERO)])
    intro.rot("torso", [(0, (-50, 0, 0)), (2.0, (-50, 0, 0)), (2.8, (15, 0, 0)), (4.2, (15, 0, 0)), (5.0, ZERO)])
    intro.rot("right_thigh", [(0, (70, 0, 0)), (2.0, (70, 0, 0)), (2.8, ZERO)])
    intro.rot("right_shin", [(0, (-80, 0, 0)), (2.0, (-80, 0, 0)), (2.8, ZERO)])
    intro.rot("left_thigh", [(0, (50, 0, 0)), (2.0, (50, 0, 0)), (2.8, ZERO)])
    intro.rot("left_shin", [(0, (-60, 0, 0)), (2.0, (-60, 0, 0)), (2.8, ZERO)])
    intro.rot("head", [(0, (-20, 0, 0)), (1.0, (-20, 20, 0)), (2.0, (-20, 20, 0)), (2.8, (40, 0, 0)), (4.2, (40, 0, 0)), (5.0, ZERO)])
    intro.rot("jaw", [(0, ZERO), (0.6, (-12, 0, 0)), (0.8, ZERO), (1.3, (-12, 0, 0)), (1.5, ZERO), (2.8, ZERO), (2.95, (-38, 0, 0)), (4.2, (-38, 0, 0)),
                      (4.6, ZERO)])
    intro.rot("right_arm", [(0, (60, 0, 0)), (2.0, (60, 0, 0)), (2.8, (20, 0, 80)), (4.2, (20, 0, 80)), (5.0, ZERO)])
    intro.rot("left_arm", [(0, (60, 0, 0)), (2.0, (60, 0, 0)), (2.8, (20, 0, -80)), (4.2, (20, 0, -80)), (5.0, ZERO)])
    intro.event(2.0, "rise").event(2.9, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.12, (15, 0, 0)), (0.4, ZERO)])
    hurt.rot("jaw", [(0, ZERO), (0.12, (-15, 0, 0)), (0.4, ZERO)])

    # he topples forward onto his horns
    death = m.animation("death", 3.0)
    death.pos("hips", [(0, ZERO), (0.8, (0, -8, 0)), (1.8, (0, -18, -12)), (3.0, (0, -24, -18))])
    death.rot("hips", [(0, ZERO), (0.8, (-10, 0, 0)), (1.8, (-70, 0, 0)), (3.0, (-85, 0, 0))])
    death.rot("right_thigh", [(0, ZERO), (0.8, (70, 0, 0)), (3.0, (80, 0, 0))])
    death.rot("left_thigh", [(0, ZERO), (0.8, (60, 0, 0)), (3.0, (75, 0, 0))])
    death.rot("right_arm", [(0, ZERO), (1.8, (150, 0, 20)), (3.0, (160, 0, 20))])
    death.rot("left_arm", [(0, ZERO), (1.8, (150, 0, -20)), (3.0, (160, 0, -20))])
    death.rot("jaw", [(0, ZERO), (0.8, (-20, 0, 0)), (1.8, (-10, 0, 0)), (3.0, (-16, 0, 0))])
    death.event(1.8, "fall")


if __name__ == "__main__":
    build().write()
