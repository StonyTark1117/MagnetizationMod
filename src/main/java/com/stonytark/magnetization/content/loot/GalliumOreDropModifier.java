package com.stonytark.magnetization.content.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Adds a rare raw-gallium drop when mining a gallium-bearing ore
 * ({@link MagTags#GALLIUM_BEARING_ORES} — Create zinc ore, and TFMG bauxite when
 * present). Gallium occurs in nature as a trace byproduct of zinc/aluminium ores,
 * so this is the survival source of raw gallium → smelt → gallium ingot.
 */
public class GalliumOreDropModifier extends LootModifier {

    public static final MapCodec<GalliumOreDropModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst)
                    .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
                    .apply(inst, GalliumOreDropModifier::new));

    private final float chance;

    public GalliumOreDropModifier(final LootItemCondition[] conditions, final float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(final ObjectArrayList<ItemStack> loot, final LootContext context) {
        final BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state != null && state.is(MagTags.GALLIUM_BEARING_ORES) && context.getRandom().nextFloat() < chance) {
            loot.add(new ItemStack(MagItems.RAW_GALLIUM.get()));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
