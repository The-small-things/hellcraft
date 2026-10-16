#!/usr/bin/env python3
"""Reshapes the vanilla Nether (Minecraft 26.3 data pack) into the Forge of Dis, and defines Purgatory and Paradiso.

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


# ============================================================================================ the Forge of Dis (3.0)

# In an Inferno world the Nether is generated from scratch (ForgeGeometry.java): the Great Forge on its mesa at the
# centre, the Slag Sea round it, and four regions beyond. Its own biome source (hellcraft:forge) and terrain
# (hellcraft:forge noise settings, ForgeDensity.java); other worlds keep the reshaped vanilla Nether above.
# region: (base vanilla biome, title, fog, particle, [(mob, weight, min, max)] monsters, creatures, carvers,
#          [step 9 features], [vanilla features dropped])
FORGE_REGIONS = {
    "great_forge": ("basalt_deltas", "The Great Forge", "#4a2410", ("minecraft:white_ash", 0.01), [], [], False, [],
                    ["minecraft:delta", "minecraft:small_basalt_columns", "minecraft:large_basalt_columns", "minecraft:basalt_blobs",
                     "minecraft:blackstone_blobs", "minecraft:spring_delta", "minecraft:patch_fire", "minecraft:patch_soul_fire",
                     "minecraft:spring_closed_double", "minecraft:brown_mushroom_nether", "minecraft:red_mushroom_nether"]),
    "slag_sea": ("basalt_deltas", "The Slag Sea", "#7a2a08", ("minecraft:white_ash", 0.03),
                 [("minecraft:magma_cube", 60, 2, 4), ("minecraft:ghast", 20, 1, 1)], [("minecraft:strider", 60, 1, 2)], False,
                 ["hellcraft:slag_crust"],
                 ["minecraft:delta", "minecraft:small_basalt_columns", "minecraft:large_basalt_columns", "minecraft:spring_delta",
                  "minecraft:spring_closed_double"]),
    "foundry_halls": ("nether_wastes", "The Foundry Halls", "#3a2420", ("minecraft:lava", 0.0006),
                      [("minecraft:blaze", 40, 1, 2), ("minecraft:wither_skeleton", 30, 1, 2), ("minecraft:magma_cube", 30, 1, 3),
                       ("minecraft:zombified_piglin", 50, 2, 4)], [("minecraft:strider", 30, 1, 2)], True,
                      ["hellcraft:forge_ruin_common", "hellcraft:foundry_pillar"], []),
    "chainlands": ("nether_wastes", "The Chainlands", "#262c38", ("minecraft:ash", 0.02),
                   [("minecraft:ghast", 50, 1, 1), ("minecraft:blaze", 20, 1, 2)], [("minecraft:strider", 40, 1, 2)], False,
                   ["hellcraft:chained_island"], []),
    "ash_wastes": ("nether_wastes", "The Ash Wastes", "#5a5550", ("minecraft:white_ash", 0.12),
                   [("minecraft:skeleton", 30, 1, 3), ("minecraft:wither_skeleton", 20, 1, 2), ("minecraft:ghast", 30, 1, 1),
                    ("minecraft:piglin", 40, 2, 4), ("minecraft:zombified_piglin", 40, 2, 4), ("minecraft:enderman", 5, 1, 2)], [], True,
                   ["hellcraft:forge_ruin_rare", "hellcraft:slag_heap_rare"], []),
    "cyclops_quarries": ("nether_wastes", "The Cyclops Quarries", "#5a2a18", ("minecraft:white_ash", 0.02),
                         [("minecraft:wither_skeleton", 30, 1, 2), ("minecraft:zombified_piglin", 40, 2, 4), ("minecraft:magma_cube", 20, 1, 2)],
                         [], True, ["hellcraft:cyclops_camp", "hellcraft:slag_heap_rare"], []),
}
# one global order for the forge features of step 9
FORGE_ORDER = ["hellcraft:forge_ruin_common", "hellcraft:forge_ruin_rare", "hellcraft:slag_heap", "hellcraft:chain_pillar",
               "hellcraft:slag_heap_rare",
               "hellcraft:slag_crust", "hellcraft:foundry_pillar", "hellcraft:chained_island", "hellcraft:cyclops_camp"]
FORGE_FEATURE_TYPES = ["slag_crust", "foundry_pillar", "chained_island", "cyclops_camp"]
FORGE_PLACED = {
    # once per chunk: each feature finds its own spots in the chunk from ForgeGeometry
    name: placed("hellcraft:" + name, IN_SQUARE, BIOME) for name in FORGE_FEATURE_TYPES
}
FORGE_PLACED["slag_heap_rare"] = placed("hellcraft:slag_heap", rarity(3), IN_SQUARE, height(LAVA_SEA + 2, 110), BIOME)
# vanilla's structures in the Forge: fortresses (blaze rods: the way to Paradiso) on the bridges of three regions,
# bastions where the piglins live, fossils in the ash
FORGE_STRUCTURES = {
    "has_structure/nether_fortress": ["foundry_halls", "chainlands", "ash_wastes", "cyclops_quarries"],
    "has_structure/bastion_remnant": ["ash_wastes", "cyclops_quarries"],
    "has_structure/ruined_portal_nether": ["foundry_halls", "ash_wastes", "cyclops_quarries"],
    "has_structure/nether_fossil": ["ash_wastes"],
    "is_nether": list(FORGE_REGIONS),
}


def on(cond_id, rule):
    return cond("minecraft:" + cond_id, rule)


def forge_material_rule():
    """The Forge's surface rules: bedrock floor and roof, then each region's own rock."""
    state = "minecraft:nether_state_selector"
    patch = "minecraft:patch"

    def region(name, *rules):
        return cond(biome_is("hellcraft:" + name), seq(*rules))

    return seq(
        "minecraft:bedrock_floor", "minecraft:bedrock_roof",
        region("great_forge",
               on("on_floor", seq(cond(noise_at_least(patch, 0.3), block("blackstone")), block("smooth_basalt"))),
               block("basalt")),
        region("slag_sea",
               on("on_floor", seq(cond(noise_at_least(state, 0.1), block("magma_block")), block("blackstone"))),
               on("under_floor", block("blackstone")),
               on("under_ceiling", block("basalt"))),
        region("foundry_halls",
               on("on_floor", seq(cond(noise_at_least(state, 0.45), block("magma_block")),
                                  cond(noise_at_least(patch, 0.2), block("polished_blackstone")), block("blackstone"))),
               on("under_floor", block("blackstone")),
               on("under_ceiling", block("blackstone"))),
        region("chainlands",
               on("on_floor", seq(cond(noise_at_least(patch, 0.35), block("soul_soil")), block("smooth_basalt"))),
               on("under_floor", block("basalt")),
               on("under_ceiling", block("blackstone"))),
        region("ash_wastes",
               on("on_floor", seq(cond(noise_at_least(state, 0.3), block("light_gray_concrete_powder")),
                                  cond(noise_at_least(patch, 0.1), block("gravel")), block("tuff"))),
               on("under_floor", block("tuff")),
               on("under_ceiling", block("basalt"))),
        # the quarries' cut faces show the strata
        region("cyclops_quarries", cond({"type": "minecraft:y_above", "add_stone_depth": False, "anchor": {"absolute": LAVA_SEA - 4},
                                         "surface_depth_multiplier": 0}, {"type": "minecraft:bandlands"})),
    )


def forge_noise_settings():
    settings = vanilla("noise_settings__nether")
    settings.pop("debug_functions", None)
    settings["material_rule"] = "hellcraft:forge"
    settings["noise_router"]["final_density"] = {
        "type": "minecraft:add",
        "left": {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8, "input": {
            "type": "minecraft:add", "left": {"type": "hellcraft:forge", "mode": "shape"},
            "right": {"type": "minecraft:mul", "left": {"type": "hellcraft:forge", "mode": "roughness"},
                      "right": "minecraft:nether/base_3d_noise"}}},
        "right": {"type": "minecraft:beardifier"},
    }
    return settings


def add_tag(path, values):
    """Adds to a biome tag the overworld generator may have written already."""
    current = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path) as f:
            current = json.load(f)
    current["values"] = current["values"] + [v for v in values if v not in current["values"]]
    write(path, current)


def forge():
    names = {}
    for name, (base, title, fog, particle, monsters, creatures, carvers, added, dropped) in FORGE_REGIONS.items():
        b = vanilla("biome__" + base)
        steps = [[f for f in step if f not in dropped] for step in b["features"]]
        steps = steps + [[] for _ in range(10 - len(steps))]
        steps[9] = steps[9] + [f for f in FORGE_ORDER if f in added]
        b["features"] = steps
        if not carvers:
            b["carvers"] = []
        attrs = b["attributes"]
        attrs["minecraft:visual/fog_color"] = fog
        attrs["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle[0]}, "probability": particle[1]}],
                                                       "modifier": "append"}

        def spawn(mob, weight, lo, hi):
            return {"type": mob, "count": lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi},
                    "weight": weight}
        by_category = {}
        if monsters:
            by_category["monster"] = [spawn(*m) for m in monsters]
        if creatures:
            by_category["creature"] = [spawn(*c) for c in creatures]
        attrs["minecraft:gameplay/natural_mob_spawns"] = {"argument": {"spawn_costs": {}, "spawns_by_category": by_category},
                                                          "modifier": "overlay"}
        write(os.path.join(HC, "worldgen", "biome", name + ".json"), b)
        names["biome.hellcraft." + name] = title
    for name, pf in FORGE_PLACED.items():
        write(os.path.join(HC, "worldgen", "placed_feature", name + ".json"), pf)
    for name in FORGE_FEATURE_TYPES:
        write(os.path.join(HC, "worldgen", "feature", name + ".json"), {"type": "hellcraft:" + name})
    write(os.path.join(HC, "worldgen", "material_rule", "forge.json"), forge_material_rule())
    write(os.path.join(HC, "worldgen", "noise_settings", "forge.json"), forge_noise_settings())
    tags = os.path.join(MC, "tags", "worldgen", "biome")
    for tag, regions in FORGE_STRUCTURES.items():
        add_tag(os.path.join(tags, tag + ".json"), ["hellcraft:" + r for r in regions])
    return names


# ============================================================================================ Purgatory

# Purgatory (Phase 7): its own small dimension, a starlit sea before dawn. The flat generator lays the sea; the
# mountain is built once by Purgatory.java.
PURGATORY_SEA = 62

def purgatory():
    write(os.path.join(HC, "worldgen", "biome", "purgatory.json"), {
        "attributes": {
            "minecraft:visual/sky_color": "#1c2a5c",
            "minecraft:visual/fog_color": "#33426e",
            "minecraft:visual/water_fog_color": "#0e2a40",
            "minecraft:audio/background_music": {"default": {"sound": "minecraft:music.overworld.meadow", "min_delay": 6000,
                                                             "max_delay": 18000}},
            "minecraft:visual/ambient_particles": {"argument": [{"particle": {"type": "minecraft:white_ash"}, "probability": 0.002}],
                                                   "modifier": "append"},
            "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {
                "water_ambient": [{"type": "minecraft:cod", "count": {"type": "minecraft:uniform", "min_inclusive": 2,
                                                                      "max_inclusive": 4}, "weight": 10}]}},
                "modifier": "overlay"},
        },
        "carvers": [],
        "downfall": 0.4,
        "effects": {"water_color": "#2a5aa8", "grass_color": "#6fbf5a", "foliage_color": "#5aa848"},
        "features": [[] for _ in range(10)],
        "has_precipitation": False,
        "temperature": 0.7,
    })
    # the hour before dawn, for ever: stars still out, the sky going pale in the east
    write(os.path.join(HC, "dimension_type", "purgatory.json"), {
        "ambient_light": 0.1,
        "attributes": {
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": False},
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/sky_light_level": 12.0,
            "minecraft:visual/sun_angle": 258.0,
            "minecraft:visual/moon_angle": 78.0,
            "minecraft:visual/star_angle": 250.0,
            "minecraft:visual/star_brightness": 0.85,
            "minecraft:visual/sky_light_factor": 0.55,
            "minecraft:visual/sky_light_color": "#a8b4ff",
            "minecraft:visual/ambient_light_color": "#30384c",
            "minecraft:visual/sunrise_sunset_color": "#66ff9a5a",
            "minecraft:visual/cloud_color": "#668090c0",
            "minecraft:visual/cloud_height": 180.0,
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": True,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
    })
    return {"biome.hellcraft.purgatory": "Purgatory"}


def purgatory_generator():
    """The preset's Purgatory: a flat sea floor under the starlit sea (Purgatory.java builds the mountain)."""
    return {"type": "minecraft:flat", "settings": {
        "biome": "hellcraft:purgatory", "features": False, "lakes": False, "structure_overrides": [],
        "layers": [{"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:stone", "height": 30},
                   {"block": "minecraft:sand", "height": 4}, {"block": "minecraft:water", "height": PURGATORY_SEA - 34}]}}


