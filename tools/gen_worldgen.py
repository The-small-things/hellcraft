#!/usr/bin/env python3
"""Generates Hellcraft's data-driven worldgen JSON.

Run from the repo root:  python3 tools/gen_worldgen.py            (Minecraft 1.21.1, src/main/resources)
                         python3 tools/gen_worldgen.py --26.3     (Minecraft 26.3, versions/26.3/src/main/resources)

Everything under src/main/resources/data/{hellcraft,minecraft} that describes biomes, surface
rules, noise settings, placed/configured features and biome tags is produced here. Keeping it in
one script guarantees that every biome lists its features in the same global order (Minecraft
refuses to load biomes whose feature orders conflict) and keeps the per-circle design readable.
"""
import json
import os
import shutil
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data")
HC = os.path.join(ROOT, "hellcraft")
MC = os.path.join(ROOT, "minecraft")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def block(name, **props):
    s = {"Name": name if ":" in name else "minecraft:" + name}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


# --------------------------------------------------------------------------------------------
# Feature steps, in Minecraft's GenerationStep.Decoration order.
RAW, LAKES, LOCAL, UG_STRUCT, SURF_STRUCT, STRONGHOLDS, ORES, UG_DECO, SPRINGS, VEG, TOP = range(11)

# Canonical global order of every placed feature we use, per step. Biomes pick subsets.
CANON = {
    RAW: [],
    LAKES: ["hellcraft:ring_fluid", "minecraft:lake_lava_underground", "hellcraft:filth_pool"],
    LOCAL: ["minecraft:amethyst_geode"],
    UG_STRUCT: ["minecraft:monster_room", "minecraft:monster_room_deep"],
    SURF_STRUCT: ["hellcraft:dis_wall", "hellcraft:burning_tomb", "hellcraft:altar_ruin", "minecraft:desert_well"],
    STRONGHOLDS: [],
    ORES: [
        "minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
        "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
        "minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
        "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
        "minecraft:ore_gold_lower", "minecraft:ore_gold_extra", "hellcraft:ore_gilded_blackstone",
        "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
        "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
        "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper", "minecraft:ore_emerald",
        "hellcraft:ore_quartz_crust",
    ],
    UG_DECO: [],
    SPRINGS: ["minecraft:spring_water", "minecraft:spring_lava"],
    VEG: [
        "minecraft:glow_lichen", "minecraft:dark_forest_vegetation", "minecraft:forest_rock", "hellcraft:limbo_birch",
        "hellcraft:dead_tree", "minecraft:trees_swamp", "minecraft:crimson_fungi_surface_placeholder",
        "hellcraft:crimson_fungus", "hellcraft:boulder_blackstone", "hellcraft:boulder_crying_obsidian",
        "hellcraft:boulder_packed_ice", "hellcraft:boulder_deepslate", "minecraft:ice_spike",
        "hellcraft:surface_delta", "hellcraft:surface_basalt_columns", "hellcraft:surface_sculk",
        "hellcraft:surface_fire", "hellcraft:surface_soul_fire", "hellcraft:cobwebs",
        "minecraft:patch_grass_forest", "minecraft:patch_grass_plain", "minecraft:flower_meadow",
        "minecraft:patch_dead_bush_2", "minecraft:patch_dead_bush_badlands", "minecraft:patch_cactus_desert",
        "hellcraft:crimson_roots", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal",
        "minecraft:brown_mushroom_swamp", "minecraft:red_mushroom_swamp", "minecraft:patch_berry_common",
        "minecraft:patch_waterlily", "minecraft:seagrass_swamp", "minecraft:seagrass_river",
    ],
    TOP: ["hellcraft:gate_wall", "minecraft:freeze_top_layer"],
}
CANON[VEG].remove("minecraft:crimson_fungi_surface_placeholder")

OVERWORLD_ORES = [
    "minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
    "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
    "minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
    "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
    "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
    "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
    "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper",
]
BASE_FEATURES = ["hellcraft:ring_fluid", "minecraft:lake_lava_underground", "minecraft:monster_room",
                 "minecraft:monster_room_deep", "hellcraft:altar_ruin", "minecraft:spring_water",
                 "minecraft:spring_lava", "minecraft:glow_lichen", "minecraft:freeze_top_layer"] + OVERWORLD_ORES


def ordered_features(extra, exclude=()):
    wanted = set(BASE_FEATURES) | set(extra)
    wanted -= set(exclude)
    for f in wanted:
        if not any(f in lst for lst in CANON.values()):
            raise SystemExit("feature %s missing from CANON" % f)
    return [[f for f in CANON[step] if f in wanted] for step in range(11)]


# --------------------------------------------------------------------------------------------
# Surface rules

def cond(if_true, then_run):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}


def blk(state):
    return {"type": "minecraft:block", "result_state": state if isinstance(state, dict) else block(state)}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def noise(name, lo, hi=1.0e9):
    return {"type": "minecraft:noise_threshold", "noise": name, "min_threshold": lo, "max_threshold": hi}


def depth(offset, add_surface=False, floor=True, secondary=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "add_surface_depth": add_surface,
            "secondary_depth_range": secondary, "surface_type": "floor" if floor else "ceiling"}


def patches(default, *pairs):
    """pairs of (noise_condition, state) evaluated in order, then default."""
    rules = [cond(c, blk(s)) for c, s in pairs]
    rules.append(blk(default))
    return seq(*rules)


ABOVE_SURFACE = {"type": "minecraft:above_preliminary_surface"}
STEEP = {"type": "minecraft:steep"}


def layered(top, under, crust, crust_depth=24, under_depth=0, steep=None):
    """Top block, a few blocks of sub-surface, then a thick 'crust' so cliff faces show the circle's rock."""
    top_rule = top if isinstance(top, dict) and "type" in top else blk(top)
    under_rule = under if isinstance(under, dict) and "type" in under else blk(under)
    crust_rule = crust if isinstance(crust, dict) and "type" in crust else blk(crust)
    inner = []
    if steep:
        inner.append(cond(STEEP, cond(depth(crust_depth), blk(steep))))
    inner += [
        cond(depth(0), top_rule),
        cond(depth(under_depth, add_surface=True), under_rule),
        cond(depth(crust_depth), crust_rule),
    ]
    return cond(ABOVE_SURFACE, seq(*inner))


