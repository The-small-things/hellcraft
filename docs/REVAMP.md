# Hellcraft 3.0: the revamp plan

**Status:** approved by the owner on 2026-10-13, with section 6 redone after their answer to Q7. Phases 0 to 4
are built (see Progress in section 13) and wait on a play session. The owner marks it up in a commentable copy, the "Hellcraft 3.0 Revamp Plan" doc
(https://claude.ai/code/artifact/4b8ddced-e5ed-48a4-9b7f-3bafdffe313d); keep this file in step with it.
Section 15 records the answers.

---

## 1. What you asked for

> A mod anybody can enjoy, without having to understand the lore. Balanced and fair for new players. The layers
> don't have to match Dante exactly; look to ULTRAKILL for how to handle it (some layers match, some don't: Lust
> has cities, Greed is Egyptian). Lucifer needs a far more dramatic reveal and buildup. The Nether and the End are
> almost vanilla, and the End is confusing to navigate. Bosses need fluid animations and lore. The world must not
> be confusing to navigate.

Decisions already made:

| | Decision |
|---|---|
| **Fresh world** | Yes. The map can be rebuilt from scratch. |
| **Client mod** | Required. Players install the Hellcraft modpack, and vanilla clients can't join. |
| **World size** | Medium: radius about 3,500 (today it is 6,000). |
| **Keep** | Lifesteal and ghosts; blood weapons and armour; style ranks and boss spoils; the Seven P's, the Hall of the Damned and Dante's Journey. |
| **1.21.1** | Dropped for now. Its code stays in the repo untouched, to come back to later. |

## 2. What's wrong today

These come from reading the 2.0.3 code:

| Complaint | Why it happens |
|---|---|
| The world is confusing | It is 12,000 blocks across, and spawn is about 5,500 blocks from Lucifer. Most cliffs between circles have only 3 ramps, so in Lust they are about 8,000 blocks apart. The only guidance is chat text, `/circle`, and the line of guardian lairs along +X. |
| Bosses look stiff | Each boss is one rigid model on a display entity. The whole model tilts, bobs and squashes; no limb can move on its own. |
| Lucifer has no reveal | You walk into the pit, four chat lines play over 14 seconds, then a name card. |
| The Nether is vanilla | Vanilla terrain with some blocks swapped, a few small ruins, and one hall at 0, 0. |
| The End is confusing | The vanilla End with recoloured outer islands in rings out to 6,400 blocks, and the Celestial Rose 7,000 blocks out over the void. You fly thousands of blocks with nothing to aim at. |
| Too much lore | Much of the guidance comes through Virgil's and Lucifer's old-fashioned speech. 2.0.3 added plain tips, but the lore is still in the way. |
| The torments punish instead of play | Most circles just debuff you: Hunger in Gluttony, Weakness in the Styx, Darkness in Heresy, Slowness in Greed. They make the game less fun and add nothing to do. |

## 3. Design pillars

Every feature is checked against these.

- **P1. Fun without the lore.** Every mechanic is explained in one plain sentence where you first meet it. Lore
  is optional: a Codex to read, and voices you can ignore.
- **P2. Fair for new players.** A safe start, a clear difficulty curve, danger that is always telegraphed, and
  PvP that doesn't reward picking on the weak.
- **P3. Every layer is its own place.** A strong theme of its own, one rule, one set-piece and one boss. Where
  Dante's punishment would be miserable to play (blown about, starved, weakened, blinded), it becomes something you
  play with instead: ride the wind, outrun the mud, time the fire. Debuffs are a last resort. ULTRAKILL is the
  model for this approach, not for the look: no layer copies its settings.
- **P4. You always know where to go.** Down is the goal. The HUD and the map always show the way down and the
  nearest Rest.
- **P5. Bosses are events.** An intro, a name card, readable attacks, real animation, lore and music.
- **P6. One danger language.** The same colours and sounds mean the same thing on every boss. For example:
  red marks on the ground mean get out; a white flash means block with your shield now (a parry staggers the
  boss); purple means it can't be blocked, so jump or dodge.

## 4. Technical changes (the client mod)

