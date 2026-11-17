"""Where a boss can be hit beyond its main box: the parts of its body that tools/gen_bosses.py turns into
entity/boss/gen/<Boss>Shape.java (BossShape), which the game follows with invisible part entities (entity/boss/BossPart).

A boss's main box (HellEntities: one upright box, the size the game gives every entity) can't follow a long body: Cerberus's
three heads reach six blocks past his, Geryon's tail trails seven blocks behind him. Each part named here is a box that
goes where its bone goes, in every animation, so a blow (or an arrow) that lands on the heads lands on the boss.

For each model:
    "scale": the size the renderer draws the model at (keep in step with its renderer: CerberusRenderer.SCALE, the
             AnimatedBossRenderer scale in HellcraftClient, ...);
    "parts": [(name, group, options), ...]: a box on the group's own cubes (with its children's when "children" is True),
             centred where they are and as big as they are at rest, unless "size" gives (width, height) in blocks. The box
             is upright and square (the game's boxes don't turn), as wide as the cubes' mean width across X and Z.
             "gaze": how far the part turns with the boss's head (the renderer's gaze factor for that bone), about the
             origin of the group named "pivot".

Hitting a part is hitting the boss: same damage, no extra. Parts overlap the main box freely; a swing strikes a boss once
however many of its parts it passes through.
"""

HITBOXES = {
    # the Great Worm: three heads on long necks, and a tail (main box 4.2 x 4.4 covers the body and legs)
    "cerberus": {
        "scale": 0.75,
        "parts": [
            ("head_mid", "head_mid", {"children": True, "gaze": 0.4, "pivot": "neck_mid"}),
            ("head_right", "head_right", {"children": True, "gaze": 0.4, "pivot": "neck_right"}),
            ("head_left", "head_left", {"children": True, "gaze": 0.4, "pivot": "neck_left"}),
            ("neck_mid", "neck2_mid", {"size": (1.4, 1.6), "gaze": 0.4, "pivot": "neck_mid"}),
            ("neck_right", "neck2_right", {"size": (1.6, 1.6), "gaze": 0.4, "pivot": "neck_right"}),
            ("neck_left", "neck2_left", {"size": (1.6, 1.6), "gaze": 0.4, "pivot": "neck_left"}),
            ("tail_near", "tail_3", {"size": (1.1, 1.1)}),
            ("tail_far", "tail_5", {"size": (1.1, 1.1)}),
        ],
    },
    # the Image of Fraud: a man's face on a serpent's body, the sting at the end (main box 3 x 3 round his chest)
    "geryon": {
        "scale": 2.0,
        "parts": [
            ("head", "head", {"size": (1.6, 2.0), "gaze": 0.4, "pivot": "neck"}),
            ("body", "seg2", {"size": (1.6, 1.6)}),
            ("coils", "seg3", {"children": True, "size": (1.6, 1.6)}),
            ("tail", "seg4", {"size": (1.4, 1.5)}),
            ("sting", "tail", {"children": True, "size": (1.4, 1.8)}),
        ],
    },
    # the judge: his serpent's tail lies coiled out across the floor (main box 3 x 6.4 is the man)
    "minos": {
        "scale": 0.675,
        "parts": [
            ("tail_near", "tail_3", {"size": (1.4, 1.0)}),
            ("tail_mid", "tail_5", {"size": (1.4, 1.0)}),
            ("tail_far", "tail_7", {"size": (1.4, 1.0)}),
        ],
    },
}
