#!/usr/bin/env python3
"""Renders Hellcraft's Lucifer models to PNG previews without Minecraft.

Run from the repo root:  python3 tools/preview_model.py [out-dir]   (default docs/)

A tiny software rasteriser: every textured face is split into one quad per texel, the quads are
rotated for the view, painter-sorted and filled, with simple directional shading. It understands the
subset of the block-model format gen_models.py writes (cuboids with one-axis rotations).
"""
import json
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
from gen_textures import png  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "assets", "hellcraft")


def read_png(path):
    data = open(path, "rb").read()
    i, w, h, idat = 8, 0, 0, b""
    while i < len(data):
        n = struct.unpack(">I", data[i:i + 4])[0]
        kind, chunk = data[i + 4:i + 8], data[i + 8:i + 8 + n]
        i += 12 + n
        if kind == b"IHDR":
            w, h = struct.unpack(">II", chunk[:8])
        elif kind == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    stride = w * 4 + 1
    return [[tuple(raw[y * stride + 1 + x * 4:y * stride + 5 + x * 4]) for x in range(w)] for y in range(h)]


def rotate(p, axis, angle, origin):
    a = math.radians(angle)
    x, y, z = (p[i] - origin[i] for i in range(3))
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def face_corners(f, t, face):
    """Corners (top-left, top-right, bottom-right, bottom-left) as seen in texture space."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def quads(model, tex):
    size = len(tex)
    out = []
    for el in model["elements"]:
        rot = el.get("rotation")
        for face, spec in el["faces"].items():
            u0, v0, u1, v1 = (c * size / 16 for c in spec["uv"])
            nu, nv = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
            tl, tr, br, bl = face_corners(el["from"], el["to"], face)
            for j in range(nv):
                for i in range(nu):
                    s0, s1, t0, t1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                    pts = []
                    for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1)):
                        p = lerp(lerp(tl, tr, s), lerp(bl, br, s), t)
                        if rot:
                            p = rotate(p, rot["axis"], rot["angle"], rot["origin"])
                        pts.append(p)
                    px = int(u0 + (u1 - u0) * (i + 0.5) / nu)
                    py = int(v0 + (v1 - v0) * (j + 0.5) / nv)
                    color = tex[min(size - 1, py)][min(size - 1, px)]
                    if color[3] > 0:
                        out.append((pts, color))
    return out


def render(model, tex, yaw, pitch, path, scale=5, pad=8):
    qs = quads(model, tex)
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p[0] - 8, p[1] - 16, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return -x, -y, z  # camera at -Z looking +Z: +X shows on the left

    light = (0.4, 0.8, -0.45)
    items = []
    for pts, color in qs:
        v = [view(p) for p in pts]
        ax, ay, az = (v[1][i] - v[0][i] for i in range(3))
        bx, by, bz = (v[3][i] - v[0][i] for i in range(3))
        n = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
        ln = math.sqrt(sum(c * c for c in n)) or 1
        d = abs(sum(n[i] / ln * light[i] for i in range(3)))
        f = 0.45 + 0.65 * d
        items.append((sum(p[2] for p in v) / 4, v, tuple(min(255, int(c * f)) for c in color[:3]) + (255,)))
    items.sort(key=lambda it: -it[0])
    xs = [p[0] for _, v, _ in items for p in v]
    ys = [p[1] for _, v, _ in items for p in v]
    minx, miny = min(xs), min(ys)
    w = int((max(xs) - minx) * scale) + 2 * pad
    h = int((max(ys) - miny) * scale) + 2 * pad
    img = [[(24, 12, 14, 255)] * w for _ in range(h)]
    for _, v, color in items:
        poly = [((p[0] - minx) * scale + pad, (p[1] - miny) * scale + pad) for p in v]
        fill(img, poly, color)
    png(path, img)


def fill(img, poly, color):
    h, w = len(img), len(img[0])
    ys = [p[1] for p in poly]
    for y in range(max(0, int(min(ys))), min(h, int(max(ys)) + 1)):
        yc = y + 0.5
        xs = []
        for i in range(len(poly)):
            (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
            if (y0 <= yc < y1) or (y1 <= yc < y0):
                xs.append(x0 + (yc - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for k in range(0, len(xs) - 1, 2):
            for x in range(max(0, int(xs[k] + 0.5)), min(w, int(xs[k + 1] + 0.5))):
                img[y][x] = color


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "docs")
    for name in ("lucifer_morning_star", "lucifer_emperor"):
        model = json.load(open(os.path.join(ROOT, "models", "item", name + ".json")))
        tex_path = model["textures"]["skin"].split(":")[1]
        tex = read_png(os.path.join(ROOT, "textures", tex_path + ".png"))
        render(model, tex, 0, 8, os.path.join(out, name + "_front.png"))
        render(model, tex, 35, 15, os.path.join(out, name + "_angle.png"))
        render(model, tex, 180, 10, os.path.join(out, name + "_back.png"))
    print("Wrote previews to", os.path.normpath(out))


if __name__ == "__main__":
    main()