- **T1. One jar for client and server.** The jar gets a client entrypoint and split client/common sources. A
  server tells a player with a missing or wrong version exactly which version to install, and where to get it.
- **T2. Real content.** Real blocks, items, mobs, sounds and particles. Today's disguised vanilla items (a Blood
  Heart is a renamed fermented spider eye) become real items. The world is fresh, so nothing needs migrating.
- **T3. Bosses with real animation.** Each boss becomes its own mob type with a jointed model: head, jaw, arms,
  wings and tail move on their own. They are animated with Minecraft's own keyframe system (the one the Warden
  and the Breeze use, with smooth interpolation). Models live in the repo as Blockbench files (`.bbmodel`), and
  a script turns them into game code. Big bosses get hitboxes made of several parts. There's no third-party
  animation library, so the mod never waits for one to update. (Checked: 26.3 has `KeyframeAnimation`.)
- **T4. Cutscenes.** The server tells clients to play a scripted camera path, with letterbox bars, fades,
  camera shake and slow pans. Players can't be hurt during one. Each cutscene can be skipped once you've seen
  it. Used for boss intros, the Lucifer reveal and the climb out of Hell.
- **T5. HUD and screens.**
  - Layer name and a depth bar.
  - A compass strip pointing to the next way down and the nearest Rest.
  - An ULTRAKILL-style style meter panel, replacing today's boss-bar workaround, and custom boss bars.
  - A **Map of Hell** screen: the funnel drawn from the same geometry as the terrain, with you-are-here, the
    Rests you've found and the bosses you've beaten.
  - A **Pilgrim's Journal** screen (your current goal, the Codex and how-tos), replacing the written book.
- **T6. Screen effects.** Frost at the screen edges in Cocytus, heat shimmer in Violence, and darkness pulses in
  Heresy.
- **T7. No more resource-pack server.** Art ships inside the mod, and port 25566 is no longer needed. Custom boss
  music ships in the mod too (Q6).
