package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.MobileFieldRegistry;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Shared persisted ownership, polarity control, field lifecycle and friendly
 * target policy for player-built magnetic golems. */
public abstract class MagneticGolem extends IronGolem {
    private static final EntityDataAccessor<Integer> POLARITY =
            SynchedEntityData.defineId(MagneticGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(MagneticGolem.class, EntityDataSerializers.OPTIONAL_UUID);

    protected MagneticGolem(final EntityType<? extends IronGolem> type, final Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POLARITY, MagneticPolarity.NORTH.ordinal());
        builder.define(OWNER, Optional.empty());
    }

    public MagneticPolarity magneticPolarity() {
        final int ordinal = entityData.get(POLARITY);
        return ordinal >= 0 && ordinal < MagneticPolarity.values().length
                ? MagneticPolarity.values()[ordinal] : MagneticPolarity.NORTH;
    }

    public void setMagneticPolarity(final MagneticPolarity polarity) {
        if (polarity != MagneticPolarity.NONE) entityData.set(POLARITY, polarity.ordinal());
    }

    public @Nullable UUID ownerUuid() { return entityData.get(OWNER).orElse(null); }
    public void setOwnerUuid(final @Nullable UUID owner) { entityData.set(OWNER, Optional.ofNullable(owner)); }

    /** Raw, undampened field at the golem's current world position. */
    public abstract @Nullable MagneticField mobileField();

    /** Live per-type config gate. Existing entities are kept save-safe but
     *  become magnetically inert while their type is disabled. */
    public abstract boolean featureEnabled();

    /** The material represented by this body, consumed for a vanilla-sized repair. */
    public abstract Item repairMaterial();

    /** Client-safe effective field for HUDs and the goggles overlay. Server
     * physics uses the authoritative chunk registry; this mirrors nearby live
     * Hematite entities so the displayed tier/range matches it. */
    public @Nullable MagneticField displayedField() {
        if (!featureEnabled()) return null;
        final MagneticField raw = mobileField();
        if (raw == null) return null;
        final int dampeners = level().getEntitiesOfClass(HematiteGolem.class,
                        AABB.ofSize(raw.origin(), 8.0d, 8.0d, 8.0d),
                        hematite -> hematite != this
                                && hematite.featureEnabled()
                                && hematite.position().distanceToSqr(raw.origin()) <= 16.0d)
                .size();
        return raw.withSteppedStrength(MobileFieldRegistry.stepDown(raw.strength(), dampeners));
    }

    /** Hematite is deliberately inert and refuses polarity changes. */
    protected boolean acceptsPolarizer() { return true; }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (featureEnabled() && held.is(repairMaterial())) {
            final float before = getHealth();
            heal(25.0f);
            if (getHealth() == before) return InteractionResult.PASS;
            playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, 1.0f,
                    1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
            held.consume(1, player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // Falling through to IronGolem would repair every mineral body with iron.
        if (featureEnabled() && held.is(Items.IRON_INGOT)) return InteractionResult.PASS;
        if (featureEnabled() && player.isShiftKeyDown() && acceptsPolarizer()
                && held.is(MagItems.HEMATITE_LENS.get())) {
            if (!level().isClientSide) {
                final MagneticPolarity stored = held.get(MagDataComponents.ARMOR_POLARITY.get());
                setMagneticPolarity(stored == null ? MagneticPolarity.NORTH : stored);
                playSound(com.stonytark.magnetization.registry.MagSounds.GOLEM_POLARIZE.get(),
                        0.8f, magneticPolarity() == MagneticPolarity.NORTH ? 1.15f : 0.85f);
                emitParticles(ParticleTypes.ELECTRIC_SPARK, 10);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) return;
        if (!featureEnabled()) {
            MobileFieldRegistry.remove(this);
            return;
        }
        final MagneticField raw = mobileField();
        MobileFieldRegistry.update(server, this, raw);
        if (raw != null) {
            FieldApplicator.applyFromEntity(server, raw, this);
            if (tickCount % 20 == 0) emitParticles(ParticleTypes.ELECTRIC_SPARK, 2);
        }
    }

    protected void emitParticles(final net.minecraft.core.particles.ParticleOptions particle,
                                 final int count) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(particle, getX(), getY() + getBbHeight() * 0.55d, getZ(),
                    count, 0.45d, 0.65d, 0.45d, 0.025d);
        }
    }

    /** Source-aware filter: own field skips the golem, its owner, and allies of
     * either. Command-spawned golems have no owner and therefore skip only self. */
    public boolean protectsFromOwnField(final Entity target) {
        final UUID owner = ownerUuid();
        final boolean sourceAllied = owner != null && target instanceof LivingEntity && isAlliedTo(target);
        boolean ownerAllied = false;
        if (owner != null && level() instanceof ServerLevel server) {
            final Entity ownerEntity = server.getEntity(owner);
            ownerAllied = ownerEntity instanceof LivingEntity living && living.isAlliedTo(target);
        }
        return IronOxideGolemLogic.protectsTarget(getUUID(), owner, target.getUUID(),
                sourceAllied, ownerAllied);
    }

    @Override
    public void remove(final Entity.RemovalReason reason) {
        MobileFieldRegistry.remove(this);
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("MagneticPolarity", magneticPolarity().name());
        final UUID owner = ownerUuid();
        if (owner != null) tag.putUUID("MagneticOwner", owner);
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MagneticPolarity")) {
            setMagneticPolarity(IronOxideGolemLogic.restorePolarity(tag.getString("MagneticPolarity")));
        }
        setOwnerUuid(tag.hasUUID("MagneticOwner") ? tag.getUUID("MagneticOwner") : null);
    }
}
