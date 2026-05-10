package com.stonytark.magnetization.content.electromagnet;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
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
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (!isPowered()) return null;
        final Vec3 origin = Vec3.atCenterOf(getBlockPos());
        return new MagneticField(
                origin,
                new Vec3(0, 1, 0),
                MagneticPolarity.SOUTH, // attract north-poled magnetizables
                MagneticStrength.MEDIUM,
                MagneticField.Shape.OMNIDIRECTIONAL
        );
    }
}
