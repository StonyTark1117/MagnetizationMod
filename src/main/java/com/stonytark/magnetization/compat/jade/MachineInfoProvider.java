package com.stonytark.magnetization.compat.jade;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.menu.MachineGuiData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade tooltip lines for the shared machines (mirrors the WTHIT provider). */
public enum MachineInfoProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = Magnetization.id("machine_info");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(final ITooltip tooltip, final BlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MachineGuiData data)) return;
        for (final Component line : data.hudLines()) {
            tooltip.add(line);
        }
    }
}
