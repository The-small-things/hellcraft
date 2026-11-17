"""Brushes for a painted face (tools/art/bbmodel.py's Face), shared by the remakes in tools/art/remake: shading that
rounds a box (tuck, rim, grad), lines and grooves, veins, cracks, eyes and teeth. Face coordinates: (0, 0) is the top
left seen from outside; on a "right" face the model's front is at the right edge, on a "left" face at the left edge,
on "top" the front is the bottom row."""
from bbmodel import mix, shade

SIDES = ("front", "back", "left", "right")


def get(f, x, y):
    if 0 <= x < f.w and 0 <= y < f.h:
        c = f.canvas.get(f.x + x, f.y + y)
        return c if c[3] else None
    return None


def tone(f, x, y, k):
    """Darkens (k < 1) or lights (k > 1) one pixel, as painted so far."""
    c = get(f, x, y)
    if c:
        f.px(x, y, shade(c[:3], k), c[3])


def tint(f, x, y, colour, t=0.5):
    c = get(f, x, y)
    if c:
        f.px(x, y, mix(c[:3], colour, t), c[3])


def tone_rect(f, x0, y0, w, h, k):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            tone(f, x, y, k)


def tint_rect(f, x0, y0, w, h, colour, t=0.5):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            tint(f, x, y, colour, t)


def tone_all(f, k):
    tone_rect(f, 0, 0, f.w, f.h, k)


def tint_all(f, colour, t=0.5):
    tint_rect(f, 0, 0, f.w, f.h, colour, t)


def grad(f, top=1.1, bottom=0.8):
    """Light to dark down a face."""
    for y in range(f.h):
        k = top + (bottom - top) * y / max(1, f.h - 1)
        for x in range(f.w):
            tone(f, x, y, k)


def tuck(faces, rows=3, k=0.7, lit=1.08, names=SIDES):
    """Shadow where a part tucks under (its lowest rows darkest) and light along its top: sells a rounded form."""
    for name in names:
        f = faces[name]
        n = min(rows, f.h)
        for i in range(n):
            for x in range(f.w):
                tone(f, x, f.h - 1 - i, k + (1 - k) * i / n)
        for x in range(1, f.w - 1):
            tone(f, x, 0, lit)
    tone_all(faces["bottom"], 0.8)


def round_x(f, k=0.8, cols=2):
    """Darkens a face's left and right edges, as a cylinder turns away."""
    n = min(cols, f.w // 2)
    for i in range(n):
        for y in range(f.h):
            tone(f, i, y, k + (1 - k) * i / n)
            tone(f, f.w - 1 - i, y, k + (1 - k) * i / n)


def round_all(faces, k=0.82, cols=2, names=SIDES):
    for n in names:
        round_x(faces[n], k, cols)


def hline(f, y, k, x0=0, x1=None):
    for x in range(x0, f.w if x1 is None else x1):
        tone(f, x, y, k)


def vline(f, x, k, y0=0, y1=None):
    for y in range(y0, f.h if y1 is None else y1):
        tone(f, x, y, k)


def line(f, x0, y0, x1, y1, colour=None, k=None, t=1.0):
    """A line of pixels: a colour (mixed in by t) or a tone k."""
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        if colour is not None:
            tint(f, x, y, colour, t)
        else:
            tone(f, x, y, k)


def groove(f, x0, y0, x1, y1, k=0.7, lit=1.1):
    """A cut in the surface: a dark line with a lit lip under (or beside) it."""
    line(f, x0, y0, x1, y1, k=k)
    if abs(x1 - x0) >= abs(y1 - y0):
        line(f, x0, y0 + 1, x1, y1 + 1, k=lit)
    else:
        line(f, x0 + 1, y0, x1 + 1, y1, k=lit)


def veins(f, count, colour, t=0.6):
    """A few raised veins wandering down a face."""
    rng = f.rng
    for _ in range(count):
        x = rng.randrange(1, max(2, f.w - 1))
        for y in range(rng.randrange(0, 3), f.h - 1):
            tint(f, x, y, colour, t)
            if rng.random() < 0.35:
                x = max(0, min(f.w - 1, x + rng.choice((-1, 1))))


def cracks(f, count, k=0.6, length=None):
    """Cracks wandering across a face from random points."""
    rng = f.rng
    for _ in range(count):
        x, y = rng.randrange(f.w), rng.randrange(f.h)
        dx, dy = rng.choice(((1, 1), (-1, 1), (1, 0), (0, 1)))
        for _ in range(length or rng.randint(3, max(4, (f.w + f.h) // 4))):
            tone(f, x, y, k)
            if rng.random() < 0.5:
                x += dx
            else:
                y += dy
            if rng.random() < 0.2:
                dx = -dx


def speckle(f, colour, density=0.05, t=0.6):
    for y in range(f.h):
        for x in range(f.w):
            if f.rng.random() < density:
                tint(f, x, y, colour, t)


def blot(f, cx, cy, r, colour, t=0.7):
    """A soft round stain."""
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                tint(f, x, y, colour, t * (1 - 0.6 * d / max(r, 1)))


def glow_rect(f, x0, y0, w, h, colour, core=None):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            f.glow(x, y, colour if (edge or core is None) else core)


def eye(f, x0, y0, w, h, socket, iris, core=None, lid=None):
    """A glowing eye in a dark socket (the socket a pixel bigger all round), an optional lid line over it."""
    for y in range(y0 - 1, y0 + h + 1):
        for x in range(x0 - 1, x0 + w + 1):
            if get(f, x, y):
                f.px(x, y, socket)
    glow_rect(f, x0, y0, w, h, iris, core)
    if lid is not None:
        for x in range(x0 - 1, x0 + w + 1):
            f.px(x, y0 - 1, lid)


def teeth(f, y, colour, x0=0, x1=None, gap=2, h=2, k=0.8):
    """A row of teeth hanging from row y."""
    for x in range(x0, f.w if x1 is None else x1):
        if x % (gap + 1) != gap:
            for i in range(h):
                f.px(x, y + i, shade(colour, 1.0 if i == 0 else k))


def fill(f, colour, noise=0.06):
    for y in range(f.h):
        for x in range(f.w):
            f.px(x, y, shade(colour, 1 + (f.rng.random() - 0.5) * 2 * noise))


def rivets(f, colour, step=6, inset=1):
    for x in range(inset, f.w - inset, step):
        f.px(x, inset, colour)
        f.px(x, f.h - 1 - inset, colour)