SURFACE = {
    "dark_wood": layered(
        patches("grass_block", (noise("minecraft:surface", 0.35), "podzol"), (noise("minecraft:surface", -1e9, -0.55), "coarse_dirt")),
        "dirt", "stone", steep="stone"),
    "vestibule": layered(
        patches("coarse_dirt", (noise("minecraft:gravel", 0.1), "gravel"), (noise("minecraft:surface", 0.4), "tuff")),
        "gravel", "tuff"),
    "acheron": layered(patches("gravel", (noise("minecraft:surface", 0.3), "clay")), "gravel", "tuff"),
    "limbo": layered(
        patches("grass_block", (noise("minecraft:calcite", 0.05), "calcite"), (noise("minecraft:surface", 0.5), "tuff")),
        "dirt", "calcite", steep="calcite"),
    "lust": layered(
        patches("tuff", (noise("minecraft:surface", 0.45), "amethyst_block"), (noise("minecraft:gravel", 0.35), "deepslate"),
                (noise("minecraft:calcite", 0.55), "crying_obsidian")),
        "deepslate", "deepslate"),
    "gluttony": layered(
        patches("mud", (noise("minecraft:surface", 0.3), "muddy_mangrove_roots"), (noise("minecraft:gravel", 0.25), "packed_mud")),
        "mud", "packed_mud"),
    "greed": layered(
        patches("blackstone", (noise("minecraft:surface", 0.6), "raw_gold_block"), (noise("minecraft:surface", 0.3), "gilded_blackstone"),
                (noise("minecraft:gravel", 0.3), "basalt")),
        "blackstone", "blackstone"),
    "styx": layered(patches("mud", (noise("minecraft:surface_swamp", 0.2), "soul_soil")), "mud", "smooth_basalt"),
    "walls_of_dis": layered("blackstone", "blackstone", "polished_blackstone"),
    "heresy": layered(
        patches("soul_soil", (noise("minecraft:soul_sand_layer", 0.0), "soul_sand"), (noise("minecraft:surface", 0.45), "basalt")),
        "soul_soil", "basalt"),
    "phlegethon": layered(patches("crimson_nylium", (noise("minecraft:surface", 0.35), "magma_block")), "netherrack", "netherrack"),
    "wood_of_suicides": layered(
        patches("coarse_dirt", (noise("minecraft:surface", 0.2), "rooted_dirt"), (noise("minecraft:gravel", 0.3), "podzol")),
        "dirt", "blackstone"),
    "burning_sands": layered(
        patches("red_sand", (noise("minecraft:surface", 0.45), "netherrack"), (noise("minecraft:gravel", 0.4), "sand")),
        "red_sand", "red_sandstone", under_depth=2),
    "malebolge": layered(patches("blackstone", (noise("minecraft:surface", 0.3), "deepslate")), "deepslate", "deepslate"),
    "malebolge_pitch": layered(patches("magma_block", (noise("minecraft:surface", 0.2), "blackstone")), "basalt", "basalt"),
    "malebolge_blight": layered(patches("sculk", (noise("minecraft:surface", 0.4), "deepslate")), "deepslate", "deepslate"),
    "well_of_giants": layered(patches("cobbled_deepslate", (noise("minecraft:surface", 0.2), "deepslate")), "deepslate", "deepslate"),
    "cocytus": layered(
        patches("packed_ice", (noise("minecraft:powder_snow", 0.45), "snow_block"), (noise("minecraft:ice", 0.3), "ice")),
        "packed_ice", "packed_ice", crust_depth=8),
    "judecca": layered(patches("blue_ice", (noise("minecraft:packed_ice", 0.3), "packed_ice")), "blue_ice", "packed_ice", crust_depth=10),
}


def surface_rule():
    rules = [cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                   "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
                  blk("bedrock"))]
    for biome_id, rule in SURFACE.items():
        rules.append(cond({"type": "minecraft:biome", "biome_is": ["hellcraft:" + biome_id]}, rule))
    rules.append(cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:deepslate",
                       "true_at_and_below": {"absolute": 0}, "false_at_and_above": {"absolute": 8}},
                      blk(block("deepslate", axis="y"))))
    return seq(*rules)


# --------------------------------------------------------------------------------------------
# Biomes

def spawn(mob, weight, lo=1, hi=4):
    return {"type": "minecraft:" + mob, "weight": weight, "minCount": lo, "maxCount": hi}


def music(track):
    return {"sound": "minecraft:" + track, "min_delay": 12000, "max_delay": 24000, "replace_current_music": False}


