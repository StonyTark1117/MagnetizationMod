package com.stonytark.magnetization.menu;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.electromagnet.ElectromagnetBlockEntity;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity;
import com.stonytark.magnetization.content.repulsor.RepulsorCoilBlockEntity;
import com.stonytark.magnetization.content.tractor.TractorBeamBlockEntity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Single capability-driven menu for every emitter that exposes a config GUI.
 *
 * <p>The {@code caps} bitmap controls which widgets the screen shows:
 * <ul>
 *   <li>{@link #CAP_ARMOR} — armor slot (Electromagnet / Kinetic Electromagnet)</li>
 *   <li>{@link #CAP_POLARITY} — NORTH/SOUTH/CLEAR buttons (operate on the armor stack
 *       in slot 0; absent on emitters without an armor slot)</li>
 *   <li>{@link #CAP_STRENGTH} — WEAK/MEDIUM/STRONG/EXTREME buttons (Electromagnet,
 *       Anchor, Repulsor, Tractor)</li>
 *   <li>{@link #CAP_RANGE} — range +/- buttons (same emitters as strength)</li>
 * </ul>
 *
 * <p>Polarity always pairs with the armor slot; strength always pairs with range.
 * Kinetic = ARMOR | POLARITY. Anchor/Repulsor/Tractor = STRENGTH | RANGE. Electromagnet
 * = ARMOR | POLARITY | STRENGTH | RANGE.
 *
 * <p>Server-side state (strength/range overrides) lives on the
 * {@link AbstractEmitterBlockEntity}; the menu mirrors it via {@link DataSlot}s.
 */
public class EmitterMenu extends AbstractContainerMenu {

    public static final int CAP_ARMOR     = 1;
    public static final int CAP_POLARITY  = 2;
    public static final int CAP_STRENGTH  = 4;
    public static final int CAP_RANGE     = 8;
    /** Excavator-only: a second persistent slot for an enchanted tool / book that
     *  injects its enchantments into the column's drop loot context (Fortune,
     *  Silk Touch). The slot is bound to the BE, so its contents survive close. */
    public static final int CAP_TOOL_SLOT = 16;

    // Button IDs sent through clickMenuButton(playerId, buttonId) on click.
    public static final int BUTTON_POLARITY_NORTH = 0;
    public static final int BUTTON_POLARITY_SOUTH = 1;
    public static final int BUTTON_POLARITY_CLEAR = 2;
    public static final int BUTTON_STRENGTH_WEAK    = 10;
    public static final int BUTTON_STRENGTH_MEDIUM  = 11;
    public static final int BUTTON_STRENGTH_STRONG  = 12;
    public static final int BUTTON_STRENGTH_EXTREME = 13;
    public static final int BUTTON_RANGE_DEC = 20;
    public static final int BUTTON_RANGE_INC = 21;

    /** Min and max for the range knob (in blocks). 0 means "use the strength tier's default". */
    public static final int RANGE_MIN = 0;
    public static final int RANGE_MAX = 64;
    public static final int RANGE_STEP = 2;

    /** Open-payload schema. Sent from the server when the player triggers the menu. */
    public record OpenPayload(BlockPos pos, int caps) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, OpenPayload::pos,
                        ByteBufCodecs.VAR_INT,  OpenPayload::caps,
                        OpenPayload::new);
    }

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final int caps;
    /** 1-slot transient container for the armor magnetize slot. Auto-returned to the
     *  player on close so the player never loses items if they walk away. */
    private final Container armorSlot;
    /** {@link MagneticStrength#ordinal()} of the BE's current strength override; -1 = default. */
    private final DataSlot strengthOrdinal = DataSlot.standalone();
    /** Current range override on the BE; 0 = default. */
    private final DataSlot rangeBlocks = DataSlot.standalone();

    /** Network constructor — invoked by IMenuTypeExtension.create on the client. */
    public static EmitterMenu fromNetwork(final int id, final Inventory inv,
                                          final RegistryFriendlyByteBuf buf) {
        final OpenPayload payload = OpenPayload.STREAM_CODEC.decode(buf);
        return new EmitterMenu(id, inv, ContainerLevelAccess.NULL, payload.pos(), payload.caps());
    }

    /** Server-side factory wraps a real {@link ContainerLevelAccess} for stillValid. */
    public EmitterMenu(final int id, final Inventory inv, final ContainerLevelAccess access,
                       final BlockPos pos, final int caps) {
        super(MagMenus.EMITTER.get(), id);
        this.access = access;
        this.pos = pos;
        this.caps = caps;
        this.armorSlot = new SimpleContainer(1) {
            @Override public boolean canPlaceItem(final int slot, final ItemStack stack) {
                // Accept armor (player magnetization) OR tools/weapons (item-attractor).
                return stack.is(MagTags.METAL_ARMOR) || stack.is(MagTags.METAL_TOOLS);
            }
            @Override public int getMaxStackSize() { return 1; }
        };

        // The armor slot at (80, 20) — only added when CAP_ARMOR is on. Even when the
        // server caps say "no armor", we still want a stable slot count, so we always
        // add a slot but make it permanently empty/locked when not allowed.
        addSlot(new ArmorMagnetizeSlot(this.armorSlot, 0, 80, 20, hasCap(CAP_ARMOR)));

        // Tool slot at (132, 20) — bound to the BE's persistent container on the
        // server, a transient client-side mirror on the client. Synced via the
        // standard slot-content network. Same locked-when-disabled trick as the
        // armor slot keeps slot indices stable across cap variants.
        final Container[] toolHolder = { new SimpleContainer(1) };
        access.execute((level, p) -> {
            final BlockEntity be = level.getBlockEntity(p);
            if (be instanceof com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity exc) {
                toolHolder[0] = exc.getToolSlot();
            }
        });
        addSlot(new ToolEnchantSlot(toolHolder[0], 0, 132, 20, hasCap(CAP_TOOL_SLOT)));

        // Player inventory rows (3) + hotbar (1). Slot indices 2..28 (main) and
        // 29..37 (hotbar) — the +2 offset accounts for armor + tool ahead.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }

        addDataSlot(strengthOrdinal);
        addDataSlot(rangeBlocks);
        // Initial sync from BE (server-side path only — client passes NULL access).
        // Uses `execute` (BiConsumer) rather than `evaluate` because the create-flavor
        // ContainerLevelAccess wraps the lambda return in Optional.of() — which NPEs
        // if the lambda returns null.
        access.execute((level, p) -> {
            final BlockEntity be = level.getBlockEntity(p);
            if (be instanceof AbstractEmitterBlockEntity emitter) {
                final MagneticStrength s = emitter.getStrengthOverride();
                strengthOrdinal.set(s == null ? -1 : s.ordinal());
                rangeBlocks.set(emitter.getRangeOverride());
            } else {
                strengthOrdinal.set(-1);
                rangeBlocks.set(0);
            }
        });
    }

    public int caps() { return caps; }
    public BlockPos pos() { return pos; }
    public boolean hasCap(final int cap) { return (caps & cap) != 0; }

    /** Current strength override ordinal as known by the menu. -1 = default. */
    public int strengthOrdinal() { return strengthOrdinal.get(); }
    /** Current range override in blocks as known by the menu. 0 = default. */
    public int rangeBlocks() { return rangeBlocks.get(); }
    /** Slot 0 access for the screen. */
    public ItemStack armorStack() { return armorSlot.getItem(0); }

    @Override
    public boolean stillValid(final Player player) {
        // Any block entity present at the configured pos counts as valid; this handles
        // both AbstractEmitterBlockEntity descendants and the Kinetic Electromagnet
        // (which extends Create's KineticBlockEntity instead). The strength/range
        // setters are no-ops for non-AbstractEmitter BEs, so the kinetic case is safe.
        return access.evaluate((level, p) -> {
            return level.getBlockEntity(p) != null
                    && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0;
        }, true);
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        return access.evaluate((level, p) -> {
            final BlockEntity be = level.getBlockEntity(p);
            switch (id) {
                case BUTTON_POLARITY_NORTH -> applyArmorPolarity(MagneticPolarity.NORTH);
                case BUTTON_POLARITY_SOUTH -> applyArmorPolarity(MagneticPolarity.SOUTH);
                case BUTTON_POLARITY_CLEAR -> applyArmorPolarity(null);
                case BUTTON_STRENGTH_WEAK    -> setStrengthIfAble(be, MagneticStrength.WEAK);
                case BUTTON_STRENGTH_MEDIUM  -> setStrengthIfAble(be, MagneticStrength.MEDIUM);
                case BUTTON_STRENGTH_STRONG  -> setStrengthIfAble(be, MagneticStrength.STRONG);
                case BUTTON_STRENGTH_EXTREME -> setStrengthIfAble(be, MagneticStrength.EXTREME);
                case BUTTON_RANGE_DEC -> bumpRange(be, -RANGE_STEP);
                case BUTTON_RANGE_INC -> bumpRange(be, +RANGE_STEP);
                default -> { return false; }
            }
            return true;
        }, false);
    }

    private void applyArmorPolarity(final @Nullable MagneticPolarity polarity) {
        if (!hasCap(CAP_POLARITY) || !hasCap(CAP_ARMOR)) return;
        final ItemStack stack = armorSlot.getItem(0);
        if (stack.isEmpty()) return;
        if (!stack.is(MagTags.METAL_ARMOR) && !stack.is(MagTags.METAL_TOOLS)) return;
        if (polarity == null) {
            stack.remove(MagDataComponents.ARMOR_POLARITY.get());
        } else {
            stack.set(MagDataComponents.ARMOR_POLARITY.get(), polarity);
        }
        armorSlot.setItem(0, stack);
        broadcastChanges();
    }

    private void setStrengthIfAble(final BlockEntity be, final MagneticStrength s) {
        if (!hasCap(CAP_STRENGTH)) return;
        if (be instanceof AbstractEmitterBlockEntity em) {
            // Refuse to set above the per-block ceiling defined in config.
            final MagneticStrength ceiling = strengthCeilingFor(em);
            if (s.ordinal() > ceiling.ordinal()) return;
            // Toggle off if clicking the currently-selected tier — lets the player
            // restore the emitter's default tier without leaving the menu.
            final MagneticStrength current = em.getStrengthOverride();
            em.setStrengthOverride(current == s ? null : s);
            strengthOrdinal.set(em.getStrengthOverride() == null ? -1 : em.getStrengthOverride().ordinal());
        }
    }

    private void bumpRange(final BlockEntity be, final int delta) {
        if (!hasCap(CAP_RANGE)) return;
        if (be instanceof AbstractEmitterBlockEntity em) {
            final int current = em.getRangeOverride();
            int next = current + delta;
            if (next < RANGE_MIN) next = RANGE_MIN;
            // Clamp to the per-block ceiling defined in config.
            final int ceiling = Math.min(RANGE_MAX, rangeCeilingFor(em));
            if (next > ceiling) next = ceiling;
            em.setRangeOverride(next);
            rangeBlocks.set(em.getRangeOverride());
        }
    }

    /** Per-block GUI ceiling for the strength tier, from {@link MagConfig}. */
    private static MagneticStrength strengthCeilingFor(final AbstractEmitterBlockEntity be) {
        try {
            if (be instanceof ElectromagnetBlockEntity)        return MagConfig.ELECTROMAGNET_MAX_STRENGTH.get();
            if (be instanceof MagneticAnchorBlockEntity)       return MagConfig.ANCHOR_MAX_STRENGTH.get();
            if (be instanceof RepulsorCoilBlockEntity)         return MagConfig.REPULSOR_MAX_STRENGTH.get();
            if (be instanceof TractorBeamBlockEntity)          return MagConfig.TRACTOR_MAX_STRENGTH.get();
            if (be instanceof MagneticExcavatorBlockEntity)    return MagConfig.EXCAVATOR_MAX_STRENGTH.get();
        } catch (final Throwable ignored) { /* config not loaded yet */ }
        return MagneticStrength.EXTREME;
    }

    /** Per-block GUI ceiling for the range, from {@link MagConfig}. */
    private static int rangeCeilingFor(final AbstractEmitterBlockEntity be) {
        try {
            if (be instanceof ElectromagnetBlockEntity)        return MagConfig.ELECTROMAGNET_MAX_RANGE.get();
            if (be instanceof MagneticAnchorBlockEntity)       return MagConfig.ANCHOR_MAX_RANGE.get();
            if (be instanceof RepulsorCoilBlockEntity)         return MagConfig.REPULSOR_MAX_RANGE.get();
            if (be instanceof TractorBeamBlockEntity)          return MagConfig.TRACTOR_MAX_RANGE.get();
            if (be instanceof MagneticExcavatorBlockEntity)    return MagConfig.EXCAVATOR_MAX_RANGE.get();
        } catch (final Throwable ignored) { /* config not loaded yet */ }
        return RANGE_MAX;
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);
        // Hand the armor slot's contents back to the player when the GUI closes,
        // so they never lose an item if they walk away mid-magnetize.
        if (!player.level().isClientSide) {
            access.execute((level, p) -> {
                final ItemStack remaining = armorSlot.removeItemNoUpdate(0);
                if (!remaining.isEmpty()) player.getInventory().placeItemBackInInventory(remaining);
            });
        }
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // 0 = armor slot, 1 = tool slot, 2..28 = inv main, 29..37 = hotbar.
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();
        if (index == 0 || index == 1) {
            // armor or tool slot → player inventory
            if (!moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // player inv → first matching slot. Try armor (if CAP_ARMOR + matching tag),
            // then tool (if CAP_TOOL_SLOT + has enchantments).
            boolean moved = false;
            if (hasCap(CAP_ARMOR)
                    && (original.is(MagTags.METAL_ARMOR) || original.is(MagTags.METAL_TOOLS))) {
                moved = moveItemStackTo(original, 0, 1, false);
            }
            if (!moved && hasCap(CAP_TOOL_SLOT)) {
                final var enchantments = original.getEnchantments();
                if (enchantments != null && !enchantments.isEmpty()) {
                    moved = moveItemStackTo(original, 1, 2, false);
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** Slot that rejects everything when CAP_ARMOR is off, and rejects items
     *  that aren't in either the metal_armor or metal_tools tag otherwise. */
    private static final class ArmorMagnetizeSlot extends Slot {
        private final boolean enabled;
        ArmorMagnetizeSlot(final Container c, final int s, final int x, final int y, final boolean enabled) {
            super(c, s, x, y);
            this.enabled = enabled;
        }
        @Override public boolean mayPlace(final ItemStack stack) {
            return enabled && (stack.is(MagTags.METAL_ARMOR) || stack.is(MagTags.METAL_TOOLS));
        }
        @Override public boolean isActive() { return enabled; }
        @Override public int getMaxStackSize() { return 1; }
    }

    /** Persistent slot for an enchanted item / book. Accepts any item carrying
     *  enchantments; the dropper logic ignores the item type and just reads
     *  what's stamped on it. Hidden + locked when CAP_TOOL_SLOT is off. */
    private static final class ToolEnchantSlot extends Slot {
        private final boolean enabled;
        ToolEnchantSlot(final Container c, final int s, final int x, final int y, final boolean enabled) {
            super(c, s, x, y);
            this.enabled = enabled;
        }
        @Override public boolean mayPlace(final ItemStack stack) {
            if (!enabled || stack.isEmpty()) return false;
            // Only meaningful with at least one enchantment — books with none
            // and bare tools waste the slot, so we filter them out.
            final var enchantments = stack.getEnchantments();
            return enchantments != null && !enchantments.isEmpty();
        }
        @Override public boolean isActive() { return enabled; }
        @Override public int getMaxStackSize() { return 1; }
    }
}
