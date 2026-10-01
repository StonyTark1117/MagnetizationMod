package com.stonytark.magnetization.mixin.client;

import com.stonytark.magnetization.client.UiAuditText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inert outside the explicit UI audit; observes, never replaces native rendering. */
@Mixin(GuiGraphics.class)
public abstract class UiAuditTextMixin {
    private boolean auditVisible(Font font, float x, float y) {
        var graphics = (GuiGraphics) (Object) this;
        var point = graphics.pose().last().pose().transformPosition(new org.joml.Vector3f(x + 1, y + font.lineHeight / 2f, 0));
        // REI's Cloth Config scissors bypass GuiGraphics' own scissor stack.
        if (System.getProperty("magnetization.audit.uiMode", "").equals("rei")
                && org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)) {
            int[] bounds = new int[4];
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, bounds);
            var window = net.minecraft.client.Minecraft.getInstance().getWindow();
            double px = point.x * window.getGuiScale();
            double py = window.getHeight() - point.y * window.getGuiScale();
            if (px < bounds[0] || py < bounds[1] || px >= bounds[0] + bounds[2] || py >= bounds[1] + bounds[3]) return false;
        }
        return point.x >= 0 && point.y >= 0 && point.x < graphics.guiWidth() && point.y < graphics.guiHeight()
                && graphics.containsPointInScissor((int) point.x, (int) point.y);
    }
    @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at=@At("HEAD"))
    private void auditString(Font font, String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> ci) {
        if (!UiAuditText.ENABLED) return;
        UiAuditText.guiDepth++;
        if (text != null && auditVisible(font, x, y)) UiAuditText.record(text);
    }
    @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at=@At("HEAD"))
    private void auditSequence(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> ci) {
        if (!UiAuditText.ENABLED) return;
        UiAuditText.guiDepth++;
        if (!auditVisible(font, x, y)) return;
        StringBuilder value=new StringBuilder();
        text.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
        UiAuditText.record(value.toString());
    }
    @Inject(method={"drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I"}, at=@At("RETURN"))
    private void auditEnd(CallbackInfoReturnable<Integer> ci) {
        if(UiAuditText.ENABLED) UiAuditText.guiDepth--;
    }
    @Inject(method="renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at=@At("HEAD"))
    private void auditItem(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.level.Level level, net.minecraft.world.item.ItemStack stack, int x, int y, int seed, int offset, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if(UiAuditText.ENABLED) UiAuditText.recordItem(stack);
    }
}
