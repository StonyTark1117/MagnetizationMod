package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

/** Real Ore Excavation runtime coverage. Runs only in its isolated optional profile. */
@GameTestHolder("magnetization_ore_excavation")
@PrefixGameTestTemplate(false)
public final class OreExcavationGameTests {
    private OreExcavationGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void magnetizationOreGroupIsAddedAtExcavationStart(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("oreexcavation"),
                "Ore Excavation compatibility profile did not load Ore Excavation");
        final boolean original = MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.get();
        MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(true);
        try {
            final BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
            final var state = MagBlocks.MAGNETITE_ORE.get().defaultBlockState();
            final var player = headlessPlayer((net.minecraft.server.level.ServerLevel) helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            // This profile is optional, so keep the test class itself free of
            // Ore Excavation method/field references. NeoForge discovers every
            // GameTest class before filtering namespaces; direct references here
            // would make the normal test server fail class discovery when the
            // optional mod is absent.
            final Class<?> agentClass = Class.forName("oreexcavation.handlers.MiningAgent");
            final Object agent = agentClass
                    .getConstructor(net.minecraft.server.level.ServerPlayer.class, BlockPos.class,
                            net.minecraft.world.level.block.state.BlockState.class)
                    .newInstance(player, origin, state);
            final Class<?> startClass = Class.forName("oreexcavation.events.EventExcavate$Start");
            final Object start = startClass.getConstructor(agentClass).newInstance(agent);
            final Class<?> compatClass = Class.forName(
                    "com.stonytark.magnetization.compat.oreexcavation.MagOreExcavationCompat");
            compatClass.getMethod("onExcavationStart", startClass).invoke(null, start);

            helper.assertTrue(state.is(MagTags.ORE_EXCAVATION_BLOCKS),
                    "Magnetite ore is missing from the Ore Excavation compatibility tag");
            final java.lang.reflect.Field blockGroup = agentClass.getField("blockGroup");
            final java.util.Set<?> groups = (java.util.Set<?>) blockGroup.get(agent);
            final java.lang.reflect.Method checkMatch = Class.forName(
                    "oreexcavation.groups.BlockEntry").getMethod("checkMatch",
                    net.minecraft.world.level.block.state.BlockState.class);
            helper.assertTrue(groups.stream().anyMatch(entry -> invokesMatch(checkMatch, entry,
                            MagBlocks.MAGNETITE_ORE.get().defaultBlockState())),
                    "Ore Excavation agent did not receive Magnetization's ore group");
            helper.assertTrue(groups.stream().anyMatch(entry -> invokesMatch(checkMatch, entry,
                            MagBlocks.HELIUM_3_GEODE.get().defaultBlockState())),
                    "Ore Excavation agent did not receive the Helium-3 geode group");
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            helper.fail("Ore Excavation compatibility API changed: " + exception);
        } finally {
            MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(original);
        }
    }

    private static boolean invokesMatch(final java.lang.reflect.Method checkMatch,
                                        final Object entry,
                                        final net.minecraft.world.level.block.state.BlockState state) {
        try {
            return (boolean) checkMatch.invoke(entry, state);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect Ore Excavation block group", exception);
        }
    }

    private static net.minecraft.server.level.ServerPlayer headlessPlayer(
            final net.minecraft.server.level.ServerLevel level) {
        final var player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ore-excavation-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        final var connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                level.getServer(), connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(
                        player.getGameProfile(), false)) {
            @Override
            public void send(final net.minecraft.network.protocol.Packet<?> packet) {
                // This GameTest player has no client connection.
            }
        };
        level.addFreshEntity(player);
        return player;
    }
}
