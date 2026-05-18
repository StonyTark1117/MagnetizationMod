package com.stonytark.magnetization.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Empty-condition criterion trigger. Any code that wants to flag a moment in
 * the player's history calls {@link #trigger(ServerPlayer)} and any
 * advancement keyed off this trigger fires.
 *
 * <p>Multiple instances of this class are registered under distinct IDs in
 * {@link com.stonytark.magnetization.registry.MagTriggers} so each advancement
 * keys off its own moment without needing a separate Java class per trigger.
 */
public class MagSimpleTrigger extends SimpleCriterionTrigger<MagSimpleTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(final ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(b -> b.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
        ).apply(b, TriggerInstance::new));
    }
}
