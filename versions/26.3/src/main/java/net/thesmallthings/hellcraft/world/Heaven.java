package net.thesmallthings.hellcraft.world;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Relics;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.util.Signs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happens in Paradiso (the terrain is Paradiso/ParadisoGeometry):
 * <ul>
 *     <li><b>The trial of the Seraph</b>: the Ender Dragon (named the Seraph by the resource pack) calls its
 *     Angelic Guard at half and at a quarter of its health; when it falls, everyone on the Threshold receives
 *     a Halo.</li>
 *     <li><b>The Celestial Rose</b>: a floating amphitheatre of light in the Empyrean. Ring its bell for
 *     Beatrice's Rose (once per soul).</li>
 *     <li><b>The Ascent</b>: a gateway on Purgatory's summit that raises those who have cast Lucifer down
 *     into heaven: the Threshold, or (once the Seraph has fallen) the Celestial Rose.</li>
 * </ul>
 */
public final class Heaven {
	private Heaven() {
	}

	public static final String ANGEL_TAG = "hellcraft_angel";
	private static final int ROSE_Y = 90;
	private static final int ROSE_RADIUS = 26;
	/** Half of the Seraph's health, then a quarter: when its Angelic Guard comes. */
	private static final Map<UUID, Integer> WAVES = new HashMap<>();
	private static int ticks;

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof EnderDragon && entity.level() instanceof ServerLevel level) {
				seraphFalls(level);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || level.dimension() != Level.END
					|| !hit.getBlockPos().equals(bell()) || !level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
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
			}
			return InteractionResult.PASS;
		});
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0) {
			return;
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) {
			return;
		}
		for (EnderDragon dragon : end.getDragons()) {
			if (!dragon.isAlive()) {
				continue;
			}
			end.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 2, dragon.getZ(), 12, 3, 2, 3, 0.02);
			float frac = dragon.getHealth() / dragon.getMaxHealth();
			int wave = frac < 0.25f ? 2 : frac < 0.5f ? 1 : 0;
			int done = WAVES.getOrDefault(dragon.getUUID(), 0);
			if (wave > done) {
				WAVES.put(dragon.getUUID(), wave);
				angels(end, dragon.blockPosition());
				end.getServer().getPlayerList().broadcastSystemMessage(Component.literal("The Seraph calls its Angelic Guard!")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
			}
		}
		if (!HellState.get(server).roseBuilt) {
			for (ServerPlayer p : end.players()) {
				double dx = p.getX() - ParadisoGeometry.ROSE_X;
				double dz = p.getZ() - ParadisoGeometry.ROSE_Z;
				if (dx * dx + dz * dz < 200 * 200) {
					buildRose(server);
					break;
				}
			}
		}
	}

	/** Two angels for every living soul near the fight. Returns how many came. */
	public static int angels(ServerLevel level, BlockPos near) {
		int n = 0;
		List<ServerPlayer> players = level.players().stream()
				.filter(p -> !p.isSpectator() && !p.isCreative() && p.blockPosition().distSqr(near) < 160 * 160).toList();
		int waves = Math.max(1, players.size());
		for (int i = 0; i < waves * 2; i++) {
			ServerPlayer target = players.isEmpty() ? null : players.get(i / 2);
			BlockPos at = target != null ? target.blockPosition() : near;
			var angel = EntityTypes.VEX.create(level, EntitySpawnReason.EVENT);
			if (angel == null) {
				continue;
			}
			angel.snapTo(at.getX() + (level.getRandom().nextDouble() - 0.5) * 8, at.getY() + 5, at.getZ() + (level.getRandom().nextDouble() - 0.5) * 8, 0, 0);
			angel.setCustomName(Component.literal("Angelic Guard").withStyle(ChatFormatting.GOLD));
			angel.setGlowingTag(true);
			angel.setLimitedLife(600);
			angel.addTag(ANGEL_TAG);
			level.addFreshEntity(angel);
			if (target != null) {
				angel.setTarget(target);
			}
			level.sendParticles(ParticleTypes.END_ROD, angel.getX(), angel.getY(), angel.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
			n++;
		}
		return n;
	}

	/** Everyone on the Threshold when the Seraph falls receives a Halo. */
	private static void seraphFalls(ServerLevel level) {
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.getX() * p.getX() + p.getZ() * p.getZ() < 250 * 250) {
				Relics.give(p, Relics.Relic.HALO);
				Journey.award(p, "journey/seraph");
				n++;
			}
		}
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("The Seraph is defeated!")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		HellcraftMod.LOGGER.info("The Seraph falls; halos for {}", n);
	}

	// ------------------------------------------------------------------ the Celestial Rose

	public static BlockPos bell() {
		return new BlockPos(ParadisoGeometry.ROSE_X, ROSE_Y + 2, ParadisoGeometry.ROSE_Z);
	}

	/** A floating amphitheatre of light: tiers of quartz and white, bands of petals, and a bell in the heart. */
	public static void buildRose(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.END);
		HellState state = HellState.get(server);
		if (level == null || state.roseBuilt) {
			return;
		}
		int cx = ParadisoGeometry.ROSE_X;
		int cz = ParadisoGeometry.ROSE_Z;
		for (int x = (cx - ROSE_RADIUS) >> 4; x <= (cx + ROSE_RADIUS) >> 4; x++) {
			for (int z = (cz - ROSE_RADIUS) >> 4; z <= (cz + ROSE_RADIUS) >> 4; z++) {
				level.getChunk(x, z);
			}
		}
		for (int dx = -ROSE_RADIUS; dx <= ROSE_RADIUS; dx++) {
			for (int dz = -ROSE_RADIUS; dz <= ROSE_RADIUS; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > ROSE_RADIUS) {
					continue;
				}
				int ring = (int) (r / 3);
				int y = ROSE_Y + ring / 2;
				BlockState top = ring == 0 ? Blocks.QUARTZ_BLOCK.defaultBlockState()
						: ring % 2 == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
						: (dx + dz) % 5 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState();
				int x = cx + dx;
				int z = cz + dz;
				level.setBlock(new BlockPos(x, y, z), top, 2);
				for (int d = 1; d <= 2 + (int) ((ROSE_RADIUS - r) / 4); d++) {
					level.setBlock(new BlockPos(x, y - d, z), Blocks.QUARTZ_BLOCK.defaultBlockState(), 2);
				}
				for (int up = 1; up <= 6; up++) {
					level.setBlock(new BlockPos(x, y + up, z), Blocks.AIR.defaultBlockState(), 2);
				}
				if (ring % 2 == 0 && ring > 0) {
					level.setBlock(new BlockPos(x, y + 1, z), Blocks.PINK_PETALS.defaultBlockState(), 2);
				}
			}
		}
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			int x = cx + (int) Math.round(Math.cos(a) * (ROSE_RADIUS - 2));
			int z = cz + (int) Math.round(Math.sin(a) * (ROSE_RADIUS - 2));
			int y = ROSE_Y + ((ROSE_RADIUS - 2) / 3) / 2;
			for (int up = 1; up <= 5; up++) {
				level.setBlock(new BlockPos(x, y + up, z), Blocks.END_ROD.defaultBlockState(), 2);
			}
		}
		level.setBlock(bell().below(), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
		level.setBlock(bell(), Blocks.BELL.defaultBlockState(), 3);
		Signs.place(level, bell().offset(0, -1, 2), 0, List.of(
				Component.literal("THE CELESTIAL").withStyle(ChatFormatting.BOLD),
				Component.literal("ROSE").withStyle(ChatFormatting.BOLD),
				Component.literal("Ring the bell for"),
				Component.literal("Beatrice's Rose")));
		state.roseBuilt = true;
		state.setDirty();
		HellcraftMod.LOGGER.info("The Celestial Rose blooms at {} {} {}", cx, ROSE_Y, cz);
	}

	// ------------------------------------------------------------------ the Ascent

	public static BlockPos ascent() {
		return new BlockPos(0, Purgatory.SUMMIT_Y + 1, 8);
	}

	/** A second gateway on Purgatory's summit, for those who have cast Lucifer down. */
	public static void buildAscent(MinecraftServer server) {
		HellState state = HellState.get(server);
		ServerLevel level = server.overworld();
		if (state.ascentBuilt || !state.purgatoryBuilt || !HellWorldgen.isInferno(level)) {
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

	/** Steps into the Ascent: up to heaven, if Lucifer has fallen to this soul. */
	public static void ascend(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		if (!soul.slewLucifer) {
			player.sendOverlayMessage(Component.literal("Only players who have beaten Lucifer can go up.").withStyle(ChatFormatting.GOLD));
			player.push(0, 0.4, -0.8);
			net.thesmallthings.hellcraft.util.Feedback.syncMotion(player);
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) {
			return;
		}
		BlockPos to;
		if (end.getDragonFight() != null && end.getDragonFight().hasPreviouslyKilledDragon()) {
			buildRose(server);
			to = bell().offset(0, -1, 4);
		} else {
			// the obsidian platform at the edge of the Seraph's island, as the portal makes it
			BlockPos p = new BlockPos(100, 49, 0);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					end.setBlock(p.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState(), 3);
					for (int y = 0; y <= 2; y++) {
						end.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
			to = p;
		}
		Ghosts.teleport(player, new HellState.GlobalSpot(Level.END, to));
		end.playSound(null, to, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		player.sendSystemMessage(Component.literal("You rise into Paradiso (the End).")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}
}
