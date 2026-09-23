package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.simulatedcoasters.MagSimulatedCoastersCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.silvergold.simulatedcoasters.SimulatedCoastersBlocks;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Exercises the exact Coasters Simulated Additions 0.2.0 registry contracts. */
@GameTestHolder("magnetization_coasters_additions")
@PrefixGameTestTemplate(false)
public final class CoastersAdditionsGameTests {
    private CoastersAdditionsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void coasterAddonObjectsLoadAndItsFinIsAMagneticEmitter(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("coasterfins"),
                "Create: Coasters Simulated Additions is not loaded in its isolated profile");
        for (final String id : new String[]{"coaster_fin", "toutatis_cart", "toutatis_front",
                "bm_inverted_cart", "white_coaster_seat", "station", "coaster_sensor"}) {
            final Block block = BuiltInRegistries.BLOCK.get(id(id));
            helper.assertTrue(block != Blocks.AIR,
                    "Published Coasters Additions block is missing: coasterfins:" + id);
        }
        for (final String id : new String[]{"lift_marker", "lift_path", "current_marker", "current_path"}) {
            helper.assertTrue(BuiltInRegistries.ITEM.get(id(id))
                            != net.minecraft.world.item.Items.AIR,
                    "Published Coasters Additions path item is missing: coasterfins:" + id);
        }

        final Block fin = BuiltInRegistries.BLOCK.get(id("coaster_fin"));
        helper.assertTrue(fin instanceof dev.simulated_team.simulated.content.blocks.redstone_magnet.RedstoneMagnetBlock,
                "Coasters Track Control Fin no longer extends Simulated's Redstone Magnet");
        helper.assertTrue(fin.defaultBlockState().is(MagTags.MAGNETIC_EMITTER_BLOCKS),
                "Coasters Track Control Fin is not counted as an onboard magnetic emitter");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void claddingAndSeatsDoNotBreakCoasterCartFieldResponse(final GameTestHelper helper) {
        final boolean original = MagConfig.SIMULATED_COASTERS_FIELD_REACTION.get();
        final Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 8, 6)));
        final ServerSubLevel cart = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), position, new Quaterniond());
        if (cart == null) {
            helper.fail("Create: Coasters Simulated could not spawn its cart/cladding fixture");
            return;
        }
        final var plot = cart.getPlot();
        final BlockPos bearing = CoasterCartPlotScan.representativeBearingPlotPos(
                plot, SimulatedCoastersBlocks.COASTER_CART_BLOCK.get());
        if (bearing == null) {
            remove(helper, cart);
            helper.fail("Spawned coaster cart has no bearing block to attach addon content to");
            return;
        }
        final BlockPos bodyPos = bearing.subtract(plot.getCenterBlock()).above();
        final BlockPos seatPos = bodyPos.above();
        final Block toutatisBody = BuiltInRegistries.BLOCK.get(id("toutatis_cart"));
        final Block seat = BuiltInRegistries.BLOCK.get(id("white_coaster_seat"));
        plot.getEmbeddedLevelAccessor().setBlock(bodyPos, toutatisBody.defaultBlockState(), 3);
        plot.getEmbeddedLevelAccessor().setBlock(seatPos, seat.defaultBlockState(), 3);
        helper.runAfterDelay(12L, () -> {
            try {
                helper.assertTrue(plot.getEmbeddedLevelAccessor().getBlockState(bodyPos).is(toutatisBody)
                                && plot.getEmbeddedLevelAccessor().getBlockState(seatPos).is(seat),
                        "Could not place the published Coasters Additions cladding and seat on the cart");
                helper.assertTrue(MagSimulatedCoastersCompat.isCoasterCart(cart),
                        "Addon cladding or seat caused the parent Coasters cart contract to be lost");

                final RigidBodyHandle handle = RigidBodyHandle.of(cart);
                helper.assertTrue(handle != null, "Clad coaster cart has no Sable physics handle");
                final MagneticField field = new MagneticField(position.add(5, 0, 0),
                        new Vec3(1, 0, 0), MagneticPolarity.SOUTH, MagneticStrength.EXTREME,
                        MagneticField.Shape.OMNIDIRECTIONAL, 16.0d);
                MagConfig.SIMULATED_COASTERS_FIELD_REACTION.set(true);
                final Vector3d before = handle.getLinearVelocity(new Vector3d());
                FieldApplicator.applyToSubLevelsOnly(helper.getLevel(), field, null, null);
                final Vector3d after = handle.getLinearVelocity(new Vector3d());
                helper.assertTrue(new Vector3d(after).sub(before).lengthSquared() > 1.0e-8,
                        "Coaster cart with addon cladding and a seat did not react to a magnetic field");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_COASTERS_FIELD_REACTION.set(original);
                remove(helper, cart);
            }
        });
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel cart) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(
                helper.getLevel());
        if (container != null && container.getSubLevel(cart.getUniqueId()) != null) {
            container.removeSubLevel(cart, SubLevelRemovalReason.REMOVED);
        }
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath("coasterfins", path);
    }
}
