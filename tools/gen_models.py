#!/usr/bin/env python3
"""Generates Hellcraft's item definitions and models (Minecraft 26.3 resource pack).

Run from the repo root:  python3 tools/gen_models.py   (after tools/gen_textures.py)

- Blood items and hell weapons: flat 16x16 icons (item/generated, item/handheld).
- Lucifer's two forms: block models built from cuboids, textured from the 32x32 swatch atlases of
  gen_textures.py. They are shown in-game by an item_display entity standing in for the invisible
  boss (see LuciferModel.java). Model space: 16 units = 1 block, feet at y = 0, the face looks
  toward -Z (north), which an item_display turns to face where the boss is looking.

tools/preview_model.py renders the models to PNGs for a quick look without Minecraft.
"""
import json
import os

ASSETS = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "assets", "hellcraft")

FLAT_ITEMS = {
    "blood_heart": "minecraft:item/generated",
    "blood_fragment": "minecraft:item/generated",
    "lucifers_bane": "minecraft:item/generated",
    "bloodletter": "minecraft:item/handheld",
    "reaper_of_minos": "minecraft:item/handheld",
    "tithe_axe": "minecraft:item/handheld",
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def item_definition(name):
    write(os.path.join(ASSETS, "items", name + ".json"),
          {"model": {"type": "minecraft:model", "model": "hellcraft:item/" + name}})


# ------------------------------------------------------------------------------------ cuboids

def uv(cell):
    """UV rectangle of a swatch cell (4x4 grid of 8px cells on a 32px texture = 4 UV units each)."""
    cx, cy = cell % 4, cell // 4
    return [cx * 4, cy * 4, cx * 4 + 4, cy * 4 + 4]


def box(frm, to, cell, north=None, south=None, east=None, west=None, up=None, down=None, rot=None):
    """A cuboid; every face uses `cell` unless overridden. rot = (axis, angle, origin)."""
    faces = {}
    for face, override in (("north", north), ("south", south), ("east", east), ("west", west), ("up", up), ("down", down)):
        faces[face] = {"uv": uv(override if override is not None else cell), "texture": "#skin"}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rot:
        axis, angle, origin = rot
        assert angle in (-45, -22.5, 0, 22.5, 45), angle
        el["rotation"] = {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}
    for v in el["from"] + el["to"]:
        assert -16 <= v <= 32, ("out of model bounds", el)
    return el


def mirror(el):
    """The same cuboid mirrored across x = 8 (for symmetric wings, arms, horns)."""
    m = json.loads(json.dumps(el))
    fx, tx = el["from"][0], el["to"][0]
    m["from"][0], m["to"][0] = round(16 - tx, 3), round(16 - fx, 3)
    m["faces"]["east"], m["faces"]["west"] = el["faces"]["west"], el["faces"]["east"]
    if "rotation" in m:
        m["rotation"]["origin"][0] = round(16 - el["rotation"]["origin"][0], 3)
        if m["rotation"]["axis"] in ("y", "z"):
            m["rotation"]["angle"] = -el["rotation"]["angle"]
    return m


def model(texture, elements):
    return {
        "textures": {"skin": texture, "particle": texture},
        "elements": elements,
        "display": {"gui": {"rotation": [20, 200, 0], "scale": [0.35, 0.35, 0.35], "translation": [0, -2, 0]}},
    }


# swatch cells (see gen_textures.py)
SKIN, SHADOW, FEATHER, FEATHER_TIP, HORN, HORN_TIP, GOLD, FACE, BLADE, BLADE_EDGE, HILT, CLOTH, EMBER = range(13)


def morning_star():
    """The Fallen Seraph: horned, crimson, black-winged, with a broken halo and a bloodied blade."""
    e = []
    # legs and hooves
    leg = box((4.5, 0, 6.5), (7.5, 12, 9.5), SHADOW)
    e += [leg, mirror(leg)]
    hoof = box((4.25, 0, 6), (7.75, 2, 10), HORN_TIP)
    e += [hoof, mirror(hoof)]
    # loincloth, torso, belt, chest
    e.append(box((3.5, 9, 5.75), (12.5, 14, 10.25), CLOTH))
    e.append(box((3.5, 12, 5.5), (12.5, 22, 10.5), SKIN, up=SHADOW, down=SHADOW))
    e.append(box((3.25, 12.5, 5.25), (12.75, 14, 10.75), HILT))
    e.append(box((2.5, 19, 5.25), (13.5, 22.5, 10.75), SKIN, up=SHADOW))  # shoulders
    # arms: the left hangs, the right reaches forward with the blade
    e.append(box((0, 11, 6.5), (2.75, 22, 9.5), SKIN, down=SHADOW))
    e.append(box((13.25, 11, 6.5), (16, 22, 9.5), SKIN, down=SHADOW, rot=("x", -22.5, (14.6, 21, 8))))
    # blade: hilt in the right hand, pointing down and forward
    e.append(box((14, 9.5, 2), (15.25, 12.5, 6), HILT, rot=("x", -22.5, (14.6, 21, 8))))
    e.append(box((13.75, -1, 0.5), (15.5, 10, 2.25), BLADE, rot=("x", -22.5, (14.6, 21, 8))))
    e.append(box((14.35, 0, 0.3), (14.9, 9.5, 0.5), BLADE_EDGE, rot=("x", -22.5, (14.6, 21, 8))))  # blood in the fuller
    # head: the face looks north (-Z)
    e.append(box((4.5, 22.5, 4.75), (11.5, 29.5, 11.25), SHADOW, north=FACE, east=SKIN, west=SKIN))
    e.append(box((5, 21.75, 5.5), (11, 23, 10.5), SHADOW))  # jaw / neck
    # horns: curling up and out
    horn = box((3.5, 28, 6.75), (5.75, 31.5, 9.25), HORN, rot=("z", 22.5, (4.6, 28.5, 8)))
    horn_tip = box((1.75, 30.25, 7.1), (3.5, 32, 8.9), HORN_TIP, rot=("z", 45, (2.6, 31, 8)))
    e += [horn, mirror(horn), horn_tip, mirror(horn_tip)]
    # the broken halo: a gold ring behind the head, cracked on one side
    e.append(box((2.5, 31, 12), (13.5, 32, 12.75), GOLD))
    e.append(box((1.5, 22, 12), (2.5, 32, 12.75), GOLD))
    e.append(box((13.5, 27.5, 12), (14.5, 32, 12.75), GOLD))
    e.append(box((13.5, 23, 12), (14.5, 25.5, 12.75), GOLD, rot=("z", 22.5, (14, 24, 12.4))))  # the broken piece, knocked askew
    # wings: a bony arm and two feathered panels per side, swept back
    sweep = ("y", -22.5, (4, 20, 13))
    arm = box((-10, 26, 13), (4, 28, 14.5), FEATHER_TIP, rot=sweep)
    upper = box((-15, 14, 13.25), (3, 26.5, 14.25), FEATHER, rot=sweep)
    lower = box((-15.5, 4, 13.5), (-4, 14.5, 14.5), FEATHER_TIP, rot=sweep)
    primaries = box((-16, 0, 13.75), (-10, 6, 14.5), FEATHER, rot=sweep)
    for piece in (arm, upper, lower, primaries):
        e += [piece, mirror(piece)]
    # embers in the chest
    e.append(box((7, 17, 5.25), (9, 19, 5.5), EMBER))
    return model("hellcraft:entity/lucifer_morning_star", e)


RED_FACE, YELLOW_FACE, BLACK_FACE, BODY, MEMBRANE, WING_BONE, ICE, DEEP_ICE, E_HORN, CROWN, HAIR, E_BLOOD = range(12)


def emperor():
    """The Emperor of the Dolorous Realm (Inferno XXXIV): three faces, six bat wings, frozen to the chest in ice."""
    e = []
    # the ice he is frozen into
    e.append(box((-6, 0, -2), (22, 7, 18), DEEP_ICE, up=ICE))
    e.append(box((-3, 7, 1), (19, 9, 15), ICE))
    # chest and shoulders rising from the ice, arms braced on it
    e.append(box((1.5, 7, 4), (14.5, 18, 12), BODY))
    e.append(box((0, 15, 4.5), (16, 19, 11.5), BODY, up=HAIR))
    arm = box((-3.5, 8, 5), (0.5, 17, 10), BODY, rot=("z", -22.5, (-1.5, 17, 7.5)))
    e += [arm, mirror(arm)]
    # one head, three faces: red ahead, pale yellow to the right, black to the left
    e.append(box((1.5, 18.5, 1.5), (14.5, 29.5, 13.5), HAIR, north=RED_FACE, east=YELLOW_FACE, west=BLACK_FACE))
    # blood running down from the three chins
    e.append(box((5, 15.5, 1.25), (11, 18.5, 1.75), E_BLOOD))
    # horns and a tarnished crown
    horn = box((2, 28.5, 6), (4, 32, 8.5), E_HORN, rot=("z", 22.5, (3, 29, 7)))
    e += [horn, mirror(horn)]
    for frm, to in (((1, 29, 1), (15, 30.5, 2)), ((1, 29, 13), (15, 30.5, 14)), ((1, 29, 2), (2, 30.5, 13)), ((14, 29, 2), (15, 30.5, 13))):
        e.append(box(frm, to, CROWN))
    for x in (2.5, 7, 11.5):
        e.append(box((x, 30.5, 1), (x + 2, 32, 2), CROWN))
    # six wings, three to a side, fanned out behind him
    wings = [
        (("z", -22.5, (2, 24, 13)), (-15, 20, 13), (2, 32, 13.75), (-15, 31, 13.5), (2, 32, 14.25)),
        (("y", -22.5, (2, 16, 13)), (-16, 11, 13.5), (2, 23, 14.25), (-16, 22, 14), (2, 23, 14.75)),
        (("z", 22.5, (3, 9, 13)), (-13, 6, 14), (3, 14, 14.75), (-13, 13, 14.5), (3, 14, 15.25)),
    ]
    for rot, mfrom, mto, bfrom, bto in wings:
        membrane = box(mfrom, mto, MEMBRANE, rot=rot)
        bone = box(bfrom, bto, WING_BONE, rot=rot)
        e += [membrane, mirror(membrane), bone, mirror(bone)]
    return model("hellcraft:entity/lucifer_emperor", e)


# ------------------------------------------------------------------------------------ recipes
# The results must match HellWeapons.create() in the 26.3 sources.

DATA = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data", "hellcraft")
HEART = {"fabric:type": "fabric:custom_data", "base": "minecraft:fermented_spider_eye", "nbt": "{hellcraft:\"heart\"}"}
FRAGMENT = {"fabric:type": "fabric:custom_data", "base": "minecraft:red_dye", "nbt": "{hellcraft:\"fragment\"}"}
OATH_LINE = "Blood Oath = sneak + right-click: costs 1 max heart, lasts 60 s."
WEAPONS = [
    ("bloodletter", "Bloodletter", "minecraft:iron_sword", "rare", ["FHF", " S "],
     ["Each hit spends 1 Blood Fragment: +4 damage and bleeding.",
      "Blood Oath: +10 damage, deep bleeding, each hit heals 1❤."]),
    ("reaper_of_minos", "Reaper of Minos", "minecraft:diamond_hoe", "epic", ["HFH", " S ", " F "],
     ["Each hit spends 1 Blood Fragment: cleaves everything",
      "within 3 blocks for 5 damage.",
      "Blood Oath: cleaves within 5 blocks for 12, slows and drags them in."]),
    ("tithe_axe", "Tithe Axe", "minecraft:diamond_axe", "epic", ["H H", " S "],
     ["Right-click: pay 3 Blood Fragments for a Blood Frenzy",
      "(Strength and Speed for 15 s).",
      "Blood Oath: Strength III, Speed II, Resistance, hits heal 1❤."]),
]


def recipes():
    for wid, title, base, rarity, pattern, lore in WEAPONS:
        components = {
            "minecraft:custom_data": {"hellcraft": "weapon", "weapon": wid},
            "minecraft:item_model": "hellcraft:" + wid,
            "minecraft:item_name": {"text": title, "color": "dark_red", "bold": True},
            "minecraft:lore": [{"text": line, "color": "gray", "italic": False} for line in lore]
                              + [{"text": OATH_LINE, "color": "red", "italic": False}],
            "minecraft:rarity": rarity,
        }
        if wid == "reaper_of_minos":
            components["minecraft:attribute_modifiers"] = [
                {"type": "minecraft:attack_damage", "id": "hellcraft:reaper_damage", "amount": 7.0, "operation": "add_value", "slot": "mainhand"},
                {"type": "minecraft:attack_speed", "id": "hellcraft:reaper_speed", "amount": -3.0, "operation": "add_value", "slot": "mainhand"},
            ]
        key = {"S": base}
        if any("H" in row for row in pattern):
            key["H"] = HEART
        if any("F" in row for row in pattern):
            key["F"] = FRAGMENT
        write(os.path.join(DATA, "recipe", wid + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "key": key, "pattern": pattern,
            "result": {"id": base, "components": components}})


def main():
    recipes()
    for name, parent in FLAT_ITEMS.items():
        item_definition(name)
        write(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": parent, "textures": {"layer0": "hellcraft:item/" + name}})
    for name, build in (("lucifer_morning_star", morning_star), ("lucifer_emperor", emperor)):
        item_definition(name)
        write(os.path.join(ASSETS, "models", "item", name + ".json"), build())
    # every texture a model names must exist
    for root, _, files in os.walk(os.path.join(ASSETS, "models")):
        for f in files:
            m = json.load(open(os.path.join(root, f)))
            for tex in m.get("textures", {}).values():
                ns, path = tex.split(":")
                assert os.path.exists(os.path.join(ASSETS, "textures", path + ".png")), (f, tex)
    print("Wrote %d item definitions." % (len(FLAT_ITEMS) + 2))


if __name__ == "__main__":
    main()
