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
public final class EmitterMenu extends AbstractContainerMenu {

    public static final int CAP_ARMOR     = 1;
    public static final int CAP_POLARITY  = 2;
    public static final int CAP_STRENGTH  = 4;
    public static final int CAP_RANGE     = 8;
    /** Excavator-only: a second persistent slot for an enchanted tool / book that
     *  injects its enchantments into the column's drop loot context (Fortune,
     *  Silk Touch). The slot is bound to the BE, so its contents survive close. */
    public static final int CAP_TOOL_SLOT = 16;
    /** Excavator-only: per-emitter override for the concurrent-pull cap (how many
     *  in-flight ferromagnetic sub-levels the excavator may have at once). Capped
     *  upstream by {@link com.stonytark.magnetization.config.MagConfig#EXCAVATOR_MAX_IN_FLIGHT}. */
    public static final int CAP_INFLIGHT = 32;
    /** Excavator-only: an internal redstone-power slot. Any redstone dust in the
     *  slot keeps the excavator active — equivalent to an external redstone
     *  signal, but safe from being mined by its own pulls. Items aren't consumed. */
    public static final int CAP_REDSTONE_FUEL = 64;

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
    public static final int BUTTON_INFLIGHT_DEC = 30;
    public static final int BUTTON_INFLIGHT_INC = 31;

    /** Per-emitter in-flight cap floor / step. The upper bound comes from
     *  {@link com.stonytark.magnetization.config.MagConfig#EXCAVATOR_MAX_IN_FLIGHT}. */
    public static final int INFLIGHT_MIN = 1;
    public static final int INFLIGHT_STEP = 1;

    /** Min and absolute max for the range knob (in blocks). 0 means "use the emitter's
     *  built-in default" — for the excavator that's the deep-reach default; for ship
     *  emitters it falls back to the strength tier's nominal range. RANGE_MAX is the
     *  hard upper bound that no per-emitter config cap can exceed — pick something
     *  generous so the config keys (electromagnetMaxRange, excavatorMaxRange, ...) get
     *  to define the gameplay limit. 512 covers bedrock-from-build-limit pulls. */
    public static final int RANGE_MIN = 0;
    public static final int RANGE_MAX = 512;
    public static final int RANGE_STEP = 8;

    /** Extra vertical space added to the GUI when {@link #CAP_INFLIGHT} is set,
     *  so the "Pulls" row and its buttons don't collide with the inventory label.
     *  The player inventory slot positions are shifted down by this amount on the
     *  excavator's menu; the screen extends {@code imageHeight} by the same. */
    public static final int EXTRA_HEIGHT_FOR_INFLIGHT = 16;

    /** Y-offset to apply to player-inventory slots / inventory label based on caps.
     *  Shared between {@link EmitterMenu} (slot placement) and {@link
     *  com.stonytark.magnetization.client.screen.EmitterScreen} (recess rendering)
     *  so they stay aligned. */
    public static int inventoryYOffset(final int caps) {
        return (caps & CAP_INFLIGHT) != 0 ? EXTRA_HEIGHT_FOR_INFLIGHT : 0;
    }

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
    /** Excavator-only: 0..100 percent reflecting the closest active pulled
     *  ship's normalized progress toward the emitter, or 100 when nothing is
     *  in flight (ready). Driven from the BE's {@code getPullProgressPct()}
     *  on each tick via {@link #broadcastChanges()}. */
    private final DataSlot pullProgress = DataSlot.standalone();
    /** Excavator-only: current per-emitter in-flight cap. 0 = "follow the
     *  admin ceiling" ({@link com.stonytark.magnetization.config.MagConfig#EXCAVATOR_MAX_IN_FLIGHT}). */
    private final DataSlot inflightCap = DataSlot.standalone();
    /** Effective default range in blocks (admin-max / 2 for this emitter type).
     *  Shown on the GUI when no override is dialed in, so the player can see
     *  what the emitter is actually using before they touch the slider. */
    private final DataSlot defaultRange = DataSlot.standalone();
    /** Current FE buffer level on the BE — synced each tick from
     *  {@link AbstractEmitterBlockEntity#getEnergyBuffer()} so the GUI bar
     *  can show live drain/fill. */
    private final DataSlot energyStored = DataSlot.standalone();
    /** Max FE the buffer holds (config-driven). Almost-static — sent so the GUI
     *  can compute the fill fraction without hard-coding the config value. */
    private final DataSlot energyCapacity = DataSlot.standalone();
    /** 0 = idle, 1 = redstone-driven, 2 = energy-driven. Drives the
     *  "Source: …" label and the bar's tint colour. */
    private final DataSlot powerSource = DataSlot.standalone();

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
                // Accept armor (player magnetization) OR tools/weapons (item-attractor)
                // OR a ferrofluid bucket (magnetize the fluid → field-emitting when placed).
                return stack.is(MagTags.METAL_ARMOR) || stack.is(MagTags.METAL_TOOLS)
                        || stack.is(com.stonytark.magnetization.registry.MagItems.FERROFLUID_BUCKET.get())
                        || stack.is(com.stonytark.magnetization.registry.MagItems.MAGNETORESISTIVE_BOOTS.get());
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
        final Container[] fuelHolder = { new SimpleContainer(1) };
        access.execute((level, p) -> {
            final BlockEntity be = level.getBlockEntity(p);
            if (be instanceof com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity exc) {
                toolHolder[0] = exc.getToolSlot();
                fuelHolder[0] = exc.getRedstoneFuelSlot();
            }
        });
        addSlot(new ToolEnchantSlot(toolHolder[0], 0, 132, 20, hasCap(CAP_TOOL_SLOT)));
        // Redstone-fuel slot at (28, 20) — accepts only redstone dust. Internal
        // power source that's immune to the excavator destroying its own redstone.
        addSlot(new RedstoneFuelSlot(fuelHolder[0], 0, 28, 20, hasCap(CAP_REDSTONE_FUEL)));

