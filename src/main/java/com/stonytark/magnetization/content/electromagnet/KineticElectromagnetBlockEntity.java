package com.stonytark.magnetization.content.electromagnet;

import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.SableBridge;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Create-powered electromagnet. Strength tier ladders with absolute RPM:
 * {@code <16} → off, {@code <64} → WEAK, {@code <128} → MEDIUM, {@code >=128} → STRONG.
 * Stress impact is fixed at 4 — same scale as a Create press.
 */
public class KineticElectromagnetBlockEntity extends KineticBlockEntity
        implements MagneticFieldSource, BlockEntitySubLevelActor {

    private static final float STRESS_IMPACT = 4f;

    private @Nullable MagneticField cachedField = null;

    public KineticElectromagnetBlockEntity(
            final BlockEntityType<?> type, final BlockPos pos, final BlockState state
    ) {
        super(type, pos, state);
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = STRESS_IMPACT;
        return STRESS_IMPACT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!(level instanceof ServerLevel server)) return;
        emitField(server, null);
    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (!(level instanceof ServerLevel server)) return;
        emitField(server, subLevel);
    }

    private void emitField(final ServerLevel server, final @Nullable ServerSubLevel host) {
        final float rpm = Math.abs(getSpeed());
        if (rpm < 16f) {
            cachedField = null;
            return;
        }
        final MagneticStrength tier;
        if (rpm < 64f) tier = MagneticStrength.WEAK;
        else if (rpm < 128f) tier = MagneticStrength.MEDIUM;
        else tier = MagneticStrength.STRONG;

        MagneticPolarity polarity = MagneticPolarity.SOUTH;
        if (PolarityInverterBlock.shouldInvert(server, getBlockPos())) {
            polarity = polarity.opposite();
        }

        MagneticField field = new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                polarity,
                tier,
                MagneticField.Shape.OMNIDIRECTIONAL
        );

        if (host != null) {
            field = SableBridge.promoteToWorldSpace(host.logicalPose(), field);
        }

        cachedField = field;
        FieldApplicator.apply(server, field, host);
    }

    @Override
    public @Nullable MagneticField currentField() {
        return cachedField;
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.translatable("tooltip.magnetization.field_status")
                .withStyle(ChatFormatting.GRAY));
        tooltip.addAll(FieldTooltipFormatter.format(cachedField, true));
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) EmitterRegistry.register(level, getBlockPos());
    }

    @Override
    public void invalidate() {
        // Create's SmartBlockEntity finals setRemoved(); invalidate() is the hook
        // it calls during the same lifecycle moment.
        if (level != null) EmitterRegistry.unregister(level, getBlockPos());
        super.invalidate();
    }
}
