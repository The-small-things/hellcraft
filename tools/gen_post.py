#!/usr/bin/env python3
"""Writes Hellcraft's screen effects (versions/26.3/src/main/resources/assets/hellcraft/post_effect):

limbo_<1-5>: Limbo drained of colour, in five steps, so the colour fades out as you walk in from the circle's edge
and back in near the Noble Castle's light (client/layer/LimboGrey picks the step). Each is vanilla's
post/color_convolve with a colour matrix that pulls every pixel toward its grey, then a blit back to the screen.
color_convolve multiplies what's left of the colour by 1.8 afterwards, so the matrix aims at saturation / 1.8.

Run from the repo root: python3 tools/gen_post.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "assets", "hellcraft",
                   "post_effect")
GREY = (0.3, 0.59, 0.11)
# how much colour is left at each step (1 = all, 0 = none)
STEPS = [0.8, 0.6, 0.42, 0.26, 0.14]


def matrix(saturation):
    s = saturation / 1.8
    rows = []
    for i in range(3):
        rows.append([round((1 - s) * GREY[j] + (s if i == j else 0), 4) for j in range(3)])
    return rows


def effect(saturation):
    r, g, b = matrix(saturation)
    return {
        "targets": {"swap": {}},
        "passes": [
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/color_convolve",
                "inputs": [{"sampler_name": "In", "target": "minecraft:main"}],
                "output": "swap",
                "uniforms": {"ColorConfig": [
                    {"name": "RedMatrix", "type": "vec3", "value": r},
                    {"name": "GreenMatrix", "type": "vec3", "value": g},
                    {"name": "BlueMatrix", "type": "vec3", "value": b},
                ]},
            },
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/blit",
                "inputs": [{"sampler_name": "In", "target": "swap"}],
                "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]},
                "output": "minecraft:main",
            },
        ],
    }


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, sat in enumerate(STEPS, 1):
        with open(os.path.join(OUT, "limbo_%d.json" % i), "w") as f:
            json.dump(effect(sat), f, indent=2)
            f.write("\n")
    print("Wrote", len(STEPS), "post effects to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
