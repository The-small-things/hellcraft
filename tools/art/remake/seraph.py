#!/usr/bin/env python3
"""The Seraph of the Primum Mobile remade at twice the detail (drawn at half its old scale). Its body, six wings of
eyes and burning halo are carried over part for part with a finer grain (tools/art/legacy/seraph.py through
remake_kit's port), and every feather gains its quill and barbs.

    python3 tools/art/remake/seraph.py && python3 tools/gen_bosses.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import remake_kit as kit  # noqa: E402
from paintkit import get, tone  # noqa: E402


def quill(fs):
    """A feather's quill down its middle and barbs slanting from it (over whatever the feather already carries)."""
    for n in ("front", "back"):
        f = fs[n]
        if f.w < 3 or f.h < 6:
            continue
        mid = f.w // 2
        for y in range(f.h - 2):
            for x in range(f.w):
                c = get(f, x, y)
                if not c or f.glow_canvas.get(f.x + x, f.y + y)[3]:
                    continue
                if x == mid:
                    tone(f, x, y, 1.1)
                elif (x + y) % 3 == 0:
                    tone(f, x, y, 0.9)


def build():
    b = kit.Remake("seraph", tex_width=1024)
    b.port("seraph", also=lambda name: quill if ("feather" in name or "covert" in name) else None)
    b.finish()


if __name__ == "__main__":
    build()
