package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Bootstrap-free state transitions shared by live golems and plain JUnit. */
public final class IronOxideGolemLogic {
    public record OxidationProgress(long ticks, boolean oxidized) {}

    public static OxidationProgress advanceOxidation(final long currentTicks,
                                                      final boolean oxidized,
                                                      final boolean enabled,
                                                      final long durationTicks) {
        if (oxidized || !enabled) return new OxidationProgress(currentTicks, oxidized);
        final long next = currentTicks == Long.MAX_VALUE ? Long.MAX_VALUE : currentTicks + 1L;
        return new OxidationProgress(next, next >= Math.max(1L, durationTicks));
    }

    public static MagneticField captureSnapshot(final MagneticField source, final Vec3 newOrigin) {
        final MagneticStrength strength = source.strength().ordinal() > MagneticStrength.STRONG.ordinal()
                ? MagneticStrength.STRONG : source.strength();
        return new MagneticField(newOrigin, source.axis(), source.polarity(), strength, source.shape());
    }

    public static MagneticPolarity restorePolarity(final String savedName) {
        try {
            final MagneticPolarity restored = MagneticPolarity.valueOf(savedName);
            return restored == MagneticPolarity.NONE ? MagneticPolarity.NORTH : restored;
        } catch (final IllegalArgumentException ignored) {
            return MagneticPolarity.NORTH;
        }
    }

    public static boolean protectsTarget(final UUID sourceId, final @Nullable UUID ownerId,
                                         final UUID targetId, final boolean sourceAllied,
                                         final boolean ownerAllied) {
        if (sourceId.equals(targetId)) return true;
        return ownerId != null && (ownerId.equals(targetId) || sourceAllied || ownerAllied);
    }

    public static boolean captureSourceAllowed(final @Nullable UUID excludedSource,
                                               final UUID candidateSource,
                                               final boolean candidateIsTitanomagnetite) {
        return !candidateIsTitanomagnetite && !candidateSource.equals(excludedSource);
    }

    private IronOxideGolemLogic() {}
}
