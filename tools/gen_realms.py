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


# ============================================================================================ Paradiso

# The End is Dante's heaven. The dragon's island in the middle stays vanilla's the_end (restyled); every outer
# island takes the sphere of its ring (ParadisoGeometry.java; the mixin TheEndBiomeSourceMixin assigns them).
# sphere: (title, top block, patch block, under block, sky, fog, particle, [features])
SPHERES = {
    "moon": ("The Moon", "calcite", "snow_block", "calcite", "#c8d4e8", "#dfe6f2", ("minecraft:white_ash", 0.01),
             ["hellcraft:paradiso_star_glowstone"]),
    "mercury": ("Mercury", "polished_diorite", "diorite", "diorite", "#d8dcef", "#e8e9f2", None,
                ["hellcraft:paradiso_star_glowstone"]),
    "venus": ("Venus", "grass_block", "moss_block", "dirt", "#f2c6dc", "#f8dde9", ("minecraft:cherry_leaves", 0.02),
              ["hellcraft:paradiso_cherry", "hellcraft:paradiso_petals", "hellcraft:paradiso_star_glowstone"]),
    "sun": ("The Sun", "yellow_terracotta", "honeycomb_block", "sandstone", "#ffe7a3", "#fff1c9", ("minecraft:wax_on", 0.01),
            ["hellcraft:paradiso_star_shroomlight", "hellcraft:paradiso_star_glowstone"]),
    "mars": ("Mars", "red_terracotta", "red_sandstone", "red_sandstone", "#f0b49a", "#f6cdb9", ("minecraft:crimson_spore", 0.005),
             ["hellcraft:paradiso_star_shroomlight"]),
    "jupiter": ("Jupiter", "quartz_block", "smooth_quartz", "quartz_block", "#e6ecff", "#f3f5ff", None,
                ["hellcraft:paradiso_star_glowstone"]),
    "saturn": ("Saturn", "packed_ice", "snow_block", "packed_ice", "#bcd3e6", "#d9e6f0", ("minecraft:snowflake", 0.01),
               ["hellcraft:paradiso_star_lantern"]),
    "fixed_stars": ("The Fixed Stars", "end_stone_bricks", "end_stone", "end_stone", "#aab8e8", "#c9d2f2", ("minecraft:end_rod", 0.004),
                    ["minecraft:chorus_plant", "hellcraft:paradiso_star_lantern", "hellcraft:paradiso_star_glowstone"]),
    "primum_mobile": ("The Primum Mobile", "prismarine_bricks", "dark_prismarine", "prismarine", "#b8f0ff", "#dcf8ff",
                      ("minecraft:end_rod", 0.008), ["minecraft:chorus_plant", "hellcraft:paradiso_star_lantern"]),
    "empyrean": ("The Empyrean", "grass_block", "white_concrete", "quartz_block", "#fff8e8", "#fffdf6", ("minecraft:end_rod", 0.012),
                 ["hellcraft:paradiso_petals", "hellcraft:paradiso_star_lantern"]),
}
# one global order for the ninth feature step of every End biome
PARADISO_ORDER = ["minecraft:chorus_plant", "hellcraft:paradiso_cherry", "hellcraft:paradiso_petals",
                  "hellcraft:paradiso_star_shroomlight", "hellcraft:paradiso_star_glowstone", "hellcraft:paradiso_star_lantern"]
# no end cities on the Moon (too close) or in the Empyrean (the Rose is there)
CITY_SPHERES = ["mercury", "venus", "sun", "mars", "jupiter", "saturn", "fixed_stars", "primum_mobile"]


def patch(state, tries, xz, survive):
    """A random patch of one block, in 26.3's form (a simple block and its placement)."""
    return ({"type": "minecraft:simple_block", "to_place": {"id": "minecraft:" + state}},
            [count(tries),
             {"type": "minecraft:offset", "x": {"type": "minecraft:trapezoid", "min": -xz, "max": xz, "plateau": 0},
              "y": {"type": "minecraft:trapezoid", "min": -1, "max": 1, "plateau": 0},
              "z": {"type": "minecraft:trapezoid", "min": -xz, "max": xz, "plateau": 0}},
             {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                 {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
                 {"type": "minecraft:would_survive", "state": {"id": "minecraft:" + survive}}]}}])


SURFACE = {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}