        // Player inventory rows (3) + hotbar (1). Slot indices 3..29 (main) and
        // 30..38 (hotbar) — the +3 offset accounts for armor + tool + fuel ahead.
        // When CAP_INFLIGHT is set the whole inventory shifts down to make room
        // for the Pulls row above (otherwise the label collides with this title).
        final int invDy = inventoryYOffset(this.caps);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + invDy + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142 + invDy));
        }

        addDataSlot(strengthOrdinal);
        addDataSlot(rangeBlocks);
        addDataSlot(pullProgress);
        addDataSlot(inflightCap);
        addDataSlot(defaultRange);
        addDataSlot(energyStored);
        addDataSlot(energyCapacity);
        addDataSlot(powerSource);
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
                defaultRange.set((int) Math.round(
                        emitter.effectiveRange(emitter.effectiveStrength(MagneticStrength.STRONG))));
            } else {
                strengthOrdinal.set(-1);
                rangeBlocks.set(0);
                defaultRange.set(0);
            }
            if (be instanceof MagneticExcavatorBlockEntity exc) {
                inflightCap.set(exc.getInFlightCapOverride());
            } else {
                inflightCap.set(0);
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
    /** Effective default range in blocks when no override is dialed in.
     *  Half of the per-block admin ceiling; equals the value the screen should
     *  show on the label when {@link #rangeBlocks()} returns 0. */
    public int defaultRangeBlocks() { return defaultRange.get(); }
    /** Excavator-only: current pull progress, 0..100. */
    public int pullProgressPct() { return pullProgress.get(); }
    /** Excavator-only: current per-emitter concurrent-pull cap override. 0 = admin ceiling. */
    public int inflightCap() { return inflightCap.get(); }
    /** Excavator-only: admin ceiling for the in-flight cap, from config. */
    public int inflightCapMax() {
        try { return com.stonytark.magnetization.config.MagConfig.EXCAVATOR_MAX_IN_FLIGHT.get(); }
        catch (final Throwable t) { return 16; }
    }
    /** Slot 0 access for the screen. */
    public ItemStack armorStack() { return armorSlot.getItem(0); }

    @Override
    public void broadcastChanges() {
        // Refresh the live BE-driven DataSlots before vanilla broadcasts diffs.
        // The strength/range DataSlots are also kept in sync at button-click
        // time, but pullProgress only makes sense when sampled fresh each tick.
        if (hasCap(CAP_TOOL_SLOT)) {
            access.execute((level, p) -> {
                if (level.getBlockEntity(p) instanceof MagneticExcavatorBlockEntity exc) {
                    pullProgress.set(exc.getPullProgressPct());
                }
            });
        }
        // Energy + power-source snapshot every tick so the GUI's bar stays live.
        access.execute((level, p) -> {
            if (level.getBlockEntity(p) instanceof AbstractEmitterBlockEntity emitter) {
                energyStored.set(emitter.getEnergyBuffer().getEnergyStored());
                energyCapacity.set(emitter.getEnergyBuffer().getMaxEnergyStored());
                powerSource.set(emitter.isEnergyPowered() ? 2 : (emitter.isRedstonePowered() ? 1 : 0));
            }
        });
        super.broadcastChanges();
    }

    public int energyStored()   { return energyStored.get(); }
    public int energyCapacity() { return energyCapacity.get(); }
    public int powerSource()    { return powerSource.get(); }

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
                case BUTTON_RANGE_DEC -> bumpRange(be, -rangeStepFor(be));
                case BUTTON_RANGE_INC -> bumpRange(be, +rangeStepFor(be));
                case BUTTON_INFLIGHT_DEC -> bumpInflightCap(be, -INFLIGHT_STEP);
                case BUTTON_INFLIGHT_INC -> bumpInflightCap(be, +INFLIGHT_STEP);
                default -> { return false; }
            }
            return true;
        }, false);
    }

    private void applyArmorPolarity(final @Nullable MagneticPolarity polarity) {
        if (!hasCap(CAP_POLARITY) || !hasCap(CAP_ARMOR)) return;
        final ItemStack stack = armorSlot.getItem(0);
        if (stack.isEmpty()) return;
        if (!stack.is(MagTags.METAL_ARMOR) && !stack.is(MagTags.METAL_TOOLS)
                && !stack.is(com.stonytark.magnetization.registry.MagItems.FERROFLUID_BUCKET.get())
                && !stack.is(com.stonytark.magnetization.registry.MagItems.MAGNETORESISTIVE_BOOTS.get())) return;
        if (polarity == null) {
            stack.remove(MagDataComponents.ARMOR_POLARITY.get());
        } else {
            stack.set(MagDataComponents.ARMOR_POLARITY.get(), polarity);
        }
        // Electromagnet-GUI stamps are permanent — clear any prior LIRM marker so
        // the stamp doesn't decay out from under the player.
        stack.remove(MagDataComponents.LIRM_CREATED_AT.get());
        armorSlot.setItem(0, stack);
        broadcastChanges();
    }

    private void setStrengthIfAble(final BlockEntity be, final MagneticStrength s) {
        if (!hasCap(CAP_STRENGTH)) return;
        if (be instanceof AbstractEmitterBlockEntity em) {
            // Refuse to set above the per-block ceiling defined in config.
            final MagneticStrength ceiling = strengthCeilingFor(em);
            if (s.ordinal() > ceiling.ordinal()) return;
            // Setting the same tier twice is a no-op rather than a clear-override
            // toggle. The previous toggle behavior surprised users: a second click
            // dropped the override and the label fell back to the default (STRONG),
            // which looked like the button silently downgraded itself. Clicking
            // STRONG explicitly already produces the default state.
            em.setStrengthOverride(s);
            strengthOrdinal.set(s.ordinal());
        }
    }

    private void bumpRange(final BlockEntity be, final int delta) {
        if (!hasCap(CAP_RANGE)) return;
        if (be instanceof AbstractEmitterBlockEntity em) {
            // First touch: start from the current effective default (half of admin
            // ceiling) rather than 0, so +/- behaves predictably relative to what
            // the player sees on the label.
            int current = em.getRangeOverride();
            if (current <= 0) {
                current = (int) Math.round(em.effectiveRange(em.effectiveStrength(MagneticStrength.STRONG)));
            }
            int next = current + delta;
            if (next < RANGE_MIN) next = RANGE_MIN;
            // Clamp to the per-block ceiling defined in config.
            final int ceiling = Math.min(RANGE_MAX, rangeCeilingFor(em));
            if (next > ceiling) next = ceiling;
            em.setRangeOverride(next);
            rangeBlocks.set(em.getRangeOverride());
        }
    }

    private void bumpInflightCap(final BlockEntity be, final int delta) {
        if (!hasCap(CAP_INFLIGHT)) return;
        if (be instanceof MagneticExcavatorBlockEntity exc) {
            final int current = exc.getInFlightCapOverride();
            int next = current + delta;
            // 0 = "follow admin ceiling"; allow setting all the way down to that.
            if (next < 0) next = 0;
            final int ceiling = inflightCapMax();
            if (next > ceiling) next = ceiling;
            exc.setInFlightCapOverride(next);
            inflightCap.set(exc.getInFlightCapOverride());
        }
    }

    /** Per-block GUI ceiling for the strength tier, from {@link MagConfig}.
     *  Public so external tools (e.g. the Imprint Module item) can apply the
     *  same clamps the GUI applies. */
    public static MagneticStrength strengthCeilingFor(final AbstractEmitterBlockEntity be) {
        try {
            if (be instanceof ElectromagnetBlockEntity)        return MagConfig.ELECTROMAGNET_MAX_STRENGTH.get();
            if (be instanceof MagneticAnchorBlockEntity)       return MagConfig.ANCHOR_MAX_STRENGTH.get();
            if (be instanceof RepulsorCoilBlockEntity)         return MagConfig.REPULSOR_MAX_STRENGTH.get();
            if (be instanceof TractorBeamBlockEntity)          return MagConfig.TRACTOR_MAX_STRENGTH.get();
            if (be instanceof MagneticExcavatorBlockEntity)    return MagConfig.EXCAVATOR_MAX_STRENGTH.get();
        } catch (final Throwable ignored) { /* config not loaded yet */ }
        return MagneticStrength.EXTREME;
    }

    /** Per-emitter range step for the +/- buttons. The Repulsor Coil's tuning
     *  band is much narrower than the long-range emitters (its default is 8
     *  blocks total, not 128), so an 8-block step would jump past most of its
     *  useful range in one click. 1-block step gives the player finger-fine
     *  control. */
    private static int rangeStepFor(final @Nullable BlockEntity be) {
        if (be instanceof RepulsorCoilBlockEntity) return 1;
        return RANGE_STEP;
    }

    /** Per-block GUI ceiling for the range, from {@link MagConfig}.
     *  Public so external tools (e.g. the Imprint Module item) can apply the
     *  same clamps the GUI applies. */
    public static int rangeCeilingFor(final AbstractEmitterBlockEntity be) {
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
        // 0 = armor, 1 = tool, 2 = redstone fuel, 3..29 = inv main, 30..38 = hotbar.
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();
        if (index <= 2) {
            // armor / tool / fuel → player inventory
            if (!moveItemStackTo(original, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // player inv → first matching slot.
            boolean moved = false;
            if (hasCap(CAP_ARMOR)
                    && (original.is(MagTags.METAL_ARMOR) || original.is(MagTags.METAL_TOOLS))) {
                moved = moveItemStackTo(original, 0, 1, false);
            }
            if (!moved && hasCap(CAP_TOOL_SLOT)) {
                final var active = original.getOrDefault(
                        net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
                final var stored = original.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
                final boolean hasAny = !active.isEmpty()
                        || (stored != null && !stored.isEmpty());
                if (hasAny) moved = moveItemStackTo(original, 1, 2, false);
            }
            if (!moved && hasCap(CAP_REDSTONE_FUEL)
                    && original.is(MagTags.REDSTONE_FUEL)) {
                moved = moveItemStackTo(original, 2, 3, false);
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
            return enabled && (stack.is(MagTags.METAL_ARMOR) || stack.is(MagTags.METAL_TOOLS)
                    || stack.is(com.stonytark.magnetization.registry.MagItems.FERROFLUID_BUCKET.get())
                        || stack.is(com.stonytark.magnetization.registry.MagItems.MAGNETORESISTIVE_BOOTS.get()));
        }
        @Override public boolean isActive() { return enabled; }
        @Override public int getMaxStackSize() { return 1; }
    }

    /** Persistent slot for internal redstone power. Accepts any item tagged
     *  {@link MagTags#REDSTONE_FUEL} — dust, blocks, torches, levers, plates,
     *  observers, and anything datapacks add. Presence-based: any amount in
     *  the slot keeps the excavator powered, and items are never consumed —
     *  pull it back out anytime to switch off. */
    private static final class RedstoneFuelSlot extends Slot {
        private final boolean enabled;
        RedstoneFuelSlot(final Container c, final int s, final int x, final int y, final boolean enabled) {
            super(c, s, x, y);
            this.enabled = enabled;
        }
        @Override public boolean mayPlace(final ItemStack stack) {
            return enabled && stack.is(MagTags.REDSTONE_FUEL);
        }
        @Override public boolean isActive() { return enabled; }
    }

    /** Persistent slot for an enchanted item / book. Accepts any item carrying
     *  enchantments — either active (regular tools) or stored (enchanted books).
     *  The dropper logic merges both into the loot context's TOOL parameter.
     *  Hidden + locked when CAP_TOOL_SLOT is off. */
    private static final class ToolEnchantSlot extends Slot {
        private final boolean enabled;
        ToolEnchantSlot(final Container c, final int s, final int x, final int y, final boolean enabled) {
            super(c, s, x, y);
            this.enabled = enabled;
        }
        @Override public boolean mayPlace(final ItemStack stack) {
            if (!enabled || stack.isEmpty()) return false;
            // Active enchantments (tools) OR stored enchantments (books). Books
            // store theirs in DataComponents.STORED_ENCHANTMENTS — the
            // ENCHANTMENTS component is empty on a book, so we have to consult
            // both.
            final var active = stack.getOrDefault(
                    net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            if (!active.isEmpty()) return true;
            final var stored = stack.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
            return stored != null && !stored.isEmpty();
        }
        @Override public boolean isActive() { return enabled; }
        @Override public int getMaxStackSize() { return 1; }
    }
}
