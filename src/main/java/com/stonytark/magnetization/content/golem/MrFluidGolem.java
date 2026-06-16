package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/**
 * MR Fluid Golem — an iron golem made of magnetorheological fluid. Borrows the
 * iron golem's behaviour (attacks hostiles, defends, etc.); differs in:
 * <ul>
 *   <li>slightly less health than an iron golem (80 vs 100);</li>
 *   <li>inherent MR-fluid damage resistance (the fluid soaks impacts);</li>
 *   <li>inside a magnetic field it's CONSTANTLY hardened — near-immune to all
 *       damage and immovable (full knockback resistance), rather than thrown.</li>
 * </ul>
 * The {@code HARDENED} flag is synced so the renderer can show the rigid texture.
 */
public class MrFluidGolem extends IronGolem {

    private static final EntityDataAccessor<Boolean> HARDENED =
            SynchedEntityData.defineId(MrFluidGolem.class, EntityDataSerializers.BOOLEAN);

    /** Inherent mitigation out of a field; near-total while hardened in a field. */
    private static final float BASE_MITIGATION = 0.30f;
    private static final float FIELD_MITIGATION = 0.92f;

    public MrFluidGolem(final EntityType<? extends IronGolem> type, final Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)        // a touch less than the iron golem's 100
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HARDENED, false);
    }

    /** True while the golem is inside a magnetic field (constantly hardened). */
    public boolean isHardened() {
        return this.entityData.get(HARDENED);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && (tickCount % com.stonytark.magnetization.config.MagConfig.golemFieldCheckTicks()) == 0 && level() instanceof ServerLevel server) {
            final boolean inField = MagneticFields.isInField(server, position());
            if (this.entityData.get(HARDENED) != inField) this.entityData.set(HARDENED, inField);
        }
    }

    @Override
    public boolean hurt(final DamageSource source, float amount) {
        if (!level().isClientSide && level() instanceof ServerLevel server
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            final float mit = MagneticFields.isInField(server, position()) ? FIELD_MITIGATION : BASE_MITIGATION;
            amount *= (1.0f - mit);
        }
        return super.hurt(source, amount);
    }
}
