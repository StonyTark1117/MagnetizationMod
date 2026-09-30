package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Supplies a real survival player's inventory for the opt-in UI audit. */
@EventBusSubscriber(modid=Magnetization.MOD_ID)
public final class CompatibilityUiAudit {
    private static boolean supplied;
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("magnetization.audit.ui") || supplied) return;
        var player=event.getServer().getPlayerList().getPlayerByName("AuditUi");
        if(player==null) return;
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().setItem(0,new ItemStack(MagItems.FIELD_COMPASS.get()));
        var api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
        var book=(ItemStack)api.getClass().getMethod("getBookStack",ResourceLocation.class)
                .invoke(api,ResourceLocation.parse("magnetization:field_manual"));
        if(book.isEmpty()) throw new IllegalStateException("Patchouli manual missing");
        player.getInventory().setItem(1,book);
        player.inventoryMenu.broadcastChanges(); supplied=true;
        org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit").info("UI_AUDIT_SERVER_READY manual={} compass={}",book,player.getInventory().getItem(0));
    }
}
