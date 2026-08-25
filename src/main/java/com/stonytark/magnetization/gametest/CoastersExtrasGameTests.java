package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.simulatedcoasters.MagSimulatedCoastersCompat;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

/** Proves Coasters Extras remains an extension of the parent Coasters bridge. */
@GameTestHolder("magnetization_coasters_extras")
@PrefixGameTestTemplate(false)
public final class CoastersExtrasGameTests {
    private CoastersExtrasGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void functionalMaterialsAndParentCartContractRemainAvailable(final GameTestHelper helper) {
        for (final String path : new String[]{"boost_track", "powered_boost_track", "brake_track",
                "station_track", "sensor_track", "slippery_track", "reverse_track",
                "splash_track", "rust_track", "bobsled_track", "launch_track"}) {
            final var id = ResourceLocation.fromNamespaceAndPath("coasters_extras", path);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id),
                    "Coasters Extras functional track item is missing: " + id);
        }
        for (final String path : new String[]{"oak_track", "stone_track", "rainbow_track",
                "white_wool_track", "blue_concrete_track"}) {
            final var id = ResourceLocation.fromNamespaceAndPath("coasters_extras", path);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id),
                    "Coasters Extras decorative track item is missing: " + id);
        }
        final var cart = CoasterCartSpawner.spawnMinimalContraption(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(5, 7, 5))),
                new Quaterniond());
        helper.runAfterDelay(12L, () -> {
            try {
                helper.assertTrue(cart != null && MagSimulatedCoastersCompat.isCoasterCart(cart)
                                && MagSimulatedCoastersCompat.receivesMagneticFields(cart),
                        "Coasters Extras stopped using the parent cart compatibility contract");
                helper.assertTrue(!MagSimulatedCoastersCompat.structuralInducerCanAdopt(cart),
                        "A loose Coasters Extras cart bypassed the parent Structural Inducer safety rule");
                helper.succeed();
            } finally {
                final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                        .getContainer(helper.getLevel());
                if (container != null && cart != null && !cart.isRemoved()) {
                    container.removeSubLevel(cart,
                            dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
            }
        });
    }
}
