package com.stonytark.magnetization.content.permanent;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PermanentMagnetBlockEntity extends AbstractEmitterBlockEntity {

    public PermanentMagnetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.PERMANENT_MAGNET.get(), pos, state);
    }

    /** Passive magnet — always on, never consumes redstone/FE. Suppresses the
     *  power-source / energy-buffer lines in WTHIT / Jade / goggles tooltips,
     *  which would otherwise wrongly imply it can take power. */
    @Override
    protected boolean acceptsPower() { return false; }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                state.getValue(PermanentMagnetBlock.POLARITY),
                materialStrength(state),
                MagneticField.Shape.OMNIDIRECTIONAL
        );
    }

    /** Engineered rare-earth magnets use the existing strength ladder instead
     *  of registering a parallel emitter type: base permanent magnets are WEAK,
     *  heat-stable SmCo is MEDIUM, and endgame NdFeB is STRONG. */
    static MagneticStrength materialStrength(final BlockState state) {
        if (state.is(MagBlocks.NEODYMIUM_MAGNET.get())) return MagneticStrength.STRONG;
        if (state.is(MagBlocks.SAMARIUM_COBALT_MAGNET.get())) return MagneticStrength.MEDIUM;
        return MagneticStrength.WEAK;
    }
}
