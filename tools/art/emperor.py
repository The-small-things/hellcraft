#!/usr/bin/env python3
"""Lucifer's true form (Inferno XXXIV): "The Emperor of the kingdom dolorous from his mid-breast forth issued from the
ice", a colossus with three faces on one head (the front one red, the right between white and yellow, the left black),
under each face two great wings, "not feathered, but like a bat's", whose beating freezes Cocytus; in each mouth a
sinner. He weeps with six eyes.

Run from the repo root:  python3 tools/art/emperor.py && python3 tools/gen_bosses.py

He is drawn 10 times his Blockbench size: chest-deep in the ice his chest is 25 blocks across, his head 12, and his
wings reach about 60 blocks up. His wings turn slowly in the fog like a windmill's sails (IDLE, under everything):
what Dante saw first. Asleep (DORMANT) his head is bowed and his eyes shut. The events time his fight (LuciferFight / EmperorAttacks):
"slam" when a hand comes down and "lift" when it rises again (between them the hand lies on the ice, a weak point);
"flash" opens the window to block a bite, "bite" when it lands and "rise" when the face lifts again (between them the
face is down within reach); "beat" his wingbeat; "weep" his tears; "open" the moment his eyes open; "freeze" his end.

Where the hands and faces come down is worked out here (reach()) and copied into EmperorEntity: keep them in step.
The hands' palms stay centred SHOULDER_X + 2 out and ARM below the shoulders, and the head's middle HEAD above the neck.

The body: a ribcage like a hull under shaggy flanks, a spine of knuckled bone, arms of corded muscle with spurs of bone
and four-fingered clawed hands; one horned head, each face with its own brow, nose, jaw and a sinner kicking in its
mouth (Judas in the red face, head first). Each wing is a bat's: an arm of bone to the wrist, three long fingers fanned
from it and a membrane between them, scalloped along its trailing edge, with a claw at the wrist.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from bbmodel import Model, material, shade, hexc, mix  # noqa: E402

SCALE = 10.0
RED = material("red", "skin", base="8a1a14", dark="4a0806")
PALE = material("pale", "skin", base="c8b878", dark="7a6a38")
BLACK = material("black", "skin", base="1a1414", dark="060404")
HIDE = material("hide", "skin", base="3a2a2e", dark="1a1214")
FUR = material("fur", "hair", light="3a2c2c", dark="0e0a0a")
FROST = material("frost", "flat", base="c8e0f0")
ICE = material("ice", "flat", base="a8c8e0")
WING = material("wing", "hair", light="2a1e22", dark="0a0608")
MEMBRANE = material("membrane", "flat", base="2a1c20", edges=False)
WING_BONE = material("wing_bone", "horn", root="4a3a36", tip="1a1212")
HORN = material("horn", "horn", root="4a3a30", tip="0e0a08")
FANG = material("fang", "horn", root="d8ccb0", tip="8a7a60")
SINNER = material("sinner", "skin", base="8a8a98", dark="4a4a58")
EYE = hexc("ffe8a0")
MOUTH = hexc("0a0202")
GUM = hexc("4a0a0a")
TEAR = hexc("a8d8ff")
LID = hexc("0e0606")
SIDES = ("front", "back", "left", "right")

# the shapes the animations and reach() share (pixels)
SHOULDER_Y = 14
SHOULDER_X = 22
ARM = 27  # shoulder to the middle of the hand
NECK_Y = 22
HEAD = 10  # neck to the middle of the head
AT_REST = 30  # the arms' rest: forward-down into the ice
SLAM = 66  # a hand on the ice in front of him
BEND = 55  # how far he bends to bite
NOD = 35  # and his neck
# (Blockbench's +X swings a hanging arm forward and tips a body or head back: the slams use +, the bites -.)


# ----------------------------------------------------------------------------------------------- painting helpers

def tint(f, x, y, k, toward=None, t=0.0):
    """Darkens (k < 1) or lightens one pixel of a face, leaving transparent pixels be; optionally mixes toward a colour."""
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


def frosted(height=6):
    """Rime and hoarfrost creeping up from the ice he is frozen in."""
    def paint(faces):
        for name in SIDES:
            f = faces[name]
            for y in range(max(0, f.h - height), f.h):
                up = f.h - 1 - y
                for x in range(f.w):
                    if up < 2 or f.rng.random() < 0.8 - 0.7 * up / max(1, height):
                        tint(f, x, y, 1.0, FROST.c["base"], 0.85 - 0.4 * up / max(1, height))
    return paint


def rimed(faces):
    """Frost along the tops of things, where the cold settles."""
    t = faces["top"]
    for y in range(t.h):
        for x in range(t.w):
            if t.rng.random() < 0.5:
                tint(t, x, y, 1.0, FROST.c["base"], 0.6)
    for name in SIDES:
        f = faces[name]
        for x in range(f.w):
            if f.rng.random() < 0.6:
                tint(f, x, 0, 1.0, FROST.c["base"], 0.5)


def ribs(faces):
    """Ribs under the hide along his flanks and the line of the sternum."""
    for name in ("left", "right"):
        f = faces[name]
        for y in range(1, f.h, 3):
            for x in range(1, f.w - 1):
                tint(f, x, y, 0.72)
                tint(f, x, y + 1, 1.1)
    f = faces["front"]
    for y in range(f.h):
        tint(f, f.w // 2, y, 0.75)
        tint(f, f.w // 2 - 1, y, 0.88)
    for y in range(2, f.h, 3):
        for x in range(2, f.w // 2 - 3):
            tint(f, x, y + (x // 4), 0.82)
            tint(f, f.w - 1 - x, y + (x // 4), 0.82)


def scars(n=4, sides=("front", "back", "left", "right")):
    """Old wounds from the war in heaven, pale seams across the hide."""
    def paint(faces):
        for name in sides:
            f = faces[name]
            for _ in range(n):
                x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
                d = f.rng.choice((-1, 1))
                for i in range(f.rng.randint(3, 6)):
                    tint(f, x + i, y + d * (i // 2), 1.45)
    return paint


def pec(faces):
    """A great slab of chest muscle: lit across its upper swell, fibres fanning to the shoulder, dark beneath."""
    f = faces["front"]
    for y in range(f.h):
        for x in range(f.w):
            swell = 1.0 - abs(x - f.w / 2) / f.w - abs(y - f.h * 0.35) / f.h
            tint(f, x, y, 0.85 + 0.35 * max(0.0, swell))
            if (x + 2 * y) % 5 == 0:
                tint(f, x, y, 0.88)
    for x in range(f.w):
        tint(f, x, f.h - 1, 0.6)
        tint(f, x, f.h - 2, 0.8)
        tint(f, x, 0, 1.15)
    for name in ("left", "right", "bottom"):
        g = faces[name]
        for y in range(g.h):
            for x in range(g.w):
                tint(g, x, y, 0.7)


def collarbones(faces):
    """The collarbones' ridges across the top of his chest, and the hollow of the throat between them."""
    f = faces["front"]
    mid = f.w // 2
    for x in range(f.w):
        y = 2 + abs(x - mid) // 9
        tint(f, x, y, 1.25)
        tint(f, x, y + 1, 0.7)
    for y in range(f.h):
        tint(f, mid, y, 0.6)
        tint(f, mid - 1, y, 0.6)


