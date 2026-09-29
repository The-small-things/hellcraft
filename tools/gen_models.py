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
    "vigil_candle": "minecraft:item/generated",
    "soul_anchor": "minecraft:item/generated",
    "halo": "minecraft:item/generated",
    "seraph_wings": "minecraft:item/generated",
    "beatrices_rose": "minecraft:item/generated",
    "bloodletter": "minecraft:item/handheld",
    "reaper_of_minos": "minecraft:item/handheld",
    "tithe_axe": "minecraft:item/handheld",
    "blood_helmet": "minecraft:item/generated",
    "blood_chestplate": "minecraft:item/generated",
    "blood_leggings": "minecraft:item/generated",
    "blood_boots": "minecraft:item/generated",
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


# ------------------------------------------------------------------------------------ the circle guardians


def minos():
    """Minos, judge of the damned (Inferno V): crowned, bearded, robed, his long tail coiled around him."""
    SKIN, SHADE, ROBE, GOLD, SCALES, BELLY, FACE, BEARD, HORN = range(9)
    e = []
    # the tail, coiled three times around his legs
    for i, (y0, inset) in enumerate(((0, 0.5), (3, 1.25), (6, 2))):
        a, b = inset, 16 - inset
        e.append(box((a, y0, a), (b, y0 + 3, a + 2.5), SCALES, down=BELLY))
        e.append(box((a, y0, b - 2.5), (b, y0 + 3, b), SCALES, down=BELLY))
        e.append(box((a, y0, a + 2.5), (a + 2.5, y0 + 3, b - 2.5), SCALES, down=BELLY))
        e.append(box((b - 2.5, y0, a + 2.5), (b, y0 + 3, b - 2.5), SCALES, down=BELLY))
    # the tail's end rises behind him, ready to lash
    e.append(box((7, 8, 13), (9.5, 20, 15.5), SCALES, rot=("x", 22.5, (8, 8, 14))))
    e.append(box((7.25, 19, 16), (9.25, 23, 18), SCALES, rot=("x", 45, (8, 20, 17))))
    # robe and body
    e.append(box((3, 9, 4), (13, 18, 12), ROBE))
    e.append(box((3.5, 17, 4.5), (12.5, 25, 11.5), ROBE, north=ROBE))
    e.append(box((3.25, 17, 4.25), (12.75, 18.25, 11.75), GOLD))            # belt
    e.append(box((7, 18, 4.25), (9, 25, 4.5), GOLD))                        # trim down the front
    arm = box((0.5, 14, 6), (3.5, 24.5, 10), ROBE, down=SKIN)
    e += [arm, mirror(arm)]
    # head, beard, horns and crown
    e.append(box((4.5, 25, 4.5), (11.5, 31, 11.5), SHADE, north=FACE, east=SKIN, west=SKIN))
    e.append(box((5, 21.5, 4), (11, 25.5, 5.5), BEARD))
    horn = box((3.25, 28.5, 7), (5, 31.5, 9), HORN, rot=("z", 22.5, (4, 29, 8)))
    e += [horn, mirror(horn)]
    e.append(box((4.25, 30.5, 4.25), (11.75, 32, 11.75), GOLD, up=SHADE))
    return model("hellcraft:entity/guardian_minos", e)


