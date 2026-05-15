package com.stonytark.magnetization.compat.jade;

import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade tooltip lines for any block whose BE implements {@link MagneticFieldSource}.
 * Mirrors the goggle-tooltip output so Jade users get the same diagnostic info
 * that goggles wearers see.
 */
public enum EmitterFieldProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return MagJadePlugin.FIELD_INFO;
    }

    @Override
    public void appendTooltip(final ITooltip tooltip, final BlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MagneticFieldSource source)) return;
        final MagneticField field = source.currentField();
        for (Component line : FieldTooltipFormatter.format(field, true)) {
            tooltip.add(line);
        }
        for (Component line : source.extraTooltipLines(true)) {
            tooltip.add(line);
        }
    }
}
