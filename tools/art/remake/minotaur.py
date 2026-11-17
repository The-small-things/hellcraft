#!/usr/bin/env python3
"""The Minotaur remade at twice the detail (drawn at half his old scale): the same wrestler's build on cloven hooves,
with a chest in two slabs over a belly of six muscles, veined arms ending in fists of separate fingers, broken cuffs
trailing their chains, a bull's head with eyes burning in the sides of its face, flared wet nostrils with a real ring
through them, teeth and a tongue in the jaw, and horns that sweep out and up in seven tapering pieces. His skeleton
and animations are the old ones.

    python3 tools/art/remake/minotaur.py && python3 tools/gen_bosses.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, mix, shade  # noqa: E402
from paintkit import (ao, both, fill, fingers, hline, line, round_all, speckle, tint, tint_rect, tone, tone_all,  # noqa: E402
                      tone_rect, tuck, veins, vline, blot)

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
TOOTH = material("tooth", "flat", base="d8ccb0")
TONGUE = material("tongue", "flat", base="8a3a3a")
EYE = hexc("ff3a1a")
EYE_HOT = hexc("ffb070")
NOSTRIL = hexc("0a0606")
WET = hexc("7a6a64")
MOUTH = hexc("3a0e0a")
VEIN = hexc("5e3426")
SCAR = hexc("b88a78")
INNER_EAR = hexc("8a5048")
RUST = hexc("6a3a20")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


# ----------------------------------------------------------------------------------------------- details

def muscle(rows=3, k=0.72, lit=1.1, vein=0):
    def detail(fs):
        tuck(fs, rows, k, lit)
        for n in ("front", "left", "right"):
            if vein:
                veins(fs[n], vein, VEIN, 0.5)
    return detail


def pec(fs):
    """One slab of his chest: rounded, its lower edge in deep shadow, coarse hair toward the breastbone."""
    f = fs["front"]
    tuck(fs, 3, 0.62, 1.12)
    inner = 0 if fs["front"].cube.frm[0] < 0 else f.w - 1   # the breastbone side
    for y in range(f.h):
        for i in range(4 - y // 3):
            x = inner + i if inner == 0 else inner - i
            if (x * 7 + y * 3) % 3:
                f.px(x, y, HIDE.c["light"] if (x + y) % 2 else HIDE.c["dark"])
    nx = f.w - 5 if inner == 0 else 4
    f.px(nx, f.h - 4, shade(SKIN.c["dark"], 0.7))
    f.px(nx + 1, f.h - 4, shade(SKIN.c["dark"], 0.7))


def ab(fs):
    tuck(fs, 1, 0.7, 1.12)
    f = fs["front"]
    vline(f, 0, 0.8)
    vline(f, f.w - 1, 0.8)


def waist(fs):
    """Ribs and the saw-toothed muscles down his flanks; the line of hair to his navel."""
    f = fs["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid - 1, y, shade(HIDE.c["dark"], 1.2))
        f.px(mid, y, shade(HIDE.c["dark"], 1.0))
    for n in ("left", "right"):
        s = fs[n]
        for y in range(1, s.h, 4):
            line(s, 1, y + 1, s.w - 2, y, k=0.78)
            line(s, 1, y + 2, s.w - 2, y + 1, k=1.08)
    b = fs["back"]
    vline(b, b.w // 2, 0.62)
    vline(b, b.w // 2 - 1, 0.62)
    vline(b, b.w // 2 + 1, 1.08)
    tuck(fs, 2, 0.8)


def back(fs):
    """His back: the spine's groove, the shoulder blades, old whip scars."""
    b = fs["back"]
    mid = b.w // 2
    vline(b, mid, 0.6)
    vline(b, mid - 1, 0.6)
    vline(b, mid + 1, 1.1)
    for cx in (mid - 9, mid + 8):
        line(b, cx - 3, 1, cx + 3, b.h - 2, k=0.8)
    for x0, y0, x1, y1 in ((3, 2, 12, 5), (b.w - 14, 1, b.w - 5, 6), (6, 7, 15, 9)):
        line(b, x0, y0, x1, y1, colour=SCAR, t=0.5)
    hline(b, b.h - 1, 0.72)
    f = fs["front"]
    line(f, 4, 2, 12, 9, colour=SCAR, t=0.55)   # a scar across his chest, under the hair
    tuck(fs, 2, 0.8)


