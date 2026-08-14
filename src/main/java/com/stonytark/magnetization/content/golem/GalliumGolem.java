package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Gallium Golem — an iron golem cast from gallium. Built with the same multiblock
 * as an iron golem but using solid gallium (see {@code GalliumGolemSpawnHandler});
 * a gallium palette-swap of the iron golem otherwise, and it behaves like one.
 *
 * <p>It is NOT magnetically susceptible (it's an inert metal mob). Being soft
 * gallium it's weaker than a normal golem even at its best — lower health and no
 * knockback resistance. Gallium also melts above room temperature: outside a cold
 * biome it takes more damage (less rigid) and after a while melts away, leaving a
 * gallium fluid source where it stood. Killed by other means, it shatters into
 * solid gallium pieces (loot table).
 */
public class GalliumGolem extends IronGolem {


    private int warmTicks = 0;
    /** Set on the melt path so the death/loot drop is skipped (we leave a fluid source instead). */
    private boolean melting = false;

    public GalliumGolem(final EntityType<? extends IronGolem> type, final Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)            // weaker than iron's 100, even in the cold
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);  // soft — no knockback resistance
    }

    private boolean inColdBiome() {
        return level().getBiome(blockPosition()).value().getBaseTemperature() < 0.2f;
    }

    public boolean featureEnabled() {
        return MagConfig.galliumGolemEnabled();
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (featureEnabled() && held.is(com.stonytark.magnetization.registry.MagItems.GALLIUM_INGOT.get())) {
            final float before = getHealth();
            heal(25.0f);
            if (getHealth() == before) return InteractionResult.PASS;
            playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, 0.8f,
                    1.2f + (random.nextFloat() - random.nextFloat()) * 0.15f);
            held.consume(1, player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (featureEnabled() && held.is(Items.IRON_INGOT)) return InteractionResult.PASS;
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel server)) return;
        if (!featureEnabled()) {
            warmTicks = 0;
            return;
        }
        if (inColdBiome()) {
            warmTicks = 0;
            return;
        }
        if (++warmTicks >= com.stonytark.magnetization.config.MagConfig.galliumGolemMeltTicks()) melt(server);
    }

    /** Melt away: leave a gallium fluid source where it stood, no loot. */
    private void melt(final ServerLevel server) {
        melting = true;
        final BlockPos pos = blockPosition();
        if (server.getBlockState(pos).canBeReplaced()) {
            server.setBlock(pos, MagBlocks.GALLIUM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        server.levelEvent(2001, pos, Block.getId(MagBlocks.SOLID_GALLIUM.get().defaultBlockState()));
        discard(); // remove without a death (so no solid-gallium loot drops)
    }

    @Override
    public boolean hurt(final DamageSource source, float amount) {
        if (featureEnabled() && !level().isClientSide
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !inColdBiome()) {
            amount *= com.stonytark.magnetization.config.MagConfig.galliumGolemWarmDamageMult(); // softer when warm
        }
        return super.hurt(source, amount);
    }

    /** True while melting — used to suppress the shatter loot on the melt path. */
    public boolean isMelting() {
        return melting;
    }
}
