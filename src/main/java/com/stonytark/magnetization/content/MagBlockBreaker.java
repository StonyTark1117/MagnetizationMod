package com.stonytark.magnetization.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Shared gate for machines that destroy WORLD blocks on their own (the
 * Excavator's tunnelling face, the Railgun's leading-slab smash, the Structural
 * Inducer's path-pulverise + soil-clear). Vanilla block-breaking fires a
 * cancellable {@link BlockEvent.BreakEvent} and honours the {@code doTileDrops}
 * game-rule; a raw {@code level.setBlock(pos, AIR)} does neither, so these
 * machines would silently bulldoze blocks inside another mod's claim/protection
 * region and would drop items even with {@code doTileDrops} off. Routing every
 * machine-driven break through here gives protection mods a veto and respects
 * the drop rule, matching how a real player break behaves.
 */
public final class MagBlockBreaker {

    private MagBlockBreaker() {}

    /**
     * Fire a fake-player {@link BlockEvent.BreakEvent} for {@code pos} so claim /
     * grief-protection mods can veto it. Returns {@code true} if the break was
     * cancelled (the caller must then leave the block alone). The fake player is
     * cached per level by {@link FakePlayerFactory}, so this allocates nothing
     * per call beyond the event object.
     */
    public static boolean isBreakVetoed(final ServerLevel level, final BlockPos pos, final BlockState state) {
        final BlockEvent.BreakEvent event =
                new BlockEvent.BreakEvent(level, pos.immutable(), state, FakePlayerFactory.getMinecraft(level));
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    /** Whether world-block drops are currently enabled ({@code doTileDrops}). */
    public static boolean dropsEnabled(final ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
    }
}
