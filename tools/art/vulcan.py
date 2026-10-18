#!/usr/bin/env python3
"""Vulcan, smith of the gods (Inferno XIV: "if he weary his smiths one after another at the black forge of
Mongibello"), at the Great Forge of Dis: a huge lame smith, soot-black skin flecked with embers, a leather apron, an
iron brace on his crippled left leg, a smouldering beard and eyes like coals, his great hammer in his right hand and a
coil of chain in his left.

Run from the repo root:  python3 tools/art/vulcan.py && python3 tools/gen_bosses.py

The animations' events are what the server times his fight by (VulcanFight): "flash" opens the window to block his
hammer and "hit" is when it lands; "leap" and "land" his Hammerfall; "pour" his molten rain; "throw" his chains; "ring"
the anvil-blow that calls his smiths; "plunge" and "roar" his rage at half health; "clang" and "roar" his entrance at
the anvil; "fall" his death.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="5e4032", dark="2e1c14")
LEATHER = material("leather", "robe", base="5a3a20", fold="3a2410", trim="8a6a3a")
STRAP = material("strap", "flat", base="3e2814")
CLOTH = material("cloth", "robe", base="3a3230", fold="221c1a")
FELT = material("felt", "robe", base="6a2a1a", fold="42180e")
BEARD = material("beard", "hair", light="5e4638", dark="1a100a")
IRON = material("iron", "metal", base="3e3e44", high="6e6e78", low="1e1e22")
HAMMER = material("hammer", "metal", base="2a2a30", high="5a5a64", low="121216")
HAFT = material("haft", "flat", base="6a4a2a")
EMBER = hexc("ff7a1a")
EMBER_HOT = hexc("ffd060")
EYE = hexc("ff5a10")
EYE_HOT = hexc("ffe080")
SOCKET = hexc("140a06")
SCAR = hexc("7a5444")
SIDES = ("front", "back", "left", "right")


# ----------------------------------------------------------------------------------------------- details

def colour(f, x, y):
    """The colour already painted at (x, y) of a face, or None off the face."""
    if 0 <= x < f.w and 0 <= y < f.h:
        return f.canvas.get(f.x + x, f.y + y)[:3]
    return None


def darken(f, x, y, k):
    c = colour(f, x, y)
    if c is not None:
        f.px(x, y, shade(c, k))


def both(*details):
    """Several detail painters on one cube, in order."""
    def apply(faces):
        for d in details:
            d(faces)
    return apply


def rounded(edge=0.8, low=0.82, names=SIDES):
    """Shades a box as if it were round: its sides darken toward their edges (where a round form turns away from the
    light) and toward the bottom (the shadow where it meets what is under it)."""
    def apply(faces):
        for n in names:
            f = faces[n]
            for y in range(f.h):
                v = y / max(1, f.h - 1)
                for x in range(f.w):
                    u = abs(2 * x / max(1, f.w - 1) - 1)
                    darken(f, x, y, (1 - (1 - edge) * u ** 2) * (1.04 - (1.04 - low) * v ** 2))
    return apply


def soot(faces):
    """Soot smeared over the skin in dark blotches, thickest low down."""
    for n in SIDES:
        f = faces[n]
        for _ in range(max(1, f.w * f.h // 25)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                darken(f, x + dx, y + dy, 0.7)


def heat(cracks=1.0):
    """Cracks of the forge's heat under his skin: thin glowing seams that wander across it, white-hot where they
    meet, with a few loose embers. They glow in the dark."""
    def apply(faces):
        for n in SIDES:
            f = faces[n]
            for _ in range(int(max(1, f.w * f.h / 70) * cracks)):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                for i in range(f.rng.randint(3, 7)):
                    f.glow(x, y, EMBER_HOT if i == 0 else EMBER if f.rng.random() < 0.75 else shade(EMBER, 0.7))
                    if f.rng.random() < 0.5:
                        x += f.rng.choice((-1, 1))
                    else:
                        y += f.rng.choice((-1, 1))
            for _ in range(max(1, f.w * f.h // 60)):
                f.glow(f.rng.randrange(f.w), f.rng.randrange(f.h), EMBER)
    return apply


def scars(faces):
    """Old burns and scars: pale ridged lines."""
    for n in ("front", "left", "right"):
        f = faces[n]
        for _ in range(max(1, f.w // 8)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            for i in range(f.rng.randint(2, 5)):
                f.px(x + i, y + i // 2, SCAR)


def face(faces):
    """A scowling smith's face: eyes like coals deep under the brow, hollow cheeks, a nose broken long ago."""
    f = faces["front"]
    w = f.w
    for cx in (1, w - 4):
        for dx in range(3):
            f.px(cx + dx, 3, SOCKET)
            f.px(cx + dx, 5, shade(SKIN.c["dark"], 0.8))
        f.glow(cx, 4, EYE)
        f.glow(cx + 1, 4, EYE_HOT)
        f.px(cx + 2, 4, SOCKET)
    for y in (6, 7):  # hollow cheeks
        darken(f, 1, y, 0.75)
        darken(f, w - 2, y, 0.75)
    for x in range(1, w - 1):  # the furrowed forehead
        if x % 3:
            darken(f, x, 1, 0.8)
    f.px(2, 0, SCAR)
    f.px(3, 1, SCAR)


