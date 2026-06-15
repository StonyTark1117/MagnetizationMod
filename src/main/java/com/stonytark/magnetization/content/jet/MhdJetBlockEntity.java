package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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
import org.jetbrains.annotations.Nullable;
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
public class MhdJetBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData, BlockEntitySubLevelActor {

    private static final int CAPACITY = 400_000;
    private static final int MAX_RECEIVE = 8_000;

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

    /** {maxSpeed, dvPerTick, feCostPerTick} for the slotted magnetic material —
     *  STRONG by design, and scaling with the material's potency. A stronger
     *  magnet raises the speed ceiling + acceleration but also burns more FE per
     *  tick, so it only reaches its ceiling if you feed it matching power. */
    private static double[] tier(final ItemStack stack) {
        final int potency = com.stonytark.magnetization.content.MagneticMaterials.potency(stack);
        if (potency <= 0) return null;
        // Strong jet engine: punchy acceleration + a high cruising ceiling, both
        // scaling with magnet potency. (blocks/tick; ×20 = blocks/second.)
        final double maxSpeed = 4.0 + potency * 0.30;   // ~4.3 .. ~12.7 b/t  (86..254 b/s)
        final double dv = 0.4 + potency * 0.06;         // ~0.46 .. ~2.1 b/t per tick
        final double feCost = 8 + potency * 8;          // bigger magnet → more FE/tick
        return new double[]{maxSpeed, dv, feCost};
    }

    public static boolean isMagnet(final ItemStack stack) { return tier(stack) != null; }

    /** Vanilla ticker: the jet is in the open world (or, defensively, a
     *  contraption still driven by the world ticker) — resolve its host ship. */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MhdJetBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.runEngine(server, SableBridge.subLevelAt(server, pos));
    }

    /** Sable sub-level tick: the jet is mounted on this ship — thrust it. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (level instanceof ServerLevel server) runEngine(server, subLevel);
    }

    /** Engine logic shared by both tick paths. An MHD jet only does work when it
     *  sits on a ship (its {@code host}); off-ship it's inert. */
    private void runEngine(final ServerLevel server, final @Nullable ServerSubLevel host) {
        final double[] t = tier(getMagnet());
        final boolean firing = t != null && host != null && energy.getEnergyStored() >= (int) t[2];
        if (firing) {
            energy.drainInternal((int) t[2]);
            thrustHost(host, t[0], t[1]);
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != firing) {
            server.setBlock(getBlockPos(), state.setValue(BlockStateProperties.LIT, firing), Block.UPDATE_CLIENTS);
        }
        if (server.getGameTime() % 10L == 0L) {
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // WTHIT
        }
    }

    /** Push the host ship along the jet's facing (transformed to world space),
     *  capped at the magnet's speed ceiling. Applied at the centre of mass so it
     *  is pure forward thrust (no spin). Force is mass-scaled so a given magnet
     *  yields a consistent acceleration regardless of ship size. */
    private void thrustHost(final ServerSubLevel host, final double maxSpeed, final double dv) {
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) return;
        final Direction facing = getBlockState().hasProperty(DirectionalBlock.FACING)
                ? getBlockState().getValue(DirectionalBlock.FACING) : Direction.UP;
        // Thrust OUT of the nozzle — opposite the FACING normal — so the ship is
        // pushed the way the jet visually points (exhaust the other way).
        final Vec3 dirLocal = Vec3.atLowerCornerOf(facing.getOpposite().getNormal());

        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;
        // Speed-ceiling check against the host's world velocity along world-facing.
        final Pose3dc pose = host.logicalPose();
        final Vec3 dirWorld = pose.transformNormal(new Vec3(dirLocal.x, dirLocal.y, dirLocal.z)).normalize();
        final Vector3dc v = handle.getLinearVelocity();
        if (v.x() * dirWorld.x + v.y() * dirWorld.y + v.z() * dirWorld.z >= maxSpeed) return;

        // force = dv·(1/Δt)·m, so applyLocalImpulse's Δv = F·Δt/m collapses to dv.
        final double mass = host.getMassTracker().getMass();
        final double force = dv * 20.0 * mass;
        final Vector3dc com = host.getMassTracker().getCenterOfMass();
        SableBridge.applyLocalImpulse(host,
                new Vector3d(com.x(), com.y(), com.z()),
                new Vector3d(dirLocal.x * force, dirLocal.y * force, dirLocal.z * force));
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
