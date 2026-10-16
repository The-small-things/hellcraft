#!/usr/bin/env python3
"""Hellcraft's own enchantments (3.0), each found in one circle only (never at an enchanting table, from a librarian
or in ordinary loot): data/hellcraft/enchantment/<id>.json and their names in the lang file. Where each is found is in
tools/gen_loot.py (CIRCLE_BOOKS); what the data can't do is in the Java code (blood/LayerEnchants.java).

Run from the repo root:  python3 tools/gen_enchants.py
"""
import json
import os

RES = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources")
DATA = os.path.join(RES, "data", "hellcraft", "enchantment")
LANG = os.path.join(RES, "assets", "hellcraft", "lang", "en_us.json")


def linear(base, per_level):
    return {"type": "minecraft:linear", "base": base, "per_level_above_first": per_level}


def attribute(eid, attr, base, per_level, operation="add_multiplied_base"):
    return {"id": "hellcraft:enchantment." + eid, "attribute": "minecraft:" + attr, "amount": linear(base, per_level), "operation": operation}


# id, name, items it goes on, max level, slots, effects (the rest is Java: LayerEnchants)
ENCHANTS = [
    # Lust: the wind carries you
    ("windstep", "Windstep", "#minecraft:enchantable/foot_armor", 3, ["feet"],
     {"minecraft:attributes": [attribute("windstep", "movement_speed", 0.08, 0.08)]}),
    # Greed: the hoarders' quick hands
    ("quickening", "Quickening", "#minecraft:enchantable/sharp_weapon", 2, ["mainhand"],
     {"minecraft:attributes": [attribute("quickening", "attack_speed", 0.1, 0.1)]}),
    # Wrath: the dash (LayerEnchants.dash, the Dash key)
    ("sidestep", "Sidestep", "#minecraft:enchantable/leg_armor", 3, ["legs"], {}),
    # Heresy: less damage from bosses (LayerEnchants.bossDamageTaken)
    ("steadfast", "Steadfast", "#minecraft:enchantable/chest_armor", 4, ["chest"], {}),
    # Violence: more damage to bosses (LayerEnchants.bossDamageDealt)
    ("judgement", "Judgement", "#minecraft:enchantable/sharp_weapon", 5, ["mainhand"], {}),
]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    names = {"key.hellcraft.dash": "Dash (Sidestep leggings)"}
    for eid, name, items, max_level, slots, effects in ENCHANTS:
        write(os.path.join(DATA, eid + ".json"), {
            "description": {"translate": "enchantment.hellcraft." + eid},
            "supported_items": items,
            "weight": 1,
            "max_level": max_level,
            "min_cost": {"base": 15, "per_level_above_first": 10},
            "max_cost": {"base": 45, "per_level_above_first": 10},
            "anvil_cost": 4,
            "slots": slots,
            "effects": effects,
        })
        names["enchantment.hellcraft." + eid] = name
    with open(LANG) as f:
        current = json.load(f)
    current.update(names)
    write(LANG, current)
    print("Wrote %d enchantments." % len(ENCHANTS))


if __name__ == "__main__":
    main()
