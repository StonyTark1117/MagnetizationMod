package com.stonytark.magnetization.gametest;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.stonytark.magnetization.client.MagPonderPlugin;
import com.stonytark.magnetization.compat.copycats.MagCopycatsCompat;
import com.stonytark.magnetization.content.inducer.StructuralInducerBlockEntity;
import com.stonytark.magnetization.physics.ShipMagneticScanner;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;

/** Published-runtime checks for Copycats+ material-derived susceptibility. */
@GameTestHolder("magnetization_copycats")
@PrefixGameTestTemplate(false)
public final class CopycatsGameTests {
    private CopycatsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 140, batch = "copycatsMaterialCompat")
    public static void copiedIronSurvivesInducerAssemblyAndDrivesSusceptibility(
            final GameTestHelper helper) {
        final BlockPos inducerPos = new BlockPos(3, 1, 3);
        final BlockPos copycatPos = inducerPos.above(3);
        helper.setBlock(inducerPos, MagBlocks.STRUCTURAL_INDUCER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN));
        helper.setBlock(copycatPos, BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("copycats", "copycat_block")));
        final var blockEntity = helper.getBlockEntity(copycatPos);
        helper.assertTrue(blockEntity instanceof ICopycatBlockEntity,
                "Published Copycats+ block did not create its material block entity");
        if (!(blockEntity instanceof ICopycatBlockEntity copycat)) return;
        copycat.setMaterial(Blocks.IRON_BLOCK.defaultBlockState());
        // Match a real player-applied material: Copycats persists the consumed
        // source stack alongside the visual block state.
        copycat.setConsumedItem(new ItemStack(Blocks.IRON_BLOCK));

        helper.assertTrue(MagCopycatsCompat.materialsOf(blockEntity).stream()
                        .anyMatch(state -> state.is(Blocks.IRON_BLOCK)),
                "Copycats+ iron material was not resolved from block-entity data");
        helper.assertTrue(blockEntity instanceof IHaveGoggleInformation,
                "Copycats+ block did not receive Magnetization goggles information");
        final ArrayList<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
        helper.assertTrue(((IHaveGoggleInformation) blockEntity).addToGoggleTooltip(tooltip, false)
                        && !tooltip.isEmpty(),
                "Copycats+ goggles hook did not describe copied material susceptibility");
        helper.assertTrue(MagPonderPlugin.hasCopycatsSceneTarget(),
                "Copycats+ Ponder target was not registered in the published runtime");

        final StructuralInducerBlockEntity inducer =
                (StructuralInducerBlockEntity) helper.getBlockEntity(inducerPos);
        helper.runAfterDelay(4, () -> {
            inducer.setExternalSignal(15);
            StructuralInducerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(inducerPos),
                    inducer.getBlockState(), inducer);
            helper.assertTrue(inducer.trackedStructureCount() == 1,
                    "Structural Inducer did not assemble the Copycats+ block entity");
        });

        helper.runAfterDelay(24, () -> {
            final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
            final ServerSubLevel assembled = container.getAllSubLevels().stream()
                    .filter(ServerSubLevel.class::isInstance)
                    .map(ServerSubLevel.class::cast)
                    .findFirst().orElse(null);
            helper.assertTrue(assembled != null,
                    "Copycats+ block entity did not survive Sable structure assembly");
            if (assembled == null) return;
            try {
                final var assembledBlockEntities = assembled.getPlot().getLoadedChunks().stream()
                        .flatMap(h -> h.getChunk().getBlockEntities().values().stream()).toList();
                final var assembledMaterials = assembledBlockEntities.stream()
                        .flatMap(be -> MagCopycatsCompat.materialsOf(be).stream()).toList();
                final var magneticState = ShipMagneticScanner.scan(assembled);
                helper.assertTrue(magneticState.ferrousBlockCount() == 1,
                        "Assembled Copycats+ iron material was not counted exactly once; count="
                                + magneticState.ferrousBlockCount() + ", blockEntities="
                                + assembledBlockEntities.stream().map(be -> be.getClass().getName()).toList()
                                + ", materials=" + assembledMaterials);
                helper.succeed();
            } finally {
                container.removeSubLevel(assembled, SubLevelRemovalReason.REMOVED);
            }
        });
    }
}
