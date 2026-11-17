# Hellcraft combat: swings and combos, dodge and stamina, weapon guard, hit feel

## State (2026-11-17)
**Stage 1 is built** (3.5 beta): stamina and its bar, the dodge roll for everyone, guarding and parrying with weapons, guard break,
hit feel on ordinary blows, and the animation pipeline every later move uses (a player rig in tools/art/player_moves.py, baked
keyframes, a blending runtime on the real player model and in first person). What changed from the plan below while building it:
- **The guard isn't vanilla's `blocks_attacks` component.** A default component can't be switched off by config and would have
  let a sword's guard pre-empt a shield in the other hand. Instead a guard is vanilla's "using an item" state, started by our
  own message after vanilla found no use for the right-click, with the blocking worked out in `combat/Guard` from a mixin.
- **The Blood Art is the attack key pressed from the guard** (not a charged heavy at full blood), so a heavy attack stays a
  heavy attack and the art is a deliberate two-button move.
- **The roll is driven by the client** tick by tick along a speed curve (a player's movement is the client's own), not one
  server push: it starts the moment the key goes down and matches the animation.
- **Heavy attacks** (stage 2): press starts the light swing's wind-up at once; still holding four ticks later, it turns into the
  heavy's charge. No delay on a tap, and no separate key.
**Stage 2 is built** (2026-11-17): swings, combos and heavy blows for swords, axes and maces (the hell weapons borrow them),
arc hits through vanilla's own attack, the weapon turning in the hand, first- and third-person animation. Left out on purpose:
fists (the empty hand is for the world, not for fighting) and "swing through grass" (a click at a block with something to fight
in the arc already swings). Blade trails wait for stage 3.
**Stage 3 is built** (2026-11-17): the hell weapons' own movesets (the Bloodletter's four cuts with a spin and a deep
finisher, the Reaper's sweeps and pulling turn, the Tithe Axe's ground-shaking slam), blood trails on their blades, and a move
shown for each Blood Art.
**Next: stage 4, tuning from play.** Every number is in `combat/Moveset` (damage shares, arcs, reach) and `HellConfig`
(stamina, roll, guard, parry). Things to watch in the first session: whether a chain out-damages vanilla clicking by too much,
whether the wind-up before a blow feels late on a busy server, the parry window (6 ticks), the roll's distance and safe ticks,
and the first-person positions of each swing (tools/art/player_moves.py, filmed by CombatTest). Ideas not done: fists, a
screen-space blade trail in first person, real sounds (docs/AUDIO.md), mobs that guard and roll.

**Getting about (2026-11-17, after "add more smooth animations like for running, bows, blocking, jumping"):** every player
seen, combat or not, is drawn by the rig: `client/combat/anim/Locomotion` (walk, run and sneak strides read by vanilla's walk
position so the feet keep pace with the ground, cross-faded by sprint and played backwards when backing up; idle breath; a
jump's tuck, a long fall's flailing, a landing that gives with how hard it was and dips your own view; leaning into bends),
item stances in `PlayerAnimator.items` (a bow drawn by how far it is drawn and loosed with a release move, a shield braced
behind and rocking when struck (the server sends `SHIELD_HIT` from `LivingEntityMixin`), food and bottles to the mouth, a
crossbow's kick, a trident's throw), and `PoseSmoother`, which eases every pose vanilla switches at once (crouching, a bow or
shield coming up, a crossbow loaded, getting into a boat) over 4.5 ticks, with the crouch's drop eased in
`AvatarRendererMixin.getRenderOffset`. The bow and crossbow clips are additive (`RigPose.additive`, `Clips.ADDITIVE`) over
vanilla's aiming arms, so the aim stays. Vanilla keeps swimming, gliding, riding, climbing, flying and sleeping, and any arm
busy with an item pose or a swing. Each player can turn it off for themselves: `/hellcraft-client animations off`
(config/hellcraft-client.json). `MotionTest` plays and films it (`motion_*`); CombatTest films the clips on mannequins.

**First person and the roll (2026-11-17, after "decrease the hitbox when rolling, the animations look great in 3rd person but not
first person"):** every swing's fp keys redone with `tools/art/fp_preview.py` (wind-ups raised where you can see them, cuts through the
middle, follow-throughs above the hotbar; the overhead chops down, the thrust stops short of shrinking to a toy, the guard break stays in
view, the roll tucks the weapon fully away); first-person locomotion keys (stride sway, sprint swing, jump lift, fall, landing drop);
`FirstPersonEase` for vanilla's first-person snaps; `RollShape` (a rolling player 0.9 high).

