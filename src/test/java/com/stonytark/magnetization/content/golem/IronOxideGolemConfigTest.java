package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.config.MagConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IronOxideGolemConfigTest {
    @Test
    void everyGolemHasAnIndependentEnabledByDefaultToggle() {
        final var toggles = List.of(
                MagConfig.GALLIUM_GOLEM_ENABLED,
                MagConfig.MR_FLUID_GOLEM_ENABLED,
                MagConfig.MAGNETITE_GOLEM_ENABLED,
                MagConfig.PYRRHOTITE_GOLEM_ENABLED,
                MagConfig.HEMATITE_GOLEM_ENABLED,
                MagConfig.TITANOMAGNETITE_GOLEM_ENABLED);
        assertEquals(List.of(
                        List.of("content", "galliumGolemEnabled"),
                        List.of("content", "mrFluidGolemEnabled"),
                        List.of("content", "magnetiteGolemEnabled"),
                        List.of("content", "pyrrhotiteGolemEnabled"),
                        List.of("content", "hematiteGolemEnabled"),
                        List.of("content", "titanomagnetiteGolemEnabled")),
                toggles.stream().map(value -> List.copyOf(value.getPath())).toList());
        toggles.forEach(value -> assertEquals(Boolean.TRUE, value.getDefault()));
    }
}
