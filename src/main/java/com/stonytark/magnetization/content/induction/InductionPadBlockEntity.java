package com.stonytark.magnetization.content.induction;

import com.stonytark.magnetization.api.EquippedArmor;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.fml.ModList;

/**
 * Induction charging pad — buffers FE (received from cables/generators) and
 * wirelessly tops up every energy-storing item a nearby player carries: main
 * inventory, armor, offhand, and (when Curios is present) curio slots.
 * Electromagnetic induction, gamified.
 */
public class InductionPadBlockEntity extends BlockEntity {

    private final InternalBuffer energy = new InternalBuffer(
            com.stonytark.magnetization.config.MagConfig.inductionPadCapacity(),
            com.stonytark.magnetization.config.MagConfig.inductionPadTransferIn());

    public InductionPadBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.INDUCTION_PAD.get(), pos, state);
    }

    /** Exposed to {@code RegisterCapabilitiesEvent} so cables can push FE in. */
    public IEnergyStorage energyBuffer() {
        return energy;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final InductionPadBlockEntity be) {
        if (level.isClientSide) return;
        if (!com.stonytark.magnetization.config.MagConfig.inductionPadEnabled()) return; // master switch — pad inert when off
        final int interval = com.stonytark.magnetization.config.MagConfig.inductionPadInterval();
        if ((level.getGameTime() % interval) != 0L) return;
        int budget = Math.min(com.stonytark.magnetization.config.MagConfig.inductionPadChargePerTick() * interval, be.energy.getEnergyStored());
        if (budget <= 0) return;

        final AABB box = new AABB(pos).inflate(com.stonytark.magnetization.config.MagConfig.inductionPadRange());
        int spent = 0;
        for (final Player player : level.getEntitiesOfClass(Player.class, box)) {
            if (budget - spent <= 0) break;
            spent += chargePlayer(player, budget - spent);
        }
        if (spent > 0) be.energy.drainInternal(spent);
    }

    /** Charge a player's carried items; returns FE consumed. */
    private static int chargePlayer(final Player player, final int budget) {
        int spent = 0;
        // Main inventory + hotbar + offhand.
        for (final ItemStack stack : player.getInventory().items) {
            spent += chargeStack(stack, budget - spent);
            if (budget - spent <= 0) return spent;
        }
        spent += chargeStack(player.getOffhandItem(), budget - spent);
        if (budget - spent <= 0) return spent;
        // Worn armor (+ horse body etc. via the shared helper).
        for (final ItemStack armor : EquippedArmor.all(player)) {
            spent += chargeStack(armor, budget - spent);
            if (budget - spent <= 0) return spent;
        }
        // Curios slots — only when the mod is present.
        if (ModList.get().isLoaded("curios")) {
            spent += com.stonytark.magnetization.compat.curios.InductionCuriosCharging
                    .chargeCurios(player, budget - spent);
        }
        return spent;
    }

    private static int chargeStack(final ItemStack stack, final int budget) {
        if (stack.isEmpty() || budget <= 0) return 0;
        final IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (cap == null || !cap.canReceive()) return 0;
        return cap.receiveEnergy(budget, false);
    }

    private static final class InternalBuffer extends EnergyStorage {
        InternalBuffer(final int capacity, final int maxReceive) {
            super(capacity, maxReceive, 0);
        }
        void drainInternal(final int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.receiveEnergy(tag.getInt("Energy"), false);
    }
}
