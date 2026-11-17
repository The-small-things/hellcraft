package net.thesmallthings.hellcraft.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How each kind of weapon swings: a chain of light moves (click again as one ends and the next follows) and a heavy one
 * (hold the attack key). A move is an animation of the player rig (tools/art/player_moves.py), whose own events say when
 * the blow lands ("hit") and when the next move may begin ("next"), plus what the blow is: its share of the weapon's
 * damage, the arc it sweeps (everything in it is struck), its reach beyond the usual, its shove, and how far the player
 * is carried forward as it lands.
 *
 * <p>Moves are timed for the weapon's usual attack speed (a sword's 1.6, an axe's 1.0, a mace's 0.6) and play faster or
 * slower with the player's own (Haste, a slower axe): a chain deals about what vanilla's clicking would, a little more
 * for keeping it going.
 */
public final class Moveset {
	/** One move of a weapon. */
	public record Move(PlayerMovesAnim anim, int index, boolean heavy, float damage, float halfAngle, float pitch, float reach, float knockback, float lunge,
					   float pull, float shock) {
		/** A plain move: no pull, no shockwave. */
		public Move(PlayerMovesAnim anim, int index, boolean heavy, float damage, float halfAngle, float pitch, float reach, float knockback, float lunge) {
			this(anim, index, heavy, damage, halfAngle, pitch, reach, knockback, lunge, 0, 0);
		}

		/** The same move, dragging what it strikes towards the player (the Reaper's turn). */
		public Move pulling(float strength) {
			return new Move(anim, index, heavy, damage, halfAngle, pitch, reach, knockback, lunge, strength, shock);
		}

		/** The same move, with a shockwave this wide where it lands (the Tithe Axe's slam). */
		public Move shaking(float radius) {
			return new Move(anim, index, heavy, damage, halfAngle, pitch, reach, knockback, lunge, pull, radius);
		}

		/** The last light move of its chain (the Bloodletter's bleeds deepest). */
		public boolean finisher(Moveset set) {
			return !heavy && index == set.light.size() - 1;
		}

		/** The tick of the animation at which the blow lands. */
		public int hitTick() {
			return anim.at("hit");
		}

		/** The tick from which the next move of the chain may begin. */
		public int nextTick() {
			return anim.at("next");
		}

		/** The tick the wind-up ends and the weapon starts forward. */
		public int swingTick() {
			return anim.at("swing");
		}

		public int ticks() {
			return anim.ticks;
		}
	}

	public final List<Move> light;
	public final Move heavy;
	/** The attack speed the moves are timed for. */
	public final float speed;

	private Moveset(float speed, Move heavy, Move... light) {
		this.speed = speed;
		this.heavy = heavy;
		this.light = List.of(light);
	}

	private static Move light(PlayerMovesAnim anim, int index, float damage, float halfAngle, float pitch, float reach, float knockback, float lunge) {
		return new Move(anim, index, false, damage, halfAngle, pitch, reach, knockback, lunge);
	}

	private static Move heavy(PlayerMovesAnim anim, float damage, float halfAngle, float pitch, float reach, float knockback, float lunge) {
		return new Move(anim, -1, true, damage, halfAngle, pitch, reach, knockback, lunge);
	}

	private static final Map<WeaponClass, Moveset> SETS = new EnumMap<>(WeaponClass.class);

	static {
		Moveset sword = new Moveset(1.6f,
				heavy(PlayerMovesAnim.SWORD_HEAVY, 1.9f, 38, 60, 0.4f, 0.6f, 0.30f),
				light(PlayerMovesAnim.SWORD_1, 0, 0.8f, 62, 45, 0.0f, 0.0f, 0.10f),
				light(PlayerMovesAnim.SWORD_2, 1, 0.8f, 66, 40, 0.0f, 0.0f, 0.10f),
				light(PlayerMovesAnim.SWORD_3, 2, 1.25f, 24, 30, 0.7f, 0.3f, 0.34f));
		Moveset axe = new Moveset(1.0f,
				heavy(PlayerMovesAnim.AXE_HEAVY, 1.9f, 40, 65, 0.3f, 0.9f, 0.26f),
				light(PlayerMovesAnim.AXE_1, 0, 0.95f, 58, 50, 0.1f, 0.2f, 0.12f),
				light(PlayerMovesAnim.AXE_2, 1, 1.1f, 58, 55, 0.1f, 0.45f, 0.12f));
		Moveset mace = new Moveset(0.6f,
				heavy(PlayerMovesAnim.MACE_HEAVY, 1.8f, 44, 70, 0.3f, 1.2f, 0.22f),
				light(PlayerMovesAnim.MACE_1, 0, 1.0f, 40, 65, 0.1f, 0.5f, 0.14f),
				light(PlayerMovesAnim.MACE_2, 1, 1.0f, 70, 40, 0.2f, 0.6f, 0.10f));
		SETS.put(WeaponClass.SWORD, sword);
		SETS.put(WeaponClass.AXE, axe);
		SETS.put(WeaponClass.MACE, mace);
		// the hell weapons' own. The Bloodletter: four quick cuts, the third a spin that catches everything round you, the
		// last a deep thrust; its heavy blow a long lunge
		SETS.put(WeaponClass.BLOODLETTER, new Moveset(1.6f,
				heavy(PlayerMovesAnim.BLOOD_HEAVY, 1.9f, 22, 35, 1.2f, 0.5f, 0.62f),
				light(PlayerMovesAnim.BLOOD_1, 0, 0.75f, 62, 45, 0.0f, 0.0f, 0.10f),
				light(PlayerMovesAnim.BLOOD_2, 1, 0.75f, 66, 40, 0.0f, 0.0f, 0.10f),
				light(PlayerMovesAnim.BLOOD_3, 2, 0.85f, 180, 40, 0.2f, 0.2f, 0.0f),
				light(PlayerMovesAnim.BLOOD_4, 3, 1.3f, 24, 30, 0.8f, 0.3f, 0.38f)));
		// the Reaper of Minos: two great sweeps and a full reaping turn that drags the harvest in
		SETS.put(WeaponClass.REAPER, new Moveset(1.0f,
				heavy(PlayerMovesAnim.REAPER_HEAVY, 1.9f, 46, 65, 0.9f, 0.8f, 0.24f),
				light(PlayerMovesAnim.REAPER_1, 0, 0.95f, 85, 45, 0.6f, 0.2f, 0.10f),
				light(PlayerMovesAnim.REAPER_2, 1, 1.0f, 78, 55, 0.6f, 0.3f, 0.12f),
				light(PlayerMovesAnim.REAPER_3, 2, 1.15f, 180, 45, 0.8f, 0.0f, 0.0f).pulling(0.55f)));
		// the Tithe Axe: a chop, an upward rip, and a slam that shakes the ground
		SETS.put(WeaponClass.TITHE_AXE, new Moveset(1.0f,
				heavy(PlayerMovesAnim.TITHE_HEAVY, 2.0f, 40, 65, 0.3f, 1.0f, 0.26f).shaking(3.5f),
				light(PlayerMovesAnim.TITHE_1, 0, 0.95f, 58, 50, 0.1f, 0.25f, 0.12f),
				light(PlayerMovesAnim.TITHE_2, 1, 1.15f, 58, 55, 0.1f, 0.5f, 0.12f)));
	}

