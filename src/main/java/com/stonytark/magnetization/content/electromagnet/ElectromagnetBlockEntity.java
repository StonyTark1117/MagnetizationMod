package com.stonytark.magnetization.content.electromagnet;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Always-on omnidirectional pull when powered with redstone. Strength tier =
 * MEDIUM. A future iteration can scale strength with Create kinetic stress.
 */
public class ElectromagnetBlockEntity extends AbstractEmitterBlockEntity {

    public ElectromagnetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.ELECTROMAGNET.get(), pos, state);
    }

    @Override
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        try { return MagConfig.ELECTROMAGNET_MAX_RANGE.get() / 2.0d; }
        catch (final Throwable t) { return tier.range(); }
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (!isPowered()) return null;
        final Vec3 origin = Vec3.atCenterOf(getBlockPos());
        final MagneticStrength strength = effectiveStrength(MagneticStrength.MEDIUM);
        return new MagneticField(
                origin,
                new Vec3(0, 1, 0),
                effectivePolarity(MagneticPolarity.SOUTH), // default attracts north-poled magnetizables
                strength,
                MagneticField.Shape.OMNIDIRECTIONAL,
                effectiveRange(strength) == strength.range() ? 0.0d : effectiveRange(strength)
        );
    }
}
