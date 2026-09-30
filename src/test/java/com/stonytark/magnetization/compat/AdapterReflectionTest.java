package com.stonytark.magnetization.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdapterReflectionTest {
    public static class Storage {
        int stored = 25, max = 100;
        public int getEnergyStored() { return stored; }
        public int getMaxEnergyStored() { return max; }
    }
    public static class AlternateStorage {
        public int getEnergyStored() { return 3; }
        public int getMaxEnergyStored() { return 4; }
    }
    public static class Parent {
        private boolean inverted;
        private String state() { return "parent"; }
        Object fallback = new Storage();
    }
    public static class Machine extends Parent {
        private Object storage;
        private int inverted = 42; // Wrong-typed shadow must fall through.
        private String state() { throw new IllegalStateException(); }
    }

    @Test
    void cachedFieldsReadLiveEnergyAndReplacedStorageIncludingNullAndUnsupportedValues() {
        final Machine machine = new Machine();
        assertEquals(.25, AdapterReflection.energyRatio(machine));
        final Storage storage = new Storage();
        machine.storage = storage;
        storage.stored = 80;
        assertEquals(.8, AdapterReflection.energyRatio(machine));
        storage.stored = 0;
        assertEquals(0, AdapterReflection.energyRatio(machine));
        storage.max = 0;
        assertEquals(.25, AdapterReflection.energyRatio(machine));
        machine.storage = new AlternateStorage();
        assertEquals(.75, AdapterReflection.energyRatio(machine));
        machine.storage = "unsupported";
        assertEquals(.25, AdapterReflection.energyRatio(machine));
        machine.storage = null;
        machine.fallback = null;
        assertEquals(0, AdapterReflection.energyRatio(machine));
    }

    @Test
    void inheritedPrivateMetadataRetainsLiveValuesAndFailureFallbacks() {
        final Machine machine = new Machine();
        for (int i = 0; i < 3; i++) {
            assertEquals("parent", AdapterReflection.noArgs(machine, "state"));
            assertNull(AdapterReflection.noArgs(machine, "missing"));
            ((Parent) machine).inverted = i % 2 == 0;
            assertEquals(i % 2 == 0, AdapterReflection.booleanField(machine, "inverted", false));
            assertTrue(AdapterReflection.booleanField(machine, "absent", true));
        }
    }
}
