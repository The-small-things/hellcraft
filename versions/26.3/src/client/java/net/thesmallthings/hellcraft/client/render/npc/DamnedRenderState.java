package net.thesmallthings.hellcraft.client.render.npc;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import net.thesmallthings.hellcraft.entity.boss.gen.DamnedAnim;
import net.thesmallthings.hellcraft.entity.npc.Damned;

/** What DamnedRenderer draws from: every animation's state, the punishment (for its props), its tint and the banner. */
public class DamnedRenderState extends LivingEntityRenderState {
	public final AnimationState[] states = new AnimationState[DamnedAnim.values().length];
	public Damned.Punishment punishment = Damned.Punishment.LIMBO;
	public int tint;
	public boolean leader;

	public DamnedRenderState() {
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}
}
