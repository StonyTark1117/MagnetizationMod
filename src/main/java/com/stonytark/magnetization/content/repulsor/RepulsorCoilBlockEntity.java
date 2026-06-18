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

    /** Holds the Vector Core (titanomagnetite) item. When present, the coil's
     *  repulsion cone ALSO drags magnetic ships caught in it toward the selected
     *  perpendicular direction — it does NOT become an on-ship thruster. */
    private final net.minecraft.world.SimpleContainer vectorCoreSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int slot, final net.minecraft.world.item.ItemStack stack) {
            return stack.is(com.stonytark.magnetization.registry.MagItems.VECTOR_CORE.get());
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {
            super.setChanged();
            // The GUI mutates this container directly; propagate to the BE so the
            // core install/removal persists AND re-syncs to clients (WTHIT + the
            // thrust state revert immediately when the core is pulled out).
            RepulsorCoilBlockEntity.this.setChanged();
            if (level instanceof ServerLevel s) markForClientSync(s);
        }
    };
    /** Which of the 4 directions perpendicular to the coil's facing the thrust
     *  pushes ships in. Cycled via the GUI button. */
    private int thrustDirIndex = 0;

    public RepulsorCoilBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.REPULSOR_COIL.get(), pos, state);
    }

    /** The persistent 1-slot container for the Vector Core (bound into the GUI). */
    public net.minecraft.world.Container getVectorCoreSlot() {
        return vectorCoreSlot;
    }

    public boolean hasVectorCore() {
        return vectorCoreSlot.getItem(0).is(com.stonytark.magnetization.registry.MagItems.VECTOR_CORE.get());
    }

    /** Convenience for right-click install + GameTests: drop a core into / clear
     *  the slot. */
    public void setVectorCore(final boolean installed) {
        vectorCoreSlot.setItem(0, installed
                ? new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.VECTOR_CORE.get())
                : net.minecraft.world.item.ItemStack.EMPTY);
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server); // push to client so WTHIT/GUI updates live
    }

    /** The 4 world directions perpendicular to {@code facing} (those whose axis
     *  differs), in a stable order. Always length 4. */
    public static Direction[] perpendicularDirs(final Direction facing) {
        final Direction.Axis axis = facing.getAxis();
        final Direction[] out = new Direction[4];
        int i = 0;
        for (final Direction d : Direction.values()) {
            if (d.getAxis() != axis) out[i++] = d;
        }
        return out;
    }

    /** World direction the added thrust currently pushes ships in. */
    public Direction thrustDirection() {
        final Direction facing = getBlockState().hasProperty(DirectionalBlock.FACING)
                ? getBlockState().getValue(DirectionalBlock.FACING) : Direction.UP;
        return perpendicularDirs(facing)[Math.floorMod(thrustDirIndex, 4)];
    }

    public int thrustDirIndex() { return Math.floorMod(thrustDirIndex, 4); }

    /** Advance the thrust direction to the next perpendicular option. */
    public void cycleThrustDir() {
        thrustDirIndex = Math.floorMod(thrustDirIndex + 1, 4);
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> extraTooltipLines(final boolean verbose) {
        final java.util.List<net.minecraft.network.chat.Component> lines =
                new java.util.ArrayList<>(super.extraTooltipLines(verbose));
        if (hasVectorCore()) {
            lines.add(net.minecraft.network.chat.Component.translatable(
                            "tooltip.magnetization.repulsor.vector_core",
                            net.minecraft.network.chat.Component.translatable(
                                    "tooltip.magnetization.direction." + thrustDirection().getSerializedName()))
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        } else {
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.magnetization.repulsor.no_core")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** Repulsor ticker: the standard emitter field, plus directional thrust when
     *  a Vector Core is installed. (Named distinctly so the method reference in
     *  the block's getTicker isn't ambiguous with the inherited generic.) */
    public static void tickRepulsor(final Level level, final BlockPos pos, final BlockState state,
                                    final RepulsorCoilBlockEntity be) {
        AbstractEmitterBlockEntity.serverTick(level, pos, state, be);
        if (be.hasVectorCore() && be.isPowered() && level instanceof ServerLevel server) {
            be.thrustShips(server, pos, state);
        }
    }

    /** With a Vector Core installed, ships caught in the repulsion CONE (along the
     *  coil's facing) are additionally dragged toward the selected perpendicular
     *  direction, up to a terminal speed. This rides on top of the normal cone
     *  repulsion — it is NOT an on-ship thruster. */
    private void thrustShips(final ServerLevel server, final BlockPos pos, final BlockState state) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return;
        final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING) : Direction.UP;
        final Vec3 coneAxis = Vec3.atLowerCornerOf(facing.getNormal());
        final Direction push = thrustDirection();
        final Vec3 dir = Vec3.atLowerCornerOf(push.getNormal());
        final double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        final double range = com.stonytark.magnetization.config.MagConfig.repulsorTrackRange();
        final double rangeSqr = range * range;
        // Same cone the repulsion uses: ~60° half-angle around the facing axis.
        final double coneCos = 0.5;
        final double thrust = com.stonytark.magnetization.config.MagConfig.repulsorTrackThrust();
        final double maxSpeed = com.stonytark.magnetization.config.MagConfig.repulsorTrackMaxSpeed();

        for (final SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship)) continue;
            if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0) continue;
            if (ShipMagneticRegistry.get(server, ship).susceptibility() <= 0.0) continue;
            final Vector3dc p = ship.logicalPose().position();
            final double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            final double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > rangeSqr) continue;
            // Must be inside the cone: angle between (coil→ship) and the facing axis.
            final double dist = Math.sqrt(distSqr);
            if (dist > 1.0e-4) {
                final double cos = (dx * coneAxis.x + dy * coneAxis.y + dz * coneAxis.z) / dist;
                if (cos < coneCos) continue; // outside the repulsion cone → not dragged
            }
            final RigidBodyHandle handle = RigidBodyHandle.of(ship);
            if (handle == null || !handle.isValid()) continue;
            final Vector3dc v = handle.getLinearVelocity();
            final double along = v.x() * dir.x + v.y() * dir.y + v.z() * dir.z;
            if (along >= maxSpeed) continue; // already at terminal speed in the push direction
            // Push linearly toward the chosen direction AND bleed off spin, so the
            // ship tracks the direction (a guided conveyor) instead of just
            // tumbling from the off-centre cone repulsion.
            final Vector3dc av = handle.getAngularVelocity();
            final double spinDamp = com.stonytark.magnetization.config.MagConfig.repulsorSpinDamp();
            handle.addLinearAndAngularVelocity(
                    new Vector3d(dir.x * thrust, dir.y * thrust, dir.z * thrust),
                    new Vector3d(-av.x() * spinDamp, -av.y() * spinDamp, -av.z() * spinDamp));
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

    /** Drop the installed Vector Core when the block is broken (called from the
     *  block's onRemove), so it's recoverable. */
    public void dropContents(final Level level, final BlockPos pos) {
        final net.minecraft.world.item.ItemStack core = vectorCoreSlot.removeItemNoUpdate(0);
        if (!core.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, core);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items =
                net.minecraft.core.NonNullList.withSize(1, net.minecraft.world.item.ItemStack.EMPTY);
        items.set(0, vectorCoreSlot.getItem(0));
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("ThrustDir", thrustDirIndex);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items =
                net.minecraft.core.NonNullList.withSize(1, net.minecraft.world.item.ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registries);
        vectorCoreSlot.setItem(0, items.get(0));
        // Back-compat: pre-slot saves stored a boolean "VectorCore".
        if (tag.getBoolean("VectorCore") && vectorCoreSlot.getItem(0).isEmpty()) {
            vectorCoreSlot.setItem(0, new net.minecraft.world.item.ItemStack(
                    com.stonytark.magnetization.registry.MagItems.VECTOR_CORE.get()));
        }
        thrustDirIndex = Math.floorMod(tag.getInt("ThrustDir"), 4);
    }
}