## Context
The owner wants combat that feels like Better Combat / Epic Fight / Immersive Combat: weapons that swing differently
and chain into combos, a stamina bar with dodge rolls, guarding and parrying with weapons (not only shields), and
meatier hits. Their answers: **all four parts**, **all melee weapons, with the most care on Hellcraft's own weapons**,
**on by default with a server setting to turn it off**. Hellcraft avoids dependencies (and those mods would need 26.3
builds), so we build our own in Hellcraft's style. The danger language already says "white = block now,
a parry staggers" and there's a Sidestep Dash, so this extends what players already know.

## What the public descriptions of those mods taught us (ideas only)
Better Combat is All Rights Reserved, so nothing of it is downloaded, decompiled or copied: Hellcraft's code, animations
and art are all our own. Ideas taken from its and Combat Roll's public pages (Epic Fight's page says little beyond
"souls-like"):
- **An upswing before the hit** (a wind-up phase, then the blow), hits by **weapon collision** (the arc) not by crosshair.
- **Sweeping reworked:** a swing that hits several enemies deals reduced damage to each unless the weapon has Sweeping
  Edge (full damage to all). Hellcraft weapons treat their own arcs as full-damage on the first target, 60% on the rest.
- **Hold attack** to keep swinging on cooldown; **swing through grass** (zero-hardness blocks like grass and flowers
  don't eat the swing).
- **Weapon data files:** each weapon's moveset is JSON in the mod (`data/hellcraft/movesets/<id>.json`, with a fallback
  by item tag: sword, axe, mace, spear), so modded weapons from other mods get a matching preset.
- **Roll charges:** a roll has a recharge (not just a cost); rolls can't be used while using an item, jumping or
  swimming; the roll's distance and recharge speed scale with gear (here: Sidestep, and later possibly boots). So the
  Dodge keeps both a stamina cost and a short per-roll cooldown, and Sidestep levels add a second charge.
- Skipped for now: dual wielding (Hellcraft's weapons are one- or two-handed by design) and Epic Fight's skill trees.

## Shape
New server package `combat/` and client package `client/combat/`. The server decides hits, stamina, i-frames and
parries. The client predicts its own swing for responsiveness and draws everything. Each part ships as its own beta
push (stages below), checked with client-test screenshots before moving on.

### Config and sync (`config/HellConfig`)
`combatOverhaul` (true), `combatStamina` (true), `staminaMax` 100, `staminaRegenDelay` 20 ticks, `staminaRegen` 1.5/tick,
`dodgeCost` 30, `heavyCost` 25, `guardDrain` (per blocked blow: damage x4). New `net/CombatConfigPayload` sent on join and
on `/hellcraft config` changes, so the client knows whether to take over the attack key. With `combatOverhaul` off,
everything below falls back to vanilla (the Sidestep Dash behaves as it does now).

### Stamina (`combat/Stamina`, client `client/combat/StaminaHud`)
In memory per player (not saved). Spent by dodges, heavy attacks and blows taken on guard; refills after a short pause.
`net/StaminaPayload` only on change. Drawn as a thin bar over the hunger bar (blood-red, flashing when empty; hidden
when full and calm, like Epic Fight). An empty bar means no dodge, no heavy attack, and a guard that breaks.

### Dodge roll (`combat/Dodge`; reuses `DashKey` and `net/DashPayload`; `blood/LayerEnchants.dash` hands over to it)
Everyone can dodge with the Dash key (Left Alt) when combat is on: a short roll the way you're moving (a hop back
standing still), costing stamina. i-frames (6 ticks) against **all melee blows and boss marks**, not only bosses
(extends `LayerEnchants.dodging` and its `ALLOW_DAMAGE` hook). **Sidestep** leggings now upgrade it: longer roll,
cheaper (-20% per level), +2 i-frame ticks per level. Third person: the body tucks and rolls (animation `roll`); first
person: a quick camera dip through `CameraMixin`. A perfect dodge (a blow lands inside the i-frames) adds style
(`StyleMeter.onParry`-like `onDodge`) and a soft whoosh.

