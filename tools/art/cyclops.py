#!/usr/bin/env python3
"""A Cyclops of the quarries: one of Vulcan's smiths (Aeneid VIII: Brontes, Steropes and Arges forging thunderbolts under
Etna), set to cutting stone in the Cyclops Quarries of the Forge of Dis. A hulking one-eyed giant in a hide loincloth,
a quarry maul in his fists, his single eye burning.

Run from the repo root:  python3 tools/art/cyclops.py && python3 tools/gen_bosses.py

The animations' events are what the server times his blows by (CyclopsEntity): "flash" opens the window to block his
maul and "hit" is when it lands; "throw" lets the boulder go; "stamp" is his quake; "roar" his rage at half health and
his waking; "fall" his death.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc  # noqa: E402

SKIN = material("skin", "skin", base="8a6c56", dark="5a4232")
HIDE = material("hide", "hair", light="6a4a2a", dark="3a2614")
LEATHER = material("leather", "robe", base="5a3c22", fold="3a2614")
ROPE = material("rope", "flat", base="8a7048")
STONE = material("stone", "flat", base="4a4448")
IRON = material("iron", "metal", base="4a4a50", high="7a7a84", low="26262a")
HAFT = material("haft", "flat", base="6a4a2a")
TUSK = material("tusk", "horn", root="d8cba4", tip="f2e8cc", edges=False)
NAIL = material("nail", "flat", base="b09a80", edges=False)
EYEBALL = material("eyeball", "flat", base="e6dcc4", edges=False)
EYE = hexc("ffb020")
EYE_HOT = hexc("fff0a0")
PUPIL = hexc("3a1000")
SOCKET = hexc("2a1a12")
MOUTH = hexc("2a0e08")
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


def grime(faces):
    """Quarry dust and dirt ground into the skin, and old pale scars."""
    for n in SIDES:
        f = faces[n]
        for _ in range(max(1, f.w * f.h // 30)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            darken(f, x, y, 0.78)
            darken(f, x + 1, y, 0.86)
        if f.rng.random() < 0.5 and f.w > 4:
            x, y = f.rng.randrange(f.w - 3), f.rng.randrange(f.h)
            for i in range(3):
                f.px(x + i, y + (i % 2), shade(SKIN.c["base"], 1.2))


def face(faces):
    """The skull's front: the dark socket round the one eye, a furrowed forehead, deep lines from nose to mouth."""
    f = faces["front"]
    w, cx = f.w, f.w // 2
    # the socket, a dark ring the eye sits in (the eye itself and its lids are their own cubes)
    for y in range(4, 8):
        for x in range(cx - 4, cx + 4):
            f.px(x, y, SOCKET if y in (4, 7) or x in (cx - 4, cx + 3) else shade(SKIN.c["dark"], 0.7))
    for x in range(1, w - 1):  # forehead furrows above the brow
        if x % 2:
            darken(f, x, 1, 0.82)
    for y in range(8, f.h - 1):  # lines from the nose down to the mouth
        darken(f, cx - 3, y, 0.75)
        darken(f, cx + 2, y, 0.75)
    for x in range(1, w - 1):
        f.px(x, f.h - 1, MOUTH)


def eye(faces):
    """The one great eye: a bloodshot white round a burning amber iris and a slit of pupil."""
    f = faces["front"]
    cx = f.w // 2
    for x in range(f.w):
        f.px(x, 0, shade(EYEBALL.c["base"], 0.75))
    f.px(0, 1, hexc("b05040"))
    f.px(f.w - 1, 2, hexc("b05040"))
    for dx in (-2, -1, 0, 1):
        for dy in (0, 1, 2):
            f.glow(cx + dx, dy, EYE)
    f.glow(cx - 2, 0, shade(EYE, 0.7))
    f.glow(cx + 1, 2, shade(EYE, 0.7))
    f.glow(cx - 1, 1, EYE_HOT)
    f.glow(cx, 1, PUPIL)
    f.glow(cx, 0, PUPIL)


def brow(faces):
    """The heavy brow: lumpy on top, its underside in deep shadow over the eye."""
    faces["bottom"].fill(lambda x, y: SOCKET)
    f = faces["front"]
    for x in range(f.w):
        darken(f, x, f.h - 1, 0.6)
        if x % 3 == 0:
            darken(f, x, 0, 0.85)


