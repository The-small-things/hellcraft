package net.thesmallthings.hellcraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Density function type {@code hellcraft:inferno}. Exposes {@link InfernoGeometry} to the noise
 * router so the vanilla noise chunk generator can build the funnel. Every mode is a plain function
 * of the column; the data pack combines them with the surface detail noise the same way
 * {@link InfernoGeometry#surfaceHeight} does:
 * <pre>height = base + detail_amplitude * noise + ridge_amplitude * (1 - |noise|)^3</pre>
 */
public record InfernoDensity(Mode mode) implements DensityFunction {
	public static final MapCodec<InfernoDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Mode.CODEC.fieldOf("mode").forGetter(InfernoDensity::mode)
	).apply(instance, InfernoDensity::new));

	/** The last column asked for, per thread: generation samples each column many times in a row. */
	private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0, 0, 0, 0});

	static double value(Mode mode, int x, int z) {
		double[] last = LAST.get();
		if (last[0] != x || last[1] != z) {
			double[] rough = InfernoGeometry.roughness(x, z);
			last[0] = x;
			last[1] = z;
			last[2] = InfernoGeometry.baseHeight(x, z);
			last[3] = rough[0];
			last[4] = rough[1];
			last[5] = rough[2];
		}
		return switch (mode) {
			case BASE -> last[2];
			case DETAIL_AMPLITUDE -> last[3];
			case RIDGE_AMPLITUDE -> last[4];
			case JAGGED -> last[5];
		};
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		Mode mode = this.mode;
		return new DensitySampler() {
			@Override
			public float sampleValue(SamplerContext sampler, int x, int y, int z) {
				return (float) value(mode, x, z);
			}

			@Override
			public void sampleVolume(SamplerContext sampler, DensityBuffer buffer, DensityVolume volume) {
				DensitySampler.sampleVolumeNaive(sampler, buffer, volume, this);
			}
		};
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return mode == Mode.BASE ? Interval.of(-96.0f, 384.0f) : Interval.of(0.0f, 64.0f);
	}

	@Override
	public int domainAxes() {
		// constant along Y: the generator may evaluate it once per column
		return AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<? extends DensityFunction> codec() {
		return MAP_CODEC;
	}

	public enum Mode implements StringRepresentable {
		BASE("base"),
		DETAIL_AMPLITUDE("detail_amplitude"),
		RIDGE_AMPLITUDE("ridge_amplitude"),
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