- **T8. Tooling.**
  - `versions/26.3` becomes self-contained: the four classes it borrows from the 1.21.1 tree are copied in.
  - The 1.21.1 jobs come out of CI. The 1.21.1 code itself stays.
  - Client game tests take screenshots of every boss animation, the HUD, the map and every layer. They're saved
    as CI artifacts, so I can see the art I make.
  - A local test server and headless client work in my environment. (Checked: JDK 25, the build, Docker and
    software OpenGL are all available. Modrinth is blocked here, so the local smoke test takes Fabric API from
    Fabric's own Maven instead.)
  - An automated test proves that every road from spawn reaches the pit without a step over one block.

## 5. The new map

The world is a funnel with radius 3,500 and Lucifer at 0, 0. Spawn is at the Gate of Hell, about 3,300 blocks
from the centre. All radii are first guesses to tune in playtests.

| From → to | Layer | Width |
|---|---|---|
| 3,500 → 3,150 | **The Dark Wood** (start, villages, mountains at the border) | 350 |
| 3,150 | **The Gate of Hell** | wall |
| 3,150 → 2,950 | **The Shore** (Vestibule and Acheron) | 200 |
| 2,950 → 2,600 | **I. Limbo** | 350 |
| 2,600 → 2,250 | **II. Lust** | 350 |
| 2,250 → 1,950 | **III. Gluttony** | 300 |
| 1,950 → 1,650 | **IV. Greed** | 300 |
| 1,650 → 1,380 | **V. Wrath** | 270 |
| 1,380 | **The Walls of Dis** | wall |
| 1,380 → 1,100 | **VI. Heresy** | 280 |
| 1,100 → 800 | **VII. Violence** (the river of blood, the wood, the sands) | 300 |
| 800 → 480 | **VIII. Fraud** (Malebolge) | 320 |
| 480 → 420 | **The Well of Giants** | 60 |
| 420 → 0 | **IX. Treachery** (Cocytus, Judecca, the pit) | 420 |

**Finding your way:**
- **N1. Four Pilgrim Roads** (north, east, south and west) run from the Gate of Hell to the centre. They are
  paved and lit, with a Virgil's Rest in every layer. Where a road meets a cliff there is always a wide stair, so
  you can't get stuck on a road.
- **N2. More ways down off the road.** Every cliff gets about 12 ramps (one every 30°) instead of 3, so in the
  outer layers you're never more than about 600 blocks from one, and much closer deeper down.
- **N3. Landmarks.** Each layer's set-piece, where its boss lives, can be seen from far away. It shows on the HUD
  once you've entered the layer.
- **N4. The HUD compass and the Map of Hell** (T5).
- **N5. Title cards** when you enter a layer, ULTRAKILL style: "LAYER II: LUST", one line on what the place does
  to you, and the gear you should have.
- **N6. Fast travel** between Rests you've visited (as today).
- **N7. The walkability test** (T8).

## 6. The layers (second draft)

Redone after Q7 ("I don't want a copy of ULTRAKILL; it handled the layers that have unfavourable gameplay
mechanics well"): the themes are my own, drawn from Dante's text, and each layer's rule turns its punishment into
something you play with (P3). The IDs (L-0, L-I…) are for markup.

| ID | Layer | Look | Rule (what you do about it) | Set-piece and boss |
|---|---|---|---|---|
| L-0 | **Dark Wood** | Forest and mountains, villages, wandering traders. Virgil stands at spawn. | None. A safe place to build. | **The Gate of Hell**: a huge gate with the inscription, also in plain words. |
| L-S | **The Shore** | A grey plain, wasps, the river Acheron. | None. | **Charon's ferry**: ride the ferry across the river. A short layer. |
| L-I | **Limbo: the Grey Meadow** | A quiet land drained of colour: grey grass, pale trees, mist. Inside the castle's seven walls is the only green in Hell. | None. A sanctuary with few monsters (Q3). | **The Noble Castle**: the hub. The poets and philosophers of old walk there as friendly ghosts who trade, give tips and hand out Codex pages. No boss. |
| L-II | **Lust: the Storm Canyons** | Violet canyons of wind-carved spires. Overhead, the souls whirl in vast flocks like starlings (Dante's own image). | **The wind is a road.** Visible wind streams carry you across the canyons. Ride them, and let go before the edge. | **Minos** judges at the canyon gate, as in Dante. He coils his tail to pass sentence. |
| L-III | **Gluttony: the Mire** | A rain-lashed bog of black mud, rotting giant trees, bloated fungus and sunken feast tables. | **The mud swallows you if you stand still.** Keep moving, or stand on roots, stone and planks. | **Cerberus**, three heads with three separate attacks (bite, howl, filth breath). The showcase for jointed animation. |
| L-IV | **Greed: the Counting House** | A mountain range of hoards: hills of gold coin, vault doors in the cliffs, giant scales, and coins as big as houses rolling down the slopes (Dante's misers roll great weights). Built like the banks and mints of Dante's Florence. | **Gold draws the hoarders.** The more you carry, the more of them come for you. Stash it and they lose interest. | **Plutus** in the Mint, a bloated golden banker-god ("Pape Satàn aleppe"). |
| L-V | **Wrath: the Fen of Styx** | A dark marsh of reed mazes and fog. Across it, the iron city of Dis glows red on the horizon (as in Dante), so you always know which way is in. A watchtower with twin beacons. | **The sullen lurk under the water.** Bubbles show where; step in and hands drag you down. Plank roads and boats are safe. Light the tower's beacon and the ferry comes. | **Phlegyas**, the ferryman giant. His ferry carries you to the Walls of Dis. |
| L-Dis | **Walls of Dis** | A city wall of red-hot iron held by fallen angels. | None. | **The Furies and Medusa.** The Furies fly; when Medusa appears, look away or turn to stone (the screen greys while you look at her). |
| L-VI | **Heresy: the Iron City** | Inside Dis: streets of red-hot iron towers, and open tombs that glow like ovens. | **The tombs blast fire in turn.** Watch the rhythm and cross between blasts. | **Farinata** rises from his tomb. The tombs around him fire in patterns. |
| L-VII | **Violence** | Three rings: the river of boiling blood, the Wood of the Suicides, the Burning Sands. | One per ring. **River:** cross at the fords while the centaur patrols look away. **Wood:** breaking branches makes the trees scream and calls the harpies, so sneak. **Sands:** fire falls in waves after a warning, so run between the rocks. | **The Minotaur** at the entrance (as in Dante). |
| L-VIII | **Fraud: Malebolge** | Ten trenches crossed by stone bridges, some broken. Some trenches have their own trick: the boiling pitch with its gang of demons, the thieves' pit, the flames that hide talking souls. | **Nothing is what it seems.** Mimic chests, a fake Rest, thieves that steal an item (kill them to get it back), and invisible monsters that give themselves away. | **Malacoda**, a demon who offers to guide you and leads you into an ambush (he lies to Dante the same way). **Geryon** above the great cliff; once beaten, he can fly you down. |
| L-G | **Well of Giants** | Chained giants at the cliff of the ice. | None. | **Antaeus** lowers you to the ice in his hand, a ride down (from Dante). |
| L-IX | **Treachery: Cocytus** | A frozen lake in four rings, with souls frozen in the ice. The Emperor's Spines lead in, and the "windmill" turns in the fog (section 8). | **The cold builds while you're away from fire.** Light the braziers on the ice: they stay lit for everyone, so the way across stays marked and warm. | **Lucifer** (section 8). |

Every layer also gets one or two new monsters, its own loot structures, and a Rest on each road.

## 7. Bosses

- **B1. Every boss fight follows the same pattern.**
  - An intro cutscene of 5–10 seconds, skippable after the first time.
  - A name card ("CERBERUS / The Great Worm") and 2–3 spoken lines.
  - 3–5 attacks in the danger language (P6), and a phase change at half health.
  - A death animation, and its own music.
- **B2. Lore.** Meeting a boss unlocks its Codex entry: who it is (2–3 plain sentences from Dante), how to fight
  it, and a quote. Each boss's lines hint at Lucifer (section 8).
- **B3. Animation.** Jointed models (T3), each with idle, walk, 3–5 attack, hurt, phase-change and death
  animations.
- **B4. Kept from 2.0:** style ranks and spoils, and the anti-cheese rules (weak arrows, no building, pulled in
  for sniping). Boss damage scaling with your hearts stays, to review after a playtest.
- **B5. The roster:**
  - Remade: Minos, Cerberus, Plutus, the Minotaur, Geryon, Vulcan and Lucifer.
  - New: Phlegyas, the Furies and Medusa, Farinata, Malacoda (a short event, not a full boss) and the Seraph in
    Paradiso (Q5).
  - That's a lot. Cut any you don't want.

## 8. Lucifer

- **L1. A buildup across the whole journey.**
  - **The wingbeat.** Every so often, all over Hell, the ground shudders and a cold wind blows from the centre:
    a screen shake, a sound, frost at the screen edges, stronger the deeper you are. The first time, a plain
    tip: "That was Lucifer's wings beating at the bottom of Hell."
  - **Every boss mentions him,** and the Codex entry "The Emperor" fills in piece by piece as you beat them.
  - **The windmill.** From the Well of Giants you see, far off in the fog, something like a giant windmill
    turning. It is his wings: Dante describes him exactly this way (Inferno XXXIV). It gets bigger and clearer as
    you cross the ice.
  - **The Spines** and his voice on the way down stay.
- **L2. The pit.** A ready check at the edge, where everyone confirms (or a 30-second countdown), so friends start
  together. Then a plain "point of no return" warning.
- **L3. The reveal** (a cutscene, then the fight).
  - Silence. The Fallen Seraph descends: beautiful, calm, lying ("I am not thy enemy").
  - Phase 1 is the duel. At half health he snaps (phase 2).
  - When his seraph form breaks, the camera pulls back: the mountain you've been walking toward is his body. It
    opens six eyes.
  - Phase 3: the Three-Faced Emperor, about 60 blocks tall and chest-deep in the ice. You fight his faces and
    hands across the whole arena, and his weak points glow.
- **L4. The ending.** He freezes and weeps. The way out is through him: you climb down his side past the centre
  of the earth, the world flips over (the camera rolls 180°), and you climb up into Purgatory under the stars.
  This is the end of Inferno, done literally.
- **L5. Kept:** the reward menu, harder fights for returning champions, and the cooldowns.

## 9. The Nether: the Forge of Dis

A fresh world means the Nether is generated from scratch, not just re-skinned.

- **Its role:** Hell's workshop. You come here to upgrade gear (the Hellforge and infernal gear), get netherite,
  and fight Vulcan.
- **Biomes (draft):**
  - Foundry Halls: vast pillared caverns with chain-lifts and machinery.
  - Slag Seas: molten metal with a cooling crust that cracks under you, with a warning first.
  - The Chainlands: islands hung on giant chains over an abyss of fire.
  - The Ash Wastes.
  - The Cyclops Quarries.
- **Structures:** ruined foundries (dungeons with forge stations), Cyclops camps (mini-boss), chain-lift towers
  for climbing, and the **Great Forge** at 0, 0, where Vulcan lives.
- **Finding your way:** a column of heat over the Great Forge, visible across the Nether and shown on the HUD
  compass.

## 10. Purgatory and Paradiso (the End)

- **One story arc, stated in the Journal:** down through Hell, defeat Lucifer, climb Purgatory, rise into
  Paradiso. The Nether is a side workshop you can visit at any time.
- **Purgatory** becomes its own small dimension: an island mountain in a starlit sea. It has seven terraces,
  where the Seven P's live, and a Gate of Return to the Gate of Hell. This is a light-touch change.
- **Paradiso** is rebuilt as a climb **upward**: nine spheres stacked one above the other, each a cluster of
  floating islands with its own look (the 2.0 themes: Moon, Mercury, Venus, Sun, Mars, Jupiter, Saturn, the
  Fixed Stars, the Primum Mobile). A column of light lifts you to the next sphere. You can't get lost: the way is
  up, and you can see the next sphere above you. The **Celestial Rose** in the Empyrean is at the top.
- Each sphere holds a short trial. The relics (Seraph Wings, the Halo, Beatrice's Rose) are the rewards.
- **No strongholds or eyes of ender:** the only way into Paradiso is from Purgatory's summit.
- The final boss is **the Seraph**, a six-winged angel (Q5).

## 11. Fair for new players

- **F1. Sanctuary.** No PvP in the Dark Wood or Limbo (a config option), and Limbo has no torment.
- **F2. Newcomer grace.** For your first 2 hours of play you can't lose hearts, and others can see that. It ends
  early if you attack a player.
- **F3. No profit from bullying.** Killing someone with less than half your hearts steals nothing, and marks you
  with *Cain's Mark*: you glow and carry a bounty for 10 minutes.
- **F4. Difficulty climbs by layer,** and the title card says what gear you should have.
- **F5. Everything is explained once, plainly,** when it first matters (title card, tip, Journal).
- **F6. Deaths to monsters cost items and XP as in vanilla.** Only players take hearts (today's 26.3 default).
- **F7. Every number is in the config,** to tune after playtests.

## 12. The systems you're keeping

- **Lifesteal and ghosts:** unchanged rules. Ghosts get the map.
- **Blood weapons and armour:** become real items with their own models. The Blood Arts get real particles and
  sounds. The Hellforge moves into the new Nether.
- **Style ranks and spoils:** same rules, shown in a proper style panel.
- **The Seven P's:** kept, and tied to Purgatory's terraces.
- **The Hall of the Damned:** kept, and moved to the Noble Castle in Limbo (or kept at spawn).
- **Dante's Journey:** rewritten for the new layers.
- **Removed or replaced:** the resource-pack server, the display-entity boss models, the guardian road along +X,
  the disguised vanilla items, and the old End rings.

## 13. Phases

Each phase ends with a test build ("Hellcraft 3.0.0 test" on the Releases page) and a play session. Nothing goes
stable without one.

| Phase | What | Done when |
|---|---|---|
| **0. Foundations** | T1, T2 (the blood items become real items), T7, T8. No new gameplay. | Friends join with the modpack; a test model animates in a CI screenshot; 2.0's features all still work. |
| **1. The map** | New geometry and terrain (section 5); roads, ramps and Rests; title cards; HUD compass; the Map of Hell; the Journal. Layers get their basic look only. | You can walk from spawn to the bottom without help. |
| **2. Boss tech** | The whole boss pipeline, proven on **Minos**: model, animations, intro cutscene, Codex entry, danger language. | You've fought Minos and told me how it feels, before I build the rest. |
| **3. Upper Hell** | Limbo to Wrath: set-pieces, monsters, structures, Cerberus, Plutus, Phlegyas, and the fairness rules F1–F4. | A play session through layers I–V. |
| **4. Lower Hell** | Dis to the Giants: the Furies and Medusa, Farinata, the Minotaur, Malacoda, Geryon, Antaeus. | A play session through layers VI–VIII. |
| **5. Lucifer** | Cocytus, the wingbeat, the windmill, the colossus, the reveal and the climb out. | You've beaten him with friends. |
| **6. The Nether** | Section 9. | A play session. |
| **7. Purgatory and Paradiso** | Section 10. | A play session. |
| **8. Polish** | Balance, the README, and the 3.0.0 stable release. | A session over the whole journey. |

Phases 3, 4 and 5 are the biggest.

**Progress.** Phase 0 was done on 2026-10-13. Phase 1 was built on 2026-10-14 (the map at radius 3,500, the four
roads, 12 ways down per cliff, 40 Rests, circle cards, the compass, the Map of Hell on M and the Journal on J).
Phase 2 was built the same day: the Blockbench model pipeline, Minos's animated body and fight in the danger
language, his entrance cutscene and Codex entry, and level arenas for the lairs. Multi-part hitboxes wait for the
first boss that needs them (Minos fits one). Phase 3 was built on 2026-10-15: the fairness rules F1-F3; the
upper circles' rules (Lust's wind streams, Gluttony's sinking mud, Greed's hoarders, Wrath's sullen under the
Styx); Limbo's Grey Meadow and the Noble Castle with nine shades who talk, trade and hand out Codex pages; the
set-pieces (soul flocks over Lust, the Mire's rotting trees, bloated fungus and sunken feast tables, the Counting
House's coin hills, vaults, giant scales and rolling coins, the Fen's fog, reeds, boardwalks, watchtowers and Dis glowing on the
horizon); Cerberus, Plutus and Phlegyas on real bodies with fights in the danger language (Phlegyas is called by
the east watchtower's beacons, and every watchtower's beacons call his ferry to Dis); and a new monster per circle
(the lovers, the gluttons, the hoarders, the wrathful), each with a Codex page. Phase 4 was built the same day: the
lower circles' rules (Heresy's tombs blast fire in turn; Phlegethon's fords, watched by centaurs; the Wood's trees
scream and call the harpies unless you sneak; the Sands' fire falls in waves, with rock arches for shelter;
Malebolge's mimics, fake Rests, thieves and the Malebranche's hooks, and Malacoda's lying escort on the fifth
ridge); the Furies (who call Medusa: look away from her) inside the Walls of Dis, Farinata in his burning tomb, the
Minotaur and Geryon, all on real bodies with intro cutscenes and Codex pages; Geryon, once beaten, flies souls down
the great cliff, and Antaeus sets them down on the ice from the Well (both optional: the ramps stay). New monsters:
heretics, centaurs, harpies, mimics, thieves and the Malebranche. Phase 5 was built on 2026-10-16, with the
playtest fixes from Phases 1 to 4 (lairs and Rests spread out, a ruined road with slab steps, Antaeus's arm, Geryon's
flight, more wind in Lust, a ferry that gets you somewhere): Cocytus's cold and its braziers, which stay lit for
everyone; his wingbeat felt all over Hell and the windmill seen across the ice; a 70-block pit with six ice pillars;
the ready check and the point-of-no-return warning; the Fallen Seraph's descent (a cutscene), his duel in the danger
language and his enrage; his breaking and the reveal of the Emperor (a cutscene), who is hurt only through the hands
and faces he leaves down and whose wingbeat you hide from behind the pillars; and the climb out down his side with
the camera rolling over, into Purgatory. Not yet: the other bosses' lines about him, and a Codex page that fills in
as they fall. Phase 6 was built the same day: the Forge of Dis, a Nether made from scratch round the Great Forge's mesa (the
Slag Sea, crossed on a crust that cracks and melts under you; the Foundry Halls, with rivers of lava and laddered pillars;
the Chainlands, islands hung on chains with bridges between them; the Ash Wastes; and the Cyclops Quarries, terraced with
ramps), stairways down the mesa, a column of heat over the Forge, the compass pointing to it, Vulcan on a real body with
an entrance cutscene and a fight in the danger language, and the Cyclopes, mini-bosses at camps in the Quarries. The Foundry
Halls' "chain-lifts" are ladders up the pillars for now. Phase 7 was built the same day: Purgatory became its own world, the
mountain island in a starlit sea before dawn; Paradiso became its own world too, nine spheres of floating islands stacked one
above the other (no sphere right over the one below, so you can always see the next), each with its own look, a short trial
(lights to gather, against the clock on Mercury, circling on Venus, in order on Jupiter's Eagle, at the top of Jacob's Ladder
on Saturn and of the star spires; the Angelic Guard on Mars) and a column of light that lifts you to the next sphere once
you've passed it; no falling damage there, and angels catch whoever falls; the Seraph on a real body, with an entrance
cutscene and a fight in the danger language, in the Primum Mobile; the Celestial Rose atop the Empyrean; Seraph Wings from
Saturn, the Halo from the Seraph and Beatrice's Rose from the bell; no strongholds in the Inferno, and the End vanilla again;
the story arc on the Journal's second page. Phase 8 began on 2026-10-17 with the
remaining items that don't wait on the play session: an op command that hands out a full testing kit; the style rank in its own panel
on the screen (section 12); every guardian of Hell saying something about Lucifer as it falls, with a Codex page on the
Emperor that fills in as they do (L1); a lift down to the next circle in each beaten guardian's lair (Q2); real
chain-lifts on the Foundry Halls' pillars; and the relics as real items, with their own looks when worn. Balance waits on the play session. Phases 1 to 7 wait on a play session.

## 14. The playtest loop

- Every phase ships as a test build. Play it on a test world, which is wiped between phases when the map
  changes.
- Send me `logs/latest.log`, screenshots, and one line per bug: what you did, what you expected, what happened.
- New: `/feedback <text>` in game saves your note with your position, layer and the time, so there's one file to
  send.
- The stable 3.0.0 comes only after a session over the full journey.

## 15. Answers (2026-10-13)

The owner changed Q4 and Q7; every other question stands as recommended.

| ID | Question | Answer |
|---|---|---|
| Q1 | Is the phase order right? | Yes, as in section 13. |
| Q2 | Do bosses block the way down? | No. Beating a boss pays spoils and opens a shortcut (a lift down that cliff); roads and ramps are always open. |
| Q3 | No PvP in the Dark Wood and Limbo, plus the 2-hour newcomer grace? | Yes. |
| Q4 | Who makes the art? | Claude, as Blockbench files checked in screenshots. The owner won't make art (and offered a Blender MCP; not needed, since Minecraft models are made of boxes, which Blockbench files describe directly). |
| Q5 | Paradiso's final boss? | The Seraph as a six-winged angel boss. |
| Q6 | Custom boss music? | Ship the tracks in the mod; the owner sends the files. |
| Q7 | The layer themes? | "I don't want a copy of ULTRAKILL; I said ULTRAKILL because it handled the layers that have unfavourable gameplay mechanics quite well." Section 6 is redone, and P3 says it. |
| Q8 | Which branch? | `claude/minecraft-dantes-inferno-mod-ukodio` (CLAUDE.md), once the owner confirms it in chat. |
| Q9 | Every friend installs the Hellcraft modpack? | Yes, with only Hellcraft and Fabric API to start. |
