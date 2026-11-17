#!/usr/bin/env python3
"""Geryon remade at twice the detail (drawn at half his old scale): the just man's face with calm blue eyes, a straight
nose, a moustache and a long beard in tiers, on a serpent's trunk rounded in three layers of fine blue-black scales,
painted with gold nooses (each with its knot and trailing cord) and red circlets with glowing hearts, a pale plated
belly, hairy paws with knuckled toes and hooked claws, dorsal spines down the hind body and a scorpion's ringed bulb
with a forked sting. His skeleton and animations are the old ones (riders still sit on seg1 and seg2).

    python3 tools/art/remake/geryon.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, mix, shade  # noqa: E402
from paintkit import ao, fill, hline, line, tint, tone, tone_all, tone_rect, tuck, vline  # noqa: E402

FACE = material("face", "skin", base="c8a888", dark="8a6a50")
HAIR = material("hair", "hair", light="b0a490", dark="4a4036")
FUR = material("fur", "hair", light="9a7a44", dark="3a2a14")
MANE = material("mane", "hair", light="7a5a2e", dark="2a1c0c")
CLAW = material("claw", "horn", root="3a3028", tip="0e0a08")
STING = material("sting", "horn", root="4a1a2a", tip="0a0406")
SERPENT = material("serpent", "flat", base="2a3a5a")
PAD = material("pad", "flat", base="2a1e18")
BODY = hexc("2a3a5a")
BODY_DARK = hexc("1a2440")
BODY_LIGHT = hexc("4a6a9a")
BELLY = hexc("c8b880")
KNOT = hexc("d8a83a")
RING = hexc("b8382a")
RING_LIGHT = hexc("f05a3a")
EYE = hexc("8ac8ff")
WHITE = hexc("e8e0d8")
LIP = hexc("9a6a58")
VENOM = hexc("7ac04a")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def circle(f, cx, cy, r, colour, glow=False):
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r - 1, cx + r + 2):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if abs(d - r) < 0.55:
                (f.glow if glow else f.px)(x, y, colour)


# ----------------------------------------------------------------------------------------------- details

def painted(faces):
    """Nooses and circlets on a blue-black hide of small overlapping scales: gold loops, each with its knot and cord
    trailing, red rings whose hearts glow faintly, and a pale belly of broad plates underneath."""
    for name, f in faces.items():
        light = 1.12 if name == "top" else 0.72 if name == "bottom" else 1.0
        if name == "bottom":
            for y in range(f.h):
                for x in range(f.w):
                    c = BELLY if y % 5 else shade(BELLY, 0.65)
                    if y % 5 == 1:
                        c = shade(BELLY, 1.1)
                    edge = min(x, f.w - 1 - x)
                    f.px(x, y, shade(c, (0.7 if edge == 0 else 0.86 if edge == 1 else 1.0) * (1.0 + (f.rng.random() - 0.5) * 0.06)))
            continue
        for y in range(f.h):
            for x in range(f.w):
                # scales: rows of 4 x 3, every other row shifted by 2
                row = y // 3
                sx = (x + (2 if row % 2 else 0)) % 4
                sy = y % 3
                c = BODY
                if sx == 0 or sy == 2:
                    c = BODY_DARK
                elif sy == 0 and sx == 2:
                    c = mix(BODY, BODY_LIGHT, 0.5)
                g = 1.0
                if name not in ("top", "bottom") and f.h > 1:
                    g = 1.1 - 0.3 * y / (f.h - 1)
                f.px(x, y, shade(c, light * g * (1.0 + (f.rng.random() - 0.5) * 0.08)))
        if f.w < 9 or f.h < 9:
            continue
        rows = [f.h // 2] if f.h < 22 else [f.h // 3 - 1, 2 * f.h // 3 + 1]
        for ri, cy in enumerate(rows):
            for j, cx in enumerate(range(6 + 6 * (ri % 2), f.w - 5, 12)):
                if (j + ri + len(name)) % 2 == 0:
                    circle(f, cx, cy, 3, KNOT)                      # a noose
                    f.px(cx, cy - 3, shade(KNOT, 1.25))
                    f.rect(cx + 2, cy + 2, 2, 2, shade(KNOT, 0.75))  # its knot
                    for i in range(4):                               # and the cord trailing from it
                        f.px(cx + 4 + i, cy + 4 + (i + 1) // 2, shade(KNOT, 0.85 - 0.08 * i))
                else:
                    circle(f, cx, cy, 3, shade(RING, 0.6))
                    circle(f, cx, cy, 2, RING)
                    f.glow(cx, cy, RING_LIGHT)
                    f.glow(cx - 1, cy, shade(RING_LIGHT, 0.8))
                    f.glow(cx, cy - 1, shade(RING_LIGHT, 0.8))
    for name in ("left", "right", "front", "back"):
        hline(faces[name], 0, 1.15)


def kindly(faces):
    """A just man's face: calm eyes with a cold blue gleam under an even brow, lines at their corners, high
    cheekbones, and a gentle mouth over the beard."""
    f = faces["front"]
    w, h = f.w, f.h
    dark = shade(FACE.c["dark"], 0.8)
    for x in range(1, w - 1):
        tone(f, x, 2, 0.9)   # a line across the forehead
    for x0 in (1, w - 6):
        tone_rect(f, x0, 5, 5, 1, 0.6)          # the lid's shadow
        for x in range(x0, x0 + 5):
            f.px(x, 6, WHITE)
            f.px(x, 7, shade(WHITE, 0.9))
        ix = x0 + 2 if x0 == 1 else x0 + 1
        for y in (6, 7):
            f.glow(ix, y, EYE)
            f.glow(ix + 1, y, EYE)
        f.px(ix, 7, hexc("203a5a"))
        tone_rect(f, x0, 8, 5, 1, 0.85)
        f.px(x0, 6, dark)
        f.px(x0 + 4, 6, dark)
    for y in (6, 7, 8):                          # crow's feet
        tone(f, 0, y, 0.8)
        tone(f, w - 1, y, 0.8)
    for x in (1, 2, w - 3, w - 2):                # cheekbones lit, hollows under them
        tone(f, x, 10, 1.1)
        tone(f, x, 12, 0.85)
        tone(f, x, 13, 0.85)
    line(f, 4, 10, 3, 13, k=0.8)
    line(f, w - 5, 10, w - 4, 13, k=0.8)
    for name in ("left", "right"):
        s = faces[name]
        vline(s, s.w - 1 if name == "right" else 0, 0.88)
    tuck(faces, 2, 0.8)


def brow(faces):
    tone_all(faces["bottom"], 0.55)
    hline(faces["front"], faces["front"].h - 1, 0.8)


def nose(faces):
    f = faces["front"]
    vline(f, 0, 1.1)
    vline(f, f.w - 1, 0.85)
    for n in ("left", "right"):
        tone_all(faces[n], 0.85)
    b = faces["bottom"]
    tone_all(b, 0.6)
    b.px(0, 0, shade(FACE.c["dark"], 0.4))
    b.px(b.w - 1, 0, shade(FACE.c["dark"], 0.4))


def lips(faces):
    f = faces["front"]
    fill(f, LIP, 0.04)
    hline(f, f.h - 1, 0.7)


def hair_detail(faces):
    """Long hair: strands running down it, darker underneath."""
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        for x in range(f.w):
            k = (0.82, 1.0, 1.08, 0.92)[(x + f.rng.randrange(2)) % 4]
            vline(f, x, k)
    tuck(faces, 3, 0.7)


def beard(faces):
    """The beard: streaked, and tapering ragged at its end."""
    hair_detail(faces)
    for name in ("front", "left", "right", "back"):
        f = faces[name]
        for x in range(f.w):
            cut = f.rng.choice((0, 0, 1, 2))
            for y in range(f.h - cut, f.h):
                f.px(x, y, (0, 0, 0), 0)


def fur(faces):
    tuck(faces, 3, 0.7, 1.1)


def ragged_fur(faces):
    for name in ("front", "back", "left", "right"):
        f = faces[name]
        x = 0
        while x < f.w:
            wide = f.rng.choice((1, 2, 2))
            cut = f.rng.choice((0, 1, 2, 3, 4))
            for xx in range(x, min(f.w, x + wide)):
                for y in range(f.h - cut, f.h):
                    f.px(xx, y, (0, 0, 0), 0)
            x += wide
    tuck(faces, 3, 0.7)


def toe(faces):
    tuck(faces, 1, 0.7, 1.1)
    t = faces["top"]
    hline(t, t.h // 2, 0.75)


def sting(faces):
    """The venomous fork: glossy, with a wet sheen."""
    for name in ("front", "left", "right", "top"):
        f = faces[name]
        vline(f, 0, 1.3)
        if f.w > 2:
            vline(f, 1, 1.12)


def venom(faces):
    for f in faces.values():
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, VENOM if (x + y) % 2 else shade(VENOM, 0.8))


def telson(faces):
    """The swollen venom bulb: ringed, with veins of a sickly green."""
    for name in ("left", "right", "top", "bottom", "back"):
        f = faces[name]
        for x in range(2, f.w, 4):
            vline(f, x, 0.7)
        for x in range(0, f.w, 3):
            tint(f, x, f.h // 2, hexc("4a7a2a"), 0.8)
            tint(f, x + 1, f.h // 2 + 1, hexc("4a7a2a"), 0.6)
    tuck(faces, 2, 0.7, 1.2)


# ----------------------------------------------------------------------------------------------- the model

def length_of(b, name, spines=False):
    """One length of the body, rounded in three layers; dorsal spines behind the riders' seats."""
    b.keep(name, SERPENT, detail=painted)
    b.keep(f"{name}_round", SERPENT, detail=painted)
    o = b.old[name]
    w, lz = o["size"][0], o["size"][2]
    cy = o["frm"][1] + o["size"][1] / 2
    b.cube(o["bone"], f"{name}_bevel", (-(w - 2) / 2, cy - (w - 2) / 2, o["frm"][2] + 0.5), (w - 2, w - 2, lz - 1), SERPENT,
           detail=painted)
    if spines:
        top = cy + w / 2
        for i, dz in enumerate(range(3, lz - 4, 5)):
            z = o["frm"][2] + dz
            h = 5 if i % 2 == 0 else 4
            b.cube(o["bone"], f"{name}_spine_{i}", (-1, top - 1.5, z), (2, h, 4), STING, rotation=(-28, 0, 0),
                   origin=(0, top, z + 2), detail=sting)
            b.cube(o["bone"], f"{name}_spine_tip_{i}", (-0.5, top + h - 2.5, z + 1.6), (1, 3, 2), STING,
                   rotation=(-28, 0, 0), origin=(0, top, z + 2))


