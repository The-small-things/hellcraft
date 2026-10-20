package net.thesmallthings.hellcraft.hazard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.LayerEnchants;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.monster.Heretic;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Champions of the circles: now and then a named, stronger soul of a circle walks near the pilgrims passing through
 * (roughly one per circle every 15-25 minutes that someone spends there, at most one alive per circle), something for
 * a server to hunt once its bosses are beaten. Each is its circle's own monster, named for someone Dante meets there,
 * with one to three affixes in its name ("Filippo Argenti, Burning and Swift"). It glows, has a boss bar near it, and
 * the whole server hears when one walks and when it falls; whoever dealt it the most damage takes its Blood Fragments,
 * maybe its circle's enchanted book, and a heap of experience. Never in a lair, a Rest, Lucifer's pit or near the
 * spawn. Tunables: champions, championMinMinutes, championMaxMinutes, championHealth, championDamage,
 * championFragments, championBookChance.
 * <p>
 * The champion's own state lives in its tags (TAG, its circle, its name, its affixes), so it survives a restart.
 */
public final class Champions {
	private Champions() {
	}

	public static final String TAG = "hellcraft_champion";
	private static final String CIRCLE_TAG = TAG + "_circle_";
	private static final String NAME_TAG = TAG + "_name_";
	private static final String AFFIX_TAG = TAG + "_affix_";
	private static final String ECHO_TAG = TAG + "_echo";
	/** MobEmpowerment's mark: champions are scaled here, not there. */
	private static final String TOUCHED = "hellcraft_touched";

	/** What makes a champion more than its kind: each simple, each with a tell. */
	public enum Affix {
		SWIFT("Swift", "it moves fast"),
		HARDENED("Hardened", "it takes less damage"),
		BURNING("Burning", "its blows set you alight, and it leaves fire behind"),
		FROZEN("Frozen", "its blows freeze and slow you"),
		VAMPIRIC("Vampiric", "it heals when it hits you"),
		BLINKING("Blinking", "now and then it vanishes and appears behind you: watch for the purple sparks"),
		WARDED("Warded", "arrows do little to it: close in"),
		THUNDERING("Thundering", "lightning strikes whoever it hits"),
		SPLITTING("Splitting", "when it dies it splits into two weaker copies");

		public final String title;
		public final String plain;

		Affix(String title, String plain) {
			this.title = title;
			this.plain = plain;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Affix byId(String id) {
			for (Affix a : values()) {
				if (a.id().equals(id.toLowerCase(Locale.ROOT))) {
					return a;
				}
			}
			return null;
		}
	}

	/** A champion's name and what it is: its kind, and what it carries. */
	private record Kind(String name, EntityType<? extends Mob> type, @Nullable Item hand) {
	}

	private static Kind kind(String name, EntityType<? extends Mob> type) {
		return new Kind(name, type, null);
	}

	private static Kind kind(String name, EntityType<? extends Mob> type, Item hand) {
		return new Kind(name, type, hand);
	}

	/** The people Dante meets in each circle, as its own monster (or what walks there). */
	private static final Map<Circle, List<Kind>> KINDS = new EnumMap<>(Circle.class);

