package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.content.meteorite.MeteoriteFieldRegistry.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pin the {@link Entry} NBT roundtrip — the AE2 hook's per-dimension SavedData
 *  persists entries through this serialiser. A regression here would silently
 *  reset every AE2-meteor's decay timer on world reload. */
class MeteoriteFieldRegistryEntryTest {

    @Test
    void roundtripPreservesPos() {
        final Entry e = new Entry(new BlockPos(123, -45, 6789), 100L);
        final CompoundTag tag = invokeSave(e);
        final Entry decoded = invokeLoad(tag);
        assertEquals(e.pos(), decoded.pos());
    }

    @Test
    void roundtripPreservesChargedAt() {
        final Entry e = new Entry(BlockPos.ZERO, 9_999_999L);
        final CompoundTag tag = invokeSave(e);
        final Entry decoded = invokeLoad(tag);
        assertEquals(e.chargedAtTick(), decoded.chargedAtTick());
    }

    @Test
    void roundtripPreservesNegativeCoordinates() {
        // BlockPos.asLong() / BlockPos.of(long) sign-extends correctly only if
        // both halves of the roundtrip use it. Pin a worst-case position.
        final Entry e = new Entry(new BlockPos(-30_000_000, -64, -30_000_000), 0L);
        final Entry decoded = invokeLoad(invokeSave(e));
        assertEquals(e.pos(), decoded.pos());
    }

    @Test
    void roundtripPreservesZeroChargedAt() {
        // Default-construction edge: a 0 chargedAt should round-trip as 0,
        // not interpreted as "never charged".
        final Entry e = new Entry(new BlockPos(1, 2, 3), 0L);
        final Entry decoded = invokeLoad(invokeSave(e));
        assertEquals(0L, decoded.chargedAtTick());
    }

    // ─── Reflection helpers ───
    // Entry.save() and Entry.load() are package-private; this test lives in
    // the same package, but reflection keeps the test resilient if those
    // accessors get tightened to private later.
    private static CompoundTag invokeSave(final Entry e) {
        try {
            final var m = Entry.class.getDeclaredMethod("save");
            m.setAccessible(true);
            return (CompoundTag) m.invoke(e);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Entry invokeLoad(final CompoundTag tag) {
        try {
            final var m = Entry.class.getDeclaredMethod("load", CompoundTag.class);
            m.setAccessible(true);
            return (Entry) m.invoke(null, tag);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
