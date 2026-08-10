package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.physics.EmitterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Per-tick dispatch of active emitter state to UI consumers (particles, hum
 * sound). Iterates the per-level {@link EmitterRegistry} (O(emitters)) instead
 * of walking every loaded BE inside view-radius chunks (O(loaded BEs)).
 *
 * <p>Listeners are notified at most once per scan with each (pos, field) tuple
 * for emitters within VIEW_RADIUS of the player. Scans run every client tick;
 * downstream subscribers can throttle inside the callback if they want.
 */
@EventBusSubscriber(modid = "magnetization", value = Dist.CLIENT)
public final class ActiveEmitterScanner {

    private static final double VIEW_RADIUS = 32.0d;

    // CopyOnWriteArrayList — listeners normally register at client setup and
    // never again, so the write-on-modify cost is paid once; reads (every
    // client tick) are lock-free. Removes any latent CME risk if a mod adds
    // a listener mid-tick.
    private static final List<BiConsumer<BlockPos, MagneticField>> listeners = new CopyOnWriteArrayList<>();
    private static final List<Runnable> postScanHooks = new CopyOnWriteArrayList<>();

    private ActiveEmitterScanner() {}

    public static void onActiveEmitter(final BiConsumer<BlockPos, MagneticField> listener) {
        listeners.add(listener);
    }

    public static void onScanComplete(final Runnable hook) {
        postScanHooks.add(hook);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final Level level = mc.level;
        if (level == null || mc.player == null) return;
        if (listeners.isEmpty()) return;

        final var viewer = mc.player.position();
        final double radiusSqr = VIEW_RADIUS * VIEW_RADIUS;

        final BlockPos viewerPos = BlockPos.containing(viewer);
        final var nearbyEmitters = new HashSet<>(EmitterRegistry.snapshotNativeNear(
                level, viewerPos, (int) VIEW_RADIUS));
        nearbyEmitters.addAll(EmitterRegistry.snapshotExternalNear(
                level, viewerPos, (int) VIEW_RADIUS, Integer.MAX_VALUE));
        for (final BlockPos pos : nearbyEmitters) {
            if (pos.getCenter().distanceToSqr(viewer) > radiusSqr) continue;
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource source)) continue;
            final MagneticField field = source.currentField();
            if (field == null) continue;
            for (BiConsumer<BlockPos, MagneticField> l : listeners) {
                l.accept(pos, field);
            }
        }

        for (Runnable hook : postScanHooks) hook.run();
    }
}
