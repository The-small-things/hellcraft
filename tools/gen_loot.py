#!/usr/bin/env python3
"""Generates Hellcraft's loot tables (Minecraft 26.3 data pack).

Run from the repo root:  python3 tools/gen_loot.py

- chests/altar_ruin: the ruined Blood Altars scattered through Hell
- chests/virgils_rest_upper|middle|lower: the supply chests at the Virgil's Rests (see ShrineSites.java),
  by depth: Limbo to Greed, the Styx and Heresy, then the Wood, the Sands and Malebolge
- chests/heretic_tomb: the "forbidden books" in some of Heresy's burning tombs
- chests/feast_table: the barrels at the heads of Gluttony's sunken feast tables
- chests/vault: the vaults in Greed's Counting House
- chests/fen_cache: the watchtowers and the drowned boats of the Fen of Styx
- gameplay/guardian_spoils: what a slain circle guardian leaves besides its blood

Blood Fragments, Vigil Candles and Soul Anchors are Hellcraft's own items (HellItems.java).
"""
import json
import os
import sys

DATA = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data", "hellcraft",
                    "loot_table")

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def item(name, weight, lo=1, hi=1, *modifiers):
    entry = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": weight}
    mods = []
    if (lo, hi) != (1, 1):
        mods.append({"count": uniform(lo, hi), "type": "minecraft:set_count"})
    mods += list(modifiers)
    if len(mods) == 1:
        entry["modifier"] = mods[0]
    elif mods:
        entry["modifier"] = mods
    return entry


def custom(base, components, weight, lo=1, hi=1):
    return item(base, weight, lo, hi, {"components": components, "type": "minecraft:set_components"})


def fragments(weight, lo, hi):
    return item("hellcraft:blood_fragment", weight, lo, hi)


def vigils(weight, lo=1, hi=1):
    return item("hellcraft:vigil_candle", weight, lo, hi)


def anchor(weight):
    return item("hellcraft:soul_anchor", weight)


def book(weight, lo, hi):
    """An enchanted book, enchanted as if at a table with lo-hi levels."""
    return item("book", weight, 1, 1, {"levels": uniform(lo, hi), "type": "minecraft:enchant_with_levels"})


def potion(weight, effect):
    return item("potion", weight, 1, 1, {"id": "minecraft:" + effect, "type": "minecraft:set_potion"})


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


def pool(lo, hi, entries):
    return {"rolls": uniform(lo, hi) if lo != hi else lo, "entries": entries}


def chest(*pools):
    return {"type": "minecraft:chest", "pools": list(pools)}


# the supplies every pilgrim is short of: arrows, books, paper, food, light
STAPLES = [
    item("arrow", 12, 8, 20),
    item("bread", 10, 2, 6),
    item("apple", 8, 2, 6),
    item("cooked_beef", 6, 2, 5),
    item("torch", 10, 6, 16),
    item("sugar_cane", 8, 3, 9),
    item("paper", 6, 3, 9),
    item("book", 6, 1, 3),
    item("leather", 6, 2, 5),
    item("string", 5, 2, 6),
    item("feather", 5, 3, 8),
    item("flint", 4, 2, 5),
]

