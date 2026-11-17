#!/usr/bin/env python3
"""The monsters and souls remade at twice the detail (two model pixels to a game pixel; drawn at half size by their
renderers): the glutton, the wrathful, the hoarder, the heretic, the centaur, the harpy, the mimic, the thief, the
Malebranche and Malacoda, the angel, the damned and Limbo's shade.

Each body is carried over part for part from its old script (tools/art/legacy/<name>.py through remake_kit's port),
its materials painted afresh at the finer grain and its hand-painted details brought across, so every skeleton and
animation is as it was. On top of that each gets a new face (tools/art/remake/faces.py: eyes in their sockets, brows,
a nose with its tip, cheekbones, a mouth), fingers and toes told apart, and its own touches below.

    python3 tools/art/remake/mobs.py [name ...] && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import faces  # noqa: E402
import remake_kit as kit  # noqa: E402
from bbmodel import hexc, material, shade  # noqa: E402
from paintkit import ao, cracks, fill, get, hline, line, speckle, tint, tone, tone_rect, tuck, veins, vline  # noqa: E402

TOOTH = material("tooth", "flat", base="d8ccb0")
SIDES = (("right", 1), ("left", -1))


def X(a, w, s):
    return a if s == 1 else -(a + w)


def digits(fs):
    """Fingers or toes told apart: dark gaps down the wider side faces and across the end."""
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        if f.w < 4:
            continue
        count = 4 if f.w >= 6 else 3
        for i in range(1, count):
            x = round(i * f.w / count)
            for y in range(f.h // 4, f.h):
                tone(f, x, y, 0.6)
    b = fs["bottom"]
    wide = b.w >= b.h
    n = b.w if wide else b.h
    count = 4 if n >= 6 else 3
    for i in range(1, count):
        k = round(i * n / count)
        for j in range(b.h if wide else b.w):
            tone(b, k if wide else j, j if wide else k, 0.6)


def parts(*extra):
    """port's `also`: fingers and toes told apart on every hand and foot, plus the named extras."""
    named = dict(extra)

    def also(name):
        if name in named:
            return named[name]
        if any(k in name for k in ("fingers", "toes", "knuckles")):
            return digits
        return None
    return also


def fang(fs):
    for n in ("front", "back", "left", "right"):
        hline(fs[n], 0, 0.75)


# ----------------------------------------------------------------------------------------------- Upper Hell

def glutton():
    b = kit.Remake("glutton")
    b.port("glutton", skip=("brow",), also=lambda n: None)
    L = b.legacy

    def more(f):
        # folds of fat under each eye and round the mouth, grease and mud on the chin
        for y in (f.h - 3, f.h - 2):
            for x in range(1, f.w - 1):
                if (x + y) % 3 == 0:
                    tone(f, x, y, 0.8)
        speckle(f, hexc("3a2c1a"), 0.08, 0.5)
    b.cubes = [c for c in b.cubes if c["name"] != "head"]
    o = b.ported["head"]
    b.cube("head", "head", tuple(v * 2 for v in o.frm), tuple(s * 2 for s in o.size), o.material,
           detail=lambda fs: (kit.ported(o)(fs), faces.paint(o.material, hexc("e8d84a"), hexc("fff8c0"), mouth=None, lines=False,
                                                             eye_y=0.2, extra=more)(fs)))
    faces.features(b, "head", (-7, 47, -12, 14, 10, 12), o.material, tilt=-8, nose=0, cheek=False, eye_y=0.2)
    for side, s in SIDES:
        b.cube("head", f"tusk_{side}", (X(2.6, 1, s), 46, -14.6), (1, 3, 1), TOOTH, detail=fang)
        b.cube("head", f"jowl_fold_{side}", (X(5, 4, s), 44.4, -13.6), (4, 3, 2), o.material, detail=ao(1, 0, 0.75))
    b.finish()


def wrathful():
    b = kit.Remake("wrathful")
    b.port("wrathful", skip=("right_brow", "left_brow", "nose", "cheeks", "teeth"),
           also=parts())
    L = b.legacy
    sk = b.ported["skull"]
    for c in b.cubes:
        if c["name"] == "skull":
            old = c["detail"]
            c["detail"] = lambda fs, old=old: ((old(fs) if old else None), faces.paint(
                L.SKIN, L.EYE, L.EYE_HOT, mouth="teeth", extra=lambda f: veins(f, 2, L.VEIN, 0.5))(fs))
    faces.features(b, "head", (-6, 49, -8, 12, 12, 12), L.SKIN, brow=L.HAIR, tilt=18, nose=4, hook=2.2)
    b.finish()


def hoarder():
    b = kit.Remake("hoarder")
    b.port("hoarder", skip=("brow", "nose", "nose_tip"), also=parts())
    L = b.legacy
    _face(b, "head", L.SKIN, L.EYE, L.EYE_HOT, socket=L.SOCKET, mouth="line")
    faces.features(b, "head", (-6, 48, -12, 12, 12, 12), L.SKIN, tilt=-8, nose=5.5, hook=4.0)
    for k, (x, y) in enumerate(((-3, 60), (2, 62), (0, 58))):   # more gold spilling from the sack's mouth
        b.cube("sack", f"spilt_coin_{k}", (x, y, 12.5), (2, 2, 1), L.GOLD)
    b.finish()


