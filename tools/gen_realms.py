#!/usr/bin/env python3
"""Reshapes the vanilla Nether and End (Minecraft 26.3 data pack): the Forge of Dis and Paradiso.

Run from the repo root:  python3 tools/gen_realms.py

Worlds keep their Nether and End generators in level.dat, pointing at vanilla ids (minecraft:nether,
minecraft:end, the vanilla biomes). So the new terrain overrides those vanilla files by id: it applies to new
worlds and to chunks not generated yet in existing ones. Each override starts from the vanilla 26.3 file saved
in tools/vanilla26/ (printed from the game jar by versions/26.3/tools/probe.sh) and changes only what it must.
"""
import copy
import json
import os

HERE = os.path.dirname(__file__)
VANILLA = os.path.join(HERE, "vanilla26")
RES = os.path.join(HERE, "..", "versions", "26.3", "src", "main", "resources")
MC = os.path.join(RES, "data", "minecraft")
HC = os.path.join(RES, "data", "hellcraft")
LANG = os.path.join(RES, "assets", "hellcraft", "lang", "en_us.json")


def vanilla(name):
    with open(os.path.join(VANILLA, name + ".json")) as f:
        return json.load(f)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def block(name):
    return {"type": "minecraft:block", "result_state": "minecraft:" + name}


def cond(if_true, then_run):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": list(ids) if len(ids) > 1 else ids[0]}


def noise_at_least(noise, lo):
    return {"type": "minecraft:noise_threshold", "max_threshold": 1.7976931348623157e+308, "min_threshold": lo, "noise": noise}


def below_y(y):
    return {"type": "minecraft:not", "invert": {"type": "minecraft:y_above", "add_stone_depth": False,
                                                 "anchor": {"absolute": y}, "surface_depth_multiplier": 0}}


def placed(feature, *placement):
    return {"feature": feature, "placement": list(placement)}


def rarity(n):
    return {"type": "minecraft:rarity_filter", "chance": n}


def count(n):
    return {"type": "minecraft:count", "count": n}


def height(lo, hi):
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo},
                                                          "max_inclusive": {"absolute": hi}}}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
LAVA_SEA = 32

# ============================================================================================ the Forge of Dis

# Vulcan's smithy (Inferno XIV): the Nether is the workshop of Hell. Blackstone and slag instead of netherrack
# on the floors of the wastes and the deltas, heaps of slag, chains hanging from the vaults, ruined forges.
FORGE_FEATURES = ["hellcraft:forge_ruin_common", "hellcraft:forge_ruin_rare", "hellcraft:slag_heap", "hellcraft:chain_pillar"]

NETHER_BIOMES = {
    # vanilla id: (new name, features added, fog colour, extra monsters, ambient particle)
    "nether_wastes": ("The Ash Wastes of Dis", ["hellcraft:forge_ruin_rare", "hellcraft:slag_heap", "hellcraft:chain_pillar"],
                      "#3b2723", [("minecraft:wither_skeleton", 25, 1, 2)], ("minecraft:white_ash", 0.02)),
    "basalt_deltas": ("Vulcan's Foundry", ["hellcraft:forge_ruin_common", "hellcraft:slag_heap", "hellcraft:chain_pillar"],
                      "#5a2a12", [("minecraft:blaze", 30, 1, 2)], None),
    "crimson_forest": ("The Bleeding Wood", ["hellcraft:forge_ruin_rare"], "#4a0808", [], ("minecraft:falling_lava", 0.004)),
    "warped_forest": ("The Cold Forge", ["hellcraft:forge_ruin_rare", "hellcraft:chain_pillar"], "#12302c", [], None),
    "soul_sand_valley": ("The Valley of Slag", ["hellcraft:forge_ruin_rare", "hellcraft:slag_heap"], "#2a3a3a",
                         [("minecraft:wither_skeleton", 15, 1, 2)], None),
}

