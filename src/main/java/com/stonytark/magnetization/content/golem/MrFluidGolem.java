package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final net.minecraft.resources.ResourceLocation HARDENED_KNOCKBACK_ID =
            com.stonytark.magnetization.Magnetization.id("mr_fluid_hardened_knockback");
    private static final AttributeModifier HARDENED_KNOCKBACK = new AttributeModifier(
            HARDENED_KNOCKBACK_ID, 1.0d, AttributeModifier.Operation.ADD_VALUE);

    public MrFluidGolem(final EntityType<? extends IronGolem> type, final Level level) {
        super(type, level);
        // MR Fluid Golems only originate from their crafted spawn egg. Treat them
        // as constructed guardians rather than naturally spawned village golems.
        setPlayerCreated(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)        // a touch less than the iron golem's 100
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0); // fluid until a field locks it rigid
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HARDENED, false);
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Older MR Golems inherited the natural Iron Golem default. They can only
        // be crafted, so migrate those existing entities to constructed guardians.
        setPlayerCreated(true);
    }

    /** True while the golem is inside a magnetic field (constantly hardened). */
    public boolean isHardened() {
        return this.entityData.get(HARDENED);
    }

    public boolean featureEnabled() {
        return MagConfig.mrFluidGolemEnabled();
    }

    /** Material mitigation represented by the currently synchronized state. */
    public float currentMitigation() {
        if (!featureEnabled()) return 0.0f;
        return isHardened() ? MagConfig.mrGolemFieldMitigation() : MagConfig.mrGolemBaseMitigation();
    }

    private void refreshHardening(final ServerLevel server) {
        setHardened(MagneticFields.isInField(server, position()));
    }

    private void setHardened(final boolean hardened) {
        final var knockback = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            if (hardened && !knockback.hasModifier(HARDENED_KNOCKBACK_ID)) {
                knockback.addTransientModifier(HARDENED_KNOCKBACK);
            } else if (!hardened && knockback.hasModifier(HARDENED_KNOCKBACK_ID)) {
                knockback.removeModifier(HARDENED_KNOCKBACK_ID);
            }
        }
        if (this.entityData.get(HARDENED) == hardened) return;
        this.entityData.set(HARDENED, hardened);
        if (level() instanceof ServerLevel server) {
            playSound((hardened ? MagSounds.GOLEM_HARDEN : MagSounds.GOLEM_SOFTEN).get(),
                    0.9f, hardened ? 0.8f : 1.15f);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            (hardened ? MagBlocks.HARDENED_MR_FLUID : MagBlocks.MR_FLUID_BLOCK)
                                    .get().defaultBlockState()),
                    getX(), getY() + getBbHeight() * 0.55d, getZ(),
                    14, 0.45d, 0.65d, 0.45d, 0.03d);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!featureEnabled()) {
            if (!level().isClientSide) setHardened(false);
            return;
        }
        if (!level().isClientSide && (tickCount % com.stonytark.magnetization.config.MagConfig.golemFieldCheckTicks()) == 0 && level() instanceof ServerLevel server) {
            refreshHardening(server);
        }
    }

    @Override
    public boolean hurt(final DamageSource source, float amount) {
        if (featureEnabled() && !level().isClientSide && level() instanceof ServerLevel server
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Damage must not observe a stale state between periodic field checks.
            refreshHardening(server);
            amount *= (1.0f - currentMitigation());
        }
        return super.hurt(source, amount);
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (featureEnabled() && held.is(MagItems.MR_FLUID_BUCKET.get()) && getHealth() < getMaxHealth()) {
            if (!level().isClientSide) {
                heal(25.0f);
                playSound(MagSounds.GOLEM_SOFTEN.get(), 0.8f, 1.25f);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    final ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                    if (held.isEmpty()) player.setItemInHand(hand, emptyBucket);
                    else if (!player.addItem(emptyBucket)) player.drop(emptyBucket, false);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // IronGolem repairs with iron ingots. This body is fluid, so deliberately
        // suppress that inherited material interaction while the feature is live.
        if (featureEnabled() && held.is(Items.IRON_INGOT)) return InteractionResult.PASS;
        return super.mobInteract(player, hand);
    }
}