def upper_face(fs):
    """The bull's eyes, set in the sides of his face under the brow and burning red; a pale blaze down the middle."""
    f = fs["front"]
    tone_rect(f, 0, 0, f.w, 2, 0.6)
    mid = f.w // 2
    for y in range(f.h):
        for dx, t in ((-2, 0.25), (-1, 0.5), (0, 0.5), (1, 0.25)):
            tint(f, mid + dx, y, hexc("9a8068"), t)
    for cx in (0, f.w - 3):
        f.rect(cx, 2, 3, 5, shade(HEAD.c["dark"], 0.45))
        for y in (3, 4, 5):
            f.glow(cx + (0 if cx else 2), y, EYE)
            f.glow(cx + 1, y, EYE if y != 4 else EYE_HOT)
    for name, ex in (("right", fs["right"].w - 4), ("left", 0)):
        s = fs[name]
        tone_rect(s, 0, 0, s.w, 2, 0.6)
        s.rect(ex, 2, 4, 5, shade(HEAD.c["dark"], 0.4))
        for y in (3, 4, 5):
            for x in range(ex + 1, ex + 3):
                s.glow(x, y, EYE)
        s.glow(ex + 1, 4, EYE_HOT)
        s.glow(ex + 2, 4, hexc("fff0d0"))
        s.px(ex + (3 if name == "left" else 0), 7, shade(HEAD.c["dark"], 0.5))
    tuck(fs, 2, 0.75)


