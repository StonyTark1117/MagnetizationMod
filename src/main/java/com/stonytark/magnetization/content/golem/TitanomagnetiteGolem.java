package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.MagneticFields;
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
import org.jetbrains.annotations.Nullable;

public final class TitanomagnetiteGolem extends MagneticGolem {
    private static final EntityDataAccessor<Boolean> CHARGED =
            SynchedEntityData.defineId(TitanomagnetiteGolem.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RECORDED_STRENGTH =
            SynchedEntityData.defineId(TitanomagnetiteGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RECORDED_SHAPE =
            SynchedEntityData.defineId(TitanomagnetiteGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AXIS_X =
            SynchedEntityData.defineId(TitanomagnetiteGolem.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AXIS_Y =
            SynchedEntityData.defineId(TitanomagnetiteGolem.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AXIS_Z =
            SynchedEntityData.defineId(TitanomagnetiteGolem.class, EntityDataSerializers.FLOAT);
    private @Nullable MagneticField recorded;
    public TitanomagnetiteGolem(final EntityType<? extends IronGolem> type, final Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 120.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d);
    }
    @Override protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(CHARGED, false);
        builder.define(RECORDED_STRENGTH, MagneticStrength.NONE.ordinal());
        builder.define(RECORDED_SHAPE, MagneticField.Shape.OMNIDIRECTIONAL.ordinal());
        builder.define(AXIS_X, 0.0f); builder.define(AXIS_Y, 1.0f); builder.define(AXIS_Z, 0.0f);
    }
    public boolean isCharged() { return entityData.get(CHARGED); }
    @Override public boolean featureEnabled() { return MagConfig.titanomagnetiteGolemEnabled(); }
    @Override public net.minecraft.world.item.Item repairMaterial() {
        return com.stonytark.magnetization.registry.MagItems.TITANOMAGNETITE_INGOT.get();
    }
    public @Nullable MagneticField recordedField() {
        return recorded != null ? recorded : syncedRecorded();
    }

    private static boolean sameRecording(final @Nullable MagneticField first,
                                         final MagneticField second) {
        return first != null && first.axis().equals(second.axis())
                && first.polarity() == second.polarity()
                && first.strength() == second.strength()
                && first.shape() == second.shape();
    }

    @Override public void aiStep() {
        if (featureEnabled() && !level().isClientSide
                && level() instanceof ServerLevel server && tickCount % 10 == 0) {
            final MagneticField found = MagneticFields.strongestField(server, position(), getUUID(), true);
            if (found != null) {
                final MagneticField captured = IronOxideGolemLogic.captureSnapshot(found, position());
                final boolean changed = !sameRecording(recorded, captured);
                recorded = captured;
                syncRecorded(recorded);
                setMagneticPolarity(found.polarity());
                entityData.set(CHARGED, true);
                if (changed) {
                    playSound(com.stonytark.magnetization.registry.MagSounds.GOLEM_CAPTURE.get(), 1.0f, 0.9f);
                    emitParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, 20);
                }
            }
        }
        super.aiStep();
    }
    @Override public @Nullable MagneticField mobileField() {
        if (!featureEnabled()) return null;
        final MagneticField snapshot = recorded != null ? recorded : syncedRecorded();
        return snapshot == null ? null : new MagneticField(position().add(0, getBbHeight() * .5d, 0),
                snapshot.axis(), magneticPolarity(), snapshot.strength(), snapshot.shape());
    }
    private void syncRecorded(final MagneticField field) {
        entityData.set(RECORDED_STRENGTH, field.strength().ordinal());
        entityData.set(RECORDED_SHAPE, field.shape().ordinal());
        entityData.set(AXIS_X, (float) field.axis().x); entityData.set(AXIS_Y, (float) field.axis().y); entityData.set(AXIS_Z, (float) field.axis().z);
        entityData.set(CHARGED, true);
    }
    private @Nullable MagneticField syncedRecorded() {
        if (!isCharged()) return null;
        return new MagneticField(position(), new net.minecraft.world.phys.Vec3(entityData.get(AXIS_X), entityData.get(AXIS_Y), entityData.get(AXIS_Z)),
                magneticPolarity(), MagneticStrength.values()[entityData.get(RECORDED_STRENGTH)],
                MagneticField.Shape.values()[entityData.get(RECORDED_SHAPE)]);
    }
    @Override public void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag); if (recorded != null) tag.put("RecordedField", recorded.toNbt());
    }
    @Override public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag); recorded = tag.contains("RecordedField") ? MagneticField.fromNbt(tag.getCompound("RecordedField")) : null;
        if (recorded != null) syncRecorded(recorded); else entityData.set(CHARGED, false);
    }
}
