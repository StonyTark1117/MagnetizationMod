package com.stonytark.magnetization.mixin.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Keeps Coasters: Magnetized targets completely unresolved when the addon is absent. */
public final class CoastersMagnetizedMixinPlugin implements IMixinConfigPlugin {
    private boolean available;

    @Override
    public void onLoad(final String mixinPackage) {
        available = getClass().getClassLoader().getResource(
                "net/antopfr/coastersmagnetized/magnet/MagnetBoost.class") != null;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return available;
    }
    @Override public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(final String targetClassName, final ClassNode targetClass,
                                   final String mixinClassName, final IMixinInfo mixinInfo) {}
    @Override public void postApply(final String targetClassName, final ClassNode targetClass,
                                    final String mixinClassName, final IMixinInfo mixinInfo) {}
}
