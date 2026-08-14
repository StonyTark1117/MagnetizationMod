package com.stonytark.magnetization.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated Curios runtime profile for equipment activation and its master gate. */
@GameTestHolder("magnetization_curios")
@PrefixGameTestTemplate(false)
public final class CuriosGameTests {
    private CuriosGameTests() {}

    /**
     * Guards the data-pack half of the integration. Capability registration and
     * activation tests are insufficient when the player entity assignment is
     * missing: Curios will attach an empty inventory and none of the advertised
     * equipment slots can be used through its UI.
     */
    @GameTest(template = "empty", timeoutTicks = 60, batch = "curiosSlots")
    public static void playerReceivesAdvertisedSlots(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "curio-slot-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        try {
            final var handler = player.getCapability(
                    top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
            helper.assertTrue(handler != null, "Curios inventory capability should attach to players");
            assertSlot(helper, handler, "charm",
                    new net.minecraft.world.item.ItemStack(
                            com.stonytark.magnetization.registry.MagItems.FIELD_COMPASS.get()));
            assertSlot(helper, handler, "back",
                    new net.minecraft.world.item.ItemStack(
                            com.stonytark.magnetization.registry.MagItems.MAGNETIC_GRAPPLE.get()));
            assertSlot(helper, handler, "hands",
                    new net.minecraft.world.item.ItemStack(
                            com.stonytark.magnetization.registry.MagItems.REPULSOR_GUN.get()));
            helper.succeed();
        } finally {
            player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    private static void assertSlot(
            final GameTestHelper helper,
            final Object handlerValue,
            final String slotId,
            final net.minecraft.world.item.ItemStack expectedItem
    ) {
        // Keep optional Curios types out of this method's descriptor. NeoForge's
        // client GameTest discovery reflects every holder even when its namespace
        // is disabled; a Curios-typed parameter therefore crashed unrelated Jade
        // and TOP profiles before their title screens could initialize.
        final var handler = (top.theillusivec4.curios.api.type.capability.ICuriosItemHandler) handlerValue;
        final var slot = handler.getCurios().get(slotId);
        helper.assertTrue(slot != null, "Player is missing advertised Curios slot: " + slotId);
        helper.assertTrue(slot.getStacks().getSlots() >= 1,
                "Advertised Curios slot has no usable entries: " + slotId);
        helper.assertTrue(slot.getStacks().isItemValid(0, expectedItem),
                expectedItem.getHoverName().getString() + " is rejected by Curios slot " + slotId);
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "curiosMasterCompat")
    public static void masterGatesRealCurioActivation(final GameTestHelper helper) {
        MagGameTests.curioRepulsorPayloadActivatesRealCharm(helper);
    }
}