def nether_sounds(kind):
    return {
        "ambient_sound": "minecraft:ambient.%s.loop" % kind,
        "mood_sound": {"sound": "minecraft:ambient.%s.mood" % kind, "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        "additions_sound": {"sound": "minecraft:ambient.%s.additions" % kind, "tick_chance": 0.0111},
    }


CAVE_MOOD = {"mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}}

BIOMES = {
    "dark_wood": dict(
        temp=0.7, rain=True, downfall=0.8,
        colors=dict(fog_color=0x22301E, sky_color=0x1A2418, water_color=0x2A3A4A, water_fog_color=0x05080A,
                    grass_color=0x3A5A2A, foliage_color=0x2A4A1A, grass_color_modifier="dark_forest"),
        sound=CAVE_MOOD, music="music.overworld.forest",
        features=["hellcraft:gate_wall", "minecraft:dark_forest_vegetation", "minecraft:patch_grass_forest", "minecraft:brown_mushroom_normal",
                  "minecraft:red_mushroom_normal", "minecraft:patch_berry_common", "minecraft:ore_emerald", "minecraft:forest_rock"],
        monster=[spawn("zombie", 100), spawn("skeleton", 100), spawn("spider", 100), spawn("creeper", 80),
                 spawn("enderman", 10, 1, 2), spawn("witch", 5, 1, 1)],
        creature=[spawn("sheep", 12), spawn("pig", 10), spawn("chicken", 10), spawn("cow", 8), spawn("rabbit", 4, 2, 3),
                  spawn("wolf", 8, 2, 4), spawn("ocelot", 2, 1, 1)],
    ),
    "vestibule": dict(
        temp=0.5, rain=True, downfall=0.4,
        colors=dict(fog_color=0x4A4A48, sky_color=0x2A2A2A, water_color=0x3A4048, water_fog_color=0x0A0C10,
                    grass_color=0x7A7A60, foliage_color=0x6A6A50),
        particle=("minecraft:white_ash", 0.01), sound=CAVE_MOOD, music="music.overworld.deep_dark",
        features=["minecraft:patch_dead_bush_2", "hellcraft:boulder_deepslate"],
        monster=[spawn("zombie", 100, 4, 6), spawn("zombie_villager", 10, 1, 2), spawn("skeleton", 60), spawn("spider", 50),
                 spawn("creeper", 40), spawn("enderman", 10, 1, 2)],
        creature=[spawn("bee", 4, 1, 3), spawn("rabbit", 3, 1, 2)],
    ),
    "acheron": dict(
        temp=0.5, rain=True, downfall=0.6,
        colors=dict(fog_color=0x3A4050, sky_color=0x202830, water_color=0x2A3040, water_fog_color=0x0A0C10),
        particle=("minecraft:ash", 0.005), sound=CAVE_MOOD, music="music.overworld.deep_dark",
        features=["minecraft:seagrass_river"],
        monster=[spawn("drowned", 100, 1, 3), spawn("zombie", 40)],
        water_creature=[spawn("squid", 2, 1, 2)],
    ),
    "limbo": dict(
        temp=0.6, rain=True, downfall=0.5,
        colors=dict(fog_color=0x8A8A86, sky_color=0x5A5A5A, water_color=0x6A7078, water_fog_color=0x1A1C20,
                    grass_color=0x8A9A7A, foliage_color=0x7A8A6A),
        particle=("minecraft:white_ash", 0.03), sound=CAVE_MOOD, music="music.overworld.meadow",
        features=["minecraft:forest_rock", "hellcraft:limbo_birch", "minecraft:patch_grass_plain", "minecraft:flower_meadow"],
        monster=[spawn("skeleton", 80), spawn("stray", 60), spawn("zombie", 30), spawn("enderman", 20, 1, 2)],
        creature=[spawn("sheep", 12), spawn("cow", 8), spawn("horse", 5, 2, 4), spawn("rabbit", 6, 2, 3), spawn("chicken", 6)],
    ),
    "lust": dict(
        temp=0.8, rain=False, downfall=0.0,
        colors=dict(fog_color=0x4A2A5A, sky_color=0x3A1A4A, water_color=0x5A3A7A, water_fog_color=0x1A0A2A,
                    grass_color=0x6A4A6A, foliage_color=0x5A3A5A),
        particle=("minecraft:cherry_leaves", 0.02), sound=CAVE_MOOD, music="music.overworld.jagged_peaks",
        features=["minecraft:amethyst_geode", "hellcraft:boulder_crying_obsidian"],
        monster=[spawn("breeze", 40, 1, 2), spawn("enderman", 60, 2, 4), spawn("zombie", 60), spawn("skeleton", 60),
                 spawn("creeper", 50), spawn("spider", 40), spawn("witch", 10, 1, 1)],
    ),
    "gluttony": dict(
        temp=0.6, rain=True, downfall=1.0,
        colors=dict(fog_color=0x3A3020, sky_color=0x2A2418, water_color=0x4A3A20, water_fog_color=0x1A1408,
                    grass_color=0x5A5A2A, foliage_color=0x4A4A20, grass_color_modifier="swamp"),
        particle=("minecraft:mycelium", 0.02), sound=CAVE_MOOD, music="music.overworld.swamp",
        features=["hellcraft:filth_pool", "minecraft:brown_mushroom_swamp", "minecraft:red_mushroom_swamp"],
        monster=[spawn("zombie", 100, 4, 6), spawn("slime", 80), spawn("hoglin", 40, 2, 4), spawn("husk", 40),
                 spawn("spider", 60), spawn("creeper", 60)],
        creature=[spawn("pig", 10, 2, 4)],
    ),
    "greed": dict(
        temp=1.2, rain=False, downfall=0.0,
        colors=dict(fog_color=0x5A4A18, sky_color=0x3A3010, water_color=0x6A5A20, water_fog_color=0x1A1408,
                    grass_color=0x8A7A30, foliage_color=0x7A6A20),
        particle=("minecraft:wax_on", 0.004), sound=nether_sounds("nether_wastes"), music="music.nether.nether_wastes",
        features=["minecraft:ore_gold_extra", "hellcraft:ore_gilded_blackstone", "hellcraft:boulder_blackstone"],
        monster=[spawn("piglin", 80, 2, 4), spawn("piglin_brute", 5, 1, 1), spawn("zombified_piglin", 30, 2, 4),
                 spawn("skeleton", 60), spawn("zombie", 60), spawn("creeper", 40)],
    ),
    "styx": dict(
        temp=0.8, rain=True, downfall=0.9,
        colors=dict(fog_color=0x1A2A1A, sky_color=0x101A10, water_color=0x1A2A1A, water_fog_color=0x050A05,
                    grass_color=0x2A3A20, foliage_color=0x2A3A1A, grass_color_modifier="swamp"),
        particle=("minecraft:ash", 0.01), sound=CAVE_MOOD, music="music.overworld.swamp",
        features=["hellcraft:dis_wall", "minecraft:trees_swamp", "minecraft:patch_waterlily", "minecraft:seagrass_swamp",
                  "minecraft:brown_mushroom_swamp"],
        monster=[spawn("drowned", 100, 2, 4), spawn("vindicator", 25, 1, 2), spawn("zombie", 60), spawn("witch", 10, 1, 1),
                 spawn("slime", 40)],
        creature=[spawn("frog", 6, 2, 4)],
    ),
    "walls_of_dis": dict(
        temp=2.0, rain=False, downfall=0.0,
        colors=dict(fog_color=0x3A1010, sky_color=0x2A0A0A, water_color=0x3A1A1A, water_fog_color=0x100505),
        particle=("minecraft:ash", 0.02), sound=nether_sounds("nether_wastes"), music="music.nether.nether_wastes",
        features=["hellcraft:dis_wall"],
        monster=[spawn("wither_skeleton", 60, 1, 3), spawn("skeleton", 60), spawn("blaze", 20, 1, 2)],
    ),
    "heresy": dict(
        temp=2.0, rain=False, downfall=0.0,
        colors=dict(fog_color=0x1B4845, sky_color=0x0A2020, water_color=0x2A4A4A, water_fog_color=0x051010),
        particle=("minecraft:ash", 0.02), sound=nether_sounds("soul_sand_valley"), music="music.nether.soul_sand_valley",
        features=["hellcraft:dis_wall", "hellcraft:burning_tomb", "hellcraft:surface_basalt_columns", "hellcraft:surface_soul_fire"],
        monster=[spawn("blaze", 40, 1, 3), spawn("wither_skeleton", 60, 1, 3), spawn("skeleton", 60), spawn("magma_cube", 40),
                 spawn("zombie", 40), spawn("ghast", 2, 1, 1)],
    ),
    "phlegethon": dict(
        temp=2.0, rain=False, downfall=0.0,
        colors=dict(fog_color=0x5A0A0A, sky_color=0x3A0505, water_color=0x8A0A0A, water_fog_color=0x3A0000,
                    grass_color=0x6A2A1A, foliage_color=0x5A1A10),
        particle=("minecraft:crimson_spore", 0.03), sound=nether_sounds("crimson_forest"), music="music.nether.crimson_forest",
        features=["hellcraft:crimson_fungus", "hellcraft:crimson_roots", "hellcraft:surface_fire", "hellcraft:ore_quartz_crust"],
        monster=[spawn("skeleton", 100, 2, 4), spawn("zombified_piglin", 30, 2, 4), spawn("magma_cube", 60)],
        creature=[spawn("strider", 60, 1, 2)],
    ),
    "wood_of_suicides": dict(
        temp=1.0, rain=False, downfall=0.0,
        colors=dict(fog_color=0x2A1A14, sky_color=0x1A0E0A, water_color=0x4A1A10, water_fog_color=0x1A0505,
                    grass_color=0x4A3A2A, foliage_color=0x3A2A1A),
        particle=("minecraft:crimson_spore", 0.015), sound=nether_sounds("warped_forest"), music="music.nether.warped_forest",
        features=["hellcraft:dead_tree", "hellcraft:cobwebs", "minecraft:patch_dead_bush_2"],
        monster=[spawn("spider", 100), spawn("cave_spider", 40, 1, 3), spawn("witch", 20, 1, 1), spawn("zombie", 50),
                 spawn("skeleton", 50), spawn("enderman", 20, 1, 2)],
    ),
    "burning_sands": dict(
        temp=2.0, rain=False, downfall=0.0,
        colors=dict(fog_color=0x7A3A1A, sky_color=0x5A2010, water_color=0x7A3A2A, water_fog_color=0x2A0A05),
        particle=("minecraft:falling_lava", 0.01), sound=nether_sounds("basalt_deltas"), music="music.nether.basalt_deltas",
        features=["minecraft:desert_well", "hellcraft:surface_fire", "minecraft:patch_dead_bush_badlands", "minecraft:patch_cactus_desert"],
        monster=[spawn("husk", 100, 2, 4), spawn("skeleton", 50), spawn("ghast", 5, 1, 1), spawn("blaze", 20, 1, 2),
                 spawn("magma_cube", 40), spawn("creeper", 60)],
    ),
    "malebolge": dict(
        temp=1.5, rain=False, downfall=0.0,
        colors=dict(fog_color=0x2A2A30, sky_color=0x151518, water_color=0x2A2A3A, water_fog_color=0x0A0A10),
        particle=("minecraft:ash", 0.01), sound=nether_sounds("basalt_deltas"), music="music.nether.basalt_deltas",
        features=["hellcraft:boulder_deepslate"],
        monster=[spawn("vindicator", 30, 1, 2), spawn("witch", 40, 1, 2), spawn("pillager", 20, 1, 3), spawn("zombie", 60),
                 spawn("skeleton", 60), spawn("silverfish", 30, 2, 4), spawn("evoker", 2, 1, 1)],
    ),
    "malebolge_pitch": dict(
        temp=2.0, rain=False, downfall=0.0,
        colors=dict(fog_color=0x1A0A0A, sky_color=0x0A0505, water_color=0x1A0A0A, water_fog_color=0x050000),
        particle=("minecraft:smoke", 0.01), sound=nether_sounds("basalt_deltas"), music="music.nether.basalt_deltas",
        features=["hellcraft:surface_delta"],
        monster=[spawn("magma_cube", 60), spawn("blaze", 40, 1, 2), spawn("zombie", 30)],
    ),
    "malebolge_blight": dict(
        temp=0.8, rain=False, downfall=0.0,
        colors=dict(fog_color=0x0A2A30, sky_color=0x051518, water_color=0x0A2A30, water_fog_color=0x020A0A),
        particle=("minecraft:sculk_soul", 0.004), sound=CAVE_MOOD, music="music.overworld.deep_dark",
        features=["hellcraft:surface_sculk"],
        monster=[spawn("silverfish", 60, 2, 4), spawn("cave_spider", 40, 1, 3), spawn("zombie", 60), spawn("creeper", 40),
                 spawn("witch", 20, 1, 1)],
    ),
    "well_of_giants": dict(
        temp=0.5, rain=False, downfall=0.0,
        colors=dict(fog_color=0x303030, sky_color=0x181818, water_color=0x2A2A2A, water_fog_color=0x0A0A0A),
        particle=("minecraft:white_ash", 0.005), sound=nether_sounds("nether_wastes"), music="music.nether.nether_wastes",
        features=["hellcraft:boulder_deepslate"],
        monster=[spawn("wither_skeleton", 40, 1, 2), spawn("zombie", 60), spawn("skeleton", 60)],
    ),
    "cocytus": dict(
        temp=-0.8, rain=True, downfall=0.5,
        colors=dict(fog_color=0x9AB8D0, sky_color=0x5A7A9A, water_color=0x3A5A8A, water_fog_color=0x0A1A2A),
        particle=("minecraft:snowflake", 0.05), sound=CAVE_MOOD, music="music.overworld.frozen_peaks",
        features=["minecraft:ice_spike", "hellcraft:boulder_packed_ice"],
        monster=[spawn("stray", 120, 2, 4), spawn("skeleton", 40), spawn("zombie", 40)],
        creature=[spawn("polar_bear", 2, 1, 2)],
    ),
    "judecca": dict(
        temp=-1.0, rain=True, downfall=0.5,
        colors=dict(fog_color=0x7A9AC8, sky_color=0x3A5A8A, water_color=0x3A5A8A, water_fog_color=0x0A1A2A),
        particle=("minecraft:snowflake", 0.1), sound=CAVE_MOOD, music="music.overworld.frozen_peaks",
        features=["hellcraft:boulder_packed_ice"],
        monster=[spawn("stray", 120, 2, 4), spawn("wither_skeleton", 40, 1, 2), spawn("evoker", 3, 1, 1)],
    ),
}

NO_ALTAR = {"acheron", "walls_of_dis", "phlegethon", "malebolge_pitch"}


def make_biome(biome_id, d):
    effects = dict(d["colors"])
    effects.update(d.get("sound", {}))
    if "particle" in d:
        effects["particle"] = {"options": {"type": d["particle"][0]}, "probability": d["particle"][1]}
    effects["music"] = music(d["music"])
    exclude = {"hellcraft:altar_ruin"} if biome_id in NO_ALTAR else set()
    return {
        "has_precipitation": d["rain"],
        "temperature": d["temp"],
        "downfall": d["downfall"],
        "effects": effects,
        "spawners": {
            "monster": d.get("monster", []),
            "creature": d.get("creature", []),
            "ambient": [spawn("bat", 10, 8, 8)],
            "axolotls": [],
            "underground_water_creature": [],
            "water_creature": d.get("water_creature", []),
            "water_ambient": [],
            "misc": [],
        },
        "spawn_costs": {},
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
        "features": ordered_features(d["features"], exclude),
    }


# --------------------------------------------------------------------------------------------
# Configured & placed features

def placed(feature, *placement):
    return {"feature": feature, "placement": list(placement)}


def count(n):
    return {"type": "minecraft:count", "count": n}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}


def heightmap(kind="WORLD_SURFACE_WG"):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def height_range(lo, hi):
    return {"type": "minecraft:height_range",
            "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def random_patch(state, tries, xz, y, on=None):
    predicates = [{"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}]
    if on:
        predicates.append({"type": "minecraft:matching_blocks", "blocks": on, "offset": [0, -1, 0]})
    return {"type": "minecraft:random_patch", "config": {
        "tries": tries, "xz_spread": xz, "y_spread": y,
        "feature": {"feature": {"type": "minecraft:simple_block",
                                "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": state}}},
                    "placement": [{"type": "minecraft:block_predicate_filter",
                                   "predicate": {"type": "minecraft:all_of", "predicates": predicates}}]}}}


CONFIGURED = {
    "ring_fluid": {"type": "hellcraft:ring_fluid", "config": {}},
    "dis_wall": {"type": "hellcraft:dis_wall", "config": {}},
    "gate_wall": {"type": "hellcraft:gate_wall", "config": {}},
    "burning_tomb": {"type": "hellcraft:burning_tomb", "config": {}},
    "altar_ruin": {"type": "hellcraft:altar_ruin", "config": {}},
    "boulder_blackstone": {"type": "hellcraft:boulder", "config": {"state": block("blackstone")}},
    "boulder_crying_obsidian": {"type": "hellcraft:boulder", "config": {"state": block("crying_obsidian")}},
    "boulder_packed_ice": {"type": "hellcraft:boulder", "config": {"state": block("packed_ice")}},
    "boulder_deepslate": {"type": "hellcraft:boulder", "config": {"state": block("cobbled_deepslate")}},
    "filth_pool": {"type": "minecraft:lake", "config": {
        "fluid": {"type": "minecraft:simple_state_provider", "state": block("water", level=0)},
        "barrier": {"type": "minecraft:simple_state_provider", "state": block("mud")}}},
    "ore_gilded_blackstone": {"type": "minecraft:ore", "config": {"size": 16, "discard_chance_on_air_exposure": 0.0, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:blackstone"}, "state": block("gilded_blackstone")}]}},
    "dead_tree": {"type": "minecraft:tree", "config": {
        "decorators": [],
        "dirt_provider": {"type": "minecraft:simple_state_provider", "state": block("rooted_dirt")},
        "force_dirt": False, "ignore_vines": True,
        "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 0, "offset": 0, "height": 0},
        "foliage_provider": {"type": "minecraft:simple_state_provider", "state": block("air")},
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
        "trunk_placer": {"type": "minecraft:upwards_branching_trunk_placer", "base_height": 4, "height_rand_a": 3,
                         "height_rand_b": 2, "extra_branch_steps": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 4},
                         "place_branch_per_log_probability": 0.4,
                         "extra_branch_length": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 2},
                         "can_grow_through": "#minecraft:mangrove_logs_can_grow_through"},
        "trunk_provider": {"type": "minecraft:weighted_state_provider", "entries": [
            {"weight": 3, "data": block("stripped_dark_oak_log", axis="y")},
            {"weight": 1, "data": block("stripped_mangrove_log", axis="y")}]},
    }},
    "cobwebs": random_patch(block("cobweb"), 24, 5, 4),
    "crimson_roots": random_patch(block("crimson_roots"), 48, 6, 2, on="minecraft:crimson_nylium"),
    "surface_fire": random_patch(block("fire"), 48, 6, 2, on="minecraft:netherrack"),
    "surface_soul_fire": random_patch(block("soul_fire"), 48, 6, 2, on="minecraft:soul_soil"),
}

PLACED = {
    "ring_fluid": placed("hellcraft:ring_fluid"),
    "dis_wall": placed("hellcraft:dis_wall"),
    "gate_wall": placed("hellcraft:gate_wall"),
    "burning_tomb": placed("hellcraft:burning_tomb", rarity(3), IN_SQUARE, heightmap(), BIOME),
    "altar_ruin": placed("hellcraft:altar_ruin", rarity(1200), IN_SQUARE, heightmap(), BIOME),
    "boulder_blackstone": placed("hellcraft:boulder_blackstone", count(1), IN_SQUARE, heightmap(), BIOME),
    "boulder_crying_obsidian": placed("hellcraft:boulder_crying_obsidian", rarity(12), IN_SQUARE, heightmap(), BIOME),
    "boulder_packed_ice": placed("hellcraft:boulder_packed_ice", rarity(3), IN_SQUARE, heightmap(), BIOME),
    "boulder_deepslate": placed("hellcraft:boulder_deepslate", rarity(4), IN_SQUARE, heightmap(), BIOME),
    "filth_pool": placed("hellcraft:filth_pool", rarity(6), IN_SQUARE, heightmap(), BIOME),
    "ore_gilded_blackstone": placed("hellcraft:ore_gilded_blackstone", count(10), IN_SQUARE, height_range(30, 120), BIOME),
    "ore_quartz_crust": placed("minecraft:ore_quartz", count(14), IN_SQUARE, height_range(-10, 40), BIOME),
    "limbo_birch": placed("minecraft:birch", rarity(4), IN_SQUARE, {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                          heightmap("OCEAN_FLOOR"),
                          {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive",
                                                                                     "state": block("birch_sapling", stage=0)}},
                          BIOME),
    "dead_tree": placed("hellcraft:dead_tree", count(5), IN_SQUARE, heightmap("OCEAN_FLOOR"), BIOME),
    "crimson_fungus": placed("minecraft:crimson_fungus", count(3), IN_SQUARE, heightmap(), BIOME),
    "crimson_roots": placed("hellcraft:crimson_roots", count(2), IN_SQUARE, heightmap(), BIOME),
    "cobwebs": placed("hellcraft:cobwebs", count(2), IN_SQUARE, heightmap(), BIOME),
    "surface_fire": placed("hellcraft:surface_fire", count(2), IN_SQUARE, heightmap(), BIOME),
    "surface_soul_fire": placed("hellcraft:surface_soul_fire", count(2), IN_SQUARE, heightmap(), BIOME),
    "surface_delta": placed("minecraft:delta", count(6), IN_SQUARE, heightmap(), BIOME),
    "surface_basalt_columns": placed("minecraft:small_basalt_columns", rarity(2), IN_SQUARE, heightmap(), BIOME),
    "surface_sculk": placed("minecraft:sculk_patch_deep_dark", count(3), IN_SQUARE, heightmap(), BIOME),
}

# --------------------------------------------------------------------------------------------
# Noise settings / dimension / preset

SURFACE_DENSITY = {"type": "minecraft:cache_2d", "argument": {
    "type": "hellcraft:inferno", "mode": "surface",
    "detail": {"type": "minecraft:noise", "noise": "hellcraft:surface_detail", "xz_scale": 1.0, "y_scale": 0.0}}}
Y_GRADIENT = {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 320, "from_value": 8.0, "to_value": -40.0}
BASE_DENSITY = {"type": "minecraft:add", "argument1": {"type": "minecraft:mul", "argument1": 0.125, "argument2": SURFACE_DENSITY},
                "argument2": Y_GRADIENT}
JAGGED = {"type": "minecraft:mul",
          "argument1": {"type": "minecraft:cache_2d", "argument": {"type": "hellcraft:inferno", "mode": "jagged"}},
          "argument2": {"type": "minecraft:noise", "noise": "hellcraft:jagged", "xz_scale": 1.0, "y_scale": 1.0}}

NOISE_SETTINGS = {
    "sea_level": 63,
    "disable_mob_generation": False,
    "aquifers_enabled": False,
    "ore_veins_enabled": False,
    "legacy_random_source": False,
    "default_block": block("stone"),
    "default_fluid": block("air"),
    "noise": {"min_y": -64, "height": 384, "size_horizontal": 1, "size_vertical": 2},
    "noise_router": {
        "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
        "temperature": 0.0, "vegetation": 0.0, "continents": 0.0, "erosion": 0.0, "depth": 0.0, "ridges": 0.0,
        "initial_density_without_jaggedness": BASE_DENSITY,
        "final_density": {"type": "minecraft:interpolated", "argument": {"type": "minecraft:add", "argument1": BASE_DENSITY, "argument2": JAGGED}},
        "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
    },
    "spawn_target": [{"temperature": [-1.0, 1.0], "humidity": [-1.0, 1.0], "continentalness": [-1.0, 1.0],
                      "erosion": [-1.0, 1.0], "depth": 0.0, "weirdness": [-1.0, 1.0], "offset": 0.0}],
    "surface_rule": surface_rule(),
}

DIMENSION_TYPE = {
    "ultrawarm": False, "natural": True, "piglin_safe": True, "respawn_anchor_works": False, "bed_works": False,
    "has_raids": False, "has_skylight": True, "has_ceiling": False, "coordinate_scale": 1.0,
    "ambient_light": 0.08, "fixed_time": 13400, "logical_height": 384, "effects": "minecraft:overworld",
    "infiniburn": "#minecraft:infiniburn_overworld", "min_y": -64, "height": 384,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
    "monster_spawn_block_light_limit": 0,
}

INFERNO_GENERATOR = {"type": "minecraft:noise", "biome_source": {"type": "hellcraft:circles"}, "settings": "hellcraft:inferno"}

# --------------------------------------------------------------------------------------------
# Biome tags

ALL = ["hellcraft:" + b for b in BIOMES]
TAGS = {
    "is_overworld": ALL,
    "has_structure/stronghold": ALL,
    "stronghold_biased_to": ["hellcraft:limbo", "hellcraft:greed", "hellcraft:heresy"],
    "has_structure/mineshaft": [b for b in ALL if b not in ("hellcraft:acheron", "hellcraft:phlegethon")],
    "has_structure/village_plains": ["hellcraft:limbo"],
    "has_structure/trail_ruins": ["hellcraft:limbo"],
    "has_structure/ruined_portal_nether": ["hellcraft:heresy", "hellcraft:burning_sands"],
    "has_structure/bastion_remnant": ["hellcraft:greed"],
    "has_structure/desert_pyramid": ["hellcraft:burning_sands"],
    "has_structure/swamp_hut": ["hellcraft:styx"],
    "has_structure/igloo": ["hellcraft:cocytus"],
    "has_structure/ancient_city": ["hellcraft:malebolge", "hellcraft:malebolge_blight", "hellcraft:malebolge_pitch"],
    "allows_surface_slime_spawns": ["hellcraft:gluttony", "hellcraft:styx"],
    "without_patrol_spawns": ALL,
    "without_zombie_sieges": ALL,
    "without_wandering_trader_spawns": [b for b in ALL if b != "hellcraft:limbo"],
    "increased_fire_burnout": ["hellcraft:wood_of_suicides", "hellcraft:dark_wood"],
    "snow_golem_melts": ["hellcraft:heresy", "hellcraft:phlegethon", "hellcraft:burning_sands", "hellcraft:walls_of_dis",
                         "hellcraft:malebolge_pitch"],
    "more_frequent_drowned_spawns": ["hellcraft:styx", "hellcraft:acheron"],
}


def main():
    for sub in ["worldgen/biome", "worldgen/configured_feature", "worldgen/placed_feature", "worldgen/noise_settings",
                "worldgen/noise", "worldgen/world_preset", "dimension_type"]:
        shutil.rmtree(os.path.join(HC, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(MC, "tags", "worldgen", "biome"), ignore_errors=True)
    shutil.rmtree(os.path.join(MC, "dimension"), ignore_errors=True)
    shutil.rmtree(os.path.join(MC, "tags", "worldgen", "world_preset"), ignore_errors=True)

    for biome_id, d in BIOMES.items():
        write(os.path.join(HC, "worldgen", "biome", biome_id + ".json"), make_biome(biome_id, d))
    for name, cfg in CONFIGURED.items():
        write(os.path.join(HC, "worldgen", "configured_feature", name + ".json"), cfg)
    for name, pf in PLACED.items():
        write(os.path.join(HC, "worldgen", "placed_feature", name + ".json"), pf)

    used = {f for lst in CANON.values() for f in lst if f.startswith("hellcraft:")}
    missing = [f for f in used if f.split(":")[1] not in PLACED]
    if missing:
        raise SystemExit("placed features without definitions: %s" % missing)

    write(os.path.join(HC, "worldgen", "noise", "surface_detail.json"), {"firstOctave": -8, "amplitudes": [1.0, 1.0, 0.6, 0.3, 0.15]})
    write(os.path.join(HC, "worldgen", "noise", "jagged.json"), {"firstOctave": -5, "amplitudes": [1.0, 0.8, 0.5]})
    write(os.path.join(HC, "worldgen", "noise_settings", "inferno.json"), NOISE_SETTINGS)
    write(os.path.join(HC, "dimension_type", "inferno.json"), DIMENSION_TYPE)
    write(os.path.join(HC, "worldgen", "world_preset", "inferno.json"), {"dimensions": {
        "minecraft:overworld": {"type": "hellcraft:inferno", "generator": INFERNO_GENERATOR},
        "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {
            "type": "minecraft:noise", "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
            "settings": "minecraft:nether"}},
        "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
            "type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"}, "settings": "minecraft:end"}},
    }})
    # "World Type: Inferno" on the Create World screen (single player) / level-type=hellcraft:inferno (servers)
    write(os.path.join(MC, "tags", "worldgen", "world_preset", "normal.json"), {"replace": False, "values": ["hellcraft:inferno"]})
    for tag, values in TAGS.items():
        write(os.path.join(MC, "tags", "worldgen", "biome", tag + ".json"), {"replace": False, "values": values})
    print("Generated %d biomes, %d configured and %d placed features." % (len(BIOMES), len(CONFIGURED), len(PLACED)))


