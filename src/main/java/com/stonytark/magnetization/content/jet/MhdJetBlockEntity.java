package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagItems;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * MHD Jet Thruster (ionocraft). The mod's strongest ship propulsion: an
 * electromagnetohydrodynamic engine that pushes magnetic Sable craft along its
 * facing — silently, with no propeller. Needs <b>both</b> a magnet (slotted via
 * right-click) and <b>FE electricity</b>: the magnet sets the speed ceiling +
 * thrust, and the stronger the magnet the more FE it burns per tick, so a big
 * magnet only reaches full speed if you feed it matching power.
 */
public class MhdJetBlockEntity extends BlockEntity implements com.stonytark.magnetization.menu.MachineGuiData {

    private static final int CAPACITY = 400_000;
    private static final int MAX_RECEIVE = 8_000;
    private static final double THRUST_RANGE = 7.0;

    private final ReceiveBuffer energy = new ReceiveBuffer(CAPACITY, MAX_RECEIVE);
    private final net.minecraft.world.SimpleContainer magnetSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) { return isMagnet(st); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() { super.setChanged(); MhdJetBlockEntity.this.setChanged(); }
    };

    public MhdJetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MHD_JET.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public ItemStack getMagnet() { return magnetSlot.getItem(0); }
    public net.minecraft.world.Container magnetContainer() { return magnetSlot; }

    public ItemStack setMagnet(final ItemStack stack) {
        final ItemStack prev = magnetSlot.getItem(0);
        magnetSlot.setItem(0, stack);
        return prev;
    }

    // ── MachineGuiData (shared GUI) ──
    @Override public net.minecraft.world.Container guiInput() { return magnetSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.JET;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return CAPACITY; }

    /** {maxSpeed, dvPerTick, feCostPerTick} for the slotted magnet — STRONG by design. */
    private static double[] tier(final ItemStack stack) {
        if (stack.is(MagItems.PERMANENT_MAGNET.get())) return new double[]{3.0, 0.18, 64};
        if (stack.is(MagItems.TEMPORARY_MAGNET.get())) return new double[]{2.2, 0.12, 24};
        if (stack.is(MagItems.MAGNETIC_PLATE.get()))   return new double[]{1.6, 0.07, 8};
        return null;
    }

    public static boolean isMagnet(final ItemStack stack) { return tier(stack) != null; }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MhdJetBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        final double[] t = tier(be.getMagnet());
        final boolean running = t != null && be.energy.getEnergyStored() >= (int) t[2];
        if (running) {
            be.energy.drainInternal((int) t[2]);
            be.thrust(server, pos, state, t[0], t[1]);
        }
        if (server.getGameTime() % 10L == 0L) {
            server.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS); // refresh WTHIT (energy)
        }
        if (state.getValue(BlockStateProperties.LIT) != running) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, running), Block.UPDATE_CLIENTS);
        }
    }

    /** Push magnetic ships in range along the jet's facing, up to maxSpeed. */
    private void thrust(final ServerLevel server, final BlockPos pos, final BlockState state,
                        final double maxSpeed, final double dv) {
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
            final double ddx = p.x() - cx, ddy = p.y() - cy, ddz = p.z() - cz;
            if (ddx * ddx + ddy * ddy + ddz * ddz > rangeSqr) continue;
            final RigidBodyHandle handle = RigidBodyHandle.of(ship);
            if (handle == null || !handle.isValid()) continue;
            final Vector3dc v = handle.getLinearVelocity();
            final double along = v.x() * dir.x + v.y() * dir.y + v.z() * dir.z;
            if (along >= maxSpeed) continue;
            handle.addLinearAndAngularVelocity(
                    new Vector3d(dir.x * dv, dir.y * dv, dir.z * dv), new Vector3d(0, 0, 0));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Magnet", magnetSlot.createTag(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        magnetSlot.fromTag(tag.getList("Magnet", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }


    /** Cable-fed buffer: external receive only; the jet drains it internally. */
    private static final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int maxReceive) { super(capacity, maxReceive, 0); }
        void drainInternal(final int amount) { this.energy = Math.max(0, this.energy - amount); }
        void setStored(final int value) { this.energy = Math.max(0, Math.min(capacity, value)); }
    }
}
