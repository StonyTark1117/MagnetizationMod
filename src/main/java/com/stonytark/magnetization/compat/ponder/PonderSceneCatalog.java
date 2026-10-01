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
                            "Insert a Deuterium Cell in the master core. The gold marker represents shared FE output from a perimeter coil. This size runs at 3x capacity, generation, and output.",
                            "Blue piping illustrates water coolant input to a perimeter coil; any core also accepts coolant. Heavy water, liquid Gallium and tagged coolants can improve output and fuel life."),
                    "magnetization:tokamak_controller", "magnetization:tokamak_coil"),
            custom("fusion_panel", "Build a Fusion Thruster panel", Kind.FUSION_PANEL, List.of(
                            "This NORTH-facing Fusion Thruster stands in the X/Y plane, inside a one-block Tokamak-Coil frame. Exhaust points NORTH; reaction thrust points SOUTH.",
                            "Expand to this 5x3 panel: three interior thrusters and twelve frame coils. All interiors share one facing, perpendicular to the panel.",
                            "Feed water, liquid Gallium, or tagged coolant through any interior or frame coil. Heavy water uses a frame coil for cooling; interiors keep it as fuel."),
                    "magnetization:fusion_thruster"),
            custom("railgun_pair", "Build a paired Railgun", Kind.RAILGUN, List.of(
                            "Build two parallel rails with emitters facing the same direction.",
                            "Both rails must reach the minimum length before an arc can launch a target. The GUI auto-assembles eligible blocks strictly inside the rails and scan bounds; protected blocks can prevent the launch."),
                    "magnetization:railgun_emitter"),
            machine("electrolyzer", "Run an Electrolyzer",
                    "Feed it water and FE; it produces hydrogen for the fusion-fuel chain.", true,
                    "magnetization:electrolyzer"),
            custom("gas_exciter", "Excite a connected gas volume", Kind.GAS_EXCITER, List.of(
                            "Purple markers show connected gas; pink shows excitation. One powered Gas Exciter energizes connected same-gas cells within the configured scan cap and loaded chunks.",
                            "Supply FE and keep redstone off. Adjacent redstone can excite gas directly, but disables this machine."),
                    "magnetization:gas_exciter"),
            custom("gas_vent", "Vent a compatibility gas", Kind.GAS_VENT, List.of(
                            "Colored markers represent the gas cloud. Pipe exactly 1000 mB of a profiled addon gas into any face and leave the wrench-aimed outlet clear.",
                            "Use an empty bucket on the source cloud to recover its original fluid. Blue piping and the bucket controls illustrate input and recovery; glass is a cloud marker. The rear Exciter can illuminate it."),
                    "magnetization:gas_vent"),
            custom("air_separator", "Route an Air Separator", Kind.AIR_SEPARATOR, List.of(
                            "Open the GUI, select Mechanical Input, then assign one horizontal face for Create rotation.",
                            "The default minimum is 64 RPM. Speed scales production up to the configured maximum.",
                            "For this NORTH-facing example: UP Helium, WEST Neon, DOWN Argon, EAST Krypton, NORTH Xenon. Each face drains its own tank; configure assignments in the GUI.",
                            "Installing an Isotope Separation Module adds slow renewable Helium-3 Crystal production."),
                    "magnetization:air_separator"),
            machine("mhd_jet", "Fuel an MHD Jet",
                    "Install a magnet, point the jet with a wrench, then feed it FE and a conductive working fluid.",
                    true, "magnetization:mhd_jet"),
            machine("micro_thruster", "Fuel a Micro Thruster",
                    "Point the thruster with a wrench, fill its ferrofluid tank, and supply FE.", true,
                    "magnetization:micro_thruster"),
            custom("ion_thruster", "Choose an Ion Thruster propellant", Kind.ION_THRUSTER, List.of(
                            "White, purple, and green markers represent Helium, Xenon, and Radon. Helium favors efficiency; Xenon gives strong safe thrust; Radon is strongest but hazardous.",
                            "Piping feeds Xenon in this example; gold represents external FE. The moving hull illustrates reaction thrust NORTH, opposite the SOUTH-facing exhaust. Keep living entities away from Radon exhaust."),
                    "magnetization:ion_thruster"),
            machine("solar_sail", "Use a Solar Sail",
                    "Mount panels on a ship and point them with a wrench; empty-hand right-click toggles each panel's night cutoff.",
                    false, "magnetization:solar_sail"),
            machine("kinetic_coil", "Use a Kinetic Coil",
                    "A passing magnetic ship induces FE and a redstone pulse in the coil.", false,
                    "magnetization:kinetic_coil"),
            machine("homopolar_motor", "Drive a Homopolar Motor",
                    "Install a magnet and connect the Create shaft; output scales with the installed magnet.", true,
                    "magnetization:homopolar_motor"),
            custom("magnetic_shaft", "Transmit rotation through a magnetic field", Kind.MAGNETIC_SHAFT, List.of(
                    "Drive one shaft mechanically. Nearby receivers match its signed RPM and share the original stress capacity; they cannot retransmit.",
                    "Ferromagnetic, Samarium-Cobalt and Neodymium shafts reach 4, 8 and 16 blocks by default. Each range is server configurable; materials do not add power.",
                    "Conflicting speeds stop the receiver. Removing drive, leaving range or unloading the source breaks its link. A newly driven shaft can take over.",
                    "Goggles show material, range, source links, RPM, load and conflicts. Hold sneak and aim at a ship to inspect applied magnetic forces and force limits."),
                    "magnetization:magnetic_shaft", "magnetization:samarium_cobalt_magnetic_shaft", "magnetization:neodymium_magnetic_shaft"),
            custom("docking_signals", "Read a dock with redstone", Kind.GENERIC, List.of(
                    "Sneak-use an anchor with an empty hand, then sneak-use a switch to link it. Use the switch to choose present, settled, lost or distance output. Goggles explain the state."),
                    "magnetization:magnetic_switch", "magnetization:magnetic_anchor"),
            machine("structural_inducer", "Reel in a Structure",
                    "Power the inducer and set its scan range. With block FACING SOUTH, its capture cone points NORTH and reels structures SOUTH toward the inducer.",
                    true, "magnetization:structural_inducer"),
            machine("dipole_electromagnet", "Aim a Dipole Electromagnet",
                    "Power it and use a wrench to aim the separated NORTH and SOUTH pole origins.", false,
                    "magnetization:dipole_electromagnet"),
            custom("rare_earth_magnets", "Refine rare-earth permanent magnets", Kind.RARE_EARTH, List.of(
                            "Bastnäsite, Monazite, Cobaltite, and Borax supply the two staged Create-processing branches.",
                            "Controls show each processing stage. Mix and press branch ingredients, superheat the blanks, then craft with Lodestones and a matching plate. Samarium-Cobalt is MEDIUM.",
                            "The Neodymium branch also uses Dysprosium and Boron. Its controls show intermediates through sintering; the final Lodestone-and-plate recipe makes the strongest machine magnet."),
                    "magnetization:samarium_cobalt_magnet", "magnetization:neodymium_magnet")
    );

    private static final List<Scene> OPTIONAL_SCENES = List.of(
            custom("steam_rails_magnetism", "Move coupled trains with magnetic fields", Kind.STEAM_RAILS, List.of(
                            "The moving two-car model illustrates a linked Steam 'n' Rails consist on a track. Both cars share one train; magnetic acceleration or braking is applied once along its rail.",
                            "Structural Inducers ignore assembled train entities; disassemble a train before treating its blocks as a structure."),
                    "railways:track_coupler"),
            custom("copycat_magnetism", "Copy magnetic material properties", Kind.COPYCATS, List.of(
                            "Apply Iron to the Copycats+ block. Its stored Iron material supplies magnetic susceptibility, including after contraption assembly.",
                            "The iron-clad section moves as an assembled-material example. Create goggles classify the stored material as ferromagnetic, diamagnetic, excluded, or nonmagnetic."),
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

    private static Scene machine(final String id, final String title, final String message,
                                 final boolean rightClickHint, final String... targets) {
        final List<String> stages = switch (id) {
            case "electrolyzer" -> List.of("Blue input piping supplies water; the gold marker represents an FE supply. Keep the hydrogen output separate from the water inlet.", "The rising white marker illustrates produced Hydrogen. Route it to storage or the Deuterium Cell recipe.");
            case "mhd_jet" -> List.of("Install a Permanent Magnet in the magnet slot. Blue piping supplies liquid Gallium; the gold marker represents external FE.", "The moving hull illustrates reaction thrust opposite the wrench-aimed exhaust. Magnets are consumed while active only when magnet fuel consumption is enabled.");
            case "micro_thruster" -> List.of("The blue pipe feeds Ferrofluid; the gold marker represents external FE. Aim the exhaust away from the desired travel direction.", "The moving hull illustrates thrust opposite the exhaust. With an empty tank or no FE, the thruster idles.");
            case "solar_sail" -> List.of("The moving panel illustrates daylight thrust along its configured thrust direction. It needs a ship, not external FE or fluid.", "The dark sky marker illustrates night. Empty-hand right-click enables night cutoff; disabled cutoff continues the night behavior.");
            case "kinetic_coil" -> List.of("The iron hull illustrates a magnetic ship passing the fixed coil; motion relative to the coil induces energy.", "The lit lamp illustrates the redstone pulse. Extract generated FE from the coil; a stationary ship does not supply continuous induction.");
            case "homopolar_motor" -> List.of("Right-click with a Permanent Magnet to install it. The attached shaft rotates; the motor needs no FE supply.", "Swap in a stronger magnetic material for more RPM and stress capacity. Installed magnets burn while producing rotation only when magnet fuel consumption is enabled.");
            case "structural_inducer" -> List.of("Gold represents external FE. Stage an eligible structure in the capture cone, opposite block FACING; choose its range in the GUI.", "The moving section illustrates assembly and reeling toward the inducer. Protected blocks, scan limits, cooldown and excluded vehicles can prevent capture.");
            case "dipole_electromagnet" -> List.of("Blue and red markers show the separated NORTH and SOUTH pole origins along the aimed axis; gold represents external FE.", "The iron item illustrates a NORTH target: NORTH repels it and SOUTH attracts it. Reverse its pole and the responses reverse.");
            default -> throw new IllegalArgumentException("Missing machine demonstration: " + id);
        };
        return new Scene(id, title, List.of(message, stages.get(0), stages.get(1)), List.of(targets), Kind.MACHINE, rightClickHint);
    }

    private static Scene custom(final String id, final String title, final Kind kind,
                                final List<String> texts, final String... targets) {
        return new Scene(id, title, texts, List.of(targets), kind, false);
    }

    public enum Kind {
        MACHINE,
        GENERIC,
        MAGNETIC_SHAFT,
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
            if (kind == Kind.MACHINE && texts.size() != 3) {
                throw new IllegalArgumentException("Machine Ponder scenes require setup and two demonstration stages");
            }
            texts = List.copyOf(texts);
            targets = List.copyOf(targets);
        }

        public String text(final int index) {
            return texts.get(index);
        }
    }
}
