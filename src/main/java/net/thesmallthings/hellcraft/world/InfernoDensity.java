package net.thesmallthings.hellcraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/**
 * Density function type {@code hellcraft:inferno}. Exposes {@link InfernoGeometry} to the noise
 * router so the vanilla noise chunk generator can build the funnel:
 * <ul>
 *     <li>{@code surface}: target surface height of the column (blocks), including detail noise</li>
 *     <li>{@code jagged}: strength of 3D overhang noise for the column</li>
 * </ul>
 * Both are 2D; the JSON wraps them in {@code minecraft:cache_2d}.
 */
public record InfernoDensity(Mode mode, DensityFunction detail) implements DensityFunction {
	public static final MapCodec<InfernoDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Mode.CODEC.fieldOf("mode").forGetter(InfernoDensity::mode),
			DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("detail", DensityFunctions.zero()).forGetter(InfernoDensity::detail)
	).apply(instance, InfernoDensity::new));
	public static final KeyDispatchDataCodec<InfernoDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

	@Override
	public double compute(FunctionContext context) {
		int x = context.blockX();
		int z = context.blockZ();
		return switch (mode) {
			case SURFACE -> InfernoGeometry.surfaceHeight(x, z, detail.compute(context));
			case JAGGED -> InfernoGeometry.jaggedAmplitude(x, z);
		};
	}

	@Override
	public void fillArray(double[] values, ContextProvider provider) {
		provider.fillAllDirectly(values, this);
	}

	@Override
	public DensityFunction mapAll(Visitor visitor) {
		return visitor.apply(new InfernoDensity(mode, detail.mapAll(visitor)));
	}

	@Override
	public double minValue() {
		return mode == Mode.SURFACE ? -96.0 : 0.0;
	}

	@Override
	public double maxValue() {
		return mode == Mode.SURFACE ? 384.0 : 2.0;
	}

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() {
		return CODEC;
	}

	public enum Mode implements StringRepresentable {
		SURFACE("surface"),
		JAGGED("jagged");

		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
		private final String name;

		Mode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
