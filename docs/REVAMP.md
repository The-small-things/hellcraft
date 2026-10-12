# Hellcraft 3.0: the revamp plan

**Status:** draft for the owner to mark up (2026-10-12). Nothing in it is built yet.

**How to mark it up:** reply with an ID and your note, for example "Q2: yes", "L-IV: make it Aztec instead",
"B5: cut Phlegyas". You can also edit this file. Anything you don't mention, I'll take as approved. Section 15
lists the questions I need answered before Phase 1.

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

## 3. Design pillars

Every feature is checked against these.

- **P1. Fun without the lore.** Every mechanic is explained in one plain sentence where you first meet it. Lore
  is optional: a Codex to read, and voices you can ignore.
- **P2. Fair for new players.** A safe start, a clear difficulty curve, danger that is always telegraphed, and
  PvP that doesn't reward picking on the weak.
- **P3. Every layer is its own place.** A strong theme, one rule, one set-piece and one boss. Dante is the
  source, not a checklist (the ULTRAKILL approach).
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
- **T7. No more resource-pack server.** Art ships inside the mod, and port 25566 is no longer needed. For custom
  boss music, see Q6.
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

## 6. The layers (first draft of the themes)

Each layer has a look, one rule (a torment with a counter), a set-piece, new enemies and a boss. The IDs (L-0,
L-I…) are for your markup.

| ID | Layer | Look | Rule (and counter) | Set-piece and boss |
|---|---|---|---|---|
| L-0 | **Dark Wood** | Forest and mountains, villages, wandering traders. Virgil stands at spawn. | None. A safe place to build. | **The Gate of Hell**: a huge gate with the inscription, also in plain words. |
| L-S | **The Shore** | A grey plain, wasps, the river Acheron. | None. | **Charon's ferry**: ride the ferry across the river. A short layer. |
| L-I | **Limbo** | A sunlit green valley under a false sky, the only daylight in Hell. | None. A sanctuary with few monsters (see Q3). | **The Noble Castle** (seven walls, from Dante): the hub, with trading, an altar and the Hall of the Damned. No boss. |
| L-II | **Lust** | A ruined city of towers in an endless night storm. | Gusts throw you in the open; wind lines show where they come from. *Shelter in buildings, or sneak.* | **Minos's tribunal** at the city's heart. He coils his tail to pass sentence. |
| L-III | **Gluttony** | Endless feast halls collapsed into mud, under a rain of filth. | The rain makes you hungry. *Get under a roof.* | **Cerberus**, three heads with three separate attacks (bite, howl, filth breath). The showcase for jointed animation. |
| L-IV | **Greed** | Egyptian: golden dunes, pyramids, obelisks, sarcophagi. Lanes of rolling boulders (Dante's hoarders roll weights). | Carried gold slows you. *Stash it.* | **Plutus** in the Great Pyramid: a bloated golden god-king ("Pape Satàn aleppe"). |
| L-V | **Wrath** | A black, storm-tossed sea-marsh with sunken ruins and a watchtower with twin beacons (from Dante). | The water weakens you. *Use a boat.* | **Phlegyas**, the ferryman giant. His ferry carries you to the Walls of Dis. |
| L-Dis | **Walls of Dis** | A city wall held by fallen angels. | None. | **The Furies and Medusa.** The Furies fly; when Medusa appears, look away or turn to stone (the screen greys while you look at her). |
| L-VI | **Heresy** | A gothic burning necropolis, a city of open fiery tombs and a cathedral. | Smoke from the tombs brings darkness. *Get under a roof.* | **Farinata** rises from his tomb. The tombs around him ignite in patterns. |
| L-VII | **Violence** | Three rings: the boiling river of blood with centaur archers; the Wood of the Suicides with harpies and bleeding trees; the Burning Sands. | Fire rains on the sands. *Roof, water or Fire Resistance.* | **The Minotaur** at the entrance (as in Dante). |
| L-VIII | **Fraud** | Malebolge: ten trenches crossed by stone bridges, some of them broken. | Deception: mimic chests, a fake Rest, monsters that are invisible but give themselves away. *Watch for the tells.* | **Malacoda**, a demon who offers to guide you and leads you into an ambush (he lies to Dante the same way). **Geryon** above the great cliff; once beaten, he can fly you down. |
| L-G | **Well of Giants** | Chained giants at the cliff of the ice. | None. | **Antaeus** lowers you to the ice in his hand, a ride down (from Dante). |
| L-IX | **Treachery** | Cocytus: a frozen lake in four rings, with souls frozen in the ice. The Emperor's Spines lead in, and the "windmill" turns in the fog (section 8). | The cold. *Leather armour, or a fire nearby.* | **Lucifer** (section 8). |

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
  - New: Phlegyas, the Furies and Medusa, Farinata, Malacoda (a short event, not a full boss) and a Paradiso boss
    (Q5).
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
- The final boss: see Q5.

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

## 14. The playtest loop

- Every phase ships as a test build. Play it on a test world, which is wiped between phases when the map
  changes.
- Send me `logs/latest.log`, screenshots, and one line per bug: what you did, what you expected, what happened.
- New: `/feedback <text>` in game saves your note with your position, layer and the time, so there's one file to
  send.
- The stable 3.0.0 comes only after a session over the full journey.

## 15. Questions for you

- **Q1. Order.** Is the phase order right? What are your top three? (For example, you might want Lucifer before
  the Nether and the End.)
- **Q2. Do bosses block the way down?** I recommend no: beating a boss pays spoils and opens a shortcut (a lift
  down that cliff), but the roads and ramps are always open.
- **Q3. PvP-free start.** No PvP in the Dark Wood and Limbo, plus the 2-hour newcomer grace (F1, F2)? I recommend
  yes.
- **Q4. Art.** By default I make the models and animations as Blockbench files from code and check them in
  screenshots. You can open and edit any of them in Blockbench, and I'll convert yours. Do you want to make some
  of the art yourself?
- **Q5. Paradiso's final boss:** (a) the Seraph as a six-winged angel boss (recommended); (b) the vanilla dragon,
  restyled; or (c) no boss, only the sphere trials.
- **Q6. Custom boss music.** Keep loading tracks from the server's config folder (which needs a small pack
  server), or ship the tracks in the mod (you send me the files)?
- **Q7. The layer themes.** Which do you like or dislike in section 6? Is there anything from ULTRAKILL's feel you
  want specifically (speed, parrying, secrets)?
- **Q8. Branch.** CLAUDE.md says to work on `claude/minecraft-dantes-inferno-mod-ukodio`, but this session was
  started on `claude/intelligent-bohr-jv9n5v`. Which should be the revamp's home branch?
- **Q9. The modpack.** Every friend installs the Hellcraft modpack (a `.mrpack` for the Modrinth App or Prism).
  Is that OK? I'd keep the pack to Hellcraft and Fabric API to start, with no performance mods.