def spine_knob(faces):
    for name in SIDES:
        f = faces[name]
        for x in range(f.w):
            tint(f, x, f.h - 1, 0.6)
    rimed(faces)


def corded(faces):
    """Muscle like rope: long cords down the limb and a few veins."""
    for name in SIDES:
        f = faces[name]
        for x in range(0, f.w, 3):
            for y in range(f.h):
                tint(f, x, y, 0.8)
        for _ in range(2):
            x = f.rng.randrange(1, max(2, f.w - 1))
            for y in range(f.h):
                tint(f, x, y, 0.88 if y % 2 else 0.92)
                if f.rng.random() < 0.3:
                    x = max(1, min(f.w - 2, x + f.rng.choice((-1, 1))))


def knuckles(faces):
    for name in ("front", "back"):
        f = faces[name]
        for x in range(f.w):
            tint(f, x, f.h - 1, 0.75)
            if x % 3 == 1:
                tint(f, x, f.h - 2, 1.2)


def shut_or_open(faces, f, cols, row):
    """Eye sockets: deep shadow round them and a shut lid (the glowing eyes cover it once they open)."""
    for c0, c1 in cols:
        for x in range(c0 - 1, c1 + 1):
            tint(f, x, row - 2, 0.55)
            tint(f, x, row - 1, 0.45)
            tint(f, x, row + 2, 0.6)
            tint(f, x, row + 3, 0.8)
        for x in range(c0, c1):
            f.px(x, row, LID)
            f.px(x, row + 1, shade(LID, 1.6))


