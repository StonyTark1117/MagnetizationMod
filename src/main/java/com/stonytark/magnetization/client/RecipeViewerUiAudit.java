package com.stonytark.magnetization.client;

import com.stonytark.magnetization.compat.RecipeViewerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.List;

/** Opens registered recipes in upstream screens and checks actual rendered text. */
@EventBusSubscriber(modid="magnetization", value=Dist.CLIENT)
public final class RecipeViewerUiAudit {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/ui-audit");
    private static int ticks, index, wait;
    private static boolean finished, opened, selectedTopic;
    private static final java.util.Set<String> observed = new java.util.LinkedHashSet<>();
    private static int fragment;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magnetization.audit.ui") || finished) return;
        String mode=System.getProperty("magnetization.audit.uiMode", "legacy");
        if (!List.of("emi","jei","rei","jer").contains(mode)) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        try {
            mc.getToasts().clear();
            if(++ticks<160) return;
            if(mode.equals("jer")) { Jer.tick(mc); return; }
            var topics=RecipeViewerInfo.topics().stream().filter(t -> !t.resolveStacks().isEmpty()).toList();
            if(index==topics.size()) {
                LOG.info("UI_AUDIT_PASS viewer={} native_information_pages={} rendered_text_verified=true",mode,index);
                finished=true; return;
            }
            var topic=topics.get(index);
            if(!opened) {
                mc.setScreen(new InventoryScreen(mc.player));
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);
                switch(mode) { case "emi" -> Emi.open(topic); case "jei" -> Jei.open(topic); case "rei" -> Rei.open(topic); }
                opened=true; wait=0; fragment=0; selectedTopic=false; observed.clear(); return;
            }
            if(++wait<20) return;
            String rendered=normalize(String.join(" ",UiAuditText.lines()));
            if (mode.equals("emi")) {
                for(String line:UiAuditText.lines()) observed.add(normalize(line));
                boolean complete=topic.descriptions().stream().flatMap(d -> mc.font.split(d,140).stream()).allMatch(line -> {
                    StringBuilder text=new StringBuilder();line.accept((i,style,cp)->{text.appendCodePoint(cp);return true;});
                    return observed.contains(normalize(text.toString()));
                });
                if (!complete) {
                    if (wait%20==0) {
                        capture(mc,"ui-emi-"+topic.id().getPath().replace('/','-')+"-part"+(++fragment)+".png");
                        require(Emi.next(mc), "EMI cannot navigate remaining description text for "+topic.id()+": "+observed);
                    }
                    require(wait<240,"EMI description navigation timed out "+topic.id()); return;
                }
                rendered=expectedText(topic);
            }
            String expected=normalize(topic.descriptions().getFirst().getString());
            if((mode.equals("rei") || mode.equals("jei")) && (selectedTopic || rendered.contains(expected.substring(0,Math.min(expected.length(),60))))) {
                selectedTopic=true;
                for(String line:UiAuditText.lines())observed.add(normalize(line));
                int textWidth=mode.equals("rei") ? Rei.textWidth(mc) : 154;
                boolean complete=topic.descriptions().stream().flatMap(d -> mc.font.split(d,textWidth).stream()).allMatch(line -> {
                    StringBuilder value=new StringBuilder();line.accept((i,style,cp)->{value.appendCodePoint(cp);return true;});return observed.contains(normalize(value.toString()));
                });
                if(!complete) {
                    if(wait%20==0){capture(mc,"ui-"+mode+"-"+topic.id().getPath().replace('/','-')+"-part"+(++fragment)+".png");if(mode.equals("rei")) Rei.scroll(mc); else Jei.scroll(mc);}
                    require(wait<600,mode+" text scrolling timed out "+topic.id()+": "+observed);return;
                }
                rendered=expectedText(topic);
            }
            String prefix=expected.substring(0,Math.min(expected.length(),60));
            if(!rendered.contains(prefix)) {
                if(mode.equals("rei") && wait%15==0 && mc.screen!=null) Rei.next(mc);
                if(wait<240) return;
                throw new IllegalStateException("Native "+mode+" screen missing "+topic.id()+" text: "+rendered);
            }
            require(mc.screen!=null && !(mc.screen instanceof InventoryScreen),"Viewer did not open a recipe screen");
            capture(mc,"ui-"+mode+"-"+topic.id().getPath().replace('/','-')+".png");
            LOG.info("UI_VIEWER_PAGE viewer={} topic={} screen={} rendered={}",mode,topic.id(),mc.screen.getClass().getName(),UiAuditText.lines());
            index++;opened=false;
        } catch(Exception|AssertionError e) { finished=true; LOG.error("UI_AUDIT_FAILED viewer="+mode+" tick="+ticks,e); }
    }
    static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    static String expectedText(RecipeViewerInfo.Topic topic) { return normalize(topic.descriptions().stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "))); }
    static String normalize(String s) { return s.replaceAll("§.","").replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT); }
    static void capture(Minecraft mc,String filename) {
        mc.getToasts().clear(); Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),message -> LOG.info("UI_CAPTURE {} {}",filename,message.getString()));
    }
    private static class Emi {
        static boolean next(Minecraft mc) throws Exception {
            var field=mc.screen.getClass().getDeclaredField("currentPage");field.setAccessible(true);
            var groups=(java.util.List<?>)field.get(mc.screen);
            for(Object object:groups) {
                var group=(dev.emi.emi.screen.WidgetGroup)object;
                for(var widget:group.widgets) if(widget instanceof dev.emi.emi.api.widget.ButtonWidget && widget.getBounds().x()>group.width/2) {
                    var b=widget.getBounds();
                    mc.screen.mouseClicked(group.x+b.x()+2,group.y+b.y()+2,0);
                    mc.screen.mouseReleased(group.x+b.x()+2,group.y+b.y()+2,0);return true;
                }
            }
            return false;
        }
        static void open(RecipeViewerInfo.Topic topic) {
            var id=ResourceLocation.fromNamespaceAndPath(topic.id().getNamespace(),"/"+topic.id().getPath());
            var recipe=dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(id);
            require(recipe!=null,"Missing EMI page "+id); dev.emi.emi.api.EmiApi.displayRecipe(recipe);
        }
    }
    private static class Jei {
        static void scroll(Minecraft mc) { mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -2); }
        static mezz.jei.api.runtime.IJeiRuntime runtime() throws Exception {
            return (mezz.jei.api.runtime.IJeiRuntime)Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        }
        static void open(RecipeViewerInfo.Topic topic) throws Exception {
            var rt=runtime(); var type=mezz.jei.api.constants.RecipeTypes.INFORMATION;
            String prefix=normalize(topic.descriptions().getFirst().getString());
            var recipes=rt.getRecipeManager().createRecipeLookup(type).get().filter(r -> normalize(r.getDescription().stream().map(net.minecraft.network.chat.FormattedText::getString).collect(java.util.stream.Collectors.joining(" "))).contains(prefix)).toList();
            require(!recipes.isEmpty(),"Missing registered JEI information recipe "+topic.id());
            rt.getRecipesGui().showRecipes(rt.getRecipeManager().getRecipeCategory(type),recipes,List.of());
        }
    }
    private static class Rei {
        static int textWidth(Minecraft mc)throws Exception {return textBounds(mc).width-11;}
        static void scroll(Minecraft mc)throws Exception {var bounds=textBounds(mc);mc.screen.mouseScrolled(bounds.getCenterX(),bounds.getCenterY(),0,-2);}
        static me.shedaniel.math.Rectangle textBounds(Minecraft mc)throws Exception {
            @SuppressWarnings("unchecked") var widgets=(java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener>)mc.screen.getClass().getMethod("widgets").invoke(mc.screen);
            Object widget=null;
            for(Object candidate:me.shedaniel.rei.api.client.gui.widgets.Widgets.walk(widgets,w->w.getClass().getSimpleName().equals("ScrollableTextWidget"))) {
                if(candidate.getClass().getSimpleName().equals("ScrollableTextWidget")){widget=candidate;break;}
            }
            require(widget!=null,"Native REI description widget missing");
            var method=widget.getClass().getMethod("getBounds");method.setAccessible(true);
            return (me.shedaniel.math.Rectangle)method.invoke(widget);
        }
        static void next(Minecraft mc) throws Exception {
            var field=mc.screen.getClass().getDeclaredField("recipeNext");field.setAccessible(true);
            var button=field.get(mc.screen);
            var bounds=(me.shedaniel.math.Rectangle)button.getClass().getMethod("getBounds").invoke(button);
            mc.screen.mouseClicked(bounds.x+bounds.width/2.0,bounds.y+bounds.height/2.0,0);
            mc.screen.mouseReleased(bounds.x+bounds.width/2.0,bounds.y+bounds.height/2.0,0);
        }
        static void open(RecipeViewerInfo.Topic topic) {
            var registered=me.shedaniel.rei.api.client.registry.display.DisplayRegistry.getInstance().getAll().values().stream().flatMap(List::stream)
                    .filter(d -> d instanceof me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay info && info.getName().getString().equals(topic.title().getString())).findFirst().orElseThrow();
            require(me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder().addUsagesFor(me.shedaniel.rei.api.common.util.EntryStacks.of(topic.resolveStacks().getFirst()))
                    .filterCategory(registered.getCategoryIdentifier()).open(),"REI native search failed "+topic.id());
        }
    }
    private static class Jer {
        private static int dropIndex=-1;
        static mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout(Minecraft mc)throws Exception {
            var field=mc.screen.getClass().getDeclaredField("layouts");field.setAccessible(true);
            var layouts=field.get(mc.screen);
            field=layouts.getClass().getDeclaredField("recipeLayoutsWithButtons");field.setAccessible(true);
            var wrappers=(java.util.List<?>)field.get(layouts);
            var wrapper=wrappers.getFirst();
            var method=wrapper.getClass().getMethod("getRecipeLayout");method.setAccessible(true);
            return (mezz.jei.api.gui.IRecipeLayoutDrawable<?>)method.invoke(wrapper);
        }
        static mezz.jei.api.gui.ingredient.IRecipeSlotDrawable slot(Minecraft mc,String item)throws Exception {
            return (mezz.jei.api.gui.ingredient.IRecipeSlotDrawable)layout(mc).getRecipeSlotsView().getSlotViews().stream()
                    .filter(s->!s.getSlotName().orElse("").equals("oreSlot") && s.getDisplayedItemStack().map(stack->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("magnetization:"+item)).orElse(false)).findFirst().orElseThrow();
        }
        static void hover(Minecraft mc,String item)throws Exception {
            var area=layout(mc).getRect();var bounds=slot(mc,item).getRect();
            double scale=mc.getWindow().getGuiScale();
            double x=(area.getX()+bounds.getX()+bounds.getWidth()/2.0)*scale;
            double y=(area.getY()+bounds.getY()+bounds.getHeight()/2.0)*scale;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),x,y);
            // Deliver the same native cursor callback when Xvfb has no window
            // manager to focus the client and dispatch GLFW motion events.
            var move=mc.mouseHandler.getClass().getDeclaredMethod("onMove",long.class,double.class,double.class);
            move.setAccessible(true);move.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x,y);
            move.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x,y);
            LOG.info("UI_JER_HOVER item={} layout={} slot={} cursor={},{}",item,area,bounds,mc.mouseHandler.xpos(),mc.mouseHandler.ypos());
        }
        static void tick(Minecraft mc) throws Exception {
            var charts=com.stonytark.magnetization.compat.jer.JerWorldgenCatalog.charts();
            if(index==charts.size()) { LOG.info("UI_AUDIT_PASS viewer=jer native_worldgen_charts={} distributions_and_drops=true",index);finished=true;return; }
            var chart=charts.get(index);
            var rt=Jei.runtime();
            String dimension=chart.dimension()==com.stonytark.magnetization.compat.jer.JerWorldgenCatalog.Dimension.END?"minecraft:the_end":"minecraft:overworld";
            var entry=jeresources.registry.WorldGenRegistry.getInstance().getWorldGen().stream().filter(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getBlock().getItem()).equals(ResourceLocation.parse("magnetization:"+chart.display())) && e.getDimension().equals(dimension)).findFirst().orElseThrow();
            if(!opened) {
                var category=rt.getRecipeManager().createRecipeCategoryLookup().get().filter(c -> c.getClass().getName().equals("jeresources.jei.worldgen.WorldGenCategory")).findFirst().orElseThrow();
                @SuppressWarnings("unchecked") var typed=(mezz.jei.api.recipe.category.IRecipeCategory<jeresources.entry.WorldGenEntry>)category;
                rt.getRecipesGui().showRecipes(typed,List.of(entry),List.of());opened=true;wait=0;dropIndex=-1;return;
            }
            if(++wait<20)return;
            if(dropIndex>=0) {
                var drop=chart.drops().get(dropIndex);
                var tooltip=slot(mc,drop.item()).getTooltip();
                String drawn=normalize(String.join(" ",UiAuditText.lines()));
                for(var line:tooltip)require(drawn.contains(normalize(line.getString())),"Native JER hovered drop text missing "+drop.item()+": "+UiAuditText.lines());
                capture(mc,"ui-jer-"+chart.source()+"-"+chart.display()+"-drop-"+drop.item()+".png");
                LOG.info("UI_JER_DROP source={} display={} item={} rendered={}",chart.source(),chart.display(),drop.item(),UiAuditText.lines());
                if(++dropIndex<chart.drops().size()){hover(mc,chart.drops().get(dropIndex).item());wait=0;return;}
                index++;opened=false;return;
            }
            require(mc.screen!=null && mc.screen.getClass().getName().contains("RecipesGui"),"JER native recipe screen missing");
            require(UiAuditText.lines().stream().anyMatch(s -> s.contains("World Gen")),"JER graph title not rendered: "+UiAuditText.lines());
            require(entry.getChances().length>0 && entry.getAverageBlockCountPerChunk()>0,"Empty native JER distribution "+chart.display());
            require(UiAuditText.lines().contains(Integer.toString(entry.getMinY())) && UiAuditText.lines().contains(Integer.toString(entry.getMaxY()))
                    && UiAuditText.lines().stream().anyMatch(s -> s.contains("%")),"Native JER distribution axes missing: "+UiAuditText.lines());
            require(UiAuditText.lines().contains("Drops"),"Native JER drop display missing");
            for(var drop:chart.drops()) {
                var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("magnetization:"+drop.item()));
                require(entry.getDrops().stream().anyMatch(s -> s.is(item)),"Missing native drop "+drop.item());
                require(UiAuditText.items().contains("magnetization:"+drop.item()),"Native JER screen did not render drop "+drop.item());
                require(entry.getLootDrops(new net.minecraft.world.item.ItemStack(item)).stream().anyMatch(d -> d.minDrop==drop.min() && d.maxDrop==drop.max() && Math.abs(d.chance-drop.chance())<0.00001),"Native JER drop bounds/chance mismatch "+drop.item());
            }
            capture(mc,"ui-jer-"+chart.source()+"-"+chart.display()+".png");
            LOG.info("UI_JER_CHART source={} display={} dimension={} minY={} maxY={} average={} drops={} rendered={}",chart.source(),chart.display(),entry.getDimension(),entry.getMinY(),entry.getMaxY(),entry.getAverageBlockCountPerChunk(),entry.getDrops(),UiAuditText.lines());
            dropIndex=0;hover(mc,chart.drops().getFirst().item());wait=0;
        }
    }
}
