package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;
import net.thesmallthings.hellcraft.entity.monster.Glutton;
import net.thesmallthings.hellcraft.entity.monster.SoulMonster;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings each upper circle's own monster out near the souls passing through (on top of the vanilla mobs of its
 * biome): the lovers over Lust, blown in on the wind; gluttons sunk in Gluttony's mud; packs of the wrathful in the
 * Fen. A few at a time, out of sight distance, never in a Virgil's Rest (layerMonsters turns it off). And now and then
 * a great coin of the Mint comes rolling across Greed, down a red line (Inferno VII: the misers and the wasters roll
 * their weights forever).
 */
public final class LayerMonsters {
	private LayerMonsters() {
	}

	private static final int HINT_COIN = 31;

	private record Kind(Zone zone, EntityType<? extends SoulMonster> type, int cap, int pack, String codex) {
	}

	private static final Kind[] KINDS = {
			new Kind(Zone.LUST, HellEntities.LOVERS, 3, 1, "lovers"),
			new Kind(Zone.GLUTTONY, HellEntities.GLUTTON, 5, 1, "glutton"),
			new Kind(Zone.STYX, HellEntities.WRATHFUL, 6, 3, "wrathful"),
	};

	/** A coin about to roll: where from, which way, and the tick it sets off. */
	private record Coin(Vec3 from, double angle, long at) {
	}

	private static final List<Coin> COINS = new ArrayList<>();
	private static final int COIN_WARN = 40;

	public static void tick(MinecraftServer server) {
		if (!COINS.isEmpty()) {
			ServerLevel level = server.overworld();
			COINS.removeIf(c -> {
				if (level.getGameTime() < c.at()) {
					return false;
				}
				RollingCoin coin = HellEntities.ROLLING_COIN.create(level, EntitySpawnReason.EVENT);
				if (coin != null && level.isLoaded(BlockPos.containing(c.from()))) {
					coin.snapTo(c.from().x, c.from().y, c.from().z, 0, 0);
					coin.launch(null, c.angle(), 110);
					level.addFreshEntity(coin);
				}
				return true;
			});
		}
		if (server.getTickCount() % 100 != 0 || !HellConfig.get().layerMonsters) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative()) {
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(p.getX(), p.getZ());
			if (zone == Zone.GREED && level.getRandom().nextFloat() < 0.08f && !Shrines.near(level, p.blockPosition())) {
				rollCoin(level, p);
			}
			for (Kind kind : KINDS) {
				if (kind.zone() != zone || level.getRandom().nextFloat() > 0.35f) {
					continue;
				}
				int near = level.getEntitiesOfClass(SoulMonster.class, new AABB(p.blockPosition()).inflate(48), m -> m.getType() == kind.type()).size();
				if (near < kind.cap()) {
					spawnNear(level, p, kind);
				}
			}
		}
	}

	private static void spawnNear(ServerLevel level, ServerPlayer p, Kind kind) {
		for (int tries = 0; tries < 6; tries++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 22 + level.getRandom().nextDouble() * 14;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (InfernoGeometry.zoneAt(x, z) != kind.zone() || !level.isLoaded(new BlockPos(x, 0, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (Shrines.near(level, at)) {
				continue;
			}
			boolean water = level.getFluidState(at.below()).is(FluidTags.WATER);
			if (kind.type() == HellEntities.GLUTTON && (water || !LayerRules.soft(level.getBlockState(at.below())))) {
				continue;
			}
			MapSync.unlockCodex(p, kind.codex());
			for (int i = 0; i < kind.pack(); i++) {
				Mob mob = spawn(level, kind, x + level.getRandom().nextInt(3) - 1, kind.type() == HellEntities.LOVERS ? y + 8 : y, z + level.getRandom().nextInt(3) - 1);
				if (mob instanceof Glutton glutton) {
					glutton.sink();
				}
			}
			return;
		}
	}

	/** A great coin rolls across near this soul, along a red line that warns of it two seconds ahead. */
	private static void rollCoin(ServerLevel level, ServerPlayer p) {
		double a = level.getRandom().nextDouble() * Math.PI * 2;
		double offset = (level.getRandom().nextDouble() - 0.5) * 10;
		double x = p.getX() - Math.cos(a) * 30 - Math.sin(a) * offset;
		double z = p.getZ() - Math.sin(a) * 30 + Math.cos(a) * offset;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		Vec3 from = new Vec3(x, y, z);
		Danger.line(level, Danger.Colour.RED, from, a, 60, 1.4, COIN_WARN);
		COINS.add(new Coin(from, a, level.getGameTime() + COIN_WARN));
		CircleHazards.hintOnce(p, HINT_COIN, "A great coin of the Mint is rolling down the red line: get off it! In Greed they roll forever.");
	}

	@Nullable
	private static Mob spawn(ServerLevel level, Kind kind, int x, int y, int z) {
		SoulMonster mob = kind.type().create(level, EntitySpawnReason.NATURAL);
		if (mob == null) {
			return null;
		}
		mob.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360, 0);
		if (!level.noCollision(mob)) {
			return null;
		}
		level.addFreshEntity(mob);
		return mob;
	}
}