# --------------------------------------------------------------------------------------------
# Minecraft 26.3. The data formats changed a lot in 26.x (environment attributes, material rules,
# inline feature configs, new block state and density function syntax), so the definitions above
# are translated here rather than duplicated.

ROOT26 = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data")


def is_state(o):
    return isinstance(o, dict) and "Name" in o and set(o) <= {"Name", "Properties"}


def state26(o):
    """Block state: a bare id for the default state, else {id, properties}."""
    if "Properties" in o:
        return {"id": o["Name"], "properties": o["Properties"]}
    return o["Name"]


def provider26(o):
    """Block state provider: a simple provider is written as the state itself, always in map form."""
    if o.get("type") == "minecraft:simple_state_provider":
        s = state26(o["state"])
        return {"id": s} if isinstance(s, str) else s
    if o.get("type") == "minecraft:weighted_state_provider":
        return {"type": "minecraft:weighted",
                "entries": [{"data": provider26({"type": "minecraft:simple_state_provider", "state": e["data"]}), "weight": e["weight"]}
                            for e in o["entries"]]}
    raise SystemExit("unknown state provider %s" % o)


def states26(o):
    """Converts every block state in a tree (surface rules, predicates)."""
    if is_state(o):
        return state26(o)
    if isinstance(o, dict):
        return {k: states26(v) for k, v in o.items()}
    if isinstance(o, list):
        return [states26(v) for v in o]
    return o


