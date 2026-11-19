"""Hellcraft's own sounds and music, made with ElevenLabs (sound effects v3, music v2.5).

This file is the list: every sound event the mod plays instead of a vanilla one, with the prompt that made it.
`tools/audio/build_sounds.py` turns the downloaded takes (tools/audio/cache/<event>/<n>.mp3, not in git) into
assets/hellcraft/sounds/<path>.ogg, writes their entries in assets/hellcraft/sounds.json and their subtitles in
the lang file, and writes the Java constants (`net.thesmallthings.hellcraft.sound.HellSounds`).

SFX: name -> (prompt, seconds, takes, subtitle[, loop]). A name "a.b_c" becomes the sound event hellcraft:a.b_c,
the Java constant A_B_C and the files sounds/sfx/a/b_c_<n>.ogg. Takes are variants the game picks between at random.
MUSIC: name -> (prompt, seconds). Music streams from sounds/music/<name>.ogg.

Prompts follow the model guide: say what the sound is, its texture and space, no visual words.
"""

SFX = {
    # ------------------------------------------------------------------ the player's combat
    "combat.guard_block": ("Sword blade blocking a heavy blow, sharp steel-on-steel clash with a short metallic scrape", 1.2, 2, "Blow guarded"),
    "combat.parry_clash": ("Bright ringing steel parry, two swords striking, high clear metallic ring", 1.5, 1, "Blow parried"),
    "combat.guard_break": ("Weapon knocked aside by a crushing blow, dull heavy clang and a crunch of metal, a grunt of strain", 1.5, 1, "Guard broken"),
    "combat.swing_light": ("Fast sword swing whoosh through the air, short and sharp", 0.6, 3, "Weapon swings"),
    "combat.swing_heavy": ("Heavy two-handed weapon swung hard, deep powerful whoosh through air", 0.9, 2, "Heavy swing"),
    "combat.shockwave": ("Heavy weapon slamming into the ground, deep earth-shaking thump with rubble scattering", 1.6, 1, "Ground slam"),
    "combat.roll": ("Quick dodge roll on dirt, cloth rustle and leather armour shuffle with a fast whoosh", 0.8, 2, "Dodge roll"),

    # ------------------------------------------------------------------ blood, altars, items
    "blood.altar_charge": ("Dark altar charging with power, low rising throb and wet blood bubbling, ominous hum", 1.8, 1, "Altar charges"),
    "blood.altar_bind": ("Deep resonant thrum like a giant slow heartbeat with a low choir hum, a soul bound to a shrine", 2.0, 1, "Soul bound"),
    "blood.altar_offer": ("Blood ritual surge, a whoosh of rushing liquid and a deep magical boom", 2.0, 1, "Offering accepted"),
    "blood.drink": ("Gulping a thick viscous liquid from a flask, two slow wet swallows", 1.2, 1, "Blood drunk"),
    "blood.heart_surge": ("Powerful single heartbeat pulse with a dark magical swell, energy rushing in", 1.5, 1, "Heart surges"),
    "blood.squelch": ("Wet fleshy squelch, short and visceral", 0.7, 1, "Flesh squelches"),
    "blood.candle": ("Striking a match and a candle wick catching flame, soft crackle", 1.2, 1, "Candle lit"),
    "blood.revive": ("A gasp of breath returning, airy shimmering whoosh rising upward with a soft deep thrum", 2.0, 1, "Soul returns"),
    "blood.prestige": ("Solemn deep bell struck once with a dark choir chord swelling, ceremonial", 3.0, 1, "Prestige rises"),
    "blood.forge": ("Blacksmith hammer striking hot metal on an anvil twice, then a hiss of quenching steam", 2.0, 1, "Hellforge rings"),
    "weapon.art_ready": ("Dark blade humming with charged energy, a rising metallic shimmer", 1.2, 1, "Blood Art ready"),
    "weapon.art_unleash": ("Demonic power unleashed, deep roaring burst of dark energy with a distorted whoosh", 2.0, 1, "Blood Art unleashed"),
    "weapon.reap": ("Scythe sweeping wide through the air, long curved whoosh with a thin metallic whistle", 1.0, 1, "Scythe reaps"),
    "weapon.howl": ("Beastly war howl echoing from a cursed weapon, growling roar", 1.8, 1, "Weapon howls"),
    "weapon.shatter": ("Heavy stone cracking and shattering under a blow, crunchy debris", 1.2, 1, "Stone shatters"),
    "weapon.cleave": ("Great axe cleaving through the air in a wide arc, heavy deep whoosh", 1.0, 1, "Axe cleaves"),
    "armour.blood": ("Putting on heavy plate armour, metal pieces clinking with a wet leather creak", 1.0, 1, "Blood armour worn"),
    "armour.infernal": ("Putting on blackened heavy steel armour, deep metallic clank and a faint hiss of heat", 1.0, 1, "Infernal armour worn"),
    "armour.halo": ("Soft heavenly chime with a glassy shimmer, a halo settling", 1.2, 1, "Halo worn"),
    "armour.wings": ("Large feathered wings unfolding with a rustle of feathers", 1.2, 1, "Wings worn"),
    "travel.depart": ("Whooshing into dark swirling smoke, a deep rushing vortex fading out", 1.8, 1, "Travel begins"),
    "travel.arrive": ("Emerging from a rushing vortex, whoosh resolving into a low thrum", 1.6, 1, "Travel ends"),
    "relic.halo": ("Clear heavenly chime ringing out with a warm shimmering tail", 1.8, 1, "Halo shines"),
    "relic.wings": ("Large feathered wings beating once powerfully, a deep whoomp of air", 1.2, 1, "Wings beat"),
    "ghost.wail": ("Ghostly whispering wail, breathy and distant, unsettling", 2.0, 1, "Ghost wails"),

    # ------------------------------------------------------------------ notices
    "ui.triumph": ("Short triumphant dark orchestral sting, brass and deep choir chord, victorious", 3.0, 1, "Triumph"),
    "ui.lesson": ("Turning an old parchment book page, dry papery rustle", 0.8, 1, "Page turns"),
    "ui.pilgrimage": ("Single deep church bell toll in the distance, long reverberating hum", 3.0, 1, "Bell tolls"),
    "ui.bounty": ("Ominous low growl and a sinister whisper, a hunter marked", 2.0, 1, "A bounty is set"),

    # ------------------------------------------------------------------ the world
    "world.lift": ("Rising wind of souls, airy whooshing updraft with faint ghostly voices", 2.0, 1, "Lifted by souls"),
    "world.lift_land": ("Soft landing on stone after floating down, gentle thud with a dissipating whoosh", 1.0, 1, "Set down"),
    "world.thread": ("Thread unspooling quickly from a wooden spool, soft rapid whirr", 1.0, 1, "Thread unwinds"),
    "world.stone_grind": ("Heavy stone door grinding open, deep scraping rumble", 2.0, 1, "Stone grinds"),
    "world.ferry_bell": ("Old ship bell ringing twice across water, distant and echoing", 2.5, 1, "Ferry bell"),
    "world.light_column": ("Column of light lifting someone upward, bright shimmering whoosh with a soft choir tone", 2.0, 1, "Light lifts you"),
    "world.sphere_open": ("Crystal resonance ringing in a vast hall, heavenly glassy hum", 2.5, 1, "Sphere opens"),
    "world.chime": ("Single clear crystal bell chime, pure tone", 1.5, 1, "Chime"),
    "world.terrace": ("Uplifting soft choir swell, a sense of relief and ascent", 3.0, 1, "Terrace climbed"),
    "spine.heartbeat": ("Deep slow monstrous heartbeat, low muffled double thump", 1.5, 1, "Heartbeat"),
    "spine.whisper": ("Unintelligible sinister whispering voices, close and breathy", 2.5, 2, "Whispers"),
    "shade.sigh": ("Ghostly soft sigh of a weary spirit, airy and hollow", 1.5, 2, "Shade sighs"),
    "shade.trade": ("Ghostly gentle hum of approval with a soft chime", 1.2, 1, "Shade trades"),
    "ferry.creak": ("Old wooden boat hull creaking", 1.2, 1, "Ferry creaks"),
    "ferry.dock": ("Wooden boat bumping against a wooden dock, hollow knock", 1.0, 1, "Ferry docks"),
    "ferry.paddle": ("Oar paddle stroking through dark water, slow splash", 1.2, 1, "Oar strokes"),
    "slag.hiss": ("Hot molten rock hissing as it cools, steam sizzle", 1.0, 1, "Slag hisses"),
    "slag.pop": ("Molten lava bubble popping, thick wet blorp", 0.7, 1, "Slag pops"),
    "slag.crack": ("Cooled rock crust cracking under weight, crunchy stone crack", 0.8, 1, "Crust cracks"),
    "hazard.flare": ("Sudden burst of flame whooshing up, fiery roar", 1.2, 1, "Flames burst"),
    "rules.tomb_rumble": ("Low rumble of fire building under stone, crackling and roaring deep below", 2.0, 1, "Tomb rumbles"),
    "rules.tomb_blast": ("Fire erupting from an opened stone tomb, violent fiery whoosh", 1.5, 1, "Tomb erupts"),
    "rules.thorns": ("Thorny branches snapping and tearing, woody crackle", 1.0, 1, "Thorns snap"),
    "rules.tree_scream": ("Tormented human scream coming from inside a creaking tree, anguished and hollow", 2.0, 1, "A tree screams"),
    "rules.sand_breath": ("Hot desert wind rising with a deep roaring breath of heat", 2.5, 1, "Fire wind rises"),
    "rules.illusion": ("Eerie magical illusion shimmering, warbling ghostly tones", 1.8, 1, "An illusion forms"),
    "rules.coins": ("Handful of gold coins clinking and jingling", 1.0, 1, "Gold clinks"),
    "rules.steal": ("Quick snatch of an item, fabric swipe and coin rattle", 0.7, 1, "Something stolen"),
    "rules.mud": ("Foot sucking into thick mud, wet squelch", 0.8, 1, "Mud squelches"),
    "rules.sullen": ("Gurgling voices bubbling up from under swamp water, drowned muttering", 2.0, 1, "The sullen gurgle"),
    "champion.taunt": ("Monstrous low taunting growl", 1.5, 1, "Champion growls"),
    "champion.blink": ("Short dark teleport whoosh with a pop", 0.8, 1, "Champion blinks"),
    "champion.clang": ("Metal armour shrugging off a hit, dull clank", 0.6, 1, "Champion's armour"),
    "champion.arrive": ("Dark warhorn blast with a monstrous roar, a champion arrives", 2.5, 1, "A champion arrives"),

    # ------------------------------------------------------------------ circle moods (Ambience, one-shots)
    "mood.vestibule": ("Swarm of angry wasps and hornets buzzing around", 2.5, 1, "Insects swarm"),
    "mood.acheron": ("Slow oar dipping in still dark river water", 2.0, 1, "Oars dip"),
    "mood.limbo": ("Faint sigh of many distant souls, airy and sorrowful", 3.0, 1, "Souls sigh"),
    "mood.lust": ("Strong gust of howling wind passing by", 3.0, 1, "Wind howls"),
    "mood.gluttony": ("Cold heavy rain and sleet pattering on mud", 3.0, 1, "Sleet falls"),
    "mood.greed": ("Heavy gold coins and metal weights clattering as they roll", 2.0, 1, "Gold clatters"),
    "mood.styx": ("Bubbles rising and popping from a dark marsh", 2.5, 1, "Marsh bubbles"),
    "mood.heresy": ("Fire crackling and roaring inside a stone coffin", 3.0, 1, "Tombs burn"),
    "mood.phlegethon": ("Thick boiling liquid bubbling and popping", 2.5, 1, "Blood boils"),
    "mood.malebolge": ("Distant agonised moans echoing in a deep stone pit", 3.0, 1, "Distant moans"),
    "mood.cocytus": ("Thick ice creaking and groaning under strain", 2.5, 1, "Ice groans"),

    # ------------------------------------------------------------------ soundscape beds and details (client layer/Soundscape)
    "ambient.vestibule.bed": ("Restless droning crowd of wailing voices far away with insect buzz, dark ambience", 20, 1, None, True),
    "ambient.limbo.bed": ("Still grey hollow wind with faint sighing voices, melancholy ambience", 20, 1, None, True),
    "ambient.lust.bed": ("Endless howling storm wind, constant gusting gale", 20, 1, None, True),
    "ambient.gluttony.bed": ("Constant cold heavy rain falling on mud and puddles", 20, 1, None, True),
    "ambient.styx.bed": ("Muffled murky water ambience heard from just below the surface, low bubbling drone", 20, 1, None, True),
    "ambient.cocytus.bed": ("Freezing wind blowing over a vast frozen lake, thin icy whistle", 20, 1, None, True),
    "ambient.judecca.bed": ("Deep icy gale roaring slowly like vast wings beating far away, frozen ambience", 20, 1, None, True),
    "ambient.vestibule.detail": ("A crowd crying out in confusion far away, then fading", 3.0, 2, None),
    "ambient.limbo.detail": ("Single faint sorrowful sigh carried on wind", 3.0, 2, None),
    "ambient.lust.detail": ("Wind carrying a fading lover's moan, swirling past", 3.0, 2, None),
    "ambient.gluttony.detail": ("Something wallowing and slurping in deep mud", 2.5, 2, None),
    "ambient.greed.detail": ("Heavy stone weight scraping and grinding as it is pushed", 2.5, 2, None),
    "ambient.styx.detail": ("Large bubble rising and bursting from a swamp, wet gloop", 2.0, 2, None),
    "ambient.heresy.detail": ("Tomb lid rattling as flames roar beneath it", 2.5, 2, None),
    "ambient.phlegethon.detail": ("Boiling river surging and hissing", 2.5, 2, None),
    "ambient.suicides.detail": ("Branches creaking and a faint pained groan from a tree", 3.0, 2, None),
    "ambient.sands.detail": ("Flakes of fire falling and hissing on hot sand", 2.5, 2, None),
    "ambient.malebolge.detail": ("Whip crack and a pained cry echoing in a stone ditch", 2.5, 2, None),
    "ambient.pitch.detail": ("Thick boiling pitch bubbling and stretching", 2.5, 2, None),
    "ambient.giants.detail": ("Giant chains straining and clanking far away, huge slow groan", 3.0, 2, None),
    "ambient.cocytus.detail": ("Ice cracking sharply across a frozen lake, echoing", 2.0, 2, None),
    "ambient.judecca.detail": ("Deep slow wingbeat far away, a vast whoomp under ice", 2.5, 2, None),
    "ambient.forge.detail": ("Distant giant hammer striking an anvil in a cavern, echoing clang", 2.5, 2, None),
    "ambient.paradiso.detail": ("Soft heavenly choir note with crystal shimmer", 3.0, 2, None),

    # ------------------------------------------------------------------ biome ambience (worldgen ambient_sounds)
    "ambient.hell.mood": ("Deep unsettling low rumble and distant groan in a cavern", 5.0, 2, None),
    "ambient.ember.loop": ("Dark cavern ambience with low rumbling and distant fire roar", 20, 1, None, True),
    "ambient.ember.additions": ("Embers crackling and a distant rock falling", 2.5, 2, None),
    "ambient.tombs.loop": ("Hollow wind among stone tombs with low burning crackle, eerie ambience", 20, 1, None, True),
    "ambient.tombs.additions": ("Faint wail from a burning tomb", 2.5, 2, None),
    "ambient.blood.loop": ("Thick river of boiling liquid flowing and bubbling, low roar", 20, 1, None, True),
    "ambient.blood.additions": ("Thick bubble popping in boiling liquid", 1.5, 2, None),
    "ambient.thicket.loop": ("Creaking dead wood forest in a dry wind, eerie ambience", 20, 1, None, True),
    "ambient.thicket.additions": ("Twisted branch creaking with a soft groan", 2.5, 2, None),
    "ambient.ash.loop": ("Hot dry wind over ash and stone, low rumbling, desolate ambience", 20, 1, None, True),
    "ambient.ash.additions": ("Ash and gravel sliding down a slope", 2.0, 2, None),
    "ambient.forge.loop": ("Vast underground forge, deep roaring furnaces, rumbling machinery and distant hammering", 20, 1, None, True),
    "ambient.forge.additions": ("Molten metal pouring and hissing far away", 2.5, 2, None),

    # ------------------------------------------------------------------ the damned
    "entity.damned.scream": ("Anguished human scream of a tormented soul, echoing", 1.8, 3, "Damned soul screams"),
    "entity.damned.sigh": ("Weary despairing moan of a tormented soul", 2.0, 3, "Damned soul moans"),
    "damned.lash": ("Sharp whip lash crack on skin", 0.6, 1, "Lash"),
    "damned.weight": ("Heavy stone weight dropped on ground, dull thud", 0.8, 1, "Weight drops"),
    "damned.splash": ("Body sloshing in thick liquid, small splash", 0.8, 1, "Splash"),

    # ------------------------------------------------------------------ guardians, shared
    "boss.awaken": ("Monstrous awakening, deep demonic rumble with a dark choir burst and a shockwave", 3.0, 1, "A guardian wakes"),
    "boss.warn": ("Ominous dark magical charge, rising sinister hum", 1.5, 1, "A warning"),
    "boss.pull": ("Magical chain yanking someone, whip-like whoosh and rattle", 1.0, 1, "Pulled in"),
    "boss.banish": ("Dark teleport, deep whoosh and pop", 1.0, 1, "Thrown out"),
    "boss.boom": ("Massive impact explosion, deep boom with rubble", 2.0, 2, "Impact"),
    "boss.fall": ("Giant creature collapsing to the ground, huge heavy thud with a final groan", 2.5, 1, "A guardian falls"),

    # Minos
    "minos.rise": ("Huge creature rising from the ground, stone grinding and earth rumbling", 2.5, 1, "Minos rises"),
    "minos.roar": ("Deep monstrous roar of an ancient giant judge, booming and reverberating", 2.5, 2, "Minos roars"),
    "minos.swipe": ("Giant claw swiping through the air, heavy whoosh", 1.0, 1, "Minos swipes"),
    "minos.tail_sweep": ("Huge serpent tail whipping around in a wide arc, scaly swoosh", 1.2, 1, "Tail sweeps"),
    "minos.lunge": ("Monster lunging and snapping jaws shut, sharp bite", 1.0, 1, "Minos lunges"),
    "minos.judgement": ("Single low ominous bell strike, solemn and heavy", 2.0, 1, "Judgement tolls"),
    # Cerberus
    "cerberus.emerge": ("Giant beast clawing out of mud, wet heavy squelching and a rumble", 2.0, 1, "Cerberus emerges"),
    "cerberus.roar": ("Three giant dogs roaring together, monstrous layered snarling roar", 2.5, 2, "Cerberus roars"),
    "cerberus.growl": ("Huge dog growling deeply, menacing rumble", 2.0, 1, "Cerberus growls"),
    "cerberus.howl": ("Giant hellhound howling, deep and echoing", 3.0, 1, "Cerberus howls"),
    "cerberus.hurt": ("Huge dog yelping in pain, deep", 1.0, 1, "Cerberus hurts"),
    "cerberus.bite": ("Huge jaws snapping shut, wet crunching bite", 0.8, 2, "Cerberus bites"),
    "cerberus.spit": ("Beast spitting a wad of mud, wet splat", 1.0, 1, "Mud spat"),
    "cerberus.mud": ("Mud splattering on the ground, thick wet splash", 1.0, 1, "Mud splatters"),
    "cerberus.stomp": ("Giant paw stomping heavily on wet ground", 1.0, 1, "Cerberus stomps"),
    # Plutus
    "plutus.coins": ("Huge pile of gold coins cascading and jingling", 2.0, 1, "Gold cascades"),
    "plutus.roar": ("Bestial greedy roar, guttural and booming with a metallic edge", 2.5, 1, "Plutus roars"),
    "plutus.slam": ("Giant fist slamming onto gold coins, heavy crash with coins scattering", 1.5, 2, "Plutus slams"),
    "plutus.coin_roll": ("Giant metal coin rolling and rumbling across stone", 1.5, 1, "Coin rolls"),
    # Phlegyas
    "phlegyas.intro": ("Ominous low horn blast echoing across dark water", 3.0, 1, "A horn sounds"),
    "phlegyas.roar": ("Furious boatman's roar, deep angry bellow over water", 2.5, 1, "Phlegyas roars"),
    "phlegyas.oar_strike": ("Heavy wooden oar striking hard, cracking thump", 1.0, 1, "Oar strikes"),
    "phlegyas.oar_sweep": ("Oar sweeping through water fast, big splashing whoosh", 1.2, 1, "Oar sweeps"),
    "phlegyas.splash": ("Huge splash of something heavy slamming into water", 1.5, 1, "Huge splash"),
    "phlegyas.shove": ("Heavy shove knocking someone back, thump and grunt", 0.8, 1, "Shoved"),
    # Furies and Medusa
    "furies.scream": ("Shrill furious shriek of a winged demon woman, piercing", 2.0, 2, "A Fury shrieks"),
    "furies.bite": ("Vicious snapping bite with a hiss", 0.7, 1, "A Fury bites"),
    "furies.dash": ("Winged creature diving fast, rushing whoosh", 1.0, 1, "A Fury dives"),
    "furies.land": ("Winged creature landing heavily, thud and wing rustle", 1.0, 1, "A Fury lands"),
    "furies.hurt": ("Winged demon woman crying out in pain, harsh shriek", 1.0, 1, "A Fury hurts"),
    "medusa.gaze": ("Petrifying magical gaze, deep ominous drone with stone grinding", 2.0, 1, "Medusa's gaze"),
    "medusa.hiss": ("Many snakes hissing together", 2.0, 1, "Snakes hiss"),
    "medusa.petrify": ("Flesh turning to stone, crackling stony crunch", 1.2, 1, "Turned to stone"),
    # Farinata
    "farinata.flame": ("Fireball launched with a deep whoosh of flame", 1.2, 2, "Flames cast"),
    "farinata.roar": ("Proud defiant roar from a burning tomb, fiery and echoing", 2.5, 1, "Farinata roars"),
    # Minotaur
    "minotaur.roar": ("Enraged bull-man bellowing roar, huge and guttural", 2.5, 2, "The Minotaur roars"),
    "minotaur.snort": ("Angry bull snorting heavily before a charge", 1.2, 1, "The Minotaur snorts"),
    "minotaur.gore": ("Horns goring, heavy thrust and crunch", 1.0, 1, "The Minotaur gores"),
    "minotaur.crash": ("Charging beast crashing into a stone wall, huge impact and rubble", 1.8, 1, "The Minotaur crashes"),
    "minotaur.spin": ("Giant axe spun around in circles, repeated heavy whooshes", 1.5, 1, "The Minotaur spins"),
    "minotaur.stomp": ("Hooves stamping heavily on stone, echoing thud", 1.0, 1, "The Minotaur stamps"),
    # Geryon
    "geryon.roar": ("Monstrous serpent dragon screech roar, layered and echoing", 2.5, 1, "Geryon roars"),
    "geryon.sting": ("Scorpion tail stinger stabbing, sharp wet thrust", 0.8, 1, "Geryon stings"),
    "geryon.illusion": ("Deceptive shimmering illusion, warbling magical tone", 1.5, 1, "Geryon deceives"),
    "geryon.hurt": ("Huge serpent monster hissing in pain", 1.2, 1, "Geryon hurts"),
    "geryon.swoop": ("Giant winged monster swooping past overhead, deep rushing whoosh", 1.5, 1, "Geryon swoops"),
    "geryon.flap": ("Giant leathery wings flapping once, deep whoomp", 1.0, 1, "Wings flap"),
    "geryon.mount": ("Climbing onto a creature's back, leather harness creak", 1.0, 1, "Mounted"),
    # Vulcan and the Cyclops
    "vulcan.hammer": ("Giant hammer striking an anvil, ringing heavy clang", 1.5, 1, "Vulcan hammers"),
    "vulcan.roar": ("Massive fire giant roaring, deep and crackling with flame", 2.5, 1, "Vulcan roars"),
    "vulcan.slam": ("Giant smith's hammer slamming the ground, explosive metal clang and boom", 1.8, 1, "Vulcan slams"),
    "vulcan.lava": ("Lava erupting and bubbling violently", 2.0, 1, "Lava surges"),
    "vulcan.chain": ("Heavy iron chain snapping and rattling", 1.2, 1, "Chain snaps"),
    "cyclops.roar": ("One-eyed giant roaring, deep primal bellow", 2.5, 1, "The Cyclops roars"),
    "cyclops.slam": ("Giant club smashing the ground, heavy crash", 1.5, 1, "The Cyclops slams"),
    "cyclops.throw": ("Giant hurling a boulder, heavy grunt and whoosh", 1.2, 1, "The Cyclops throws"),
    # Antaeus
    "antaeus.groan": ("Enormous giant groaning slowly, deep rumbling voice", 2.5, 1, "Antaeus groans"),
    "antaeus.set_down": ("Giant hand setting something down on stone, heavy soft thud", 1.0, 1, "Set down"),
    # the Seraph (Paradiso)
    "seraph.radiance": ("Radiant holy energy blooming, bright shimmering choir burst", 2.5, 1, "The Seraph shines"),
    "seraph.cry": ("Angelic high piercing cry, choral and echoing", 2.5, 1, "The Seraph cries"),
    "seraph.wings": ("Six great feathered wings beating together, powerful rush of air", 1.5, 1, "Wings beat"),
    "seraph.call": ("Holy light beam called down from above, rising whoosh and shimmer", 1.5, 1, "Light called"),
    "seraph.flame": ("Holy fire igniting, bright roaring whoosh", 1.2, 1, "Holy fire"),
    "seraph.burst": ("Holy light exploding, bright bursting boom with shimmer", 1.5, 1, "Light bursts"),
    "seraph.unveil": ("Overwhelming divine presence revealed, rising choir and deep swell", 2.5, 1, "The Seraph unveils"),
    "seraph.blaze": ("Blinding holy blaze, searing whoosh of light and fire", 2.0, 1, "Holy blaze"),
    "seraph.summon": ("Angelic summoning, bright chime cascade", 1.5, 1, "Angels called"),
    # the angels
    "angel.descend": ("Angel descending, bright crystal resonance and soft wings", 2.0, 1, "An angel descends"),
    "angel.ambient": ("Soft angelic humming, ethereal and calm", 2.0, 2, "Angel hums"),
    "angel.hurt": ("Ethereal angelic gasp, glassy", 0.8, 1, "Angel hurts"),
    "angel.death": ("Angelic voice fading into shimmering light", 1.8, 1, "Angel fades"),

    # ------------------------------------------------------------------ Lucifer
    "lucifer.shout": ("Enormous demonic voice booming, deep distorted roar", 2.0, 1, "Lucifer roars"),
    "lucifer.murmur": ("Deep demonic murmur, low growling whisper echoing in ice", 2.0, 1, "Lucifer murmurs"),
    "lucifer.toll": ("Huge ominous bell tolling once, deep resonant hum", 3.0, 1, "A bell tolls"),
    "lucifer.curse": ("Dark curse cast, deep distorted drone with a sinister hiss", 2.0, 1, "A curse"),
    "fallen.rise": ("Fallen angel rising, dark choir burst and a rush of black wings", 3.0, 1, "The Fallen Seraph rises"),
    "fallen.slash": ("Blade slash of light through the air, sharp shimmering whoosh", 0.8, 2, "A blade of light"),
    "fallen.wings": ("Black wings sweeping hard, heavy rush of air", 1.2, 1, "Wings sweep"),
    "fallen.dash": ("Lightning fast dash, sharp rushing whoosh", 0.8, 1, "The Seraph dashes"),
    "fallen.star": ("Falling star impact, bright crackling explosion", 1.5, 2, "A star falls"),
    "fallen.shatter": ("Crystal shattering into fragments, bright glassy crash", 1.2, 1, "Crystal shatters"),
    "fallen.blink": ("Short teleport, bright shimmer and pop", 0.8, 1, "The Seraph blinks"),
    "fallen.parried": ("Sword parried by a glowing blade, heavy ringing clash", 1.2, 1, "Blade turned"),
    "fallen.wings_break": ("Wings of light shattering like glass, huge glassy crash", 2.0, 1, "Wings shatter"),
    "emperor.roar": ("Colossal three-faced demon roaring from under ice, deafening layered roar", 3.5, 1, "The Emperor roars"),
    "emperor.ice_break": ("Huge sheet of ice breaking apart, deep cracking crash", 2.5, 1, "Ice breaks"),
    "emperor.slam": ("Giant fist slamming into ice, massive boom and cracking ice", 2.0, 2, "The Emperor slams"),
    "emperor.drag": ("Giant hand dragging across ice, deep grinding scrape", 2.0, 1, "A hand drags"),
    "emperor.inhale": ("Colossal creature inhaling deeply, huge rushing breath in", 2.0, 1, "The Emperor inhales"),
    "emperor.breath": ("Freezing breath blasted out, roaring icy wind", 2.5, 1, "Frozen breath"),
    "emperor.icicle": ("Icicle shattering on the ground, bright icy crash", 1.0, 2, "Ice shatters"),
    "emperor.spikes": ("Ice spikes erupting from the ground, sharp cracking", 1.2, 1, "Ice spikes"),
    "emperor.swat_parried": ("Giant claw knocked aside, heavy deflecting clang", 1.2, 1, "Swat turned"),
    "emperor.wingbeat": ("Colossal bat wings beating once, enormous deep whoomp of wind", 2.0, 1, "Wings beat"),
    "wingbeat.flap": ("Colossal wings beating far away, deep slow whoomp", 2.0, 1, None),
    "wingbeat.rumble": ("Distant deep rumble like thunder under ice", 2.5, 1, None),
    "impact.boom": ("Heavy ground impact, deep boom with dust", 1.2, 1, None),
    "impact.smash": ("Crushing ground smash, heavy crunch of stone", 1.0, 1, None),
    "impact.burst": ("Burst of air from an impact, sharp windy blast", 0.8, 1, None),

    # ------------------------------------------------------------------ Hell's monsters
    "glutton.emerge": ("Bloated creature heaving out of thick mud, wet sucking", 1.5, 1, "Glutton emerges"),
    "glutton.squelch": ("Fat creature squelching, wet slap", 0.8, 1, "Glutton squelches"),
    "glutton.ambient": ("Bloated monster grunting and gurgling, wet piggish snort", 1.5, 2, "Glutton grunts"),
    "glutton.hurt": ("Bloated monster squealing in pain, wet", 0.8, 1, "Glutton hurts"),
    "glutton.death": ("Bloated monster dying, gurgling squeal and wet collapse", 1.5, 1, "Glutton dies"),
    "mimic.rattle": ("Wooden chest lid rattling slightly", 0.6, 1, "A chest rattles"),
    "mimic.open": ("Wooden chest lid flung open with a creak", 0.8, 1, "A chest opens"),
    "mimic.roar": ("Chest monster roaring, toothy wooden snarl", 1.2, 1, "Mimic roars"),
    "mimic.ambient": ("Creaking wooden monster grumbling, low growl", 1.5, 1, "Mimic grumbles"),
    "mimic.hurt": ("Wooden box being struck, cracking thud and growl", 0.8, 1, "Mimic hurts"),
    "mimic.death": ("Wooden chest smashing apart, splintering crash", 1.2, 1, "Mimic breaks"),
    "heretic.flame": ("Burning figure flaring up, whoosh of flame", 1.0, 1, "Heretic flares"),
    "heretic.ambient": ("Burning man moaning, raspy and crackling", 1.5, 2, "Heretic moans"),
    "heretic.hurt": ("Burning man crying out, raspy", 0.8, 1, "Heretic hurts"),
    "heretic.death": ("Burning figure crumbling to ash with a final gasp", 1.5, 1, "Heretic dies"),
    "malebranche.chain": ("Chain rattling quickly", 0.8, 1, "Chain rattles"),
    "malebranche.hook": ("Grappling hook thrown, whooshing rope and metal", 1.0, 1, "Hook thrown"),
    "malebranche.angry": ("Demon snarling angrily, guttural", 1.2, 1, "Malebranche snarls"),
    "malebranche.ambient": ("Small demon cackling and grunting", 1.5, 2, "Malebranche cackles"),
    "malebranche.hurt": ("Demon grunting in pain", 0.7, 1, "Malebranche hurts"),
    "malebranche.death": ("Demon death screech fading", 1.5, 1, "Malebranche dies"),
    "malacoda.laugh": ("Evil demon laughing loudly, deep mocking cackle", 2.5, 1, "Malacoda laughs"),
    "malacoda.ambient": ("Big demon chuckling darkly, guttural", 1.5, 1, "Malacoda chuckles"),
    "centaur.angry": ("Wild horse-man neighing angrily, rearing", 1.5, 1, "Centaur rears"),
    "centaur.shoot": ("Bow string releasing an arrow, twang and whoosh", 0.8, 1, "Centaur shoots"),
    "centaur.ambient": ("Horse snorting and a man's low grunt", 1.5, 2, "Centaur snorts"),
    "centaur.hurt": ("Horse whinnying in pain", 0.8, 1, "Centaur hurts"),
    "centaur.death": ("Horse-man dying, pained neigh and a collapse", 1.5, 1, "Centaur dies"),
    "wrathful.roar": ("Furious man roaring with rage, raw scream", 1.2, 1, "Wrathful roars"),
    "wrathful.ambient": ("Angry man muttering and growling through teeth", 1.5, 2, "Wrathful mutters"),
    "wrathful.hurt": ("Angry man grunting in pain", 0.7, 1, "Wrathful hurts"),
    "wrathful.death": ("Enraged man's last furious scream, choked off", 1.2, 1, "Wrathful dies"),
    "harpy.swoop": ("Bird-woman diving, feathered whoosh", 1.0, 1, "Harpy swoops"),
    "harpy.screech": ("Harpy screeching, shrill bird-like scream", 1.2, 1, "Harpy screeches"),
    "harpy.ambient": ("Harpy cawing and chattering, raspy bird-woman", 1.5, 2, "Harpy caws"),
    "harpy.hurt": ("Harpy squawking in pain", 0.7, 1, "Harpy hurts"),
    "harpy.death": ("Harpy dying, falling screech and feathers", 1.5, 1, "Harpy dies"),
    "lovers.swoop": ("Two spirits swooping past on the wind, airy rushing whoosh", 1.0, 1, "Lovers swoop"),
    "lovers.gust": ("Sudden burst of swirling wind", 1.0, 1, "Wind bursts"),
    "lovers.ambient": ("Ghostly longing sigh of two lovers, airy", 1.5, 2, "Lovers sigh"),
    "lovers.hurt": ("Ghostly gasp, airy and sharp", 0.7, 1, "Lovers hurt"),
    "lovers.death": ("Two ghostly voices fading away on the wind", 1.5, 1, "Lovers fade"),
    "thief.steal": ("Quick snatch and a hissing snicker", 0.8, 1, "Thief steals"),
    "thief.ambient": ("Snake-like man hissing and skittering", 1.2, 2, "Thief hisses"),
    "thief.hurt": ("Hissing yelp of pain", 0.6, 1, "Thief hurts"),
    "thief.death": ("Hissing death rattle", 1.2, 1, "Thief dies"),
}

