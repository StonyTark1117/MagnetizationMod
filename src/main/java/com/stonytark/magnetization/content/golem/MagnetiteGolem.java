package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MagnetiteGolem extends MagneticGolem {
    private static final EntityDataAccessor<Boolean> OXIDIZED =
            SynchedEntityData.defineId(MagnetiteGolem.class, EntityDataSerializers.BOOLEAN);
    private long oxidationTicks;

    public MagnetiteGolem(final EntityType<? extends IronGolem> type, final Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 100.0d);
    }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(OXIDIZED, false);
    }
    public boolean isOxidized() { return entityData.get(OXIDIZED); }
    public long oxidationTicks() { return oxidationTicks; }
    @Override public boolean featureEnabled() { return MagConfig.magnetiteGolemEnabled(); }
    @Override public net.minecraft.world.item.Item repairMaterial() {
        return isOxidized()
                ? com.stonytark.magnetization.registry.MagItems.MAGHEMITE_INGOT.get()
                : com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get();
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!featureEnabled() || !(level() instanceof ServerLevel) || isOxidized()) return;
        final boolean enabled;
        final long duration;
        try { enabled = MagConfig.MAGNETITE_OXIDATION_ENABLED.get(); }
        catch (final Throwable ignored) { return; }
        try { duration = MagConfig.MAGNETITE_OXIDATION_TICKS.get(); }
        catch (final Throwable ignored) { return; }
        final IronOxideGolemLogic.OxidationProgress progress = IronOxideGolemLogic.advanceOxidation(
                oxidationTicks, isOxidized(), enabled, duration);
        oxidationTicks = progress.ticks();
        if (progress.oxidized() && !isOxidized()) {
            entityData.set(OXIDIZED, true);
            playSound(com.stonytark.magnetization.registry.MagSounds.GOLEM_OXIDIZE.get(), 1.0f, 0.85f);
            emitParticles(net.minecraft.core.particles.ParticleTypes.WAX_OFF, 24);
        }
    }
    @Override public MagneticField mobileField() {
        if (!featureEnabled()) return null;
        return new MagneticField(position().add(0, getBbHeight() * .5d, 0), new Vec3(0, 1, 0),
                magneticPolarity(), isOxidized() ? MagneticStrength.MEDIUM : MagneticStrength.WEAK,
                MagneticField.Shape.OMNIDIRECTIONAL);
    }
    @Override public void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putLong("OxidationTicks", oxidationTicks); tag.putBoolean("Oxidized", isOxidized());
    }
    @Override public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag); oxidationTicks = tag.getLong("OxidationTicks"); entityData.set(OXIDIZED, tag.getBoolean("Oxidized"));
    }
}
