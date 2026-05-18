package com.stonytark.magnetization.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Snapshot of an emitter's user-tunable configuration — the data the
 * Titanomagnetite Imprint Module stores. Captured from one emitter via
 * shift-right-click, projected onto another via right-click. Sourced from
 * the captured emitter's override values, so an emitter with no overrides
 * yet still captures its current effective state.
 *
 * <p>{@code sourceBlockId} records the block the snapshot was taken from
 * (e.g. {@code magnetization:electromagnet}) for the tooltip's "what type
 * of emitter this preset is for" line. The Imprint Module itself doesn't
 * restrict projection by type — players are free to copy an Electromagnet
 * preset onto a Tractor Beam if that's what they want — but the tooltip
 * surfaces the original so mismatches are intentional, not accidental.
 *
 * @param strength      effective strength tier at capture time
 * @param polarity      effective polarity at capture time
 * @param range         effective range (blocks) at capture time
 * @param sourceBlockId block id of the emitter the preset was captured from
 */
public record EmitterPreset(MagneticStrength strength,
                            MagneticPolarity polarity,
                            int range,
                            ResourceLocation sourceBlockId) {

    public static final Codec<EmitterPreset> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.STRING.xmap(MagneticStrength::valueOf, MagneticStrength::name)
                    .fieldOf("strength").forGetter(EmitterPreset::strength),
            MagneticPolarity.CODEC.fieldOf("polarity").forGetter(EmitterPreset::polarity),
            Codec.INT.fieldOf("range").forGetter(EmitterPreset::range),
            ResourceLocation.CODEC.fieldOf("source_block").forGetter(EmitterPreset::sourceBlockId)
    ).apply(b, EmitterPreset::new));

    public static final StreamCodec<ByteBuf, EmitterPreset> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.map(MagneticStrength::valueOf, MagneticStrength::name),
                    EmitterPreset::strength,
                    MagneticPolarity.STREAM_CODEC, EmitterPreset::polarity,
                    ByteBufCodecs.VAR_INT, EmitterPreset::range,
                    ResourceLocation.STREAM_CODEC, EmitterPreset::sourceBlockId,
                    EmitterPreset::new
            );
}