def build():
    b = kit.Remake("geryon", tex_width=512)

    length_of(b, "chest")
    b.keep("mane", MANE, grow=(1, 0, 0), detail=ragged_fur)
    b.cube("chest", "mane_ruff", (-8, 14, -43), (16, 14, 4), MANE, detail=ragged_fur)
    b.cube("chest", "mane_top", (-7, 38, -42), (14, 5, 14), MANE, detail=ragged_fur)
    for side, s in SIDES:
        b.cube("chest", f"mane_{side}", (X(10.5, 3, s), 20, -42), (3, 18, 12), MANE, detail=ragged_fur)

    b.keep("neck", FACE, detail=fur)
    b.keep("nape_hair", HAIR, detail=hair_detail)

    # the head: the face of a just man
    b.keep("head", FACE, detail=kindly)
    for side, s in SIDES:
        b.cube("head", f"brow_{side}", (X(0.5, 6, s), 44.6, -61.2), (6, 2, 2), HAIR, rotation=(0, 0, -5 * s),
               origin=(0.5 * s, 45.6, -60), detail=brow)
        b.cube("head", f"cheekbone_{side}", (X(3, 3, s), 39.6, -60.8), (3, 2, 1), FACE, detail=ao(1, 0, 0.82))
        b.cube("head", f"moustache_{side}", (X(0.3, 5, s), 36.4, -61.8), (5, 2, 2), HAIR, rotation=(0, 0, 12 * s),
               origin=(0.3 * s, 38, -61))
        b.keep(f"{side}_locks", HAIR, detail=beard)
        b.cube("head", f"{side}_locks_front", (X(6, 2, s), 38, -60), (2, 11, 4), HAIR, detail=beard)
        b.keep(f"{side}_ear", FACE, grow=(0, 0, 0.5))
    b.stick("head", "nose", (0, 45.4, -60.6), (0, 39.6, -62.8), 2, FACE, depth=2, detail=nose)
    b.cube("head", "nose_tip", (-1.5, 38.6, -63.4), (3, 2, 3), FACE, detail=nose)
    b.cube("head", "lips", (-2, 35.6, -60.6), (4, 1, 1), FACE, detail=lips)
    b.keep("hair", HAIR, grow=(1, 0, 0), detail=hair_detail)
    b.cube("head", "hair_fringe", (-7, 47.5, -61.6), (14, 3, 2), HAIR, detail=beard)
    b.keep("hair_back", HAIR, detail=hair_detail)
    b.keep("beard", HAIR, detail=beard)
    b.keep("beard_mid", HAIR, detail=beard)
    b.keep("beard_tip", HAIR, detail=beard)
    b.cube("head", "beard_end", (-2, 17.5, -59.4), (4, 4, 3), HAIR, detail=beard)

    # the paws, hairy to the armpits
    for side, s in SIDES:
        paw = f"{side}_paw"
        b.keep(f"{side}_shoulder", FUR, grow=(1, 0, 0), detail=ragged_fur)
        b.keep(f"{side}_leg", FUR, detail=fur)
        b.keep(f"{side}_elbow_tuft", MANE, detail=ragged_fur)
        b.cube(paw, f"{side}_forearm_fur", (X(5.4, 9, s), 4, -39), (9, 7, 10), FUR, detail=ragged_fur)
        b.keep(f"{side}_wrist", FUR, detail=fur)
        b.keep(f"{side}_claws", FUR, detail=fur)
        b.cube(paw, f"{side}_pad", (X(6, 8, s), -5.4, -41), (8, 1, 9), PAD)
        for i in range(4):
            x0 = 5 + i * 2.66
            b.cube(paw, f"{side}_toe_{i}", (X(x0, 2, s), -5, -45.5), (2, 4, 5), FUR, detail=toe)
            b.cube(paw, f"{side}_claw_{i}", (X(x0 + 0.5, 1, s), -4.4, -48.5), (1, 2, 4), CLAW, rotation=(-28, 0, 0),
                   origin=(X(x0 + 0.5, 1, s) + 0.5, -3, -45.5))
        b.cube(paw, f"{side}_dewclaw", (X(13.6, 1, s), -2, -37), (1, 3, 2), CLAW)

    for name in ("seg1", "seg2"):
        length_of(b, name)
    for name in ("seg3", "seg4", "tail"):
        length_of(b, name, spines=True)

    # the scorpion's end: a ringed bulb, a hooked neck, a fork of two barbs, venom beading at their points
    b.keep("sting_base", STING, detail=telson)
    b.cube("sting", "sting_base_round", (-4, 22, 94), (8, 12, 10), STING, detail=telson)
    b.keep("sting_neck", STING, detail=telson)
    b.keep("sting_hook", STING, detail=sting)
    for side, s in SIDES:
        b.keep(f"sting_barb_{side}", STING, detail=sting)
        b.keep(f"sting_point_{side}", STING, detail=sting)
        o = b.old[f"sting_point_{side}"]
        b.cube("sting", f"sting_venom_{side}", (o["frm"][0] + 0.5, o["frm"][1] - 1, o["frm"][2] + 0.5), (1, 1, 1), STING,
               rotation=o["rotation"], origin=o["origin"], detail=venom)
    b.finish()


if __name__ == "__main__":
    build()