def density26(o):
    if not isinstance(o, dict):
        return o
    t = o["type"]
    if t in ("minecraft:add", "minecraft:mul", "minecraft:min", "minecraft:max"):
        return {"type": t, "left": density26(o["argument1"]), "right": density26(o["argument2"])}
    if t == "minecraft:cache_2d":
        # a column-constant function: sample it at one height and cache it
        return {"type": "minecraft:cache", "input": {"type": "minecraft:slice", "axis": "y", "coordinate": 0,
                                                      "input": density26(o["argument"])}}
    if t == "minecraft:y_clamped_gradient":
        return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": o["from_y"], "from_value": o["from_value"],
                "to_coordinate": o["to_y"], "to_value": o["to_value"]}
    if t == "minecraft:interpolated":
        # the old noise settings used size_horizontal 1 and size_vertical 2: 4x8 block cells
        return {"type": t, "cell_size_xz": 4, "cell_size_y": 8, "input": density26(o["argument"])}
    if t == "minecraft:noise":
        return dict(o)
    if t == "hellcraft:inferno":
        if o["mode"] != "surface":
            return dict(o)
        # 26.x density functions can't easily take inputs, so the column terms come from Java and the
        # detail noise is mixed in here, exactly like InfernoGeometry.surfaceHeight:
        # base + detail_amplitude * n + ridge_amplitude * (1 - |n|)^3
        n = density26(o["detail"])
        col = lambda mode: {"type": "hellcraft:inferno", "mode": mode}
        ridge = {"type": "minecraft:cube", "input": {"type": "minecraft:sub", "left": 1.0, "right": {"type": "minecraft:abs", "input": n}}}
        return {"type": "minecraft:add", "left": col("base"), "right": {
            "type": "minecraft:add",
            "left": {"type": "minecraft:mul", "left": col("detail_amplitude"), "right": n},
            "right": {"type": "minecraft:mul", "left": col("ridge_amplitude"), "right": ridge}}}
    raise SystemExit("unknown density function %s" % t)


