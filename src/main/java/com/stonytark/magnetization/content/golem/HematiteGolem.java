package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;

public final class HematiteGolem extends MagneticGolem {
    private static final EntityDataAccessor<Integer> DAMPENED_SOURCES =
            SynchedEntityData.defineId(HematiteGolem.class, EntityDataSerializers.INT);
    public HematiteGolem(final EntityType<? extends IronGolem> type, final Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 110.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d);
    }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(DAMPENED_SOURCES, 0);
    }
    @Override public boolean featureEnabled() { return MagConfig.hematiteGolemEnabled(); }
    @Override public net.minecraft.world.item.Item repairMaterial() {
        return com.stonytark.magnetization.registry.MagItems.HEMATITE_INGOT.get();
    }
    @Override public void aiStep() {
        if (featureEnabled() && !level().isClientSide
                && level() instanceof ServerLevel server && tickCount % 10 == 0) {
            final int previous = dampenedSourceCount();
            final int current = com.stonytark.magnetization.physics.MagneticFields.countSourcesNear(
                    server, position(), 4.0d, getUUID());
            entityData.set(DAMPENED_SOURCES, current);
            if (current != previous && current > 0) {
                playSound(com.stonytark.magnetization.registry.MagSounds.GOLEM_DAMPEN.get(), 0.65f, 0.7f);
                emitParticles(net.minecraft.core.particles.ParticleTypes.ASH, 10);
            }
        }
        super.aiStep();
    }
    public int dampenedSourceCount() { return entityData.get(DAMPENED_SOURCES); }
    @Override protected boolean acceptsPolarizer() { return false; }
    @Override public @Nullable MagneticField mobileField() { return null; }
}