def nose(faces):
    """A broad flat nose with flaring nostrils."""
    b = faces["bottom"]
    b.px(0, b.h - 1, SOCKET)
    b.px(b.w - 1, b.h - 1, SOCKET)
    f = faces["front"]
    f.px(0, f.h - 1, SOCKET)
    f.px(f.w - 1, f.h - 1, SOCKET)
    for y in range(f.h - 1):
        darken(f, 0, y, 0.85)


def jaw(faces):
    """The jutting underbite: a row of crooked lower teeth along its top edge, the chin in shadow."""
    t = faces["top"]
    t.fill(lambda x, y: MOUTH)
    f = faces["front"]
    for x in range(1, f.w - 1):
        if x % 3 != 0:
            f.px(x, 0, TUSK.c["root"] if x % 2 else shade(TUSK.c["root"], 0.8))
    for x in range(f.w):
        darken(f, x, f.h - 1, 0.7)
    for y in range(f.h):
        darken(f, f.w // 2, y, 0.9)


def chest(faces):
    """The slabs of his chest, the breastbone between them, a scatter of coarse hair."""
    f = faces["front"]
    mid = f.w // 2
    for y in range(1, f.h - 1):
        darken(f, mid, y, 0.75)
    for x in range(1, f.w - 1):
        darken(f, x, f.h - 2, 0.75)
    for _ in range(f.w):
        f.px(f.rng.randrange(2, f.w - 2), f.rng.randrange(1, f.h - 2), HIDE.c["dark"])


def gut(faces):
    """A hard gut: the navel and a line of hair down to the loincloth."""
    f = faces["front"]
    mid = f.w // 2
    f.px(mid, f.h // 2, SOCKET)
    for y in range(f.h // 2 + 2, f.h, 2):
        f.px(mid, y, HIDE.c["dark"])
    for y in (1, 3):
        for x in range(2, f.w - 2):
            darken(f, x, y, 0.88)


def muscle(faces):
    """The split between two muscles down the front and back of a limb."""
    for n in ("front", "back"):
        f = faces[n]
        for y in range(1, f.h - 1):
            darken(f, f.w // 2, y, 0.82)


def toes(faces):
    """Toes with dirty nails."""
    f = faces["front"]
    for x in range(f.w):
        if x % 2 == 1:
            for y in range(f.h):
                darken(f, x, y, 0.7)
        else:
            f.px(x, 0, NAIL.c["base"])


def knuckles(faces):
    f = faces["front"]
    for x in range(f.w):
        for y in (1, 3):
            darken(f, x, y, 0.72)
    for x in range(1, f.w, 2):
        darken(f, x, 0, 0.8)


def hide_edge(faces):
    """A ragged hide: a fringe of dark tufts at its hem and lacing at its seams."""
    for n in SIDES:
        f = faces[n]
        for x in range(f.w):
            if x % 2:
                f.px(x, f.h - 1, shade(HIDE.c["dark"], 0.6))
        if f.h > 4:
            for y in range(1, f.h - 2, 3):
                f.px(0, y, ROPE.c["base"])


def lashing(faces):
    """Rope wound round and round."""
    for n in SIDES:
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if (x + y * 2) % 3 == 0:
                    darken(f, x, y, 0.65)


def bark(faces):
    """A rough-hewn log for a haft: grain, knots, the bark left on in strips."""
    for n in SIDES:
        f = faces[n]
        for y in range(f.h):
            for x in range(f.w):
                if (y * 3 + x) % 5 == 0:
                    darken(f, x, y, 0.75)
        for _ in range(2):
            y = f.rng.randrange(f.h)
            f.px(f.rng.randrange(f.w), y, hexc("3a2614"))


def maul_detail(faces):
    """A crude block of iron hammered out by hand: dents and pits, its striking faces bright from use."""
    for n in ("left", "right", "top", "bottom", "front", "back"):
        f = faces[n]
        for _ in range(f.w * f.h // 10):
            darken(f, f.rng.randrange(f.w), f.rng.randrange(f.h), 0.7)
    for n in ("front", "back"):
        f = faces[n]
        for y in range(1, f.h - 1):
            for x in range(1, f.w - 1):
                if (x + y) % 3:
                    f.px(x, y, IRON.c["high"])


def rock(faces):
    """A torn-up boulder: cracked grey stone with lichen and the earth still on it."""
    for n in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[n]
        for _ in range(max(1, f.w // 3)):
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            for i in range(f.rng.randint(2, 5)):
                f.px(x, y, hexc("26222a"))
                x += f.rng.choice((-1, 0, 1))
                y += 1
        for _ in range(max(1, f.w * f.h // 25)):
            f.px(f.rng.randrange(f.w), f.rng.randrange(f.h), hexc("5a6a3a") if n != "bottom" else hexc("4a3420"))


# ----------------------------------------------------------------------------------------------- the body

def build():
    m = Model("cyclops", texture_width=256)
    hips = m.bone("hips", (0, 22, 0))
    # the hide loincloth: a wrap round the hips, a rope belt, and flaps hanging front and back
    hips.cube("loincloth", (-7.5, 16, -4.5), (15, 7, 9), HIDE, detail=hide_edge)
    hips.cube("rope_belt", (-8, 22, -5), (16, 2, 10), ROPE, inflate=0.1, detail=lashing)
    hips.cube("belt_knot", (1, 20.5, -5.8), (3, 3, 1), ROPE, detail=lashing)
    hips.cube("flap_front", (-3, 11, -5.4), (6, 6, 1), HIDE, detail=hide_edge)
    hips.cube("flap_back", (-4.5, 11, 4.4), (9, 6, 1), HIDE, detail=hide_edge)
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        thigh = hips.bone(f"{side}_thigh", (4 * s, 19, 0))
        # thick thighs to a knobbly knee, a bulging calf and broad bare feet
        thigh.cube(f"{side}_thigh", (x(0.5, 7), 12, -3.5), (7, 8, 7), SKIN, mirror=mirror, detail=both(muscle, grime, rounded(0.75, 0.85)))
        thigh.cube(f"{side}_knee", (x(1, 6), 9, -3), (6, 4, 6), SKIN, mirror=mirror, detail=rounded(0.8, 0.8))
        thigh.cube(f"{side}_kneecap", (x(2, 4), 9.5, -3.7), (4, 3, 1), SKIN, mirror=mirror)
        shin = thigh.bone(f"{side}_shin", (4 * s, 10, 0))
        shin.cube(f"{side}_shin", (x(1.5, 5), 3, -2.5), (5, 7, 5), SKIN, mirror=mirror, detail=both(grime, rounded(0.75, 0.9)))
        shin.cube(f"{side}_calf", (x(1.5, 5), 4, 1.5), (5, 5, 2), SKIN, mirror=mirror, detail=rounded(0.75, 0.85))
        shin.cube(f"{side}_ankle_wrap", (x(1, 6), 2, -3), (6, 2, 6), HIDE, mirror=mirror)
        shin.cube(f"{side}_foot", (x(0.5, 7), 0, -5), (7, 3, 8), SKIN, mirror=mirror, detail=rounded(0.85, 0.7))
        shin.cube(f"{side}_toes", (x(0.5, 7), 0, -6), (7, 2, 1), SKIN, mirror=mirror, detail=toes)

    # he stoops: the torso leans forward a little, the head and arms set back so they hang and look ahead
    torso = hips.bone("torso", (0, 24, 0), rotation=(-8, 0, 0))
    # a hard round gut, a deep chest, and the great hump of muscle that thrusts his head forward
    torso.cube("belly", (-8, 24, -5), (16, 9, 10), SKIN, detail=both(gut, grime, rounded(0.75, 0.85)))
    torso.cube("gut", (-6, 24.5, -6), (12, 7, 1), SKIN, detail=rounded(0.8, 0.75))
    torso.cube("chest", (-10, 33, -5.5), (20, 11, 11), SKIN, detail=both(chest, grime, rounded(0.8, 0.9)))
    torso.cube("pecs", (-8.5, 36, -6.4), (17, 6, 1), SKIN, detail=both(chest, rounded(0.85, 0.7)))
    torso.cube("hump", (-7, 42, -1), (14, 5, 7), SKIN, detail=both(grime, rounded(0.75, 0.9)))
    for side, s in (("r", 1), ("l", -1)):
        torso.cube(f"trap_{side}", ((1 if s == 1 else -10), 41.5, -3), (9, 4, 7), SKIN, rotation=(0, 0, -18 * s),
                   origin=(2 * s, 45, 0), detail=rounded(0.8, 0.95))
    # a hide slung over one shoulder, tied with rope
    torso.cube("hide_sash", (-1, 29, -6.8), (3, 15, 1), HIDE, rotation=(0, 0, -35), origin=(0, 37, -6.5), detail=hide_edge)

    head = torso.bone("head", (0, 44, -2), rotation=(8, 0, 0))
    # a low skull thrust forward: a dome, a brow like a ledge over the one eye, a broad nose, and a jutting underbite
    head.cube("skull", (-5, 43, -7.5), (10, 11, 9), SKIN, detail=both(face, rounded(0.8, 0.9)))
    head.cube("dome", (-4, 54, -6), (8, 2, 7), SKIN, detail=rounded(0.85, 0.9))
    head.cube("brow", (-5.5, 51.5, -9), (11, 2, 3), SKIN, detail=both(brow, rounded(0.85, 1.0, ("front",))))
    head.cube("eye", (-3, 48.5, -8), (6, 3, 1), EYEBALL, detail=eye)
    head.cube("lid_upper", (-3.5, 51, -8.4), (7, 1, 1), SKIN)
    head.cube("lid_lower", (-3, 48, -8.3), (6, 1, 1), SKIN)
    head.cube("nose", (-2, 45, -9), (4, 3, 2), SKIN, detail=both(nose, rounded(0.8, 0.8, ("front",))))
    head.cube("nose_bridge", (-1, 47.5, -8.5), (2, 1, 1), SKIN)
    head.cube("jaw", (-5.5, 40, -8.6), (11, 4, 8), SKIN, detail=both(jaw, rounded(0.8, 0.75)))
    for side, s in (("r", 1), ("l", -1)):
        head.cube(f"tusk_{side}", ((3 if s == 1 else -4), 43.5, -9), (1, 3, 1), TUSK)
        head.cube(f"ear_{side}", ((5 if s == 1 else -6), 46, -3.5), (1, 4, 2), SKIN)
        head.cube(f"cheek_{side}", ((3 if s == 1 else -5.5), 45, -8), (2, 2, 1), SKIN)
    head.cube("hair", (-3, 53.5, 0), (6, 2, 2), HIDE)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        mirror = s == -1
        arm = torso.bone(f"{side}_arm", (12 * s, 42, 0), rotation=(8, 0, 0))
        # long heavy arms: a round shoulder, the upper arm, an elbow
        arm.cube(f"{side}_deltoid", (x(9.5, 8), 37, -4), (8, 7, 8), SKIN, rotation=(0, 0, -8 * s), origin=(12 * s, 42, 0),
                 mirror=mirror, detail=both(grime, rounded(0.75, 0.85)))
        arm.cube(f"{side}_arm", (x(10, 7), 30, -3.5), (7, 8, 7), SKIN, mirror=mirror, detail=both(muscle, rounded(0.75, 0.85)))
        arm.cube(f"{side}_elbow", (x(10.5, 6), 29, 0.5), (6, 3, 3), SKIN, mirror=mirror)
        forearm = arm.bone(f"{side}_forearm", (13.5 * s, 30, 0))
        # a thick forearm to a wrist bound in hide, then a big knuckled fist
        forearm.cube(f"{side}_forearm", (x(10, 7), 23, -3.5), (7, 7, 7), SKIN, mirror=mirror, detail=both(muscle, grime, rounded(0.75, 0.85)))
        forearm.cube(f"{side}_wrist", (x(10.5, 6), 18, -3), (6, 5, 6), SKIN, mirror=mirror, detail=rounded(0.8, 0.85))
        forearm.cube(f"{side}_wrap", (x(10, 7), 19, -3.5), (7, 3, 7), HIDE, mirror=mirror, detail=hide_edge)
        forearm.cube(f"{side}_fist", (x(9.5, 8), 13, -3.5), (8, 5, 7), SKIN, mirror=mirror, detail=rounded(0.8, 0.85))
        forearm.cube(f"{side}_fingers", (x(10, 6), 13.5, -4.5), (6, 4, 1), SKIN, mirror=mirror, detail=knuckles)
        forearm.cube(f"{side}_thumb", (x(10.5, 3), 16.5, -5), (3, 2, 2), SKIN, mirror=mirror)
        if s == 1:
            # the maul: a rough log of a haft through the right fist, a great hand-beaten block of iron lashed to its
            # end with rope and wedged, its striking face forward so the blow lands in front of him
            maul = forearm.bone("maul", (13.5, 16, 0))
            maul.cube("maul_haft", (12.5, -5, -1), (2, 25, 2), HAFT, inflate=0.25, detail=bark)
            maul.cube("maul_lashing", (12, -6, -1.5), (3, 3, 3), ROPE, inflate=0.2, detail=lashing)
            maul.cube("maul_head", (9.5, -12, -5), (8, 7, 10), IRON, detail=maul_detail)
            maul.cube("maul_band", (9.25, -10, -4.5), (8, 2, 9), IRON, inflate=0.35)
            maul.cube("maul_wedge", (12.5, -5, -0.5), (2, 1, 1), HAFT, inflate=0.3)
    # the boulder he throws, held over his head when he does (hidden otherwise: CyclopsEntity.partHidden)
    boulder = torso.bone("boulder", (0, 60, -2))
    boulder.cube("boulder", (-6, 55, -8), (12, 11, 12), STONE, detail=rock)
    boulder.cube("boulder_lump", (-7, 57, -6.5), (14, 7, 9), STONE, detail=rock)
    boulder.cube("boulder_crown", (-4.5, 54, -9), (9, 13, 10), STONE, detail=rock)
    animations(m)
    return m


ZERO = (0, 0, 0)
HIDDEN = (0.01, 0.01, 0.01)
SHOWN = (1, 1, 1)


def animations(m):
    idle = m.animation("idle", 3.0, loop=True)
    idle.pos("torso", [(0, ZERO), (1.5, (0, 0.6, 0)), (3.0, ZERO)])
    idle.rot("head", [(0, (0, -12, 0)), (1.5, (0, 12, 0)), (3.0, (0, -12, 0))])

    walk = m.animation("walk", 1.3, loop=True)
    walk.rot("right_thigh", [(0, (-28, 0, 0)), (0.65, (28, 0, 0)), (1.3, (-28, 0, 0))])
    walk.rot("left_thigh", [(0, (28, 0, 0)), (0.65, (-28, 0, 0)), (1.3, (28, 0, 0))])
    walk.rot("right_shin", [(0, ZERO), (0.3, (-30, 0, 0)), (0.65, ZERO), (1.3, ZERO)])
    walk.rot("left_shin", [(0, ZERO), (0.65, ZERO), (0.95, (-30, 0, 0)), (1.3, ZERO)])
    walk.rot("left_arm", [(0, (-20, 0, -6)), (0.65, (20, 0, -6)), (1.3, (-20, 0, -6))])
    walk.pos("hips", [(0, ZERO), (0.32, (0, -0.8, 0)), (0.65, ZERO), (0.97, (0, -0.8, 0)), (1.3, ZERO)])

    # the Smash: the maul swung up over his head, a white flash, then down in front of him
    smash = m.animation("smash", 1.7)
    smash.rot("right_arm", [(0, ZERO), (0.65, (160, 0, 0)), (0.95, (165, 0, 0)), (1.1, (40, 0, 0)), (1.7, ZERO)])
    smash.rot("left_arm", [(0, ZERO), (0.65, (150, 0, 20)), (1.1, (40, 0, 10)), (1.7, ZERO)])
    smash.rot("torso", [(0, ZERO), (0.65, (12, 0, 0)), (1.1, (-25, 0, 0)), (1.7, ZERO)])
    smash.event(0.55, "flash").event(1.1, "hit")

    # the Throw: he wrenches a boulder from the ground, lifts it over his head and hurls it
    throw = m.animation("throw", 2.0)
    throw.rot("torso", [(0, ZERO), (0.5, (-35, 0, 0)), (1.0, (15, 0, 0)), (1.35, (-25, 0, 0)), (2.0, ZERO)])
    throw.rot("right_arm", [(0, ZERO), (0.5, (60, 0, 0)), (1.0, (170, 0, 10)), (1.35, (60, 0, 0)), (2.0, ZERO)])
    throw.rot("left_arm", [(0, ZERO), (0.5, (60, 0, 0)), (1.0, (170, 0, -10)), (1.35, (60, 0, 0)), (2.0, ZERO)])
    # (the boulder is hidden whenever he isn't throwing: CyclopsEntity.partHidden; here it drops out of sight once thrown)
    throw.scale("boulder", [(0, SHOWN), (1.35, SHOWN), (1.4, HIDDEN), (2.0, HIDDEN)])
    throw.pos("boulder", [(0, (0, -40, -10)), (0.55, (0, -40, -10)), (1.0, ZERO), (1.35, ZERO)])
    throw.event(1.35, "throw")

    # the Quake: up on one foot, then down with all his weight
    quake = m.animation("quake", 1.6)
    quake.rot("right_thigh", [(0, ZERO), (0.6, (-70, 0, 0)), (0.9, (-75, 0, 0)), (1.05, (10, 0, 0)), (1.6, ZERO)])
    quake.rot("right_shin", [(0, ZERO), (0.6, (60, 0, 0)), (1.05, ZERO)])
    quake.rot("torso", [(0, ZERO), (0.6, (10, 0, 0)), (1.05, (-15, 0, 0)), (1.6, ZERO)])
    quake.pos("hips", [(0, ZERO), (0.6, (0, 2, 0)), (1.05, (0, -3, 0)), (1.6, ZERO)])
    quake.event(1.05, "stamp")

    # reels after a blocked blow
    stagger = m.animation("stagger", 2.2)
    stagger.pos("hips", [(0, ZERO), (0.3, (0, -7, 2)), (1.8, (0, -7, 2)), (2.2, ZERO)])
    stagger.rot("right_thigh", [(0, ZERO), (0.3, (80, 0, 0)), (1.8, (80, 0, 0)), (2.2, ZERO)])
    stagger.rot("right_shin", [(0, ZERO), (0.3, (-90, 0, 0)), (1.8, (-90, 0, 0)), (2.2, ZERO)])
    stagger.rot("torso", [(0, ZERO), (0.3, (-25, 0, 0)), (1.8, (-25, 0, 0)), (2.2, ZERO)])
    stagger.rot("head", [(0, ZERO), (0.6, (-10, -25, 0)), (0.9, (-10, 25, 0)), (1.2, (-10, -25, 0)), (2.2, ZERO)])

    # woken (and again at half health): he beats his chest and roars
    roar = m.animation("roar", 2.4)
    roar.rot("head", [(0, ZERO), (0.8, (35, 0, 0)), (1.8, (35, 0, 0)), (2.4, ZERO)])
    roar.rot("right_arm", [(0, ZERO), (0.4, (100, 0, -40)), (0.6, (80, 0, -30)), (0.8, (100, 0, -40)), (1.0, (20, 0, 60)), (1.8, (20, 0, 60)),
                           (2.4, ZERO)])
    roar.rot("left_arm", [(0, ZERO), (0.5, (100, 0, 40)), (0.7, (80, 0, 30)), (1.0, (20, 0, -60)), (1.8, (20, 0, -60)), (2.4, ZERO)])
    roar.rot("torso", [(0, ZERO), (0.8, (12, 0, 0)), (1.8, (12, 0, 0)), (2.4, ZERO)])
    roar.event(1.0, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("torso", [(0, ZERO), (0.12, (10, 0, 0)), (0.4, ZERO)])

    death = m.animation("death", 2.8)
    death.pos("hips", [(0, ZERO), (0.8, (0, -8, 0)), (1.6, (0, -14, -8)), (2.8, (0, -18, -14))])
    death.rot("hips", [(0, ZERO), (0.8, ZERO), (1.6, (-60, 0, 0)), (2.8, (-85, 0, 0))])
    death.rot("right_thigh", [(0, ZERO), (0.8, (80, 0, 0)), (2.8, (80, 0, 0))])
    death.rot("left_thigh", [(0, ZERO), (0.8, (75, 0, 0)), (2.8, (75, 0, 0))])
    death.rot("right_arm", [(0, ZERO), (1.6, (140, 0, 20)), (2.8, (160, 0, 20))])
    death.rot("left_arm", [(0, ZERO), (1.6, (140, 0, -20)), (2.8, (160, 0, -20))])
    death.event(1.6, "fall")


if __name__ == "__main__":
    build().write()
