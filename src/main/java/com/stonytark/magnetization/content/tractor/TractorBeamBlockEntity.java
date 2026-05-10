package com.stonytark.magnetization.content.tractor;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class TractorBeamBlockEntity extends AbstractEmitterBlockEntity {

    public TractorBeamBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TRACTOR_BEAM.get(), pos, state);
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (!isPowered()) return null;
        final Direction facing = state.getValue(DirectionalBlock.FACING);
        // Beam shoots along facing; force on targets pulls them backward toward us,
        // which is encoded as polarity SOUTH + axis-along-facing on a DIRECTIONAL field.
        final Vec3 axis = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        final MagneticStrength strength = effectiveStrength(MagneticStrength.STRONG);
        final double range = effectiveRange(strength);
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                axis,
                effectivePolarity(MagneticPolarity.SOUTH),
                strength,
                MagneticField.Shape.DIRECTIONAL,
                range == strength.range() ? 0.0d : range
        );
    }
}