TABLES = {
    "chests/altar_ruin": chest(
        pool(3, 6, [
            item("crying_obsidian", 10, 1, 4),
            item("glowstone_dust", 10, 2, 8),
            item("bread", 12, 2, 6),
            item("apple", 8, 2, 5),
            item("iron_ingot", 10, 1, 5),
            item("gold_ingot", 6, 1, 4),
            item("arrow", 8, 6, 16),
            item("book", 6, 1, 3),
            item("sugar_cane", 5, 2, 6),
            item("name_tag", 4),
            item("golden_apple", 3),
            fragments(8, 1, 3),
            vigils(4),
            book(4, 5, 20),
        ])),
    "chests/virgils_rest_upper": chest(
        pool(4, 7, STAPLES + [
            item("iron_ingot", 8, 2, 6),
            item("coal", 6, 4, 10),
            fragments(6, 1, 3),
        ]),
        pool(1, 1, [vigils(3, 1, 2), book(2, 5, 15), item("bow", 2), item("golden_apple", 1)])),
    "chests/virgils_rest_middle": chest(
        pool(4, 7, STAPLES + [
            item("iron_ingot", 8, 3, 8),
            item("gold_ingot", 4, 2, 6),
            item("diamond", 3, 1, 2),
            item("ender_pearl", 3, 1, 3),
            item("experience_bottle", 4, 2, 6),
            fragments(8, 2, 4),
        ]),
        pool(1, 2, [vigils(3, 1, 2), book(3, 15, 25), potion(2, "fire_resistance"), item("golden_apple", 2), item("crossbow", 1)])),
    "chests/virgils_rest_lower": chest(
        pool(4, 7, STAPLES + [
            item("diamond", 5, 1, 3),
            item("gold_ingot", 4, 2, 6),
            item("ender_pearl", 4, 2, 4),
            item("experience_bottle", 5, 3, 8),
            item("leather_chestplate", 2),
            item("leather_boots", 2),
            fragments(8, 2, 5),
        ]),
        pool(1, 2, [vigils(3, 1, 2), book(4, 25, 30), potion(3, "long_fire_resistance"), item("golden_apple", 2)]),
        pool(1, 1, [anchor(1), empty(3)])),
    "chests/heretic_tomb": chest(
        pool(1, 2, [book(1, 15, 30)]),
        pool(2, 4, [
            item("paper", 6, 3, 9),
            item("book", 5, 1, 3),
            item("lapis_lazuli", 6, 3, 9),
            item("experience_bottle", 5, 2, 5),
            item("bone", 6, 2, 6),
            item("soul_torch", 4, 2, 6),
            fragments(4, 1, 2),
        ])),
    "chests/forge_ruin": chest(
        pool(3, 6, [
            item("iron_ingot", 10, 2, 7),
            item("gold_ingot", 8, 2, 6),
            item("netherite_scrap", 3, 1, 2),
            item("coal", 8, 4, 12),
            item("magma_cream", 5, 1, 4),
            item("blaze_powder", 5, 1, 4),
            item("iron_chain", 5, 2, 6),
            fragments(8, 1, 4),
            potion(4, "fire_resistance"),
        ]),
        pool(1, 1, [item("netherite_upgrade_smithing_template", 1), item("golden_apple", 2), book(2, 15, 30), empty(5)])),
    "chests/feast_table": chest(
        pool(4, 8, [
            item("cooked_beef", 10, 2, 6),
            item("cooked_porkchop", 10, 2, 6),
            item("baked_potato", 8, 3, 8),
            item("pumpkin_pie", 6, 1, 4),
            item("cake", 3),
            item("honey_bottle", 5, 1, 3),
            item("rotten_flesh", 8, 2, 8),
            item("bone", 6, 2, 6),
            item("gold_nugget", 6, 3, 12),
            item("golden_carrot", 4, 2, 6),
            fragments(5, 1, 2),
        ]),
        pool(1, 1, [item("golden_apple", 2), book(2, 10, 20), potion(2, "regeneration"), empty(4)])),
    "chests/vault": chest(
        pool(3, 6, [
            item("gold_ingot", 12, 2, 8),
            item("gold_nugget", 10, 6, 18),
            item("raw_gold", 8, 2, 6),
            item("emerald", 8, 2, 6),
            item("diamond", 4, 1, 3),
            item("gold_block", 2),
            item("golden_carrot", 4, 2, 5),
            item("clock", 3),
            fragments(5, 1, 3),
        ]),
        pool(1, 1, [item("golden_apple", 3), item("enchanted_golden_apple", 1), book(2, 20, 30), item("golden_horse_armor", 2), empty(4)])),
    "chests/fen_cache": chest(
        pool(3, 6, [
            item("oak_boat", 6),
            item("arrow", 8, 6, 16),
            item("bread", 8, 2, 6),
            item("iron_ingot", 6, 2, 5),
            item("lily_pad", 4, 2, 6),
            item("nautilus_shell", 2),
            item("experience_bottle", 4, 2, 5),
            potion(5, "water_breathing"),
            fragments(6, 1, 3),
        ]),
        pool(1, 1, [item("trident", 1), book(2, 15, 25), item("turtle_helmet", 1), item("golden_apple", 2), empty(5)])),
    "gameplay/guardian_spoils": {"type": "minecraft:chest", "pools": [
        pool(2, 2, [book(1, 30, 30)]),
        pool(1, 2, [item("diamond", 3, 1, 3), item("golden_apple", 3), item("experience_bottle", 3, 4, 8), vigils(2, 1, 2)]),
    ]},
}


# Vanilla's oak and dark oak leaves (26.3), with apples six times as likely: Hell has few other foods early on.
VANILLA = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data", "minecraft",
                       "loot_table")
NOT_SHEARED = {"type": "minecraft:inverted", "term": {"type": "minecraft:any_of",
                                                      "terms": ["minecraft:tool/can_shear", "minecraft:tool/can_silk_touch"]}}


def fortune(*chances):
    return {"type": "minecraft:table_bonus", "chances": list(chances), "enchantment": "minecraft:fortune"}


def leaves(wood):
    return {"type": "minecraft:block", "pools": [
        {"entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": NOT_SHEARED["term"], "name": "minecraft:%s_leaves" % wood},
            {"type": "minecraft:item",
             "condition": {"type": "minecraft:all_of", "terms": [
                 {"type": "minecraft:survives_explosion"}, fortune(0.05, 0.0625, 0.083333336, 0.1)]},
             "name": "minecraft:%s_sapling" % wood}]}], "rolls": 1},
        {"condition": NOT_SHEARED, "entries": [
            {"type": "minecraft:item", "condition": fortune(0.02, 0.022222223, 0.025, 0.033333335, 0.1),
             "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "max": 2, "min": 1}},
                          {"type": "minecraft:explosion_decay"}],
             "name": "minecraft:stick"}], "rolls": 1},
        {"condition": NOT_SHEARED, "entries": [
            {"type": "minecraft:item",
             "condition": {"type": "minecraft:all_of", "terms": [
                 {"type": "minecraft:survives_explosion"}, fortune(0.03, 0.035, 0.04, 0.05, 0.12)]},
             "name": "minecraft:apple"}], "rolls": 1},
    ], "random_sequence": "minecraft:blocks/%s_leaves" % wood}


def main():
    for name, table in TABLES.items():
        write(os.path.join(DATA, name + ".json"), table)
    for wood in ("oak", "dark_oak"):
        write(os.path.join(VANILLA, "blocks", wood + "_leaves.json"), leaves(wood))
    print("Wrote %d loot tables." % (len(TABLES) + 2))


if __name__ == "__main__":
    main()
