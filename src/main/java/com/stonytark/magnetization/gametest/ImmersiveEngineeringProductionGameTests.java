package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import static com.stonytark.magnetization.gametest.NativeCompatTestSupport.*;

/** Forms upstream templates and lets the normal world ticker run their native processors. */
@GameTestHolder("magnetization_immersiveengineering")
@PrefixGameTestTemplate(false)
public final class ImmersiveEngineeringProductionGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "ieNativeMixer")
    public static void nativeMixerConsumesMineralsOilAndEnergy(GameTestHelper h) throws Exception {
        Object state = form(h, "MIXER");
        IEnergyStorage energy = (IEnergyStorage) field(state, "energy");
        IItemHandler inventory = (IItemHandler) field(state, "inventory");
        IFluidHandler tank = (IFluidHandler) field(state, "tank");
        energy.receiveEnergy(16000, false);
        h.assertTrue(inventory.insertItem(0, stack("magnetization:raw_magnetite"), false).isEmpty(),
                "IE mixer rejected first mineral");
        h.assertTrue(inventory.insertItem(1, stack("magnetization:raw_magnetite"), false).isEmpty(),
                "IE mixer rejected second mineral");
        h.assertTrue(tank.fill(new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID
                .get(net.minecraft.resources.ResourceLocation.parse("immersiveengineering:plantoil")), 1000),
                IFluidHandler.FluidAction.EXECUTE) == 1000, "IE mixer rejected oil");
        h.succeedWhen(() -> {
            int ferrofluid = 0;
            for (int i = 0; i < tank.getTanks(); i++) {
                var fluid = tank.getFluidInTank(i);
                if (fluid.is(MagFluids.FERROFLUID.get())) ferrofluid += fluid.getAmount();
            }
            h.assertTrue(ferrofluid == 1000, "IE mixer has not produced 1000 mB ferrofluid: " + ferrofluid);
            h.assertTrue(inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty(),
                    "IE mixer did not consume both minerals");
            h.assertTrue(energy.getEnergyStored() < 16000, "IE mixer consumed no FE");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 800, batch = "ieNativeMetalPress")
    public static void nativeMetalPressProducesAllSupplementalPlates(GameTestHelper h) throws Exception {
        Object state = form(h, "METAL_PRESS");
        IEnergyStorage energy = (IEnergyStorage) call(state, "getEnergy");
        IItemHandler input = (IItemHandler) field(state, "inputCap");
        set(state, "mold", stack("immersiveengineering:mold_plate"));
        energy.receiveEnergy(16000, false);
        String[] ingots = {"ferromagnetic_ingot", "samarium_cobalt_alloy", "neodymium_alloy"};
        String[] plates = {"magnetic_plate", "samarium_cobalt_plate", "neodymium_alloy_plate"};
        int[] fed = {0};
        var outputsSeen = new java.util.HashMap<String, Integer>();
        var area = new AABB(h.absolutePos(new BlockPos(2, 40, 2))).inflate(16);
        h.onEachTick(() -> {
            if (fed[0] < ingots.length && input.insertItem(0, stack("magnetization:" + ingots[fed[0]]), false).isEmpty())
                fed[0]++;
            for (var item : h.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
                for (String plate : plates) if (item.getItem().is(stack("magnetization:" + plate).getItem())) {
                    outputsSeen.merge(plate, item.getItem().getCount(), Integer::sum);
                    item.discard();
                    break;
                }
            }
        });
        h.succeedWhen(() -> {
            for (String plate : plates)
                h.assertTrue(outputsSeen.getOrDefault(plate, 0) == 1,
                        "IE metal press has not produced exactly one " + plate + "; seen=" + outputsSeen
                                + " fed=" + fed[0] + " FE=" + energy.getEnergyStored()
                                + " queue=" + ((java.util.Collection<?>) call(field(state, "processor"), "getQueue")).size());
            h.assertTrue(fed[0] == ingots.length, "IE metal press never accepted all three materials");
            h.assertTrue(energy.getEnergyStored() < 16000, "IE metal press consumed no FE");
            h.assertTrue(((java.util.Collection<?>) call(field(state, "processor"), "getQueue")).isEmpty(),
                    "IE metal press has not finished consuming its inputs");
        });
    }

    private static Object form(GameTestHelper h, String name) throws Exception {
        Object multiblock = Class.forName("blusunrize.immersiveengineering.common.blocks.multiblocks.IEMultiblocks")
                .getField(name).get(null);
        BlockPos origin = h.absolutePos(new BlockPos(2, 40, 2));
        @SuppressWarnings("unchecked")
        var structure = (java.util.List<StructureTemplate.StructureBlockInfo>) call(multiblock,
                "getStructure", new Class<?>[]{Level.class}, h.getLevel());
        int minX = structure.stream().mapToInt(info -> info.pos().getX()).min().orElseThrow();
        int maxX = structure.stream().mapToInt(info -> info.pos().getX()).max().orElseThrow();
        int minZ = structure.stream().mapToInt(info -> info.pos().getZ()).min().orElseThrow();
        int maxZ = structure.stream().mapToInt(info -> info.pos().getZ()).max().orElseThrow();
        forceTicking(h, new BlockPos(2 + minX, 40, 2 + minZ), new BlockPos(2 + maxX, 40, 2 + maxZ));
        for (var info : structure) h.getLevel().setBlockAndUpdate(origin.offset(info.pos()), info.state());
        BlockPos trigger = origin.offset((BlockPos) call(multiblock, "getTriggerOffset"));
        boolean formed = (boolean) call(multiblock, "createStructure", new Class<?>[]{Level.class,
                BlockPos.class, Direction.class, net.minecraft.world.entity.player.Player.class},
                h.getLevel(), trigger, Direction.SOUTH, h.makeMockPlayer(GameType.SURVIVAL));
        h.assertTrue(formed, "Could not form native IE " + name + " template");
        var master = h.getLevel().getBlockEntity(origin.offset((BlockPos) call(multiblock, "getMasterFromOriginOffset")));
        h.assertTrue(master != null, "Formed IE multiblock has no master");
        return call(call(master, "getHelper"), "getState");
    }
}
