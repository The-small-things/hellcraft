package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.FarinataEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.FarinataAnim;
import net.thesmallthings.hellcraft.hazard.BossFx;
import net.thesmallthings.hellcraft.hazard.Danger;

import java.util.ArrayList;
import java.util.List;

/**
 * Farinata degli Uberti (Inferno X), the proud Ghibelline, risen in his burning tomb in the middle of the Iron City
 * "as if he held Hell in great disdain". He never leaves his tomb; eight more tombs stand round him, and they fire at
 * his command. In the danger language (hazard/Danger):
 * <ul>
 *     <li><b>Disdain</b> (a white line): his fist comes down on his tomb's rim and fire runs along the ground at you.
 *     Block it and he sinks back into his tomb, open to blows.</li>
 *     <li><b>The Pyres</b> (red circles on the tombs): he points and the tombs round him blast fire, in patterns:
 *     every other one, then the rest; one after another round the ring; or all but one.</li>
 *     <li><b>Exile</b> (a purple ring round him): the air round his tomb blasts outward. It can't be blocked: keep out
 *     of the ring.</li>
 * </ul>
 * At half his health he rises higher out of the fire and bellows at the city that exiled him; from then on the tombs
 * fire twice for every command.
 */
final class FarinataFight extends GuardianFight {
	private static final int TOMBS = 8;
	private static final double RING = 11.0;
	private static final double PYRE_RADIUS = 3.8;
	private static final float PYRE_DAMAGE = 8.0f;
	private static final double DISDAIN_REACH = 20.0;
	private static final double DISDAIN_WIDTH = 1.5;
	private static final float DISDAIN_DAMAGE = 10.0f;
	private static final double EXILE_INNER = 2.5;
	private static final double EXILE_OUTER = 8.5;
	private static final float EXILE_DAMAGE = 8.0f;

	private int cycle;
	private int pattern;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;

	FarinataFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		FarinataEntity body = HellEntities.FARINATA.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Farinata");
		}
		buildTombs();
		return body;
	}

	private static FarinataEntity farinata(Mob body) {
		return (FarinataEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("disdain", "pyres", "exile");
	}

	@Override
	protected String introLine() {
		return "Who were thine ancestors?";
	}

	@Override
	protected String deathLine() {
		return "*Farinata sinks back into his fire, as proud as ever.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.FARINATA_INTRO;
	}

	@Override
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		farinata(body).play(FarinataAnim.INTRO);
		lockedUntil = tick + FarinataAnim.INTRO.ticks;
		schedule(FarinataAnim.INTRO.at("rise"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.5f, 0.4f);
			BossFx.send(level, ParticleTypes.FLAME, body.getX(), body.getY() + 1, body.getZ(), 150, 1.2, 1.5, 1.2, 0.05);
		});
		return FarinataAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ the tombs

	/** The tomb he stands in and the eight round it (built for the fight, kept after it). */
	private void buildTombs() {
		int y = lair.getY();
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState fuel = Blocks.NETHERRACK.defaultBlockState();
		// his own: walls two high round a fire, an opening on no side
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				BlockPos p = lair.offset(dx, 0, dz);
				level.setBlock(p.below(), edge ? wall : fuel, 3);
				level.setBlock(p, edge ? wall : (dx == 0 && dz == 0 ? Blocks.AIR.defaultBlockState() : Blocks.FIRE.defaultBlockState()), 3);
				level.setBlock(p.above(), edge ? wall : Blocks.AIR.defaultBlockState(), 3);
			}
		}
		for (Vec3 t : tombs()) {
			BlockPos c = BlockPos.containing(t.x, y, t.z);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
					BlockPos p = c.offset(dx, 0, dz);
					level.setBlock(p.below(), edge ? wall : fuel, 3);
					level.setBlock(p, edge ? wall : Blocks.FIRE.defaultBlockState(), 3);
				}
			}
		}
	}

	private List<Vec3> tombs() {
		List<Vec3> out = new ArrayList<>();
		for (int i = 0; i < TOMBS; i++) {
			double a = i * Math.PI * 2 / TOMBS;
			double x = lair.getX() + 0.5 + Math.cos(a) * RING;
			double z = lair.getZ() + 0.5 + Math.sin(a) * RING;
			out.add(new Vec3(x, lair.getY(), z));
		}
		return out;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		String[] order = enraged ? new String[]{"disdain", "pyres", "exile", "pyres", "disdain", "pyres"} : new String[]{"disdain", "pyres", "disdain", "exile", "pyres"};
		String attack = order[cycle++ % order.length];
		int cooldown = perform(attack, body, nearest == null ? targets.get(0) : nearest);
		return enraged ? Math.max(cooldown * 3 / 4, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "disdain" -> disdain(body, target);
			case "pyres" -> pyres(body);
			case "exile" -> exile(body);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** Disdain: a white line. Block it and he sinks back into his tomb. */
	private int disdain(Mob body, LivingEntity target) {
		FarinataEntity farinata = farinata(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = FarinataAnim.DISDAIN.at("flash");
		int hit = FarinataAnim.DISDAIN.at("hit");
		lockedUntil = tick + FarinataAnim.DISDAIN.ticks;
		farinata.play(FarinataAnim.DISDAIN);
		say(level.getRandom().nextBoolean() ? "Thy people learned not that art well!" : "I scattered thy fathers twice!");
		schedule(flash, () -> {
			farinata.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.line(level, Danger.Colour.WHITE, c, angle, DISDAIN_REACH, DISDAIN_WIDTH, hit - flash);
		});
		schedule(hit, () -> {
			farinata.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.5f, 0.6f);
			for (double r = 2; r <= DISDAIN_REACH; r += 1.0) {
				BossFx.send(level, ParticleTypes.FLAME, c.x + Math.cos(angle) * r, c.y + 0.3, c.z + Math.sin(angle) * r, 6, 0.3, 0.2, 0.3, 0.02);
			}
			for (LivingEntity e : inBand(c, angle, DISDAIN_REACH, DISDAIN_WIDTH)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), DISDAIN_DAMAGE)) {
					e.igniteForSeconds(4);
				}
			}
		});
		return FarinataAnim.DISDAIN.ticks + 20;
	}

	/** The Pyres: he points, and the tombs round him blast in a pattern (twice in his fury). */
	private int pyres(Mob body) {
		FarinataEntity farinata = farinata(body);
		int fire = FarinataAnim.COMMAND.at("fire");
		lockedUntil = tick + FarinataAnim.COMMAND.ticks;
		farinata.play(FarinataAnim.COMMAND);
		tip("The tombs round him will blast where the red circles are: keep clear of them!");
		List<int[]> waves = pattern(pattern++ % 3);
		int t = fire;
		for (int round = 0; round < (enraged ? 2 : 1); round++) {
			for (int[] wave : waves) {
				blast(body, wave, t);
				t += 24;
			}
		}
		return t + 30;
	}

	/** The waves of a pattern: which tombs fire together, wave after wave. */
	private List<int[]> pattern(int which) {
		List<int[]> waves = new ArrayList<>();
		switch (which) {
			case 0 -> {
				// every other one, then the rest
				waves.add(new int[]{0, 2, 4, 6});
				waves.add(new int[]{1, 3, 5, 7});
			}
			case 1 -> {
				// one after another round the ring, two at a time
				int start = level.getRandom().nextInt(TOMBS);
				for (int i = 0; i < TOMBS; i += 2) {
					waves.add(new int[]{(start + i) % TOMBS, (start + i + 1) % TOMBS});
				}
			}
			default -> {
				// all but one: find the gap
				int gap = level.getRandom().nextInt(TOMBS);
				int[] all = new int[TOMBS - 1];
				for (int i = 0, k = 0; i < TOMBS; i++) {
					if (i != gap) {
						all[k++] = i;
					}
				}
				waves.add(all);
			}
		}
		return waves;
	}

	/** These tombs warn (red circles), then blast `at` ticks from now. */
	private void blast(Mob body, int[] which, int at) {
		List<Vec3> all = tombs();
		int warn = 18;
		schedule(at - warn, () -> {
			for (int i : which) {
				Danger.circle(level, Danger.Colour.RED, all.get(i), PYRE_RADIUS, warn);
			}
		});
		schedule(at, () -> {
			for (int i : which) {
				Vec3 c = all.get(i);
				level.playSound(null, BlockPos.containing(c), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.5f);
				for (int k = 0; k < 30; k++) {
					double ox = (level.getRandom().nextDouble() - 0.5) * 2;
					double oz = (level.getRandom().nextDouble() - 0.5) * 2;
					level.sendParticles(ParticleTypes.FLAME, true, false, c.x + ox, c.y + 0.5, c.z + oz, 0, ox * 0.05, 0.6 + level.getRandom().nextDouble() * 0.4,
							oz * 0.05, 1.0);
				}
				for (LivingEntity e : victims(c, PYRE_RADIUS + 0.3)) {
					if (e.hurtServer(level, Danger.unblockable(level, body), PYRE_DAMAGE)) {
						e.igniteForSeconds(4);
					}
				}
			}
		});
	}

	/** Exile: a purple ring. The air round his tomb blasts outward: keep out of the ring. */
	private int exile(Mob body) {
		FarinataEntity farinata = farinata(body);
		Vec3 c = body.position();
		int blast = FarinataAnim.EXILE.at("blast");
		lockedUntil = tick + FarinataAnim.EXILE.ticks;
		farinata.play(FarinataAnim.EXILE);
		farinata.warn(BossEntity.Warning.PURPLE);
		say("Exile! As they exiled me!");
		tip("The air round his tomb will blast outward: get out of the purple ring!");
		Danger.ring(level, Danger.Colour.PURPLE, c, EXILE_INNER, EXILE_OUTER, blast);
		schedule(blast, () -> {
			farinata.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
			BossFx.send(level, ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 6, 3, 0.5, 3, 0);
			for (LivingEntity e : inRing(c, EXILE_INNER, EXILE_OUTER)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), EXILE_DAMAGE)) {
					fling(e, c, 1.6, 0.6);
				}
			}
		});
		return FarinataAnim.EXILE.ticks + 25;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		FarinataEntity farinata = farinata(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(farinata);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 12.0);
		}
		if (tick >= lockedUntil && staggerUntil < 0) {
			LivingEntity nearest = nearest(body, targets());
			if (nearest != null) {
				turnToward(body, nearest, 3.0f);
			}
		}
		// his tomb's fire flickers round him
		if (tick % 4 == 0) {
			BossFx.send(level, ParticleTypes.FLAME, body.getX(), body.getY() + 0.3, body.getZ(), 4, 1.0, 0.2, 1.0, 0.01);
		}
	}

	private void rage(FarinataEntity farinata) {
		enraged = true;
		farinata.warn(BossEntity.Warning.NONE);
		farinata.play(FarinataAnim.PHASE);
		shield(farinata, FarinataAnim.PHASE.ticks);
		delayNextAttack(FarinataAnim.PHASE.ticks + 10);
		lockedUntil = tick + FarinataAnim.PHASE.ticks;
		say("Florence! Thou shalt not have me twice!");
		tip("Farinata is furious: the tombs round him fire twice for every command now.");
		schedule(FarinataAnim.PHASE.at("roar"), () -> level.playSound(null, farinata.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.4f));
	}

	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		FarinataEntity farinata = farinata(body);
		farinata.warn(BossEntity.Warning.NONE);
		farinata.play(FarinataAnim.STAGGER);
		staggerUntil = tick + FarinataAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(FarinataAnim.STAGGER.ticks + 15);
		level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
		BossFx.send(level, ParticleTypes.CRIT, body.getX(), body.getY() + 2, body.getZ(), 40, 1.0, 1.0, 1.0, 0.4);
		say("Hm. Thou art not without breeding.");
		by.sendOverlayMessage(Component.literal("Parried! Farinata sinks into his tomb: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}
}
