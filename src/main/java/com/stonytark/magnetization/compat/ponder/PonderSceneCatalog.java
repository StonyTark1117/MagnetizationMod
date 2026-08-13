package com.stonytark.magnetization.compat.ponder;

import java.util.List;

/**
 * Registry-independent inventory of Magnetization's Ponder scenes.
 *
 * <p>Keeping target IDs and scene IDs outside the client plugin lets unit tests
 * compare the published Ponder surface with the shipping block resources. The
 * plugin consumes this catalog directly, so a catalog entry cannot silently
 * drift away from registration.
 */
public final class PonderSceneCatalog {

    private static final List<Scene> CORE_SCENES = List.of(
            custom("tokamak_ring", "Build a Tokamak ring", Kind.TOKAMAK,
                    "magnetization:tokamak_controller", "magnetization:tokamak_coil"),
            custom("fusion_panel", "Build a Fusion Thruster panel", Kind.FUSION_PANEL,
                    "magnetization:fusion_thruster"),
            custom("railgun_pair", "Build a paired Railgun", Kind.RAILGUN,
                    "magnetization:railgun_emitter"),
            generic("electrolyzer", "Run an Electrolyzer",
                    "Feed it water and FE; it produces hydrogen for the fusion-fuel chain.", true,
                    "magnetization:electrolyzer"),
            custom("gas_exciter", "Excite a connected gas volume", Kind.GAS_EXCITER,
                    "magnetization:gas_exciter"),
            custom("gas_vent", "Vent a compatibility gas", Kind.GAS_VENT,
                    "magnetization:gas_vent"),
            custom("air_separator", "Route an Air Separator", Kind.AIR_SEPARATOR,
                    "magnetization:air_separator"),
            generic("mhd_jet", "Fuel an MHD Jet",
                    "Install a magnet, point the jet with a wrench, then feed it FE and a conductive working fluid.",
                    true, "magnetization:mhd_jet"),
            generic("micro_thruster", "Fuel a Micro Thruster",
                    "Point the thruster with a wrench, fill its ferrofluid tank, and supply FE.", true,
                    "magnetization:micro_thruster"),
            custom("ion_thruster", "Choose an Ion Thruster propellant", Kind.ION_THRUSTER,
                    "magnetization:ion_thruster"),
            generic("solar_sail", "Use a Solar Sail",
                    "Mount panels on a ship and point them with a wrench; daylight and panel count drive thrust.",
                    false, "magnetization:solar_sail"),
            generic("kinetic_coil", "Use a Kinetic Coil",
                    "A passing magnetic ship induces FE and a redstone pulse in the coil.", false,
                    "magnetization:kinetic_coil"),
            generic("homopolar_motor", "Drive a Homopolar Motor",
                    "Install a magnet and connect the Create shaft; output scales with the installed magnet.", true,
                    "magnetization:homopolar_motor"),
            generic("structural_inducer", "Launch a Structure",
                    "Power the inducer, point it with a wrench, and set its scan range before launching the structure ahead.",
                    true, "magnetization:structural_inducer"),
            generic("dipole_electromagnet", "Aim a Dipole Electromagnet",
                    "Power it and use a wrench to aim the separated NORTH and SOUTH pole origins.", false,
                    "magnetization:dipole_electromagnet"),
            custom("rare_earth_magnets", "Refine rare-earth permanent magnets", Kind.RARE_EARTH,
                    "magnetization:samarium_cobalt_magnet", "magnetization:neodymium_magnet")
    );

    private static final List<Scene> OPTIONAL_SCENES = List.of(
            custom("steam_rails_magnetism", "Move coupled trains with magnetic fields", Kind.STEAM_RAILS,
                    "railways:track_coupler"),
            custom("copycat_magnetism", "Copy magnetic material properties", Kind.COPYCATS,
                    "copycats:copycat_block")
    );

    private PonderSceneCatalog() {}

    public static List<Scene> coreScenes() {
        return CORE_SCENES;
    }

    public static List<Scene> optionalScenes() {
        return OPTIONAL_SCENES;
    }

    public static List<Scene> allScenes() {
        return java.util.stream.Stream.concat(CORE_SCENES.stream(), OPTIONAL_SCENES.stream()).toList();
    }

    private static Scene generic(final String id, final String title, final String message,
                                 final boolean rightClickHint, final String... targets) {
        return new Scene(id, title, message, List.of(targets), Kind.GENERIC, rightClickHint);
    }

    private static Scene custom(final String id, final String title, final Kind kind, final String... targets) {
        return new Scene(id, title, "", List.of(targets), kind, false);
    }

    public enum Kind {
        GENERIC,
        TOKAMAK,
        FUSION_PANEL,
        RAILGUN,
        GAS_EXCITER,
        GAS_VENT,
        AIR_SEPARATOR,
        ION_THRUSTER,
        RARE_EARTH,
        STEAM_RAILS,
        COPYCATS
    }

    public record Scene(String id, String title, String message, List<String> targets,
                        Kind kind, boolean rightClickHint) {
        public Scene {
            if (id.isBlank() || title.isBlank() || targets.isEmpty()
                    || targets.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Ponder scene metadata must be complete");
            }
            targets = List.copyOf(targets);
        }
    }
}
