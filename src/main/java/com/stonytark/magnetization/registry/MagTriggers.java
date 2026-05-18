package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.advancements.MagSimpleTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> REGISTER =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Magnetization.MOD_ID);

    /** Fires when a player is within 16 blocks of an emitter as its FE buffer
     *  drives the field — i.e. {@code energyActiveThisTick} transitions from
     *  false to true on a tick. Used by the {@code powered_by_energy} advancement. */
    public static final DeferredHolder<CriterionTrigger<?>, MagSimpleTrigger> ENERGY_ACTIVATED =
            REGISTER.register("energy_activated", MagSimpleTrigger::new);

    /** Fires when the Repulsor Gun's self-recoil actually kicks the player
     *  back — the ray-trace from the gun must have landed on a magnetic
     *  emitter block. Used by the hidden {@code recoil_launch} advancement. */
    public static final DeferredHolder<CriterionTrigger<?>, MagSimpleTrigger> RECOIL_LAUNCH =
            REGISTER.register("recoil_launch", MagSimpleTrigger::new);

    private MagTriggers() {}
}
