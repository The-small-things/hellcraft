package net.thesmallthings.hellcraft.world;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Relics;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.npc.Angel;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.util.Signs;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;
import net.thesmallthings.hellcraft.world.feature.ParadisoIslandFeature;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Heaven's pieces outside the climb itself (Paradiso):
 * <ul>
 *     <li><b>The Celestial Rose</b>: the bowl of light atop Paradiso, in the Empyrean (ParadisoIslandFeature builds it).
 *     Ring its bell for Beatrice's Rose (once per soul).</li>
 *     <li><b>The Ascent</b>: a gateway on Purgatory's summit that raises those who have cast Lucifer down into
 *     Paradiso, onto the Moon.</li>
 *     <li><b>The Angelic Guard</b>: angels that test you (Mars's trial, the Seraph's fight).</li>
 * </ul>
 */
public final class Heaven {
	private Heaven() {
	}

	public static final String ANGEL_TAG = "hellcraft_angel";

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || level.dimension() != Paradiso.LEVEL
					|| !hit.getBlockPos().equals(Paradiso.bell()) || !level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}
			HellState.Soul soul = Hearts.soul(sp);
			if (soul.rose) {
				sp.sendOverlayMessage(Component.literal("Beatrice has already given you her rose.").withStyle(ChatFormatting.LIGHT_PURPLE));
			} else {
				soul.rose = true;
				HellState.get(sp.level().getServer()).setDirty();
				Relics.give(sp, Relics.Relic.BEATRICES_ROSE);
				Journey.award(sp, "journey/beatrice");
				Dialogue.notice(sp, Component.literal("Beatrice gave you her rose. You have climbed all of Hell, Purgatory and Paradiso. "
						+ "The gate on the arrival isle goes home to the Gate of Hell.").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return InteractionResult.PASS;
		});
	}

	/** Two angels for every living soul near a place, for a minute. Returns how many came. */
	public static int angels(ServerLevel level, BlockPos near) {
		return angels(level, near, 2, null);
	}

	/** {@code each} angels of the Guard for every living soul near a place, tagged (if a tag is given) so a trial can count them. */
	public static int angels(ServerLevel level, BlockPos near, int each, String tag) {
		int n = 0;
		List<ServerPlayer> players = level.players().stream()
				.filter(p -> !p.isSpectator() && !p.isCreative() && p.blockPosition().distSqr(near) < 48 * 48).toList();
		int waves = Math.max(1, players.size());
		for (int i = 0; i < waves * each; i++) {
			ServerPlayer target = players.isEmpty() ? null : players.get(i / each);
			BlockPos at = target != null ? target.blockPosition() : near;
			Angel angel = HellEntities.ANGEL.create(level, EntitySpawnReason.EVENT);
			if (angel == null) {
				continue;
			}
			angel.snapTo(at.getX() + (level.getRandom().nextDouble() - 0.5) * 8, at.getY() + 5, at.getZ() + (level.getRandom().nextDouble() - 0.5) * 8, 0, 0);
			angel.guard();
			angel.setCustomName(Component.literal("Angelic Guard").withStyle(ChatFormatting.GOLD));
			angel.addTag(ANGEL_TAG);
			if (tag != null) {
				angel.addTag(tag);
			}
			level.addFreshEntity(angel);
			if (target != null) {
				angel.setTarget(target);
			}
			level.sendParticles(ParticleTypes.END_ROD, angel.getX(), angel.getY(), angel.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
			n++;
		}
		return n;
	}

	// ------------------------------------------------------------------ the host

	/** Angels wheeling round each sphere's great island, and more round the Celestial Rose, flying in and out of it. */
	public static final int HOST_EACH = 6;
	public static final int ROSE_SWARM = 14;

	/**
	 * Keeps the heavens full of angels: round every sphere a soul is in, its host circles the great island at different
	 * heights, and in the Empyrean a swarm flies in and out of the Rose. Run every couple of seconds; the angels go when
	 * nobody is near (they despawn like any mob far from players). Returns how many came.
	 */
	public static int host(ServerLevel level) {
		Set<Sphere> occupied = EnumSet.noneOf(Sphere.class);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				occupied.add(ParadisoGeometry.sphereAtY(p.getY()));
			}
		}
		return host(level, occupied, false);
	}

	/** Fills these spheres' hosts (load: load each sphere's middle first, for /hellcraft paradiso host). */
	public static int host(ServerLevel level, Set<Sphere> spheres, boolean load) {
		int n = 0;
		for (Sphere s : spheres) {
			double[] c = ParadisoGeometry.centre(s);
			int top = ParadisoGeometry.topY(s);
			if (load) {
				level.getChunk((int) Math.floor(c[0]) >> 4, (int) Math.floor(c[1]) >> 4);
			}
			if (!level.isLoaded(BlockPos.containing(c[0], top, c[1]))) {
				continue;
			}
			AABB around = new AABB(c[0] - 96, top - 40, c[1] - 96, c[0] + 96, top + 64, c[1] + 96);
			int there = level.getEntitiesOfClass(Angel.class, around, a -> !a.isGuard()).size();
			int want = HOST_EACH + (s.last() ? ROSE_SWARM : 0);
			for (int k = there; k < want; k++) {
				Angel angel = HellEntities.ANGEL.create(level, EntitySpawnReason.EVENT);
				if (angel == null) {
					continue;
				}
				var random = level.getRandom();
				double phase = random.nextDouble() * Math.PI * 2;
				double dir = random.nextBoolean() ? 1 : -1;
				if (s.last() && k >= HOST_EACH) {
					// the swarm, in and out of the Rose like bees
					angel.circle(c[0], top + 4 + random.nextDouble() * 10, c[1], ParadisoIslandFeature.ROSE_RADIUS - 2, 3,
							dir * (0.010 + random.nextDouble() * 0.006), phase, true);
				} else {
					angel.circle(c[0], top + 6 + random.nextDouble() * 18, c[1], ParadisoGeometry.centreRadius(s) + 6 + random.nextDouble() * 16,
							2 + random.nextDouble() * 3, dir * (0.004 + random.nextDouble() * 0.004), phase, false);
				}
				level.addFreshEntity(angel);
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ the Ascent

	public static BlockPos ascent() {
		return new BlockPos(0, Purgatory.SUMMIT_Y + 1, 8);
	}

	/** A second gateway on Purgatory's summit, for those who have cast Lucifer down. */
	public static void buildAscent(MinecraftServer server) {
		HellState state = HellState.get(server);
		ServerLevel level = Purgatory.level(server);
		if (level == null || state.ascentBuilt || !state.purgatoryIsland) {
			return;
		}
		BlockPos gate = ascent();
		level.getChunk(gate.getX() >> 4, gate.getZ() >> 4);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(gate.offset(x, -1, z), Blocks.QUARTZ_BLOCK.defaultBlockState(), 2);
				level.setBlock(gate.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		level.setBlock(gate, Blocks.END_GATEWAY.defaultBlockState(), 3);
		Signs.place(level, gate.offset(0, 0, -2), 8, List.of(
				Component.literal("THE ASCENT").withStyle(ChatFormatting.BOLD),
				Component.literal("to Paradiso, for"),
				Component.literal("those who have"),
				Component.literal("beaten Lucifer")));
		state.ascentBuilt = true;
		state.setDirty();
		HellcraftMod.LOGGER.info("The Ascent opens on the summit at {}", gate.toShortString());
	}

	/** Steps into the Ascent: up to Paradiso, if Lucifer has fallen to this soul. */
	public static void ascend(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		if (!soul.slewLucifer) {
			player.sendOverlayMessage(Component.literal("Only players who have beaten Lucifer can go up.").withStyle(ChatFormatting.GOLD));
			player.push(0, 0.4, -0.8);
			net.thesmallthings.hellcraft.util.Feedback.syncMotion(player);
			return;
		}
		Paradiso.enter(player);
	}
}
