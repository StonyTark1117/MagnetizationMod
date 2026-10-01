package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import static com.stonytark.magnetization.gametest.NativeCompatTestSupport.*;

@GameTestHolder("magnetization_tfmg")
@PrefixGameTestTemplate(false)
public final class TfmgProductionGameTests {
    @GameTest(template = "empty", timeoutTicks = 260, batch = "tfmgNativeCasting")
    public static void nativeBasinsCastBucketSizedGalliumAndLithium(GameTestHelper h) {
        cast(h, new BlockPos(1, 40, 1), MagFluids.GALLIUM.get(), "magnetization:solid_gallium");
        cast(h, new BlockPos(3, 40, 1), MagFluids.LIQUID_LITHIUM.get(), "magnetization:lithium");
        nativeCastControl(h, new BlockPos(5, 40, 1));
        h.runAfterDelay(230, h::succeed);
    }

    private static void cast(GameTestHelper h, BlockPos pos, Fluid fluid, String result) {
        var be = place(h, pos, "tfmg:casting_basin");
        var tank = (IFluidHandler) field(be, "tank");
        var inventory = (IItemHandlerModifiable) field(be, "inventory");
        int originalCapacity = tank.getTankCapacity(0);
        h.assertTrue(tank.fill(new FluidStack(fluid, 1000), IFluidHandler.FluidAction.SIMULATE) == 1000,
                "Native basin cannot accept a simulated bucket of " + fluid);
        h.assertTrue(tank.getTankCapacity(0) == originalCapacity && tank.getFluidInTank(0).isEmpty(),
                "Simulating casting input mutated native basin");
        h.assertTrue(tank.fill(new FluidStack(fluid, 999), IFluidHandler.FluidAction.EXECUTE) == 999,
                "Native basin did not accept cast input");
        h.runAfterDelay(10, () -> {
            h.assertTrue(inventory.getStackInSlot(0).isEmpty(), "Partial bucket created a cast output");
            h.assertTrue(tank.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.EXECUTE) == 1,
                    "Native basin rejected final mB");
        });
        h.runAfterDelay(225, () -> {
            h.assertTrue(inventory.getStackInSlot(0).is(stack(result).getItem())
                    && inventory.getStackInSlot(0).getCount() == 1, "Native basin did not produce " + result);
            h.assertTrue(tank.getFluidInTank(0).isEmpty(), "Native cast did not consume a full bucket");
            h.setBlock(pos, Blocks.AIR);
        });
    }

    private static void nativeCastControl(GameTestHelper h, BlockPos pos) {
        var be = place(h, pos, "tfmg:casting_basin");
        var tank = (IFluidHandler) field(be, "tank");
        var inventory = (IItemHandlerModifiable) field(be, "inventory");
        int nativeCapacity = tank.getTankCapacity(0);
        h.assertTrue(nativeCapacity == 90 || nativeCapacity == 144,
                "Unexpected pinned TFMG native casting capacity " + nativeCapacity);
        h.assertTrue(tank.fill(new FluidStack(fluid("tfmg:liquid_concrete"), nativeCapacity + 1),
                        IFluidHandler.FluidAction.EXECUTE) == nativeCapacity,
                "Bucket casting adapter changed upstream casting capacity");
        h.runAfterDelay(225, () -> {
            h.assertTrue(inventory.getStackInSlot(0).is(stack("tfmg:cinderblock").getItem())
                    && inventory.getStackInSlot(0).getCount() == 1,
                    "Native liquid-concrete casting control did not produce a cinderblock");
            h.assertTrue(tank.getFluidInTank(0).isEmpty(), "Native cinderblock cast did not consume concrete");
            h.setBlock(pos, Blocks.AIR);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2400, batch = "tfmgNativeBlasting")
    public static void nativeBlastFurnaceConsumesOreFluxFuelAndHotAir(GameTestHelper h) {
        blast(h, new BlockPos(2, 40, 2), "raw_magnetite");
        blast(h, new BlockPos(10, 40, 2), "raw_hematite");
        h.runAfterDelay(2300, h::succeed);
    }

    private static void blast(GameTestHelper h, BlockPos base, String ore) {
        forceTicking(h, base.offset(-1, 0, -1), base.offset(1, 3, 1));
        // Native 3x3x3 furnace: four wall faces, four supports and a hollow center.
        // The wall tag also includes a hatch; a furnace may contain only one tuyere.
        var wall = block("tfmg:fireproof_bricks");
        var support = block("tfmg:fireproof_brick_reinforcement");
        for (int y = 0; y < 3; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) {
                if (y == 0) h.setBlock(base, wall);
            } else h.setBlock(base.offset(x, y, z), x == 0 || z == 0 ? wall : support);
        }
        BlockPos output = base.north();
        h.setBlock(output, block("tfmg:blast_furnace_output").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos hotAirPos = base.east().above();
        place(h, hotAirPos, "tfmg:blast_furnace_hatch");
        place(h, base.above(3), "tfmg:blast_furnace_hatch");
        h.setBlock(base.above(2), Blocks.AIR);
        var be = h.getBlockEntity(output);
        var tuyere = h.getBlockEntity(hotAirPos);
        var gas = h.getBlockEntity(base.above(3));
        var input = (IItemHandlerModifiable) field(be, "inputInventory");
        var flux = (IItemHandlerModifiable) field(be, "fluxInventory");
        var hotAir = (IFluidHandler) field(tuyere, "tank");
        var primary = (IFluidHandler) field(be, "primaryTank");
        var secondary = (IFluidHandler) field(be, "secondaryTank");
        // The normal item collector loads coke fuel; do not inject process progress or outputs.
        var fuel = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),
                h.absolutePos(base.above()).getX() + 0.5, h.absolutePos(base.above()).getY() + 0.1,
                h.absolutePos(base.above()).getZ() + 0.5, taggedItem("tfmg:blast_furnace_fuel").copyWithCount(64));
        fuel.setNoGravity(true);
        h.getLevel().addFreshEntity(fuel);
        call(be, "collectItems");
        int collectedFuel = (int) field(be, "fuel");
        h.assertTrue(collectedFuel > 0, "Native furnace did not collect coke fuel");
        input.setStackInSlot(0, stack("magnetization:" + ore));
        flux.setStackInSlot(0, taggedItem("tfmg:flux"));
        try {
            // Community Edition selects through a cached two-item input.
            var inputType = Class.forName("com.drmangotea.tfmg.recipes.input.IndustrialBlastingRecipeInput");
            var recipeInput = inputType.getConstructor(ItemStack.class, ItemStack.class).newInstance(
                    input.getStackInSlot(0), flux.getStackInSlot(0));
            @SuppressWarnings("rawtypes")
            var cache = (net.minecraft.world.item.crafting.RecipeManager.CachedCheck) field(be, "quickCheck");
            var selected = cache.getRecipeFor((net.minecraft.world.item.crafting.RecipeInput) recipeInput, h.getLevel());
            var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) selected.orElseThrow();
            h.assertTrue(holder.id().equals(ResourceLocation.parse("magnetization:tfmg_industrial_blasting_" + ore)),
                    "Native furnace selected competing blasting recipe " + holder.id() + " for " + ore);
        } catch (ClassNotFoundException originalRuntime) {
            // Original 1.2.0 iterates the one-slot recipe type directly. Its normal
            // completion and exact three-fluid results prove selection below.
            h.assertTrue(h.getLevel().getRecipeManager().byKey(
                    ResourceLocation.parse("magnetization:tfmg_industrial_blasting_" + ore)).isPresent(),
                    "Original TFMG did not load the supplemental blast recipe");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        hotAir.fill(new FluidStack(fluid("tfmg:hot_air"), 4000), IFluidHandler.FluidAction.EXECUTE);
        Object multiblock;
        Object liveTuyere;
        try {
            multiblock = field(be, "multiblock");
            call(multiblock, "evaluate");
            liveTuyere = call(multiblock, "getTuyereBlockEntity");
        } catch (IllegalStateException originalRuntime) {
            multiblock = be;
            call(be, "getSize"); // Original 1.2.0 discovers its hatch while measuring the furnace.
            h.assertTrue(h.absolutePos(hotAirPos).equals(field(be, "tuyerePos")),
                    "Original TFMG did not discover its hot-air hatch");
            liveTuyere = tuyere;
        }
        h.assertTrue((int) call(multiblock, "getSize") == 3, "Native blast furnace did not form");
        h.assertTrue(liveTuyere == tuyere, "Native furnace did not recognize live hot-air hatch");
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(output)) == be,
                "Native furnace controller was replaced during fixture construction");
        call(be, "executeRecipe");
        h.assertTrue((int) field(be, "timer") > 0, "Native blasting did not start");
        int[] suppliedAir = {hotAir.getFluidInTank(0).getAmount()};
        h.onEachTick(() -> {
            if (!input.getStackInSlot(0).isEmpty()) suppliedAir[0] += hotAir.fill(
                    new FluidStack(fluid("tfmg:hot_air"), 4000), IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay((int) field(be, "timer") + 100L, () -> {
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(output)) == be,
                    "Native furnace controller changed during processing");
            h.assertTrue(input.getStackInSlot(0).isEmpty() && flux.getStackInSlot(0).isEmpty(),
                    "Native blasting did not consume " + ore + " and flux; timer=" + field(be, "timer") + " fuel=" + field(be, "fuel") + " air=" + hotAir.getFluidInTank(0) + " input=" + input.getStackInSlot(0) + " flux=" + flux.getStackInSlot(0));
            h.assertTrue(primary.getFluidInTank(0).is(fluid("tfmg:molten_steel"))
                    && primary.getFluidInTank(0).getAmount() == 144, "Native blasting produced wrong steel");
            h.assertTrue(secondary.getFluidInTank(0).is(fluid("tfmg:molten_slag"))
                    && secondary.getFluidInTank(0).getAmount() == 144, "Native blasting produced wrong slag");
            h.assertTrue(((IFluidHandler) field(gas, "tank")).getFluidInTank(0).is(fluid("tfmg:furnace_gas")),
                    "Native blasting produced no gas byproduct");
            h.assertTrue(suppliedAir[0] > hotAir.getFluidInTank(0).getAmount(), "Native blasting used no hot air");
            h.assertTrue((int) field(be, "fuelConsumeTimer") > 0 || (int) field(be, "fuel") < collectedFuel,
                    "Native furnace did not spend fuel burn time");
            fuel.discard();
        });
    }

    private static ItemStack taggedItem(String id) {
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse(id));
        return BuiltInRegistries.ITEM.getTag(tag).orElseThrow().iterator().next().value().getDefaultInstance();
    }
    private static Fluid fluid(String id) { return BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id)).orElseThrow(); }
}
