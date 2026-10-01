package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.compat.RecipeViewerInfo;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real screens, native menu packets and rendering in an isolated connected client. */
@EventBusSubscriber(modid=Magnetization.MOD_ID, value=Dist.CLIENT)
public final class CompatibilityUiAuditClient {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magnetization.audit.ui")) return;
        if (!System.getProperty("magnetization.audit.uiMode", "legacy").equals("legacy")) return;
        if (!net.neoforged.fml.ModList.get().isLoaded("emi") || !net.neoforged.fml.ModList.get().isLoaded("curios")
                || !net.neoforged.fml.ModList.get().isLoaded("patchouli")) return;
        Loaded.tick(event);
    }

    // Keep optional EMI bytecode out of the automatically registered subscriber.
    private static final class Loaded {
        private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit");
        private static int ticks;
        private static boolean finished;
        private static int charmSlot=-1;
        public static void tick(ClientTickEvent.Post event) {
            if(!Boolean.getBoolean("magnetization.audit.ui") || finished) return;
            var mc=Minecraft.getInstance();
            if(mc.player==null || mc.level==null || mc.gameMode==null) return;
            try {
                ticks++;
                switch(ticks) {
                    case 80 -> {
                        require(mc.player.getInventory().getItem(0).is(MagItems.FIELD_COMPASS.get()),"Server compass did not synchronize");
                        mc.player.getInventory().selected=1;
                        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                    }
                    case 110 -> {
                        requireScreen(mc,"vazkii.patchouli.client.book.gui.GuiBookLanding");
                        capture(mc,"ui-patchouli-landing.png");
                    }
                    case 130 -> {
                        var api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
                        api.getClass().getMethod("openBookEntry",ResourceLocation.class,ResourceLocation.class,int.class)
                                .invoke(api,ResourceLocation.parse("magnetization:field_manual"),ResourceLocation.parse("magnetization:machines/iron_oxide_golems"),0);
                    }
                    case 160 -> {
                        requireScreen(mc,"vazkii.patchouli.client.book.gui.GuiBookEntry");
                        capture(mc,"ui-patchouli-entry.png");
                        var spread=mc.screen.getClass().getSuperclass().getDeclaredField("spread");
                        spread.setAccessible(true);
                        int previous=spread.getInt(mc.screen);
                        mc.screen.mouseScrolled(0,0,-1,0);
                        require(spread.getInt(mc.screen)==previous+1,"Patchouli entry did not advance its page spread");
                    }
                    case 165 -> capture(mc,"ui-patchouli-next-page.png");
                    case 166 -> {
                        var api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
                        api.getClass().getMethod("openBookEntry",ResourceLocation.class,ResourceLocation.class,int.class)
                                .invoke(api,ResourceLocation.parse("magnetization:field_manual"),ResourceLocation.parse("magnetization:advanced/compatibility"),6);
                    }
                    case 168 -> {
                        requireScreen(mc,"vazkii.patchouli.client.book.gui.GuiBookEntry");
                        capture(mc,"ui-patchouli-audit.png");
                    }
                    case 170 -> {
                        var api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
                        api.getClass().getMethod("openBookEntry",ResourceLocation.class,ResourceLocation.class,int.class)
                                .invoke(api,ResourceLocation.parse("magnetization:field_manual"),ResourceLocation.parse("magnetization:advanced/compatibility"),10);
                    }
                    case 180 -> {
                        requireScreen(mc,"vazkii.patchouli.client.book.gui.GuiBookEntry");
                        capture(mc,"ui-patchouli-compatibility.png");
                        mc.setScreen(null);
                        var payload=(net.minecraft.network.protocol.common.custom.CustomPacketPayload)Class.forName("top.theillusivec4.curios.common.network.client.CPacketOpenCurios")
                                .getConstructor(ItemStack.class).newInstance(ItemStack.EMPTY);
                        PacketDistributor.sendToServer(payload);
                    }
                    case 220 -> {
                        requireScreen(mc,"top.theillusivec4.curios.client.gui.CuriosScreen");
                        var menu=mc.player.containerMenu;
                        int source=-1;
                        for(var slot:menu.slots) {
                            if(slot.getItem().is(MagItems.FIELD_COMPASS.get())) source=slot.index;
                            if(slot.getClass().getName().equals("top.theillusivec4.curios.common.inventory.CurioSlot")
                                    && slot.getClass().getMethod("getIdentifier").invoke(slot).equals("charm")) charmSlot=slot.index;
                        }
                        require(source>=0 && charmSlot>=0,"Curios menu missing source item or charm slot");
                        mc.gameMode.handleInventoryMouseClick(menu.containerId,source,0,ClickType.PICKUP,mc.player);
                        mc.gameMode.handleInventoryMouseClick(menu.containerId,charmSlot,0,ClickType.PICKUP,mc.player);
                    }
                    case 260 -> {
                        require(mc.player.containerMenu.getSlot(charmSlot).getItem().is(MagItems.FIELD_COMPASS.get()),"Native Curios equip did not synchronize");
                        require(mc.player.containerMenu.getCarried().isEmpty(),"Curios equip left the item on cursor");
                        capture(mc,"ui-curios-equipped.png");
                        LOG.info("UI_CURIOS_PASS charmSlot={} equipped={}",charmSlot,mc.player.containerMenu.getSlot(charmSlot).getItem());
                        mc.player.closeContainer();
                        mc.setScreen(new InventoryScreen(mc.player));
                        dev.emi.emi.api.EmiApi.setSearchText("@magnetization");
                    }
                    case 300 -> {
                        int pages=0;
                        for(var topic:RecipeViewerInfo.topics()) {
                            if(topic.resolveStacks().isEmpty()) continue;
                            var id=ResourceLocation.fromNamespaceAndPath(topic.id().getNamespace(),"/"+topic.id().getPath());
                            require(dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(id)!=null,"Missing EMI information page "+id); pages++;
                        }
                        require(pages>0,"No EMI information pages");
                        LOG.info("UI_EMI_PAGES_RESOLVED count={}",pages);
                        capture(mc,"ui-emi-search.png");
                        var recipe=dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse("magnetization:/info/dipole_electromagnet"));
                        require(recipe!=null,"Missing dipole information recipe");
                        dev.emi.emi.api.EmiApi.displayRecipe(recipe);
                    }
                    case 340 -> {
                        requireScreen(mc,"dev.emi.emi.screen.RecipeScreen");
                        capture(mc,"ui-emi-info.png");
                        dev.emi.emi.api.EmiApi.displayRecipes(dev.emi.emi.api.stack.EmiStack.of(MagItems.FIELD_COMPASS.get()));
                    }
                    case 380 -> {
                        requireScreen(mc,"dev.emi.emi.screen.RecipeScreen");
                        capture(mc,"ui-emi-crafting.png");
                        LOG.info("UI_AUDIT_PASS patchouli=item_open_entry_page_navigation curios=server_menu_equip emi=search_info_crafting");
                        finished=true;
                    }
                    default -> { }
                }
            } catch(Exception|AssertionError e) { finished=true; LOG.error("UI_AUDIT_FAILED tick="+ticks,e); }
        }
        private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
        private static void requireScreen(Minecraft mc,String name) {
            require(mc.screen!=null && mc.screen.getClass().getName().equals(name),"Expected "+name+", got "+mc.screen);
        }
        private static void capture(Minecraft mc,String filename) {
            mc.getToasts().clear();
            Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),message -> LOG.info("UI_CAPTURE {} {}",filename,message.getString()));
        }
    }
}