# ============================================================================================ Paradiso

# Paradiso (Phase 7): its own dimension, nine spheres stacked one above the other and the Empyrean on top
# (ParadisoGeometry.java). The noise generator makes nothing but air; the islands are one feature
# (ParadisoIslandFeature) and each sphere's biome (ParadisoBiomeSource, by height) gives it its sky.
# sphere: (title, sky, fog, particle, music)
SPHERES = {
    "moon": ("The Moon", "#c8d4e8", "#dfe6f2", ("minecraft:white_ash", 0.006), "minecraft:music.overworld.snowy_slopes"),
    "mercury": ("Mercury", "#cfe0f4", "#e8eef6", None, "minecraft:music.overworld.meadow"),
    "venus": ("Venus", "#f2c6dc", "#f8dde9", ("minecraft:cherry_leaves", 0.01), "minecraft:music.overworld.cherry_grove"),
    "sun": ("The Sun", "#ffe7a3", "#fff1c9", ("minecraft:wax_on", 0.006), "minecraft:music.overworld.meadow"),
    "mars": ("Mars", "#f0b49a", "#f6cdb9", ("minecraft:crimson_spore", 0.004), "minecraft:music.overworld.stony_peaks"),
    "jupiter": ("Jupiter", "#e6ecff", "#f3f5ff", ("minecraft:wax_off", 0.004), "minecraft:music.overworld.meadow"),
    "saturn": ("Saturn", "#bcd3e6", "#d9e6f0", ("minecraft:snowflake", 0.006), "minecraft:music.overworld.frozen_peaks"),
    "fixed_stars": ("The Fixed Stars", "#6c7cc8", "#9aa8e0", ("minecraft:end_rod", 0.003), "minecraft:music.overworld.snowy_slopes"),
    "primum_mobile": ("The Primum Mobile", "#b8f0ff", "#dcf8ff", ("minecraft:end_rod", 0.006), "minecraft:music.end"),
    "empyrean": ("The Empyrean", "#fff8e8", "#fffdf6", ("minecraft:end_rod", 0.01), "minecraft:music.credits"),
}
PARADISO_HEIGHT = 1024