def brow(fs):
    tone_all(fs["bottom"], 0.4)
    for n in ("front", "left", "right"):
        f = fs[n]
        hline(f, f.h - 1, 0.55)
        hline(f, f.h - 2, 0.8)
        hline(f, 0, 1.1)
    f = fs["front"]
    for x in (f.w // 2 - 1, f.w // 2):
        vline(f, x, 0.7)   # the furrow between his eyes


def bridge(fs):
    f = fs["front"]
    for y in range(f.h):
        for dx in (-1, 0):
            tint(f, f.w // 2 + dx, y, hexc("9a8068"), 0.4)
    tuck(fs, 2, 0.8)
    for n in ("left", "right"):
        s = fs[n]
        for y in range(2, s.h - 1, 3):
            hline(s, y, 0.85, 1, s.w - 1)   # wrinkles where he snarls


def muzzle(fs):
    """A broad wet muzzle: flared nostrils with a shine over them, the cleft of his lip, the mouth's line."""
    f = fs["front"]
    w = f.w
    for nx in (2, w - 6):
        f.rect(nx, 3, 4, 3, NOSTRIL)
        f.px(nx, 3, shade(MUZZLE.c["dark"], 0.8))
        f.px(nx + 3, 3, shade(MUZZLE.c["dark"], 0.8))
        for x in range(nx, nx + 4):
            f.px(x, 2, WET)
        f.px(nx + 1, 1, shade(WET, 1.15))
    vline(f, w // 2, 0.6, 3)
    vline(f, w // 2 - 1, 0.6, 3)
    hline(f, f.h - 1, 0.5)
    hline(f, f.h - 2, 0.8)
    speckle(f, WET, 0.06, 0.5)
    b = fs["bottom"]
    fill(b, MOUTH, 0.05)
    for x in range(0, b.w, 3):
        b.px(x, 0, TOOTH.c["base"])
        b.px(x + 1, 0, shade(TOOTH.c["base"], 0.85))
    for name in ("left", "right"):
        s = fs[name]
        hline(s, s.h - 1, 0.5)
        hline(s, s.h - 2, 0.8)
    tone_all(fs["top"], 1.05)


def jaw(fs):
    t = fs["top"]
    fill(t, MOUTH, 0.05)
    tuck(fs, 2, 0.65, names=("front", "left", "right"))


def teeth_row(fs):
    for n in ("front", "left", "right", "back"):
        f = fs[n]
        for x in range(2, f.w, 3):
            for y in range(f.h):
                f.px(x, y, shade(MOUTH, 1.2))
        hline(f, f.h - 1, 0.8)


def ear(fs):
    f = fs["front"]
    for x in range(1, f.w - 1):
        for y in range(1, f.h - 1):
            f.px(x, y, INNER_EAR if (x + y) % 3 else shade(INNER_EAR, 0.75))


def horn(fs):
    for n in ("front", "back", "top", "bottom"):
        f = fs[n]
        for x in range(0, f.w, 3):
            vline(f, x, 0.85)
        hline(f, f.h - 1, 0.8)


def horn_boss(fs):
    tuck(fs, 2, 0.7, 1.15)


def fist(fs):
    fingers(fs, 3, 0.6, names=("left", "right"))
    tuck(fs, 1, 0.78)


def finger(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        hline(f, f.h // 2, 0.75)       # the knuckle's crease
        hline(f, f.h - 1, 0.7)
        hline(f, f.h - 2, 1.15)        # a thick nail's edge


def cuff(fs):
    """A broken iron cuff: rivets, pitting and rust."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(2, f.w, 4):
            f.px(x, 1, IRON.c["high"])
            f.px(x, f.h - 2, IRON.c["high"])
        speckle(f, RUST, 0.16, 0.7)
        hline(f, f.h // 2, 0.7)


def link(fs):
    for n in ("front", "back"):
        f = fs[n]
        if f.w >= 3 and f.h >= 4:
            for y in range(1, f.h - 1):
                for x in range(1, f.w - 1):
                    f.px(x, y, (0, 0, 0), 0)
    for f in fs.values():
        speckle(f, RUST, 0.2, 0.7)


def belt(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        hline(f, 0, 1.3)
        hline(f, f.h - 1, 0.6)
        for x in range(2, f.w, 5):
            f.px(x, f.h // 2, IRON.c["high"])
            f.px(x, f.h // 2 + 1, IRON.c["low"])


def buckle(fs):
    f = fs["front"]
    f.rect(2, 2, f.w - 4, f.h - 4, shade(LEATHER.c["base"], 0.7))
    vline(f, f.w // 2, 1.3, 1, f.h - 1)


def ragged(fs):
    """Torn cloth: its lower edge in tatters, stained dark."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        x = 0
        while x < f.w:
            wide = f.rng.choice((1, 2, 2, 3))
            cut = f.rng.choice((0, 1, 2, 3, 5))
            for xx in range(x, min(f.w, x + wide)):
                for y in range(f.h - cut, f.h):
                    f.px(xx, y, (0, 0, 0), 0)
                if cut < f.h:
                    tone(f, xx, f.h - cut - 1, 0.65)
            x += wide
        blot(f, f.rng.randrange(f.w), f.h * 0.6, 3, hexc("2a0e08"), 0.6)
    tone_all(fs["bottom"], 0.7)


def hoof(fs):
    f = fs["front"]
    vline(f, f.w // 2, 0.5)
    vline(f, f.w // 2 - 1, 0.5)
    for n in ("front", "left", "right", "back"):
        s = fs[n]
        hline(s, s.h - 1, 1.5)
        hline(s, 0, 0.8)
        for y in range(2, s.h - 1, 3):
            hline(s, y, 0.85)


def fetlock(fs):
    tuck(fs, 2, 0.6)


def knee(fs):
    tuck(fs, 2, 0.7, 1.12)


# ----------------------------------------------------------------------------------------------- the model

def horn_chain(b, side, s):
    """A horn sweeping out, up and forward in seven tapering pieces."""
    px, py, pz = 10.0, 112.0, -12.0
    segs = [(7, 8, -8, 6), (7, 7, 0, 18), (6, 6, 8, 32), (6, 5, 18, 48), (6, 4, 28, 64), (5, 3, 38, 80), (5, 2, 46, 94)]
    for i, (length, thick, yaw, lift) in enumerate(segs):
        ry, rz = math.radians(yaw), math.radians(lift)
        d = (math.cos(rz) * math.cos(ry), math.sin(rz) * math.cos(ry), -math.sin(ry))
        x0 = px if s == 1 else -px - length
        b.cube(f"{side}_horn", f"{side}_horn_{i}", (x0, py - thick / 2, pz - thick / 2), (length, thick, thick), HORN_SIDE[i],
               rotation=(0, yaw * s, lift * s), origin=(px * s, py, pz), detail=horn)
        px, py, pz = px + d[0] * (length - 1.5), py + d[1] * (length - 1.5), pz + d[2] * (length - 1.5)


# a horn's colour runs root to tip along the whole chain: each piece its own flat band
HORN_SIDE = [material(f"horn{i}", "flat", base="%02x%02x%02x" % mix(HORN.c["root"], HORN.c["tip"], (i / 6) ** 1.4))
             for i in range(7)]


def build():
    b = kit.Remake("minotaur", tex_width=512)

    # the hips: a torn loincloth under a studded belt
    b.keep("pelvis", SKIN)
    b.keep("loincloth", CLOTH, grow=(1, 0, 1), detail=ragged)
    b.keep("belt", LEATHER, detail=belt)
    b.keep("buckle", IRON, detail=buckle)
    b.keep("flap_front", CLOTH, detail=ragged)
    b.keep("flap_back", CLOTH, detail=ragged)
    b.cube("hips", "flap_front_under", (-6, 32, -9.2), (12, 14, 1), CLOTH, detail=ragged)
    for side, s in SIDES:
        b.cube("hips", f"flap_{side}", (X(13.4, 2, s), 36, -5), (2, 12, 10), CLOTH, detail=ragged)
    b.keep("tail", HIDE, detail=ao(0, 2, 0.7))
    b.keep("tail_end", HIDE)
    b.keep("tuft", CURLS, detail=fetlock)
    b.cube("tail", "tuft_tip", (-2, 18, 8), (4, 5, 4), CURLS, detail=fetlock)

    for side, s in SIDES:
        b.keep(f"{side}_thigh", HIDE, detail=muscle(3, 0.75))
        b.keep(f"{side}_thigh_low", HIDE, detail=knee)
        b.keep(f"{side}_knee", HIDE, detail=knee)
        b.cube(f"{side}_thigh", f"{side}_quad", (X(2, 12, s), 40, -9.4), (12, 14, 2), HIDE, detail=muscle(3, 0.7))
        b.cube(f"{side}_thigh", f"{side}_ham", (X(2, 12, s), 40, 7.4), (12, 13, 2), HIDE, detail=muscle(3, 0.7))
        b.keep(f"{side}_shin", HIDE, detail=muscle(2, 0.75))
        b.keep(f"{side}_calf", HIDE, detail=knee)
        b.keep(f"{side}_ankle", HIDE)
        b.keep(f"{side}_fetlock", CURLS, grow=(1, 0, 1), detail=fetlock)
        b.cube(f"{side}_shin", f"{side}_fetlock_back", (X(5, 8, s), 4, 4), (8, 7, 3), CURLS, detail=fetlock)
        b.keep(f"{side}_hoof", HOOF, detail=hoof)
        b.keep(f"{side}_toe_in", HOOF, detail=hoof)
        b.keep(f"{side}_toe_out", HOOF, detail=hoof)
        for k, dx in enumerate((4.5, 11.5)):
            b.cube(f"{side}_shin", f"{side}_dewclaw_{k}", (X(dx, 2, s), 5, 5.6), (2, 3, 2), HOOF)

    # the torso
    b.keep("waist", SKIN, detail=waist)
    for row, y in enumerate((62.4, 67.2, 72.0)):
        for side, s in SIDES:
            b.cube("torso", f"ab_{row}_{side}", (X(0.5, 8, s), y, -10.2), (8, 4, 2), SKIN, detail=ab)
    b.keep("ribs", SKIN, detail=waist)
    b.keep("chest", SKIN, detail=back)
    for side, s in SIDES:
        b.cube("torso", f"pec_{side}", (X(0.5, 16, s), 82.6, -12.6), (16, 10, 3), SKIN, detail=pec)
        b.cube("torso", f"oblique_{side}", (X(12, 3, s), 63, -8.6), (3, 9, 10), SKIN, detail=muscle(2, 0.75))
        b.cube("torso", f"blade_{side}", (X(3, 12, s), 80, 9.4), (12, 11, 2), SKIN, detail=muscle(3, 0.75))
    b.keep("lats", SKIN, detail=back)
    b.keep("traps", SKIN, detail=muscle(2, 0.75, 1.12))
    b.keep("throat", HEAD, detail=ao(3, 0, 0.7))
    b.keep("hump", HEAD, detail=muscle(2, 0.75, 1.12))
    b.keep("mane", CURLS, grow=(1, 0, 0))
    b.cube("torso", "mane_ridge", (-3, 104, 0), (6, 3, 8), CURLS)
    b.cube("torso", "mane_back", (-6, 92, 9), (12, 9, 3), CURLS, detail=fetlock)

    # the head
    b.keep("skull", HEAD)
    b.keep("forelock", CURLS, grow=(1, 0, 0))
    b.cube("head", "forelock_fringe", (-6, 111, -21), (12, 3, 3), CURLS, detail=fetlock)
    b.keep("brow", HEAD, detail=brow)
    b.keep("face", HEAD, detail=upper_face)
    b.keep("cheeks", HEAD, detail=muscle(2, 0.72, 1.1))
    b.keep("bridge", HEAD, detail=bridge)
    b.keep("muzzle", MUZZLE, detail=muzzle)
    for side, s in SIDES:
        b.cube("head", f"nostril_{side}", (X(4, 5, s), 92.6, -37), (5, 5, 2), MUZZLE, detail=ao(1, 0, 0.7))
        b.cube("head", f"ring_{side}", (X(2, 1, s), 84, -37.4), (1, 7, 1), IRON)
        b.cube("head", f"upper_teeth_{side}", (X(6.4, 1, s), 89, -34), (1, 2, 12), TOOTH, detail=teeth_row)
    b.cube("head", "ring_bottom", (-3, 83, -37.4), (6, 1, 1), IRON)
    b.keep("jaw", HEAD, detail=jaw)
    b.keep("chin", HEAD, detail=ao(2, 0, 0.7))
    b.keep("throat_tuft", CURLS, detail=fetlock)
    b.cube("jaw", "lower_teeth", (-6, 90, -31.4), (12, 1, 1), TOOTH, detail=teeth_row)
    for side, s in SIDES:
        b.cube("jaw", f"lower_teeth_{side}", (X(5, 1, s), 90, -30.4), (1, 1, 10), TOOTH, detail=teeth_row)
    b.cube("jaw", "tongue", (-3, 89.6, -29), (6, 1, 12), TONGUE, detail=ao(0, 0))
    b.cube("jaw", "beard", (-3, 80, -31), (6, 5, 5), CURLS, detail=fetlock)
    for side, s in SIDES:
        b.keep(f"{side}_horn_boss", HEAD, grow=(1, 1, 1), detail=horn_boss)
        horn_chain(b, side, s)
        b.keep(f"{side}_ear", HEAD, detail=ear)
        b.keep(f"{side}_ear_tip", HEAD, detail=ear)

    # the arms
    for side, s in SIDES:
        arm, fore = f"{side}_arm", f"{side}_forearm"
        b.keep(f"{side}_deltoid", SKIN, detail=muscle(4, 0.7, 1.12, 1))
        b.cube(arm, f"{side}_deltoid_cap", (X(17, 10, s), 95.4, -6), (10, 2, 12), SKIN, detail=muscle(1, 0.85, 1.12))
        b.keep(f"{side}_arm", SKIN, detail=muscle(2, 0.75, 1.08, 1))
        b.keep(f"{side}_biceps", SKIN, detail=muscle(3, 0.68, 1.12, 1))
        b.cube(arm, f"{side}_triceps", (X(18, 8, s), 72, 3.6), (8, 11, 3), SKIN, detail=muscle(3, 0.7, 1.1))
        b.keep(f"{side}_elbow", SKIN, detail=muscle(2, 0.75))
        b.keep(f"{side}_forearm", SKIN, detail=muscle(2, 0.75, 1.08, 2))
        b.cube(fore, f"{side}_forearm_swell", (X(16.4, 11, s), 59, -6), (11, 8, 5), SKIN, detail=muscle(2, 0.72, 1.1, 2))
        b.keep(f"{side}_wrist", SKIN, detail=muscle(1, 0.8, 1.05, 2))
        b.keep(f"{side}_cuff", IRON, grow=(1, 0, 1), detail=cuff)
        b.keep(f"{side}_link_0", IRON, grow=(0.5, 0, 0), detail=link)
        b.keep(f"{side}_link_1", IRON, grow=(0.5, 0, 0), detail=link)
        b.cube(fore, f"{side}_link_2", (X(20.5, 3, s), 33.4, 5.2), (3, 6, 1), IRON, detail=link)
        b.keep(f"{side}_palm", SKIN, detail=fist)
        for k, z in enumerate((-5.4, -2.8, -0.2, 2.4)):
            b.cube(fore, f"{side}_finger_{k}", (X(18, 7, s), 31.4 + (0.8 if k == 3 else 0), z), (7, 7 - (1 if k == 3 else 0), 2),
                   SKIN, detail=finger)
        b.keep(f"{side}_thumb", SKIN, detail=finger)
    b.finish()


if __name__ == "__main__":
    build()
