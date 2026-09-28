#!/usr/bin/env python3
"""Generates Hellcraft's loot tables (Minecraft 26.3 data pack).

Run from the repo root:  python3 tools/gen_loot.py

- chests/altar_ruin: the ruined Blood Altars scattered through Hell
- chests/virgils_rest_upper|middle|lower: the supply chests at the Virgil's Rests (see ShrineSites.java),
  by depth: Limbo to Greed, the Styx and Heresy, then the Wood, the Sands and Malebolge
- chests/heretic_tomb: the "forbidden books" in some of Heresy's burning tombs
- gameplay/guardian_spoils: what a slain circle guardian leaves besides its blood

Custom items (Blood Fragments, Vigil Candles, Soul Anchors) carry the same components as BloodItems.java;
the game re-stamps them anyway once they are in a player's inventory.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_models import ANCHOR, FRAGMENT, VIGIL  # noqa: E402

DATA = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data", "hellcraft",
                    "loot_table")

FRAGMENT_ITEM = dict(FRAGMENT["components"], **{
    "minecraft:lore": [{"text": "Right-click 8 together to clot them into a Blood Heart", "color": "gray", "italic": False}],
    "minecraft:rarity": "uncommon",
})


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def item(name, weight, lo=1, hi=1, *modifiers):
    entry = {"type": "minecraft:item", "name": "minecraft:" + name, "weight": weight}
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
    return custom("red_dye", FRAGMENT_ITEM, weight, lo, hi)


def vigils(weight, lo=1, hi=1):
    return custom("poisonous_potato", VIGIL, weight, lo, hi)


def anchor(weight):
    return custom("poisonous_potato", ANCHOR, weight)


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
    "gameplay/guardian_spoils": {"type": "minecraft:chest", "pools": [
        pool(2, 2, [book(1, 30, 30)]),
        pool(1, 2, [item("diamond", 3, 1, 3), item("golden_apple", 3), item("experience_bottle", 3, 4, 8), vigils(2, 1, 2)]),
    ]},
}


def main():
    for name, table in TABLES.items():
        write(os.path.join(DATA, name + ".json"), table)
    print("Wrote %d loot tables." % len(TABLES))


if __name__ == "__main__":
    main()
