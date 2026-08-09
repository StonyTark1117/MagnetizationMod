package com.stonytark.magnetization.content.dipole;

import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dipole Electromagnet — a placement-dependent, wrench-rotatable electromagnet
 * with BOTH poles at once: a NORTH pole projected from the {@code +FACING} end and
 * a SOUTH pole from the {@code -FACING} end. Otherwise it behaves like a normal
 * Electromagnet (redstone/FE powered, GUI strength + range tuning).
 *
 * <p>Each pole is an independent {@link MagneticField.Shape#OMNIDIRECTIONAL} field
 * whose {@code origin} is offset from the block centre along the facing axis by
 * {@link MagConfig#dipolePoleOffset()}. Offset origins (rather than one centred
 * field, or directional fields) are what make this a true dipole: omnidirectional
 * force is radial from each origin, so the two poles form a real spatial gradient
 * that reinforces instead of cancelling. Both poles emit at the same
 * {@link #effectiveStrength} / {@link #effectiveRange}, so the GUI tunes them
 * together.
 *
 * <p>The primary field is what {@link #currentField()} stores; the HUD expands it
 * into both ends. The opposite field is emitted via {@link #computeSecondaryField}. Both run
 * through the shared modifier pipeline, so an adjacent Polarity Inverter flips both
 * (the ends swap), and Hematite/Halbach/Lens act on both.
 */
public class DipoleElectromagnetBlockEntity extends AbstractEmitterBlockEntity {

    public DipoleElectromagnetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.DIPOLE_ELECTROMAGNET.get(), pos, state);
    }

    @Override
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        try { return MagConfig.ELECTROMAGNET_MAX_RANGE.get() / 2.0d; }
        catch (final Throwable t) { return tier.range(); }
    }

    /** The +FACING (NORTH) pole. Public so the gametest can assert on it. */
    public MagneticField northPoleField(final BlockState state) {
        return poleField(state, +1, MagneticPolarity.NORTH);
    }

    /** The -FACING (SOUTH) pole. Public so the gametest can assert on it. */
    public MagneticField southPoleField(final BlockState state) {
        return poleField(state, -1, MagneticPolarity.SOUTH);
    }

    /**
     * Build one pole's omnidirectional field. {@code sign} shifts the origin toward
     * the +facing ({@code +1}) or -facing ({@code -1}) end. Polarity is hardcoded per
     * pole (NOT routed through {@code effectivePolarity}) so the dipole can never
     * collapse to a single pole — the shared modifier pass still flips both when an
     * Inverter is adjacent, which is the intended symmetric behaviour.
     */
    private MagneticField poleField(final BlockState state, final int sign, final MagneticPolarity polarity) {
        final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING) : Direction.UP;
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        final Vec3 origin = Vec3.atCenterOf(getBlockPos()).add(axis.scale(sign * MagConfig.dipolePoleOffset()));
        final MagneticStrength strength = effectiveStrength(MagneticStrength.STRONG); // full strength per pole
        final double range = effectiveRange(strength);
        return new MagneticField(origin, axis, polarity, strength,
                MagneticField.Shape.OMNIDIRECTIONAL,
                range == strength.range() ? 0.0d : range,
                analogForceOverride());
    }

    @Override
    protected boolean analogRedstoneEnabled() {
        return MagConfig.analogRedstoneDipole();
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        return isPowered() ? northPoleField(state) : null;
    }

    @Override
    protected @Nullable MagneticField computeSecondaryField(final BlockState state) {
        return isPowered() ? southPoleField(state) : null;
    }

    @Override
    public List<Component> fieldTooltipLines(final boolean verbose) {
        return FieldTooltipFormatter.formatDipole(currentField(), verbose);
    }
}
