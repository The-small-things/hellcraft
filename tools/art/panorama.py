#!/usr/bin/env python3
"""Paints the title screen's panorama: Hell seen from inside the City of Dis, as six cube faces
(versions/26.3/src/main/resources/assets/minecraft/textures/gui/title/background/panorama_<0-5>.png, which replace
Minecraft's own; client/title/HellTitle darkens them and adds the embers).

The funnel is a heightfield of terraces stepping down to Cocytus (the circles' floors, their cliffs, the Styx, the
red ring of Phlegethon, Malebolge's ditches), the Walls of Dis ring the camera with glowing towers, and in the pit
the Emperor stands in the ice with his six wings spread. Each pixel is a ray marched through it, lit by the glow from
below and lost in red haze with distance; the sky is smoke over a burning horizon, with embers.

Run from the repo root:  python3 tools/art/panorama.py [--size 1024] [--preview]
--preview also writes versions/26.3/build/art-preview/panorama.png (the four side faces in a strip).
The faces follow Minecraft's panorama screenshots (Minecraft.grabPanoramixScreenshot): 0 ahead, 1-3 turning right,
4 straight up (the top of the image behind you), 5 straight down (the top of the image ahead).
"""
import argparse
import math
import os
from multiprocessing import Pool

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "versions", "26.3")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "minecraft", "textures", "gui", "title", "background")
PREVIEW = os.path.join(ROOT, "build", "art-preview")

# the camera: high on the Walls of Dis, between two towers, looking down at the pit (north, -Z)
CAM = np.array([0.0, 212.0, 418.0])
YAW = 180.0

# the circles, outermost first: (outer radius, floor height, colour, cliff colour)
BANDS = [
    (1400, 330, (34, 30, 26), (40, 30, 26)),     # the rim and the Dark Wood
    (900, 300, (40, 44, 34), (58, 50, 44)),      # the Vestibule
    (820, 272, (70, 74, 64), (66, 60, 56)),      # Limbo
    (740, 246, (66, 46, 80), (60, 46, 70)),      # Lust
    (660, 222, (62, 46, 30), (56, 42, 30)),      # Gluttony
    (580, 198, (92, 74, 30), (54, 44, 30)),      # Greed
    (500, 172, (26, 40, 34), (40, 44, 40)),      # Wrath (the Styx)
    (432, 128, (66, 26, 20), (70, 34, 28)),      # Heresy, inside the walls
    (360, 104, (78, 30, 22), (74, 40, 32)),      # the river of blood's ring
    (300, 92, (120, 88, 52), (90, 60, 40)),      # the Burning Sands
    (270, 68, (54, 44, 40), (60, 50, 46)),       # Malebolge
    (150, 30, (46, 40, 44), (52, 48, 56)),       # the Well of Giants
    (112, 0, (150, 176, 196), (90, 110, 130)),   # Cocytus
]
RADII = np.array([b[0] for b in BANDS], dtype=float)
FLOORS = np.array([b[1] for b in BANDS], dtype=float)
COLOURS = np.array([b[2] for b in BANDS], dtype=float)
CLIFFS = np.array([b[3] for b in BANDS], dtype=float)
FOG = np.array([110.0, 22.0, 12.0])
DIS_INNER, DIS_OUTER, DIS_TOP = 432.0, 440.0, 186.0
LUCIFER = np.array([0.0, 0.0, 0.0])


