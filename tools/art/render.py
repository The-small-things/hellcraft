#!/usr/bin/env python3
"""Renders a boss's .bbmodel (versions/26.3/art) to PNG without Minecraft, so a model can be looked at while it is
being built: from the front, the side, three-quarters and behind, at rest or at any moment of an animation.

Run from the repo root:
    python3 tools/art/render.py minos                         # rest pose, four views
    python3 tools/art/render.py minos --anim strike --times 0.4,0.8,1.2 --views side,front
    python3 tools/art/render.py minos --out /tmp/minos.png --size 360

Writes versions/26.3/build/art-preview/<model>[_<anim>].png by default (build/ is not checked in).

Works in Blockbench's space (Y up, the model faces -Z): a group or cube turns about its origin by (x, y, z) degrees,
applied X first, then Y, then Z (the same order Minecraft uses; see tools/gen_bosses.py), and an animation's
rotation and position keys add to a bone's own. A z-buffered orthographic rasteriser with nearest texel sampling and
simple directional light; the glow texture is drawn at full brightness. Box UV faces are mapped as Minecraft lays
them out, closely enough to judge shapes and paint (a face's texture may come out mirrored).
"""
import argparse
import base64
import io
import json
import math
import os
import sys

import numpy as np

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover
    sys.exit("render.py needs Pillow (pip install pillow)")

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "versions", "26.3")
ART = os.path.join(ROOT, "art")
OUT = os.path.join(ROOT, "build", "art-preview")

# the camera's direction (yaw about Y, from the front) and pitch for each named view
VIEWS = {"front": (0, 12), "side": (-90, 8), "three": (35, 15), "back": (180, 12), "top": (20, 60), "left": (90, 8),
         "low": (25, -12)}


