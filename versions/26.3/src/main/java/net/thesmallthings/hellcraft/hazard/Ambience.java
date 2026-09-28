package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each circle sounds and looks like itself: the wasps of the Vestibule, Charon's oar on the Acheron,
 * the sighs of Limbo, the hurricane of Lust, the rain of Gluttony, the chink of gold in Greed, the
 * bubbling Styx, the burning tombs, the boiling blood, the far screams of Malebolge and the creaking
 * ice of Cocytus. Every few seconds each player hears a sound from somewhere near them and sees a
 * little drift of the circle's particles.
 */
public final class Ambience {
	private Ambience() {
	}

	private static final DustParticleOptions GOLD = new DustParticleOptions(0xE8B923, 0.9f);
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private record Mood(Holder<SoundEvent> sound, float volume, float pitch, @Nullable ParticleOptions particle, int count) {
	}

	private static Holder<SoundEvent> h(SoundEvent sound) {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
	}

	@Nullable
	private static Mood mood(Zone zone) {
		return switch (zone) {
			case VESTIBULE -> new Mood(h(SoundEvents.BEE_LOOP_AGGRESSIVE), 0.35f, 0.8f, ParticleTypes.SMOKE, 6);
			case ACHERON -> new Mood(h(SoundEvents.BOAT_PADDLE_WATER), 0.8f, 0.6f, ParticleTypes.SPLASH, 12);
			case LIMBO -> new Mood(h(SoundEvents.SOUL_ESCAPE.value()), 0.9f, 0.7f, ParticleTypes.WHITE_ASH, 20);
			case LUST -> new Mood(h(SoundEvents.ELYTRA_FLYING), 0.3f, 0.6f, ParticleTypes.CLOUD, 8);
			case GLUTTONY -> new Mood(h(SoundEvents.WEATHER_RAIN), 0.5f, 0.8f, ParticleTypes.FALLING_WATER, 30);
			case GREED -> new Mood(SoundEvents.ARMOR_EQUIP_GOLD, 0.5f, 0.7f, GOLD, 10);
			case STYX, WALLS_OF_DIS -> new Mood(h(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT), 0.6f, 0.6f, ParticleTypes.SPLASH, 10);
			case HERESY -> new Mood(h(SoundEvents.FIRE_AMBIENT), 1.0f, 0.7f, ParticleTypes.SMOKE, 12);
			case PHLEGETHON, WOOD_OF_SUICIDES, BURNING_SANDS -> new Mood(h(SoundEvents.LAVA_POP), 0.8f, 0.6f, ParticleTypes.DRIPPING_LAVA, 8);
			case MALEBOLGE, MALEBOLGE_PITCH, MALEBOLGE_BLIGHT, WELL_OF_GIANTS -> new Mood(h(SoundEvents.GHAST_AMBIENT), 0.25f, 0.5f, ParticleTypes.ASH, 20);
			case COCYTUS, JUDECCA -> new Mood(h(SoundEvents.POWDER_SNOW_BREAK), 0.6f, 0.5f, ParticleTypes.SNOWFLAKE, 16);
			default -> null;
		};
	}

	public static void tick(MinecraftServer server) {
		if (!HellConfig.get().circleAmbience) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (now < NEXT.getOrDefault(player.getUUID(), 0L)) {
				continue;
			}
			NEXT.put(player.getUUID(), now + 40 + random.nextInt(40));
			if (Spine.shelters(player)) {
				// the spines have their own heartbeat
				continue;
			}
			double r2 = player.getX() * player.getX() + player.getZ() * player.getZ();
			if (LuciferManager.fighting() && r2 < 120 * 120) {
				// the fight has its own music
				continue;
			}
			Mood mood = mood(InfernoGeometry.zoneAt(player.getX(), player.getZ()));
			if (mood == null) {
				continue;
			}
			double a = random.nextDouble() * Math.PI * 2;
			double d = 4 + random.nextDouble() * 8;
			Feedback.soundAt(player, mood.sound(), SoundSource.AMBIENT, player.getX() + Math.cos(a) * d, player.getY() + 1,
					player.getZ() + Math.sin(a) * d, mood.volume(), mood.pitch() + (random.nextFloat() - 0.5f) * 0.2f);
			if (mood.particle() != null) {
				level.sendParticles(mood.particle(), player.getX(), player.getY() + 2.5, player.getZ(), mood.count(), 6, 2.5, 6, 0.01);
			}
		}
	}

	public static void forget(ServerPlayer player) {
		NEXT.remove(player.getUUID());
	}
}
