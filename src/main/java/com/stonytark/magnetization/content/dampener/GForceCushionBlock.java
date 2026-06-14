package com.stonytark.magnetization.content.dampener;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * G-Force Cushion (magnetoresistive kinetic dampener). Placed at the bottom of a
 * drop, it senses the incoming velocity of anyone wearing metallic armor and
 * ramps its magnetic field resistance to arrest the fall — negating all fall
 * damage no matter the height. Without metallic armor (the conductor the field
 * grips), it's just a normal landing.
 */
public final class GForceCushionBlock extends Block {

    public GForceCushionBlock(final Properties props) {
        super(props);
    }

    @Override
    public void fallOn(final Level level, final BlockState state, final BlockPos pos,
                       final Entity entity, final float fallDistance) {
        if (entity instanceof LivingEntity living && hasMetallicArmor(living)) {
            // Full magnetoresistive arrest — zero fall damage.
            entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
            if (level instanceof ServerLevel server && fallDistance > 1.0) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        12, 0.4, 0.1, 0.4, 0.04);
                level.playSound(null, pos, SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
            }
            return;
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    private static boolean hasMetallicArmor(final LivingEntity living) {
        for (final ItemStack armor : living.getArmorSlots()) {
            if (armor.is(MagTags.METAL_ARMOR)) return true;
        }
        return false;
    }
}
