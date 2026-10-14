package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

/**
 * Fair play between players (the plan's F1-F3):
 * <ul>
 *     <li><b>Sanctuary.</b> No fighting other players in the Dark Wood, on the Shore or in Limbo
 *     (sanctuaryNoPvp).</li>
 *     <li><b>Newcomer grace.</b> For a new soul's first hours of play (newcomerGraceMinutes) other players can't
 *     take its hearts, and everyone can see it: "(new)" after its name. Attacking a player ends it early.</li>
 *     <li><b>Cain's Mark.</b> Killing a soul with less than half your hearts takes nothing from it, and marks you:
 *     you glow, and whoever kills you while it lasts (cainMarkMinutes) is paid Blood Hearts (cainBountyHearts).</li>
 * </ul>
 */
public final class Fairness {
	private Fairness() {
	}

	public static final String NEWCOMER_TEAM = "hellcraft_newcomer";

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim) || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
				return true;
			}
			if (HellConfig.get().sanctuaryNoPvp && (inSanctuary(victim) || inSanctuary(attacker))) {
				attacker.sendOverlayMessage(Component.literal("No fighting other players in the Dark Wood, on the Shore or in Limbo.")
						.withStyle(ChatFormatting.AQUA));
				return false;
			}
			HellState.Soul soul = Hearts.soul(attacker);
			if (soul.graceTicks > 0) {
				soul.graceTicks = 0;
				HellState.get(attacker.level().getServer()).setDirty();
				attacker.sendSystemMessage(Component.literal("You attacked a player, so your newcomer protection has ended: other players can take your hearts now.")
						.withStyle(ChatFormatting.GOLD));
				updateTeam(attacker, false);
			}
			return true;
		});
	}

	/** A new soul's protection starts (first join only). */
	public static void welcome(ServerPlayer player, HellState.Soul soul) {
		int minutes = HellConfig.get().newcomerGraceMinutes;
		if (minutes <= 0) {
			return;
		}
		soul.graceTicks = minutes * 1200;
		HellState.get(player.level().getServer()).setDirty();
		player.sendSystemMessage(Component.literal("For your first " + hours(minutes) + " of play, other players can't take your hearts "
				+ "(\"new\" shows after your name). Attack a player and the protection ends.").withStyle(ChatFormatting.GOLD));
		updateTeam(player, true);
	}

	/** Once a second: newcomer time runs down while online, and Cain's Mark keeps its marked glowing until it fades. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		HellState state = HellState.get(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			HellState.Soul soul = state.existing(p.getUUID());
			if (soul == null) {
				continue;
			}
			if (soul.graceTicks > 0) {
				soul.graceTicks = Math.max(0, soul.graceTicks - 20);
				state.setDirty();
				if (soul.graceTicks == 0) {
					p.sendSystemMessage(Component.literal("Your newcomer protection has ended: from now on, other players can take your hearts.")
							.withStyle(ChatFormatting.GOLD));
				}
			}
			updateTeam(p, soul.graceTicks > 0);
			if (soul.cainUntil > now) {
				p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
			} else if (soul.cainUntil != 0) {
				soul.cainUntil = 0;
				state.setDirty();
				p.sendSystemMessage(Component.literal("Cain's Mark has faded.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	/** Where players can't fight each other: the Dark Wood, the Shore (Vestibule and Acheron) and Limbo. */
	public static boolean inSanctuary(ServerPlayer p) {
		if (p.level().dimension() != Level.OVERWORLD || !HellWorldgen.isInferno(p.level())) {
			return false;
		}
		return sanctuary(InfernoGeometry.circleAt(p.getX(), p.getZ()));
	}

	static boolean sanctuary(Circle circle) {
		return circle == Circle.DARK_WOOD || circle == Circle.VESTIBULE || circle == Circle.LIMBO;
	}

	/** Killing a soul with less than half your hearts. */
	static boolean bullying(int victimHearts, int killerHearts) {
		return victimHearts * 2 < killerHearts;
	}

	/**
	 * Whether this death costs the dead their heart: never during newcomer grace, never when the killer had more than
	 * twice their hearts, and for deaths to monsters only if the server says so.
	 */
	public static boolean heartAtStake(ServerPlayer dead, @Nullable ServerPlayer killer) {
		HellState.Soul soul = Hearts.soul(dead);
		if (soul.graceTicks > 0) {
			return false;
		}
		if (killer == null) {
			return HellConfig.get().pveDeathsCostHearts;
		}
		return !bullying(soul.hearts, Hearts.soul(killer).hearts);
	}

	/**
	 * A player killed a player: explains why no heart changed hands (grace, or Cain's Mark for bullying), and pays
	 * whoever killed a marked soul. Returns true if the heart changes hands as usual.
	 */
	public static boolean onKill(ServerPlayer dead, ServerPlayer killer) {
		MinecraftServer server = dead.level().getServer();
		HellState state = HellState.get(server);
		HellState.Soul deadSoul = Hearts.soul(dead);
		HellState.Soul killerSoul = Hearts.soul(killer);
		long now = server.overworld().getGameTime();
		if (deadSoul.cainUntil > now) {
			deadSoul.cainUntil = 0;
			state.setDirty();
			int reward = HellConfig.get().cainBountyHearts;
			BloodItems.give(killer, BloodItems.heart(reward));
			server.getPlayerList().broadcastSystemMessage(Component.literal(killer.getGameProfile().name() + " struck down " + dead.getGameProfile().name()
					+ ", who bore Cain's Mark (" + reward + " Blood Hearts).").withStyle(ChatFormatting.GOLD), false);
		}
		if (deadSoul.graceTicks > 0) {
			dead.sendSystemMessage(Component.literal("You're new here, so you kept your hearts (newcomer protection: " + minutesLeft(deadSoul) + " min left).")
					.withStyle(ChatFormatting.GOLD));
			killer.sendSystemMessage(Component.literal(dead.getGameProfile().name() + " is new here and protected: you took no heart.")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		if (bullying(deadSoul.hearts, killerSoul.hearts)) {
			int minutes = HellConfig.get().cainMarkMinutes;
			killerSoul.cainUntil = now + minutes * 1200L;
			state.setDirty();
			killer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
			dead.sendSystemMessage(Component.literal("Your killer had more than twice your hearts, so you kept yours.").withStyle(ChatFormatting.GOLD));
			killer.sendSystemMessage(Component.literal("Killing a soul with less than half your hearts takes nothing, and marks you: Cain's Mark. For "
					+ minutes + " min you glow, and whoever kills you gets " + HellConfig.get().cainBountyHearts + " Blood Hearts.")
					.withStyle(ChatFormatting.DARK_RED));
			server.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + killer.getGameProfile().name() + " killed a far weaker soul and bears Cain's Mark: ")
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
					.append(Component.literal("they glow for " + minutes + " min, and whoever kills them gets " + HellConfig.get().cainBountyHearts
							+ " Blood Hearts.").withStyle(ChatFormatting.RED)), false);
			HellcraftMod.LOGGER.info("Cain's Mark on {} for killing {}", killer.getGameProfile().name(), dead.getGameProfile().name());
			return false;
		}
		return true;
	}

	public static int minutesLeft(HellState.Soul soul) {
		return (soul.graceTicks + 1199) / 1200;
	}

	/** "(new)" after a protected newcomer's name, in the tab list and over their head (unless they're on another team). */
	private static void updateTeam(ServerPlayer player, boolean newcomer) {
		Scoreboard board = player.level().getServer().getScoreboard();
		String name = player.getScoreboardName();
		PlayerTeam current = board.getPlayersTeam(name);
		if (newcomer) {
			if (current != null) {
				return;
			}
			PlayerTeam team = board.getPlayerTeam(NEWCOMER_TEAM);
			if (team == null) {
				team = board.addPlayerTeam(NEWCOMER_TEAM);
				team.setPlayerSuffix(Component.literal(" (new)").withStyle(ChatFormatting.GREEN));
			}
			board.addPlayerToTeam(name, team);
		} else if (current != null && current.getName().equals(NEWCOMER_TEAM)) {
			board.removePlayerFromTeam(name, current);
		}
	}

	private static String hours(int minutes) {
		return minutes % 60 == 0 ? (minutes / 60) + (minutes == 60 ? " hour" : " hours") : minutes + " minutes";
	}

	/** For the smoke test: the sanctuary circles and the half-your-hearts rule. */
	public static String selfTest() {
		return "Fairness test: sanctuary dark_wood=" + sanctuary(Circle.DARK_WOOD) + " limbo=" + sanctuary(Circle.LIMBO)
				+ " lust=" + sanctuary(Circle.LUST) + "; 10 vs 25 bullying=" + bullying(10, 25) + ", 15 vs 25 bullying=" + bullying(15, 25);
	}
}