def _face(b, cube, skin, eye, hot=None, **kw):
    """Repaints the front of a ported head cube with a new face, after whatever it carried over."""
    for c in b.cubes:
        if c["name"] == cube:
            old = c["detail"]
            painter = faces.paint(skin, eye, hot, **kw)
            c["detail"] = lambda fs, old=old: ((old(fs) if old else None), painter(fs))
            return
    raise KeyError(cube)


# ----------------------------------------------------------------------------------------------- Lower Hell

def heretic():
    b = kit.Remake("heretic")
    b.port("heretic", skip=("brow",), also=parts())
    L = b.legacy

    def burning(f):
        for _ in range(3):   # cracks in the char with fire showing through
            x, y = f.rng.randrange(f.w), f.rng.randrange(f.h)
            for i in range(f.rng.randint(2, 4)):
                f.glow(x, y, L.EMBER if i % 2 else L.EMBER_DEEP)
                x += f.rng.choice((-1, 0, 1))
                y += 1
    _face(b, "head", L.CHAR, L.EMBER_HOT, L.FLAME_CORE, socket=L.SOCKET, mouth="open", lines=False, extra=burning)
    faces.features(b, "head", (-6, 52, -7, 12, 10, 12), L.CHAR, tilt=10, nose=3, hook=1.4)
    b.finish()


def centaur():
    b = kit.Remake("centaur")
    b.port("centaur", skip=("brow", "nose"), also=parts())
    L = b.legacy
    _face(b, "head", L.SKIN, L.EYE, hexc("ffd08a"), socket=L.SOCKET, mouth=None)
    faces.features(b, "head", (-6, 66, -20, 12, 12, 12), L.SKIN, brow=L.MANE, tilt=14, nose=4.5, hook=2.4)
    for side, s in SIDES:
        b.cube("head", f"moustache_{side}", (X(0.3, 4, s), 66.6, -21.8), (4, 2, 1), L.MANE, rotation=(0, 0, 14 * s),
               origin=(0.3 * s, 68, -21))
        for leg in ("front", "back"):   # feathering over each hoof
            z = -12 if leg == "front" else 18
            b.cube(f"{leg}_{side}_leg", f"{leg}_{side}_feather", (X(3.4, 5, s), 2.6, z + 1), (5, 4, 3), L.POINTS, detail=ao(2, 0, 0.6))
    b.finish()


def harpy():
    b = kit.Remake("harpy")
    b.port("harpy", skip=("brow", "nose", "mouth"), also=parts())
    L = b.legacy
    _face(b, "head", L.SKIN, L.EYE, hexc("ffffc0"), socket=L.SOCKET, mouth="teeth")
    faces.features(b, "head", (-6, 44, -8, 12, 12, 12), L.SKIN, brow=L.HAIR, tilt=16, nose=4.5, hook=3.2, tip=False)
    b.cube("head", "nose_hook", (-1, 46.4, -12.2), (2, 3, 2), L.SKIN, rotation=(-30, 0, 0), origin=(0, 49, -11))
    b.finish()


def mimic():
    b = kit.Remake("mimic")
    b.port("mimic")
    b.finish()


def thief():
    b = kit.Remake("thief")
    b.port("thief", skip=("brow", "nose", "right_cheek", "left_cheek"), also=parts())
    L = b.legacy
    _face(b, "skull", L.SKIN, L.EYE, hexc("f0ffb0"), mouth="line")
    faces.features(b, "head", (-5, 49, -5, 10, 10, 10), L.SKIN, tilt=12, nose=4, hook=2.0)
    for side, s in SIDES:   # the serpent's fangs
        b.cube("serpent", f"serpent_fang_{side}", (9 + 1.2 * s - 0.5, 52, -9), (1, 2, 1), TOOTH, detail=fang)
    b.finish()


