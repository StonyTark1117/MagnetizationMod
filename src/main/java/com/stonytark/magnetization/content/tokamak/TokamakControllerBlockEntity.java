package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Tokamak fusion reactor controller. When ringed by Tokamak Coils (the 8
 * blocks around it in its own layer form the confinement torus) and loaded with
 * deuterium fuel, it fuses and generates a large, steady FE output that it
 * pushes to adjacent machines/cables. Fuel is loaded by right-clicking with a
 * Deuterium Cell.
 */
public class TokamakControllerBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData {

    private final GenBuffer energy = new GenBuffer(
            com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(),
            // Extract cap = the most any tier can output (He-3) so per-tier limiting
            // in pushEnergy isn't clamped by the buffer's own maxExtract.
            com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3());
    private int burnTime = 0;
    private int currentTier = 0;   // 0 = D-D, 1 = D-T, 2 = D-He³ — set when a cell is consumed
    private int lastOutput = 0; // FE actually pushed to neighbours last tick (GUI readout)

    /** Fuel slot — holds spare fusion cells (D-D / D-T / D-He³), auto-fed into the burn. */
    private final net.minecraft.world.SimpleContainer fuelSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return cellTier(st) >= 0;
        }
        @Override public void setChanged() { super.setChanged(); TokamakControllerBlockEntity.this.setChanged(); }
    };

    /** Fusion-cell tier: 0 = Deuterium, 1 = Tritium, 2 = Helium-3, -1 = not a cell. */
    private static int cellTier(final ItemStack st) {
        if (st.is(com.stonytark.magnetization.registry.MagItems.DEUTERIUM_CELL.get())) return 0;
        if (st.is(com.stonytark.magnetization.registry.MagItems.TRITIUM_CELL.get())) return 1;
        if (st.is(com.stonytark.magnetization.registry.MagItems.HELIUM_3_CELL.get())) return 2;
        return -1;
    }

    private static int tierGenPerTick(final int tier) {
        return switch (tier) {
            case 1 -> com.stonytark.magnetization.config.MagConfig.tokamakGenPerTickTritium();
            case 2 -> com.stonytark.magnetization.config.MagConfig.tokamakGenPerTickHelium3();
            default -> com.stonytark.magnetization.config.MagConfig.tokamakGenPerTick();
        };
    }

    private static int tierBurnTicks(final int tier) {
        return switch (tier) {
            case 1 -> com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksTritium();
            case 2 -> com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksHelium3();
            default -> com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksPerCell();
        };
    }

    private static int tierOutputRate(final int tier) {
        return switch (tier) {
            case 1 -> com.stonytark.magnetization.config.MagConfig.tokamakOutputRateTritium();
            case 2 -> com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3();
            default -> com.stonytark.magnetization.config.MagConfig.tokamakOutputRate();
        };
    }

    public int currentTier() { return currentTier; }

    public TokamakControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TOKAMAK_CONTROLLER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() {
        return energy;
    }

    public net.minecraft.world.Container fuelContainer() {
        return fuelSlot;
    }

    // ── MachineGuiData (shared GUI: fuel runtime + current FE output) ──
    @Override public net.minecraft.world.Container guiInput() { return fuelSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.TOKAMAK;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(); }
    @Override public int guiStat1() { return burnTime; }          // ticks; screen shows seconds
    @Override public int guiStat2() { return lastOutput; }        // FE/tick out
    @Override public int guiStat3() { return currentTier; }       // 0=D-D, 1=D-T, 2=D-He³

    /** Prepend the active fuel tier (D-D / D-T / D-He³) to the shared tokamak HUD
     *  lines so WTHIT/Jade/TOP show which cell is burning + its runtime + output. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> hudLines() {
        final java.util.List<net.minecraft.network.chat.Component> out = new java.util.ArrayList<>();
        final String[] tiers = {"dd", "dt", "he3"};
        out.add(net.minecraft.network.chat.Component.translatable(
                "tooltip.magnetization.gui_tokamak_tier_" + tiers[Math.min(2, Math.max(0, currentTier))])
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        out.addAll(com.stonytark.magnetization.menu.MachineGuiData.super.hudLines());
        return out;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final TokamakControllerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        // Auto-feed: load one cell when the burn is empty, recording its tier so
        // gen/output use that tier's rates (mixing tiers cleanly, one cell at a time).
        if (be.burnTime <= 0) {
            final ItemStack cell = be.fuelSlot.getItem(0);
            final int tier = cellTier(cell);
            if (tier >= 0) {
                cell.shrink(1);
                be.fuelSlot.setItem(0, cell);
                be.currentTier = tier;
                be.burnTime += tierBurnTicks(tier);
                be.setChanged();
            }
        }
        final boolean fusing = be.burnTime > 0 && isRingFormed(level, pos);
        if (fusing) {
            be.energy.generate(tierGenPerTick(be.currentTier));
            be.burnTime--;
            be.setChanged();
        }
        if (state.getValue(BlockStateProperties.LIT) != fusing) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, fusing), Block.UPDATE_CLIENTS);
        }
        be.lastOutput = pushEnergy(server, pos, be.energy, tierOutputRate(be.currentTier));
        if (server.getGameTime() % 10L == 0L) {
            server.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS); // WTHIT
        }
    }

    /** The 8 blocks around the controller in its own layer must all be Tokamak Coils. */
    public static boolean isRingFormed(final Level level, final BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!level.getBlockState(pos.offset(dx, 0, dz)).is(MagBlocks.TOKAMAK_COIL.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Push to neighbours; returns total FE moved this tick (the GUI's output readout). */
    private static int pushEnergy(final ServerLevel level, final BlockPos pos, final GenBuffer energy, final int outputRate) {
        if (energy.getEnergyStored() <= 0) return 0;
        int pushed = 0;
        for (final Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) break;
            final IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            final int offered = Math.min(outputRate - pushed, energy.getEnergyStored());
            final int accepted = target.receiveEnergy(offered, false);
            if (accepted > 0) { energy.extractEnergy(accepted, false); pushed += accepted; }
        }
        return pushed;
    }

    private static final class GenBuffer extends EnergyStorage {
        GenBuffer(final int capacity, final int maxExtract) {
            super(capacity, 0, maxExtract);
        }
        void generate(final int amount) {
            this.energy = Math.min(this.capacity, this.energy + amount);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Burn", burnTime);
        tag.putInt("Tier", currentTier);
        tag.putInt("LastOutput", lastOutput); // synced via getUpdateTag → WTHIT/GUI output readout
        tag.put("Fuel", fuelSlot.createTag(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.generate(tag.getInt("Energy"));
        burnTime = tag.getInt("Burn");
        currentTier = tag.getInt("Tier");
        lastOutput = tag.getInt("LastOutput");
        fuelSlot.fromTag(tag.getList("Fuel", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
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
}
