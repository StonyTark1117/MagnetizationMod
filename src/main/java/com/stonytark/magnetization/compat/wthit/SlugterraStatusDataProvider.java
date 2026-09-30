package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagEffects;
import mcp.mobius.waila.api.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/** Vanilla does not synchronize every mob's full effect list to observing clients. */
public enum SlugterraStatusDataProvider implements IDataProvider<LivingEntity> {
    INSTANCE;
    static final String KEY = "magnetization:slugterra_status";
    static boolean supported(LivingEntity entity) {
        final String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        return namespace.equals("slugterra") || namespace.equals("slugterra_dark");
    }
    @Override public void appendData(IDataWriter writer, IServerAccessor<LivingEntity> accessor, IPluginConfig config) {
        final var entity = accessor.getTarget();
        if (!MagConfig.wthitCompatEnabled() || !MagConfig.slugterraCompatEnabled() || !supported(entity)) return;
        final var effect = entity.getEffect(MagEffects.MAGNETIZED);
        if (effect == null) return;
        final var data = new CompoundTag();
        data.putUUID("entity", entity.getUUID());
        data.putInt("amplifier", effect.getAmplifier());
        data.putBoolean("infinite", effect.isInfiniteDuration());
        data.putLong("expires", accessor.getLevel().getGameTime() + effect.getDuration());
        writer.raw().put(KEY, data);
    }
}
