package com.stonytark.magnetization.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stonytark.magnetization.config.MagConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Datapack condition for compatibility recipes controlled by server-facing
 * COMMON config. Changing one of these values takes effect on the next data
 * reload, matching the lifecycle of recipes themselves. */
public record CompatConfigCondition(Feature feature) implements ICondition {

    public enum Feature {
        TFMG_COMPAT("tfmg_compat"),
        TFMG_PROCESSING("tfmg_processing"),
        TFMG_STEELMAKING("tfmg_steelmaking"),
        PATCHOULI("patchouli"),
        PATCHOULI_MANUAL_MAGNETITE("patchouli_manual_magnetite"),
        PATCHOULI_MANUAL_IRON("patchouli_manual_iron"),
        PATCHOULI_MANUAL_LODESTONE("patchouli_manual_lodestone"),
        CREATE_NEW_AGE("create_new_age"),
        COSMONAUTICS("cosmonautics"),
        IMMERSIVE_ENGINEERING("immersive_engineering"),
        ALEXSCAVES("alexscaves"),
        CREATE_ADDITION("create_addition"),
        PLANT_OIL_FERROFLUID("plant_oil_ferrofluid");

        private final String serializedName;

        Feature(final String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static Feature parse(final String name) {
            for (final Feature feature : values()) {
                if (feature.serializedName.equals(name)) return feature;
            }
            throw new IllegalArgumentException("Unknown Magnetization compatibility recipe feature: " + name);
        }
    }

    public static final MapCodec<CompatConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(Codec.STRING.fieldOf("feature")
                            .xmap(Feature::parse, Feature::serializedName)
                            .forGetter(CompatConfigCondition::feature))
                    .apply(instance, CompatConfigCondition::new));

    @Override
    public boolean test(final IContext context) {
        return switch (feature) {
            case TFMG_COMPAT -> MagConfig.tfmgCompatEnabled();
            case TFMG_PROCESSING -> MagConfig.tfmgProcessingRecipesEnabled();
            case TFMG_STEELMAKING -> MagConfig.tfmgSteelmakingRecipesEnabled();
            case PATCHOULI -> MagConfig.patchouliCompatEnabled();
            case PATCHOULI_MANUAL_MAGNETITE -> MagConfig.fieldManualMagnetiteRecipeEnabled();
            case PATCHOULI_MANUAL_IRON -> MagConfig.fieldManualIronRecipeEnabled();
            case PATCHOULI_MANUAL_LODESTONE -> MagConfig.fieldManualLodestoneRecipeEnabled();
            case CREATE_NEW_AGE -> MagConfig.createNewAgeRecipesEnabled();
            case COSMONAUTICS -> MagConfig.cosmonauticsRecipesEnabled();
            case IMMERSIVE_ENGINEERING -> MagConfig.immersiveEngineeringRecipesEnabled();
            case ALEXSCAVES -> MagConfig.alexsCavesRecipesEnabled();
            case CREATE_ADDITION -> MagConfig.createAdditionRecipesEnabled();
            case PLANT_OIL_FERROFLUID -> MagConfig.plantOilFerrofluidRecipeEnabled();
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
