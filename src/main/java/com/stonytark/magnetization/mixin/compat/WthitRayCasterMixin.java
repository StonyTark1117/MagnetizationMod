package com.stonytark.magnetization.mixin.compat;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.ObjDoubleConsumer;

/** WTHIT traverses main-level cells directly, bypassing Sable's Level.clip. */
@Pseudo
@Mixin(targets="mcp.mobius.waila.gui.hud.RayCaster", remap=false)
public abstract class WthitRayCasterMixin {
    @Inject(method="cast", at=@At("TAIL"))
    private static void magnetization$shipBlock(Level level, Entity entity, Vec3 start, Vec3 direction,
            double range, ObjDoubleConsumer<HitResult> consumer, CallbackInfo ci) {
        if (!mcp.mobius.waila.config.PluginConfig.CLIENT.getBoolean(mcp.mobius.waila.api.WailaConstants.CONFIG_SHOW_BLOCK)) return;
        var hit=level.clip(new ClipContext(start,start.add(direction.scale(range)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,entity));
        if(hit.getType()!=HitResult.Type.BLOCK || Sable.HELPER.getContaining(level,hit.getBlockPos())==null) return;
        // Feed a real sublevel hit into the upstream nearest-hit selection. Its
        // plot coordinates identify the BE; its distance must be measured in world space.
        consumer.accept(hit,Sable.HELPER.distanceSquaredWithSubLevels(level,start,hit.getLocation()));
    }
}
