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

import java.util.ArrayList;
import java.util.List;
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

    private static final List<BiConsumer<BlockPos, MagneticField>> listeners = new ArrayList<>();
    private static final List<Runnable> postScanHooks = new ArrayList<>();

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

        EmitterRegistry.forEach(level, (lvl, pos) -> {
            if (pos.getCenter().distanceToSqr(viewer) > radiusSqr) return;
            final BlockEntity be = lvl.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource source)) return;
            final MagneticField field = source.currentField();
            if (field == null) return;
            for (BiConsumer<BlockPos, MagneticField> l : listeners) {
                l.accept(pos, field);
            }
        });

        for (Runnable hook : postScanHooks) hook.run();
    }
}
