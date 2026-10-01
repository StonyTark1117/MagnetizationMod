package com.stonytark.magnetization.compat;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ExternalFieldFallbackTest {
    public static class Adapter {
        boolean powered;
        public boolean power() { return powered; }
        public boolean canRun(int demand) { return powered && demand == 1; }
        public Object invalid() { return 1; }
        public boolean broken() { throw new IllegalStateException(); }
    }

    @Test
    void liveTrueAndFalseAvoidFallbackButMissingInvalidAndThrowingMethodsUseIt() {
        final Adapter target = new Adapter();
        final AtomicInteger reads = new AtomicInteger();
        final BooleanSupplier fallback = () -> { reads.incrementAndGet(); return true; };
        for (boolean powered : new boolean[]{true, false, true}) {
            target.powered = powered;
            assertEquals(powered, ExternalFieldCompat.invokeBooleanOrElse(target, "power", fallback));
            assertEquals(powered, ExternalFieldCompat.invokeBooleanWithInt(target, "canRun", 1, fallback));
        }
        assertEquals(0, reads.get());
        for (String method : new String[]{"missing", "invalid", "broken"}) {
            assertTrue(ExternalFieldCompat.invokeBooleanOrElse(target, method, fallback));
        }
        assertTrue(ExternalFieldCompat.invokeBooleanWithInt(target, "missing", 1, fallback));
        assertEquals(4, reads.get());
    }
}
