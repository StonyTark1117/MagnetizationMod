package com.stonytark.magnetization.compat.top;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticFieldSource;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public enum EmitterProbeProvider implements IProbeInfoProvider {
    INSTANCE;

    private static final ResourceLocation ID = Magnetization.id("emitter_probe");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public void addProbeInfo(
            final ProbeMode mode, final IProbeInfo probeInfo, final Player player,
            final Level level, final BlockState state, final IProbeHitData data
    ) {
        final BlockEntity be = level.getBlockEntity(data.getPos());
        if (!(be instanceof MagneticFieldSource source)) return;
        for (Component line : FieldTooltipFormatter.format(source.currentField(), true)) {
            probeInfo.text(line);
        }
    }
}
