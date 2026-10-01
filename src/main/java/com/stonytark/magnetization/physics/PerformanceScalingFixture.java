package com.stonytark.magnetization.physics;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Disposable opt-in benchmark fixtures, invoked only by PerformanceFixture. */
final class PerformanceScalingFixture {
    private record MovingShip(ServerSubLevel ship, Vec3 origin) {}
    private static final Map<ServerLevel, PerformanceScalingFixture> ACTIVE = new WeakHashMap<>();
    private final List<BlockPos> blocks = new ArrayList<>();
    private final Set<Long> forced = new HashSet<>();
    private final List<MovingShip> ships = new ArrayList<>();
    private int queries;
    private long ticks;
    private long queryHits;

    static void setup(ServerLevel level, String kind, int count) {
        clear(level);
        var fixture = new PerformanceScalingFixture();
        ACTIVE.put(level, fixture);
        switch (kind) {
            case "native" -> {
                fixture.queries = count;
                fixture.place(level, new BlockPos(0, 79, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());
                fixture.place(level, new BlockPos(0, 80, 0), MagBlocks.ELECTROMAGNET.get().defaultBlockState());
            }
            case "shafts", "ships" -> {
                for (int i = 0; i < count; i++) fixture.network(level, i, kind.equals("ships"));
            }
            default -> throw new IllegalArgumentException("Unknown scaling fixture " + kind);
        }
    }

    private void place(ServerLevel level, BlockPos p, BlockState state) {
        long chunk = ChunkPos.asLong(p);
        if (!level.getForcedChunks().contains(chunk)) {
            level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
            forced.add(chunk);
        }
        level.setBlock(p, state, 3);
        blocks.add(p);
    }

    private void network(ServerLevel level, int index, boolean moving) {
        var source = new BlockPos((index % 16) * 12 - 90, 160, (index / 16) * 12 - 90);
        var shaft = MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        place(level, source, shaft);
        place(level, source.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        var motor = (CreativeMotorBlockEntity) level.getBlockEntity(source.west());
        motor.generatedSpeed.setValue(32);
        motor.updateGeneratedRotation();
        var receiver = source.east(3);
        place(level, receiver, shaft);
        if (!moving) return;
        place(level, receiver.above(), Blocks.IRON_BLOCK.defaultBlockState());
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, receiver, List.of(receiver, receiver.above()),
                new BoundingBox3i(receiver.getX(), receiver.getY(), receiver.getZ(),
                        receiver.getX() + 1, receiver.getY() + 2, receiver.getZ() + 1));
        if (ship == null) throw new IllegalStateException("Scaling ship assembly failed");
        ships.add(new MovingShip(ship, receiver.getCenter()));
        var anchorPos = source.south(3);
        place(level, anchorPos, MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState());
        var anchor = level.getBlockEntity(anchorPos);
        var tag = anchor.saveWithoutMetadata(level.registryAccess());
        tag.putUUID("BoundShip", ship.getUniqueId());
        anchor.loadWithComponents(tag, level.registryAccess());
        var switchPos = anchorPos.south();
        place(level, switchPos, MagBlocks.MAGNETIC_SWITCH.get().defaultBlockState());
        ((com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity)
                level.getBlockEntity(switchPos)).linkAnchor(anchorPos);
    }

    static void tick(ServerLevel level) {
        var fixture = ACTIVE.get(level);
        if (fixture == null) return;
        fixture.ticks++;
        for (int i = 0; i < fixture.queries; i++) {
            Vec3 point = new Vec3((i % 32) - 15.5, 82.5, ((i / 32) % 32) - 15.5);
            if (MagneticFields.isInField(level, point)) fixture.queryHits++;
        }
        var container = SubLevelContainer.getContainer(level);
        for (int i = 0; i < fixture.ships.size(); i++) {
            var moving = fixture.ships.get(i);
            // Bounded repeated range entry/exit and rotation, including docking reads.
            var origin = moving.origin();
            container.physicsSystem().getPipeline().teleport(moving.ship(),
                    new org.joml.Vector3d(origin.x + Math.sin(fixture.ticks / 30.0 + i) * 3,
                            origin.y, origin.z), new org.joml.Quaterniond().rotateY(Math.sin(fixture.ticks / 40.0) * .2));
        }
    }

    static Map<String, Long> counts(ServerLevel level) {
        var f = ACTIVE.get(level);
        if (f == null) return Map.of();
        var shafts = com.stonytark.magnetization.content.shaft.MagneticShaftNetwork.loadedShafts(level);
        return Map.of("scaling_ticks", f.ticks, "synthetic_point_queries", f.ticks * f.queries,
                "synthetic_query_hits", f.queryHits, "moving_ships", (long) f.ships.size(),
                "loaded_shafts", (long) shafts.size(),
                "driven_shafts", shafts.stream().filter(s -> s.status() == com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity.Status.SOURCE).count(),
                "receiving_shafts", shafts.stream().filter(s -> s.status() == com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity.Status.RECEIVING).count());
    }

    static void clear(ServerLevel level) {
        var f = ACTIVE.remove(level);
        if (f == null) return;
        var container = SubLevelContainer.getContainer(level);
        for (var moving : f.ships) container.removeSubLevel(moving.ship(), SubLevelRemovalReason.REMOVED);
        for (var p : f.blocks) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        for (long key : f.forced) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
    }
}
