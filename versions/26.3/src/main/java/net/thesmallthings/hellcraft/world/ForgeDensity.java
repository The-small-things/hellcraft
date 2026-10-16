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
 * Density function type {@code hellcraft:forge}: exposes {@link ForgeGeometry} to the Forge of Dis's noise router
 * (worldgen/noise_settings/forge.json, written by tools/gen_realms.py), which adds the Nether's own 3D noise:
 * <pre>density = shape(x, y, z) + roughness(x, z) * nether/base_3d_noise</pre>
 */
public record ForgeDensity(Mode mode) implements DensityFunction {
	public static final MapCodec<ForgeDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Mode.CODEC.fieldOf("mode").forGetter(ForgeDensity::mode)
	).apply(instance, ForgeDensity::new));

	/** The last column asked for, per thread: the generator samples each column many times in a row. */
	private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0, 0, 0});

	static double[] column(int x, int z) {
		double[] last = LAST.get();
		if (last[0] != x || last[1] != z) {
			double[] c = ForgeGeometry.column(x, z);
			last[0] = x;
			last[1] = z;
			last[2] = c[0];
			last[3] = c[1];
			last[4] = c[2];
		}
		return last;
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		Mode mode = this.mode;
		return new DensitySampler() {
			@Override
			public float sampleValue(SamplerContext sampler, int x, int y, int z) {
				double[] c = column(x, z);
				if (mode == Mode.ROUGHNESS) {
					return (float) c[4];
				}
				return (float) ForgeGeometry.shape(x, y, z, new double[]{c[2], c[3], c[4]});
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
		return mode == Mode.ROUGHNESS ? Interval.of(0.0f, 2.0f) : Interval.of(-16.0f, 16.0f);
	}

	@Override
	public int domainAxes() {
		return mode == Mode.ROUGHNESS ? AXIS_X | AXIS_Z : AXIS_X | AXIS_Y | AXIS_Z;
	}

	@Override
	public MapCodec<? extends DensityFunction> codec() {
		return MAP_CODEC;
	}

	public enum Mode implements StringRepresentable {
		SHAPE("shape"),
		ROUGHNESS("roughness");

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