NETHER_PLACED = {
    "forge_ruin_common": placed("hellcraft:forge_ruin", rarity(6), IN_SQUARE, height(LAVA_SEA + 4, 110), BIOME),
    "forge_ruin_rare": placed("hellcraft:forge_ruin", rarity(18), IN_SQUARE, height(LAVA_SEA + 4, 110), BIOME),
    "slag_heap": placed("hellcraft:slag_heap", count(2), IN_SQUARE, height(LAVA_SEA + 2, 110), BIOME),
    "chain_pillar": placed("hellcraft:chain_pillar", count(3), IN_SQUARE, height(50, 115), BIOME),
}


def forge_floors():
    """Surface rules for the wastes and the deltas, tried before vanilla's own."""
    lava_holes = cond(below_y(LAVA_SEA), cond({"type": "minecraft:hole"}, block("lava")))
    wastes = cond(biome_is("minecraft:nether_wastes"), seq(
        cond("minecraft:on_floor", seq(
            lava_holes,
            cond(noise_at_least("minecraft:nether_state_selector", 0.35), block("basalt")),
            cond(noise_at_least("minecraft:patch", 0.4), block("gravel")),
            block("blackstone"))),
        cond("minecraft:under_floor", block("blackstone"))))
    foundry = cond(biome_is("minecraft:basalt_deltas"), seq(
        cond("minecraft:under_ceiling", block("basalt")),
        cond("minecraft:on_floor", seq(
            lava_holes,
            cond(noise_at_least("minecraft:nether_state_selector", 0.25), block("magma_block")),
            cond(noise_at_least("minecraft:patch", -0.2), block("basalt")),
            block("blackstone"))),
        cond("minecraft:under_floor", block("basalt"))))
    return [wastes, foundry]


def nether():
    rule = vanilla("material_rule__nether")
    # after the bedrock floor and roof
    rule["sequence"] = rule["sequence"][:2] + forge_floors() + rule["sequence"][2:]
    write(os.path.join(MC, "worldgen", "material_rule", "nether.json"), rule)

    settings = vanilla("noise_settings__nether")
    # larger halls: a little less rock everywhere (the vanilla density is squeezed and interpolated)
    interp = settings["noise_router"]["final_density"]["left"]["input"]
    interp["input"] = {"type": "minecraft:add", "left": interp["input"], "right": -0.06}
    write(os.path.join(MC, "worldgen", "noise_settings", "nether.json"), settings)

    for biome_id, (title, added, fog, monsters, particle) in NETHER_BIOMES.items():
        b = vanilla("biome__" + biome_id)
        steps = b["features"] + [[] for _ in range(10 - len(b["features"]))]
        steps[9] = steps[9] + [f for f in FORGE_FEATURES if f in added]
        b["features"] = steps
        attrs = b["attributes"]
        attrs["minecraft:visual/fog_color"] = fog
        spawns = attrs["minecraft:gameplay/natural_mob_spawns"]["argument"]["spawns_by_category"].setdefault("monster", [])
        for mob, weight, lo, hi in monsters:
            spawns.append({"type": mob, "count": lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi},
                           "weight": weight})
        if particle:
            attrs["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle[0]}, "probability": particle[1]}],
                                                           "modifier": "append"}
        write(os.path.join(MC, "worldgen", "biome", biome_id + ".json"), b)

    for name, pf in NETHER_PLACED.items():
        write(os.path.join(HC, "worldgen", "placed_feature", name + ".json"), pf)
    for name in ("forge_ruin", "slag_heap", "chain_pillar"):
        write(os.path.join(HC, "worldgen", "feature", name + ".json"), {"type": "hellcraft:" + name})
    return {"biome.minecraft." + b: v[0] for b, v in NETHER_BIOMES.items()}


# ============================================================================================ the names

def lang(names):
    with open(LANG) as f:
        current = json.load(f)
    current.update(names)
    write(LANG, current)


def main():
    names = {}
    names.update(nether())
    lang(names)
    print("Reshaped the Nether: %d biomes." % len(NETHER_BIOMES))


if __name__ == "__main__":
    main()
