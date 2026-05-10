package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.menu.EmitterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagMenus {

    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, Magnetization.MOD_ID);

    /** One menu type, three modes: Electromagnet (full), Kinetic (armor-only),
     *  Strength-only (Anchor/Repulsor/Tractor). The mode bitmap is sent in the
     *  open packet. */
    public static final DeferredHolder<MenuType<?>, MenuType<EmitterMenu>> EMITTER =
            REGISTER.register("emitter",
                    () -> IMenuTypeExtension.create(EmitterMenu::fromNetwork));

    private MagMenus() {}
}
