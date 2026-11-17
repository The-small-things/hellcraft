#!/usr/bin/env python3
"""The Fallen Seraph remade at twice the detail (drawn at half FallenSeraphEntity.SCALE). His old body is carried
over part for part with a finer grain (tools/art/legacy/fallen_seraph.py through remake_kit's port); new are his
head (a fine cold face: eyes of cold light in shadowed sockets, arched brows, a long straight nose, thin lips, the
dark crack of what he is becoming running from one eye to his jaw; a tarnished circlet, hair in separate locks),
hands with fingers, a sun embossed on his breastplate, and a second and third row of feathers over each of his six
wings, every feather with its quill and barbs.

    python3 tools/art/remake/fallen_seraph.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, mix, shade  # noqa: E402
from paintkit import ao, fill, fingers, hline, line, tint, tone, tone_all, tone_rect, tuck, vline  # noqa: E402

SKIN = material("skin", "skin", base="e8dcd0", dark="a89888")
HAIR = material("hair", "hair", light="fbf6e4", dark="c4b48c")
GOLD = material("gold", "metal", base="c8a040", high="f0d070", low="7a5a18")
TARNISH = material("tarnish", "metal", base="9a8040", high="d8c070", low="4a3818")
FEATHER = material("feather", "flat", base="ece8e0", edges=False)
EYE = hexc("8ad8ff")
EYE_HOT = hexc("e8fbff")
SOCKET = hexc("6a5a58")
ASH = hexc("4a4440")
SCORCH = hexc("2a2420")
CRACK = hexc("3a1a1a")
EMBER = hexc("c04a2a")
LIP = hexc("b89a90")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def face(fs):
    """A fine, cold face: shadowed sockets with eyes of cold light, hollow cheeks, and a crack of something dark
    running from his left eye down the cheek to the jaw, a dull red showing in its depths."""
    f = fs["front"]
    w = f.w
    tone_rect(f, 0, 0, w, 2, 0.8)
    for x0 in (1, w - 4):
        f.rect(x0, 4, 3, 3, SOCKET)
        f.glow(x0, 5, EYE)
        f.glow(x0 + 1, 5, EYE_HOT)
        f.glow(x0 + 2, 5, EYE)
        f.glow(x0 + 1, 4, EYE)
        tone_rect(f, x0, 7, 3, 1, 0.85)
    for y in range(7, 11):
        tone(f, 0, y, 0.82)
        tone(f, w - 1, y, 0.82)
        tone(f, 1, y, 0.92)
        tone(f, w - 2, y, 0.92)
    crack = ((w - 3, 7), (w - 3, 8), (w - 2, 8), (w - 2, 9), (w - 3, 10), (w - 3, 11), (w - 4, 11))
    for x, y in crack:
        f.px(x, y, CRACK)
    f.glow(w - 2, 9, EMBER)
    f.px(w - 4, 8, shade(CRACK, 1.6))
    f.px(w - 1, 9, shade(CRACK, 1.6))
    s = fs["left"]
    for x, y in ((0, 9), (1, 9), (1, 10), (2, 11)):
        s.px(x, y, CRACK)
    tuck(fs, 1, 0.8)


def brow(fs):
    tone_all(fs["bottom"], 0.6)
    hline(fs["front"], fs["front"].h - 1, 0.85)


def nose(fs):
    f = fs["front"]
    vline(f, f.w - 1, 0.85)
    for n in ("left", "right"):
        tone_all(fs[n], 0.86)
    tone_all(fs["bottom"], 0.7)


def lips(fs):
    f = fs["front"]
    fill(f, LIP, 0.03)
    hline(f, f.h - 1, 0.7)


def strands(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(f.w):
            vline(f, x, (0.86, 1.0, 1.06, 0.94)[(x + f.rng.randrange(2)) % 4])
        for x in range(f.w):
            cut = f.rng.choice((0, 0, 1, 2))
            for y in range(f.h - cut, f.h):
                f.px(x, y, (0, 0, 0), 0)
    tuck(fs, 2, 0.8)


def circlet(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for x in range(1, f.w, 3):
            tone(f, x, 0, 0.6)


def sun(fs):
    """Embossed on his breastplate: a sun with eight rays, its gold gone dull, a scorch across it."""
    f = fs["front"]
    cx, cy = f.w // 2, f.h // 2
    for dx, dy in ((0, 1), (1, 0), (1, 1), (1, -1)):
        for i in range(2, 5):
            for sgn in (1, -1):
                tone(f, cx + dx * i * sgn, cy + dy * i * sgn, 1.3 if i < 4 else 1.15)
    for y in range(cy - 1, cy + 2):
        for x in range(cx - 1, cx + 2):
            tone(f, x, y, 1.35)
    tone(f, cx, cy, 0.7)
    line(f, 1, f.h - 3, f.w - 2, 2, colour=SCORCH, t=0.45)


def plume(scorch):
    """A feather: a pale quill down its middle, barbs slanting from it, a rounded tip, singed toward the end."""
    def detail(fs):
        for n in ("front", "back"):
            f = fs[n]
            mid = f.w // 2
            for y in range(f.h):
                t = y / max(1, f.h - 1)
                for x in range(f.w):
                    k = 0.92 if (x + y) % 3 == 0 else 1.0
                    tone(f, x, y, k * (1.04 - 0.14 * t))
                    if t > 1 - scorch:
                        tint(f, x, y, ASH if f.rng.random() < 0.75 else SCORCH, 0.75 * (t - (1 - scorch)) / scorch)
                f.px(mid, y, mix((250, 248, 240), ASH, max(0.0, t - 0.6)))
                tone(f, 0, y, 0.84)
                tone(f, f.w - 1, y, 0.84)
            for x in (0, f.w - 1):
                f.px(x, f.h - 1, (0, 0, 0), 0)
            if f.rng.random() < 0.4:   # a gap where the barbs have parted
                gy = f.rng.randrange(f.h // 3, f.h - 1)
                f.px(f.rng.choice((0, f.w - 1)), gy, (0, 0, 0), 0)
    return detail


def finger(fs):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        hline(f, f.h // 2, 0.82)
        hline(f, f.h - 1, 0.85)


def build():
    b = kit.Remake("fallen_seraph", tex_width=512)
    b.port("fallen_seraph", skip=("head", "brow", "nose", "jaw", "right_fingers", "left_fingers", "right_hair", "left_hair"),
           also={"breastplate": sun})

    # the head
    b.cube("head", "head", (-5, 64, -5), (10, 12, 10), SKIN, detail=face)
    b.cube("head", "jaw", (-4, 62.8, -4.6), (8, 2, 8), SKIN, detail=ao(1, 0, 0.8))
    b.cube("head", "chin", (-2, 62.4, -5.3), (4, 3, 2), SKIN, detail=ao(1, 0, 0.8))
    for side, s in SIDES:
        b.cube("head", f"brow_{side}", (X(0.5, 4, s), 71.2, -5.7), (4, 1, 2), SKIN, rotation=(0, 0, -8 * s),
               origin=(0.5 * s, 71.7, -5), detail=brow)
        b.cube("head", f"cheekbone_{side}", (X(2.4, 2, s), 67.4, -5.5), (2, 1, 1), SKIN)
        b.cube("head", f"{side}_hair", (X(4.6, 2, s), 60, -5), (2, 14, 8), HAIR, detail=strands)
        b.cube("head", f"{side}_lock", (X(3.6, 2, s), 62, -5.9), (2, 11, 2), HAIR, detail=strands)
        b.cube("head", f"{side}_ear", (X(4.8, 1, s), 67, -1.5), (1, 3, 2), SKIN)
        b.cube("head", f"circlet_{side}", (X(5, 1, s), 73.4, -5.5), (1, 1, 11), TARNISH, detail=circlet)
    b.stick("head", "nose", (0, 71.6, -5.2), (0, 67.4, -6.6), 2, SKIN, depth=2, detail=nose)
    b.cube("head", "nose_tip", (-1, 66.8, -6.9), (2, 1, 2), SKIN, detail=nose)
    b.cube("head", "lips", (-2, 65.2, -5.3), (4, 1, 1), SKIN, detail=lips)
    b.cube("head", "circlet", (-5.5, 73.4, -6), (11, 1, 1), TARNISH, detail=circlet)
    b.cube("head", "circlet_gem", (-1, 73, -6.5), (2, 2, 1), GOLD)
    b.cube("head", "hair_fringe", (-5, 74, -6.2), (10, 3, 2), HAIR, detail=strands)
    b.cube("head", "hair_fall", (-4, 52, 3.4), (8, 8, 2), HAIR, detail=strands)

    # hands with fingers
    for side, s in SIDES:
        for k, z in enumerate((-2, -1, 0, 1)):
            b.cube(f"{side}_arm", f"{side}_finger_{k}", (X(9, 3, s), 30.5 + (0.6 if k in (0, 3) else 0), z), (3, 3, 1), SKIN,
                   detail=finger)

    # two more rows of feathers over each wing: shorter ones lapping the long flight feathers, front and back
    for side, s in SIDES:
        for i, (y, w, h) in enumerate(((30, 16, 7), (26, 18, 8), (21, 14, 7))):
            wing = f"{side}_wing{i}"
            top = 2 * (y + 2)
            n = w - 5
            for row, (drop, length0, z) in enumerate(((2, 2 * h - 6, 3.4), (5, 2 * h - 3, 3.0))):
                for k in range(n):
                    t = k / max(1, n - 1)
                    fx = 5 + k * (2 * w - 12) / max(1, n - 1)
                    length = length0 + int(round(4 * t))
                    tilt = (-6 + 20 * t) * s
                    zz = z + (0.3 if k % 2 else 0)
                    b.cube(wing, f"{side}_wing{i}_plume{row}_{k}", (X(fx, 3, s), top - drop - length, zz), (3, length, 1),
                           FEATHER, rotation=(0, 0, tilt), origin=((fx + 1.5) * s, top - drop, zz),
                           detail=plume(0.2 + 0.3 * t))
    b.finish()


if __name__ == "__main__":
    build()
