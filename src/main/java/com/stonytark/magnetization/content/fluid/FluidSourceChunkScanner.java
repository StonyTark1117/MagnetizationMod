package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Re-registers the field/creep fluid source cells when a chunk loads.
 *
 * <p>The fluid registries (magnetized ferrofluid, plain ferrofluid, gallium,
 * mixed gallium, MR fluid) are transient static maps populated ONLY from each
 * block's {@code onPlace}. But {@code onPlace} does NOT fire when a chunk is
 * deserialized from disk — so after a save→quit→reload, a settled pool's source
 * blocks still exist in the world but their registry entries are gone, and the
 * pool silently stops emitting its field / creeping / hardening until physically
 * disturbed. This scanner walks each loaded chunk (cheaply skipping sections
 * whose palette has no tracked block) and re-adds the source cells, mirroring how
 * {@link com.stonytark.magnetization.content.meteorite.AeMeteoriteScanner} rebuilds
 * the meteorite field registry on {@link ChunkEvent.Load}. Re-adding is idempotent,
 * so this is safe to run on every load.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FluidSourceChunkScanner {

    private FluidSourceChunkScanner() {}

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        // Collect source cells now (reading the loaded chunk is safe), but DEFER the
        // registry writes to the server thread: ChunkEvent.Load can fire off the main
        // thread, and the fluid registries are not synchronized against the main-thread
        // tick handlers that read/prune them.
        final java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        final java.util.List<BlockState> states = new java.util.ArrayList<>();
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = chunk.getMinSection();
        final int baseX = chunk.getPos().getMinBlockX();
        final int baseZ = chunk.getPos().getMinBlockZ();
        for (int si = 0; si < sections.length; si++) {
            final LevelChunkSection sec = sections[si];
            if (sec.hasOnlyAir()) continue;
            if (!sec.maybeHas(FluidSourceChunkScanner::isTrackedSource)) continue;   // palette skip — cheap
            final int sectionMinY = (minSection + si) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        final BlockState st = sec.getBlockState(x, y, z);
                        if (!isTrackedSource(st)) continue;
                        positions.add(new BlockPos(baseX + x, sectionMinY + y, baseZ + z));
                        states.add(st);
                    }
                }
            }
        }
        if (positions.isEmpty()) return;
        server.getServer().execute(() -> {
            for (int i = 0; i < positions.size(); i++) register(server, positions.get(i), states.get(i));
        });
    }

    private static boolean isTrackedSource(final BlockState st) {
        return st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())
                || st.is(MagBlocks.FERROFLUID_BLOCK.get())
                || st.is(MagBlocks.MIXED_GALLIUM_BLOCK.get())
                || st.is(MagBlocks.GALLIUM_BLOCK.get())
                || st.is(MagBlocks.MR_FLUID_BLOCK.get());
    }

    /** Mirror each block's {@code onPlace} registration (source cells only). */
    private static void register(final ServerLevel level, final BlockPos pos, final BlockState st) {
        if (!st.getFluidState().isSource()) return;   // only true source cells, like onPlace
        if (st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())) {
            MagnetizedFerrofluidRegistry.add(level, pos, st.getValue(MagnetizedFerrofluidBlock.POLARITY));
        } else if (st.is(MagBlocks.FERROFLUID_BLOCK.get())) {
            FerrofluidSourceRegistry.add(level, pos);
        } else if (st.is(MagBlocks.MIXED_GALLIUM_BLOCK.get())) {
            FerrofluidSourceRegistry.add(level, pos);   // creeps as a plain ferrofluid source
            GalliumRegistry.add(level, pos);            // also carries the Lorentz current
        } else if (st.is(MagBlocks.GALLIUM_BLOCK.get())) {
            GalliumRegistry.add(level, pos);
        } else if (st.is(MagBlocks.MR_FLUID_BLOCK.get())) {
            MrFluidSourceRegistry.add(level, pos);
        }
    }
}
