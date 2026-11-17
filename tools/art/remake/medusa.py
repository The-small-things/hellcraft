#!/usr/bin/env python3
"""Medusa remade at twice the detail (two model pixels to a game pixel; drawn at half MedusaEntity.SCALE): the bust
come alive over the battlements of Dis, with room on her face for lidded eyes, a nose with nostrils, parted lips and a
cleft chin, a plinth with a carved band, a chiton pinned with a serpent brooch, and sixteen serpents that taper from
thick roots to heads with jaws, eyes and tongues. Her skeleton and animations are the old ones (bust > head > eyes,
snake<i> > _mid > _tip), so the gaze still opens the "eyes" part.

Run from the repo root (needs Blender's Python module, `pip install bpy`):
    python3 tools/art/remake/medusa.py && python3 tools/gen_bosses.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, mix, shade  # noqa: E402
from paintkit import (cracks, fill, grad, groove, hline, line, round_all, speckle, tint, tint_rect, tone,  # noqa: E402
                      tone_all, tone_rect, tuck, vline)

STONE = material("stone", "skin", base="8e8e86", dark="5c5c56")
LIPS = material("lips", "skin", base="76716c", dark="54504c")
PLINTH = material("plinth", "skin", base="6c6a64", dark="46443f")
SERPENT = material("serpent", "scales", base="3a5a3a", edge="6a8a4a", belly="9aa070")
COILS = material("coils", "scales", base="2e4a30", edge="5a7a42", belly="8a9060")
ROBE = material("robe", "robe", base="6a6862", fold="48463f")
BRONZE = material("bronze", "metal", base="6a7a5a", high="a8b890", low="34402e")
EYE = hexc("e8fff0")
EYE_EDGE = hexc("8affc0")
LID = hexc("7c7c74")
LASH = hexc("2e2e2c")
FANG = hexc("e8e4d0")
MOUTH = hexc("3a1e22")
TONGUE = material("tongue", "flat", base="8a2a2e")
SNAKE_EYE = hexc("e8d040")
MOSS = hexc("56664a")
DARK = shade(STONE.c["dark"], 0.75)


# ----------------------------------------------------------------------------------------------- geometry helpers

def add(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def mul(a, k):
    return tuple(v * k for v in a)


def norm(a):
    n = math.sqrt(sum(v * v for v in a)) or 1.0
    return tuple(v / n for v in a)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def turn(v, axis, deg):
    k = norm(axis)
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    kv = cross(k, v)
    kd = sum(k[i] * v[i] for i in range(3))
    return tuple(v[i] * c + kv[i] * s + k[i] * kd * (1 - c) for i in range(3))


def side_x(s):
    return lambda a, w: a if s == 1 else -(a + w)


SIDES = (("right", 1), ("left", -1))


# ----------------------------------------------------------------------------------------------- details

def weathered(faces):
    """Old stone: hairline cracks, a little moss in the low corners."""
    for n in ("front", "back", "left", "right"):
        f = faces[n]
        cracks(f, max(1, f.w * f.h // 160), 0.72)
        for _ in range(f.w * f.h // 60):
            x, y = f.rng.randrange(f.w), f.h - 1 - f.rng.randrange(max(1, f.h // 3))
            tint(f, x, y, MOSS, 0.35)


def upper_face(faces):
    """Her shut eyes: the brow's shadow, each lid a smooth dome with its crease above and lashes along its edge, the
    hollow beside the nose and the light on its bridge."""
    f = faces["front"]
    tone_rect(f, 0, 0, f.w, 2, 0.72)
    for x0 in (1, f.w - 6):
        tone_rect(f, x0 - 1 if x0 > 1 else 0, 2, 6, 5, 0.86)        # the socket
        for dx in range(5):
            f.px(x0 + dx, 3, shade(LID, 0.6))                          # the crease
            f.px(x0 + dx, 4, shade(LID, 1.12 if 0 < dx < 4 else 0.95))  # the lid, lit
            f.px(x0 + dx, 5, LID)
            f.px(x0 + dx, 6, LASH)
        f.px(x0, 4, shade(LID, 0.85))
        f.px(x0 + 4, 4, shade(LID, 0.85))
        tone_rect(f, x0, 7, 5, 1, 0.9)                                  # under the eye
    mid = f.w // 2
    for y in range(f.h):
        tone(f, mid - 1, y, 1.08)
        tone(f, mid, y, 1.08)
        tone(f, mid - 2, y, 0.85)
        tone(f, mid + 1, y, 0.85)
    for n in ("left", "right"):
        tone_rect(faces[n], 0, 0, faces[n].w, 2, 0.8)


def forehead(faces):
    f = faces["front"]
    hline(f, f.h - 1, 1.1)
    # a crack running down from her hairline: she is stone
    for x, y in ((4, 0), (4, 1), (5, 1), (5, 2), (5, 3), (6, 3), (6, 4)):
        tone(f, x, y, 0.62)
    for x, y in ((10, 0), (10, 1), (9, 2)):
        tone(f, x, y, 0.7)
    tone_rect(f, 0, 0, f.w, 1, 0.8)


def cheek(faces):
    tuck(faces, 1, 0.8, 1.12)


def nose(faces):
    """The bridge of her nose: lit along its ridge, shadowed down each side."""
    f = faces["front"]
    for y in range(f.h):
        tone(f, 0, y, 0.85)
        tone(f, f.w - 1, y, 0.85)
        tone(f, f.w // 2, y, 1.1)
    for n in ("left", "right"):
        tone_all(faces[n], 0.82)


def nose_tip(faces):
    f = faces["front"]
    tone(f, 1, 0, 1.12)
    tone(f, 2, 0, 1.12)
    b = faces["bottom"]
    fill(b, shade(STONE.c["dark"], 0.8), 0.03)
    for x in (0, b.w - 1):
        b.px(x, b.h - 1, hexc("26262a"))
        b.px(x, b.h - 2, hexc("26262a"))
    for n in ("left", "right"):
        s = faces[n]
        tone_rect(s, 0, s.h - 1, s.w, 1, 0.8)


def upper_lip(faces):
    f = faces["front"]
    mid = f.w // 2
    tone(f, mid - 1, 0, 0.8)   # the cupid's bow
    tone(f, mid, 0, 0.8)
    hline(f, f.h - 1, 0.7)
    tone(f, 0, 0, 0.8)
    tone(f, f.w - 1, 0, 0.8)
    fill(faces["bottom"], MOUTH, 0.03)


def lower_lip(faces):
    f = faces["front"]
    hline(f, 0, 1.15, 1, f.w - 1)
    hline(f, f.h - 1, 0.8)
    tone(f, 0, 0, 0.75)
    tone(f, f.w - 1, 0, 0.75)
    fill(faces["top"], MOUTH, 0.03)


def lower_face(faces):
    """Her cheeks' hollows, the line from nose to mouth, and the shadow under her jaw."""
    f = faces["front"]
    for x in (1, 2, f.w - 3, f.w - 2):
        for y in range(1, f.h - 1):
            tone(f, x, y, 0.9)
    line(f, 3, 0, 2, 3, k=0.8)
    line(f, f.w - 4, 0, f.w - 3, 3, k=0.8)
    fill(faces["bottom"], shade(STONE.c["dark"], 0.75), 0.04)
    for n in ("left", "right"):
        s = faces[n]
        hline(s, s.h - 1, 0.75)


