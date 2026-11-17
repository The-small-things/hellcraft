package net.thesmallthings.hellcraft.entity.boss;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusShape;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonShape;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One of a boss's hit boxes beyond its main one (BossShape, tools/art/hitboxes.py): Cerberus's heads, Geryon's tail. The
 * game hard-wires multi-part hit boxes to the Ender Dragon, so these are entities of their own, like the Emperor's weak
 * points: invisible, never saved, pickable, and wherever the boss's model puts that bone. The boss spawns them (server)
 * and places them every tick; each client places them too, from the same shape, so they sit on the drawn model without
 * waiting for positions from the server.
 *
 * Whatever hits a part hits the boss: a blow (the player's attack is turned onto the boss itself, {@link #register}, and
 * combat/Swing resolves parts to their boss), an arrow or anything else that hurts it. Once the boss is dead its parts
 * can't be hit, and they go when it does.
 */
public class BossPart extends Entity {
	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(BossPart.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_INDEX = SynchedEntityData.defineId(BossPart.class, EntityDataSerializers.BYTE);
	/** How long (ticks) a part waits for its boss before it goes (a boss unloaded, or not yet arrived on a client). */
	private static final int ORPHAN_TICKS = 40;

	@Nullable
	private BossEntity owner;
	private int orphaned;

	public BossPart(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/** A player's attack on a part is an attack on its boss (vanilla's full attack: enchantments, the weapon's own effects). */
	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof BossPart part) || level.isClientSide()) {
				// the client sends the attack as it is; the server turns it onto the boss
				return InteractionResult.PASS;
			}
			BossEntity boss = part.owner();
			if (boss != null && boss.isAlive()) {
				player.attack(boss);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** The boss behind an entity, if it is one of a boss's parts; otherwise the entity itself. */
	@Nullable
	public static Entity body(@Nullable Entity entity) {
		return entity instanceof BossPart part ? part.owner() : entity;
	}

	/** Belongs to this boss as its part `index` (server side, before it is added). */
	void attach(BossEntity boss, int index) {
		this.owner = boss;
		entityData.set(DATA_OWNER, boss.getId());
		entityData.set(DATA_INDEX, (byte) index);
	}

	public int index() {
		return entityData.get(DATA_INDEX);
	}

	/** Its boss, or null while it can't be found (on a client it is found by its id, and adopts this part). */
	@Nullable
	public BossEntity owner() {
		if (owner == null || owner.isRemoved()) {
			owner = level().getEntity(entityData.get(DATA_OWNER)) instanceof BossEntity boss && !boss.isRemoved() ? boss : null;
			if (owner != null && level().isClientSide()) {
				owner.adopt(index(), this);
			}
		}
		return owner;
	}

	/** Puts its box here: `centre` its middle, `width` across and `height` tall (the boss calls it every tick). */
	void place(Vec3 centre, float width, float height) {
		double y = centre.y - height / 2;
		setPos(centre.x, y, centre.z);
		setBoundingBox(new AABB(centre.x - width / 2, y, centre.z - width / 2, centre.x + width / 2, y + height, centre.z + width / 2));
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && owner != null && owner.isRemoved()) {
			// its boss is gone (killed, unloaded): so is the part
			discard();
			return;
		}
		BossEntity boss = owner();
		if (boss == null) {
			if (!level().isClientSide() && ++orphaned > ORPHAN_TICKS) {
				discard();
			}
			return;
		}
		orphaned = 0;
		if (!level().isClientSide() && !boss.owns(this)) {
			// the boss has another part in this place (a reload made it again): this one is a stray
			discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		BossEntity boss = owner();
		if (boss == null || !boss.isAlive() || source.getEntity() == boss || source.getDirectEntity() == boss) {
			return false;
		}
		return boss.hurtServer(level, source, damage);
	}

	@Override
	public boolean isPickable() {
		BossEntity boss = owner;
		return boss != null && boss.isAlive();
	}

	@Override
	public boolean isAttackable() {
		return isPickable();
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER, -1);
		builder.define(DATA_INDEX, (byte) 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	/**
	 * /hellcraft hitbox test: every shape matches its boss's animations, parts turn with the body, and a Cerberus set down
	 * here (facing west) gets his eight parts, his middle head well in front of him, and a blow to it hurts him.
	 */
	public static String selfTest(ServerLevel level, Vec3 at) {
		String problem = BossShape.selfTest();
		if (problem == null) {
			problem = CerberusShape.SHAPE.check("Cerberus", CerberusAnim.values());
		}
		if (problem == null) {
			problem = GeryonShape.SHAPE.check("Geryon", GeryonAnim.values());
		}
		if (problem == null) {
			problem = MinosShape.SHAPE.check("Minos", MinosAnim.values());
		}
		if (problem != null) {
			return "Boss hit box test FAILED: " + problem;
		}
		CerberusEntity cerberus = HellEntities.CERBERUS.create(level, EntitySpawnReason.COMMAND);
		if (cerberus == null) {
			return "Boss hit box test FAILED: no Cerberus";
		}
		try {
			cerberus.snapTo(at.x, at.y, at.z, 90, 0);
			cerberus.setYBodyRot(90);
			cerberus.setYHeadRot(90);
			cerberus.setNoAi(true);
			level.addFreshEntity(cerberus);
			cerberus.updateParts(cerberus.shape());
			List<BossPart> parts = cerberus.parts();
			if (parts.size() != CerberusShape.SHAPE.parts()) {
				return "Boss hit box test FAILED: Cerberus has " + parts.size() + " parts, not " + CerberusShape.SHAPE.parts();
			}
			BossPart head = parts.getFirst();
			Vec3 centre = head.getBoundingBox().getCenter();
			// facing west (yaw 90) his front is -X: the middle head is some 6.7 blocks that way
			double ahead = cerberus.getX() - centre.x;
			if (ahead < 4.5 || Math.abs(centre.z - cerberus.getZ()) > 1.5 || centre.y - cerberus.getY() < 3.5) {
				return "Boss hit box test FAILED: Cerberus's middle head is at " + centre.subtract(cerberus.position()) + " from his feet";
			}
			if (head.owner() != cerberus || !head.isPickable() || head.getBoundingBox().getXsize() < 1.0) {
				return "Boss hit box test FAILED: his head is not his, not pickable, or has no size";
			}
			float before = cerberus.getHealth();
			head.hurtServer(level, level.damageSources().generic(), 5.0f);
			if (cerberus.getHealth() >= before) {
				return "Boss hit box test FAILED: a blow to Cerberus's head didn't hurt him";
			}
			return String.format("Boss hit box test OK: Cerberus has %d parts, his middle head %.1f blocks ahead, a blow there took %.1f health",
					parts.size(), ahead, before - cerberus.getHealth());
		} finally {
			for (BossPart p : cerberus.parts()) {
				p.discard();
			}
			cerberus.discard();
		}
	}
}