def cerberus():
    """Cerberus (Inferno VI): the great three-headed dog of the rain, collared in iron."""
    FUR, DARK, FACE, MAW, IRON, CLAW, FILTH, FANG = range(8)
    e = []
    e.append(box((3, 9, 0), (13, 19, 22), FUR, down=DARK))                  # body
    e.append(box((2.5, 15, -1), (13.5, 21, 8), FUR))                        # shoulders
    for z0 in (1, 17):
        leg = box((3, 0, z0), (6, 10, z0 + 4), DARK)
        paw = box((2.75, 0, z0 - 0.5), (6.25, 1.5, z0 + 4), CLAW)
        e += [leg, mirror(leg), paw, mirror(paw)]
    e.append(box((7, 15, 21), (9, 17, 30), DARK, rot=("x", -22.5, (8, 16, 22))))    # tail
    # three heads on three necks, the middle one highest
    for x0, lift in ((-0.5, 0), (5.25, 2), (11, 0)):
        e.append(box((x0 + 1, 17 + lift, -4), (x0 + 5.5, 22 + lift, 1), FUR))            # neck
        e.append(box((x0 + 0.5, 20 + lift, -9), (x0 + 6, 26 + lift, -3), DARK, north=FACE))
        e.append(box((x0 + 1.5, 20 + lift, -13), (x0 + 5, 23 + lift, -9), FUR, down=MAW, north=FANG))
        e.append(box((x0 + 1.75, 19 + lift, -12.5), (x0 + 4.75, 20 + lift, -9), MAW))     # open jaw
        ear = box((x0 + 0.75, 26 + lift, -6), (x0 + 2, 28 + lift, -4.5), DARK)
        e += [ear, box((x0 + 4.5, 26 + lift, -6), (x0 + 5.75, 28 + lift, -4.5), DARK)]
        e.append(box((x0 + 0.75, 17.5 + lift, -2.5), (x0 + 5.75, 19 + lift, -1.5), IRON))  # collar
    e.append(box((5, 8.5, 6), (11, 9, 16), FILTH))                          # dripping filth
    return model("hellcraft:entity/guardian_cerberus", e)


def plutus():
    """Plutus (Inferno VII): the bloated wolf of greed, hung with gold. "Pape Satan, pape Satan aleppe!"."""
    FUR, DARK, FACE, GOLD, TARNISH, BELLY, VELVET = range(7)
    e = []
    leg = box((3, 0, 6), (7, 8, 11), DARK)
    e += [leg, mirror(leg)]
    e.append(box((1, 6, 3), (15, 19, 14), FUR, north=BELLY))                # bloated belly
    e.append(box((2, 17, 4.5), (14, 24, 13), FUR))                          # hunched shoulders
    e.append(box((1.5, 12, 2.75), (14.5, 13, 3.25), GOLD))                  # chains of gold
    e.append(box((2, 15.5, 2.75), (14, 16.5, 3.25), TARNISH))
    arm = box((-1, 7, 6), (2, 22, 10), FUR, down=DARK)
    e += [arm, mirror(arm)]
    e.append(box((-2.5, 2, 4.5), (3, 8, 11), VELVET, up=GOLD))             # sacks of gold in his fists
    e.append(box((13, 2, 4.5), (18.5, 8, 11), VELVET, up=GOLD))
    e.append(box((4.5, 23, 3), (11.5, 29, 10), DARK, north=FACE))           # wolf head
    e.append(box((6, 23, -1), (10, 26, 3), FUR, north=FACE))                # snout
    ear = box((4.5, 29, 6), (6.5, 32, 8), DARK)
    e += [ear, mirror(ear)]
    e.append(box((4.75, 28.75, 3.5), (11.25, 30, 9.5), GOLD))               # a crown of coins
    return model("hellcraft:entity/guardian_plutus", e)


def minotaur():
    """The Minotaur (Inferno XII), infamy of Crete: bull-headed, huge-shouldered, raging."""
    HIDE, DARK, FACE, HORN, HOOF, LEATHER, RING, CHEST = range(8)
    e = []
    leg = box((4, 0, 6), (7.5, 12, 10), HIDE)
    hoof = box((3.75, 0, 5.5), (7.75, 2, 10.5), HOOF)
    e += [leg, mirror(leg), hoof, mirror(hoof)]
    e.append(box((3.5, 10, 5), (12.5, 15, 11), LEATHER))
    e.append(box((3, 14, 5), (13, 23, 11), CHEST))
    e.append(box((1, 20, 4.5), (15, 25, 11.5), HIDE))                       # shoulders
    arm = box((-1.5, 10, 6), (2, 23, 10), HIDE)
    fist = box((-1.75, 8, 5.75), (2.25, 11, 10.25), DARK)
    e += [arm, mirror(arm), fist, mirror(fist)]
    e.append(box((5, 24, 3.5), (11, 30, 10), DARK, north=FACE))             # bull head
    e.append(box((6, 24, 1), (10, 27, 3.5), DARK))                          # snout
    e.append(box((7.25, 23.25, 0.5), (8.75, 24.75, 1), RING))               # nose ring
    horn = box((1.5, 28, 6), (5.5, 29.5, 7.5), HORN)
    tip = box((0.5, 28.5, 6.25), (2, 32, 7.25), HOOF, rot=("z", -22.5, (1.25, 29, 6.75)))
    e += [horn, mirror(horn), tip, mirror(tip)]
    return model("hellcraft:entity/guardian_minotaur", e)


