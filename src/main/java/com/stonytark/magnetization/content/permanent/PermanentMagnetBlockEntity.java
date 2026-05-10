package com.stonytark.magnetization.content.permanent;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PermanentMagnetBlockEntity extends AbstractEmitterBlockEntity {

    public PermanentMagnetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.PERMANENT_MAGNET.get(), pos, state);
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                state.getValue(PermanentMagnetBlock.POLARITY),
                MagneticStrength.WEAK,
                MagneticField.Shape.OMNIDIRECTIONAL
        );
    }
}