	/** What a move is called on the style panel. */
	public static String label(PlayerMovesAnim anim) {
		return switch (anim) {
			case SWORD_1, BLOOD_1 -> "CUT";
			case SWORD_2, BLOOD_2 -> "BACKCUT";
			case SWORD_3, BLOOD_4 -> "THRUST";
			case BLOOD_3 -> "SPIN";
			case AXE_1, TITHE_1 -> "CHOP";
			case AXE_2, TITHE_2 -> "RIP";
			case MACE_1 -> "POUND";
			case MACE_2 -> "SWING";
			case REAPER_1, REAPER_2 -> "REAP";
			case REAPER_3 -> "HARVEST";
			case SWORD_HEAVY, AXE_HEAVY, MACE_HEAVY, REAPER_HEAVY -> "CLEAVE";
			case TITHE_HEAVY -> "SLAM";
			case BLOOD_HEAVY -> "LUNGE";
			default -> anim.name().replace('_', ' ');
		};
	}

	@Nullable
	public static Moveset of(@Nullable WeaponClass kind) {
		return kind == null ? null : SETS.get(kind);
	}

	@Nullable
	public static Moveset of(ItemStack stack) {
		return of(WeaponClass.of(stack));
	}

	/** The move of this set with that animation, or null. */
	@Nullable
	public Move find(int animOrdinal) {
		if (heavy.anim.ordinal() == animOrdinal) {
			return heavy;
		}
		for (Move move : light) {
			if (move.anim.ordinal() == animOrdinal) {
				return move;
			}
		}
		return null;
	}

	/** The light move after this one in the chain (the first, after the last or after a heavy). */
	public Move after(@Nullable Move move) {
		if (move == null || move.heavy) {
			return light.get(0);
		}
		return light.get((move.index + 1) % light.size());
	}

	/** How fast this player's moves play: their attack speed against the one the moves are timed for. */
	public float pace(Player player) {
		return Mth.clamp((float) player.getAttributeValue(Attributes.ATTACK_SPEED) / speed, 0.6f, 1.6f);
	}

	static String selfTest() {
		for (Map.Entry<WeaponClass, Moveset> e : SETS.entrySet()) {
			Moveset set = e.getValue();
			for (Move move : set.light) {
				String problem = check(e.getKey(), move);
				if (problem != null) {
					return problem;
				}
				if (set.light.get(move.index) != move) {
					return e.getKey() + ": " + move.anim + " is out of place in its chain";
				}
			}
			String problem = check(e.getKey(), set.heavy);
			if (problem != null) {
				return problem;
			}
			if (set.after(set.light.get(set.light.size() - 1)) != set.light.get(0) || set.after(set.heavy) != set.light.get(0)) {
				return e.getKey() + ": the chain doesn't come round";
			}
		}
		return "";
	}

	private static String check(WeaponClass kind, Move move) {
		if (!(move.swingTick() < move.hitTick() && move.hitTick() < move.nextTick() && move.nextTick() <= move.ticks())) {
			return kind + ": " + move.anim + " must wind up, land, then free the next move, in that order";
		}
		// a held key turns a light move's wind-up into the heavy: there must be time before its blow lands
		if (!move.heavy && move.hitTick() < Swing.CHARGE_AT + 2) {
			return kind + ": " + move.anim + " lands too soon for a heavy to take over";
		}
		if (move.damage <= 0 || move.halfAngle <= 0 || move.halfAngle > 180) {
			return kind + ": " + move.anim + " has no blow";
		}
		return null;
	}
}
