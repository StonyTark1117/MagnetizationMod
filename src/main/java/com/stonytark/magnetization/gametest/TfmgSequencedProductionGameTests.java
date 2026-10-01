package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import static com.stonytark.magnetization.gametest.NativeCompatTestSupport.*;

/** Routes workpieces between a native winder and a normally ticking deployer/depot. */
@GameTestHolder("magnetization_tfmg")
@PrefixGameTestTemplate(false)
public final class TfmgSequencedProductionGameTests {
    @GameTest(template = "empty", timeoutTicks = 2400, batch = "tfmgNativeMotorAssembly")
    public static void nativeMotorAssemblyConsumesMagnetsAndSpool(GameTestHelper h) {
        sequence(h, "tfmg_motor_from_permanent_magnet", "tfmg:electric_motor");
    }

    @GameTest(template = "empty", timeoutTicks = 3000, batch = "tfmgNativeGeneratorAssembly")
    public static void nativeGeneratorAssemblyConsumesMagnetsAndSpool(GameTestHelper h) {
        sequence(h, "tfmg_generator_from_permanent_magnet", "tfmg:generator");
    }

    private static void sequence(GameTestHelper h, String path, String expected) {
        var recipe = (SequencedAssemblyRecipe) h.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("magnetization:" + path)).orElseThrow().value();
        var winder = (KineticBlockEntity) place(h, new BlockPos(2, 40, 2), "tfmg:winding_machine");
        var depotPos = new BlockPos(5, 40, 2);
        place(h, depotPos, "create:depot");
        h.setBlock(depotPos.above(2), block("create:deployer").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        var deployer = (DeployerBlockEntity) h.getBlockEntity(depotPos.above(2));
        IItemHandler depot = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(depotPos), null);
        ItemStack spool = stack("tfmg:copper_spool");
        // SpoolItem normally initializes its wire count when crafted by a player.
        spool.getItem().onCraftedBy(spool, h.getLevel(), h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
        final boolean modernWinder = hasModernWinder(winder);
        setSpool(winder, modernWinder, spool);
        int originalWire = wireAmount(spool);
        ItemStack[] workpiece = {recipe.getIngredient().getItems()[0].copyWithCount(1)};
        ItemStack[] heldInput = {ItemStack.EMPTY};
        int[] stage = {0}, magnets = {0}, attempts = {1};
        boolean[] waiting = {false};
        h.runAfterDelay(2800, () -> h.assertTrue(attempts[0] > 1 || stage[0] > 0,
                "Native assembly never advanced; winder=" + windingInput(winder, modernWinder)
                        + " spool=" + getSpool(winder, modernWinder) + " deployer=" + deployer.getPlayer().getMainHandItem()));
        h.onEachTick(() -> {
            winder.setSpeed(256);
            deployer.setSpeed(256);
            var steps = recipe.getSequence();
            var step = steps.get(stage[0] % steps.size()).getRecipe();
            boolean winding = step.getType().toString().contains("winding");
            if (!waiting[0]) {
                if (deployer.getPlayer() == null) return;
                heldInput[0] = workpiece[0].copy();
                if (winding) {
                    setWindingInput(winder, modernWinder, workpiece[0]);
                    call(winder, "findRecipe");
                } else {
                    ItemStack ingredient = step.getIngredients().get(1).getItems()[0].copyWithCount(1);
                    deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ingredient);
                    h.assertTrue(depot.insertItem(0, workpiece[0], false).isEmpty(), "Deployer depot rejected assembly input");
                }
                waiting[0] = true;
                return;
            }
            ItemStack result = ItemStack.EMPTY;
            if (winding) {
                result = windingOutput(winder, modernWinder);
                if (result.isEmpty() || !modernWinder && ItemStack.isSameItemSameComponents(result, heldInput[0])) return;
                clearWindingOutput(winder, modernWinder);
                h.assertTrue(windingInput(winder, modernWinder).isEmpty(), "Native winder kept input");
            } else {
                for (int slot = 0; slot < depot.getSlots(); slot++) {
                    var candidate = depot.getStackInSlot(slot);
                    if (!candidate.isEmpty() && !ItemStack.isSameItemSameComponents(candidate, heldInput[0])) {
                        result = depot.extractItem(slot, 1, false);
                        break;
                    }
                }
                if (result.isEmpty()) return;
                if (step.getIngredients().get(1).test(stack("magnetization:permanent_magnet"))) {
                    h.assertTrue(deployer.getPlayer().getMainHandItem().isEmpty(), "Native deployer did not consume Permanent Magnet");
                    magnets[0]++;
                }
            }
            workpiece[0] = result;
            stage[0]++;
            waiting[0] = false;
            if (stage[0] == steps.size() * recipe.getLoops()) {
                ItemStack completed = result;
                h.assertTrue(recipe.resultPool.stream().anyMatch(output -> completed.is(output.getStack().getItem())),
                        "Native assembly ended in an unlisted result " + result);
                h.assertTrue(magnets[0] == recipe.getLoops() * attempts[0],
                        "Native assembly did not consume one magnet per loop");
                h.assertTrue(wireAmount(getSpool(winder, modernWinder))
                                == originalWire - 75 * recipe.getLoops() * attempts[0],
                        "Native winding did not consume expected spool wire");
                if (!result.is(stack(expected).getItem())) {
                    h.assertTrue(attempts[0] < 4, "Native assembly rolled only scrap after four complete cycles");
                    attempts[0]++;
                    workpiece[0] = recipe.getIngredient().getItems()[0].copyWithCount(1);
                    stage[0] = 0;
                    return;
                }
                h.setBlock(depotPos.above(2), Blocks.AIR);
                h.setBlock(depotPos, Blocks.AIR);
                h.setBlock(new BlockPos(2, 40, 2), Blocks.AIR);
                h.succeed();
            }
        });
    }

    private static boolean hasModernWinder(Object winder) {
        try { winder.getClass().getMethod("getSpool"); return true; }
        catch (NoSuchMethodException originalRuntime) { return false; }
    }

    private static void setSpool(Object winder, boolean modern, ItemStack spool) {
        if (modern) call(winder, "setSpool", new Class<?>[]{ItemStack.class}, spool);
        else set(winder, "spool", spool);
    }

    private static ItemStack getSpool(Object winder, boolean modern) {
        return modern ? (ItemStack) call(winder, "getSpool") : (ItemStack) field(winder, "spool");
    }

    private static net.neoforged.neoforge.items.IItemHandlerModifiable oldWindingInventory(Object winder) {
        return (net.neoforged.neoforge.items.IItemHandlerModifiable) field(winder, "inventory");
    }

    private static void setWindingInput(Object winder, boolean modern, ItemStack item) {
        if (modern) call(winder, "setInput", new Class<?>[]{ItemStack.class}, item);
        else oldWindingInventory(winder).setStackInSlot(0, item);
    }

    private static ItemStack windingInput(Object winder, boolean modern) {
        return modern ? (ItemStack) call(winder, "getInput") : oldWindingInventory(winder).getStackInSlot(0);
    }

    private static ItemStack windingOutput(Object winder, boolean modern) {
        return modern ? (ItemStack) call(winder, "getOutput") : oldWindingInventory(winder).getStackInSlot(0);
    }

    private static void clearWindingOutput(Object winder, boolean modern) {
        if (modern) call(winder, "setOutput", new Class<?>[]{ItemStack.class}, ItemStack.EMPTY);
        else oldWindingInventory(winder).setStackInSlot(0, ItemStack.EMPTY);
    }

    private static int wireAmount(ItemStack spool) {
        try {
            @SuppressWarnings("unchecked")
            var component = (net.minecraft.core.component.DataComponentType<Integer>) Class.forName("com.drmangotea.tfmg.registry.TFMGDataComponents")
                    .getField("SPOOL_AMOUNT").get(null);
            return spool.getOrDefault(component, 0);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