### Weapon guard and parry (`combat/Guard`)
Swords, axes, Hellcraft weapons (and the mace) get vanilla's `blocks_attacks` component at startup through Fabric's
default-item-component event. Vanilla then handles right-click to guard, the guard pose, facing checks, damage
reduction, and the hook into `BossRules.raisingShieldAgainst` and `GuardianFight`'s parry window, with no new code
paths (verified: `Item.use` starts using any item with `BLOCKS_ATTACKS`, and its use animation becomes `BLOCK`).
Weapons block less than a shield: swords 60%, axes 50%, hell weapons 70%; arrows are not blocked. A shield in the off
hand still takes priority. Every blow taken on guard costs stamina; an empty bar breaks the guard (stunned 1 s, like
an axe disabling a shield). **Parry:** a guard raised in the last 5 ticks before a melee blow cancels it fully and
staggers the attacker: Hell's monsters (`SoulMonster` gets `stagger(ticks)`: AI paused, knocked back), vanilla mobs
knocked back and slowed, and a boss counts as a blocked white flash (the existing `onParried`). Sound and particles to
match.
Hell weapons' **Blood Art** moves to the charged heavy attack at 100% blood (see below). The **Blood Oath** stays on
sneak + right-click. Plain right-click becomes guard. The weapons' lore and the Guide say so.

### Swings and combos (`combat/Moveset`, `combat/Swing`; client `client/combat/SwingAnimator`)
- **Movesets by weapon class:** sword (slash, backslash, thrust; heavy: lunge), axe (diagonal chop, return chop; heavy:
  overhead cleave), mace (wind-up smash; vanilla smash attack kept), trident/spear (jab, jab, sweep; their right-click
  stays vanilla), fists (jab, cross). Pickaxes, shovels and hoes stay vanilla (the Reaper of Minos is the exception).
