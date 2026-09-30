package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/** Opt-in development audit; requires two real, connected Minecraft clients. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AircraftNetworkAudit {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/aircraft-network-audit");
    private static List<ResourceLocation> types;
    private static int index, age;
    private static Entity craft;
    private static Vec3 start;
    private static double repelStartX;
    private static boolean finished;
    private static final boolean ORDINARY = Boolean.getBoolean("magnetization.audit.aircraftOrdinary");
    private static int initialFuel;
    private static java.util.List<Integer> fuelSlots = java.util.List.of();

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("magnetization.audit.aircraftNetwork") || finished) return;
        final var server = event.getServer();
        final var pilot = server.getPlayerList().getPlayerByName("AuditPilot");
        final var observer = server.getPlayerList().getPlayerByName("AuditObserver");
        if (pilot == null || observer == null) return;
        try {
            if (types == null) {
                types = BuiltInRegistries.ENTITY_TYPE.getTag(MagTags.MAGNETIZABLE_ENTITIES).orElseThrow().stream()
                        .map(h -> BuiltInRegistries.ENTITY_TYPE.getKey(h.value()))
                        .filter(id -> id.getNamespace().equals("immersive_aircraft") || id.getNamespace().equals("aviator_dream"))
                        .sorted().toList();
                if (types.size() != 15) throw new IllegalStateException("Expected 15 aircraft, got " + types);
                pilot.setGameMode(ORDINARY ? GameType.SURVIVAL : GameType.CREATIVE); observer.setGameMode(GameType.SPECTATOR);
            }
            if (craft == null) {
                pilot.stopRiding();
                pilot.teleportTo(server.overworld(), 0, 180, 0, 0, 0);
                observer.teleportTo(server.overworld(), 4, 185, 0, 0, 0);
                craft = BuiltInRegistries.ENTITY_TYPE.get(types.get(index)).create(server.overworld());
                craft.setPos(0, 180, 0);
                craft.setInvulnerable(true);
                craft.setCustomName(net.minecraft.network.chat.Component.literal("audit-" + index));
                server.overworld().addFreshEntity(craft);
                if (!pilot.startRiding(craft, true)) throw new IllegalStateException("Cannot pilot " + types.get(index));
                if (ORDINARY) {
                    pilot.getFoodData().setFoodLevel(20);
                    pilot.setHealth(pilot.getMaxHealth());
                    auditCrafting(pilot, types.get(index));
                    fuelSlots = fuelSlots(craft);
                    final var inventory = (net.minecraft.world.Container)craft.getClass().getMethod("getInventory").invoke(craft);
                    for (int slot : fuelSlots) inventory.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL,64));
                    initialFuel = fuelSlots.size()*64;
                    LOG.info("AIRCRAFT_ORDINARY type={} fuelSlots={} initialCoal={} strength={} force={} ticks=400", types.get(index),fuelSlots,initialFuel,MagneticStrength.WEAK,MagneticStrength.WEAK.force());
                }
                start = craft.position(); age = 0;
                LOG.info("AIRCRAFT_BEGIN type={} uuid={}", types.get(index), craft.getUUID());
            }
            age++;
            if (pilot.getVehicle() != craft || !craft.isAlive()) throw new IllegalStateException("Pilot/craft lost for " + types.get(index));
            if (age % 20 == 0) observer.teleportTo(server.overworld(), craft.getX()+4, craft.getY()+5, craft.getZ(), 0, 0);
            if (age == (ORDINARY ? 240 : 110)) repelStartX = craft.getX();
            if (age >= (ORDINARY ? 100 : 60) && age < (ORDINARY ? 380 : 160)) {
                final var pole = age < (ORDINARY ? 240 : 110) ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH;
                FieldApplicator.applyEntitiesOnly(server.overworld(), new MagneticField(
                        craft.position().add(-3, craft.getBbHeight()*0.5, 0), new Vec3(1,0,0), pole,
                        MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL, 4, ORDINARY ? 0.0 : 9.0));
            }
            if (age % 10 == 0) LOG.info("AIRCRAFT_SERVER type={} uuid={} age={} x={} y={} z={} vx={} entityTicks={} pilotPos={}",
                    types.get(index), craft.getUUID(), age, craft.getX(), craft.getY(), craft.getZ(), craft.getDeltaMovement().x, craft.tickCount, pilot.position());
            if (age == (ORDINARY ? 400 : 180)) {
                if (craft.getX() <= repelStartX) throw new IllegalStateException("Repulsion did not reverse piloted motion for " + types.get(index)
                        + "; before=" + repelStartX + " after=" + craft.getX());
                if (craft.position().distanceTo(start) < 2) throw new IllegalStateException("No sustained motion for " + types.get(index));
                if (ORDINARY) {
                    final var inventory=(net.minecraft.world.Container)craft.getClass().getMethod("getInventory").invoke(craft);
                    final int remaining=fuelSlots.stream().mapToInt(slot -> inventory.getItem(slot).getCount()).sum();
                    if (!fuelSlots.isEmpty() && remaining >= initialFuel) throw new IllegalStateException("Native survival engine consumed no fuel: "+types.get(index));
                    LOG.info("AIRCRAFT_FUEL_PASS type={} initial={} remaining={} creative={}",types.get(index),initialFuel,remaining,pilot.isCreative());
                }
                LOG.info("AIRCRAFT_PASS type={} ticks={} distance={}", types.get(index), age, craft.position().distanceTo(start));
                pilot.stopRiding(); craft.discard(); craft = null; index++;
                if (index == types.size()) { finished = true; LOG.info("AIRCRAFT_NETWORK_SERVER_PASS types={}", types.size()); }
            }
        } catch (Exception e) { finished = true; LOG.error("AIRCRAFT_NETWORK_FAILED", e); }
    }
    private static java.util.List<Integer> fuelSlots(Entity entity) throws Exception {
        final var description=entity.getClass().getMethod("getInventoryDescription").invoke(entity);
        final var type=Class.forName("immersive_aircraft.entity.inventory.VehicleInventoryDescription").getField("BOILER").get(null);
        final var slots=(java.util.List<?>)description.getClass().getMethod("getSlots",String.class).invoke(description,type);
        final java.util.List<Integer> indices=new java.util.ArrayList<>();
        for (var slot:slots) indices.add((int)slot.getClass().getMethod("index").invoke(slot));
        return indices;
    }

    private static void auditCrafting(net.minecraft.server.level.ServerPlayer player, ResourceLocation id) {
        final var level=player.serverLevel();
        final var item=BuiltInRegistries.ITEM.get(id);
        final var recipe=level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                .filter(r -> r.value().getResultItem(level.registryAccess()).is(item)).findFirst().orElseThrow(() -> new IllegalStateException("No native crafting recipe for "+id));
        if (!(recipe.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped)) throw new IllegalStateException("Unexpected native recipe form: "+id);
        final var menu=new net.minecraft.world.inventory.CraftingMenu(0,player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, player.blockPosition()));
        int inputCount=0;
        for(int y=0;y<shaped.getHeight();y++) for(int x=0;x<shaped.getWidth();x++) {
            final var ingredient=shaped.getIngredients().get(y*shaped.getWidth()+x);
            if(ingredient.isEmpty()) continue;
            final var alternatives=ingredient.getItems();
            if(alternatives.length==0) throw new IllegalStateException("Unresolved native ingredient: "+recipe.id());
            menu.getSlot(1+y*3+x).set(alternatives[0].copyWithCount(1)); inputCount++;
        }
        final var result=menu.getSlot(0).getItem();
        if(!result.is(item)) throw new IllegalStateException("Native crafting menu did not resolve "+id+": "+result);
        final var crafted=menu.getSlot(0).remove(1);
        menu.getSlot(0).onTake(player,crafted);
        for(int i=1;i<=9;i++) if(!menu.getSlot(i).getItem().isEmpty()) throw new IllegalStateException("Native crafting did not consume input slot "+i+" for "+id);
        LOG.info("AIRCRAFT_CRAFT_PASS type={} recipe={} inputs={} output={}",id,recipe.id(),inputCount,crafted);
    }

}
