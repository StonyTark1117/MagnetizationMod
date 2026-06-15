package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.GalliumGolem;
import com.stonytark.magnetization.content.golem.MrFluidGolem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types for the mod (currently just the MR Fluid Golem). */
public final class MagEntities {

    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, Magnetization.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MrFluidGolem>> MR_FLUID_GOLEM =
            REGISTER.register("mr_fluid_golem", () -> EntityType.Builder
                    .of(MrFluidGolem::new, MobCategory.MISC)
                    .sized(1.4f, 2.7f)                 // iron-golem footprint
                    .clientTrackingRange(10)
                    .build("mr_fluid_golem"));

    public static final DeferredHolder<EntityType<?>, EntityType<GalliumGolem>> GALLIUM_GOLEM =
            REGISTER.register("gallium_golem", () -> EntityType.Builder
                    .of(GalliumGolem::new, MobCategory.MISC)
                    .sized(1.4f, 2.7f)                 // iron-golem footprint
                    .clientTrackingRange(10)
                    .build("gallium_golem"));

    /** Mod-bus listener: supply the golems' attributes. */
    public static void onAttributes(final EntityAttributeCreationEvent event) {
        event.put(MR_FLUID_GOLEM.get(), MrFluidGolem.createAttributes().build());
        event.put(GALLIUM_GOLEM.get(), GalliumGolem.createAttributes().build());
    }

    private MagEntities() {}
}
