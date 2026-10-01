package com.stonytark.magnetization.mixin.client;

import com.stonytark.magnetization.client.UiAuditText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native goggles and JER item tooltips draw text directly through Font. */
@Mixin(Font.class)
public abstract class UiAuditFontMixin {
    @Inject(method="drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at=@At("HEAD"))
    private void auditText(FormattedCharSequence text, float x, float y, int color, boolean shadow,
            Matrix4f matrix, MultiBufferSource buffers, Font.DisplayMode mode, int background, int light,
            CallbackInfoReturnable<Integer> ci) {
        if(!UiAuditText.ENABLED || UiAuditText.guiDepth!=0 || !java.util.List.of("goggles", "jer").contains(System.getProperty("magnetization.audit.uiMode",""))) return;
        var mc=Minecraft.getInstance();
        var point=matrix.transformPosition(new org.joml.Vector3f(x+1,y+4,0));
        if(point.x<0 || point.y<0 || point.x>=mc.getWindow().getGuiScaledWidth() || point.y>=mc.getWindow().getGuiScaledHeight()) return;
        StringBuilder value=new StringBuilder();
        text.accept((index,style,codepoint)->{value.appendCodePoint(codepoint);return true;});
        UiAuditText.record(value.toString());
    }
}