def paradiso():
    write(os.path.join(HC, "worldgen", "feature", "paradiso_islands.json"), {"type": "hellcraft:paradiso_islands"})
    write(os.path.join(HC, "worldgen", "placed_feature", "paradiso_islands.json"), placed("hellcraft:paradiso_islands"))
    names = {}
    for sphere, (title, sky, fog, particle, track) in SPHERES.items():
        attrs = {
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/water_fog_color": "#8ab4e8",
            "minecraft:audio/background_music": {"default": {"sound": track, "min_delay": 3000, "max_delay": 12000,
                                                             "replace_current_music": True}},
            "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {}}, "modifier": "overlay"},
        }
        if particle:
            attrs["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle[0]}, "probability": particle[1]}],
                                                           "modifier": "append"}
        steps = [[] for _ in range(10)]
        steps[3] = ["hellcraft:paradiso_islands"]
        write(os.path.join(HC, "worldgen", "biome", "paradiso_" + sphere + ".json"), {
            "attributes": attrs, "carvers": [], "downfall": 0.4,
            "effects": {"water_color": "#5a9ae8", "grass_color": "#7ad06a", "foliage_color": "#66c060"},
            "features": steps, "has_precipitation": False, "temperature": 0.7})
        names["biome.hellcraft.paradiso_" + sphere] = "Paradiso: " + title

    # only air: the islands are a feature
    settings = vanilla("noise_settings__end")
    settings.pop("debug_functions", None)
    settings["default_block"] = "minecraft:air"
    settings["disable_mob_generation"] = True
    settings["legacy_random_source"] = False
    settings["material_rule"] = "minecraft:end"
    settings["noise"] = {"min_y": 0, "height": 16}
    settings["noise_router"] = {k: 0.0 for k in settings["noise_router"]}
    settings["noise_router"]["final_density"] = -1.0
    write(os.path.join(HC, "worldgen", "noise_settings", "paradiso.json"), settings)

    # heaven's light: always day, the sun high, a few stars showing through the blue
    write(os.path.join(HC, "dimension_type", "paradiso.json"), {
        "ambient_light": 0.35,
        "attributes": {
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": False},
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:visual/sun_angle": 20.0,
            "minecraft:visual/moon_angle": 200.0,
            "minecraft:visual/star_angle": 20.0,
            "minecraft:visual/star_brightness": 0.35,
            "minecraft:visual/sky_light_color": "#fff4d6",
            # islands hang over islands: their shadows are soft, never black
            "minecraft:visual/ambient_light_color": "#9a9aa6",
            "minecraft:visual/cloud_color": "#ccffffff",
            "minecraft:visual/cloud_height": 1100.0,
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": True,
        "height": PARADISO_HEIGHT,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": PARADISO_HEIGHT,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
    })
    return names


def paradiso_generator():
    return {"type": "minecraft:noise", "biome_source": {"type": "hellcraft:paradiso"}, "settings": "hellcraft:paradiso"}


# The End was Paradiso until Phase 7; it is vanilla's again (and unreachable in an Inferno world: no strongholds).
OLD_END_FILES = [
    os.path.join(MC, "worldgen", "biome", "the_end.json"),
    os.path.join(MC, "worldgen", "material_rule", "end.json"),
    os.path.join(MC, "worldgen", "density_function", "end", "sloped_cheese.json"),
    os.path.join(MC, "dimension_type", "the_end.json"),
    os.path.join(MC, "loot_table", "chests", "end_city_treasure.json"),
    os.path.join(HC, "worldgen", "biome", "paradiso_threshold.json"),
]
OLD_NAMES = ["biome.minecraft.the_end", "entity.minecraft.ender_dragon", "biome.hellcraft.paradiso_threshold"]


# ============================================================================================ the names

def lang(names):
    with open(LANG) as f:
        current = json.load(f)
    for key in OLD_NAMES:
        current.pop(key, None)
    current.update(names)
    write(LANG, current)


def main():
    names = {}
    names.update(nether())
    names.update(forge())
    names.update(purgatory())
    names.update(paradiso())
    for path in OLD_END_FILES:
        if os.path.exists(path):
            os.remove(path)
    lang(names)
    print("Reshaped the Nether (%d biomes), built the Forge of Dis (%d regions), Purgatory and Paradiso (%d spheres)."
          % (len(NETHER_BIOMES), len(FORGE_REGIONS), len(SPHERES)))


if __name__ == "__main__":
    main()
