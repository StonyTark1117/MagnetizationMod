package com.stonytark.magnetization.content.repulsor;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RepulsorCoilBlockEntity extends AbstractEmitterBlockEntity {

    public RepulsorCoilBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.REPULSOR_COIL.get(), pos, state);
    }

    @Override
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        try { return MagConfig.REPULSOR_MAX_RANGE.get() / 2.0d; }
        catch (final Throwable t) { return tier.range(); }
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (!isPowered()) return null;
        // Axis is the placement direction. UP for the classic hover pad;
        // NORTH/SOUTH/EAST/WEST/DOWN for tunnel-style propulsion lanes.
        final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING)
                : Direction.UP;
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        final MagneticStrength strength = effectiveStrength(MagneticStrength.MEDIUM);
        final double range = effectiveRange(strength);
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                axis,
                effectivePolarity(MagneticPolarity.NORTH), // repulsive on a conical field
                strength,
                MagneticField.Shape.CONICAL,
                range == strength.range() ? 0.0d : range
        );
    }
}