def rot_matrix(deg):
    """Blockbench's Euler rotation: X first, then Y, then Z (R = Rz Ry Rx)."""
    x, y, z = (math.radians(v) for v in deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def affine(rotation, origin):
    """4x4: turn about origin."""
    m = np.eye(4)
    m[:3, :3] = rot_matrix(rotation)
    o = np.array(origin, dtype=float)
    m[:3, 3] = o - m[:3, :3] @ o
    return m


def translate(v):
    m = np.eye(4)
    m[:3, 3] = v
    return m


def load_texture(doc, index):
    for t in doc.get("textures", []):
        if str(t.get("id")) == str(index) or doc["textures"].index(t) == index:
            data = base64.b64decode(t["source"].split(",", 1)[1])
            return np.asarray(Image.open(io.BytesIO(data)).convert("RGBA")).astype(np.float32)
    return None


# ----------------------------------------------------------------------------------------------- animation

def catmull(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)


def sample(keys, t):
    """keys: [(time, vec, interp)] sorted; the value at time t."""
    if not keys:
        return np.zeros(3)
    times = [k[0] for k in keys]
    vals = [np.array(k[1], dtype=float) for k in keys]
    if t <= times[0]:
        return vals[0]
    if t >= times[-1]:
        return vals[-1]
    i = max(j for j in range(len(times)) if times[j] <= t)
    a, b = times[i], times[i + 1]
    f = (t - a) / (b - a) if b > a else 0
    if keys[i + 1][2] == "catmullrom" or keys[i][2] == "catmullrom":
        p0 = vals[max(i - 1, 0)]
        p3 = vals[min(i + 2, len(vals) - 1)]
        return catmull(p0, vals[i], vals[i + 1], p3, f)
    return vals[i] + (vals[i + 1] - vals[i]) * f


def pose_of(doc, anim_name, t):
    """bone name -> (rotation add, position add) at time t of the named animation."""
    if not anim_name:
        return {}
    anim = None
    for a in doc.get("animations", []):
        if a["name"].split(".")[-1] == anim_name:
            anim = a
    if anim is None:
        sys.exit(f"no animation {anim_name}: " + ", ".join(a["name"].split(".")[-1] for a in doc.get("animations", [])))
    out = {}
    for animator in anim["animators"].values():
        if animator.get("type") != "bone":
            continue
        chans = {}
        for k in animator["keyframes"]:
            d = k["data_points"][0]
            chans.setdefault(k["channel"], []).append((k["time"], (float(d["x"]), float(d["y"]), float(d["z"])), k.get("interpolation", "linear")))
        for c in chans.values():
            c.sort(key=lambda k: k[0])
        out[animator["name"]] = (sample(chans.get("rotation", []), t), sample(chans.get("position", []), t))
    return out


# ----------------------------------------------------------------------------------------------- geometry

# each face: the corner the texture's top-left sits on, and the directions its u (right) and v (down) run, as
# (axis index, sign) over the cube's box; plus its outward normal
FACES = {
    "north": ((1, 1, 0), (-1, 0, 0), (0, -1, 0), (0, 0, -1)),
    "south": ((0, 1, 1), (1, 0, 0), (0, -1, 0), (0, 0, 1)),
    "east": ((1, 1, 1), (0, 0, -1), (0, -1, 0), (1, 0, 0)),
    "west": ((0, 1, 0), (0, 0, 1), (0, -1, 0), (-1, 0, 0)),
    "up": ((1, 1, 0), (-1, 0, 0), (0, 0, 1), (0, 1, 0)),
    "down": ((1, 0, 1), (-1, 0, 0), (0, 0, -1), (0, -1, 0)),
}


def collect(doc, poses):
    """Every visible face as (corner, u edge, v edge, normal, uv rect), in model space after bones and animation."""
    elements = {e["uuid"]: e for e in doc["elements"]}
    faces = []

    def walk(group, parent_m):
        name = group["name"]
        rot = np.array(group.get("rotation", [0, 0, 0]), dtype=float)
        pos = np.zeros(3)
        if name in poses:
            rot = rot + poses[name][0]
            pos = poses[name][1]
        m = parent_m @ translate(pos) @ affine(rot, group["origin"])
        for child in group.get("children", []):
            if isinstance(child, dict):
                walk(child, m)
            else:
                e = elements.get(child)
                if e is not None and e.get("visibility", True):
                    add_element(e, m)

    def add_element(e, m):
        inflate = float(e.get("inflate", 0) or 0)
        lo = np.array(e["from"], dtype=float) - inflate
        hi = np.array(e["to"], dtype=float) + inflate
        em = m @ affine(e.get("rotation", [0, 0, 0]), e.get("origin", [0, 0, 0]))
        size = hi - lo
        for fname, (corner, u, v, n) in FACES.items():
            f = e["faces"].get(fname)
            if not f or f.get("texture") is None:
                continue
            c = lo + np.array(corner) * size
            ue = np.array(u) * size
            ve = np.array(v) * size
            p = em @ np.append(c, 1)
            pu = em[:3, :3] @ ue
            pv = em[:3, :3] @ ve
            nn = em[:3, :3] @ np.array(n, dtype=float)
            faces.append((p[:3], pu, pv, nn, f["uv"]))

    for g in doc["outliner"]:
        if isinstance(g, dict):
            walk(g, np.eye(4))
        else:
            e = elements.get(g)
            if e is not None:
                add_element(e, np.eye(4))
    return faces


# ----------------------------------------------------------------------------------------------- raster

def render(doc, tex, glow, poses, yaw, pitch, size, bounds=None):
    faces = collect(doc, poses)
    # the view: turn the model so the camera looks along +Z' (screen x right, y up)
    cam = rot_matrix((pitch, 0, 0)) @ rot_matrix((0, -yaw, 0))
    # a camera at -Z looking +Z sees the model's front; screen right is the model's -X
    flip = np.diag([-1.0, 1.0, 1.0])
    view = flip @ cam
    pts = []
    for p, pu, pv, n, uv in faces:
        for corner in (p, p + pu, p + pv, p + pu + pv):
            pts.append(view @ corner)
    pts = np.array(pts) if pts else np.zeros((1, 3))
    if bounds is None:
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        bounds = (lo, hi)
    lo, hi = bounds
    span = max(hi[0] - lo[0], hi[1] - lo[1], 1) * 1.08
    scale = size / span
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:] = np.array([30, 30, 36], dtype=np.float32)
    # a faint floor line at y = 0
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    light = view @ np.array([0.35, 0.8, -0.5])
    light = light / np.linalg.norm(light)
    th, tw = tex.shape[:2]

    for p, pu, pv, n, uv in faces:
        p2, u2, v2, n2 = view @ p, view @ pu, view @ pv, view @ n
        if n2[2] >= -1e-6:  # facing away (the camera looks along +Z)
            continue
        sp = np.array([(p2[0] - cx) * scale + size / 2, size / 2 - (p2[1] - cy) * scale])
        su = np.array([u2[0] * scale, -u2[1] * scale])
        sv = np.array([v2[0] * scale, -v2[1] * scale])
        det = su[0] * sv[1] - su[1] * sv[0]
        if abs(det) < 1e-6:
            continue
        corners = np.array([sp, sp + su, sp + sv, sp + su + sv])
        x0, y0 = np.floor(corners.min(axis=0)).astype(int)
        x1, y1 = np.ceil(corners.max(axis=0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, size - 1), min(y1, size - 1)
        if x1 < x0 or y1 < y0:
            continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        dx = xs + 0.5 - sp[0]
        dy = ys + 0.5 - sp[1]
        s = (dx * sv[1] - dy * sv[0]) / det
        t = (su[0] * dy - su[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not inside.any():
            continue
        depth = p2[2] + s * u2[2] + t * v2[2]
        ua, va, ub, vb = uv
        tu = np.clip((ua + s * (ub - ua)), 0, tw - 1e-3)
        tv = np.clip((va + t * (vb - va)), 0, th - 1e-3)
        tu = np.floor(np.where(ub < ua, tu - 1e-4, tu)).astype(int).clip(0, tw - 1)
        tv = np.floor(np.where(vb < va, tv - 1e-4, tv)).astype(int).clip(0, th - 1)
        texel = tex[tv, tu]
        g = glow[tv, tu] if glow is not None else None
        ok = inside & (texel[..., 3] > 8) & (depth < zbuf[ys, xs])
        if not ok.any():
            continue
        lit = 0.55 + 0.45 * max(0.0, float(-(n2 / np.linalg.norm(n2)) @ -light)) + 0.0
        lit = min(1.15, 0.5 + 0.6 * max(0.0, float((n2 / np.linalg.norm(n2)) @ light)))
        colour = texel[..., :3] * lit
        if g is not None:
            ga = g[..., 3:4] / 255.0
            colour = colour * (1 - ga) + g[..., :3] * ga
        yy, xx = ys[ok], xs[ok]
        zbuf[yy, xx] = depth[ok]
        img[yy, xx] = colour[ok]
    return np.clip(img, 0, 255).astype(np.uint8), bounds


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model")
    ap.add_argument("--anim")
    ap.add_argument("--times", help="comma-separated seconds into the animation (default: 6 even steps)")
    ap.add_argument("--views", default=None, help="comma-separated: " + ", ".join(VIEWS) + " or yaw:pitch")
    ap.add_argument("--size", type=int, default=320)
    ap.add_argument("--out")
    args = ap.parse_args()

    path = args.model if args.model.endswith(".bbmodel") else os.path.join(ART, args.model + ".bbmodel")
    doc = json.load(open(path))
    tex = load_texture(doc, 0)
    glow = load_texture(doc, 1) if len(doc.get("textures", [])) > 1 else None
    name = os.path.basename(path)[:-len(".bbmodel")]

    views = (args.views or ("side,front" if args.anim else "front,three,side,back")).split(",")
    angles = [VIEWS[v] if v in VIEWS else tuple(float(x) for x in v.split(":")) for v in views]
    if args.anim:
        length = next(a["length"] for a in doc["animations"] if a["name"].split(".")[-1] == args.anim) if any(
            a["name"].split(".")[-1] == args.anim for a in doc["animations"]) else 1
        times = [float(x) for x in args.times.split(",")] if args.times else [length * i / 5 for i in range(6)]
    else:
        times = [None]

    # one framing per view, from the rest pose, so frames line up
    frames = []
    for yaw, pitch in angles:
        _, bounds = render(doc, tex, glow, {}, yaw, pitch, 8)
        lo, hi = bounds
        pad = (hi - lo) * 0.25 if args.anim else (hi - lo) * 0.0
        bounds = (lo - pad, hi + pad)
        row = []
        for t in times:
            poses = pose_of(doc, args.anim, t) if args.anim else {}
            img, _ = render(doc, tex, glow, poses, yaw, pitch, args.size, bounds)
            row.append((img, f"{name} {args.anim or 'rest'}" + (f" {t:.2f}s" if t is not None else "") + f" yaw {yaw:g}"))
        frames.append(row)

    if not args.anim:  # the views side by side
        frames = [[row[0] for row in frames]]
    rows, cols = len(frames), len(frames[0])
    sheet = Image.new("RGB", (cols * args.size, rows * args.size))
    draw = ImageDraw.Draw(sheet)
    for r, row in enumerate(frames):
        for c, (img, label) in enumerate(row):
            sheet.paste(Image.fromarray(img), (c * args.size, r * args.size))
            draw.text((c * args.size + 4, r * args.size + 3), label, fill=(240, 220, 120))
    out = args.out or os.path.join(OUT, name + (f"_{args.anim}" if args.anim else "") + ".png")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
