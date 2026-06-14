package com.stonytark.magnetization.mixin.client;

import com.stonytark.magnetization.content.AnvilDampenerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deadens the anvil "use" clang (level event 1030) when a magnet from
 * {@code #magnetization:anvil_dampeners} is touching the anvil — the
 * client-side half of {@link AnvilDampenerHandler}. The durability half is
 * server-side via {@code AnvilRepairEvent}.
 */
@Mixin(LevelRenderer.class)
public class AnvilSoundMixin {

    private static final int ANVIL_USED = 1030;

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void magnetization$dampenAnvilSound(final int type, final BlockPos pos,
                                                final int data, final CallbackInfo ci) {
        if (type != ANVIL_USED) return;
        final Level level = Minecraft.getInstance().level;
        if (level == null) return;
        // Deaden when a magnet sits beside the anvil, or the anvil is one of our
        // self-dampened magnetic-metal anvils.
        if (AnvilDampenerHandler.hasAdjacentDampener(level, pos)
                || level.getBlockState(pos).is(com.stonytark.magnetization.api.MagTags.DAMPENED_ANVILS)) {
            ci.cancel();
        }
    }
}