def brow(faces):
    f = faces["bottom"]
    f.fill(lambda x, y: SOCKET)
    for n in ("front",):
        g = faces[n]
        for x in range(g.w):
            darken(g, x, g.h - 1, 0.6)


def beard(faces):
    """A beard in thick dark ropes, lighter where they catch the forge's glow."""
    for name in ("front", "left", "right", "back"):
        f = faces[name]
        for x in range(f.w):
            if x % 2 == 0:
                for y in range(f.h - 1):
                    darken(f, x, y, 0.8)
            elif f.h > 2:
                f.px(x, f.rng.randrange(f.h), BEARD.c["light"])


def smoulder(faces):
    """The beard's tips smoulder."""
    for name in ("front", "left", "right", "back"):
        f = faces[name]
        for x in range(f.w):
            if (x * 3) % 4 == 0:
                f.glow(x, f.h - 1, EMBER)
            if (x * 5) % 7 == 0 and f.rng.random() < 0.5:
                f.glow(x, f.h - 2, shade(EMBER, 0.8))


def mouth(faces):
    """The mouth, a grim line in the beard under the moustache."""
    f = faces["front"]
    for x in range(2, f.w - 2):
        f.px(x, 1, SOCKET)
    for x in (1, f.w - 2):
        f.px(x, 2, SOCKET)


def chest(faces):
    """The great slabs of his chest, the breastbone between them, and his ribs under the arms."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(1, f.h - 2):
        darken(f, mid, y, 0.72)
        darken(f, mid - 1, y, 0.85)
    for x in range(1, f.w - 1):
        darken(f, x, f.h - 3, 0.75)  # the lower edge of the pectorals
        darken(f, x, f.h - 2, 0.85)
    for n in ("left", "right"):
        g = faces[n]
        for y in range(3, g.h - 1, 2):
            for x in range(2, g.w - 2):
                darken(g, x, y, 0.84)


def abs_detail(faces):
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        darken(f, mid, y, 0.78)
    for y in (2, 5):
        for x in range(2, f.w - 2):
            darken(f, x, y, 0.82)


def muscle(faces):
    """The split between two muscles down the front and back of a limb."""
    for n in ("front", "back"):
        f = faces[n]
        for y in range(1, f.h - 1):
            darken(f, f.w // 2, y, 0.8)


def veins(faces):
    """Veins standing out on a smith's forearm."""
    for n in ("front", "right", "left"):
        f = faces[n]
        x = f.rng.randrange(1, max(2, f.w - 1))
        for y in range(f.h):
            f.px(x, y, shade(SKIN.c["base"], 1.25))
            if f.rng.random() < 0.35:
                x = max(0, min(f.w - 1, x + f.rng.choice((-1, 1))))