	static {
		KINDS.put(Circle.VESTIBULE, List.of(kind("He Who Made the Great Refusal", EntityTypes.ZOMBIE),
				kind("the Banner-Chaser", EntityTypes.HUSK), kind("the Neutral Who Ran Forever", EntityTypes.ZOMBIE)));
		KINDS.put(Circle.LIMBO, List.of(kind("Hector of Troy", EntityTypes.SKELETON, Items.IRON_SWORD),
				kind("Camilla the Swift", EntityTypes.SKELETON, Items.BOW), kind("Penthesilea, Queen of the Amazons", EntityTypes.SKELETON, Items.BOW),
				kind("Aeneas the Wanderer", EntityTypes.SKELETON, Items.IRON_SWORD)));
		KINDS.put(Circle.LUST, List.of(kind("Francesca the Unrepentant", HellEntities.LOVERS), kind("Semiramis, Who Made Lust Lawful", HellEntities.LOVERS),
				kind("Cleopatra the Luxurious", HellEntities.LOVERS), kind("Dido, Who Broke Faith", HellEntities.LOVERS),
				kind("Paolo, Ever Blown", HellEntities.LOVERS)));
		KINDS.put(Circle.GLUTTONY, List.of(kind("Ciacco the Swollen", HellEntities.GLUTTON), kind("Forese the Starving", HellEntities.GLUTTON),
				kind("Bonagiunta of the Feast", HellEntities.GLUTTON)));
		KINDS.put(Circle.GREED, List.of(kind("Plutus's Treasurer", EntityTypes.PIGLIN_BRUTE, Items.GOLDEN_AXE),
				kind("the Tonsured Miser", EntityTypes.PIGLIN_BRUTE, Items.GOLDEN_AXE), kind("the Cardinal Who Squandered", EntityTypes.PIGLIN_BRUTE, Items.GOLDEN_AXE)));
		KINDS.put(Circle.WRATH, List.of(kind("Filippo Argenti", HellEntities.WRATHFUL), kind("the Sullen One of the Styx", HellEntities.WRATHFUL),
				kind("the Gurgler in the Mire", HellEntities.WRATHFUL)));
		KINDS.put(Circle.HERESY, List.of(kind("the Epicurean", HellEntities.HERETIC), kind("Cavalcante, Who Asks for His Son", HellEntities.HERETIC),
				kind("Frederick, the Heretic Emperor", HellEntities.HERETIC), kind("Cardinal Ubaldini", HellEntities.HERETIC)));
		KINDS.put(Circle.VIOLENCE, List.of(kind("Nessus the Ferryman", HellEntities.CENTAUR), kind("Pholus, Full of Wrath", HellEntities.CENTAUR),
				kind("Capaneus the Defiant", EntityTypes.WITHER_SKELETON, Items.STONE_SWORD), kind("Pier della Vigna", EntityTypes.WITHER_SKELETON, Items.STONE_SWORD),
				kind("Attila, Scourge of God", EntityTypes.WITHER_SKELETON, Items.IRON_SWORD)));
		KINDS.put(Circle.FRAUD, List.of(kind("Vanni Fucci", HellEntities.THIEF), kind("Ulysses' Flame", EntityTypes.BLAZE),
				kind("Barbariccia the Curly-Bearded", HellEntities.MALEBRANCHE), kind("Graffiacane the Dog-Scratcher", HellEntities.MALEBRANCHE)));
		KINDS.put(Circle.TREACHERY, List.of(kind("Count Ugolino", EntityTypes.ZOMBIE), kind("Archbishop Ruggieri", EntityTypes.STRAY, Items.BOW),
				kind("Bocca degli Abati", EntityTypes.STRAY, Items.BOW), kind("Fra Alberigo, Whose Soul Fell Early", EntityTypes.STRAY, Items.IRON_SWORD),
				kind("Ganelon the Betrayer", EntityTypes.WITHER_SKELETON, Items.STONE_SWORD)));
	}

	/** The circles that have champions, outermost first. */
	public static List<Circle> circles() {
		return List.copyOf(KINDS.keySet());
	}

	private static String place(Circle c) {
		return switch (c) {
			case VESTIBULE -> "the Vestibule";
			case LIMBO -> "Limbo";
			default -> c.title().substring(c.title().indexOf('—') + 2);
		};
	}

	private static ChatFormatting colour(Circle c) {
		return switch (c) {
			case VESTIBULE -> ChatFormatting.YELLOW;
			case LIMBO -> ChatFormatting.GRAY;
			case LUST -> ChatFormatting.LIGHT_PURPLE;
			case GLUTTONY -> ChatFormatting.DARK_GREEN;
			case GREED -> ChatFormatting.GOLD;
			case WRATH -> ChatFormatting.DARK_AQUA;
			case HERESY -> ChatFormatting.RED;
			case VIOLENCE -> ChatFormatting.DARK_RED;
			case FRAUD -> ChatFormatting.DARK_PURPLE;
			default -> ChatFormatting.AQUA;
		};
	}

	private static BossEvent.BossBarColor bar(Circle c) {
		return switch (c) {
			case LUST -> BossEvent.BossBarColor.PINK;
			case GLUTTONY -> BossEvent.BossBarColor.GREEN;
			case GREED, VESTIBULE -> BossEvent.BossBarColor.YELLOW;
			case WRATH, TREACHERY -> BossEvent.BossBarColor.BLUE;
			case HERESY, VIOLENCE -> BossEvent.BossBarColor.RED;
			case FRAUD -> BossEvent.BossBarColor.PURPLE;
			default -> BossEvent.BossBarColor.WHITE;
		};
	}

	/** Each circle's enchanted book (tools/gen_loot.py circle_book); circles without one of their own give any of them. */
	@Nullable
	private static ResourceKey<Enchantment> book(Circle c) {
		return switch (c) {
			case LUST -> LayerEnchants.WINDSTEP;
			case GREED -> LayerEnchants.QUICKENING;
			case WRATH -> LayerEnchants.SIDESTEP;
			case HERESY -> LayerEnchants.STEADFAST;
			case VIOLENCE -> LayerEnchants.JUDGEMENT;
			default -> null;
		};
	}

	/** "Filippo Argenti, Burning and Swift". */
	public static String fullName(String name, List<Affix> affixes) {
		if (affixes.isEmpty()) {
			return name;
		}
		List<String> titles = affixes.stream().map(a -> a.title).toList();
		String joined = titles.size() == 1 ? titles.get(0)
				: String.join(", ", titles.subList(0, titles.size() - 1)) + " and " + titles.get(titles.size() - 1);
		return name + ", " + joined;
	}