def _demon(name, skull, box):
    b = kit.Remake(name)
    b.port(name, also=parts())
    L = b.legacy
    skin = b.ported[skull].material
    eye = getattr(L, "EYE", hexc("ffb020"))

    def eyes(fs):
        f = fs["front"]
        ey = round(f.h * 0.32)
        for x0, inner in ((1, 2), (f.w - 5, 1)):
            f.rect(x0, ey, 4, 3, shade(skin.c["dark"], 0.4))
            f.glow(x0 + inner, ey + 1, eye)
            f.glow(x0 + 3 - inner, ey + 1, hexc("fff0a0"))
            f.glow(x0 + inner, ey + 2, shade(eye, 0.7))
        for y in range(ey + 4, f.h - 1):
            tone(f, 0, y, 0.75)
            tone(f, f.w - 1, y, 0.75)
        cracks(f, 2, 0.7, 4)
    for c in b.cubes:
        if c["name"] == skull:
            old = c["detail"]
            c["detail"] = lambda fs, old=old: ((old(fs) if old else None), eyes(fs))
    x0, y0, z0, w, h, d = box
    for side, s in SIDES:
        b.cube("head", f"cheek_spike_{side}", (X(w / 2 - 0.6, 2, s), y0 + 3, z0 + 2), (2, 2, 3), skin, rotation=(0, 20 * s, 0),
               origin=(X(w / 2 - 0.6, 2, s) + 1, y0 + 4, z0 + 2))
        b.cube("head", f"brow_horn_{side}", (X(1.5, 2, s), y0 + h - 0.5, z0 + 1), (2, 3, 2), TOOTH, rotation=(-20, 0, -12 * s),
               origin=(X(1.5, 2, s) + 1, y0 + h, z0 + 2), detail=fang)
    return b


def malebranche():
    b = _demon("malebranche", "skull", (-5, 48, -5, 10, 10, 10))
    for side, s in SIDES:
        b.cube("head", f"fang_{side}", (X(1.6, 1, s), 46.8, -8.6), (1, 3, 1), TOOTH, detail=fang)
    b.finish()


def malacoda():
    b = _demon("malacoda", "head", (-6, 58, -6, 12, 12, 12))
    for side, s in SIDES:
        b.cube("head", f"fang_{side}", (X(2.2, 1, s), 57.4, -7.4), (1, 3, 1), TOOTH, detail=fang)
    b.finish()


# ----------------------------------------------------------------------------------------------- souls and angels

def angel():
    b = kit.Remake("angel")
    b.port("angel", skip=("brow", "nose"), also=lambda n: quill if ("feather" in n or "covert" in n) else (digits if "fingers" in n else None))
    L = b.legacy
    _face(b, "cranium", L.SKIN, hexc("5aa8f0"), hexc("2a5a9a"), white=hexc("ffffff"), socket=shade(L.SKIN.c["dark"], 0.8),
          mouth=None, lines=False, eye_y=0.5, hollow=0.95, glow=False)
    faces.features(b, "head", (-5, 64.8, -4.8, 10, 10, 10), L.SKIN, brow=L.HAIR, tilt=-5, nose=3.5, hook=1.6, eye_y=0.5,
                   cheek=False)
    b.cube("head", "lips", (-1.5, 63.4, -5.3), (3, 1, 1), material("lips", "flat", base="e0a090"))
    b.finish()


def quill(fs):
    for n in ("front", "back"):
        f = fs[n]
        if f.w < 3 or f.h < 6:
            continue
        mid = f.w // 2
        for y in range(f.h - 2):
            for x in range(f.w):
                if not get(f, x, y) or f.glow_canvas.get(f.x + x, f.y + y)[3]:
                    continue
                if x == mid:
                    tone(f, x, y, 1.08)
                elif (x + y) % 3 == 0:
                    tone(f, x, y, 0.92)


def damned():
    b = kit.Remake("damned")
    b.port("damned", also=parts())
    L = b.legacy
    _face(b, "head", L.SKIN, L.EYE, hexc("e8f4ff"), socket=L.SOCKET, mouth=None, hollow=0.72)
    faces.features(b, "head", (-6, 48, -6, 12, 12, 12), L.SKIN, tilt=-12, nose=4, hook=1.8)
    for side, s in SIDES:   # ribs showing under the shroud's edge
        for k in range(3):
            b.cube("body", f"rib_{side}_{k}", (X(1, 6, s), 36 + k * 2.4, -4.6), (6, 1, 1), L.SKIN, detail=ao(1, 0, 0.7))
    b.finish()


def shade_():
    b = kit.Remake("shade")
    b.port("shade", skip=("brow", "nose"), also=parts())
    L = b.legacy
    _face(b, "head", L.SKIN, L.EYE, hexc("ffffff"), mouth=None, hollow=0.88, eye_y=0.36)
    faces.features(b, "head", (-6, 50, -6, 12, 14, 12), L.SKIN, brow=L.HAIR, tilt=4, nose=5, hook=2.4, eye_y=0.36)
    for k, (x, r) in enumerate(((-5, -30), (-2, -12), (2, 12), (5, 30))):   # more leaves on the laurel
        b.cube("head", f"laurel_leaf_{k}", (x - 1, 62.4, -7.6), (2, 3, 1), L.LAUREL, rotation=(0, 0, r), origin=(x, 62.4, -7))
    b.finish()


MOBS = {"glutton": glutton, "wrathful": wrathful, "hoarder": hoarder, "heretic": heretic, "centaur": centaur,
        "harpy": harpy, "mimic": mimic, "thief": thief, "malebranche": malebranche, "malacoda": malacoda, "angel": angel,
        "damned": damned, "shade": shade_}

if __name__ == "__main__":
    for name in (sys.argv[1:] or MOBS):
        MOBS[name]()
