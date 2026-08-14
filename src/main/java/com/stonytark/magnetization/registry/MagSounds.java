package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Subtitled state-change cues for the custom golems. */
public final class MagSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, Magnetization.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_POLARIZE = register("golem.polarize");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_OXIDIZE = register("golem.oxidize");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_HEAT_CHANGE = register("golem.heat_change");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_DAMPEN = register("golem.dampen");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_CAPTURE = register("golem.capture");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_HARDEN = register("golem.harden");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_SOFTEN = register("golem.soften");

    private static DeferredHolder<SoundEvent, SoundEvent> register(final String path) {
        return REGISTER.register(path,
                () -> SoundEvent.createVariableRangeEvent(Magnetization.id(path)));
    }

    private MagSounds() {}
}
