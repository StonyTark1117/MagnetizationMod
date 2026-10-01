package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.physics.ShipMagneticScanner;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Native machine tick/interaction workflows in the pinned Supplementaries + Sable profile. */
@GameTestHolder("magnetization_supplementaries_workflow")
@PrefixGameTestTemplate(false)
public final class SupplementariesWorkflowGameTests {
    @GameTest(template="empty", timeoutTicks=180, batch="nativeCannon")
    public static void cannonFiresFromMovingShip(GameTestHelper h) {
        final var level = h.getLevel();
        final var base = h.absolutePos(new BlockPos(2, 280, 2));
        final List<BlockPos> blocks = new ArrayList<>();
        for (var p : BlockPos.betweenClosed(base.offset(-1,-1,-1),base.offset(1,-1,1))) {
            level.setBlockAndUpdate(p, Blocks.IRON_BLOCK.defaultBlockState()); blocks.add(p.immutable());
        }
        level.setBlockAndUpdate(base, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("supplementaries:cannon")).defaultBlockState()); blocks.add(base);
        final var ship = assemble(h, base, blocks);
        final var cannon = tile(ship, "CannonBlockTile");
        invoke(cannon,"setProjectile",new Class<?>[]{ItemStack.class},new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("supplementaries:cannonball")),2));
        invoke(cannon,"setFuel",new Class<?>[]{ItemStack.class},new ItemStack(Items.GUNPOWDER,4));
        invoke(cannon,"setFirePower",new Class<?>[]{byte.class},(byte)1);
        final var start = new Vector3d(ship.logicalPose().position());
        final Entity[] projectile = {null};
        final Vec3[] spawned = {null};
        h.runAfterDelay(5, () -> {
            RigidBodyHandle.of(ship).addLinearAndAngularVelocity(new Vector3d(1,0,0), new Vector3d(0,0.08,0));
            h.runAfterDelay(20, () -> invoke(cannon,"ignite",new Class<?>[]{Entity.class},(Object)null));
        });
        h.onEachTick(() -> {
            for (var entity : level.getAllEntities()) {
                if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("supplementaries:cannonball") && projectile[0] == null) {
                    projectile[0] = entity; spawned[0] = entity.position();
                    final var world = ship.logicalPose().position();
                    h.assertTrue(spawned[0].distanceTo(new Vec3(world.x, world.y, world.z)) < 12,
                            "Cannon projectile spawned outside ship world coordinates: " + spawned[0] + " ship=" + world);
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(projectile[0] != null, "Native cannon did not produce a projectile");
            h.assertTrue(projectile[0].position().distanceTo(spawned[0]) > 3, "Cannon projectile did not travel");
            h.assertTrue(((ItemStack)invoke(cannon,"getProjectile")).getCount()==1, "Cannon ammunition not consumed exactly once");
            h.assertTrue(((ItemStack)invoke(cannon,"getFuel")).getCount()==3, "Cannon fuel not consumed exactly once");
            h.assertTrue(start.distance(ship.logicalPose().position())>0.2, "Cannon ship did not move");
            h.assertTrue(ShipMagneticScanner.scan(ship).ferrousBlockCount()==10, "Cannon ship lost magnetic blocks");
            org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info("NATIVE_CANNON_PASS spawn={} traveled={} fuel=3 ammo=1",spawned[0],projectile[0].position().distanceTo(spawned[0]));
            projectile[0].discard(); remove(h,ship);
        });
    }

    /** Minimal reproducer isolates payload restoration from pulley controls and ship splitting. */
    @GameTest(template="empty", timeoutTicks=40, batch="nativeMovingPulleyAssembly")
    public static void movingPulleyPayloadSurvivesNativeAssembly(GameTestHelper h) throws Exception {
        final var level = h.getLevel();
        final var base = h.absolutePos(new BlockPos(2,220,2));
        final var original = base.west(5);
        level.setBlockAndUpdate(original,Blocks.CHEST.defaultBlockState());
        ((Container)level.getBlockEntity(original)).setItem(0,new ItemStack(Items.DIAMOND,17));
        final var carried = level.getBlockEntity(original).saveWithFullMetadata(level.registryAccess());
        final var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("supplementaries:moving_pulley_block"))
                .defaultBlockState().setValue(BlockStateProperties.FACING,Direction.DOWN);
        level.setBlockAndUpdate(base,state);
        final var factory = state.getBlock().getClass().getMethod("newMovingBlockEntity",BlockPos.class,
                net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.world.level.block.state.BlockState.class,Direction.class,boolean.class,boolean.class);
        final var moving = (BlockEntity)factory.invoke(null,base,state,Blocks.CHEST.defaultBlockState(),Direction.DOWN,true,false);
        invoke(moving,"setAnimationDuration",new Class<?>[]{int.class},40);
        invoke(moving,"supp$setCarriedBlockEntityNbt",new Class<?>[]{net.minecraft.nbt.CompoundTag.class},carried);
        level.setBlockEntity(moving);
        level.setBlockAndUpdate(base.east(),Blocks.IRON_BLOCK.defaultBlockState());
        h.assertTrue(level.getBlockEntity(base)==moving,"Native moving block fixture missing before assembly");
        final var saved = moving.saveWithFullMetadata(level.registryAccess());
        final var ship = assemble(h,base,List.of(base,base.east()));
        final var destination = ship.getPlot().getCenterBlock();
        final var restored = level.getBlockEntity(destination);
        org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info(
                "MOVING_PULLEY_ASSEMBLY sourceType={} savedKeys={} destinationState={} destinationEntity={}",
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(moving.getType()),saved.getAllKeys(),level.getBlockState(destination),restored);
        h.assertTrue(restored != null,"Sable assembly retained moving-pulley state but did not reconstruct its block entity");
        final var nbt = (net.minecraft.nbt.CompoundTag)invoke(restored,"supp$getCarriedBlockEntityNbt");
        h.assertTrue(carried.equals(nbt),"Moving pulley lost carried chest data during assembly");
        remove(h,ship);h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=140, batch="nativePulley")
    public static void pulleyMovesLoadedChestDownAndUpOnMovingShip(GameTestHelper h) {
        final var level = h.getLevel(); final var base = h.absolutePos(new BlockPos(2,280,2));
        final List<BlockPos> blocks = new ArrayList<>();
        level.setBlockAndUpdate(base, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("supplementaries:pulley_block"))
                .defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X)); blocks.add(base);
        level.setBlockAndUpdate(base.below(), Blocks.CHAIN.defaultBlockState()); blocks.add(base.below());
        level.setBlockAndUpdate(base.below(2), Blocks.CHEST.defaultBlockState()); blocks.add(base.below(2));
        ((Container)level.getBlockEntity(base.below(2))).setItem(0,new ItemStack(Items.DIAMOND,17));
        // Side support keeps the pulley mounted while leaving the payload travel path free.
        level.setBlockAndUpdate(base.east(),Blocks.IRON_BLOCK.defaultBlockState()); blocks.add(base.east());
        final var control = base.east(10);
        level.setBlockAndUpdate(control, level.getBlockState(base));
        level.setBlockAndUpdate(control.below(),Blocks.CHAIN.defaultBlockState());
        level.setBlockAndUpdate(control.below(2),Blocks.CHEST.defaultBlockState());
        ((Container)level.getBlockEntity(control.below(2))).setItem(0,new ItemStack(Items.DIAMOND,17));
        final var controlPulley=level.getBlockEntity(control);
        invoke(controlPulley,"setDisplayedItem",new Class<?>[]{ItemStack.class},new ItemStack(Items.CHAIN,4));
        invoke(controlPulley,"serverSideUpdateWhenChanged",new Class<?>[]{net.minecraft.core.HolderLookup.Provider.class},level.registryAccess());
        final var ship = assemble(h,base,blocks);
        final var pulley = tile(ship,"PulleyBlockTile"); final var pos = pulley.getBlockPos();
        invoke(pulley,"setDisplayedItem",new Class<?>[]{ItemStack.class},new ItemStack(Items.CHAIN,4));
        invoke(pulley,"serverSideUpdateWhenChanged",new Class<?>[]{net.minecraft.core.HolderLookup.Provider.class},level.registryAccess());
        final var start = new Vector3d(ship.logicalPose().position());
        final int[] ticks={0};
        h.onEachTick(() -> {
            if (++ticks[0] < 16) org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info("PULLEY_TICK age={} column={} ships={}",ticks[0],java.util.stream.IntStream.rangeClosed(0,4).mapToObj(i -> level.getBlockState(pos.below(i)).toString()).toList(),SubLevelContainer.getContainer(level).getAllSubLevels().size());
        });
        h.runAfterDelay(5, () -> {
            RigidBodyHandle.of(ship).addLinearAndAngularVelocity(new Vector3d(0.5,0,0),new Vector3d(0,0.1,0));
            h.assertTrue((boolean)invoke(controlPulley,"releaseRopeDown"),"Ground control refused to extend");
            h.assertTrue((boolean)invoke(pulley,"releaseRopeDown"),"Native pulley refused to extend");
            h.runAfterDelay(25, () -> {
                org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info("PULLEY_DIAGNOSTIC winding={} column={}",invoke(pulley,"getDisplayedItem"), java.util.stream.IntStream.rangeClosed(0,5).mapToObj(i -> level.getBlockState(pos.below(i)).toString()).toList());
                assertChest(h,control.below(3));
                org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info("PULLEY_GROUND_PASS shipPositions={}",SubLevelContainer.getContainer(level).getAllSubLevels().stream().map(s -> s.getPlot().getLoadedChunks().stream().flatMap(c -> c.getChunk().getBlockEntities().values().stream()).map(t -> t.getType()+":"+t.getBlockPos()).toList()).toList());
                h.assertTrue(((ItemStack)invoke(controlPulley,"getDisplayedItem")).getCount()==3,"Ground extension did not consume chain");
                h.assertTrue((boolean)invoke(controlPulley,"pullRopeUp"),"Ground control refused to retract");
                h.runAfterDelay(25, () -> {
                    assertChest(h,control.below(2));
                    h.assertTrue(((ItemStack)invoke(controlPulley,"getDisplayedItem")).getCount()==4,"Ground retraction did not recover chain");
                    org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info("PULLEY_GROUND_ROUNDTRIP_PASS payload=17_diamonds recoveredChain=4");
                    assertChest(h,pos.below(3));
                    h.assertTrue(((ItemStack)invoke(pulley,"getDisplayedItem")).getCount()==3,"Pulley did not consume chain on extension");
                    h.assertTrue((boolean)invoke(pulley,"pullRopeUp"),"Native pulley refused to retract");
                    h.runAfterDelay(25, () -> {
                        assertChest(h,pos.below(2));
                        h.assertTrue(((ItemStack)invoke(pulley,"getDisplayedItem")).getCount()==4,"Pulley did not recover chain on retraction");
                        h.assertTrue(start.distance(ship.logicalPose().position())>0.2,"Pulley ship did not move");
                        org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info("NATIVE_PULLEY_PASS payload=17_diamonds extend=1 retract=1 recoveredChain=4");
                        remove(h,ship); h.succeed();
                    });
                });
            });
        });
    }

    private static void assertChest(GameTestHelper h, BlockPos pos) {
        h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof Container,"Native pulley did not move chest to "+pos);
        final var stack=((Container)h.getLevel().getBlockEntity(pos)).getItem(0);
        h.assertTrue(stack.is(Items.DIAMOND) && stack.getCount()==17,"Pulley lost chest contents");
    }
    private static ServerSubLevel assemble(GameTestHelper h, BlockPos base, List<BlockPos> blocks) {
        final var ship=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),base,blocks,
                new BoundingBox3i(base.getX()-2,base.getY()-4,base.getZ()-2,base.getX()+3,base.getY()+2,base.getZ()+3));
        SubLevelContainer.getContainer(h.getLevel()).addForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
        return ship;
    }
    private static BlockEntity tile(ServerSubLevel ship,String name) {
        for(var holder:ship.getPlot().getLoadedChunks()) for(var tile:holder.getChunk().getBlockEntities().values())
            if(tile.getClass().getSimpleName().equals(name)) return tile;
        throw new IllegalStateException("Assembled machine missing: "+name);
    }
    private static Object invoke(Object target,String name) { return invoke(target,name,new Class<?>[0]); }
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args) {
        try { return target.getClass().getMethod(name,types).invoke(target,args); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("Native machine call failed: "+name,e); }
    }
    private static void remove(GameTestHelper h,ServerSubLevel ship) {
        SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(ship,SubLevelRemovalReason.REMOVED);
    }
}
