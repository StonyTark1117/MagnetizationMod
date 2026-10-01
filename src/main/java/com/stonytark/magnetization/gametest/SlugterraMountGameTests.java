package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("magnetization_slugterra")
@PrefixGameTestTemplate(false)
public final class SlugterraMountGameTests {
    private static final List<String> MOUNTS = List.of("burro_mecha", "perro_mecha", "toro_mecha");

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void emittersMoveRiddenAndUnriddenMountsAndSendMotion(GameTestHelper h) {
        final var level = h.getLevel();
        final var origin = h.absolutePos(new BlockPos(1, 50, 1));
        final var rider = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        rider.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        try {
            for (String id : MOUNTS) for (Block block : List.of(MagBlocks.TRACTOR_BEAM.get(),
                    MagBlocks.MAGNETIC_ANCHOR.get(), MagBlocks.REPULSOR_COIL.get())) {
                var state = block.defaultBlockState();
                if (state.hasProperty(DirectionalBlock.FACING)) state = state.setValue(DirectionalBlock.FACING, Direction.EAST);
                level.setBlockAndUpdate(origin, state);
                var emitter = (AbstractEmitterBlockEntity) level.getBlockEntity(origin);
                emitter.setRangeOverride(8);
                emitter.setStrengthOverride(MagneticStrength.WEAK);
                emitter.setRedstoneLevel(15);
                Vec3 unriddenImpulse = null;
                for (boolean ridden : List.of(false, true)) {
                    final var mount = create(h, id);
                    mount.setPos(Vec3.atCenterOf(origin.east(3)));
                    mount.setOldPosAndRot();
                    mount.setNoGravity(true);
                    level.addFreshEntity(mount);
                    try {
                        if (ridden) {
                            rider.setPos(mount.position());
                            rider.setOldPosAndRot();
                            h.assertTrue(rider.startRiding(mount, true), "Could not mount " + id);
                        }
                        mount.setDeltaMovement(Vec3.ZERO);
                        final List<Packet<?>> packets = new ArrayList<>();
                        final var tracker = new ServerEntity(level, mount, 1, true, packets::add);
                        AbstractEmitterBlockEntity.serverTick(level, origin, state, emitter);
                        final var impulse = mount.getDeltaMovement();
                        final boolean repel = block == MagBlocks.REPULSOR_COIL.get();
                        h.assertTrue(repel ? impulse.x > 0 : impulse.x < 0, "Wrong field response: " + id + "/" + block + "/ridden=" + ridden);
                        h.assertTrue(impulse.length() <= MagConfig.SLUGTERRA_MOUNT_MAX_IMPULSE.get() + 1e-9, "Mount impulse exceeds cap");
                        if (!ridden) unriddenImpulse = impulse;
                        else h.assertTrue(impulse.distanceTo(unriddenImpulse) < 1e-9, "Armored rider doubled mount force");
                        tracker.sendChanges();
                        h.assertTrue(packets.stream().anyMatch(p -> p instanceof ClientboundSetEntityMotionPacket motion
                                && motion.getId() == mount.getId() && Math.signum(motion.getXa()) == Math.signum(impulse.x)),
                                "Tracker did not broadcast mount motion");
                        final Vec3 before = mount.position();
                        // Dispatches through Bajoterra's native ridden travel override.
                        mount.travel(Vec3.ZERO);
                        h.assertTrue((mount.getX() - before.x) * impulse.x > 0, "Native travel erased magnetic displacement: " + id + "/" + block + "/ridden=" + ridden + " impulse=" + impulse + " before=" + before + " after=" + mount.position());
                        if (ridden) {
                            rider.rideTick();
                            h.assertTrue(rider.getVehicle() == mount, "Field dismounted rider");
                            h.assertTrue(Math.abs(rider.getX() - mount.getX()) < 0.01, "Passenger did not follow mount");
                        }
                    } finally { rider.stopRiding(); mount.discard(); }
                }
                level.removeBlock(origin, false);
            }
            for (var setting : List.of(MagConfig.SLUGTERRA_MOUNTS_ENABLED, MagConfig.SLUGTERRA_COMPAT_ENABLED)) {
                boolean old = setting.get();
                try {
                    setting.set(false);
                    for (String id : MOUNTS) {
                        var mount = create(h, id);
                        h.assertTrue(!FieldApplicator.isMagnetizableTarget(mount), "Disabled intrinsic mount response remains");
                        mount.discard();
                    }
                } finally { setting.set(old); }
            }
            h.succeed();
        } finally { rider.stopRiding(); rider.getInventory().clearContent();
            rider.discard(); level.removeBlock(origin, false); }
    }

    private static Mob create(GameTestHelper h, String id) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("bajoterrafn:" + id));
        return (Mob) Objects.requireNonNull(type.create(h.getLevel()), id);
    }
}