def face_front(mat):
    """The red face's paint: furrows on the brow, sockets, frozen tears, a gaping mouth with gums and a sinner's dark."""
    def paint(faces):
        f = faces["front"]
        w = f.w
        for y in (1, 3):
            for x in range(4, w - 4):
                tint(f, x, y + (1 if abs(x - w / 2) < 3 else 0), 0.78)
        shut_or_open(faces, f, ((3, 7), (w - 7, w - 3)), 6)
        for x in (5, w - 6):
            for y in range(9, 14):
                f.px(x, y, TEAR if y % 2 else shade(TEAR, 0.85))
        for y in range(9, 13):
            tint(f, 3, y, 0.8)
            tint(f, w - 4, y, 0.8)
        for y in range(14, 20):
            for x in range(4, w - 4):
                f.px(x, y, MOUTH if 15 <= y else GUM)
        for x in range(3, w - 3):
            tint(f, x, 13, 0.6)
        for y in range(10, 14):
            tint(f, 2, y, 0.7)
            tint(f, w - 3, y, 0.7)
    return paint


def face_side(mat, name):
    """A side face (painted on the plate's outward face): sockets, frozen tears, a gaping mouth."""
    def paint(faces):
        f = faces[name]
        w = f.w
        for y in (1, 3):
            for x in range(3, w - 3):
                tint(f, x, y, 0.78)
        shut_or_open(faces, f, ((2, 6), (w - 6, w - 2)), 5)
        for x in (4, w - 5):
            for y in range(8, 12):
                f.px(x, y, TEAR if y % 2 else shade(TEAR, 0.85))
        for y in range(12, 17):
            for x in range(3, w - 3):
                f.px(x, y, MOUTH if y >= 13 else GUM)
        for x in range(2, w - 2):
            tint(f, x, 11, 0.6)
    return paint


def eye_glow(faces):
    for name in ("front", "back", "left", "right", "top", "bottom"):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                f.glow(x, y, EYE)


def brow_detail(faces):
    for name in SIDES + ("bottom",):
        f = faces[name]
        for y in range(f.h):
            for x in range(f.w):
                if name == "bottom" or y == f.h - 1:
                    tint(f, x, y, 0.55)
    for name in SIDES:
        f = faces[name]
        for x in range(f.w):
            tint(f, x, 0, 1.15)


def nostrils(faces):
    b = faces["bottom"]
    for x in range(b.w):
        for y in range(b.h):
            if 0 < x < b.w - 1 and x != b.w // 2 and 0 < y < b.h - 1:
                b.px(x, y, MOUTH)


def jaw_detail(faces):
    """The lower jaw, hanging open: teeth along its top, the dark of the mouth behind them."""
    t = faces["top"]
    for y in range(t.h):
        for x in range(t.w):
            t.px(x, y, MOUTH if y < t.h - 2 else GUM)
    for x in range(0, t.w, 2):
        t.px(x, t.h - 1, FANG.c["root"])
    occlude(bottom=2, k=0.6)(faces)


def membrane(right, a_len, b_ang, b_len, scallop=3.0):
    """A bat's wing membrane between two rays from the wrist: ray A runs along the panel's top edge (its length a_len),
    ray B b_ang degrees below it (b_len long). Everything outside that triangle is cut away, and the trailing edge
    between the two tips is scalloped inward. Veins run out from the wrist; the skin is thin, darker at its edges."""
    def paint(faces):
        tb = math.radians(b_ang)
        bx, by = b_len * math.cos(tb), b_len * math.sin(tb)
        for name in ("front", "back"):
            f = faces[name]
            from_wrist = (name == "back") == right  # whether fx counts out from the wrist on this face
            for fy in range(f.h):
                for fx in range(f.w):
                    d = (fx if from_wrist else f.w - 1 - fx) + 0.5
                    o = fy + 0.5
                    # inside the triangle wrist (0, 0), A (a_len, 0), B (bx, by)?
                    cross_b = bx * o - by * d  # <= 0 between the two rays
                    ex, ey = bx - a_len, by
                    cross_e = ex * o - ey * (d - a_len)  # >= 0 on the wrist's side of the edge A-B
                    edge_len = math.hypot(ex, ey)
                    along = ((d - a_len) * ex + o * ey) / (edge_len * edge_len)
                    dist_e = cross_e / edge_len
                    bite = scallop * math.sin(math.pi * max(0.0, min(1.0, along)))
                    if cross_b > 0 or dist_e < bite:
                        f.px(fx, fy, (0, 0, 0), alpha=0)
                        continue
                    c = MEMBRANE.c["base"]
                    r = math.hypot(d, o)
                    ang = math.atan2(o, d)
                    vein = abs((ang / tb) * 4 % 1.0 - 0.5) < 0.08 if tb else False
                    c = shade(c, 0.95 + 0.25 * min(1.0, r / max(a_len, b_len)))
                    if vein:
                        c = shade(c, 0.7)
                    if dist_e - bite < 1.5:
                        c = shade(c, 0.6)  # the trailing edge's hem
                    if f.rng.random() < 0.012:
                        c = mix(c, FROST.c["base"], 0.2)  # rime
                    f.px(fx, fy, c)
        for name in ("top", "bottom", "left", "right"):
            f = faces[name]
            for fy in range(f.h):
                for fx in range(f.w):
                    f.px(fx, fy, (0, 0, 0), alpha=0)
    return paint


