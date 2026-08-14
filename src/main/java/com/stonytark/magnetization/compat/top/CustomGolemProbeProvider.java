package com.stonytark.magnetization.compat.top;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.CustomGolemHud;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoEntityProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** All custom golem state lines for The One Probe. */
public enum CustomGolemProbeProvider implements IProbeInfoEntityProvider {
    INSTANCE;

    @Override
    public String getID() {
        return Magnetization.id("custom_golem_probe").toString();
    }

    @Override
    public void addProbeEntityInfo(final ProbeMode mode, final IProbeInfo probeInfo,
                                   final Player player, final Level level, final Entity entity,
                                   final IProbeHitEntityData data) {
        CustomGolemHud.lines(entity).forEach(probeInfo::text);
    }
}