MUSIC = {
    "dark_wood": ("Dark ambient orchestral, lonely and lost, slow tempo, sparse low strings, distant solo cello, soft wind texture, "
                  "quiet and uneasy, building slightly in the middle and fading out", 100),
    "vestibule": ("Restless dark ambient drone, slow tempo, dissonant low strings and murmuring choir, buzzing textures, anxious "
                  "and aimless, gently swelling and receding", 100),
    "limbo": ("Melancholy medieval ambient, slow tempo, solo lute and soft recorder over a grey string pad, sorrowful and still, "
              "ancient and timeless", 100),
    "lust": ("Dark romantic orchestral, longing and stormy, slow waltz feel, sweeping strings and harp, wind-like choir, "
             "swelling like gusts and falling back", 100),
    "gluttony": ("Heavy sluggish dark ambient, very slow tempo, low brass and tuba groans, muddy bass drones, rain-like "
                 "percussion, oppressive and bloated", 100),
    "greed": ("Dark grinding orchestral, slow heavy march, low brass, clanking metallic percussion, cold and obsessive, "
              "repeating ostinato", 100),
    "dis": ("Ominous dark orchestral, slow tempo, deep choir, war drums in the distance, low brass swells, a fortress city "
            "of iron, foreboding and tense", 100),
    "heresy": ("Dark sacred choral ambient, slow tempo, low male chant over organ drone, crackling fire texture, solemn "
               "and doomed", 100),
    "violence": ("Brutal dark orchestral, mid tempo, pounding taiko drums, distorted low strings, harsh brass stabs, "
                 "boiling and violent, relentless", 100),
    "malebolge": ("Twisted dark ambient, slow and creeping, detuned strings, eerie music box, scraping metal textures, "
                  "deceitful and unsettling", 100),
    "cocytus": ("Frozen dark ambient, very slow, glassy high strings, icy chimes, deep sub bass drone, desolate and "
                "frozen still, utterly cold", 100),
    "forge": ("Industrial dark orchestral, mid tempo, rhythmic anvil hammer percussion, deep brass, roaring low drones, "
              "a vast forge of giants, powerful and driving", 100),
    "purgatory": ("Hopeful sacred ambient, slow tempo, gentle choir and organ, soft harp, dawn light, peaceful longing "
                  "and quiet hope, rising gradually", 100),
    "paradiso": ("Heavenly ethereal orchestral, slow tempo, shimmering strings, harp arpeggios, celesta and soft female "
                 "choir, serene and luminous", 100),
    "empyrean": ("Majestic heavenly choral, slow and radiant, full choir, organ and strings building to a glorious "
                 "luminous swell, awe and peace", 100),
    "lucifer.duel": ("Epic dark orchestral boss battle, fast tempo, aggressive strings ostinato, pounding drums, "
                     "dark choir chanting, brass hits, a deadly duel with a fallen angel, intense and driving", 150),
    "lucifer.enraged": ("Furious epic dark orchestral battle, very fast tempo, relentless war drums, screaming brass, "
                        "frantic strings, massive choir, chaotic and enraged", 150),
    "lucifer.true_form": ("Apocalyptic epic orchestral and choir boss finale, slow heavy tempo, colossal drums, deep pipe "
                          "organ, enormous brass, frozen and monumental, the devil himself, overwhelming dread", 180),
    # the guardians' fights: a track for each phase (_1 until half health, _2 enraged after), played by music/BossTheme
    "minos_1": ("Dark orchestral boss battle, steady mid tempo, a grim tribunal: pipe organ and low brass in a stern march, "
                "booming timpani, male choir intoning, a serpent-like low strings motif, solemn and menacing", 90),
    "minos_2": ("Furious dark orchestral boss battle, fast tempo, the judge in a rage: frantic strings ostinato, pounding "
                "timpani and war drums, roaring brass and a shouting choir, organ blasts, relentless", 90),
    "cerberus_1": ("Savage dark orchestral boss battle, mid tempo, a monstrous beast in mud and freezing rain: tribal drums, "
                   "growling low brass, snarling distorted cellos, heavy and primal", 90),
    "cerberus_2": ("Feral epic boss battle, fast tempo, three-headed beast unleashed: thunderous tribal drums, howling brass, "
                   "shrieking strings, chaotic and bestial, ferocious", 90),
    "plutus_1": ("Dark orchestral boss battle, mid tempo, a greedy demon of gold: heavy grinding march, low brass, clinking "
                 "metallic percussion and coins, a cold obsessive ostinato, menacing", 90),
    "plutus_2": ("Frantic dark orchestral boss battle, fast tempo, avarice gone mad: hammering metallic percussion, blaring "
                 "brass, racing strings, a mad repeating ostinato, greedy frenzy", 90),
    "phlegyas_1": ("Dark orchestral boss battle, mid tempo, a ferryman on a murky swamp river: heavy rowing rhythm in the "
                   "drums, deep surging strings, low horns through fog, brooding and wrathful", 90),
    "phlegyas_2": ("Stormy epic boss battle, fast tempo, wrath on a raging marsh: crashing drums like waves, furious "
                   "strings, roaring brass, thunder-like hits, turbulent and violent", 90),
    "furies_1": ("Dark orchestral boss battle, fast tempo, three shrieking winged furies: screeching high strings, "
                 "stabbing brass, rapid percussion, female voices wailing, wild and vicious", 90),
    "furies_2": ("Terrifying epic boss battle, fast tempo, a gorgon rises: petrifying low choir, hissing strings, "
                 "relentless drums, dissonant brass clusters, dread and panic", 90),
    "farinata_1": ("Dark sacred boss battle, mid tempo, a proud heretic in a burning tomb: dark pipe organ, low chanting "
                   "choir, crackling fire textures, heavy drums, defiant and haughty", 90),
    "farinata_2": ("Blazing epic boss battle, fast tempo, the tombs erupt in fire: thundering organ, roaring choir, "
                   "pounding drums, searing brass, an inferno of rage", 90),
    "minotaur_1": ("Brutal dark orchestral boss battle, mid tempo, a bull-headed beast in a stone labyrinth: pounding "
                   "drums like hooves, bellowing low brass, tense ostinato strings, heavy and stalking", 90),
    "minotaur_2": ("Rampaging epic boss battle, fast tempo, the beast charges: stampeding drums, bellowing horns, "
                   "furious strings, crashing cymbals, unstoppable rage", 90),
    "geryon_1": ("Eerie dark orchestral boss battle, mid tempo, a winged serpent of fraud with an honest face: deceptive "
                 "twisting melody, sly woodwinds over menacing strings, gliding swells, unsettling", 90),
    "geryon_2": ("Dizzying epic boss battle, fast tempo, a lying monster unmasked: whirling strings, lurching drums, "
                 "distorted brass, disorienting shifts, chaotic and treacherous", 90),
    "vulcan_1": ("Industrial epic boss battle, mid tempo, the smith of the gods in a vast forge: rhythmic anvil hammer "
                 "percussion, deep brass, roaring furnace drones, powerful and driving", 90),
    "vulcan_2": ("Molten epic boss battle, fast tempo, the forge erupts: thunderous hammering percussion, blazing brass, "
                 "roaring choir, lava-like low drones, overwhelming fury", 90),
    "seraph_1": ("Majestic heavenly boss battle, mid tempo, a six-winged burning angel: radiant choir, soaring strings, "
                 "bright brass fanfares, pounding timpani, awe and severity", 90),
    "seraph_2": ("Blazing celestial epic boss battle, fast tempo, holy fire unleashed: full choir in full voice, racing "
                 "strings, triumphant brass, thundering drums, overwhelming radiance", 90),
}

# Music for each biome (worldgen) and each sphere of Paradiso.
BIOME_MUSIC = {
    "dark_wood": "dark_wood", "vestibule": "vestibule", "acheron": "vestibule", "limbo": "limbo", "noble_castle": "limbo",
    "lust": "lust", "gluttony": "gluttony", "greed": "greed", "styx": "dis", "walls_of_dis": "dis", "heresy": "heresy",
    "phlegethon": "violence", "wood_of_suicides": "violence", "burning_sands": "violence", "malebolge": "malebolge",
    "malebolge_pitch": "malebolge", "malebolge_blight": "malebolge", "well_of_giants": "malebolge", "cocytus": "cocytus",
    "judecca": "cocytus",
}
SPHERE_MUSIC = {"primum_mobile": "empyrean", "empyrean": "empyrean"}  # the other spheres: paradiso
# vanilla nether_sounds(kind) -> Hellcraft's ambience
NETHER_AMBIENCE = {"nether_wastes": "ember", "soul_sand_valley": "tombs", "crimson_forest": "blood", "warped_forest": "thicket",
                   "basalt_deltas": "ash"}
