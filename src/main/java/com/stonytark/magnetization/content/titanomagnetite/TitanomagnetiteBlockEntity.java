package com.stonytark.magnetization.content.titanomagnetite;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Field-recording BE. Each {@link #computeField} pass scans the 6
 * axis-aligned neighbours for any {@link MagneticFieldSource} carrying a
 * non-null {@code currentField()}; the strongest such field is captured into
 * {@link #recordedField} and overwrites whatever was previously recorded.
 *
 * <p>When no neighbour is actively emitting, the BE keeps emitting the most
 * recently recorded field. The {@code MagneticField}'s origin is re-anchored
 * to this block's center each tick so the playback emits from the
 * titanomagnetite's position, not the source emitter's.
 *
 * <p>Persistence: {@link #recordedField} is serialised via
 * {@link MagneticField#toNbt()} so the imprint survives chunk unload, world
 * reload, and server restart — matching the "paleomagnetic record" theme.
 */
public final class TitanomagnetiteBlockEntity extends AbstractEmitterBlockEntity {

    private @Nullable MagneticField recordedField;

    public TitanomagnetiteBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TITANOMAGNETITE.get(), pos, state);
    }

    /** Titanomagnetite is a passive paleomagnetic record; emission is
     *  driven by the captured imprint, not by redstone or FE. Suppress
     *  the inherited power-source + energy-buffer tooltip lines. */
    @Override
    protected boolean acceptsPower() { return false; }

    /** Loot-table {@code minecraft:copy_components} reads this to attach the
     *  captured field's NBT to the dropped block-item. The receiving place
     *  picks it up via {@link #applyImplicitComponents} on the new BE so a
     *  player can pocket a charged titanomagnetite and redeploy it without
     *  losing the imprint. */
    @Override
    protected void collectImplicitComponents(final net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (recordedField != null) {
            builder.set(com.stonytark.magnetization.registry.MagDataComponents.RECORDED_FIELD.get(),
                    recordedField.toNbt());
        }
    }

    /** Inverse of {@link #collectImplicitComponents}. Called when the block is
     *  placed from an ItemStack carrying the RECORDED_FIELD component. */
    @Override
    protected void applyImplicitComponents(final BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        final net.minecraft.nbt.CompoundTag tag = input.get(
                com.stonytark.magnetization.registry.MagDataComponents.RECORDED_FIELD.get());
        if (tag != null) {
            recordedField = MagneticField.fromNbt(tag);
        }
    }

    /** Strip the recorded-field NBT from the BE's standard save tag — it's
     *  already covered by the RECORDED_FIELD data component when held as an
     *  item, and leaving it in the tag duplicates it on pick-block lookups. */
    @Override
    public void removeComponentsFromTag(final net.minecraft.nbt.CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("RecordedField");
    }

    /** Capture-scan radius. The original "6 axis neighbours" rule missed
     *  passive sources (Permanent Magnets) when tick ordering meant the
     *  neighbour hadn't computed its currentField yet, and missed setups
     *  where the player placed the titanomagnetite one or two blocks away
     *  from the source emitter. Walking the EmitterRegistry within this
     *  radius is more forgiving and matches the user's mental model
     *  ("first magnetic field it touches"). */
    private static final int CAPTURE_SCAN_RADIUS = 6;

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        final Level level = getLevel();
        if (level == null) return recordedField;
        final BlockPos pos = getBlockPos();
        final Vec3 hereCenter = Vec3.atCenterOf(pos);

        // Walk every registered emitter in this level within CAPTURE_SCAN_RADIUS
        // and pick the strongest non-null currentField(). The registry is
        // already maintained on BE load/unload, so we get every emitter type
        // (PermanentMagnet, Electromagnet, Repulsor, TractorBeam, Pyrrhotite,
        // MeteoriteCore, etc.) without per-type code here.
        final MagneticField[] strongestRef = new MagneticField[]{null};
        com.stonytark.magnetization.physics.EmitterRegistry.forEach(level, (lvl, srcPos) -> {
            if (srcPos.equals(pos)) return;
            if (srcPos.distSqr(pos) > (double) (CAPTURE_SCAN_RADIUS * CAPTURE_SCAN_RADIUS)) return;
            final BlockEntity be = lvl.getBlockEntity(srcPos);
            if (!(be instanceof MagneticFieldSource src) || be == this) return;
            final MagneticField nf = src.currentField();
            if (nf == null) return;
            // Range gate: only count emitters whose own range covers us. This
            // is the "field touches the titanomagnetite" check.
            if (Math.sqrt(srcPos.distSqr(pos)) > nf.range() + 1.0) return;
            if (strongestRef[0] == null
                    || nf.strength().ordinal() > strongestRef[0].strength().ordinal()) {
                strongestRef[0] = nf;
            }
        });
        MagneticField strongestNeighbour = strongestRef[0];

        // Fallback for tick-order races: if the registry walk found nothing,
        // re-scan the 6 axis neighbours by blockstate so a freshly-placed
        // Permanent Magnet that hasn't ticked yet still gets captured. The
        // PermanentMagnetBlock's POLARITY blockstate property carries all the
        // info we need; we synthesise a WEAK NORTH/SOUTH field from it.
        if (strongestNeighbour == null) {
            for (final Direction d : Direction.values()) {
                final BlockState neighbourState = level.getBlockState(pos.relative(d));
                if (!(neighbourState.getBlock() instanceof com.stonytark.magnetization.content.permanent.PermanentMagnetBlock)) continue;
                final com.stonytark.magnetization.api.MagneticPolarity pol =
                        neighbourState.getValue(com.stonytark.magnetization.content.permanent.PermanentMagnetBlock.POLARITY);
                strongestNeighbour = new MagneticField(
                        hereCenter,
                        new Vec3(0, 1, 0),
                        pol,
                        com.stonytark.magnetization.api.MagneticStrength.WEAK,
                        MagneticField.Shape.OMNIDIRECTIONAL);
                break;
            }
        }

        if (strongestNeighbour != null) {
            // Capture: copy the neighbour's field but re-anchor the origin to
            // this block so playback emits from our position when the source
            // is later removed.
            recordedField = new MagneticField(
                    hereCenter,
                    strongestNeighbour.axis(),
                    strongestNeighbour.polarity(),
                    strongestNeighbour.strength(),
                    strongestNeighbour.shape()
            );
            setChanged();
        }

        if (recordedField == null) return null;
        // Always emit at our own position regardless of where the field was
        // originally recorded (the saved field might still carry the source's
        // coordinates after a load).
        return new MagneticField(
                hereCenter,
                recordedField.axis(),
                recordedField.polarity(),
                recordedField.strength(),
                recordedField.shape()
        );
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> extraTooltipLines(final boolean verbose) {
        final java.util.List<net.minecraft.network.chat.Component> lines = super.extraTooltipLines(verbose);
        if (recordedField == null) {
            lines.add(net.minecraft.network.chat.Component.translatable(
                            "tooltip.magnetization.titanomagnetite.empty")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else {
            final net.minecraft.ChatFormatting polColor = recordedField.polarity() == com.stonytark.magnetization.api.MagneticPolarity.NORTH
                    ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.AQUA;
            lines.add(net.minecraft.network.chat.Component.translatable(
                            "tooltip.magnetization.titanomagnetite.recorded",
                            recordedField.strength().name(), recordedField.polarity().name())
                    .withStyle(polColor));
        }
        return lines;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (recordedField != null) tag.put("RecordedField", recordedField.toNbt());
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        recordedField = tag.contains("RecordedField")
                ? MagneticField.fromNbt(tag.getCompound("RecordedField"))
                : null;
    }
}