- A move has its animation, a hit event time, an arc (half-angle, reach from the weapon's attribute, height), a damage
  multiplier, knockback and a combo window. Attacking again in the window (or buffered just before it) plays the next
  move; waiting resets the chain. The attack cooldown bar shows the move's recovery.
- **Hold attack** (with no block targeted) to charge (8 ticks or more), release for the class's heavy move (costs stamina).
- **Hits:** client `MinecraftMixin` on `startAttack`: holding a moveset weapon, it plays the move locally and sends
  `net/SwingPayload`. If the crosshair is on a block and no entity is in the arc, it mines as before. The server checks
  the cooldown, runs the move on the player's clock, and at the hit event sweeps the arc. It calls vanilla
  `player.attack(target)` once per target with full attack strength and a temporary `ATTACK_DAMAGE` modifier for the
  move's multiplier, so enchantments, crits, lifesteal, `HellWeapons` passives, `StyleMeter`, fairness rules and
  `BossRules` all keep working. Vanilla's sweep is suppressed during our sweep. Line of sight is required per target.
- Other players see the swing: the server relays `SwingPayload` (entity id, move, start) to everyone tracking.
- **Animation source, the boss pipeline reused:** `tools/art/player_moves.py` builds `versions/26.3/art/player_moves.bbmodel`,
  a player-shaped rig with bones `head body right_arm left_arm right_leg left_leg`, one animation per move, with `hit` events.
  `tools/gen_bosses.py` turns it into keyframes (`client/render/gen/PlayerMovesAnimations`) and timings
  (`entity/boss/gen/PlayerMovesAnim`, or a `combat/gen` target), so the server times hits by the same events, exactly
  as boss blows are timed. Rotations follow the Blockbench rule in CLAUDE.md (+X swings a hanging arm forward).
- **Third person:** a mixin at the end of `HumanoidModel.setupAnim` (players only, via `AvatarRenderState.id`) applies
  the playing move's keyframes on top of the vanilla pose. Walking legs blend; arms are taken over.
- **First person:** a mixin on `FirstPersonHandsAndItemsRenderer.applyItemArmAttackTransform`/`swingArm` replaces the
  vanilla bob with a transform sampled from the same move's `right_arm` channel (shoulder pivot, plus yaw sweep), so
  both views come from one source.

### Hit feel (client `client/combat/HitFeel`; server `net/HitPayload` to the attacker and those nearby)
- **Hit-stop:** the attacker's swing pauses 2 to 4 frames on a confirmed hit (longer for heavies), and the target's
  hurt flash holds.
- **Camera kick:** a small directional shake on your own hits, bigger on heavies, parries and guard breaks (the
  existing shake in `CameraMixin`, used by `danger/Impacts`).
- **Particles:** a slash trail of sweep particles along the arc at the hit event, coloured per weapon; a blood spray on
  flesh, sparks on armour, undead bone dust, and gold sparks on a parry.
- **Sounds:** `hellcraft:combat.swing.light|heavy`, `combat.hit.flesh|armour`, `combat.parry`, `combat.guard_break`,
  `combat.roll` in `sounds.json`, built from vanilla events with pitch and volume variants, as the soundscapes are.

### Hellcraft weapons: their own movesets (the "more care" part)
- **Bloodletter** (rapier): quick 4-hit chain (slash, slash, spin slash, thrust); the finisher makes the target bleed
  longer. Crimson trail. Heavy at full blood = **Exsanguinate** dash (the existing Blood Art) with its own lunge animation.
- **Reaper of Minos** (scythe): wide sweeping arcs (160 degrees), slow 3-hit chain ending in a full spin that pulls
  enemies in. Heavy at full blood = **Harvest** with a two-handed overhead reap.
- **Tithe Axe:** heavy 2-hit chain (chop, upward rip); the heavy is a ground slam with a small shockwave ring. At full
  blood = **Blood Frenzy**, a roar pose; during the frenzy the chain gets a third hit.
- **Blood Pickaxe:** stays a tool (vanilla mining); a single pick-strike move for fights, and its Blood Art on heavy at full blood.
- **Infernal forms:** the same moves plus ember trails and a fire burst on finishers.
- New lore lines and Guide text for each.

## Stages (each a beta push)
1. **Foundations:** config and sync, stamina and HUD, dodge roll for everyone, weapon guard and parry, hit-feel basics
   (hit-stop, kick, particles, sounds) on vanilla attacks.
2. **Swings and combos:** the move pipeline (player_moves.bbmodel to keyframes and timings), third- and first-person
   animation, arc hits, heavies and combos for the vanilla classes.
3. **Hell weapons:** signature movesets, Blood Art on full-blood heavies, trails, lore, Guide and Lessons.
4. **Tuning** from the screenshots and the owner's play session (numbers in HellConfig).

## Files
- New: `combat/{Stamina,Dodge,Guard,Moveset,Swing,CombatSync}.java`, `net/{CombatConfigPayload,StaminaPayload,SwingPayload,HitPayload}.java`,
  client `client/combat/{StaminaHud,SwingAnimator,FirstPersonSwing,HitFeel}.java`, mixins `client/mixin/{MinecraftAttackMixin,HumanoidModelMixin,FirstPersonHandsMixin}.java`,
  `tools/art/player_moves.py`, `versions/26.3/art/player_moves.bbmodel`, generated `PlayerMoves*`.
- Changed: `blood/LayerEnchants` (dash goes to `Dodge`), `blood/HellWeapons` (Blood Art on heavy, lore), `hazard/BossRules`
  (weapon guard counts like a shield), `entity/monster/SoulMonster` (`stagger`), `hazard/StyleMeter` (`onDodge`),
  `client/mixin/CameraMixin` (kick and roll dip), `client/DashKey`, `config/HellConfig`, `assets/hellcraft/sounds.json`,
  `tools/gen_models.py` (lore, names), `blood/Guide` and `blood/Lessons` (combat page and first-time tips),
  `HellcraftMod`/`HellcraftClient` registration, mixin json files.
- Docs: README "Combat" section, `.github/notes/beta.md`, CLAUDE.md code map.

## Verification
- `cd versions/26.3 && ./gradlew build compileGametestJava`; `python3 tools/gen_bosses.py` leaves the generated files in step.
- Server self-test `/hellcraft combat test` (moveset table sane, arc maths hits the right spots offline, stamina costs and
  regen, guard values), checked by `scripts/smoke-test.sh`; run `bash tools/dev/smokelocal.sh`.
- New client test `CombatTest` (added to the manifest and `tools/dev/test-shards.txt`, then `python3 tools/dev/testshard.py check`):
  zombies in a ring, a 3-hit sword combo hits only those in the arc; a roll through a zombie's blow takes no damage; a
  timed sword guard parries and staggers a Hell monster; an empty stamina bar breaks the guard; screenshots of every
  move at its hit frame in third person (front and side) and first person (`combat_<weapon>_<move>`), the stamina bar,
  and the trails and particles. `tools/art/render.py player_moves --anim <move>` while authoring. View every picture before pushing.
- `HudTest`, `BossTest` and `LuciferTest` still pass (bosses' parry still works with weapon guards and the new dodge).
- Push each stage; `bash tools/dev/waitci.sh <sha>`; beta refreshes. Stable only on "release it".
