package com.stonytark.magnetization.content.shaft;

import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.network.chat.Component;

/** The same material progression as our permanent magnets; capacity always comes from the drive. */
public enum MagneticShaftMaterial {
    FERROMAGNETIC(MagneticStrength.WEAK),
    SAMARIUM_COBALT(MagneticStrength.MEDIUM),
    NEODYMIUM(MagneticStrength.STRONG);

    private final MagneticStrength strength;
    MagneticShaftMaterial(MagneticStrength strength) { this.strength = strength; }
    public MagneticStrength strength() { return strength; }
    public Component label() { return Component.translatable("shaft.magnetization.material." + name().toLowerCase(java.util.Locale.ROOT)); }
    public int range() {
        return switch (this) {
            case FERROMAGNETIC -> MagConfig.MAGNETIC_SHAFT_RANGE.get();
            case SAMARIUM_COBALT -> MagConfig.SAMARIUM_COBALT_SHAFT_RANGE.get();
            case NEODYMIUM -> MagConfig.NEODYMIUM_SHAFT_RANGE.get();
        };
    }
}