def color(c):
    return "#%06x" % c


def spawn26(e):
    count = e["minCount"] if e["minCount"] == e["maxCount"] else \
        {"type": "minecraft:uniform", "min_inclusive": e["minCount"], "max_inclusive": e["maxCount"]}
    return {"type": e["type"], "count": count, "weight": e["weight"]}


FIRE_BURNOUT = set(TAGS["increased_fire_burnout"])
# Biomes where monsters spawn much more sparsely (26.3): single mobs plus a spawn "energy budget",
# the mechanism vanilla uses to keep the Soul Sand Valley thin
CALM_BIOMES26 = {"dark_wood", "vestibule", "acheron", "limbo"}
GOLEM_MELTS = set(TAGS["snow_golem_melts"])


def make_biome26(biome_id, d):
    old = make_biome(biome_id, d)
    colors = d["colors"]
    attrs = {
        "minecraft:visual/fog_color": color(colors["fog_color"]),
        "minecraft:visual/sky_color": color(colors["sky_color"]),
        "minecraft:visual/water_fog_color": color(colors["water_fog_color"]),
        "minecraft:audio/background_music": {"default": {k: v for k, v in music(d["music"]).items() if k != "replace_current_music"}},
    }
    sound = d.get("sound", {})
    ambient = {}
    if "mood_sound" in sound:
        ambient["mood"] = sound["mood_sound"]
    if "ambient_sound" in sound:
        ambient["loop"] = sound["ambient_sound"]
    if "additions_sound" in sound:
        ambient["additions"] = sound["additions_sound"]
    if ambient:
        attrs["minecraft:audio/ambient_sounds"] = ambient
    if "particle" in d:
        attrs["minecraft:visual/ambient_particles"] = {
            "argument": [{"particle": {"type": d["particle"][0]}, "probability": d["particle"][1]}], "modifier": "append"}
    spawns = {cat: [spawn26(e) for e in entries] for cat, entries in old["spawners"].items() if entries}
    costs = {}
    if biome_id in CALM_BIOMES26:
        # the upper circles players cross first: monsters come alone and keep their distance from each other
        for e in spawns.get("monster", []):
            e["count"] = 1
            costs[e["type"]] = {"charge": 0.7, "energy_budget": 0.15}
    attrs["minecraft:gameplay/natural_mob_spawns"] = {
        "argument": {"spawn_costs": costs, "spawns_by_category": spawns}, "modifier": "overlay"}
    attrs["minecraft:gameplay/can_pillager_patrol_spawn"] = False
    ident = "hellcraft:" + biome_id
    if ident in FIRE_BURNOUT:
        attrs["minecraft:gameplay/increased_fire_burnout"] = True
    if ident in GOLEM_MELTS:
        attrs["minecraft:gameplay/snow_golem_melts"] = True
    effects = {"water_color": color(colors["water_color"])}
    for key in ("grass_color", "foliage_color"):
        if key in colors:
            effects[key] = color(colors[key])
    if "grass_color_modifier" in colors:
        effects["grass_color_modifier"] = colors["grass_color_modifier"]
    return {
        "attributes": attrs,
        "carvers": old["carvers"]["air"],
        "downfall": old["downfall"],
        "effects": effects,
        "features": old["features"],
        "has_precipitation": old["has_precipitation"],
        "temperature": old["temperature"],
    }


