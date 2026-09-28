package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** What a guardian fight is made from: where, which guardian, and whether it's a test (never gives up). */
public record GuardianContext(ServerLevel level, Guardian kind, BlockPos lair, boolean debug) {
}
