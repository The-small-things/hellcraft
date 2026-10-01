#!/usr/bin/env python3
"""Dante's Journey: Hellcraft's advancement tab (Minecraft 26.3).

Run from the repo root:  python3 tools/gen_advancements.py   (gen_worldgen.py --26.3 runs it too)

Places are reached with vanilla location criteria on Hellcraft's biomes; deeds (a guardian slain, a P
burned, Lucifer cast down) use a single "done" criterion of type minecraft:impossible, which the mod
awards from code (util/Journey.java).
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data", "hellcraft",
                   "advancement", "journey")


def icon(item, model=None):
    i = {"id": item}
    if model:
        i["components"] = {"minecraft:item_model": model}
    return i


def at_biomes(*biomes):
    """One criterion per biome (any of them is enough)."""
    criteria = {}
    for b in biomes:
        criteria[b.split(":")[1]] = {
            "trigger": "minecraft:location",
            "conditions": {"player": {"type": "minecraft:entity_properties", "entity": "this",
                                      "predicate": {"minecraft:location": {"biomes": b}}}},
        }
    return criteria, [list(criteria)]


def deed():
    return {"done": {"trigger": "minecraft:impossible"}}, [["done"]]


def write(name, parent, title, description, ico, criteria, frame="task", hidden=False, root=False):
    crit, req = criteria
    display = {
        "icon": ico,
        "title": {"text": title},
        "description": {"text": description},
        "frame": frame,
    }
    if root:
        display["background"] = "minecraft:block/blackstone"
        display["show_toast"] = False
        display["announce_to_chat"] = False
    if hidden:
        display["hidden"] = True
    adv = {}
    if parent:
        adv["parent"] = "hellcraft:journey/" + parent
    adv.update({"criteria": crit, "display": display, "requirements": req})
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(adv, f, indent=2)
        f.write("\n")
    return name


def main():
    for old in os.listdir(OUT) if os.path.isdir(OUT) else []:
        os.remove(os.path.join(OUT, old))
    n = 0

    def w(*args, **kwargs):
        nonlocal n
        n += 1
        return write(*args, **kwargs)

    # the root is granted the moment you set foot in the Inferno
    w("root", None, "Dante's Journey", "Abandon all hope, ye who enter here.",
      icon("minecraft:crying_obsidian"), ({"tick": {"trigger": "minecraft:tick"}}, [["tick"]]), root=True)
    w("dark_wood", "root", "Midway Upon the Journey", "Enter the Dark Wood, where Hell begins",
      icon("minecraft:dark_oak_sapling"), at_biomes("hellcraft:dark_wood", "hellcraft:vestibule", "hellcraft:acheron"))

    circles = [
        ("limbo", "The First Circle", "Reach Limbo, the first circle", "minecraft:gray_candle", ["limbo"]),
        ("lust", "The Second Circle", "Reach Lust, the second circle", "minecraft:pink_petals", ["lust"]),
        ("gluttony", "The Third Circle", "Reach Gluttony, the third circle", "minecraft:rotten_flesh", ["gluttony"]),
        ("greed", "The Fourth Circle", "Reach Greed, the fourth circle", "minecraft:gold_block", ["greed"]),
        ("wrath", "The Fifth Circle", "Reach the river Styx in Wrath, the fifth circle", "minecraft:mud", ["styx"]),
        ("heresy", "The Sixth Circle", "Pass the Walls of Dis into Heresy, the sixth circle",
         "minecraft:soul_lantern", ["walls_of_dis", "heresy"]),
        ("violence", "The Seventh Circle", "Reach Violence, the seventh circle",
         "minecraft:magma_block", ["phlegethon", "wood_of_suicides", "burning_sands"]),
        ("fraud", "The Eighth Circle", "Reach Malebolge (Fraud), the eighth circle", "minecraft:black_concrete_powder",
         ["malebolge", "malebolge_blight", "malebolge_pitch", "well_of_giants"]),
        ("treachery", "The Ninth Circle", "Reach the frozen lake of Cocytus (Treachery), the ninth circle", "minecraft:blue_ice", ["cocytus"]),
        ("judecca", "Judecca", "Reach Judecca, where Lucifer is frozen at the centre", "minecraft:packed_ice", ["judecca"]),
    ]
    parent = "dark_wood"
    for key, title, desc, item, biomes in circles:
        parent = w(key, parent, title, desc, icon(item), at_biomes(*["hellcraft:" + b for b in biomes]))

    w("lucifer", "judecca", "Lucifer Cast Down", "Defeat Lucifer in all three phases",
      icon("minecraft:stick", "hellcraft:lucifer_emperor"), deed(), frame="challenge")
    w("purgatory", "lucifer", "To Rebehold the Stars", "Climb out of Hell to Purgatory",
      icon("minecraft:light_blue_stained_glass"), deed(), frame="goal")
    w("first_p", "purgatory", "The First Terrace", "Prestige once at a Blood Altar",
      icon("minecraft:feather"), deed(), frame="goal")
    w("purified", "first_p", "Pure and Ready to Rise", "Reach max prestige (7 of 7)",
      icon("minecraft:nether_star"), deed(), frame="challenge")

    guardians = [
        ("minos", "Judge of the Damned", "Defeat Minos, a guardian boss", "limbo"),
        ("cerberus", "Three Throats", "Defeat Cerberus, a guardian boss", "gluttony"),
        ("plutus", "Pape Satàn, Pape Satàn Aleppe", "Defeat Plutus, a guardian boss", "greed"),
        ("minotaur", "The Infamy of Crete", "Defeat the Minotaur, a guardian boss", "violence"),
        ("geryon", "The Face of a Just Man", "Defeat Geryon, a guardian boss", "fraud"),
    ]
    for gid, title, desc, parent in guardians:
        w("guardian_" + gid, parent, title, desc, icon("minecraft:stick", "hellcraft:guardian_" + gid), deed(), frame="goal")

    w("forge", "root", "The Forge of Dis", "Enter the Nether",
      icon("minecraft:anvil"), ({"entered": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "minecraft:the_nether"}}},
                                [["entered"]]))
    w("guardian_vulcan", "forge", "Hammer of the Gods", "Defeat Vulcan at the Great Forge in the Nether",
      icon("minecraft:stick", "hellcraft:guardian_vulcan"), deed(), frame="challenge")

    spheres = ["moon", "mercury", "venus", "sun", "mars", "jupiter", "saturn", "fixed_stars", "primum_mobile", "empyrean"]
    w("paradiso", "purgatory", "Paradiso", "Reach Paradiso's first ring of islands in the End",
      icon("minecraft:glowstone"), at_biomes(*["hellcraft:paradiso_" + s for s in spheres]), frame="goal")
    w("seraph", "paradiso", "The Seraph Falls", "Help defeat the Seraph in the End",
      icon("minecraft:stick", "hellcraft:halo"), deed(), frame="challenge")
    w("empyrean", "paradiso", "The Love That Moves the Sun", "Reach the Empyrean, the farthest ring in the End",
      icon("minecraft:end_rod"), at_biomes("hellcraft:paradiso_empyrean"))
    w("beatrice", "empyrean", "Beatrice", "Ring the bell at the Celestial Rose",
      icon("minecraft:stick", "hellcraft:beatrices_rose"), deed(), frame="goal")

    w("hell_is_full", "root", "Hell Is Full", "Lose your last heart and become a ghost",
      icon("minecraft:skeleton_skull"), deed(), hidden=True)
    w("harrowing", "root", "The Harrowing", "Revive a ghost at a Blood Altar",
      icon("minecraft:respawn_anchor"), deed())
    print("Wrote", n, "advancements to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