def paradiso():
    feats = {
        "paradiso_star_glowstone": {"type": "hellcraft:star_cluster", "state": "minecraft:glowstone"},
        "paradiso_star_lantern": {"type": "hellcraft:star_cluster", "state": "minecraft:sea_lantern"},
        "paradiso_star_shroomlight": {"type": "hellcraft:star_cluster", "state": "minecraft:shroomlight"},
    }
    petals, petal_placement = patch("pink_petals", 32, 5, "pink_petals")
    feats["paradiso_petals"] = petals
    for name, f in feats.items():
        write(os.path.join(HC, "worldgen", "feature", name + ".json"), f)
    placed_features = {
        "paradiso_star_glowstone": placed("hellcraft:paradiso_star_glowstone", rarity(3), IN_SQUARE, BIOME),
        "paradiso_star_lantern": placed("hellcraft:paradiso_star_lantern", rarity(2), IN_SQUARE, BIOME),
        "paradiso_star_shroomlight": placed("hellcraft:paradiso_star_shroomlight", rarity(3), IN_SQUARE, BIOME),
        "paradiso_petals": placed("hellcraft:paradiso_petals", count(2), IN_SQUARE, SURFACE, BIOME, *petal_placement),
        "paradiso_cherry": placed("minecraft:cherry", count(2), IN_SQUARE, SURFACE,
                                  {"type": "minecraft:block_predicate_filter", "predicate": {
                                      "type": "minecraft:would_survive",
                                      "state": {"id": "minecraft:cherry_sapling", "properties": {"stage": "0"}}}},
                                  BIOME),
    }
    for name, pf in placed_features.items():
        write(os.path.join(HC, "worldgen", "placed_feature", name + ".json"), pf)

    template = vanilla("biome__end_highlands")
    names = {}
    for sphere, (title, top, patch_block, under, sky, fog, particle, added) in SPHERES.items():
        b = copy.deepcopy(template)
        steps = [[] for _ in range(10)]
        steps[0] = ["minecraft:end_island_decorated"]
        steps[4] = ["minecraft:end_gateway_return"]
        steps[9] = [f for f in PARADISO_ORDER if f in added]
        b["features"] = steps
        attrs = b["attributes"]
        attrs["minecraft:visual/sky_color"] = sky
        attrs["minecraft:visual/fog_color"] = fog
        if particle:
            attrs["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle[0]}, "probability": particle[1]}],
                                                           "modifier": "append"}
        monsters = attrs["minecraft:gameplay/natural_mob_spawns"]["argument"]["spawns_by_category"]
        monsters["monster"] = [] if sphere == "empyrean" else [{"type": "minecraft:enderman", "count": 1, "weight": 5}]
        b["temperature"] = 0.7
        b["downfall"] = 0.4
        write(os.path.join(HC, "worldgen", "biome", "paradiso_" + sphere + ".json"), b)
        names["biome.hellcraft.paradiso_" + sphere] = "Paradiso: " + title

    # the Threshold: vanilla's central island, in heaven's light
    end = vanilla("biome__the_end")
    end["attributes"]["minecraft:visual/sky_color"] = "#b8c6f0"
    end["attributes"]["minecraft:visual/fog_color"] = "#d8def2"
    write(os.path.join(MC, "worldgen", "biome", "the_end.json"), end)
    names["biome.minecraft.the_end"] = "Paradiso: The Threshold"
    names["entity.minecraft.ender_dragon"] = "The Seraph"

    # the ground of each sphere
    rules = []
    for sphere, (title, top, patch_block, under, *_rest) in SPHERES.items():
        rules.append(cond(biome_is("hellcraft:paradiso_" + sphere), seq(
            cond("minecraft:on_floor", seq(cond(noise_at_least("minecraft:patch", 0.25), block(patch_block)), block(top))),
            cond("minecraft:under_floor", block(under)))))
    rules.append(vanilla("material_rule__end"))
    write(os.path.join(MC, "worldgen", "material_rule", "end.json"), seq(*rules))

    # larger islands, closer together
    cheese = vanilla("density_function__end__sloped_cheese")
    write(os.path.join(MC, "worldgen", "density_function", "end", "sloped_cheese.json"),
          {"type": "minecraft:add", "left": cheese, "right": 0.04})

    # heaven's sky over the whole End (each sphere tints its own)
    dim = vanilla("dimension_type__the_end")
    dim["ambient_light"] = 0.4
    dim["skybox"] = "overworld"
    dim["attributes"]["minecraft:visual/sky_color"] = "#c9d6f5"
    dim["attributes"]["minecraft:visual/fog_color"] = "#e6e9f5"
    dim["attributes"]["minecraft:visual/sky_light_color"] = "#fff4d6"
    dim["attributes"]["minecraft:visual/sky_light_factor"] = 1.0
    dim["attributes"]["minecraft:visual/ambient_light_color"] = "#b0b0c0"
    write(os.path.join(MC, "dimension_type", "the_end.json"), dim)

    tags = os.path.join(MC, "tags", "worldgen", "biome")
    write(os.path.join(tags, "is_end.json"), {"replace": False, "values": ["hellcraft:paradiso_" + s for s in SPHERES]})
    write(os.path.join(tags, "has_structure", "end_city.json"), {"replace": False, "values": ["hellcraft:paradiso_" + s for s in CITY_SPHERES]})
    return names


# ============================================================================================ the names

def lang(names):
    with open(LANG) as f:
        current = json.load(f)
    current.update(names)
    write(LANG, current)


def main():
    names = {}
    names.update(nether())
    names.update(paradiso())
    lang(names)
    print("Reshaped the Nether (%d biomes) and the End (%d spheres)." % (len(NETHER_BIOMES), len(SPHERES)))


if __name__ == "__main__":
    main()
