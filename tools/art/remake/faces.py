"""A face for a remade head (two model pixels to a game pixel), shared by the souls and monsters whose heads are a
box about 12 across: `paint` repaints the head's front (eyes in their sockets, the lines of the face, a mouth), to
use as port's `also` on the old head cube, and `features` adds the parts that stand out from it (brows, a nose with
its tip, cheekbones, lips). The old brow, nose, cheek and mouth cubes are left out of the port."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from bbmodel import hexc, shade  # noqa: E402
from paintkit import fill, hline, line, tone, tone_all, tone_rect, vline  # noqa: E402

TOOTH = hexc("d8ccb0")
DARK = hexc("1a0c0a")


def paint(skin, eye, hot=None, socket=None, white=None, mouth="line", lines=True, eye_y=0.34, hollow=0.8, extra=None,
          glow=True):
    """A detail for a head cube: its front face painted afresh. eye: the iris's colour (glowing unless glow is False),
    hot: its bright middle, white: the eyeball's colour for eyes that are not all light, mouth: "line", "teeth"
    (bared), "open" (gaping, teeth above and below) or None, extra(face): more paint."""
    socket_c = socket or shade(skin.c["dark"], 0.45)

    def detail(fs):
        f = fs["front"]
        skin.paint(f)
        w, h = f.w, f.h
        ey = round(h * eye_y)
        put = f.glow if glow else f.px
        tone_rect(f, 0, max(0, ey - 2), w, 2, 0.72)   # the brow's shadow
        if lines:
            for y in range(1, max(1, ey - 2), 2):
                for x in range(2, w - 2):
                    if (x + y * 2) % 5:
                        tone(f, x, y, 0.85)
        for x0, inner in ((1, 2), (w - 5, 1)):
            f.rect(x0, ey, 4, 3, socket_c)
            if white is not None:
                for x in range(x0, x0 + 4):
                    f.px(x, ey + 1, white)
            put(x0 + inner, ey + 1, eye)
            put(x0 + 3 - inner, ey + 1, hot or eye)
            if white is None:
                put(x0 + inner, ey + 2, shade(eye, 0.75))
            tone_rect(f, x0, ey + 3, 4, 1, 0.78)       # the bag under the eye
        for y in range(ey + 3, h - 1):                  # hollow cheeks
            for x, k in ((0, hollow), (1, (hollow + 1) / 2), (w - 2, (hollow + 1) / 2), (w - 1, hollow)):
                tone(f, x, y, k)
        line(f, 3, ey + 4, 2, h - 3, k=0.75)            # from the nose to the mouth's corners
        line(f, w - 4, ey + 4, w - 3, h - 3, k=0.75)
        my = h - 3
        if mouth == "line":
            for x in range(3, w - 3):
                tone(f, x, my, 0.5)
            tone(f, 2, my + (1 if lines else 0), 0.6)
            tone(f, w - 3, my + (1 if lines else 0), 0.6)
        elif mouth == "teeth":
            f.rect(2, my - 1, w - 4, 3, DARK)
            for x in range(2, w - 2):
                f.px(x, my - 1 if x % 2 else my, shade(TOOTH, 1.0 if x % 2 else 0.85))
                f.px(x, my + 1, shade(TOOTH, 0.8) if x % 2 == 0 else DARK)
        elif mouth == "open":
            f.rect(3, my - 2, w - 6, 4, DARK)
            for x in range(3, w - 3):
                if x % 2:
                    f.px(x, my - 2, TOOTH)
                else:
                    f.px(x, my + 1, shade(TOOTH, 0.85))
            f.rect(w // 2 - 1, my, 2, 1, hexc("5a1a1a"))
        tone_rect(f, 0, h - 1, w, 1, 0.75)
        if extra:
            extra(f)
    return detail


def _brow(fs):
    tone_all(fs["bottom"], 0.45)
    f = fs["front"]
    hline(f, f.h - 1, 0.75)


def _nose(fs):
    f = fs["front"]
    vline(f, 0, 1.1)
    vline(f, f.w - 1, 0.82)
    for n in ("left", "right"):
        tone_all(fs[n], 0.84)
    b = fs["bottom"]
    tone_all(b, 0.6)
    b.px(0, 0, DARK)
    b.px(b.w - 1, 0, DARK)


def _lips(colour):
    def detail(fs):
        f = fs["front"]
        fill(f, colour, 0.04)
        hline(f, f.h - 1, 0.7)
    return detail


def features(b, bone, box, skin, brow=None, tilt=10, nose=4.5, hook=2.2, cheek=True, lips=None, eye_y=0.34, tip=True):
    """The parts that stand out of a face on the head box (x0, y0, z0, w, h, d; its front at z0): two brows (in
    `brow`'s material, tilted down toward the nose by `tilt` degrees: a scowl), a nose `nose` long standing `hook`
    out at its tip, cheekbones, and lips of the given colour."""
    x0, y0, z0, w, h, d = box
    ey = y0 + h - round(h * eye_y)          # the top of the eyes
    bw = int(w // 2 - 1)
    for s in (1, -1):
        def X(a, ww):
            return a if s == 1 else -(a + ww)
        if brow is not False:
            b.cube(bone, f"brow_{'right' if s == 1 else 'left'}", (X(0.4, bw), ey - 0.2, z0 - 1.2), (bw, 2, 2), brow or skin,
                   rotation=(0, 0, tilt * s), origin=(0.4 * s, ey + 0.8, z0), detail=_brow)
        if cheek:
            b.cube(bone, f"cheekbone_{'right' if s == 1 else 'left'}", (X(w / 2 - 3.6, 3), ey - 5.2, z0 - 0.6), (3, 2, 1), skin)
    if nose:
        b.stick(bone, "nose", (0, ey + 0.4, z0 - 0.2), (0, ey + 0.4 - nose, z0 - hook), 2, skin, depth=2, detail=_nose)
        if tip:
            b.cube(bone, "nose_tip", (-1.5, ey - nose - 0.6, z0 - hook - 0.9), (3, 2, 2), skin, detail=_nose)
    if lips is not None:
        b.cube(bone, "lips", (-2, y0 + 2, z0 - 0.5), (4, 1, 1), skin, detail=_lips(lips))
