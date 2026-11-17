#!/usr/bin/env python3
"""First person, offline: draws what a player sees of their sword through a move of tools/art/player_moves.py, frame by
frame, in a second (the client test takes minutes). It follows the game exactly:

    vanilla's arm place      translate(0.56, -0.52, -0.72)              (FirstPersonHandsAndItemsRenderer.applyItemArmTransform)
    Hellcraft's fp bone      translate(fp_pos / 16), rotate(fp)         (client mixin/FirstPersonHandsMixin)
    the sword's display      handheld firstperson_righthand: translate(1.13, 3.2, 1.13)/16, rotate XYZ (0, -90, 25), scale 0.68
    the flat item            translate(-0.5), the sprite in x, y at z 0.5 (a sword: pommel bottom left, tip top right)

seen from the eye (looking down -Z, Y up) with the game's 70 degree field of view, 16:9. The blade is drawn as its
outline, lighter along the edge that leads the cut (the flat faces you when the blade looks wide).

    python3 tools/art/fp_preview.py sword_1 [sword_2 ...] [--times 0,3,5,9] [--step 1]
writes versions/26.3/build/art-preview/fp_<move>.png. Keep the whole blade on screen through a swing: a blade that
leaves the bottom of the screen reads as nothing happening.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import player_moves as pm  # noqa: E402

W, H = 427, 240
FOV = 70.0
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "versions", "26.3", "build", "art-preview")
F = np.diag([-1.0, -1.0, 1.0])  # Blockbench's turn and shift of the fp bone, as the game applies them (see player_moves.fp_rot)


def rot_xyz(x, y, z):
    x, y, z = (math.radians(v) for v in (x, y, z))
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rx @ ry @ rz


DISPLAY_R = rot_xyz(0, -90, 25)
DISPLAY_T = np.array([1.13, 3.2, 1.13]) / 16
DISPLAY_S = 0.68
ARM = np.array([0.56, -0.52, -0.72])

# a sword sprite's outline in item space (pixels, x right, y up): the blade from the guard to the tip, and the grip
BLADE = [(4.5, 6.5), (13.5, 15.5), (15.5, 15.5), (15.5, 13.5), (6.5, 4.5)]
GUARD = [(2.5, 6.5), (6.5, 2.5), (7.5, 3.5), (3.5, 7.5)]
GRIP = [(0.5, 1.5), (1.5, 0.5), (4.5, 3.5), (3.5, 4.5)]


def item_point(px, py):
    p = np.array([px / 16 - 0.5, py / 16 - 0.5, 0.0])
    return DISPLAY_T + DISPLAY_R @ (p * DISPLAY_S)


def project(v):
    f = 1.0 / math.tan(math.radians(FOV / 2))
    z = -v[2]
    if z < 0.02:
        z = 0.02
    x = v[0] / z * f / (W / H)
    y = v[1] / z * f
    return (W / 2 + x * W / 2, H / 2 - y * H / 2)


def fp_at(clip, tick):
    """The fp bone's shift (blocks) and turn at this tick, as the game applies them."""
    shift = np.zeros(3)
    turn = np.eye(3)
    if "fp" in clip.pos:
        shift = F @ np.array(clip._sample("fp", clip.pos["fp"], tick, False), dtype=float) / 16
    if "fp" in clip.rot:
        m = clip._sample("fp", clip.rot["fp"], tick, True)
        if not isinstance(m, np.ndarray):
            m = pm.matrix(m)
        turn = F @ m @ F
    return shift, turn


def draw_frame(draw, ox, clip, tick):
    shift, turn = fp_at(clip, tick)

    def view(px, py):
        return ARM + shift + turn @ item_point(px, py)

    def poly(points, fill):
        draw.polygon([(ox + x, y) for x, y in (project(view(px, py)) for px, py in points)], fill=fill, outline=(20, 20, 24))

    # the flat's normal against the view: wide (facing you) or edge on
    normal = turn @ DISPLAY_R @ np.array([0, 0, 1.0])
    centre = view(9, 9)
    facing = abs(float(np.dot(normal, -centre / np.linalg.norm(centre))))
    shade = int(120 + 110 * facing)
    poly(GRIP, (110, 72, 40))
    poly(GUARD, (60, 60, 66))
    poly(BLADE, (shade, shade, shade + 6))
    tip = project(view(15, 15))
    draw.ellipse((ox + tip[0] - 2, tip[1] - 2, ox + tip[0] + 2, tip[1] + 2), fill=(230, 60, 60))


def render(name, times=None, step=1.0):
    make = getattr(pm, name)
    clip = make()
    if times is None:
        times = []
        t = 0.0
        while t <= clip.ticks + 1e-6:
            times.append(round(t, 2))
            t += step
    cols = min(6, len(times))
    rows = (len(times) + cols - 1) // cols
    img = Image.new("RGB", (W * cols, H * rows), (40, 44, 52))
    draw = ImageDraw.Draw(img)
    events = {round(t): n for t, n in clip.events}
    for i, t in enumerate(times):
        ox, oy = (i % cols) * W, (i // cols) * H
        sub = Image.new("RGB", (W, H), (150, 185, 225))
        sd = ImageDraw.Draw(sub)
        sd.rectangle((0, H * 0.55, W, H), fill=(120, 120, 118))
        sd.rectangle((W * 0.29, H - 22, W * 0.71, H - 6), fill=(60, 60, 60))  # the hotbar
        draw_frame(sd, 0, clip, t)
        label = f"{name} {t:g}" + (f" [{events[round(t)]}]" if round(t) in events and abs(t - round(t)) < 1e-6 else "")
        sd.text((4, 3), label, fill=(255, 255, 0))
        img.paste(sub, (ox, oy))
        draw.rectangle((ox, oy, ox + W - 1, oy + H - 1), outline=(0, 0, 0))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.abspath(os.path.join(OUT, f"fp_{name}.png"))
    img.save(path)
    print(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    times, step = None, 1.0
    if "--times" in args:
        i = args.index("--times")
        times = [float(v) for v in args[i + 1].split(",")]
        del args[i:i + 2]
    if "--step" in args:
        i = args.index("--step")
        step = float(args[i + 1])
        del args[i:i + 2]
    for name in args:
        render(name, times, step)
