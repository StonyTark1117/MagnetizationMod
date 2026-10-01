package com.stonytark.magnetization.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import static com.stonytark.magnetization.gametest.NativeCompatTestSupport.*;

final class NativeRecipeProductionTestSupport {
    private NativeRecipeProductionTestSupport() {}

    static void mechanicalCraft(GameTestHelper h, String recipeId, String resultId) {
        var recipe = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("magnetization:" + recipeId))
                .orElseThrow().value();
        h.assertTrue(recipe instanceof ShapedRecipe, recipeId + " has no crafting grid");
        var shaped = (ShapedRecipe) recipe;
        var origin = new BlockPos(2, 40, 2);
        forceTicking(h, origin.west().north(), origin.offset(shaped.getWidth(), shaped.getHeight(), 1));
        var crafterPositions = new java.util.ArrayList<BlockPos>();
        for (int row = 0; row < shaped.getHeight(); row++) for (int x = 0; x < shaped.getWidth(); x++) {
            BlockPos pos = origin.offset(x, shaped.getHeight() - row - 1, 0);
            Direction target = x == shaped.getWidth() - 1 ? Direction.DOWN : Direction.EAST;
            var facing = AllBlocks.MECHANICAL_CRAFTER.getDefaultState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            var pointing = MechanicalCrafterBlock.POINTING.getPossibleValues().stream()
                    .filter(value -> MechanicalCrafterBlock.getTargetDirection(
                            facing.setValue(MechanicalCrafterBlock.POINTING, value)) == target)
                    .findFirst().orElseThrow();
            h.setBlock(pos, facing.setValue(MechanicalCrafterBlock.POINTING, pointing));
        }
        // A single native source drives the meshed crafter cogs through Create's kinetic network.
        var cogPos = origin.west();
        h.setBlock(cogPos, AllBlocks.COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        var motorPos = cogPos.north();
        h.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH));
        var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
                h.getLevel().getBlockEntity(h.absolutePos(motorPos));
        motor.generatedSpeed.setValue(256);
        motor.updateGeneratedRotation();
        // Placement updates recreate neighbouring block entities; fill the live grid only after it is complete.
        for (int row = 0; row < shaped.getHeight(); row++) for (int x = 0; x < shaped.getWidth(); x++) {
            BlockPos pos = origin.offset(x, shaped.getHeight() - row - 1, 0);
            var crafter = (MechanicalCrafterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
            var ingredient = shaped.getIngredients().get(row * shaped.getWidth() + x);
            if (ingredient.isEmpty()) set(crafter, "covered", true);
            else crafter.getInventory().setStackInSlot(0, ingredient.getItems()[0].copyWithCount(1));
            crafterPositions.add(pos);
        }
        h.setBlock(origin.offset(shaped.getWidth() - 1, -2, 0), Blocks.STONE);
        h.runAfterDelay(5, () -> h.assertTrue(crafterPositions.stream().allMatch(pos -> {
            var live = (MechanicalCrafterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
            return live != null && live.getSpeed() != 0;
        }), "Native motor did not power the complete crafter grid; motor=" + motor.getSpeed()
                + " crafters=" + crafterPositions.stream().map(pos -> {
                    var live = (MechanicalCrafterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
                    return live == null ? "missing" : Float.toString(live.getSpeed());
                }).toList()));
        h.succeedWhen(() -> {
            var outputs = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(origin)).inflate(12));
            int count = outputs.stream().filter(e -> e.getItem().is(stack(resultId).getItem()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(count == shaped.getResultItem(h.getLevel().registryAccess()).getCount(),
                    "Native mechanical crafting did not produce " + resultId + ": " + count
                            + " nearby=" + outputs.stream().map(e -> e.getItem().toString()).toList()
                            + " crafters=" + crafterPositions.stream().map(pos -> {
                                var c = (MechanicalCrafterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
                                return c == null ? "missing/" + h.getLevel().getBlockState(h.absolutePos(pos))
                                        : c.getInventory().getStackInSlot(0) + "/" + field(c, "phase") + "/" + c.getSpeed();
                            }).toList());
            h.assertTrue(crafterPositions.stream().allMatch(pos -> ((MechanicalCrafterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos)))
                    .getInventory().getStackInSlot(0).isEmpty()), "Native mechanical crafters did not consume all ingredients");
            crafterPositions.forEach(pos -> h.getLevel().removeBlock(h.absolutePos(pos), false));
            h.setBlock(motorPos, Blocks.AIR);
            h.setBlock(cogPos, Blocks.AIR);
            outputs.forEach(ItemEntity::discard);
        });
    }


    static void mixing(GameTestHelper h, String oilId) {
        BlockPos pos = new BlockPos(2, 40, 2);
        var basin = place(h, pos, "create:basin");
        var mixer = (com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity)
                place(h, pos.above(2), "create:mechanical_mixer");
        var inventory = (net.neoforged.neoforge.items.IItemHandler) field(basin, "inputInventory");
        var fluid = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                h.absolutePos(pos), Direction.UP);
        h.assertTrue(inventory.insertItem(0, stack("magnetization:raw_magnetite").copyWithCount(2), false).isEmpty(),
                "Native Create basin rejected ferrofluid minerals");
        h.assertTrue(fluid.fill(new net.neoforged.neoforge.fluids.FluidStack(
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(oilId)).orElseThrow(), 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 1000,
                "Native Create basin rejected compatible oil");
        h.onEachTick(() -> { if (!mixer.isRemoved()) mixer.setSpeed(256); });
        h.succeedWhen(() -> {
            int output = 0;
            for (int i = 0; i < fluid.getTanks(); i++) if (fluid.getFluidInTank(i)
                    .is(com.stonytark.magnetization.registry.MagFluids.FERROFLUID.get())) output += fluid.getFluidInTank(i).getAmount();
            h.assertTrue(output == 1000, "Native Create mixing produced " + output + " mB ferrofluid from " + oilId);
            h.assertTrue(inventory.getStackInSlot(0).isEmpty(), "Native mixing did not consume both minerals");
            h.setBlock(pos.above(2), Blocks.AIR);
            h.setBlock(pos, Blocks.AIR);
        });
    }

    static void vanillaCraft(GameTestHelper h, String recipeId, String resultId) {
        var recipe = (ShapedRecipe) h.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("magnetization:" + recipeId)).orElseThrow().value();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var menu = new net.minecraft.world.inventory.CraftingMenu(1, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))));
        for (int row = 0; row < recipe.getHeight(); row++) for (int x = 0; x < recipe.getWidth(); x++) {
            var ingredient = recipe.getIngredients().get(row * recipe.getWidth() + x);
            if (!ingredient.isEmpty()) menu.getSlot(1 + row * 3 + x).set(ingredient.getItems()[0].copyWithCount(1));
        }
        ItemStack result = menu.getSlot(0).remove(recipe.getResultItem(h.getLevel().registryAccess()).getCount());
        h.assertTrue(result.is(stack(resultId).getItem()), "Native crafting table produced wrong " + resultId);
        menu.getSlot(0).onTake(player, result);
        for (int i = 1; i <= 9; i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "Crafting table did not consume input " + i);
        h.succeed();
    }
}