AIR26 = {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}


def trapezoid(spread):
    return {"type": "minecraft:trapezoid", "min": -spread, "max": spread, "plateau": 0}


def feature26(name, cfg):
    t = cfg["type"]
    c = cfg["config"]
    if t.startswith("hellcraft:"):
        out = {"type": t}
        if "state" in c:
            out["state"] = state26(c["state"])
        return out
    if t == "minecraft:random_patch":
        # random patches are now a simple feature plus placement (see patch26)
        return {"type": "minecraft:simple_block", "to_place": provider26(c["feature"]["feature"]["config"]["to_place"])}
    if t == "minecraft:lake":
        return {"type": t, "barrier": provider26(c["barrier"]), "fluid": provider26(c["fluid"]),
                "can_place_feature": {"type": "minecraft:true"},
                "can_replace_with_air_or_fluid": {"type": "minecraft:not", "predicate": {
                    "type": "minecraft:matching_block_tag", "tag": "minecraft:features_cannot_replace"}},
                "can_replace_with_barrier": {"type": "minecraft:not", "predicate": {
                    "type": "minecraft:matching_block_tag", "tag": "minecraft:lava_pool_stone_cannot_replace"}}}
    if t == "minecraft:ore":
        return {"type": t, "discard_chance_on_air_exposure": c["discard_chance_on_air_exposure"], "size": c["size"],
                "targets": [{"state": state26(x["state"]), "target": x["target"]} for x in c["targets"]]}
    if t == "minecraft:tree":
        dirt = provider26(c["dirt_provider"])
        return {"type": t,
                "below_trunk_provider": {"type": "minecraft:rule_based", "rules": [{
                    "if_true": {"type": "minecraft:not", "predicate": {
                        "type": "minecraft:matching_block_tag", "tag": "minecraft:cannot_replace_below_tree_trunk"}},
                    "then": dirt}]},
                "decorators": c["decorators"],
                "foliage_placer": c["foliage_placer"],
                "foliage_provider": provider26(c["foliage_provider"]),
                "ignore_vines": c["ignore_vines"],
                "minimum_size": c["minimum_size"],
                "trunk_placer": c["trunk_placer"],
                "trunk_provider": provider26(c["trunk_provider"])}
    raise SystemExit("unknown feature type %s (%s)" % (t, name))


