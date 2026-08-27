package com.stonytark.magnetization.content.excavator;

import com.stonytark.magnetization.config.MagConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticExcavatorSafetyTest {

    @Test
    void blockEntityExtractionIsDisabledByDefault() {
        assertEquals(java.util.List.of("machines", "excavatorAffectsBlockEntities"),
                java.util.List.copyOf(MagConfig.EXCAVATOR_AFFECTS_BLOCK_ENTITIES.getPath()));
        assertFalse(MagConfig.EXCAVATOR_AFFECTS_BLOCK_ENTITIES.getDefault());
        assertFalse(MagConfig.excavatorAffectsBlockEntities());
        assertFalse(MagneticExcavatorBlockEntity.canExtractBlockEntity(true, false));
        assertTrue(MagneticExcavatorBlockEntity.canExtractBlockEntity(false, false));
    }

    @Test
    void blockEntityExtractionRequiresExplicitOptIn() {
        assertFalse(MagneticExcavatorBlockEntity.canExtractBlockEntity(true, false));
        assertTrue(MagneticExcavatorBlockEntity.canExtractBlockEntity(true, true));
        assertTrue(MagneticExcavatorBlockEntity.canExtractBlockEntity(false, true));
    }
}
