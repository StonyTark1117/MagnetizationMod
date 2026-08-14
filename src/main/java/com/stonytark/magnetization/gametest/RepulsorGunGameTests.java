package com.stonytark.magnetization.gametest;

import com.mojang.authlib.GameProfile;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Behavioral coverage for every effect advertised by the Repulsor Gun. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RepulsorGunGameTests {
    private static final String EMPTY = "empty";

    private RepulsorGunGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = "repulsorGunBehavior")
    public static void handUsePushesLooseObjectsAndMagneticEntities(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerPlayer player = player(level, helper.absolutePos(new BlockPos(1, 2, 1)));
        final ItemStack stack = new ItemStack(MagItems.REPULSOR_GUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        faceSouth(player);

        final ItemEntity loose = new ItemEntity(level, player.getX(), player.getEyeY(), player.getZ() + 3.0,
                new ItemStack(Items.IRON_INGOT));
        level.addFreshEntity(loose);
        final var golem = EntityType.IRON_GOLEM.create(level);
        helper.assertTrue(golem != null, "Could not create a magnetic entity target");
        golem.setPos(player.getX(), player.getY(), player.getZ() + 4.0);
        level.addFreshEntity(golem);

        final var result = stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "Hand right-click did not activate the Repulsor Gun");
        helper.assertTrue(stack.get(MagDataComponents.FIRED_AT.get()) != null,
                "Hand activation did not stamp the firing state");
        helper.assertTrue(player.getCooldowns().isOnCooldown(MagItems.REPULSOR_GUN.get()),
                "Hand activation did not arm the Repulsor Gun cooldown");
        helper.assertTrue(loose.getDeltaMovement().z > 0.1,
                "Repulsor Gun did not push a loose item inside its cone: " + loose.getDeltaMovement());
        helper.assertTrue(golem.getDeltaMovement().z > 0.001,
                "Repulsor Gun did not repel a magnetic entity inside its cone: " + golem.getDeltaMovement());

        loose.discard();
        golem.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = "repulsorGunBehavior")
    public static void handUseRecoilsFromMagneticEmitter(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        final ServerPlayer player = player(level, playerPos);
        final ItemStack stack = new ItemStack(MagItems.REPULSOR_GUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        faceSouth(player);

        // Keep the emitter inside the 3x3 empty template; its south boundary is
        // itself collidable and would correctly stop the gun's first-hit ray.
        final BlockPos target = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ() + 1.0);
        // The vanilla Lodestone is the manual's headline recoil fixture. It was
        // documented from the gun's first release but omitted from the emitter
        // tag, so firing at one produced particles without launching the user.
        level.setBlock(target, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(target).is(com.stonytark.magnetization.api.MagTags.MAGNETIC_EMITTER_BLOCKS),
                "Vanilla Lodestone fixture is missing from the magnetic-emitter gameplay tag");
        final var hit = level.clip(new ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(12.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        helper.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target),
                "Repulsor recoil ray did not reach its magnetic-emitter fixture: type=" + hit.getType()
                        + " pos=" + hit.getBlockPos() + " target=" + target + " location=" + hit.getLocation()
                        + " eye=" + player.getEyePosition() + " look=" + player.getLookAngle());
        stack.getItem().use(level, player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.getDeltaMovement().z < -0.01,
                "Repulsor Gun did not recoil away from a magnetic emitter: " + player.getDeltaMovement());
        level.removeBlock(target, false);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "repulsorGunBehavior")
    public static void handUsePushesSableShip(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos template = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos playerPos = new BlockPos(template.getX(), 240, template.getZ());
        final BlockPos shipPos = playerPos.offset(0, 1, 4);
        final ServerPlayer player = player(level, playerPos);
        final ItemStack stack = new ItemStack(MagItems.REPULSOR_GUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        faceSouth(player);

        level.setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        final var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                shipPos.getX(), shipPos.getY(), shipPos.getZ(),
                shipPos.getX() + 1, shipPos.getY() + 1, shipPos.getZ() + 1);
        final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                        level, shipPos, List.of(shipPos), bounds);
        helper.assertTrue(ship != null, "Could not assemble the Repulsor Gun ship target");

        helper.runAfterDelay(3L, () -> {
            final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            if (handle == null) {
                removeShip(level, ship);
                player.discard();
                helper.fail("Repulsor Gun ship target has no valid physics handle");
                return;
            }
            final double beforeZ = handle.getLinearVelocity(new org.joml.Vector3d()).z;
            stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
            helper.runAfterDelay(2L, () -> {
                try {
                    final double afterZ = handle.getLinearVelocity(new org.joml.Vector3d()).z;
                    helper.assertTrue(afterZ > beforeZ + 0.01,
                            "Repulsor Gun did not push a Sable ship downrange: "
                                    + beforeZ + " -> " + afterZ);
                } finally {
                    removeShip(level, ship);
                    player.discard();
                }
                helper.succeed();
            });
        });
    }

    private static ServerPlayer player(final ServerLevel level, final BlockPos position) {
        final ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "repulsor-gun-test"),
                ClientInformation.createDefault()) {
            @Override
            protected ItemCooldowns createItemCooldowns() {
                return new ItemCooldowns();
            }
        };
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return player;
    }

    private static void faceSouth(final Player player) {
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setXRot(0.0F);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void removeShip(final ServerLevel level,
                                   final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        if (ship == null || ship.isRemoved()) return;
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container != null) {
            container.removeSubLevel(ship,
                    dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
    }
}