# ----------------------------------------------------------------------------------------------- the model

def build():
    m = Model("emperor", texture_width=256)
    root = m.bone("root", (0, 0, 0))
    # the ice he is frozen in, heaved up and cracked round his chest (it stays when he bends)
    # (kept low and within his body's reach, BODY_RADIUS in EmperorEntity, so nobody walks through it)
    for k, (ang, w, tilt) in enumerate([(0, 14, 4), (55, 12, -5), (120, 14, 5), (180, 16, -3), (235, 12, 5), (300, 13, -5)]):
        a = math.radians(ang)
        cx, cz = 17 * math.sin(a), -11 * math.cos(a)
        root.cube(f"ice{k}", (cx - w / 2, -3, cz - 3), (w, 3, 6), ICE, rotation=(tilt, ang, 0), origin=(cx, -1.5, cz), detail=rimed)
    upper = root.bone("upper", (0, 0, 0))
    # the trunk: a deep ribcage like a ship's hull, great pectorals, the trapezius heaped to the neck, a spine of bony
    # knuckles down his back, shaggy flanks, all frosted where the ice holds him
    upper.cube("chest", (-17, -8, -9), (34, 10, 18), HIDE, detail=combo(frosted(6), scars(2)))
    upper.cube("ribcage", (-20, 2, -10), (40, 12, 20), HIDE, detail=combo(ribs, scars(3)))
    upper.cube("shoulders", (-21, 12, -8), (42, 6, 16), HIDE, detail=combo(rimed, collarbones))
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        upper.cube(f"{side}_pec", (x(1, 17), 6, -12), (17, 8, 3), HIDE, detail=pec)
        upper.cube(f"{side}_trapezius", (x(2, 16), 15, -6), (16, 5, 12), HIDE, rotation=(0, 0, -14 * s), origin=(2 * s, 18, 0),
                   detail=combo(rimed, occlude(bottom=2, k=0.75)))
        upper.cube(f"{side}_flank_fur", (x(17, 4), -6, -7), (4, 18, 14), FUR, detail=frosted(5))
    upper.cube("abdomen", (-9, -6, -10.5), (18, 9, 2), HIDE, detail=combo(frosted(5), occlude(top=2, k=0.7)))
    upper.cube("back_fur", (-15, -4, 9), (30, 18, 3), FUR, detail=frosted(5))
    for k, y in enumerate((16, 11, 6, 1)):
        upper.cube(f"spine{k}", (-1.5, y, 9.5 + (1 if k else 0)), (3, 3, 4), HORN, detail=spine_knob)

    neck = upper.bone("neck", (0, NECK_Y, 0))
    neck.cube("neck", (-7, 17, -6), (14, 7, 12), HIDE, detail=combo(occlude(top=2, k=0.6), rimed))
    neck.cube("throat", (-5, 17, -7.5), (10, 5, 2), HIDE, detail=occlude(top=2, k=0.6))
    head = neck.bone("head", (0, NECK_Y, 0))
    # one head, three faces: red in front, pale on the right, black on the left (his right is +X). The head itself
    # is the red face's; the side faces are plates on it, each with its own brow, nose and jaw.
    head.cube("head", (-10, 22, -10), (20, 20, 20), RED, detail=face_front(RED))
    head.cube("face_right", (10, 23, -8), (1, 18, 16), PALE, detail=face_side(PALE, "right"))
    head.cube("face_left", (-11, 23, -8), (1, 18, 16), BLACK, detail=face_side(BLACK, "left"))
    head.cube("crest", (-2, 41, -9), (4, 2, 18), RED, detail=rimed)
    # the red face: a frowning brow ridge, a broad flat nose, cheekbones, fangs, and the jaw hanging open on Judas
    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        head.cube(f"brow_front_{side}", (x(1, 8), 35.5, -12), (8, 2, 3), RED, rotation=(0, 0, 12 * s), origin=(s, 36, -10),
                  detail=brow_detail)
        head.cube(f"cheek_front_{side}", (x(5, 4), 30, -11.5), (4, 2, 2), RED, detail=occlude(bottom=1, k=0.7))
        head.cube(f"fang_front_{side}", (x(3.5, 2), 24, -10.8), (2, 4, 1), FANG)
    head.cube("nose_front", (-2, 29, -12), (4, 6, 2), RED, detail=occlude(top=2, k=0.8))
    head.cube("nostrils_front", (-3, 28, -12.5), (6, 2, 3), RED, detail=nostrils)
    head.cube("jaw_front", (-8, 18, -12), (16, 4, 8), RED, detail=jaw_detail)
    # the side faces: Brutus in the black mouth, Cassius in the pale
    for side, s, mat in (("right", 1, PALE), ("left", -1, BLACK)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        head.cube(f"brow_{side}", (x(11, 2), 35, -7), (2, 2, 14), mat, detail=brow_detail)
        head.cube(f"nose_{side}", (x(11, 2), 29, -2), (2, 6, 4), mat, detail=occlude(top=2, k=0.8))
        head.cube(f"nostrils_{side}", (x(11, 2.5), 28, -2.5), (2, 2, 5), mat, inflate=0.2, detail=nostrils)
        head.cube(f"cheek_{side}", (x(10.5, 2), 30, -7), (2, 2, 14), mat, detail=occlude(bottom=1, k=0.7))
        head.cube(f"jaw_{side}", (x(9, 5), 19, -6), (5, 4, 12), mat, detail=jaw_detail)
        for z in (-5, 3):
            head.cube(f"fang_{side}{z}", (x(10.6, 1), 24, z), (1, 4, 2), FANG)
        for k, z in enumerate((-2.5, 0.5)):
            head.cube(f"sinner_{side}{k}", (x(10.5, 2), 24, z), (2, 7, 2), SINNER, rotation=(0, 0, (-115 - 20 * k) * s),
                      origin=(11.5 * s, 25, z + 1), detail=occlude(top=1, k=0.75))
    for k, xx in enumerate((-3, 1)):
        head.cube(f"sinner_front{k}", (xx, 23, -18), (2, 2, 7), SINNER, rotation=(-28 - 12 * k, 10 - 20 * k, 0), origin=(xx + 1, 24, -11),
                  detail=occlude(bottom=1, k=0.75))
    # icicles of frozen tears and slaver on the jaws
    for k, (xx, zz) in enumerate(((-5, -11.5), (0, -11.8), (4, -11.5))):
        head.cube(f"icicle{k}", (xx, 15, zz), (1, 3, 1), FROST, inflate=-0.1)
    # great horns, curving out and up from the crown in four tapering lengths
    for side, s in (("right", 1), ("left", -1)):
        bx, by = 6.0, 41.0
        for k, (ang, length, t) in enumerate([(62, 6, 5), (40, 6, 4), (14, 5, 3), (-12, 5, 2)]):
            a = math.radians(ang)
            ox = bx * s
            head.cube(f"{side}_horn" if k == 0 else f"{side}_horn{k}", (ox - t / 2, by, -t / 2 - 1), (t, length + 1, t), HORN,
                      rotation=(-6 * k, 0, -ang * s), origin=(ox, by, -1))
            bx += length * math.sin(a)
            by += length * math.cos(a)
    # his six eyes (shown once they open), in the sockets under the brows
    eyes = head.bone("eyes", (0, 30, 0))
    for x in (-7, 3):
        eyes.cube(f"eye_front{x}", (x, 34, -10.5), (4, 2, 1), FROST, detail=eye_glow)
    for z in (-6, 2):
        eyes.cube(f"eye_right{z}", (10.5, 34, z), (1, 2, 4), FROST, detail=eye_glow)
        eyes.cube(f"eye_left{z}", (-11.5, 34, z), (1, 2, 4), FROST, detail=eye_glow)

    for side, s in (("right", 1), ("left", -1)):
        def x(a, w):
            return a if s == 1 else -(a + w)
        arm = upper.bone(f"{side}_arm", (SHOULDER_X * s, SHOULDER_Y, 0), rotation=(AT_REST, 0, 0))
        # a mountain of a shoulder, the upper arm with its biceps, the elbow with a spur of bone, the forearm in cords,
        # the wrist, and the hand: a broad palm, four clawed fingers bent to grip the ice and a thumb
        arm.cube(f"{side}_deltoid", (x(18, 12), 5, -6), (12, 12, 12), HIDE, detail=combo(rimed, scars(1)))
        arm.cube(f"{side}_arm", (x(19, 10), -2, -5), (10, 9, 10), HIDE, detail=combo(corded, occlude(top=1, k=0.75)))
        arm.cube(f"{side}_biceps", (x(20.5, 7), -1, -6.5), (7, 6, 2), HIDE, detail=occlude(bottom=1, k=0.7))
        arm.cube(f"{side}_elbow", (x(19.5, 9), -4, -4.5), (9, 4, 9), HIDE)
        arm.cube(f"{side}_elbow_spur", (x(23, 2), -5, 3.5), (2, 5, 2), HORN, rotation=(-35, 0, 0), origin=(24 * s, -3, 4.5))
        arm.cube(f"{side}_forearm", (x(19.5, 9), -10, -4.5), (9, 7, 9), HIDE, detail=combo(corded, frosted(3)))
        arm.cube(f"{side}_wrist", (x(20, 8), -11, -4), (8, 2, 8), HIDE, detail=frosted(2))
        arm.cube(f"{side}_hand", (x(18, 12), -14, -5), (12, 4, 10), HIDE, detail=combo(knuckles, frosted(4)))
        for k in range(4):
            fx = 18.2 + k * 3
            arm.cube(f"{side}_finger{k}", (x(fx, 3), -17.5, -4.5), (3, 4, 3), HIDE, rotation=(12, 0, 0), origin=(24 * s, -14, -3),
                     detail=frosted(4))
            arm.cube(f"{side}_claw{k}", (x(fx + 0.5, 2), -20, -5), (2, 3, 2), HORN, rotation=(40, 0, 0), origin=(24 * s, -17, -3.5))
        arm.cube(f"{side}_claws", (x(17, 2), -16.5, -5), (2, 4, 2), HIDE, rotation=(10, 0, 18 * s), origin=(18 * s, -13, -4),
                 detail=frosted(4))
        arm.cube(f"{side}_thumb_claw", (x(16.5, 2), -19, -5.5), (2, 3, 2), HORN, rotation=(30, 0, 18 * s), origin=(18 * s, -13, -4))
        # three great bat's wings on each side, rising behind him
        for i, (y, spread) in enumerate([(20, 55), (16, 20), (12, -15)]):
            wing = upper.bone(f"{side}_wing{i}", (16 * s, y, 8), rotation=(0, -15 * s, spread * s))
            build_wing(wing, side, s, i, y)
    animations(m)
    return m


# the wing's shape in its own plane, from its root (pixels out and up): the wrist, and the three fingers fanned from it
WRIST = (16.0, 24.0)
FINGERS = [(8, 29), (-30, 31), (-62, 30)]  # (degrees above the outward line, length)


def build_wing(wing, side, s, i, y):
    """One bat's wing: the arm from the root to the wrist, three fingers fanned out from there, a membrane between each
    pair of fingers and one from the last finger back to his body, and the claw at the wrist."""
    rx, ry = 16.0 * s, float(y)
    wx, wy = WRIST
    z = 8.0
    arm_ang = math.degrees(math.atan2(wy, wx))
    arm_len = math.hypot(wx, wy)
    right = s == 1

    def rod(name, ox, oy, ang, length, t, mat):
        # a rod of bone lying along +X from (ox, oy) in the wing's plane, turned up by ang degrees
        wing.cube(name, ((ox if right else ox - length) if True else 0, oy - t / 2, z - t / 2 + 0.5), (length, t, t), mat,
                  rotation=(0, 0, ang * s), origin=(ox, oy, z + 0.5))

    # the arm: from the root to the wrist
    wing.cube(f"{side}_wing{i}", ((rx if right else rx - math.ceil(arm_len)), ry - 1.5, z - 1), (math.ceil(arm_len), 3, 3), WING_BONE,
              rotation=(0, 0, arm_ang * s), origin=(rx, ry, z + 0.5))
    wxx, wyy = rx + wx * s, ry + wy
    wing.cube(f"{side}_wing{i}_thumb", (wxx - 1, wyy, z - 0.5), (2, 4, 2), HORN, rotation=(0, 0, -25 * s), origin=(wxx, wyy, z + 0.5))
    rays = [(a, l) for a, l in FINGERS] + [(arm_ang - 180, arm_len)]
    for k, (ang, length) in enumerate(FINGERS):
        rod(f"{side}_wing{i}_finger{k}", wxx, wyy, ang, length, 2 if k else 2, WING_BONE)
    for k in range(len(rays) - 1):
        a_ang, a_len = rays[k]
        b_ang, b_len = rays[k + 1]
        if k == len(rays) - 2:
            b_len *= 0.92  # the last web ends a little short of his body
        gap = (a_ang - b_ang) % 360
        width = max(1, math.ceil(b_len * math.sin(math.radians(gap))))
        length = max(1, math.ceil(max(a_len, b_len * math.cos(math.radians(gap)))))
        oy = wyy - width
        ox = wxx if right else wxx - length
        wing.cube(f"{side}_wing{i}_web{k}", (ox, oy, z), (length, width, 1), MEMBRANE, rotation=(0, 0, a_ang * s), origin=(wxx, wyy, z + 0.5),
                  detail=membrane(right, a_len, gap, b_len, 2.5 + 0.06 * a_len))


ZERO = (0, 0, 0)


def windmill(a, length, amount=25, phase=0.0):
    """His wings turning: each pair beats a little after the one above it."""
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            t0 = (phase + i * 0.15) * length
            keys = [(0, (0, 0, 0)), (length * 0.5, (0, 0, -amount * s)), (length, (0, 0, 0))]
            a.rot(f"{side}_wing{i}", [(t, (vx, vy, vz)) for t, (vx, vy, vz) in keys])
            del t0


def rest_arms(a, length):
    for side in ("right", "left"):
        a.rot(f"{side}_arm", [(0, ZERO), (length, ZERO)])


def animations(m):
    # always (under everything else): breathing, the wings turning slow and cold like a windmill's sails
    idle = m.animation("idle", 5.0, loop=True)
    windmill(idle, 5.0, 25)
    idle.pos("upper", [(0, ZERO), (2.5, (0, 1, 0)), (5.0, ZERO)])

    # asleep: head bowed (his eyes are shut: the game hides them), the wings still turning in the fog
    dormant = m.animation("dormant", 8.0, loop=True)
    dormant.rot("head", [(0, (-15, 0, 0)), (8.0, (-15, 0, 0))])

    # a hand raised high and brought down on the ice; it lies there until it lifts (a weak point)
    for side, s in (("right", 1), ("left", -1)):
        slam = m.animation(f"slam_{side}", 5.0)
        slam.rot(f"{side}_arm", [(0, ZERO), (1.2, (170 - AT_REST, 0, 10 * s)), (1.6, (SLAM - AT_REST, 0, 0)), (4.2, (SLAM - AT_REST, 0, 0)),
                                 (5.0, ZERO)], interp="linear")
        slam.rot("upper", [(0, ZERO), (1.2, (8, 8 * s, 0)), (1.6, (-12, -6 * s, 0)), (4.2, (-12, -6 * s, 0)), (5.0, ZERO)])
        slam.event(1.6, "slam").event(4.2, "lift")

    # both hands at once: raised high together and brought down either side of him (both lie on the ice after)
    both = m.animation("slam_both", 5.0)
    for side, s in (("right", 1), ("left", -1)):
        both.rot(f"{side}_arm", [(0, ZERO), (1.4, (175 - AT_REST, 0, 14 * s)), (1.8, (SLAM - AT_REST, 0, 0)), (4.2, (SLAM - AT_REST, 0, 0)),
                                 (5.0, ZERO)], interp="linear")
    both.rot("upper", [(0, ZERO), (1.4, (12, 0, 0)), (1.8, (-16, 0, 0)), (4.2, (-16, 0, 0)), (5.0, ZERO)])
    both.event(1.8, "slam").event(4.2, "lift")

    # a hand laid on the ice and dragged across it: the arm comes down low and stays there while he turns in the ice
    # (the game turns his whole body, so the hand sweeps round with him); "down" when it touches, "lift" at the end
    for side, s in (("right", 1), ("left", -1)):
        drag = m.animation(f"drag_{side}", 5.0)
        drag.rot(f"{side}_arm", [(0, ZERO), (0.8, (100 - AT_REST, 0, 25 * s)), (1.2, (SLAM - AT_REST, 0, 12 * s)),
                                 (4.0, (SLAM - AT_REST, 0, 12 * s)), (5.0, ZERO)], interp="linear")
        drag.rot("upper", [(0, ZERO), (1.2, (-10, 10 * s, 0)), (4.0, (-10, 10 * s, 0)), (5.0, ZERO)])
        drag.event(1.2, "down").event(4.0, "lift")

    # his frozen breath: the head drawn back to inhale, then thrust forward and down with the jaws wide while the cold
    # pours out (the game turns his whole body, so the breath sweeps across the ice); "breathe" to "end"
    breath = m.animation("breath", 5.0)
    breath.rot("head", [(0, ZERO), (0.9, (25, 0, 0)), (1.4, (-30, 0, 0)), (4.0, (-30, 0, 0)), (5.0, ZERO)])
    breath.rot("upper", [(0, ZERO), (0.9, (8, 0, 0)), (1.4, (-14, 0, 0)), (4.0, (-14, 0, 0)), (5.0, ZERO)])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            breath.rot(f"{side}_wing{i}", [(0, ZERO), (1.4, (0, -25 * s, 20 * s)), (4.0, (0, -25 * s, 20 * s)), (5.0, ZERO)])
    breath.event(0.9, "inhale").event(1.4, "breathe").event(4.0, "end")

    # a bite: he bends, a face comes down to the ice, flashes, bites, and stays low a moment (a weak point)
    for name, yaw in (("bite", 0), ("bite_right", 90), ("bite_left", -90)):
        bite = m.animation(name, 5.0)
        bite.rot("upper", [(0, ZERO), (1.4, (-BEND, 0, 0)), (4.0, (-BEND, 0, 0)), (5.0, ZERO)])
        bite.rot("neck", [(0, ZERO), (1.4, (-NOD, 0, 0)), (4.0, (-NOD, 0, 0)), (5.0, ZERO)])
        bite.rot("head", [(0, ZERO), (0.8, (0, yaw, 0)), (1.5, (10, yaw, 0)), (1.6, (-10, yaw, 0)), (4.0, (-10, yaw, 0)), (5.0, ZERO)])
        bite.event(1.0, "flash").event(1.5, "bite").event(4.0, "rise")

    # his wingbeat: the cold wind across the whole pit
    beat = m.animation("wingbeat", 3.0)
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            beat.rot(f"{side}_wing{i}", [(0, ZERO), (1.2, (0, -30 * s, 40 * s)), (1.6, (0, 30 * s, -50 * s)), (3.0, ZERO)])
    beat.rot("upper", [(0, ZERO), (1.2, (10, 0, 0)), (1.6, (-8, 0, 0)), (3.0, ZERO)])
    beat.event(1.6, "beat")

    # half health: he weeps with six eyes, and the tears fall as frozen blood
    weep = m.animation("weep", 4.0)
    windmill(weep, 2.0, 30)
    weep.rot("head", [(0, ZERO), (1.0, (25, 0, 0)), (3.0, (25, 0, 0)), (4.0, ZERO)])
    weep.event(1.5, "weep")

    # the reveal: the head lifts and six eyes open
    reveal = m.animation("reveal", 6.0)
    windmill(reveal, 3.0, 40)
    reveal.rot("head", [(0, (-15, 0, 0)), (2.0, (-15, 0, 0)), (3.0, (20, 0, 0)), (6.0, ZERO)])
    reveal.rot("upper", [(0, ZERO), (3.0, (10, 0, 0)), (6.0, ZERO)])
    reveal.event(2.5, "open").event(4.0, "roar")

    hurt = m.animation("hurt", 0.4)
    hurt.rot("upper", [(0, ZERO), (0.15, (3, 0, 0)), (0.4, ZERO)])

    # his end: he freezes, head bowed, the wings folding down, and weeps
    death = m.animation("death", 6.0)
    death.rot("head", [(0, ZERO), (3.0, (-35, 0, 0)), (6.0, (-35, 0, 0))])
    death.rot("upper", [(0, ZERO), (3.0, (-10, 0, 0)), (6.0, (-10, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        for i in range(3):
            death.rot(f"{side}_wing{i}", [(0, ZERO), (3.0, (0, 0, -60 * s)), (6.0, (0, 0, -60 * s))])
    death.event(3.0, "freeze")


def reach():
    """Where a slammed hand and a biting head come to rest, in blocks from his feet (forward, up, to his right)."""
    px = SCALE / 16.0
    th = math.radians(SLAM)
    hand_fwd = ARM * math.sin(th)
    hand_up = SHOULDER_Y - ARM * math.cos(th)
    print("slam hand: forward %.2f up %.2f side %.2f" % (hand_fwd * px, hand_up * px, (SHOULDER_X + 2) * px))
    b = math.radians(BEND)
    n = math.radians(BEND + NOD)
    head_fwd = NECK_Y * math.sin(b) + HEAD * math.sin(n)
    head_up = NECK_Y * math.cos(b) + HEAD * math.cos(n)
    print("bite head: forward %.2f up %.2f" % (head_fwd * px, head_up * px))


if __name__ == "__main__":
    build().write()
    reach()
