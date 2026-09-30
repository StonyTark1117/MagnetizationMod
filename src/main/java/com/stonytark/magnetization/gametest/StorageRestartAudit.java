package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.physics.ShipMagneticScanner;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Two separate dedicated-server processes use the normal world/ship save lifecycle. Opt-in dev audit only. */
@EventBusSubscriber(modid=Magnetization.MOD_ID)
public final class StorageRestartAudit {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("magnetization/storage-restart-audit");
    private static final List<String> SOPH=List.of("iron_chest","gold_chest","netherite_chest","iron_barrel","gold_barrel","netherite_barrel","chest");
    private static final List<String> IRON=List.of("iron_chest","gold_chest","copper_chest","diamond_chest","crystal_chest","obsidian_chest");
    private static final Path MANIFEST=Path.of("storage-restart-audit.properties");
    private static int ticks;
    private static boolean finished;
    private static ServerSubLevel ship;
    private static Vector3d start;
    private static Properties saved;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        final String phase=System.getProperty("magnetization.audit.storageRestart","");
        if (phase.isEmpty() || finished) return;
        final var server=event.getServer(); final var level=server.overworld(); ticks++;
        try {
            if (ticks==1) {
                check(phase.equals("create") || phase.equals("verify"),"Invalid phase");
                if (phase.equals("create")) {
                    check(!Files.exists(MANIFEST),"Refusing to overwrite existing audit manifest");
                    ship=create(level); saved=new Properties();
                    saved.setProperty("ship",ship.getUniqueId().toString());
                    saved.setProperty("creatorPid",Long.toString(ProcessHandle.current().pid()));
                } else {
                    saved=new Properties(); try(var in=Files.newInputStream(MANIFEST)){saved.load(in);}
                    check(!saved.getProperty("creatorPid").equals(Long.toString(ProcessHandle.current().pid())),"Verification must use a new JVM");
                }
            }
            if (ship==null) {
                ship=(ServerSubLevel)SubLevelContainer.getContainer(level).getSubLevel(UUID.fromString(saved.getProperty("ship")));
                check(ticks<200 || ship!=null,"Persisted ship did not load after restart");
                if(ship==null)return;
                assertContents(ship);
                check(cooked(ship)==Integer.parseInt(saved.getProperty("cooked")),"Cooking output changed during shutdown/restart");
                LOG.info("STORAGE_RESTART_RESTORED ship={} creatorPid={} verifierPid={}",ship.getUniqueId(),saved.getProperty("creatorPid"),ProcessHandle.current().pid());
            }
            if(start==null && ticks>=5) {
                start=new Vector3d(ship.logicalPose().position());
                final var body=RigidBodyHandle.of(ship);check(body!=null && body.isValid(),"Missing storage physics body");
                body.addLinearAndAngularVelocity(new Vector3d(0.5,0,0),new Vector3d(0,0.1,0));
            }
            if(phase.equals("create") && ticks==80) {
                assertContents(ship);check(start.distance(ship.logicalPose().position())>0.25,"Ship did not move before save");
                saved.setProperty("cooked",Integer.toString(cooked(ship)));
                try(var out=Files.newOutputStream(MANIFEST)){saved.store(out,"Native storage restart audit");}
                LOG.info("STORAGE_RESTART_CREATED ship={} cooked={} pid={}",ship.getUniqueId(),saved.getProperty("cooked"),ProcessHandle.current().pid());
                finished=true;server.halt(false);
            } else if(phase.equals("verify") && ticks==500) {
                assertContents(ship);check(start.distance(ship.logicalPose().position())>0.25,"Restored ship did not move");
                check(cooked(ship)>Integer.parseInt(saved.getProperty("cooked")),"Native smelting did not continue after process restart");
                for(var be:entities(ship)) {
                    if(isSoph(be)) {
                        final var external=(IItemHandler)storageClass().getMethod("getExternalItemHandler",Direction.class).invoke(be,Direction.UP);
                        check(external.extractItem(0,7,false).getCount()==7,"Restored external extraction failed");
                        check(inventory(be).getStackInSlot(0).getCount()==89,"Restored extraction did not update inventory");
                    } else if(be instanceof Container container) {
                        check(container.removeItem(0,7).getCount()==7 && container.getItem(0).getCount()==24,"Iron Chests restored extraction failed");
                    }
                }
                LOG.info("STORAGE_RESTART_PASS sophisticated={} ironChests={} cooked={} ticksAfterRestart={}",SOPH.size(),IRON.size(),cooked(ship),ticks);
                finished=true;server.halt(false);
            }
        } catch(Throwable e) {finished=true;LOG.error("STORAGE_RESTART_FAILED phase="+phase,e);server.halt(false);}
    }

    private static ServerSubLevel create(ServerLevel level) throws Exception {
        final var origin=new BlockPos(0,120,0); final List<BlockPos> blocks=new ArrayList<>();
        final List<String> ids=new ArrayList<>();SOPH.forEach(s->ids.add("sophisticatedstorage:"+s));IRON.forEach(s->ids.add("ironchest:"+s));
        for(int i=0;i<ids.size();i++) {
            final var pos=origin.east(i); final var id=ResourceLocation.parse(ids.get(i));
            check(BuiltInRegistries.BLOCK.containsKey(id),"Missing "+id);
            level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id).defaultBlockState());
            final var be=level.getBlockEntity(pos);
            if(isSoph(be)) {
                final var upgrades=upgrades(be);
                check(upgrades.insertItem(0,item("stack_upgrade_tier_1"),false).isEmpty(),"Stack upgrade rejected");
                check(inventory(be).insertItem(0,new ItemStack(Items.DIAMOND,96),false).isEmpty(),"Stack upgrade failed to raise capacity");
                storageClass().getMethod("setCustomName",Component.class).invoke(be,Component.literal(ids.get(i)));
                storageClass().getMethod("toggleLock").invoke(be);
                if(ids.get(i).equals("sophisticatedstorage:netherite_chest")) {
                    check(upgrades.insertItem(1,item("smelting_upgrade"),false).isEmpty(),"Smelting upgrade rejected");
                    final var logic=cooking(be);
                    logic.getClass().getMethod("setCookInput",ItemStack.class).invoke(logic,new ItemStack(Items.RAW_IRON,4));
                    logic.getClass().getMethod("setFuel",ItemStack.class).invoke(logic,new ItemStack(Items.COAL,2));
                }
            } else {
                check(be instanceof Container,"Iron Chest missing native inventory");
                ((Container)be).setItem(0,new ItemStack(Items.EMERALD,31));be.setChanged();
            }
            blocks.add(pos);blocks.add(pos.below());
        }
        final var result=SubLevelAssemblyHelper.assembleBlocks(level,origin,blocks,new BoundingBox3i(0,119,0,ids.size(),121,1));
        SubLevelContainer.getContainer(level).addForceLoadTicket(result,SubLevelLoadingTicketType.COMMAND_FORCED,Unit.INSTANCE);
        return result;
    }
    private static List<BlockEntity> entities(ServerSubLevel sub) {
        return sub.getPlot().getLoadedChunks().stream().flatMap(h->h.getChunk().getBlockEntities().values().stream()).toList();
    }
    private static void assertContents(ServerSubLevel sub) throws Exception {
        check(entities(sub).size()==SOPH.size()+IRON.size(),"Storage block entity lost");
        check(ShipMagneticScanner.scan(sub).ferrousBlockCount()==9,"Metal/wood classification changed");
        for(var be:entities(sub)) {
            if(isSoph(be)) {
                check(inventory(be).getStackInSlot(0).is(Items.DIAMOND) && inventory(be).getStackInSlot(0).getCount()==96,"Sophisticated contents lost");
                check(upgrades(be).getStackInSlot(0).is(item("stack_upgrade_tier_1").getItem()),"Stack upgrade lost");
                check((boolean)storageClass().getMethod("isLocked").invoke(be),"Storage lock lost");
                final var name=(Component)storageClass().getMethod("getCustomName").invoke(be);
                check(name!=null && name.getString().equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString()),"Custom name lost");
            } else {
                final var stack=((Container)be).getItem(0);check(stack.is(Items.EMERALD) && stack.getCount()==31,"Iron Chests contents lost");
            }
        }
    }
    private static int cooked(ServerSubLevel sub) throws Exception {
        final var be=entities(sub).stream().filter(b->BuiltInRegistries.BLOCK.getKey(b.getBlockState().getBlock()).toString().equals("sophisticatedstorage:netherite_chest")).findFirst().orElseThrow();
        final var logic=cooking(be);final var output=(ItemStack)logic.getClass().getMethod("getCookOutput").invoke(logic);
        check(output.isEmpty() || output.is(Items.IRON_INGOT),"Unexpected smelting result");return output.getCount();
    }
    private static Object cooking(BlockEntity be) throws Exception {
        final var handler=upgrades(be);
        final var wrappers=(Map<?,?>)handler.getClass().getMethod("getSlotWrappers").invoke(handler);
        final var wrapper=wrappers.get(1);check(wrapper!=null,"Missing smelting wrapper");
        final var logic=Class.forName("net.p3pp3rf1y.sophisticatedcore.upgrades.cooking.CookingUpgradeWrapper").getMethod("getCookingLogic").invoke(wrapper);
        logic.getClass().getMethod("getCookingInventory").invoke(logic);
        return logic;
    }
    private static IItemHandler inventory(BlockEntity be) throws Exception {return handler(be,"getInventoryHandler");}
    private static IItemHandler upgrades(BlockEntity be) throws Exception {return handler(be,"getUpgradeHandler");}
    private static IItemHandler handler(BlockEntity be,String method) throws Exception {
        final var wrapper=storageClass().getMethod("getStorageWrapper").invoke(be);
        return (IItemHandler)Class.forName("net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper").getMethod(method).invoke(wrapper);
    }
    private static Class<?> storageClass() throws Exception {return Class.forName("net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity");}
    private static boolean isSoph(BlockEntity be) throws Exception {return storageClass().isInstance(be);}
    private static ItemStack item(String id) {return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedstorage:"+id)));}
    private static void check(boolean test,String message) {if(!test)throw new IllegalStateException(message);}
}