def placed26(name, pf):
    placement = states26(pf["placement"])
    cfg = CONFIGURED.get(pf["feature"].split(":")[1]) if pf["feature"].startswith("hellcraft:") else None
    if cfg and cfg["type"] == "minecraft:random_patch":
        c = cfg["config"]
        predicates = [AIR26] + [states26(p) for p in c["feature"]["placement"][0]["predicate"]["predicates"][1:]]
        placement += [count(c["tries"]),
                      {"type": "minecraft:offset", "x": trapezoid(c["xz_spread"]), "y": trapezoid(c["y_spread"]),
                       "z": trapezoid(c["xz_spread"])},
                      {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": predicates}}]
    return {"feature": pf["feature"], "placement": placement}


def noise26(first_octave, amplitudes, base_amplitude):
    return {"amplitude_modifiers": amplitudes, "base_amplitude": base_amplitude, "base_octave": first_octave,
            "octave_count": len(amplitudes)}


SURFACE_HEIGHT26 = density26(SURFACE_DENSITY)


def noise_settings26():
    old = NOISE_SETTINGS["noise_router"]
    return {
        "default_block": state26(NOISE_SETTINGS["default_block"]),
        "default_fluid": state26(NOISE_SETTINGS["default_fluid"]),
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "material_rule": states26(NOISE_SETTINGS["surface_rule"]),
        "noise": {"height": 384, "min_y": -64},
        "noise_router": {
            # the surface rules' "above preliminary surface" reads this: the funnel's own surface height
            "chunk_surface_level": SURFACE_HEIGHT26,
            "continents": 0.0, "depth": 0.0, "erosion": 0.0,
            "final_density": density26(old["final_density"]),
            "ridges": 0.0, "temperature": 0.0, "vegetation": 0.0,
        },
        "sea_level": NOISE_SETTINGS["sea_level"],
        "spawn_target": [],
    }


NIGHTMARE_BED = {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True}

DIMENSION_TYPE26 = {
    "ambient_light": DIMENSION_TYPE["ambient_light"],
    "attributes": {
        "minecraft:audio/ambient_sounds": {"mood": CAVE_MOOD["mood_sound"]},
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game"}},
        # bed_works: false -- beds explode, as in the Nether
        "minecraft:gameplay/bed_rule": NIGHTMARE_BED,
        "minecraft:gameplay/straw_bed_rule": NIGHTMARE_BED,
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:gameplay/can_start_raid": False,
        # piglin_safe: true
        "minecraft:gameplay/piglins_zombify": False,
        "minecraft:gameplay/nether_portal_spawns_piglin": True,
        "minecraft:visual/ambient_light_color": "#0a0a0a",
        "minecraft:visual/cloud_color": "#ccffffff",
        "minecraft:visual/cloud_height": 192.33,
        "minecraft:visual/fog_color": "#c0d8ff",
        "minecraft:visual/sky_color": "#78a7ff",
    },
    "coordinate_scale": 1.0,
    # eternal dusk: Landmarks stops the clock at 13400 when the world is created
    "default_clock": "minecraft:overworld",
    "has_ceiling": False,
    "has_ender_dragon_fight": False,
    "has_skylight": True,
    "height": 384,
    "infiniburn": "#minecraft:infiniburn_overworld",
    "logical_height": 384,
    "min_y": -64,
    "monster_spawn_block_light_limit": 0,
    "monster_spawn_light_level": DIMENSION_TYPE["monster_spawn_light_level"],
    "timelines": "#minecraft:in_overworld",
}

# Biome tags that became environment attributes in 26.x
TAGS26_DROPPED = {"without_patrol_spawns", "increased_fire_burnout", "snow_golem_melts"}


def main26():
    hc = os.path.join(ROOT26, "hellcraft")
    mc = os.path.join(ROOT26, "minecraft")
    for sub in ["worldgen/biome", "worldgen/feature", "worldgen/placed_feature", "worldgen/noise_settings",
                "worldgen/noise", "worldgen/world_preset", "dimension_type"]:
        shutil.rmtree(os.path.join(hc, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(mc, "tags", "worldgen"), ignore_errors=True)

    for biome_id, d in BIOMES.items():
        write(os.path.join(hc, "worldgen", "biome", biome_id + ".json"), make_biome26(biome_id, d))
    for name, cfg in CONFIGURED.items():
        write(os.path.join(hc, "worldgen", "feature", name + ".json"), feature26(name, cfg))
    for name, pf in PLACED.items():
        write(os.path.join(hc, "worldgen", "placed_feature", name + ".json"), placed26(name, pf))
    # base_amplitude is the overall scale (the old format derived it from the octave count)
    write(os.path.join(hc, "worldgen", "noise", "surface_detail.json"), noise26(-8, [1.0, 1.0, 0.6, 0.3, 0.15], 1.0))
    write(os.path.join(hc, "worldgen", "noise", "jagged.json"), noise26(-5, [1.0, 0.8, 0.5], 0.95))
    write(os.path.join(hc, "worldgen", "noise_settings", "inferno.json"), noise_settings26())
    write(os.path.join(hc, "dimension_type", "inferno.json"), DIMENSION_TYPE26)
    write(os.path.join(hc, "worldgen", "world_preset", "inferno.json"), {"dimensions": {
        "minecraft:overworld": {"type": "hellcraft:inferno", "generator": INFERNO_GENERATOR},
        "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
            "type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"}, "settings": "minecraft:end"}},
        "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {
            "type": "minecraft:noise", "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
            "settings": "minecraft:nether"}},
    }})
    write(os.path.join(mc, "tags", "worldgen", "world_preset", "normal.json"), {"replace": False, "values": ["hellcraft:inferno"]})
    for tag, values in TAGS.items():
        if tag not in TAGS26_DROPPED:
            write(os.path.join(mc, "tags", "worldgen", "biome", tag + ".json"), {"replace": False, "values": values})
    print("Generated %d biomes, %d features and %d placed features for 26.3." % (len(BIOMES), len(CONFIGURED), len(PLACED)))


if __name__ == "__main__":
    if "--26.3" in sys.argv:
        main26()
    else:
        main()
