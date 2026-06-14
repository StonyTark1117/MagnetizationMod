package com.stonytark.magnetization.content.repulsor;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

public class RepulsorCoilBlockEntity extends AbstractEmitterBlockEntity {

    // Directional-thrust ("propulsion track") tuning, used when a Vector Core is slotted.
    private static final double THRUST_RANGE = 6.0;
    private static final double THRUST_DV = 0.045;       // velocity added per tick toward facing
    private static final double MAX_TRACK_SPEED = 1.1;   // terminal speed along the track

    /** A Vector Core (titanomagnetite) installed → the coil thrusts ships along
     *  its facing (a magnetic conveyor) on top of its normal repulsion. */
    private boolean hasVectorCore = false;

    public RepulsorCoilBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.REPULSOR_COIL.get(), pos, state);
    }

    public boolean hasVectorCore() {
        return hasVectorCore;
    }

    public void setVectorCore(final boolean installed) {
        this.hasVectorCore = installed;
        setChanged();
    }

    /** Repulsor ticker: the standard emitter field, plus directional thrust when
     *  a Vector Core is installed. (Named distinctly so the method reference in
     *  the block's getTicker isn't ambiguous with the inherited generic.) */
    public static void tickRepulsor(final Level level, final BlockPos pos, final BlockState state,
                                    final RepulsorCoilBlockEntity be) {
        AbstractEmitterBlockEntity.serverTick(level, pos, state, be);
        if (be.hasVectorCore && be.isPowered() && level instanceof ServerLevel server) {
            be.thrustShips(server, pos, state);
        }
    }

    /** Push magnetic ships within range along the coil's facing, up to a terminal
     *  track speed — a self-propelling magnetic conveyor. */
    private void thrustShips(final ServerLevel server, final BlockPos pos, final BlockState state) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return;
        final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING) : Direction.UP;
        final Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        final double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        final double rangeSqr = THRUST_RANGE * THRUST_RANGE;

        for (final SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship)) continue;
            if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0) continue;
            if (ShipMagneticRegistry.get(server, ship).susceptibility() <= 0.0) continue;
            final Vector3dc p = ship.logicalPose().position();
            final double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            if (dx * dx + dy * dy + dz * dz > rangeSqr) continue;
            final RigidBodyHandle handle = RigidBodyHandle.of(ship);
            if (handle == null || !handle.isValid()) continue;
            final Vector3dc v = handle.getLinearVelocity();
            final double along = v.x() * dir.x + v.y() * dir.y + v.z() * dir.z;
            if (along >= MAX_TRACK_SPEED) continue; // already at track speed
            handle.addLinearAndAngularVelocity(
                    new Vector3d(dir.x * THRUST_DV, dir.y * THRUST_DV, dir.z * THRUST_DV),
                    new Vector3d(0, 0, 0));
        }
    }

    @Override
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        return 8.0d;
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (!isPowered()) return null;
        final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING)
                : Direction.UP;
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        final MagneticStrength strength = effectiveStrength(MagneticStrength.MEDIUM);
        final double range = effectiveRange(strength);
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                axis,
                effectivePolarity(MagneticPolarity.NORTH),
                strength,
                MagneticField.Shape.CONICAL,
                range == strength.range() ? 0.0d : range
        );
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("VectorCore", hasVectorCore);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.hasVectorCore = tag.getBoolean("VectorCore");
    }
}
