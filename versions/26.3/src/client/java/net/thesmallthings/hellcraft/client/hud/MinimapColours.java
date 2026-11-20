package net.thesmallthings.hellcraft.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The minimap's colours, as Xaero's draws them: each block's own top texture, averaged, and tinted the way the game tints it
 * there (grass, leaves and water take the biome's colours, so Hell's circles look like themselves). Worked out once per block
 * state (the texture once per sprite, read from the resource packs) and kept; a few new textures a tick at most, the rest
 * waiting with the plain map colour meanwhile. 0 means "not known": use the block's map colour.
 */
final class MinimapColours {
	private MinimapColours() {
	}

	/** A block state's averaged top texture (ARGB, 0 if none) and which of its tints applies to that face (-1: none). */
	private record Entry(int colour, int tint) {
	}

	/** How many new textures may be read in one tick. */
	private static final int LOADS_PER_TICK = 6;

	private static final Map<BlockState, Entry> STATES = new HashMap<>();
	private static final Map<Identifier, Integer> SPRITES = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create(42L);
	private static int loads;
	private static boolean broken;

	static void tick() {
		loads = 0;
	}

	static void clear() {
		STATES.clear();
		SPRITES.clear();
	}

	/** The top of {@code state} at {@code pos} as the minimap colours it (ARGB), or 0 to use its map colour. */
	static int top(ClientLevel level, BlockState state, BlockPos pos) {
		if (broken) {
			return 0;
		}
		Entry e = STATES.get(state);
		if (e == null) {
			try {
				e = build(state);
			} catch (RuntimeException | LinkageError ex) {
				// the game's models changed under us: say so once and fall back to the map colours for good
				HellcraftMod.LOGGER.warn("Minimap: block colours from textures are off ({})", ex.toString());
				broken = true;
				return 0;
			}
			if (e == null) {
				return 0;
			}
			STATES.put(state, e);
		}
		if (e.colour == 0) {
			return 0;
		}
		if (e.tint < 0) {
			return e.colour;
		}
		BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, e.tint);
		if (source == null) {
			return e.colour;
		}
		return multiply(e.colour, source.colorInWorld(state, level, pos));
	}

	private static int multiply(int c, int t) {
		int r = ((c >> 16) & 255) * ((t >> 16) & 255) / 255;
		int g = ((c >> 8) & 255) * ((t >> 8) & 255) / 255;
		int b = (c & 255) * (t & 255) / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Null while its texture waits for a later tick. */
	private static Entry build(BlockState state) {
		Minecraft mc = Minecraft.getInstance();
		BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RANDOM, parts);
		TextureAtlasSprite sprite = null;
		int tint = -2;
		// the face looking up; for plants and the like (no such face), any face
		for (Direction side : new Direction[] {Direction.UP, null}) {
			for (BlockStateModelPart part : parts) {
				List<BakedQuad> quads = part.getQuads(side);
				if (!quads.isEmpty()) {
					Object info = quads.get(0).materialInfo();
					sprite = spriteIn(info);
					tint = tintIn(info);
					break;
				}
			}
			if (sprite != null) {
				break;
			}
		}
		if (sprite == null) {
			sprite = model.particleMaterial().sprite();
		}
		if (tint == -2) {
			tint = mc.getBlockColors().getTintSources(state).isEmpty() ? -1 : 0;
		}
		Identifier name = sprite.contents().name();
		Integer colour = SPRITES.get(name);
		if (colour == null) {
			if (loads >= LOADS_PER_TICK) {
				return null;
			}
			loads++;
			colour = average(name);
			SPRITES.put(name, colour);
		}
		return new Entry(colour, tint);
	}

	/** A quad's sprite, from its material record (looked up by type, since the record's shape is the game's to change). */
	private static TextureAtlasSprite spriteIn(Object info) {
		Object s = component(info, TextureAtlasSprite.class, null);
		if (s instanceof TextureAtlasSprite sprite) {
			return sprite;
		}
		Object m = component(info, Material.Baked.class, null);
		return m instanceof Material.Baked baked ? baked.sprite() : null;
	}

	/** A quad's tint index, or -1 for none, or -2 if the record doesn't say. */
	private static int tintIn(Object info) {
		Object t = component(info, int.class, "tint");
		return t instanceof Integer i ? (i < 0 ? -1 : i) : -2;
	}

	private static Object component(Object record, Class<?> type, String nameHint) {
		if (record == null || !record.getClass().isRecord()) {
			return null;
		}
		for (RecordComponent rc : record.getClass().getRecordComponents()) {
			if (type.isAssignableFrom(rc.getType()) && (nameHint == null || rc.getName().toLowerCase(java.util.Locale.ROOT).contains(nameHint))) {
				try {
					var accessor = rc.getAccessor();
					accessor.setAccessible(true);
					return accessor.invoke(record);
				} catch (ReflectiveOperationException | RuntimeException e) {
					return null;
				}
			}
		}
		return null;
	}

	/** The average colour of a sprite's texture file (its first frame), opaque pixels only; 0 if it can't be read. */
	private static int average(Identifier sprite) {
		Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return 0;
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			int w = image.getWidth();
			int h = Math.min(image.getHeight(), w);
			long r = 0;
			long g = 0;
			long b = 0;
			long weight = 0;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int c = image.getPixel(x, y);
					int a = (c >>> 24) & 255;
					if (a < 32) {
						continue;
					}
					r += (long) ((c >> 16) & 255) * a;
					g += (long) ((c >> 8) & 255) * a;
					b += (long) (c & 255) * a;
					weight += a;
				}
			}
			// mostly see-through (a flower, a rail, glass): too little to colour a map with
			if (weight < (long) w * h * 255 / 8) {
				return 0;
			}
			return 0xFF000000 | (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
		} catch (Exception e) {
			return 0;
		}
	}
}
