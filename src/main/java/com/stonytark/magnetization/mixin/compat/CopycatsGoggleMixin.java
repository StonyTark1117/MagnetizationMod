package com.stonytark.magnetization.mixin.compat;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.compat.copycats.MagCopycatsCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;

/** Adds material-derived magnetic status to Copycats+ goggles without making
 *  Copycats a hard dependency. */
@Pseudo
@Mixin(targets = {
        "com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity",
        "com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateCopycatBlockEntity"
}, remap = false)
public abstract class CopycatsGoggleMixin implements IHaveGoggleInformation {
    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean sneaking) {
        final var materials = MagCopycatsCompat.materialsOf((BlockEntity) (Object) this);
        if (materials.isEmpty()) return false;
        final boolean excluded = materials.stream()
                .anyMatch(s -> s.is(MagTags.MAGNETIC_SUSCEPTIBILITY_EXCLUDED));
        final boolean diamagnetic = !excluded && materials.stream()
                .anyMatch(s -> s.is(MagTags.DIAMAGNETIC_BLOCKS));
        final boolean ferromagnetic = !excluded && !diamagnetic && materials.stream()
                .anyMatch(s -> s.is(MagTags.FERROMAGNETIC_BLOCKS));
        final String key = excluded ? "excluded"
                : diamagnetic ? "diamagnetic"
                : ferromagnetic ? "ferromagnetic" : "nonmagnetic";
        tooltip.add(Component.translatable("magnetization.goggle.copycat_material." + key)
                .withStyle(ChatFormatting.GRAY));
        return true;
    }
}
