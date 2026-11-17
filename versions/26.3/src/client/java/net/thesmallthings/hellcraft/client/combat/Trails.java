package net.thesmallthings.hellcraft.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.client.combat.anim.Bone;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import net.thesmallthings.hellcraft.combat.Moveset;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What a swing leaves in the air. As any weapon comes round, a sweep of white marks the cut; a hell weapon's blade also
 * draws its path in blood from hilt to tip, twice a tick so the curve is smooth (and in embers, once forged infernal).
 * The blade's place is worked out from the same pose the body is drawn with, so the trail sits on the blade. Not drawn
 * for your own swings in first person, where the blade is in front of your eyes instead.
 */
public final class Trails {
	private Trails() {
	}

	private static final DustParticleOptions BLOOD = new DustParticleOptions(0xB01018, 0.9f);
	private static final DustParticleOptions DARK_BLOOD = new DustParticleOptions(0x5C060A, 1.1f);
	private static final Vector3f HIPS = new Vector3f(0, 12, 0);
	private static final RigPose scratch = new RigPose();

	/** Once a tick for each player mid-move (PlayerAnimator). */
	public static void tick(Player player, PlayerAnimator animator, PlayerMovesAnim move, float before, float now) {
		Moveset set = Moveset.of(player.getMainHandItem());
		Moveset.Move data = set == null ? null : set.find(move.ordinal());
		if (data == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean own = player == mc.player && mc.options.getCameraType().isFirstPerson();
		Level level = player.level();
		// the cut itself: a sweep in front as the blow lands, for the wide moves
		if (before < data.hitTick() && now >= data.hitTick() && data.halfAngle() >= 50) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			double reach = 1.6 + data.reach();
			level.addParticle(ParticleTypes.SWEEP_ATTACK, player.getX() - Mth.sin(yaw) * reach, player.getY(0.55), player.getZ() + Mth.cos(yaw) * reach,
					0, 0, 0);
		}
		ItemStack weapon = player.getMainHandItem();
		if (own || HellWeapons.of(weapon) == null || now < data.swingTick() || before > data.hitTick() + 3) {
			return;
		}
		boolean infernal = HellWeapons.infernal(weapon);
		Vector3f hand = new Vector3f();
		Vector3f tip = new Vector3f();
		for (float partial : new float[]{0.5f, 1.0f}) {
			if (!animator.sample(partial, scratch) || !blade(scratch, player, hand, tip)) {
				continue;
			}
			for (int i = 1; i <= 4; i++) {
				float f = i / 4.0f;
				double x = Mth.lerp(f, hand.x, tip.x);
				double y = Mth.lerp(f, hand.y, tip.y);
				double z = Mth.lerp(f, hand.z, tip.z);
				level.addParticle(i == 4 ? BLOOD : DARK_BLOOD, x, y, z, 0, 0, 0);
				if (infernal && i >= 3) {
					level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.01, 0);
				}
			}
		}
	}

	/**
	 * Where the sword hand and the blade's tip are in the world for this pose (the rig's own skeleton, as PlayerRig lays
	 * it on the model; the vanilla pose under it is left out, which a swing has taken over anyway).
	 */
	private static boolean blade(RigPose pose, Player player, Vector3f hand, Vector3f tip) {
		boolean left = player.getMainArm() == HumanoidArm.LEFT;
		if (left) {
			pose = new RigPose().set(pose).mirror();
		}
		Bone armBone = left ? Bone.LEFT_ARM : Bone.RIGHT_ARM;
		if (pose.weight(armBone) < 0.5f) {
			return false;
		}
		float side = left ? 1 : -1;
		Quaternionf rootRot = new Quaternionf().slerp(pose.rotation[Bone.ROOT.ordinal()], pose.weight(Bone.ROOT));
		Vector3f rootShift = new Vector3f(pose.position[Bone.ROOT.ordinal()]).mul(pose.weight(Bone.ROOT));
		Quaternionf bodyRot = new Quaternionf().slerp(pose.rotation[Bone.BODY.ordinal()], pose.weight(Bone.BODY));
		Matrix4f m = new Matrix4f().translate(rootShift).translate(HIPS).rotate(rootRot).rotate(bodyRot).translate(-HIPS.x, -HIPS.y, -HIPS.z);
		// the shoulder, then down the arm to the grip, then along the blade (which the item bone turns about the grip)
		m.translate(side * 5, 2, 0).rotate(pose.rotation[armBone.ordinal()]).translate(side, 9, 0);
		m.transformPosition(hand.set(0, 0, 0));
		Quaternionf grip = new Quaternionf().slerp(pose.rotation[Bone.ITEM.ordinal()], pose.weight(Bone.ITEM));
		Vector3f along = grip.transform(new Vector3f(0, 0.174f, -0.985f)).mul(15);
		m.transformPosition(tip.set(along));
		toWorld(player, hand);
		toWorld(player, tip);
		return true;
	}

	/** A point of the player model's space (1/16 blocks, y down from the shoulders) into the world, as the renderer places it. */
	private static void toWorld(Player player, Vector3f p) {
		float scale = 0.9375f / 16.0f;
		float x = -p.x * scale;
		float y = (24.0f - p.y) * scale + 0.001f;
		float z = p.z * scale;
		float turn = (180.0f - player.yBodyRot) * Mth.DEG_TO_RAD;
		float cos = Mth.cos(turn);
		float sin = Mth.sin(turn);
		p.set((float) player.getX() + x * cos + z * sin, (float) player.getY() + y, (float) player.getZ() - x * sin + z * cos);
	}
}
