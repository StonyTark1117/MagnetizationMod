package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Hardens MR (magnetorheological) fluid into {@link MagBlocks#HARDENED_MR_FLUID}
 * while it sits inside an active magnetic field — a walkable solid for temporary
 * bridges — and reverts it to fluid once the field is gone. Iterates the small
 * MR-fluid source + hardened registries (no field-volume scan); when a source is
 * in a field it flood-hardens the whole connected fluid body so the bridge is
 * continuous, recording per-cell whether it was a source so the revert restores
 * the body correctly.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MrFluidHardenHandler {

    private static final int FLOOD_BUDGET = 512;

    /** True only while the handler is clearing a reverting body to air. When set,
     *  {@link HardenedMrFluidBlock#onRemove} must NOT run its "broken by a player →
     *  becomes MR fluid source" branch — otherwise every flowing cell we clear
     *  would be recreated as a source, duplicating the fluid. */
    private static boolean fieldReverting = false;

    public static boolean isFieldReverting() { return fieldReverting; }

    private MrFluidHardenHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if ((server.getGameTime() % com.stonytark.magnetization.config.MagConfig.mrFluidHardenTicks()) != 0L) return;

        // Harden: any MR-fluid source now in a field flood-hardens its body.
        int budget = FLOOD_BUDGET;
        final Set<BlockPos> done = new HashSet<>();
        for (final BlockPos src : MrFluidSourceRegistry.snapshot(server)) {
            if (budget <= 0) break;
            if (done.contains(src)) continue;
            final BlockState st = server.getBlockState(src);
            if (!st.is(MagBlocks.MR_FLUID_BLOCK.get())) { MrFluidSourceRegistry.remove(server, src); continue; }
            if (MagneticFields.isInField(server, src)) budget = floodHarden(server, src, done, budget);
        }

        // Revert: any hardened block no longer in a field melts back. Mirror how
        // ferrofluid resets a pool — clear the WHOLE reverting body to AIR first,
        // then restore ONLY the cells that were sources and let them re-flow to
        // refill. Restoring sources inline (the old approach) let a restored
        // source's flow reach a not-yet-cleared cell and leave a flowing cell as a
        // NEW source, duplicating fluid.
        final java.util.List<BlockPos> restoreSources = new java.util.ArrayList<>();
        fieldReverting = true; // suppress onRemove's "broken → MR source" branch while we clear
        try {
            for (final BlockPos pos : HardenedMrFluidRegistry.snapshot(server)) {
                if (!server.isLoaded(pos)) continue;
                final BlockState st = server.getBlockState(pos);
                if (!st.is(MagBlocks.HARDENED_MR_FLUID.get())) { HardenedMrFluidRegistry.remove(server, pos); continue; }
                if (!MagneticFields.isInField(server, pos)) {
                    if (st.getValue(HardenedMrFluidBlock.SOURCE)) restoreSources.add(pos.immutable());
                    server.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    HardenedMrFluidRegistry.remove(server, pos);
                }
            }
        } finally {
            fieldReverting = false;
        }
        for (final BlockPos src : restoreSources) {
            server.setBlock(src, MagBlocks.MR_FLUID_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Flood the connected MR-fluid body from {@code start}, converting each cell
     *  to a hardened block (recording whether it was a source). */
    private static int floodHarden(final ServerLevel server, final BlockPos start,
                                   final Set<BlockPos> done, int budget) {
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        done.add(start);
        while (!queue.isEmpty() && budget > 0) {
            final BlockPos p = queue.poll();
            final BlockState st = server.getBlockState(p);
            if (!st.is(MagBlocks.MR_FLUID_BLOCK.get())) continue;
            final boolean source = st.getFluidState().isSource();
            server.setBlock(p, MagBlocks.HARDENED_MR_FLUID.get().defaultBlockState()
                    .setValue(HardenedMrFluidBlock.SOURCE, source), Block.UPDATE_ALL);
            MrFluidSourceRegistry.remove(server, p);
            HardenedMrFluidRegistry.add(server, p);
            budget--;
            for (final Direction d : Direction.values()) {
                final BlockPos n = p.relative(d).immutable();
                if (done.add(n) && server.isLoaded(n) && server.getBlockState(n).is(MagBlocks.MR_FLUID_BLOCK.get())) {
                    queue.add(n);
                }
            }
        }
        return budget;
    }
}
