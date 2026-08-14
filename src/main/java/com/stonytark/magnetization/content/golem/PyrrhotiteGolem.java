package com.stonytark.magnetization.content.golem;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteHeatResolver;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PyrrhotiteGolem extends MagneticGolem {
    private static final EntityDataAccessor<Integer> HEAT =
            SynchedEntityData.defineId(PyrrhotiteGolem.class, EntityDataSerializers.INT);
    public PyrrhotiteGolem(final EntityType<? extends IronGolem> type, final Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 90.0d);
    }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(HEAT, BlazeBurnerBlock.HeatLevel.NONE.ordinal());
    }
    public BlazeBurnerBlock.HeatLevel observedHeat() { return BlazeBurnerBlock.HeatLevel.values()[entityData.get(HEAT)]; }
    @Override public void aiStep() {
        if (!level().isClientSide && tickCount % 10 == 0) {
            final BlazeBurnerBlock.HeatLevel previous = observedHeat();
            final BlazeBurnerBlock.HeatLevel resolved = PyrrhotiteHeatResolver.resolve(level(), blockPosition());
            if (resolved != previous) {
                entityData.set(HEAT, resolved.ordinal());
                playSound(com.stonytark.magnetization.registry.MagSounds.GOLEM_HEAT_CHANGE.get(),
                        0.8f, resolved == BlazeBurnerBlock.HeatLevel.NONE ? 0.7f : 1.1f);
                emitParticles(resolved == BlazeBurnerBlock.HeatLevel.NONE
                        ? net.minecraft.core.particles.ParticleTypes.SMOKE
                        : net.minecraft.core.particles.ParticleTypes.FLAME, 12);
            }
        }
        super.aiStep();
    }
    @Override public @Nullable MagneticField mobileField() {
        final MagneticStrength strength = PyrrhotiteHeatResolver.strengthForHeat(observedHeat());
        return strength == null ? null : new MagneticField(position().add(0, getBbHeight() * .5d, 0),
                new Vec3(0, 1, 0), magneticPolarity(), strength, MagneticField.Shape.OMNIDIRECTIONAL);
    }
}
