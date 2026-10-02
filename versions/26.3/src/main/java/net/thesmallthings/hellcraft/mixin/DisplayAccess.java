package net.thesmallthings.hellcraft.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display entities keep their transformation setters private: boss animation (BossModel) needs them. */
@Mixin(Display.class)
public interface DisplayAccess {
	@Invoker("setTransformation")
	void hellcraft$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void hellcraft$setInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void hellcraft$setInterpolationDelay(int ticks);

	@Invoker("setGlowColorOverride")
	void hellcraft$setGlowColorOverride(int rgb);
}
