package net.thesmallthings.hellcraft.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of weapon a stack is, for Hellcraft's combat: how it guards (and later, how it swings). Vanilla's swords,
 * axes and the mace, and each hell weapon as its own kind. Spears, tridents and tools keep vanilla's ways.
 */
public enum WeaponClass {
	SWORD(1.0f, false, false),
	AXE(0.85f, true, true),
	MACE(0.85f, true, true),
	BLOODLETTER(1.15f, false, false),
	REAPER(1.15f, true, true),
	TITHE_AXE(1.15f, true, true);

	/** How well it guards, against the guardBlock setting (a sword: 1). */
	public final float guard;
	/** Held in both hands: its own guard stance. */
	public final boolean twoHanded;
	/**
	 * A heavy weapon: once its blow lands the swing is committed, and no guard cuts its recovery short (combat/Swing.committed).
	 * In return its wielder is braced through that recovery and takes less from blows (Swing.brace).
	 */
	public final boolean heavy;

	WeaponClass(float guard, boolean twoHanded, boolean heavy) {
		this.guard = guard;
		this.twoHanded = twoHanded;
		this.heavy = heavy;
	}

	@Nullable
	public static WeaponClass of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		HellWeapons.Weapon hell = HellWeapons.of(stack);
		if (hell != null) {
			return switch (hell) {
				case BLOODLETTER -> BLOODLETTER;
				case REAPER -> REAPER;
				case TITHE_AXE -> TITHE_AXE;
				case BLOOD_PICKAXE -> null;
			};
		}
		if (stack.has(DataComponents.PIERCING_WEAPON) || stack.has(DataComponents.KINETIC_WEAPON)) {
			return null;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return SWORD;
		}
		if (stack.is(ItemTags.AXES)) {
			return AXE;
		}
		if (stack.is(Items.MACE)) {
			return MACE;
		}
		return null;
	}
}
