package com.stonytark.magnetization.mixin.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Prevents optional TFMG mixins from resolving foreign targets when TFMG is absent. */
public final class TfmgMixinPlugin implements IMixinConfigPlugin {
    private static final String POLARIZER_RESOURCE =
            "com/drmangotea/tfmg/content/electricity/utilities/polarizer/PolarizerBlockEntity.class";
    private boolean tfmgAvailable;

    @Override
    public void onLoad(final String mixinPackage) {
        tfmgAvailable = TfmgMixinPlugin.class.getClassLoader().getResource(POLARIZER_RESOURCE) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return tfmgAvailable;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass,
                         final String mixinClassName, final IMixinInfo mixinInfo) {}

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass,
                          final String mixinClassName, final IMixinInfo mixinInfo) {}
}