def hash2(x, z, seed=0):
    h = (x.astype(np.int64) * 374761393 + z.astype(np.int64) * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


def vnoise(x, z, scale, seed=0):
    """Smooth value noise in [0, 1]."""
    x = x / scale
    z = z / scale
    x0 = np.floor(x)
    z0 = np.floor(z)
    fx = x - x0
    fz = z - z0
    fx = fx * fx * (3 - 2 * fx)
    fz = fz * fz * (3 - 2 * fz)
    a = hash2(x0, z0, seed)
    b = hash2(x0 + 1, z0, seed)
    c = hash2(x0, z0 + 1, seed)
    d = hash2(x0 + 1, z0 + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def band_of(x, z):
    r = np.hypot(x, z)
    ang = np.arctan2(z, x)
    # the cliffs wander a little round the funnel
    r = r + 7 * np.sin(ang * 9) + 4 * np.sin(ang * 23 + 1.3)
    idx = np.searchsorted(-RADII, -r, side="right") - 1
    return np.clip(idx, 0, len(BANDS) - 1), r, ang


def height(x, z):
    """The ground's height (with the Walls of Dis), and which band it is."""
    idx, r, ang = band_of(x, z)
    h = FLOORS[idx] + 3 * vnoise(x, z, 30.0) + 1.5 * vnoise(x, z, 7.0, 3)
    # the Styx lies flat
    h = np.where(idx == 6, FLOORS[6] + 0.3, h)
    # Phlegethon, a ring of blood sunk in its circle
    blood = (idx == 8) & (np.abs(r - 330) < 9)
    h = np.where(blood, FLOORS[8] - 3, h)
    # Malebolge's ditches: rings of ridges stepping down
    male = idx == 10
    h = np.where(male, h - 6 * (np.sin(r * 0.33) > 0.6), h)
    # the walls of Dis, crenellated, with towers every 30 degrees
    rr = np.hypot(x, z)
    wall = (rr > DIS_INNER) & (rr < DIS_OUTER)
    teeth = (np.sin(ang * rr * 0.9) > 0) * 3
    h = np.where(wall, DIS_TOP + teeth, h)
    h = np.where(tower_at(x, z), DIS_TOP + 26, h)
    return h, idx, r


def tower_at(x, z):
    """The towers on the walls, every 30 degrees (none just behind the camera)."""
    ang = np.arctan2(z, x)
    k = np.round((ang - math.pi / 12) / (math.pi / 6)) * (math.pi / 6) + math.pi / 12
    tx, tz = np.cos(k) * (DIS_INNER + 4), np.sin(k) * (DIS_INNER + 4)
    return np.hypot(x - tx, z - tz) < 9


def lucifer_sdf(p):
    """The Emperor in the ice: a body, a head of three faces, six wings. Negative inside."""
    x, y, z = p[..., 0] / 1.5, p[..., 1] / 1.5, p[..., 2] / 1.5

    def ellipsoid(c, rad):
        q = np.stack([(x - c[0]) / rad[0], (y - c[1]) / rad[1], (z - c[2]) / rad[2]], -1)
        return (np.linalg.norm(q, axis=-1) - 1) * min(rad)

    d = ellipsoid((0, 40, 0), (30, 70, 22))                     # the body, sunk to the chest in the ice
    d = np.minimum(d, ellipsoid((0, 122, 0), (20, 22, 18)))     # the head
    d = np.minimum(d, ellipsoid((-17, 118, 4), (9, 13, 9)))     # the faces either side
    d = np.minimum(d, ellipsoid((17, 118, 4), (9, 13, 9)))
    for side in (-1, 1):
        d = np.minimum(d, ellipsoid((side * 9, 152, 0), (3, 16, 3)))  # horns
        for k, (lift, spread) in enumerate(((60, 0.95), (25, 0.75), (-10, 0.55))):
            # each wing: a long thin blade from the shoulder, out and up, turned to face the viewer a little
            a = math.radians(30 + k * 28)
            cx, cy = side * math.cos(a) * 95 * spread + side * 20, 100 + lift
            u = np.stack([(x - cx), (y - cy), z], -1)
            ca, sa = math.cos(a * side), math.sin(a * side)
            ux = u[..., 0] * ca + u[..., 1] * sa
            uy = -u[..., 0] * sa + u[..., 1] * ca
            q = np.stack([ux / (105 * spread), uy / 26, u[..., 2] / 3.5], -1)
            d = np.minimum(d, (np.linalg.norm(q, axis=-1) - 1) * 3.5)
    return d * 1.5


def sky(dirs, rng):
    y = dirs[..., 1]
    t = np.clip(y, -0.2, 1)
    horizon = np.array([170.0, 44.0, 16.0])
    mid = np.array([60.0, 14.0, 10.0])
    top = np.array([10.0, 4.0, 5.0])
    w = np.clip(t / 0.25, 0, 1)[..., None]
    col = horizon * (1 - w) + mid * w
    w2 = np.clip((t - 0.25) / 0.75, 0, 1)[..., None]
    col = col * (1 - w2) + top * w2
    # rolling smoke
    ang = np.arctan2(dirs[..., 2], dirs[..., 0])
    smoke = vnoise(ang * 160, y * 140, 9.0, 11) * 0.6 + vnoise(ang * 400, y * 360, 9.0, 12) * 0.4
    col = col * (0.7 + 0.5 * smoke[..., None] * (1 - w2 * 0.5))
    # embers drifting up
    ember = rng.random(y.shape) < 0.0016 * np.clip(1 - y, 0.2, 1)
    col = np.where(ember[..., None], np.array([255.0, 170.0, 60.0]) * rng.uniform(0.5, 1, y.shape)[..., None], col)
    return col


def shade(points, dirs, t, idx, r, rng):
    x, z = points[..., 0], points[..., 2]
    e = 0.8
    hx = height(x + e, z)[0] - height(x - e, z)[0]
    hz = height(x, z + e)[0] - height(x, z - e)[0]
    n = np.stack([-hx, np.full_like(hx, 2 * e), -hz], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    steep = n[..., 1] < 0.55
    base = np.where(steep[..., None], CLIFFS[idx], COLOURS[idx])
    # strata on cliffs, grain on floors
    grain = vnoise(x * 1.0 + z * 0.3, points[..., 1] * 3.0, 4.0, 5) if True else 0
    base = base * (0.75 + 0.5 * grain[..., None])
    # light: the glow from the pit and a dim red sky
    to_pit = -np.stack([x, np.full_like(x, -260.0), z], -1)
    to_pit /= np.linalg.norm(to_pit, axis=-1, keepdims=True)
    lit = 0.35 + 0.65 * np.clip((n * to_pit).sum(-1), 0, 1)
    col = base * lit[..., None]
    rr = np.hypot(x, z)
    # what burns: Phlegethon's blood, the tombs of Heresy, the sands' fire, the towers' tops
    blood = (idx == 8) & (np.abs(r - 330) < 9)
    col = np.where(blood[..., None], np.array([200.0, 24.0, 18.0]) * (0.8 + 0.4 * vnoise(x, z, 6.0, 7))[..., None], col)
    cells = hash2(np.floor(x / 8), np.floor(z / 8), 21)
    fx, fz = (x / 8) % 1, (z / 8) % 1
    tomb = (idx == 7) & (cells > 0.82) & (rr < DIS_INNER - 4) & (np.abs(fx - 0.5) < 0.14) & (np.abs(fz - 0.5) < 0.22)
    col = np.where(tomb[..., None], np.array([255.0, 120.0, 30.0]) * (0.6 + 0.4 * cells[..., None]), col)
    sands = (idx == 9) & (hash2(np.floor(x / 3), np.floor(z / 3), 9) > 0.93)
    col = np.where(sands[..., None], np.array([255.0, 150.0, 50.0]), col)
    on_tower = tower_at(x, z)
    towers = on_tower & (points[..., 1] > DIS_TOP + 24)
    tower_wall = on_tower & ~towers & (points[..., 1] > 120)
    windows = (hash2(np.floor(points[..., 1] / 3), np.floor(np.arctan2(z, x) * 900), 6) > 0.9)
    col = np.where(tower_wall[..., None], np.where(windows[..., None], np.array([255.0, 110.0, 40.0]), np.array([58.0, 20.0, 16.0])), col)
    col = np.where(towers[..., None], np.array([255.0, 80.0, 30.0]), col)
    wall = (rr > DIS_INNER - 0.5) & (rr < DIS_OUTER + 0.5) & (points[..., 1] > 140) & ~on_tower
    col = np.where(wall[..., None], np.array([70.0, 22.0, 18.0]) * (0.7 + 0.6 * (hash2(np.floor(points[..., 1] / 2), np.floor(np.arctan2(z, x) * 300), 4) > 0.97))[..., None] + np.array([30.0, 6.0, 4.0]), col)
    # the Styx's dark water catches the red sky
    styx = idx == 6
    col = np.where(styx[..., None], np.array([30.0, 44.0, 38.0]) + 50 * np.clip(-dirs[..., 1:2] * 0 + 0.4 * vnoise(x, z, 12.0, 8)[..., None], 0, 1), col)
    # campfires on the upper circles
    fires = (idx < 6) & (hash2(np.floor(x / 5), np.floor(z / 5), 31) > 0.985)
    col = np.where(fires[..., None], np.array([255.0, 140.0, 40.0]), col)
    return col


def render_face(args):
    face, size = args
    rng = np.random.default_rng(100 + face)
    yaw = math.radians(YAW + 90 * (face if face < 4 else 0))
    f = np.array([-math.sin(yaw), 0.0, math.cos(yaw)])
    right = np.array([-math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.array([0.0, 1.0, 0.0])
    if face == 4:
        f, up = up, -np.array([-math.sin(math.radians(YAW)), 0.0, math.cos(math.radians(YAW))])
    elif face == 5:
        f, up = -up, np.array([-math.sin(math.radians(YAW)), 0.0, math.cos(math.radians(YAW))])
    s = (np.arange(size) + 0.5) / size * 2 - 1
    sx, sy = np.meshgrid(s, -s)
    dirs = f + sx[..., None] * right + sy[..., None] * up
    dirs /= np.linalg.norm(dirs, axis=-1, keepdims=True)
    flat = dirs.reshape(-1, 3)
    n = flat.shape[0]
    t = np.full(n, 0.5)
    hit = np.zeros(n, dtype=bool)
    luc = np.zeros(n, dtype=bool)
    alive = np.ones(n, dtype=bool)
    # rays that climb out over everything never hit anything
    t_max = 2600.0
    for _ in range(900):
        ids = np.nonzero(alive)[0]
        if ids.size == 0:
            break
        p = CAM + flat[ids] * t[ids, None]
        h, _, _ = height(p[:, 0], p[:, 2])
        under = p[:, 1] < h
        near_luc = (np.hypot(p[:, 0], p[:, 2]) < 330) & (p[:, 1] < 300)
        lu = np.zeros(ids.size, dtype=bool)
        if near_luc.any():
            lu[near_luc] = lucifer_sdf(p[near_luc]) < 0
        done = under | lu
        hit[ids[done]] = True
        luc[ids[lu & ~under]] = True
        gone = (t[ids] > t_max) | (p[:, 1] > 420) & (flat[ids, 1] > 0)
        alive[ids[done | gone]] = False
        step = np.maximum(0.35, t[ids] * 0.008)
        step = np.where(near_luc, np.minimum(step, 1.5), step)
        t[ids[~done]] += step[~done]
    # back up a little to the surface
    lo = np.maximum(0.0, t - np.maximum(0.35, t * 0.008))
    hi = t.copy()
    for _ in range(7):
        mid = (lo + hi) / 2
        p = CAM + flat * mid[:, None]
        h, _, _ = height(p[:, 0], p[:, 2])
        inside = p[:, 1] < h
        inside |= luc & (lucifer_sdf(p) < 0)
        hi = np.where(hit & inside, mid, hi)
        lo = np.where(hit & ~inside, mid, lo)
    t = np.where(hit, hi, t)
    pts = CAM + flat * t[:, None]
    col = sky(flat, rng)
    if hit.any():
        hp = pts[hit]
        h, idx, r = height(hp[:, 0], hp[:, 2])
        c = shade(hp, flat[hit], t[hit], idx, r, rng)
        lmask = luc[hit]
        if lmask.any():
            # the Emperor: black against the glow, his rims lit red from below, eyes burning
            lp = hp[lmask]
            body = np.array([22.0, 14.0, 16.0]) * (0.8 + 0.4 * vnoise(lp[:, 0] * 2, lp[:, 1] * 2, 3.0, 13))[:, None]
            rim = np.clip((lp[:, 1] - 20) / 160, 0, 1)[:, None]
            body = body + np.array([70.0, 12.0, 6.0]) * (1 - rim) * 0.5
            eyes = (np.abs(lp[:, 1] - 126) < 2.2) & (np.abs(np.abs(lp[:, 0]) - 6) < 2.5) | \
                   (np.abs(lp[:, 1] - 122) < 1.8) & (np.abs(np.abs(lp[:, 0]) - 18) < 2)
            body = np.where(eyes[:, None], np.array([255.0, 60.0, 20.0]), body)
            c[lmask] = body
        fog = 1 - np.exp(-t[hit] / 560.0)
        c = c * (1 - fog[:, None]) + FOG * fog[:, None]
        col[hit] = c
    img = np.clip(col.reshape(size, size, 3), 0, 255).astype(np.uint8)
    return face, img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--size", type=int, default=1024)
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    with Pool(min(6, os.cpu_count() or 1)) as pool:
        faces = dict(pool.map(render_face, [(f, args.size) for f in range(6)]))
    for f, img in faces.items():
        Image.fromarray(img).save(os.path.join(OUT, "panorama_%d.png" % f), optimize=True)
    if args.preview:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = np.concatenate([faces[f] for f in range(4)], axis=1)
        Image.fromarray(strip).save(os.path.join(PREVIEW, "panorama.png"))
        Image.fromarray(np.concatenate([faces[4], faces[5]], axis=1)).save(os.path.join(PREVIEW, "panorama_updown.png"))
    print("Wrote the panorama to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