def vulcan():
    """Vulcan (Inferno XIV), the smith of the gods at his forge in Mongibello: soot-black, ember-veined,
    a leather apron, a beard of sparks, and the hammer that forged Jove's thunderbolts."""
    SKIN, EMBER, FACE, APRON, BEARD, IRON, HANDLE, HEART = range(8)
    e = []
    leg = box((4, 0, 6), (7.5, 11, 10), SKIN)
    boot = box((3.75, 0, 5.5), (7.75, 3, 10.5), IRON)
    e += [leg, mirror(leg), boot, mirror(boot)]
    e.append(box((3, 10, 4.5), (13, 22, 11.5), SKIN, north=HEART))             # the forge-heart glowing in his chest
    e.append(box((2.5, 6, 3.5), (13.5, 20, 4.5), APRON))                       # leather apron
    e.append(box((1, 19, 4.5), (15, 24, 11.5), SKIN))                          # shoulders
    arm = box((-2, 9, 6), (1.5, 23, 10), EMBER)
    e += [arm, mirror(arm)]
    e.append(box((5, 24, 4.5), (11, 30, 10.5), SKIN, north=FACE))             # head
    e.append(box((5.5, 21, 3.5), (10.5, 25, 5), BEARD))                        # beard of sparks
    # the hammer, in his right hand
    e.append(box((-1, 4, 7.25), (0.5, 22, 8.75), HANDLE))
    e.append(box((-3, 20, 5), (2.5, 25, 11), IRON))
    return model("hellcraft:entity/guardian_vulcan", e)


def geryon():
    """Geryon (Inferno XVII), the image of fraud: the face of a just man, a painted serpent's body,
    hairy paws, and a forked scorpion's tail."""
    FACE, HAIR, HIDE, BELLY, PAW, CLAW, SHELL, STING = range(8)
    e = []
    e.append(box((5, 13, -14), (11, 19, -8), HAIR, north=FACE))             # the face
    e.append(box((4.75, 18.5, -14.25), (11.25, 20, -7.75), HAIR))
    e.append(box((4.5, 11, -9), (11.5, 18, 0), HIDE, down=BELLY))
    e.append(box((4, 10, 0), (12, 17, 12), HIDE, down=BELLY))
    e.append(box((5, 11, 12), (11, 16, 22), HIDE, down=BELLY))
    e.append(box((6, 12, 22), (10, 15, 31), SHELL))
    # the tail rises from its end and arches forward over the back, sting first
    e.append(box((6.5, 13, 26), (9.5, 26, 29), SHELL, rot=("x", -22.5, (8, 14, 27.5))))
    e.append(box((7, 23, 19.5), (9, 26, 24.5), STING, rot=("x", 45, (8, 24.5, 23))))
    paw = box((2, 6, -6), (5, 12, -2), PAW)
    claw = box((1.75, 4.5, -7), (5.25, 6, -2), CLAW)
    e += [paw, mirror(paw), claw, mirror(claw)]
    hind = box((2.5, 6, 6), (5, 11, 9), PAW)
    e += [hind, mirror(hind)]
    return model("hellcraft:entity/guardian_geryon", e)


GUARDIANS = {
    "guardian_minos": minos,
    "guardian_cerberus": cerberus,
    "guardian_plutus": plutus,
    "guardian_minotaur": minotaur,
    "guardian_geryon": geryon,
    "guardian_vulcan": vulcan,
}


# ------------------------------------------------------------------------------------ recipes
# The results must match HellWeapons.create() in the 26.3 sources.

