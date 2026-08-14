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
            custom("tokamak_ring", "Build a solid-core Tokamak", Kind.TOKAMAK, List.of(
                            "Fill the ring interior with Reactor Cores. This 5x5 reactor uses 16 coils and a solid 3x3 interior of 9 cores.",
                            "The center core becomes the master. Other cores forward fuel and FE; perimeter coils expose the shared FE output. This size runs at 3x capacity, generation, and output.",
                            "Pipe water, heavy water, liquid Gallium, or tagged coolant into any core or perimeter coil. Higher-quality coolant boosts output and fuel life farther."),
                    "magnetization:tokamak_controller", "magnetization:tokamak_coil"),
            custom("fusion_panel", "Build a Fusion Thruster panel", Kind.FUSION_PANEL, List.of(
                            "A Fusion Thruster interior sits inside a one-block Tokamak-Coil frame.",
                            "Expand the interior into a solid rectangular panel; all interiors share one facing.",
                            "Feed water, liquid Gallium, or tagged coolant through any interior or frame coil. Heavy water uses a frame coil for cooling; interiors keep it as fuel."),
                    "magnetization:fusion_thruster"),
            custom("railgun_pair", "Build a paired Railgun", Kind.RAILGUN, List.of(
                            "Build two parallel rails with emitters facing the same direction.",
                            "Both rails must reach the minimum length before an arc can launch a target. The GUI can auto-assemble every block staged between them into a centered projectile ship."),
                    "magnetization:railgun_emitter"),
            generic("electrolyzer", "Run an Electrolyzer",
                    "Feed it water and FE; it produces hydrogen for the fusion-fuel chain.", true,
                    "magnetization:electrolyzer"),
            custom("gas_exciter", "Excite a connected gas volume", Kind.GAS_EXCITER, List.of(
                            "One powered Gas Exciter elects itself to energize the entire connected same-gas volume.",
                            "Supply FE and keep redstone off. Adjacent redstone can excite gas directly, but disables this machine."),
                    "magnetization:gas_exciter"),
            custom("gas_vent", "Vent a compatibility gas", Kind.GAS_VENT, List.of(
                            "Pipe exactly 1000 mB of a profiled addon gas into any face and leave the wrench-aimed outlet clear.",
                            "The source cloud keeps its fluid identity and is recoverable. An Exciter directly behind the vent can illuminate it."),
                    "magnetization:gas_vent"),
            custom("air_separator", "Route an Air Separator", Kind.AIR_SEPARATOR, List.of(
                            "Open the GUI, select Mechanical Input, then assign one horizontal face for Create rotation.",
                            "The default minimum is 64 RPM. Speed scales production up to the configured maximum.",
                            "Assign the five remaining faces to Helium, Neon, Argon, Krypton, and Xenon; each face drains an independent tank.",
                            "Installing an Isotope Separation Module adds slow renewable Helium-3 Crystal production."),
                    "magnetization:air_separator"),
            generic("mhd_jet", "Fuel an MHD Jet",
                    "Install a magnet, point the jet with a wrench, then feed it FE and a conductive working fluid.",
                    true, "magnetization:mhd_jet"),
            generic("micro_thruster", "Fuel a Micro Thruster",
                    "Point the thruster with a wrench, fill its ferrofluid tank, and supply FE.", true,
                    "magnetization:micro_thruster"),
            custom("ion_thruster", "Choose an Ion Thruster propellant", Kind.ION_THRUSTER, List.of(
                            "Helium favors efficiency and cruising speed; Xenon gives strong safe thrust; Radon is strongest but hazardous.",
                            "Mount it on a ship, aim the exhaust with a wrench, then supply FE and one accepted gas propellant."),
                    "magnetization:ion_thruster"),
            generic("solar_sail", "Use a Solar Sail",
                    "Mount panels on a ship and point them with a wrench; empty-hand right-click toggles each panel's night cutoff.",
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
            custom("rare_earth_magnets", "Refine rare-earth permanent magnets", Kind.RARE_EARTH, List.of(
                            "Bastnäsite, Monazite, Cobaltite, and Borax supply the two staged Create-processing branches.",
                            "Samarium-Cobalt is the heat-stable MEDIUM tier, refined from Monazite and Cobaltite.",
                            "Neodymium-Iron-Boron is the strongest machine magnet and also requires Dysprosium and Boron."),
                    "magnetization:samarium_cobalt_magnet", "magnetization:neodymium_magnet")
    );

    private static final List<Scene> OPTIONAL_SCENES = List.of(
            custom("steam_rails_magnetism", "Move coupled trains with magnetic fields", Kind.STEAM_RAILS, List.of(
                            "Steam 'n' Rails couplers share one train. A magnetic field accelerates or brakes the linked consist along its track.",
                            "Structural Inducers ignore assembled train entities; disassemble a train before treating its blocks as a structure."),
                    "railways:track_coupler"),
            custom("copycat_magnetism", "Copy magnetic material properties", Kind.COPYCATS, List.of(
                            "A Copycats+ block inherits magnetic susceptibility from its copied material, including after contraption assembly.",
                            "Create goggles report whether the stored material is ferromagnetic, diamagnetic, excluded, or nonmagnetic."),
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
        return new Scene(id, title, List.of(message), List.of(targets), Kind.GENERIC, rightClickHint);
    }

    private static Scene custom(final String id, final String title, final Kind kind,
                                final List<String> texts, final String... targets) {
        return new Scene(id, title, texts, List.of(targets), kind, false);
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

    public record Scene(String id, String title, List<String> texts, List<String> targets,
                        Kind kind, boolean rightClickHint) {
        public Scene {
            if (id.isBlank() || title.isBlank() || targets.isEmpty()
                    || targets.stream().anyMatch(String::isBlank) || texts.isEmpty()
                    || texts.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Ponder scene metadata must be complete");
            }
            if (kind == Kind.GENERIC && texts.size() != 1) {
                throw new IllegalArgumentException("Generic Ponder scenes require exactly one instruction");
            }
            texts = List.copyOf(texts);
            targets = List.copyOf(targets);
        }

        public String text(final int index) {
            return texts.get(index);
        }
    }
}
