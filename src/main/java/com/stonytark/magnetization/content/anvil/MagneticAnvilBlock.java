package com.stonytark.magnetization.content.anvil;

import net.minecraft.world.level.block.AnvilBlock;

/**
 * An anvil cast from one of our magnetic metals. Inherits all vanilla anvil
 * behaviour (repair menu, falling, facing); its magnetic identity — a deadened
 * use-clang and a per-metal break chance — is applied externally via
 * {@code #magnetization:dampened_anvils} + {@code AnvilDampenerHandler} /
 * {@code AnvilSoundMixin}, so the block itself stays trivial. (Inherits
 * {@code AnvilBlock}'s codec — fine for a placed block.)
 */
public final class MagneticAnvilBlock extends AnvilBlock {

    public MagneticAnvilBlock(final Properties props) {
        super(props);
    }
}