def apron(faces):
    """Thick leather, scorched and spattered with burns, stitched round its edge."""
    f = faces["front"]
    for _ in range(f.w * f.h // 18):
        x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
        f.px(x, y, shade(LEATHER.c["fold"], 0.45))
        darken(f, x + 1, y, 0.7)
    for y in range(1, f.h - 1, 2):
        f.px(1, y, LEATHER.c["trim"])
        f.px(f.w - 2, y, LEATHER.c["trim"])
    for x in range(1, f.w - 1, 2):
        f.px(x, f.h - 2, LEATHER.c["trim"])


def buckle(faces):
    f = faces["front"]
    mid = f.w // 2
    for y in range(f.h):
        f.px(mid - 2, y, IRON.c["high"])
        f.px(mid + 1, y, IRON.c["high"])
    f.px(mid - 1, 0, IRON.c["high"])
    f.px(mid, 0, IRON.c["high"])
    f.px(mid - 1, f.h - 1, IRON.c["high"])
    f.px(mid, f.h - 1, IRON.c["high"])


def stitched(faces):
    """A leather strap with a row of stitches down it."""
    for n in SIDES:
        f = faces[n]
        if f.w >= 3:
            for y in range(0, f.h, 2):
                f.px(f.w // 2, y, LEATHER.c["trim"])


def bands(faces):
    """A boot or bracer laced with straps."""
    for n in SIDES:
        f = faces[n]
        for y in range(1, f.h, 3):
            for x in range(f.w):
                f.px(x, y, STRAP.c["base"])
                if x % 3 == 1:
                    f.px(x, y, IRON.c["high"])


def knuckles(faces):
    f = faces["front"]
    for x in range(f.w):
        for y in range(f.h):
            if y in (1, 3):
                darken(f, x, y, 0.72)
    for x in range(0, f.w, 2):
        f.px(x, 0, shade(SKIN.c["base"], 1.15))


def hammer_face(faces):
    """The hammer's striking face glows white-hot, cooling to red at its rim."""
    f = faces["front"]
    for y in range(f.h):
        for x in range(f.w):
            rim = x in (0, f.w - 1) or y in (0, f.h - 1)
            f.glow(x, y, EMBER if rim else EMBER_HOT if (x + y) % 3 else hexc("fff4c0"))
    for n in ("left", "right", "top", "bottom"):
        g = faces[n]
        for y in range(g.h):
            for x in range(g.w):
                g.glow(x, y, EMBER)


def hammer_body(faces):
    """The head: dark iron, the heat creeping back from the face along it, hammer marks on its cheeks."""
    for n in ("left", "right", "top", "bottom"):
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                # the front of the head is toward the right edge of the right face, the left edge of the left face, and
                # the bottom row of the top
                t = x / max(1, f.w - 1) if n == "right" else 1 - x / max(1, f.w - 1) if n == "left" else (
                    y / max(1, f.h - 1) if n == "top" else 1 - y / max(1, f.h - 1))
                if t > 0.85:
                    f.glow(x, y, shade(EMBER, 0.7))
                elif t > 0.7 and (x + y) % 2:
                    f.px(x, y, hexc("5a1a0a"))
        for _ in range(3):
            darken(f, f.rng.randrange(f.w), f.rng.randrange(f.h), 0.7)


def wrap(faces):
    """Leather wound round the haft."""
    for n in SIDES:
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 3 == 0:
                    f.px(x, y, STRAP.c["base"])


def grain(faces):
    """The haft's wood grain, scorched black near the head."""
    for n in SIDES:
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if (y + x * 5) % 4 == 0:
                    darken(f, x, y, 0.8)
                if y > f.h - 5:
                    darken(f, x, y, 0.55 + 0.1 * (f.h - 1 - y))


def links(faces):
    """A chain: links alternately flat (a ring round a dark hole) and edge-on (a bar)."""
    for n in SIDES:
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if y % 4 in (1, 2) and n in ("front", "back") and 0 < x < f.w - 1:
                    f.px(x, y, IRON.c["low"])
                elif y % 4 == 3:
                    f.px(x, y, shade(IRON.c["low"], 0.7) if x in (0, f.w - 1) else IRON.c["high"])


def ingot(faces):
    """A bar of iron glowing yellow-hot in the tongs' jaws."""
    for n in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EMBER_HOT if (x + y) % 2 else EMBER)


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("vulcan", texture_width=256)
    hips = m.bone("hips", (0, 26, 0))
    # breeches of soot-black linen, a broad belt, and the heavy apron hanging to mid-thigh
    hips.cube("pelvis", (-8.5, 17, -4.5), (17, 10, 9), CLOTH, detail=rounded(0.8, 0.8))
    hips.cube("belt", (-9, 25, -5.5), (18, 3, 11), STRAP, inflate=0.2, detail=stitched)
    hips.cube("belt_buckle", (-2.5, 24.5, -6.2), (5, 4, 1), IRON, detail=buckle)
    hips.cube("apron_skirt", (-7.5, 14, -6.8), (15, 12, 1), LEATHER, detail=apron)
    hips.cube("apron_hem", (-7, 13, -7.2), (14, 2, 1), LEATHER)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        lame = s == -1
        thigh = hips.bone(f"{side}_thigh", (4.5 * s, 22, 0))
        # the thigh tapers to the knee; the crippled left one is a little withered
        tw = 7 if not lame else 6
        thigh.cube(f"{side}_thigh", (x(1 if not lame else 1.5, tw), 15, -4), (tw, 8, 8), CLOTH, mirror=mirror,
                   detail=rounded(0.75, 0.85))
        thigh.cube(f"{side}_knee", (x(1.5, 6), 11, -3.5), (6, 5, 7), CLOTH, mirror=mirror, detail=rounded(0.8, 0.8))
        thigh.cube(f"{side}_kneecap", (x(2.5, 4), 11.5, -4.3), (4, 3, 1), STRAP, mirror=mirror)
        shin = thigh.bone(f"{side}_shin", (4.5 * s, 12, 0))
        # a calf, then a laced leather boot with a heel and a scorched toe-cap
        shin.cube(f"{side}_shin", (x(1.5, 6), 7, -3), (6, 6, 6), SKIN, mirror=mirror, detail=both(soot, rounded(0.75, 0.9)))
        shin.cube(f"{side}_calf", (x(2, 5), 6, 1.5), (5, 6, 2), SKIN, mirror=mirror, detail=rounded(0.75, 0.85))
        turn = (0, 18 * s, 0) if lame else (0, 0, 0)  # the lame foot turns out
        shin.cube(f"{side}_boot", (x(1, 7), 2, -3.5), (7, 6, 7), LEATHER, mirror=mirror, detail=bands)
        shin.cube(f"{side}_foot", (x(1, 7), 0, -5.5), (7, 3, 9), LEATHER, rotation=turn, origin=(4.5 * s, 1, 0), mirror=mirror,
                  detail=rounded(0.85, 0.7))
        shin.cube(f"{side}_toecap", (x(1.5, 6), 0, -6.5), (6, 2, 1), IRON, rotation=turn, origin=(4.5 * s, 1, 0), mirror=mirror)
        if lame:
            # the crippled leg in its iron brace: bands at the shin and thigh, bars down both sides, a stirrup
            shin.cube("brace", (x(1, 7), 8, -3.5), (7, 2, 7), IRON, inflate=0.3, mirror=True)
            shin.cube("brace_bar", (x(7.5, 1), 2, -1), (1, 16, 2), IRON, mirror=True)
            shin.cube("brace_bar_inner", (x(0.5, 1), 4, -1), (1, 12, 2), IRON, mirror=True)
            shin.cube("brace_stirrup", (x(0.5, 8), 0, -1), (8, 1, 2), IRON, inflate=0.1, mirror=True)
            thigh.cube("brace_upper", (x(1, 7), 16, -4), (7, 2, 8), IRON, inflate=0.3, mirror=True)
            thigh.cube("brace_hinge", (x(7.6, 1), 11, -1.5), (1, 3, 3), IRON, mirror=True)

    torso = hips.bone("torso", (0, 28, 0))
    # a thick gut and a barrel of a chest, heavy traps rising to a bull neck
    torso.cube("belly", (-8.5, 26, -5), (17, 10, 10), SKIN, detail=both(abs_detail, heat(0.7), rounded(0.75, 0.9)))
    torso.cube("chest", (-11, 35, -6), (22, 11, 12), SKIN, detail=both(chest, soot, heat(), scars, rounded(0.8, 0.95)))
    torso.cube("lats", (-12, 34, -3.5), (24, 9, 9), SKIN, detail=both(heat(0.5), rounded(0.75, 0.85)))
    torso.cube("shoulders", (-9, 43, -4), (18, 4, 9), SKIN, detail=both(soot, rounded(0.75, 1.0)))
    for side, s in (("r", 1), ("l", -1)):
        # the trapezius, sloping from the neck down to each shoulder
        torso.cube(f"trap_{side}", ((1 if s == 1 else -11), 44.5, -3.5), (10, 4, 8), SKIN, rotation=(0, 0, -16 * s),
                   origin=(2 * s, 48, 0), detail=both(soot, rounded(0.8, 0.95)))
    torso.cube("neck", (-3.5, 46, -5), (7, 4, 7), SKIN, detail=rounded(0.7, 0.8))
    # the apron's bib over his chest, hung from a strap round his neck
    torso.cube("apron_bib", (-6, 29, -7), (12, 13, 1), LEATHER, detail=apron)
    for side, s in (("r", 1), ("l", -1)):
        torso.cube(f"bib_strap_{side}", ((4 if s == 1 else -6), 41, -6.6), (2, 7, 1), STRAP, rotation=(0, 0, -18 * s),
                   origin=(5 * s, 42, -6.6), detail=stitched)

    head = torso.bone("head", (0, 49, -2))
    head.cube("skull", (-4.5, 50, -7.5), (9, 9, 9), SKIN, detail=both(face, rounded(0.8, 0.9)))
    head.cube("brow", (-4.7, 55, -8.4), (9, 2, 2), SKIN, detail=brow)
    head.cube("nose", (-1, 51.5, -9.2), (2, 4, 2), SKIN, rotation=(0, 0, 8), origin=(0, 53, -8), detail=rounded(0.8, 0.7))
    for side, s in (("r", 1), ("l", -1)):
        head.cube(f"ear_{side}", ((4.5 if s == 1 else -5.5), 52.5, -4), (1, 3, 2), SKIN)
    # the beard: a moustache over the mouth, a full beard round the jaw, then thick ropes narrowing to smouldering tips
    head.cube("moustache", (-3.5, 50.5, -9.2), (7, 2, 1), BEARD, detail=beard)
    head.cube("beard", (-5, 47, -8.8), (10, 5, 8), BEARD, detail=both(mouth, beard))
    head.cube("beard_mid", (-4, 43, -9), (8, 4, 4), BEARD, detail=beard)
    head.cube("beard_tip", (-2.5, 40, -8.6), (5, 3, 3), BEARD, detail=both(beard, smoulder))
    head.cube("sideburns", (-5, 48, -6.5), (10, 4, 6), BEARD, inflate=0.2, detail=beard)
    # a smith's felt cap over wild hair
    head.cube("hair", (-5, 52, -3), (10, 6, 6), BEARD, detail=beard)
    head.cube("cap", (-4.75, 57.5, -7.75), (9, 2, 9), FELT, inflate=0.2, detail=rounded(0.8, 0.8))
    head.cube("cap_crown", (-4, 59.5, -6.5), (8, 2, 7), FELT, detail=rounded(0.8, 0.85))
    head.cube("cap_tip", (-2.5, 61.5, -5), (5, 1, 5), FELT)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (14 * s, 46, 0))
        # a round shoulder, then the upper arm with its biceps
        arm.cube(f"{side}_deltoid", (x(10.5, 9), 40, -4.5), (9, 8, 9), SKIN, rotation=(0, 0, -8 * s), origin=(14 * s, 46, 0),
                 mirror=mirror, detail=both(heat(0.8), rounded(0.75, 0.85)))
        arm.cube(f"{side}_deltoid_cap", (x(11.5, 7), 47.5, -3.5), (7, 2, 7), SKIN, rotation=(0, 0, -8 * s), origin=(14 * s, 46, 0),
                 mirror=mirror, detail=rounded(0.8, 0.8))
        arm.cube(f"{side}_arm", (x(11, 8), 33, -4), (8, 8, 8), SKIN, mirror=mirror, detail=both(muscle, soot, rounded(0.75, 0.85)))
        arm.cube(f"{side}_bicep", (x(12, 6), 34, -4.8), (6, 6, 1), SKIN, mirror=mirror, detail=rounded(0.8, 0.8))
        forearm = arm.bone(f"{side}_forearm", (15 * s, 33, 0))
        # a smith's forearms: thicker than his upper arms, veined, bound in a leather bracer at the wrist
        forearm.cube(f"{side}_forearm", (x(10.5, 9), 26, -4.5), (9, 8, 9), SKIN, mirror=mirror,
                     detail=both(veins, heat(0.8), rounded(0.75, 0.85)))
        forearm.cube(f"{side}_wrist", (x(11, 8), 20, -4), (8, 7, 8), SKIN, mirror=mirror, detail=rounded(0.75, 0.85))
        forearm.cube(f"{side}_bracer", (x(10.5, 9), 21, -4.5), (9, 5, 9), LEATHER, mirror=mirror, detail=bands)
        # the fist: the back of the hand, fingers wrapped round the haft, a thumb over them
        forearm.cube(f"{side}_fist", (x(11.5, 7), 14, -3.5), (7, 6, 7), SKIN, mirror=mirror, detail=rounded(0.8, 0.85))
        forearm.cube(f"{side}_fingers", (x(11.5, 6), 14.5, -4.5), (6, 5, 1), SKIN, mirror=mirror, detail=knuckles)
        forearm.cube(f"{side}_thumb", (x(12, 3), 18.5, -5), (3, 2, 3), SKIN, mirror=mirror)
        if s == 1:
            # the hammer hangs from his fist: an iron pommel, a leather-wrapped grip, a long ash haft, iron bands binding
            # it into the head, and the head itself, its striking face (forward, so it lands in a downward blow)
            # white-hot and a short peen behind
            hammer = forearm.bone("hammer", (15, 17, 0))
            hammer.cube("pommel", (13.5, 21, -1.5), (3, 2, 3), IRON, inflate=0.3)
            hammer.cube("grip", (13.5, 12, -1.5), (3, 9, 3), HAFT, inflate=0.1, detail=wrap)
            hammer.cube("haft", (14, -1, -1), (2, 13, 2), HAFT, detail=grain)
            hammer.cube("binding", (13.5, -2, -1.5), (3, 3, 3), IRON, inflate=0.2)
            hammer.cube("hammer_head", (11.5, -9, -5), (7, 7, 10), HAMMER, detail=hammer_body)
            hammer.cube("hammer_cheek", (11, -8.5, -3), (8, 6, 6), HAMMER)
            hammer.cube("hammer_face", (11.25, -9.25, -6), (7, 8, 1), HAMMER, inflate=0.25, detail=hammer_face)
            hammer.cube("hammer_peen", (12.5, -8, 5), (5, 5, 2), HAMMER)
            hammer.cube("hammer_peen_tip", (13.5, -7, 7), (3, 3, 1), HAMMER)
        else:
            # the tongs in his left fist, a bar of hot iron in their jaws, and a coil of chain hanging from his wrist
            chain = forearm.bone("chain", (-15, 14, 0))
            chain.cube("tongs_a", (-15.5, 2, -2.5), (1, 14, 1), IRON, rotation=(-6, 0, 0), origin=(-15, 15, -2))
            chain.cube("tongs_b", (-15.5, 2, -0.5), (1, 14, 1), IRON, rotation=(6, 0, 0), origin=(-15, 15, 0))
            chain.cube("tongs_pivot", (-16, 9, -2.5), (2, 2, 3), IRON)
            chain.cube("tongs_jaw", (-16, 0, -3.5), (2, 3, 5), IRON)
            chain.cube("ingot", (-17, -1, -2.5), (4, 2, 3), IRON, detail=ingot)
            chain.cube("chain", (-19.5, 4, -1), (2, 12, 2), IRON, detail=links)
            chain.cube("chain_hook", (-20.5, 2, -1.5), (4, 2, 3), IRON)
    animations(m)
    return m