DATA = os.path.join(os.path.dirname(__file__), "..", "versions", "26.3", "src", "main", "resources", "data", "hellcraft")
# fabric:components (not fabric:custom_data) so the recipe book shows the real Blood Heart / Fragment: it
# displays the base item with these components applied. They must equal what BloodItems.heart()/fragment() set.
HEART = {"fabric:type": "fabric:components", "base": "minecraft:fermented_spider_eye", "components": {
    "minecraft:custom_data": {"hellcraft": "heart"},
    "minecraft:item_model": "hellcraft:blood_heart",
    "minecraft:item_name": {"text": "Blood Heart", "color": "dark_red", "bold": True},
}}
FRAGMENT = {"fabric:type": "fabric:components", "base": "minecraft:red_dye", "components": {
    "minecraft:custom_data": {"hellcraft": "fragment"},
    "minecraft:item_model": "hellcraft:blood_fragment",
    "minecraft:item_name": {"text": "Blood Fragment", "color": "red"},
}}
# the last two lore lines of every weapon (red)
WEAPON_FOOTER = ["Right-click at full blood: its Blood Art. Hits and kills fill it.",
                 "Blood Oath = sneak + right-click: costs 3❤ of health, lasts 30 s."]
WEAPONS = [
    ("bloodletter", "Bloodletter", "minecraft:iron_sword", "rare", [" F ", "FSF", " F "],
     ["Charged hits bleed your foe and heal you.",
      "Blood Art, Exsanguinate: lunge forward, cutting everything",
      "in your path (8 damage, deep bleeding, heals you).",
      "Blood Oath: +10 damage, and every hit heals 1❤."]),
    ("reaper_of_minos", "Reaper of Minos", "minecraft:diamond_hoe", "epic", ["FFF", "FSF", " F "],
     ["Charged hits cleave everything within 3 blocks for 4.",
      "Blood Art, Harvest: reap everything within 5 blocks",
      "for 12, slowing them and dragging them in.",
      "Blood Oath: every hit is a Harvest."]),
    ("tithe_axe", "Tithe Axe", "minecraft:diamond_axe", "epic", ["FFF", "FSF", " F "],
     ["The tithe: kills with it drop Blood Fragments twice as often.",
      "Blood Art, Blood Frenzy: Strength II, Speed II and Haste II",
      "for 15 s.",
      "Blood Oath: Strength III, Speed II, Resistance, hits heal 1❤."]),
]


# The results must match BloodArmour.create() in the 26.3 sources.
ARMOUR_LORE = [
    "Blood armour: 2 pieces heal you 5% of the damage you deal,",
    "4 pieces 10%, plus a Blood Rush when near death",
    "and Resistance under a Blood Oath.",
]
ARMOUR = [
    ("blood_helmet", "Blood Helm", "minecraft:diamond_helmet", "head", [" F ", "FAF", " F "]),
    ("blood_chestplate", "Blood Cuirass", "minecraft:diamond_chestplate", "chest", [" F ", "FAF", " F "]),
    ("blood_leggings", "Blood Greaves", "minecraft:diamond_leggings", "legs", [" F ", "FAF", " F "]),
    ("blood_boots", "Blood Sabatons", "minecraft:diamond_boots", "feet", [" F ", "FAF", " F "]),
]


def armour_recipes():
    for aid, title, base, slot, pattern in ARMOUR:
        components = {
            "minecraft:custom_data": {"hellcraft": "armour", "armour": aid},
            "minecraft:item_model": "hellcraft:" + aid,
            "minecraft:item_name": {"text": title, "color": "dark_red", "bold": True},
            "minecraft:lore": [{"text": line, "color": "gray", "italic": False} for line in ARMOUR_LORE],
            "minecraft:rarity": "epic",
            "minecraft:equippable": {"slot": slot, "equip_sound": "minecraft:item.armor.equip_diamond", "asset_id": "hellcraft:blood"},
        }
        write(os.path.join(DATA, "recipe", aid + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment",
            "key": {"A": base, "F": FRAGMENT}, "pattern": pattern,
            "result": {"id": base, "components": components}})
    # how the armour looks when worn (textures/entity/equipment/<layer>/blood.png)
    write(os.path.join(ASSETS, "equipment", "blood.json"), {"layers": {
        "humanoid": [{"texture": "hellcraft:blood"}],
        "humanoid_leggings": [{"texture": "hellcraft:blood"}],
    }})