def chin(faces):
    f = faces["front"]
    tone(f, f.w // 2, 1, 0.8)   # the cleft
    tone(f, f.w // 2 - 1, 1, 0.8)
    hline(f, 0, 0.82)
    hline(f, f.h - 1, 0.75)
    fill(faces["bottom"], shade(STONE.c["dark"], 0.75), 0.04)


def open_eyes(faces):
    """The gaze: a white-hot slit in a ring of cold green light."""
    for name, f in faces.items():
        for y in range(f.h):
            for x in range(f.w):
                edge = x in (0, f.w - 1) or y in (0, f.h - 1)
                f.glow(x, y, EYE_EDGE if edge else EYE)
    f = faces["front"]
    for y in range(f.h):
        f.glow(f.w // 2, y, hexc("ffffff"))
        f.glow(f.w // 2 - 1, y, hexc("ffffff"))


def neck(faces):
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        tone(f, mid - 1, y, 0.9)   # the windpipe's hollow
        tone(f, mid, y, 0.9)
    tone_rect(f, 0, 0, f.w, 3, 0.7)   # the jaw's shadow
    round_all(faces, 0.85)
    tone_rect(faces["left"], 0, 0, faces["left"].w, 2, 0.75)
    tone_rect(faces["right"], 0, 0, faces["right"].w, 2, 0.75)


def chest(faces):
    """Her chest above the chiton: the collarbones' shadows, the hollow at her throat, a crack across the breastbone."""
    f = faces["front"]
    mid = f.w // 2
    tone_rect(f, mid - 2, 0, 4, 2, 0.75)
    line(f, 2, 2, mid - 3, 1, k=0.82)
    line(f, f.w - 3, 2, mid + 2, 1, k=0.82)
    for x, y in ((mid + 4, 2), (mid + 5, 3), (mid + 5, 4), (mid + 6, 5)):
        tone(f, x, y, 0.62)
    hline(f, f.h - 1, 0.8)
    weathered(faces)


def carved_folds(faces):
    """Drapery carved in stone: deep folds, lit on one flank and dark in the cut, deeper toward the hem."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            k = x % 5
            for y in range(f.h):
                deep = 0.8 if y > f.h * 0.55 else 0.9
                if k == 0:
                    tint(f, x, y, ROBE.c["fold"], 0.9)
                    tone(f, x, y, deep)
                elif k == 1:
                    tint(f, x, y, ROBE.c["fold"], 0.4)
                elif k == 3:
                    tone(f, x, y, 1.13)
        for _ in range(f.w // 5):
            tone(f, f.rng.randrange(f.w), 0, 0.7)   # chips along the neckline
        hline(f, f.h - 1, 0.75)


def fold(faces):
    f = faces["front"]
    vline(f, 0, 1.12)
    if f.w > 1:
        vline(f, f.w - 1, 0.8)
    grad(f, 1.05, 0.85)


def breast(faces):
    f = faces["front"]
    tuck(faces, 2, 0.72, 1.12)
    for x in range(0, f.w, 3):
        vline(f, x, 0.86, 1)


def shoulder(faces):
    tuck(faces, 3, 0.78, 1.1)
    weathered(faces)


def stump(faces):
    """Where the bust ends at the arm: rough broken stone."""
    for n in ("left", "right", "bottom"):
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                tone(f, x, y, 0.68 + f.rng.random() * 0.3)
        cracks(f, 2, 0.55)


def plinth_base(faces):
    for n in ("front", "back", "left", "right"):
        f = faces[n]
        hline(f, 0, 1.2)
        hline(f, f.h - 1, 0.7)
        cracks(f, 2, 0.65, 3)
    weathered(faces)


def meander(faces):
    """The plinth's band: a carved Greek key between two fillets."""
    for n in ("front", "back", "left", "right"):
        f = faces[n]
        hline(f, 0, 1.18)
        hline(f, f.h - 1, 0.72)
        if f.h >= 5:
            for x in range(1, f.w - 1):
                u = x % 6
                # a key 6 wide over rows 1..3: |‾‾ |_ _|
                if u == 0:
                    for y in (1, 2, 3):
                        tone(f, x, y, 0.62)
                elif u in (1, 2, 3):
                    tone(f, x, 1, 0.62)
                    if u == 3:
                        tone(f, x, 2, 0.62)
                elif u in (4, 5):
                    tone(f, x, 3, 0.62)
        for _ in range(max(1, f.w // 10)):
            tone(f, f.rng.randrange(f.w), f.rng.randrange(f.h), 0.6)   # chips


def moulding(faces):
    for n in ("front", "back", "left", "right"):
        f = faces[n]
        hline(f, 0, 1.22)
        if f.h > 1:
            hline(f, f.h - 1, 0.7)
        for x in range(0, f.w, 3):
            tone(f, x, f.h - 1, 0.8)


def brooch(faces):
    """A bronze disc with a coiled serpent on it, its eye a chip of green glass."""
    f = faces["front"]
    for x, y in ((0, 0), (f.w - 1, 0), (0, f.h - 1), (f.w - 1, f.h - 1)):
        f.px(x, y, (0, 0, 0), 0)
    f.glow(f.w // 2, f.h // 2 - 1, EYE_EDGE)


def coil(faces):
    """A wound serpent's body: a dark seam where each loop lies on the next."""
    for n in ("front", "back", "left", "right"):
        f = faces[n]
        for y in range(3, f.h, 5):
            hline(f, y, 0.55)
            if y + 1 < f.h:
                hline(f, y + 1, 1.15)


def coil_top(faces):
    f = faces["top"]
    for r in range(2, max(f.w, f.h), 5):
        for y in range(f.h):
            for x in range(f.w):
                d = max(abs(x - f.w / 2 + 0.5), abs(y - f.h / 2 + 0.5))
                if int(d) == r:
                    tone(f, x, y, 0.6)
    coil(faces)


def snake_head(faces):
    """A serpent's head (its snout is the stick's end): yellow eyes under dark brows on each side, a pale stripe
    down the crown."""
    for name in ("left", "right"):
        s = faces[name]
        ex = s.w // 2
        hline(s, 0, 0.75)
        s.px(ex, 0, hexc("141a10"))
        s.glow(ex, 1, SNAKE_EYE)
        s.px(ex + (1 if ex + 1 < s.w else -1), 1, hexc("141a10"))
    for n in ("front", "back"):
        f = faces[n]
        for y in range(0, f.h, 2):
            tone(f, f.w // 2, y, 1.25)
    tone_all(faces["top"], 0.85)


def snake_snout(faces):
    """The snout: nostrils on its end, the mouth's dark line round it, a fang at each corner."""
    t = faces["top"]
    t.px(0, 0, hexc("141a10"))
    t.px(t.w - 1, 0, hexc("141a10"))
    for name in ("left", "right"):
        s = faces[name]
        for y in range(s.h):
            s.px(s.w // 2, y, MOUTH)
        s.px(s.w // 2, 0, FANG)


# ----------------------------------------------------------------------------------------------- the serpents

# (root, first heading, curl in degrees per segment, segments), in the old model's pixels: the chain is the old one
# doubled, so every joint falls on the bone the animations already turn.
SNAKES = [
    ((-4.2, 21.0, -2.2), (-1.0, 0.5, -0.3), 30, 5),
    ((4.2, 21.0, -2.2), (1.0, 0.5, -0.3), -30, 5),
    ((-4.0, 19.0, 3.0), (-1.0, 0.2, 0.4), 34, 5),
    ((4.0, 19.0, 3.0), (1.0, 0.2, 0.4), -34, 5),
    ((0.0, 23.5, 3.5), (0.0, 0.8, 0.6), 28, 5),
    ((-2.4, 24.0, -2.4), (-0.4, 1.0, -0.3), -26, 5),
    ((2.4, 24.0, -2.4), (0.4, 1.0, -0.3), 26, 5),
    ((0.0, 24.6, 0.6), (0.0, 1.0, 0.1), 22, 5),
    ((-4.6, 16.5, 0.8), (-1.0, -0.5, 0.2), -38, 5),
    ((4.6, 16.5, 0.8), (1.0, -0.5, 0.2), 38, 5),
    ((-2.6, 24.0, 2.4), (-0.6, 0.9, 0.5), 32, 4),
    ((2.6, 24.0, 2.4), (0.6, 0.9, 0.5), -32, 4),
    ((-4.4, 22.6, 0.8), (-1.0, 0.8, 0.0), -24, 4),
    ((4.4, 22.6, 0.8), (1.0, 0.8, 0.0), 24, 4),
    ((-2.0, 18.0, 5.4), (-0.5, -0.8, 0.5), 36, 4),
    ((2.0, 18.0, 5.4), (0.5, -0.8, 0.5), -36, 4),
]


def serpent(b, i):
    root, heading, curl, segs = SNAKES[i]
    d = norm(heading)
    axis = cross(d, (0, 1, 0))
    axis = norm(axis) if math.sqrt(sum(v * v for v in axis)) > 0.3 else (1, 0, 0)
    curl = abs(curl)
    p = mul(root, 2)
    bone = f"snake{i}"
    big = i < 8
    widths = [6, 5, 4, 4] if big else [4, 3, 3]
    for k in range(segs):
        if k == 2:
            bone = f"snake{i}_mid"
            assert max(abs(b.pivot(bone)[j] - p[j]) for j in range(3)) < 0.05, bone
        if k == segs - 1:
            bone = f"snake{i}_tip"
            assert max(abs(b.pivot(bone)[j] - p[j]) for j in range(3)) < 0.05, bone
            # the head rears, turned a little back toward the front, its jaw hanging open, its tongue out
            hd = norm(add(d, (0, 0.3, -0.25)))
            w, ln, dp = (5, 5, 4) if big else (4, 4, 3)
            b.stick(bone, f"snake{i}_neck", p, add(p, mul(hd, 2.5)), w - 1, SERPENT, depth=dp - 1)
            h0 = add(p, mul(hd, 1.5))
            b.stick(bone, f"snake{i}_head", h0, add(h0, mul(hd, ln)), w, SERPENT, depth=dp, detail=snake_head)
            s0 = add(h0, mul(hd, ln - 0.5))
            b.stick(bone, f"snake{i}_snout", s0, add(s0, mul(hd, 2.5)), w - 2, SERPENT, depth=dp - 1, detail=snake_snout)
            t0 = add(s0, mul(hd, 2.5))
            b.stick(bone, f"snake{i}_tongue", t0, add(t0, mul(hd, 2)), 1, TONGUE, depth=1)
        else:
            w = widths[min(k, len(widths) - 1)]
            q = add(p, mul(d, 4.6 if big else 3.8))
            b.stick(bone, f"snake{i}_{k}", p, add(p, mul(d, (4.6 if big else 3.8) + 1.0)), w, SERPENT)
            p = q
        d = norm(turn(d, axis, curl * (1.3 if k < 2 else -1.1)))


# ----------------------------------------------------------------------------------------------- the model

def build():
    b = kit.Remake("medusa", tex_width=512)

    # the plinth: a base slab, a band carved with a Greek key, a moulding under the bust
    b.cube("bust", "plinth_base", (-22, 0, -14), (44, 2, 28), PLINTH, detail=plinth_base)
    b.cube("bust", "plinth", (-20, 2, -12), (40, 5, 24), PLINTH, detail=meander)
    b.cube("bust", "plinth_moulding", (-19, 6, -11), (38, 1, 22), PLINTH, detail=moulding)

    # the chest in a carved chiton, its folds falling from a brooch on her left shoulder
    b.cube("bust", "chiton", (-16, 6, -9), (32, 10, 18), ROBE, detail=carved_folds)
    b.cube("bust", "chiton_waist", (-15, 7, -10), (30, 4, 20), ROBE, detail=carved_folds)
    b.cube("bust", "chest", (-13, 15, -8), (26, 6, 16), STONE, detail=chest)
    b.cube("bust", "upper_back", (-12, 20, -1), (24, 3, 9), STONE, detail=weathered)
    b.cube("bust", "chiton_back", (-16, 15, 3), (32, 6, 6), ROBE, detail=carved_folds)
    b.stick("bust", "chiton_sash", (-13, 20.4, -8.6), (10, 6.8, -10.6), 4, ROBE, depth=1, detail=carved_folds)
    b.stick("bust", "chiton_sash_edge", (-10.5, 20.6, -9.4), (12, 7.4, -12.0), 1, ROBE, depth=1, detail=fold)
    b.stick("bust", "chiton_sash_back", (-13, 21.2, 7.2), (11, 6.8, 9.2), 6, ROBE, depth=2, detail=carved_folds)
    b.cube("bust", "brooch", (-16, 18, -10.5), (4, 4, 2), BRONZE, detail=brooch)
    b.cube("bust", "brooch_boss", (-15, 19, -11.2), (2, 2, 1), BRONZE)
    for k, x0 in enumerate((-12.5, -9.5, -6.5, -3.0, 0.5, 4.0, 7.0, 10.0, 12.8)):
        # the folds fall from the neckline, fanning a little toward the hem
        b.stick("bust", f"fold{k}", (x0, 15.8, -9.3), (x0 * 1.12, 6.4, -10.3), 2 if k % 3 == 0 else 1, ROBE, depth=1,
                detail=fold)
    for side, s in SIDES:
        x = side_x(s)
        # rounded shoulders, cut off above the arm as a bust is
        b.cube("bust", f"{side}_shoulder", (x(12, 7), 9, -6), (7, 10, 12), STONE, detail=shoulder)
        b.cube("bust", f"{side}_shoulder_cap", (x(11, 6), 18, -5), (6, 3, 10), STONE, detail=shoulder)
        b.cube("bust", f"{side}_shoulder_front", (x(12.5, 5), 11, -7), (5, 6, 2), STONE, detail=shoulder)
        b.cube("bust", f"{side}_arm_stump", (x(15, 4), 6.4, -5), (4, 7, 10), STONE, detail=stump)
        b.cube("bust", f"{side}_breast", (x(1.6, 8), 9.5, -10.6), (8, 6, 2), ROBE, detail=breast)
        b.cube("bust", f"{side}_breast_top", (x(2.6, 6), 15, -9.8), (6, 1, 1), ROBE, detail=breast)
        b.stick("bust", f"{side}_trapezius", (4.0 * s, 26.5, 1.2), (15.6 * s, 18.8, 0.4), 6, STONE, depth=10)
        b.stick("bust", f"{side}_collarbone", (1.8 * s, 21.2, -8.3), (12.8 * s, 20.2, -6.3), 2, STONE, depth=2)
        # the long muscle of her neck, from behind the ear to the throat
        b.stick("bust", f"{side}_neck_muscle", (4.6 * s, 30, 0.8), (1.6 * s, 21.2, -5.6), 2, STONE, depth=4)
    b.cube("bust", "neck", (-5, 20, -5), (10, 11, 10), STONE, detail=neck)
    b.cube("bust", "throat", (-2, 22, -5.6), (4, 6, 1), STONE)

    # the head
    b.cube("head", "cranium", (-8, 31, -8), (16, 16, 18), STONE, detail=weathered)
    b.cube("head", "upper_face", (-7, 30, -9.2), (14, 8, 2), STONE, detail=upper_face)
    b.cube("head", "forehead", (-7, 38, -9), (14, 7, 2), STONE, detail=forehead)
    for side, s in SIDES:
        x = side_x(s)
        b.stick("head", f"{side}_brow", (0.8 * s, 38.4, -9.9), (7.0 * s, 37.4, -9.5), 2, STONE, depth=2)
        b.cube("head", f"{side}_cheekbone", (x(3.4, 4), 30.6, -10.0), (4, 2, 2), STONE, detail=cheek)
        b.cube("head", f"{side}_cheek", (x(3.0, 4), 26.6, -9.6), (4, 4, 2), STONE, detail=cheek)
        b.stick("head", f"{side}_jaw", (6.4 * s, 32, 2), (3.6 * s, 25.2, -6.6), 2, STONE, depth=4)
        b.cube("head", f"{side}_temple", (x(7.2, 1), 32, -6), (1, 9, 8), STONE)
        b.cube("head", f"{side}_nostril", (x(1.2, 2), 30.4, -11.6), (2, 2, 2), STONE, detail=nose_tip)
    b.stick("head", "nose", (0, 38.2, -9.6), (0, 31.4, -11.9), 2, STONE, depth=3, detail=nose)
    b.cube("head", "nose_tip", (-1.5, 30.6, -13.0), (3, 2, 3), STONE, detail=nose_tip)
    b.cube("head", "upper_lip", (-3, 28.3, -10.2), (6, 1, 2), LIPS, detail=upper_lip)
    b.cube("head", "lower_lip", (-2, 26.6, -10.4), (4, 2, 2), LIPS, detail=lower_lip)
    b.cube("head", "lower_face", (-6, 25, -8.8), (12, 6, 14), STONE, detail=lower_face)
    b.cube("head", "chin", (-3, 23.4, -9.8), (6, 4, 3), STONE, detail=chin)
        # the coiled serpents her hair grows from: a cap wound over the skull, locks over the ears, a mass at the back
    b.cube("head", "coils_cap", (-9, 43, -7.5), (18, 5, 18), COILS, detail=coil_top)
    b.cube("head", "coils_crown", (-7, 47, -5.5), (14, 3, 14), COILS, detail=coil_top)
    b.cube("head", "coils_back", (-8.5, 28, 8), (17, 17, 5), COILS, detail=coil)
    b.cube("head", "coils_nape", (-6, 24, 6), (12, 5, 6), COILS, detail=coil)
    b.cube("head", "coils_fringe", (-8.5, 44, -9.6), (17, 3, 3), COILS, detail=coil)
    for side, s in SIDES:
        x = side_x(s)
        b.cube("head", f"{side}_coils", (x(7.6, 3), 29, -4), (3, 16, 14), COILS, detail=coil)
        b.cube("head", f"{side}_coils_lock", (x(7.0, 3), 31, -9.4), (3, 13, 4), COILS, detail=coil, rotation=(0, 0, -6 * s), origin=(8.5 * s, 44, -7.4))
        b.stick("head", f"{side}_coils_tress", (8.6 * s, 31, -7.5), (7.4 * s, 22.5, -7.0), 3, COILS, depth=3, detail=coil)
        b.stick("head", f"{side}_coil_band", (1.0 * s, 49.4, -8.5), (9.6 * s, 44.2, -7.5), 3, COILS, depth=3)
        b.stick("head", f"{side}_coil_band_back", (1.0 * s, 49.6, 8.5), (9.6 * s, 42.0, 10.5), 4, COILS, depth=3)
    # her open eyes: a part of their own, there only while she gazes
    for side, s in SIDES:
        x = side_x(s)
        b.cube("eyes", f"{side}_eye", (x(1.6, 5), 33.2, -9.7), (5, 3, 1), STONE, detail=open_eyes)
    for i in range(len(SNAKES)):
        serpent(b, i)
    b.finish()


if __name__ == "__main__":
    build()
