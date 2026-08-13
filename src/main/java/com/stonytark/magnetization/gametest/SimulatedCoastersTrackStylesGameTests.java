package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.simulatedcoasters.MagSimulatedCoastersCompat;
import com.stonytark.magnetization.config.MagConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks;
import dev.silvergold.simulatedcoasters.track.anchor.CoasterAnchorpointBlockEntity;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathEdge;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathGraphManager;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathNode;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathTrackFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

/** Combined-runtime contract for the optional Coasters Track Styles addon. */
@GameTestHolder("magnetization_simulatedcoasters_track_styles")
@PrefixGameTestTemplate(false)
public final class SimulatedCoastersTrackStylesGameTests {
    private static final int BEAM_COLOR = 0x4422AA;
    private static final int RAIL_COLOR = 0xDDDDAA;

    private SimulatedCoastersTrackStylesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void styledTrackMetadataPreservesCoasterMagnetics(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("simulatedcoasters"),
                "Combined profile is missing Create: Coasters Simulated");
        helper.assertTrue(ModList.get().isLoaded("coasterssimulatedextratypes"),
                "Combined profile is missing Coasters Simulated: Track Styles");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(
                        "coasterssimulatedextratypes", "coaster_style_tool")),
                "Track Styles did not register its Coaster Style Tool");

        final BlockPos anchorPos = new BlockPos(1, 1, 1);
        helper.setBlock(anchorPos, SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get());
        final var blockEntity = helper.getBlockEntity(anchorPos);
        helper.assertTrue(blockEntity instanceof CoasterAnchorpointBlockEntity,
                "Coasters anchor block entity was not created");
        if (!(blockEntity instanceof CoasterAnchorpointBlockEntity anchor)) return;

        anchor.getPersistentData().putInt("CoasterBeamColor", BEAM_COLOR);
        anchor.getPersistentData().putInt("CoasterRailColor", RAIL_COLOR);
        final BlockPos peer = helper.absolutePos(anchorPos.offset(4, 0, 0));
        helper.assertTrue(anchor.getPeerCurveBeamDiffuseRgb(peer) == BEAM_COLOR
                        && anchor.getPeerCurveRailDiffuseRgb(peer) == RAIL_COLOR,
                "Track Styles mixin did not preserve independent anchor beam/rail colors");

        final Vec3 cartPosition = Vec3.atCenterOf(helper.absolutePos(new BlockPos(7, 5, 7)));
        final BlockPos railFrom = BlockPos.containing(cartPosition.add(-3.0d, 0.0d, 0.0d));
        final BlockPos railTo = railFrom.offset(6, 0, 0);
        final CoasterPathEdge edge = CoasterPathEdge.straight(railFrom, railTo,
                Vec3.atCenterOf(railFrom), Vec3.atCenterOf(railTo));
        final var graph = CoasterPathGraphManager.get(helper.getLevel());
        graph.upsertNode(new CoasterPathNode(railFrom, new Vec3(0, 1, 0)));
        graph.upsertNode(new CoasterPathNode(railTo, new Vec3(0, 1, 0)));
        graph.addEdge(edge);
        final ServerSubLevel cart = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), cartPosition, new Quaterniond(),
                new CoasterPathTrackFrame.GraphHit(cartPosition, edge, 0.5d));
        if (cart == null) {
            helper.fail("Combined runtime could not spawn a rail-engaged coaster cart");
            return;
        }

        final boolean originalMaster = MagConfig.SIMULATED_COASTERS_COMPAT_ENABLED.get();
        final boolean originalInducer = MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.get();
        helper.runAfterDelay(12L, () -> {
            try {
                MagConfig.SIMULATED_COASTERS_COMPAT_ENABLED.set(true);
                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(true);
                helper.assertTrue(MagSimulatedCoastersCompat.isCoasterCart(cart),
                        "Track-styled runtime stopped recognizing Coasters cart sublevels");
                helper.assertTrue(MagSimulatedCoastersCompat.isRailEngaged(cart),
                        "Track-styled runtime lost the cart's rail engagement");
                helper.assertTrue(MagSimulatedCoastersCompat.structuralInducerCanAdopt(cart),
                        "Track-styled rail-engaged cart was rejected by the Structural Inducer bridge");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_COASTERS_COMPAT_ENABLED.set(originalMaster);
                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(originalInducer);
                remove(helper, cart);
            }
        });
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel cart) {
        final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container != null && container.getSubLevel(cart.getUniqueId()) != null) {
            container.removeSubLevel(cart, SubLevelRemovalReason.REMOVED);
        }
    }
}
