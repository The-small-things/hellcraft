package net.thesmallthings.hellcraft.entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * A beast that carries souls down a cliff (Antaeus's hand, the tamed Geryon). Whoever rides one can't be hurt: the
 * ride may brush the rock, and nothing should knock a pilgrim off it.
 */
public interface Carrier {
	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity.getVehicle() instanceof Carrier));
	}
}
