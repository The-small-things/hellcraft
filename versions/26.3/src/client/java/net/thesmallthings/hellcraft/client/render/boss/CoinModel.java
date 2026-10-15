package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.CoinAnimations;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;

/** A great coin of the Mint (generated: CoinMesh), turning as it rolls (CoinAnimations, sped up to match its pace). */
public class CoinModel extends EntityModel<CoinRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("rolling_coin"), "main");
	/** The roll animation turns once in 2 s; at RollingCoin.SPEED the coin turns once every 2 * pi * RADIUS / SPEED ticks. */
	private static final float PACE = (float) (40 / (2 * Math.PI * RollingCoin.RADIUS / RollingCoin.SPEED));

	private final KeyframeAnimation roll;

	public CoinModel(ModelPart root) {
		super(root);
		this.roll = CoinAnimations.ROLL.bake(root);
	}

	@Override
	public void setupAnim(CoinRenderState state) {
		super.setupAnim(state);
		roll.apply(state.roll, state.ageInTicks, PACE);
	}
}