	// ------------------------------------------------------------------ the champions walking now

	/** One champion: its boss bar, who hurt it how much, and its clocks. */
	private static final class Live {
		final UUID id;
		final ServerLevel level;
		final Circle circle;
		final String name;
		final List<Affix> affixes;
		final ServerBossEvent bar;
		final Map<UUID, Float> damage = new HashMap<>();
		final Set<UUID> told = new HashSet<>();
		long lastSeen;
		int nextBlink = 200;
		int blinkAt = -1;

		Live(Mob mob, Circle circle, String name, List<Affix> affixes) {
			this.id = mob.getUUID();
			this.level = (ServerLevel) mob.level();
			this.circle = circle;
			this.name = name;
			this.affixes = affixes;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(fullName(name, affixes)).withStyle(colour(circle), ChatFormatting.BOLD),
					bar(circle), BossEvent.BossBarOverlay.NOTCHED_10);
			this.lastSeen = mob.level().getGameTime();
		}

		boolean has(Affix a) {
			return affixes.contains(a);
		}
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** Per circle: ticks someone has spent there since its last champion, and how many it takes for the next. */
	private static final Map<Circle, Integer> PRESENCE = new EnumMap<>(Circle.class);
	private static final Map<Circle, Integer> NEXT = new EnumMap<>(Circle.class);
	/** Fire a Burning champion left behind: where, and when it goes out. */
	private static final Map<BlockPos, Long> FIRES = new HashMap<>();
	private static final int CHECK = 400;
	private static final double BAR_RANGE = 48;
	private static final double TELL_RANGE = 24;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Champions::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && entity.entityTags().contains(TAG) && !LIVE.containsKey(entity.getUUID())) {
				adopt(mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Champions::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Champions::afterDeath);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.values().forEach(l -> l.bar.removeAllPlayers());
			LIVE.clear();
			for (BlockPos pos : FIRES.keySet()) {
				if (server.overworld().getBlockState(pos).is(Blocks.FIRE)) {
					server.overworld().removeBlock(pos, false);
				}
			}
			FIRES.clear();
		});
	}

	/** A champion back from a restart (or a chunk loading again): its state is in its tags. */
	@Nullable
	private static Live adopt(Mob mob) {
		Circle circle = null;
		int index = 0;
		List<Affix> affixes = new ArrayList<>();
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(CIRCLE_TAG)) {
				try {
					circle = Circle.valueOf(tag.substring(CIRCLE_TAG.length()).toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException ignored) {
					// a circle that no longer exists
				}
			} else if (tag.startsWith(NAME_TAG)) {
				try {
					index = Integer.parseInt(tag.substring(NAME_TAG.length()));
				} catch (NumberFormatException ignored) {
					// keep the first name
				}
			} else if (tag.startsWith(AFFIX_TAG)) {
				Affix a = Affix.byId(tag.substring(AFFIX_TAG.length()));
				if (a != null) {
					affixes.add(a);
				}
			}
		}
		if (circle == null || !KINDS.containsKey(circle)) {
			return null;
		}
		List<Kind> kinds = KINDS.get(circle);
		Live live = new Live(mob, circle, kinds.get(Math.floorMod(index, kinds.size())).name(), affixes);
		LIVE.put(mob.getUUID(), live);
		return live;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = server.overworld().getGameTime();
		if (!FIRES.isEmpty() && server.getTickCount() % 10 == 0) {
			FIRES.entrySet().removeIf(e -> {
				if (now < e.getValue()) {
					return false;
				}
				if (level.isLoaded(e.getKey()) && level.getBlockState(e.getKey()).is(Blocks.FIRE)) {
					level.removeBlock(e.getKey(), false);
				}
				return true;
			});
		}
		if (!LIVE.isEmpty()) {
			for (Iterator<Live> it = LIVE.values().iterator(); it.hasNext(); ) {
				Live live = it.next();
				Entity e = live.level.getEntity(live.id);
				if (!(e instanceof Mob mob) || !mob.isAlive()) {
					live.bar.removeAllPlayers();
					it.remove();
					continue;
				}
				if (!walk(live.level, mob, live, now)) {
					live.bar.removeAllPlayers();
					it.remove();
				}
			}
		}
		if (server.getTickCount() % CHECK == 0) {
			bringOut(level);
		}
	}

	/** One tick of a champion: its bar, its target, its tells and its tricks. False once it has gone. */
	private static boolean walk(ServerLevel level, Mob mob, Live live, long now) {
		int t = mob.tickCount;
		if (t % 10 == 0) {
			Player near = level.getNearestPlayer(mob, 128);
			if (near != null) {
				live.lastSeen = now;
			} else if (now - live.lastSeen > 6000) {
				// no one came for it in five minutes: it goes back into the dark
				HellcraftMod.LOGGER.info("Champion {} of {} went back into the dark", live.name, live.circle.name().toLowerCase(Locale.ROOT));
				mob.discard();
				return false;
			}
			live.bar.setProgress(Math.max(0.0f, mob.getHealth() / mob.getMaxHealth()));
			for (ServerPlayer p : new ArrayList<>(live.bar.getPlayers())) {
				if (p.level() != level || p.distanceTo(mob) > BAR_RANGE || !p.isAlive()) {
					live.bar.removePlayer(p);
				}
			}
			for (ServerPlayer p : level.players()) {
				double d = p.distanceTo(mob);
				if (d <= BAR_RANGE) {
					live.bar.addPlayer(p);
				}
				if (d <= TELL_RANGE && live.told.add(p.getUUID())) {
					tell(p, live);
				}
			}
		}
		if (t % 20 == 0 && mob.getTarget() == null) {
			Player prey = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), 24, e -> e instanceof Player p && !p.isCreative() && !p.isSpectator());
			if (prey != null) {
				mob.setTarget(prey);
			}
		}
		// the tells: what it is, at a glance
		if (t % 5 == 0) {
			for (Affix a : live.affixes) {
				ParticleOptions particle = switch (a) {
					case SWIFT -> mob.walkAnimation.speed() > 0.1f ? ParticleTypes.CLOUD : null;
					case BURNING -> ParticleTypes.FLAME;
					case FROZEN -> ParticleTypes.SNOWFLAKE;
					case VAMPIRIC -> t % 20 == 0 ? BloodAltar.BLOOD : null;
					case THUNDERING -> t % 15 == 0 ? ParticleTypes.ELECTRIC_SPARK : null;
					case SPLITTING -> mob.getHealth() < mob.getMaxHealth() / 2 && t % 10 == 0 ? ParticleTypes.CRIT : null;
					case WARDED -> t % 20 == 0 ? ParticleTypes.ENCHANT : null;
					default -> null;
				};
				if (particle != null) {
					level.sendParticles(particle, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 3, mob.getBbWidth() * 0.5,
							mob.getBbHeight() * 0.4, mob.getBbWidth() * 0.5, 0.01);
				}
			}
		}
		if (live.has(Affix.BURNING) && t % 40 == 0 && mob.walkAnimation.speed() > 0.1f && level == level.getServer().overworld()) {
			leaveFire(level, mob.blockPosition(), level.getGameTime());
		}
		if (live.has(Affix.BLINKING)) {
			blink(level, mob, live, t);
		}
		return true;
	}

	/** Blinking: purple sparks for a second, then it stands behind its target. */
	private static void blink(ServerLevel level, Mob mob, Live live, int t) {
		LivingEntity target = mob.getTarget();
		if (live.blinkAt < 0) {
			if (t >= live.nextBlink && target != null && target.distanceTo(mob) > 3.0 && target.distanceTo(mob) < 32) {
				live.blinkAt = t + 20;
				level.playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.6f);
			}
			return;
		}
		if (t < live.blinkAt) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 6, 0.4, 0.8, 0.4, 0.05);
			return;
		}
		live.blinkAt = -1;
		live.nextBlink = t + 160 + level.getRandom().nextInt(80);
		if (target == null || !target.isAlive()) {
			return;
		}
		Vec3 look = target.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : look.normalize();
		double x = target.getX() - look.x * 2.5;
		double z = target.getZ() - look.z * 2.5;
		BlockPos at = BlockPos.containing(x, target.getY(), z);
		if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) {
			return;
		}
		level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
		mob.teleportTo(x, target.getY(), z);
		mob.lookAt(target, 360, 360);
		level.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.8f);
	}

	private static void leaveFire(ServerLevel level, BlockPos pos, long now) {
		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
				|| Shrines.near(level, pos) || FIRES.size() > 64) {
			return;
		}
		level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
		FIRES.put(pos.immutable(), now + 60);
	}

	/** The first time a soul comes near a champion: what it is and what its affixes do, plainly. */
	private static void tell(ServerPlayer p, Live live) {
		StringBuilder what = new StringBuilder();
		for (Affix a : live.affixes) {
			what.append(what.isEmpty() ? "" : "; ").append(a.title).append(": ").append(a.plain);
		}
		Dialogue.notice(p, Component.literal(live.name + " is a champion of " + place(live.circle) + ". " + what + ". Whoever deals it the most damage "
				+ "takes its Blood Fragments.").withStyle(colour(live.circle)));
	}

	// ------------------------------------------------------------------ hits, deaths and spoils

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Live hurt = LIVE.get(entity.getUUID());
		if (hurt != null && taken > 0) {
			if (source.getEntity() instanceof ServerPlayer p) {
				hurt.damage.merge(p.getUUID(), taken, Float::sum);
			}
			if (hurt.has(Affix.HARDENED)) {
				level.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.4f, 1.6f);
			}
		}
		Live hitter = source.getEntity() == null ? null : LIVE.get(source.getEntity().getUUID());
		if (hitter == null || taken <= 0 || blocked || entity == source.getEntity()) {
			return;
		}
		Entity champion = source.getEntity();
		if (hitter.has(Affix.BURNING)) {
			entity.igniteForSeconds(4);
		}
		if (hitter.has(Affix.FROZEN)) {
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), champion);
			entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), 120));
			level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		}
		if (hitter.has(Affix.VAMPIRIC) && champion instanceof LivingEntity living) {
			living.heal(taken * 0.6f + 1.0f);
			level.sendParticles(BloodAltar.BLOOD, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.3, 0.5, 0.3, 0);
			level.sendParticles(BloodAltar.BLOOD, living.getX(), living.getY() + 1.2, living.getZ(), 12, 0.4, 0.6, 0.4, 0);
		}
		if (hitter.has(Affix.THUNDERING)) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
			if (bolt != null) {
				bolt.snapTo(entity.getX(), entity.getY(), entity.getZ());
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			entity.hurtServer(level, level.damageSources().lightningBolt(), 3.0f);
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		Live live = LIVE.remove(entity.getUUID());
		if (live == null || !(entity.level() instanceof ServerLevel level) || !(entity instanceof Mob mob)) {
			return;
		}
		live.bar.removeAllPlayers();
		MinecraftServer server = level.getServer();
		ServerPlayer victor = null;
		float best = 0;
		for (Map.Entry<UUID, Float> e : live.damage.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null && e.getValue() > best) {
				best = e.getValue();
				victor = p;
			}
		}
		if (victor == null && source.getEntity() instanceof ServerPlayer killer) {
			victor = killer;
		}
		if (live.has(Affix.SPLITTING)) {
			split(level, mob, live);
		}
		if (victor == null) {
			HellcraftMod.LOGGER.info("Champion {} died with no one to credit", live.name);
			return;
		}
		HellConfig c = HellConfig.get();
		int depth = live.circle.depth();
		int fragments = Math.max(0, c.championFragments) + depth;
		BloodItems.give(victor, BloodItems.fragment(fragments));
		List<String> got = new ArrayList<>();
		got.add(fragments + " Blood Fragments");
		if (level.getRandom().nextDouble() < c.championBookChance) {
			ItemStack book = book(level, live.circle);
			if (!book.isEmpty()) {
				got.add("an enchanted book (" + book.getHoverName().getString() + ")");
				BloodItems.give(victor, book);
			}
		}
		ExperienceOrb.award(level, victor.position(), 40 + 10 * depth);
		got.add("experience");
		Dialogue.notice(victor, Component.literal("You dealt " + live.name + " the most damage, so its spoils are yours: " + String.join(", ", got) + ".")
				.withStyle(colour(live.circle)));
		server.getPlayerList().broadcastSystemMessage(Component.literal(victor.getGameProfile().name() + " has slain " + live.name + ", champion of "
				+ place(live.circle) + ".").withStyle(colour(live.circle), ChatFormatting.BOLD), false);
		HellcraftMod.LOGGER.info("Champion slain: {} by {}", live.name, victor.getGameProfile().name());
	}

	/** One of the five circle books: this circle's own, or any of them for a circle without one. */
	private static ItemStack book(ServerLevel level, Circle circle) {
		ResourceKey<Enchantment> key = book(circle);
		if (key == null) {
			List<ResourceKey<Enchantment>> all = List.of(LayerEnchants.WINDSTEP, LayerEnchants.QUICKENING, LayerEnchants.SIDESTEP,
					LayerEnchants.STEADFAST, LayerEnchants.JUDGEMENT);
			key = all.get(level.getRandom().nextInt(all.size()));
		}
		Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
		if (holder == null) {
			return ItemStack.EMPTY;
		}
		int max = holder.value().getMaxLevel();
		return EnchantmentHelper.createBook(new EnchantmentInstance(holder, 1 + level.getRandom().nextInt(Math.max(1, max))));
	}

	/** Splitting: two weaker copies where it fell (they split no further, and carry no spoils). */
	private static void split(ServerLevel level, Mob dead, Live live) {
		for (int i = 0; i < 2; i++) {
			Entity made = dead.getType().create(level, EntitySpawnReason.EVENT);
			if (!(made instanceof Mob echo)) {
				return;
			}
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			echo.snapTo(dead.getX() + Math.cos(a), dead.getY(), dead.getZ() + Math.sin(a), dead.getYRot(), 0);
			echo.addTag(ECHO_TAG);
			echo.addTag(TOUCHED);
			equip(echo, kindOf(live));
			setBase(echo, Attributes.MAX_HEALTH, Math.max(10.0, dead.getMaxHealth() * 0.2));
			echo.setHealth(echo.getMaxHealth());
			scaleDamage(echo, live.circle);
			echo.setCustomName(Component.literal("A shard of " + live.name).withStyle(colour(live.circle)));
			echo.setCustomNameVisible(true);
			echo.setGlowingTag(true);
			level.addFreshEntity(echo);
			level.sendParticles(ParticleTypes.EXPLOSION, echo.getX(), echo.getY() + 1, echo.getZ(), 1, 0, 0, 0, 0);
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceTo(dead) < 32) {
				p.sendOverlayMessage(Component.literal(live.name + " split in two! Finish the shards.").withStyle(colour(live.circle), ChatFormatting.BOLD));
			}
		}
	}

	@Nullable
	private static Kind kindOf(Live live) {
		for (Kind k : KINDS.get(live.circle)) {
			if (k.name().equals(live.name)) {
				return k;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ bringing them out

	/** Every 20 seconds: a circle someone has been in long enough gets its champion, if it has none walking. */
	private static void bringOut(ServerLevel level) {
		HellConfig c = HellConfig.get();
		if (!c.champions || level.getDifficulty() == Difficulty.PEACEFUL || !HellWorldgen.isInferno(level)) {
			return;
		}
		Map<Circle, List<ServerPlayer>> who = new EnumMap<>(Circle.class);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && !p.isCreative() && p.isAlive()) {
				Circle circle = InfernoGeometry.circleAt(p.getX(), p.getZ());
				if (KINDS.containsKey(circle)) {
					who.computeIfAbsent(circle, k -> new ArrayList<>()).add(p);
				}
			}
		}
		for (Map.Entry<Circle, List<ServerPlayer>> e : who.entrySet()) {
			Circle circle = e.getKey();
			int presence = PRESENCE.merge(circle, CHECK, Integer::sum);
			int next = NEXT.computeIfAbsent(circle, k -> interval(level));
			if (presence < next || LIVE.values().stream().anyMatch(l -> l.circle == circle)) {
				continue;
			}
			List<ServerPlayer> there = e.getValue();
			ServerPlayer near = there.get(level.getRandom().nextInt(there.size()));
			Vec3 at = findSpot(level, near, circle);
			if (at == null) {
				continue;
			}
			Mob champion = spawn(level, circle, -1, randomAffixes(level, circle), at, near, true);
			if (champion != null) {
				PRESENCE.put(circle, 0);
				NEXT.put(circle, interval(level));
			}
		}
	}

	private static int interval(ServerLevel level) {
		HellConfig c = HellConfig.get();
		int lo = Math.max(1, c.championMinMinutes);
		int hi = Math.max(lo, c.championMaxMinutes);
		return (lo + level.getRandom().nextInt(hi - lo + 1)) * 1200;
	}

	/** Somewhere out of the way near a soul: in its circle, out of the lairs, the Rests, the pit and the spawn. */
	@Nullable
	private static Vec3 findSpot(ServerLevel level, ServerPlayer p, Circle circle) {
		BlockPos spawn = level.getRespawnData().pos();
		for (int tries = 0; tries < 12; tries++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 24 + level.getRandom().nextDouble() * 16;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (!fits(level, x, z, circle, spawn)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (Shrines.near(level, at) || !level.getFluidState(at.below()).isEmpty() && !level.getFluidState(at.below()).is(FluidTags.WATER)
					|| Math.abs(y - p.getY()) > 24) {
				continue;
			}
			return Vec3.atBottomCenterOf(at);
		}
		return null;
	}

	private static boolean fits(ServerLevel level, int x, int z, Circle circle, BlockPos spawn) {
		return InfernoGeometry.circleAt(x, z) == circle
				&& !InfernoGeometry.nearLair(x, z, InfernoGeometry.LAIR_CLEARANCE + 10)
				&& Math.hypot(x, z) > InfernoGeometry.PIT_RADIUS + 40
				&& Math.hypot(x - spawn.getX(), z - spawn.getZ()) > 128
				&& level.isLoaded(new BlockPos(x, 0, z));
	}

	/** One to three affixes: more the deeper the circle. */
	private static List<Affix> randomAffixes(ServerLevel level, Circle circle) {
		int count = 1 + (circle.depth() >= 4 && level.getRandom().nextBoolean() ? 1 : 0) + (circle.depth() >= 7 && level.getRandom().nextBoolean() ? 1 : 0);
		List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
		List<Affix> out = new ArrayList<>();
		while (out.size() < count && !pool.isEmpty()) {
			out.add(pool.remove(level.getRandom().nextInt(pool.size())));
		}
		out.sort(null);
		return out;
	}

	/** The health of a champion of this circle. */
	public static double health(Circle circle) {
		return Math.max(20.0, HellConfig.get().championHealth) * (1.0 + 0.1 * circle.depth());
	}

	/**
	 * Brings out a champion of a circle at a spot (name index -1: a random one of its names). Tells the whole server,
	 * naming the soul it walks near (when there is one). Returns it, or null if it couldn't be made.
	 */
	@Nullable
	public static Mob spawn(ServerLevel level, Circle circle, int nameIndex, List<Affix> affixes, Vec3 at, @Nullable ServerPlayer near, boolean announce) {
		List<Kind> kinds = KINDS.get(circle);
		if (kinds == null) {
			return null;
		}
		int index = nameIndex < 0 ? level.getRandom().nextInt(kinds.size()) : Math.floorMod(nameIndex, kinds.size());
		Kind kind = kinds.get(index);
		Mob mob = kind.type().create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
		mob.addTag(TAG);
		mob.addTag(CIRCLE_TAG + circle.name().toLowerCase(Locale.ROOT));
		mob.addTag(NAME_TAG + index);
		mob.addTag(TOUCHED);
		for (Affix a : affixes) {
			mob.addTag(AFFIX_TAG + a.id());
		}
		equip(mob, kind);
		String full = fullName(kind.name(), affixes);
		mob.setCustomName(Component.literal(full).withStyle(colour(circle), ChatFormatting.BOLD));
		mob.setCustomNameVisible(true);
		mob.setGlowingTag(true);
		mob.setPersistenceRequired();
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		setBase(mob, Attributes.MAX_HEALTH, health(circle));
		mob.setHealth(mob.getMaxHealth());
		scaleDamage(mob, circle);
		setBase(mob, Attributes.FOLLOW_RANGE, 40.0);
		AttributeInstance armour = mob.getAttribute(Attributes.ARMOR);
		if (armour != null) {
			armour.setBaseValue(armour.getBaseValue() + 4.0);
		}
		AttributeInstance knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.setBaseValue(Math.max(knockback.getBaseValue(), 0.5));
		}
		// daylight doesn't burn a champion, and fire doesn't burn a Burning one
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		if (affixes.contains(Affix.SWIFT)) {
			multiply(mob, Attributes.MOVEMENT_SPEED, 0.4);
			multiply(mob, Attributes.FLYING_SPEED, 0.4);
		}
		if (affixes.contains(Affix.HARDENED)) {
			mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 1, false, false));
		}
		if (affixes.contains(Affix.WARDED)) {
			// arrows from players do as little as they do to a boss (BossRules, bossProjectileDamage)
			mob.addTag(BossRules.BODY_TAG);
		}
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		if (mob instanceof Heretic heretic) {
			heretic.rise();
		}
		LIVE.put(mob.getUUID(), new Live(mob, circle, kind.name(), List.copyOf(affixes)));
		HellcraftMod.LOGGER.info("Champion walks: {} in {} at {}", fullName(kind.name(), affixes), circle.name().toLowerCase(Locale.ROOT),
				mob.blockPosition().toShortString());
		if (!announce) {
			return mob;
		}
		level.playSound(null, mob.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 1.4f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 60, 0.6, 1.0, 0.6, 0.05);
		String affixList = String.join(", ", affixes.stream().map(a -> a.title).toList());
		boolean book = book(circle) != null;
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("A champion walks in " + place(circle)
				+ (near != null ? ", near " + near.getGameProfile().name() : "") + ": " + kind.name() + (affixList.isEmpty() ? "" : " (" + affixList + ")")
				+ ". It drops Blood Fragments" + (book ? " and maybe " + place(circle) + "'s enchanted book." : " and maybe an enchanted book."))
				.withStyle(colour(circle)), false);
		return mob;
	}

	private static void equip(Mob mob, @Nullable Kind kind) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0f);
		}
		if (kind != null && kind.hand() != null) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(kind.hand()));
		}
	}

	private static void scaleDamage(Mob mob, Circle circle) {
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(damage.getBaseValue() * Math.max(1.0, HellConfig.get().championDamage) * (1.0 + 0.05 * circle.depth()));
		}
	}

	private static void setBase(LivingEntity e, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = e.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void multiply(LivingEntity e, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double extra) {
		AttributeInstance instance = e.getAttribute(attribute);
		if (instance != null) {
			instance.addPermanentModifier(new AttributeModifier(HellcraftMod.id("champion_swift"), extra, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	// ------------------------------------------------------------------ commands

	/** /hellcraft champion [circle] [affixes...], /hellcraft champion test, /hellcraft champion list (operators). */
	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		List<String> ids = new ArrayList<>(circles().stream().map(c -> c.name().toLowerCase(Locale.ROOT)).toList());
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("champion")
						.executes(ctx -> summon(ctx, null, ""))
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest(ctx.getSource().getLevel(), ctx.getSource().getPosition());
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return result.contains("Champion test OK") ? 1 : 0;
						}))
						.then(Commands.literal("list").executes(ctx -> {
							String list = LIVE.isEmpty() ? "No champions walk now." : "Champions walking: " + String.join("; ", LIVE.values().stream()
									.map(l -> fullName(l.name, l.affixes) + " (" + place(l.circle) + ")").toList());
							ctx.getSource().sendSuccess(() -> Component.literal(list), false);
							return LIVE.size();
						}))
						.then(Commands.argument("circle", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids, b))
								.executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "circle"), ""))
								.then(Commands.argument("affixes", StringArgumentType.greedyString())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of(Affix.values()).stream().map(Affix::id).toList(), b))
										.executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "circle"), StringArgumentType.getString(ctx, "affixes")))))));
	}

	private static int summon(CommandContext<CommandSourceStack> ctx, @Nullable String circleId, String affixText) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		Circle circle;
		if (circleId == null) {
			circle = InfernoGeometry.circleAt(player.getX(), player.getZ());
			if (!KINDS.containsKey(circle) || !HellWorldgen.isInferno(level)) {
				circle = circles().get(level.getRandom().nextInt(circles().size()));
			}
		} else {
			try {
				circle = Circle.valueOf(circleId.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				circle = null;
			}
			if (circle == null || !KINDS.containsKey(circle)) {
				source.sendFailure(Component.literal("No champions in " + circleId + ". Circles: " + String.join(", ",
						circles().stream().map(c -> c.name().toLowerCase(Locale.ROOT)).toList())));
				return 0;
			}
		}
		List<Affix> affixes = new ArrayList<>();
		for (String word : affixText.trim().split("[\\s,]+")) {
			if (word.isEmpty()) {
				continue;
			}
			Affix a = Affix.byId(word);
			if (a == null) {
				source.sendFailure(Component.literal("No affix " + word + ". Affixes: " + String.join(", ", List.of(Affix.values()).stream().map(Affix::id).toList())));
				return 0;
			}
			if (!affixes.contains(a)) {
				affixes.add(a);
			}
		}
		affixes.sort(null);
		if (affixes.isEmpty()) {
			affixes = randomAffixes(level, circle);
		}
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : look.normalize();
		Vec3 at = player.position().add(look.scale(5));
		Mob mob = spawn(level, circle, -1, affixes, at, player, true);
		if (mob == null) {
			source.sendFailure(Component.literal("The champion could not be brought out here."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Brought out " + mob.getCustomName().getString() + " (" + (int) mob.getMaxHealth() + " health)."), true);
		return 1;
	}

	/** For the smoke test: one champion of every circle, looked over, then taken away again. */
	public static String selfTest(ServerLevel level, Vec3 from) {
		List<String> wrong = new ArrayList<>();
		int made = 0;
		List<Affix> forced = List.of(Affix.BURNING, Affix.SWIFT);
		if (!fullName("Filippo Argenti", forced).equals("Filippo Argenti, Burning and Swift")
				|| !fullName("Ciacco the Swollen", List.of(Affix.FROZEN, Affix.VAMPIRIC, Affix.WARDED)).equals("Ciacco the Swollen, Frozen, Vampiric and Warded")) {
			wrong.add("name format");
		}
		level.getChunk(BlockPos.containing(from));
		int i = 0;
		for (Circle circle : circles()) {
			List<Affix> affixes = i == 0 ? forced : randomAffixes(level, circle);
			Vec3 at = from.add(2 * (i % 5), 0, 2 * (i / 5));
			i++;
			Mob mob = spawn(level, circle, -1, affixes, at, null, false);
			if (mob == null) {
				wrong.add(circle.name().toLowerCase(Locale.ROOT) + " not made");
				continue;
			}
			made++;
			Live live = LIVE.get(mob.getUUID());
			String name = mob.getCustomName() == null ? "" : mob.getCustomName().getString();
			if (live == null || !name.startsWith(live.name) || live.affixes.isEmpty() || live.affixes.size() > 3
					|| !live.affixes.stream().allMatch(a -> name.contains(a.title) && mob.entityTags().contains(AFFIX_TAG + a.id()))) {
				wrong.add(circle.name().toLowerCase(Locale.ROOT) + " name/affixes (" + name + ")");
			}
			if (Math.abs(mob.getMaxHealth() - health(circle)) > 1.0 || mob.getHealth() < mob.getMaxHealth() - 0.5) {
				wrong.add(circle.name().toLowerCase(Locale.ROOT) + " health " + mob.getMaxHealth());
			}
			if (!mob.hasGlowingTag() || !mob.isPersistenceRequired()) {
				wrong.add(circle.name().toLowerCase(Locale.ROOT) + " glow");
			}
			if (live != null) {
				live.bar.removeAllPlayers();
				LIVE.remove(mob.getUUID());
			}
			mob.discard();
		}
		boolean deeperTougher = health(Circle.TREACHERY) > health(Circle.LUST);
		return "Champion test: " + made + " circles, deeper is tougher=" + deeperTougher + (wrong.isEmpty() && deeperTougher && made == circles().size()
				? "; Champion test OK" : "; wrong: " + String.join(", ", wrong));
	}

	/** True for a champion (or one of its shards). */
	public static boolean isChampion(Entity e) {
		return e.entityTags().contains(TAG) || e.entityTags().contains(ECHO_TAG);
	}
}