ZERO = (0, 0, 0)


def animations(m):
    # heavy breathing, the hammer swaying, the crippled leg favoured
    idle = m.animation("idle", 3.2, loop=True)
    idle.pos("torso", [(0, ZERO), (1.6, (0, 0.8, 0)), (3.2, ZERO)])
    idle.rot("head", [(0, ZERO), (1.6, (4, 0, 0)), (3.2, ZERO)])
    idle.rot("right_arm", [(0, (8, 0, 6)), (1.6, (14, 0, 8)), (3.2, (8, 0, 6))])
    idle.rot("left_arm", [(0, (0, 0, -8)), (1.6, (4, 0, -10)), (3.2, (0, 0, -8))])
    idle.rot("left_thigh", [(0, (-6, 0, -6)), (3.2, (-6, 0, -6))])

    # a limping stride: the braced leg swings stiff, the body dips onto it
    walk = m.animation("walk", 1.4, loop=True)
    walk.rot("right_thigh", [(0, (-28, 0, 0)), (0.7, (28, 0, 0)), (1.4, (-28, 0, 0))])
    walk.rot("right_shin", [(0, (0, 0, 0)), (0.35, (-35, 0, 0)), (0.7, (0, 0, 0)), (1.4, (0, 0, 0))])
    walk.rot("left_thigh", [(0, (20, 0, -6)), (0.7, (-20, 0, -6)), (1.4, (20, 0, -6))])
    walk.pos("hips", [(0, ZERO), (0.35, (0, -0.5, 0)), (0.7, ZERO), (1.05, (0, -2, 0)), (1.4, ZERO)])
    walk.rot("torso", [(0, (0, 0, 0)), (1.05, (0, 0, -6)), (1.4, (0, 0, 0))])
    walk.rot("right_arm", [(0, (20, 0, 6)), (0.7, (-15, 0, 6)), (1.4, (20, 0, 6))])
    walk.rot("left_arm", [(0, (-15, 0, -8)), (0.7, (20, 0, -8)), (1.4, (-15, 0, -8))])

    # the Strike: the hammer up over his head (white flash), then down in front of him
    strike = m.animation("strike", 1.8)
    strike.rot("right_arm", [(0, (8, 0, 6)), (0.7, (165, 0, 10)), (1.0, (170, 0, 10)), (1.15, (45, 0, 6)), (1.8, (8, 0, 6))])
    strike.rot("right_forearm", [(0, ZERO), (0.7, (30, 0, 0)), (1.15, ZERO)])
    strike.rot("torso", [(0, ZERO), (0.7, (12, 0, 0)), (1.0, (14, 0, 0)), (1.15, (-28, 0, 0)), (1.8, ZERO)])
    strike.rot("left_arm", [(0, ZERO), (0.7, (-20, 0, -30)), (1.15, (30, 0, -20)), (1.8, ZERO)])
    strike.pos("hips", [(0, ZERO), (0.7, (0, 0, 2)), (1.15, (0, -2, -3)), (1.8, ZERO)])
    strike.event(0.6, "flash").event(1.15, "hit")

    # Hammerfall: he crouches, leaps with the hammer high, and comes down on his mark
    fall = m.animation("hammerfall", 2.4)
    fall.pos("hips", [(0, ZERO), (0.7, (0, -6, 0)), (1.0, (0, 14, 0)), (1.3, (0, 16, 0)), (1.55, (0, -4, 0)), (2.4, ZERO)])
    fall.rot("right_thigh", [(0, ZERO), (0.7, (60, 0, 0)), (1.0, (-10, 0, 0)), (1.55, (50, 0, 0)), (2.4, ZERO)])
    fall.rot("right_shin", [(0, ZERO), (0.7, (-70, 0, 0)), (1.0, ZERO), (1.55, (-50, 0, 0)), (2.4, ZERO)])
    fall.rot("left_thigh", [(0, ZERO), (0.7, (40, 0, 0)), (1.0, (-10, 0, 0)), (1.55, (30, 0, 0)), (2.4, ZERO)])
    fall.rot("right_arm", [(0, (8, 0, 6)), (0.7, (40, 0, 6)), (1.1, (175, 0, 10)), (1.55, (50, 0, 6)), (2.4, (8, 0, 6))])
    fall.rot("torso", [(0, ZERO), (0.7, (-20, 0, 0)), (1.1, (15, 0, 0)), (1.55, (-30, 0, 0)), (2.4, ZERO)])
    fall.event(1.0, "leap").event(1.55, "land")

    # Molten Rain: arms up, he upends an unseen crucible over the hall
    pour = m.animation("pour", 2.2)
    pour.rot("right_arm", [(0, (8, 0, 6)), (0.8, (150, 0, 25)), (1.4, (160, 0, 25)), (2.2, (8, 0, 6))])
    pour.rot("left_arm", [(0, ZERO), (0.8, (150, 0, -25)), (1.4, (160, 0, -25)), (2.2, ZERO)])
    pour.rot("torso", [(0, ZERO), (0.8, (10, 0, 0)), (1.4, (-15, 0, 0)), (2.2, ZERO)])
    pour.rot("head", [(0, ZERO), (0.8, (25, 0, 0)), (1.4, (-10, 0, 0)), (2.2, ZERO)])
    pour.event(1.2, "pour")

    # the Chains: the chain whirled round his head, then flung along the line
    throw = m.animation("throw", 1.8)
    throw.rot("left_arm", [(0, ZERO), (0.3, (150, 0, -60)), (0.5, (150, 90, -60)), (0.7, (150, 180, -60)), (0.9, (150, 270, -60)),
                           (1.05, (90, 0, -10)), (1.8, ZERO)])
    throw.rot("torso", [(0, ZERO), (0.9, (0, 25, 0)), (1.05, (-15, -20, 0)), (1.8, ZERO)])
    throw.event(1.05, "throw")

    # the anvil-blow that calls his smiths: the hammer down onto the floor three times
    summon = m.animation("summon", 2.2)
    summon.rot("right_arm", [(0, (8, 0, 6)), (0.35, (120, 0, 6)), (0.55, (40, 0, 6)), (0.9, (120, 0, 6)), (1.1, (40, 0, 6)),
                             (1.45, (120, 0, 6)), (1.65, (40, 0, 6)), (2.2, (8, 0, 6))])
    summon.rot("torso", [(0, ZERO), (0.55, (-15, 0, 0)), (1.1, (-15, 0, 0)), (1.65, (-20, 0, 0)), (2.2, ZERO)])
    summon.event(1.65, "ring")

    # reels after a blocked blow: down on his good knee, shaking his head
    stagger = m.animation("stagger", 2.4)
    stagger.pos("hips", [(0, ZERO), (0.3, (0, -8, 2)), (2.0, (0, -8, 2)), (2.4, ZERO)])
    stagger.rot("right_thigh", [(0, ZERO), (0.3, (80, 0, 0)), (2.0, (80, 0, 0)), (2.4, ZERO)])
    stagger.rot("right_shin", [(0, ZERO), (0.3, (-90, 0, 0)), (2.0, (-90, 0, 0)), (2.4, ZERO)])
    stagger.rot("torso", [(0, ZERO), (0.3, (-25, 0, 0)), (2.0, (-25, 0, 0)), (2.4, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.6, (-15, -25, 0)), (0.9, (-15, 25, 0)), (1.2, (-15, -25, 0)), (1.5, (-15, 25, 0)), (2.4, ZERO)])
    stagger.rot("right_arm", [(0, (8, 0, 6)), (0.3, (30, 0, 20)), (2.0, (30, 0, 20)), (2.4, (8, 0, 6))])

    # his rage at half health: the hammer driven into the floor, then a roar with arms flung wide
    phase = m.animation("phase", 3.2)
    phase.rot("right_arm", [(0, (8, 0, 6)), (0.8, (170, 0, 10)), (1.2, (30, 0, 6)), (1.9, (30, 0, 6)), (2.3, (20, 0, 70)),
                            (2.9, (20, 0, 70)), (3.2, (8, 0, 6))])
    phase.rot("left_arm", [(0, ZERO), (1.9, ZERO), (2.3, (20, 0, -70)), (2.9, (20, 0, -70)), (3.2, ZERO)])
    phase.rot("torso", [(0, ZERO), (0.8, (14, 0, 0)), (1.2, (-35, 0, 0)), (1.9, (-35, 0, 0)), (2.3, (15, 0, 0)), (2.9, (15, 0, 0)),
                        (3.2, ZERO)])
    phase.rot("head", [(0, ZERO), (2.3, (30, 0, 0)), (2.9, (30, 0, 0)), (3.2, ZERO)])
    phase.event(1.2, "plunge").event(2.3, "roar")

    # his entrance: bent over his anvil, hammering, then he straightens and roars
    intro = m.animation("intro", 5.0)
    intro.rot("torso", [(0, (-30, 0, 0)), (3.0, (-30, 0, 0)), (3.6, (15, 0, 0)), (4.4, (15, 0, 0)), (5.0, ZERO)])
    intro.rot("right_arm", [(0, (90, 0, 6)), (0.4, (140, 0, 6)), (0.6, (60, 0, 6)), (1.2, (140, 0, 6)), (1.4, (60, 0, 6)),
                            (2.0, (140, 0, 6)), (2.2, (60, 0, 6)), (3.0, (60, 0, 6)), (3.6, (30, 0, 60)), (4.4, (30, 0, 60)),
                            (5.0, (8, 0, 6))])
    intro.rot("left_arm", [(0, (50, 0, -10)), (3.0, (50, 0, -10)), (3.6, (30, 0, -60)), (4.4, (30, 0, -60)), (5.0, ZERO)])
    intro.rot("head", [(0, (-15, 0, 0)), (3.0, (-15, 0, 0)), (3.6, (30, 0, 0)), (4.4, (30, 0, 0)), (5.0, ZERO)])
    intro.event(0.6, "clang").event(1.4, "clang2").event(2.2, "clang3").event(3.6, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])
    hurt.rot("head", [(0, ZERO), (0.12, (15, 0, 0)), (0.4, ZERO)])

    # he sinks to his knees, the hammer falls from his hand, and he topples forward
    death = m.animation("death", 3.4)
    death.pos("hips", [(0, ZERO), (0.9, (0, -10, 0)), (2.0, (0, -14, -6)), (3.4, (0, -20, -14))])
    death.rot("hips", [(0, ZERO), (0.9, ZERO), (2.0, (-50, 0, 0)), (3.4, (-80, 0, 0))])
    death.rot("right_thigh", [(0, ZERO), (0.9, (85, 0, 0)), (3.4, (85, 0, 0))])
    death.rot("left_thigh", [(0, ZERO), (0.9, (80, 0, 0)), (3.4, (80, 0, 0))])
    death.rot("right_shin", [(0, ZERO), (0.9, (-90, 0, 0)), (3.4, (-90, 0, 0))])
    death.rot("left_shin", [(0, ZERO), (0.9, (-85, 0, 0)), (3.4, (-85, 0, 0))])
    death.rot("right_arm", [(0, (8, 0, 6)), (0.9, (20, 0, 30)), (2.0, (140, 0, 20)), (3.4, (160, 0, 20))])
    death.rot("left_arm", [(0, ZERO), (2.0, (140, 0, -20)), (3.4, (160, 0, -20))])
    death.rot("hammer", [(0, ZERO), (0.9, (60, 0, 0)), (1.2, (80, 0, 0))])
    death.event(2.0, "fall")


if __name__ == "__main__":
    build().write()
