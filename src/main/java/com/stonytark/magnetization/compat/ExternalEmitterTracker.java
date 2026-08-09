package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Rebuilds and ticks the position index for optional-mod field emitters,
 * including block-only Create: New Age magnets that have no block entity. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class ExternalEmitterTracker {
    private ExternalEmitterTracker() {}

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        final List<BlockPos> found = scan(chunk);
        if (found.isEmpty()) return;
        if (level instanceof ServerLevel server) {
            server.getServer().execute(() -> found.forEach(pos -> EmitterRegistry.register(server, pos)));
        } else {
            found.forEach(pos -> EmitterRegistry.register(level, pos));
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(final ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        final ChunkPos unloading = chunk.getPos();
        for (final BlockPos pos : EmitterRegistry.snapshot(level)) {
            if (new ChunkPos(pos).equals(unloading)
                    && ExternalFieldCompat.isKnownEmitter(chunk.getBlockState(pos))) {
                EmitterRegistry.unregister(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (ExternalFieldCompat.isKnownEmitter(event.getPlacedBlock())) {
            EmitterRegistry.register(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level
                && ExternalFieldCompat.isKnownEmitter(event.getState())) {
            EmitterRegistry.unregister(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        for (final BlockPos pos : EmitterRegistry.snapshot(server)) {
            final BlockState state = server.getBlockState(pos);
            if (!ExternalFieldCompat.isKnownEmitter(state)) continue;
            final MagneticField field = ExternalFieldCompat.currentField(server, pos);
            if (field == null) continue;
            if (ExternalFieldCompat.shipsOnly(state)) {
                FieldApplicator.applyToSubLevelsOnly(server, field, null, null);
            } else {
                FieldApplicator.apply(server, field);
            }
        }
        EnderFieldRelayCompat.apply(server);
    }

    private static List<BlockPos> scan(final LevelChunk chunk) {
        final List<BlockPos> positions = new ArrayList<>();
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = chunk.getMinSection();
        final int baseX = chunk.getPos().getMinBlockX();
        final int baseZ = chunk.getPos().getMinBlockZ();
        for (int si = 0; si < sections.length; si++) {
            final LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(ExternalFieldCompat::isKnownEmitter)) continue;
            final int baseY = (minSection + si) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                if (ExternalFieldCompat.isKnownEmitter(section.getBlockState(x, y, z))) {
                    positions.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                }
            }
        }
        return positions;
    }
}