def recipes():
    for wid, title, base, rarity, pattern, lore in WEAPONS:
        components = {
            "minecraft:custom_data": {"hellcraft": "weapon", "weapon": wid},
            "minecraft:item_model": "hellcraft:" + wid,
            "minecraft:item_name": {"text": title, "color": "dark_red", "bold": True},
            "minecraft:lore": [{"text": line, "color": "gray", "italic": False} for line in lore]
                              + [{"text": line, "color": "red", "italic": False} for line in WEAPON_FOOTER],
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


# The results must match BloodItems.vigil() / anchor() in the 26.3 sources. The base is a poisonous potato
# with its food removed, so the item is inert and no vanilla recipe takes it.
INERT = {"!minecraft:food": {}, "!minecraft:consumable": {}}
VIGIL = dict(INERT, **{
    "minecraft:custom_data": {"hellcraft": "vigil"},
    "minecraft:item_model": "hellcraft:vigil_candle",
    "minecraft:item_name": {"text": "Vigil Candle", "color": "aqua"},
    "minecraft:lore": [{"text": "Right-click to light it where you stand.", "color": "gray", "italic": False},
                       {"text": "Your next death wakes you beside it, once.", "color": "gray", "italic": False}],
    "minecraft:rarity": "uncommon",
})
ANCHOR = dict(INERT, **{
    "minecraft:custom_data": {"hellcraft": "anchor"},
    "minecraft:item_model": "hellcraft:soul_anchor",
    "minecraft:item_name": {"text": "Soul Anchor", "color": "aqua", "bold": True},
    "minecraft:lore": [{"text": "Carry it. If you die, you rise again", "color": "gray", "italic": False},
                       {"text": "where you fell, and it breaks.", "color": "gray", "italic": False}],
    "minecraft:rarity": "rare",
    "minecraft:enchantment_glint_override": True,
})


# Paradiso's relics; must match Relics.java
WINGS = {
    "minecraft:custom_data": {"hellcraft": "relic", "relic": "seraph_wings"},
    "minecraft:item_model": "hellcraft:seraph_wings",
    "minecraft:item_name": {"text": "Seraph Wings", "color": "aqua", "bold": True},
    "minecraft:lore": [{"text": "Never wear out. While gliding, sneak for a", "color": "gray", "italic": False},
                       {"text": "rush of the Primum Mobile's wind (every 10 s).", "color": "gray", "italic": False}],
    "minecraft:rarity": "epic",
    "minecraft:unbreakable": {},
}


def item_recipes():
    write(os.path.join(DATA, "recipe", "vigil_candle.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:torch", "minecraft:bone", "minecraft:string"],
        "result": {"id": "minecraft:poisonous_potato", "components": VIGIL}})


def main():
    recipes()
    item_recipes()
    armour_recipes()
    for name, parent in FLAT_ITEMS.items():
        item_definition(name)
        write(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": parent, "textures": {"layer0": "hellcraft:item/" + name}})
    for name, build in [("lucifer_morning_star", morning_star), ("lucifer_emperor", emperor)] + list(GUARDIANS.items()):
        item_definition(name)
        write(os.path.join(ASSETS, "models", "item", name + ".json"), build())
    # every texture a model names must exist
    for root, _, files in os.walk(os.path.join(ASSETS, "models")):
        for f in files:
            m = json.load(open(os.path.join(root, f)))
            for tex in m.get("textures", {}).values():
                ns, path = tex.split(":")
                assert os.path.exists(os.path.join(ASSETS, "textures", path + ".png")), (f, tex)
    print("Wrote %d item definitions." % (len(FLAT_ITEMS) + 2 + len(GUARDIANS)))


if __name__ == "__main__":
    main()
