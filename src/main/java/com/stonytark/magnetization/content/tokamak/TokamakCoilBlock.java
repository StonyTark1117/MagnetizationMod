package com.stonytark.magnetization.content.tokamak;

import net.minecraft.world.level.block.Block;

/**
 * Superconducting confinement coil — the structural ring of the tokamak. Purely
 * passive; the {@link TokamakControllerBlockEntity} scans for a complete ring of
 * these around itself to form the reactor.
 */
public final class TokamakCoilBlock extends Block {

    public TokamakCoilBlock(final Properties props) {
        super(props);
    }
}
