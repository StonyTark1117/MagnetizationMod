package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Continuous hum loop attached to each active emitter. Subscribes to
 * {@link ActiveEmitterScanner}'s shared per-tick walk; for each emitter seen,
 * ensures a looping {@link AbstractTickableSoundInstance} is registered. After
 * each scan, anything not seen this tick is faded out.
 *
 * <p>Class load is forced from {@code MagClientRegistration#onClientSetup}
 * (calling {@link #touch()}). NeoForge 21.1 rejects {@code @EventBusSubscriber}
 * on classes with no {@code @SubscribeEvent} methods, so we don't use that
 * annotation here.
 */
public final class EmitterHumSound extends AbstractTickableSoundInstance {

    private static final Map<BlockPos, EmitterHumSound> active = new HashMap<>();
    private static final Set<BlockPos> seenThisScan = new HashSet<>();
    private static boolean wired = false;

    static {
        wire();
    }

    /** No-op call-site that forces class load so the static {@code wire()} fires. */
    public static void touch() {}

    private final BlockPos emitterPos;
    private boolean shouldFade = false;

    private EmitterHumSound(final BlockPos emitterPos) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
        this.emitterPos = emitterPos;
        this.x = emitterPos.getX() + 0.5;
        this.y = emitterPos.getY() + 0.5;
        this.z = emitterPos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.pitch = 1.4f;
        this.relative = false;
    }

    private static void wire() {
        if (wired) return;
        wired = true;
        ActiveEmitterScanner.onActiveEmitter(EmitterHumSound::onActive);
        ActiveEmitterScanner.onScanComplete(EmitterHumSound::onScanDone);
    }

    private static void onActive(final BlockPos pos, final MagneticField field) {
        seenThisScan.add(pos);
        if (active.containsKey(pos)) return;
        final EmitterHumSound sound = new EmitterHumSound(pos.immutable());
        active.put(pos.immutable(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private static void onScanDone() {
        if (active.isEmpty()) {
            seenThisScan.clear();
            return;
        }
        // Fade any active emitter we did not see this scan.
        active.entrySet().removeIf(entry -> {
            if (seenThisScan.contains(entry.getKey())) return false;
            entry.getValue().requestStop();
            return true;
        });
        seenThisScan.clear();
    }

    @Override
    public void tick() {
        if (shouldFade) {
            this.volume = Math.max(0.0f, this.volume - 0.05f);
            if (this.volume <= 0.0f) stop();
            return;
        }
        if (this.volume < 0.25f) this.volume += 0.02f;
    }

    private void requestStop() {
        this.shouldFade = true;
    }
}
