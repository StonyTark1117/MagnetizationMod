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
            custom("pyrrhotite_heat", "Activate Pyrrhotite with heat", Kind.PYRRHOTITE_HEAT, List.of(
                    "Cold Pyrrhotite emits no field. Blue cells illustrate field strength; heat must touch the block or a catalyst that can reach it.",
                    "A smouldering or fading Blaze Burner gives WEAK strength. A lit campfire also gives WEAK.",
                    "A kindled Blaze Burner gives STRONG strength. Fire and Magma Blocks give the same tier.",
                    "A seething Blaze Burner gives EXTREME strength. Lava also gives EXTREME; heat levels do not add together.",
                    "The basic catalyst relays its adjacent heat to Pyrrhotite up to 3 blocks away. The blue line shows this reach.",
                    "An Enhanced Catalyst reaches 5 blocks. The heat source must still touch the catalyst.",
                    "A Cosmic Catalyst reaches 7 blocks. Range is measured independently along each axis, not along a cable.",
                    "Catalysts cannot chain. This heated basic catalyst is 6 blocks away; the unheated middle catalyst cannot extend its range.",
                    "The hottest source in range wins. Here the Enhanced Catalyst supplies EXTREME despite the weaker basic source.",
                    "Remove the heat sources and the field ends on the next heat scan. The server controls active and idle scan intervals."),
                    "magnetization:pyrrhotite_block", "magnetization:pyrrhotite_catalyst", "magnetization:enhanced_pyrrhotite_catalyst", "magnetization:cosmic_pyrrhotite_catalyst"),
            custom("gyrostabilizer", "Stop ship rotation, keep translation", Kind.GYROSTABILIZER, List.of(
                    "Mount the Gyrostabilizer aboard a ship. This moving section illustrates a ship translating and rotating while unpowered.",
                    "Power it with adjacent redstone. It cancels angular velocity while translation continues; it does not anchor the ship in place.",
                    "FE is an alternative: stabilization costs 20 FE per tick. Gold marks an external FE supply. Redstone power takes priority and uses no FE.",
                    "Remove both power sources and rotation is free again. A powered Gyrostabilizer on the ground has no ship to stabilize and spends no FE."),
                    "magnetization:gyrostabilizer"),
            custom("induction_pad", "Charge carried FE equipment", Kind.INDUCTION_PAD, List.of(
                    "Induction Pads require the server to enable inductionPadEnabled; it is off by default. This tutorial shows the enabled behavior.",
                    "Supply FE through a cable or generator. Gold marks the external supply; the pad stores energy for nearby equipment.",
                    "The gold token represents carried equipment that accepts FE. Green cells illustrate its charge increasing as the pad spends energy; ordinary tools do not charge.",
                    "Charging checks inventory, offhand and worn armor, plus enabled Curios slots. Stand within the configured area: by default 4 blocks beyond each pad face.",
                    "Leaving that area stops charging. An empty pad also cannot charge; transfer rate, scan interval and range are server configurable."),
                    "magnetization:induction_pad"),
            custom("railgun_remote", "Operate a Railgun Remote", Kind.RAILGUN_REMOTE, List.of(
                    "Build two matching parallel rails, then keep their breeches powered. This operating example uses four copper rail blocks per breech.",
                    "Open a breech GUI and place a Railgun Remote in its remote slot. The bound remote switches the pair to manual operation.",
                    "Take the bound remote out. The pair stays in manual mode and holds a captured payload instead of launching automatically.",
                    "Board the held ship with the remote in hand. Keep power supplied and wait for HOLDING before requesting a launch.",
                    "Right-click the bound remote to fire from the ship. Both rails launch the payload; the illustrated rider travels with it.",
                    "Sneak-use the remote to clear its binding. A reachable bound pair returns to automatic mode; if unreachable, only the carried remote is cleared."
            ), "magnetization:railgun_remote", "magnetization:railgun_emitter"),
            custom("imprint_module", "Copy an emitter preset", Kind.IMPRINT, List.of(
                    "An Imprint Module stores configured strength, polarity, and range. This source is EXTREME, SOUTH, and 64 blocks; temporary power throttling is not copied.",
                    "Sneak-right-click the source with an empty module to capture its preset. The filled module can be reused on other emitter types.",
                    "Sneak-right-click another emitter to apply. Example destination caps MEDIUM/16 clamp this EXTREME/64 preset to MEDIUM/16. Polarity stays SOUTH; the module retains its preset.",
                    "Right-click in air to clear the module, ready for a new capture. Clearing the item leaves the destination settings in place."
            ), "magnetization:imprint_module", "magnetization:electromagnet", "magnetization:repulsor_coil"),
            custom("tractor_beam", "Aim a Tractor Beam", Kind.TRACTOR, List.of(
                    "Aim the Tractor Beam with a wrench. Facing sets the directional field axis: point it into the space containing the ship.",
                    "Supply power. The default SOUTH field pulls an ordinary NORTH-polarity ship back toward the emitter, opposite the facing arrow.",
                    "Rotating the emitter rotates the field axis. Facing describes where the beam aims; the pull on this ship points back along that axis.",
                    "A neighboring Polarity Inverter reverses the field and pushes this ship away. Ship inverter parity can also reverse its response; movement here illustrates the force direction."
            ), "magnetization:tractor_beam", "magnetization:polarity_inverter"),
            custom("magnetic_basics", "Understand fields and ship polarity", Kind.MAGNETIC_BASICS, List.of(
                    "Blue marks a NORTH ship. A NORTH Permanent Magnet repels it; the moving hull illustrates the force direction.",
                    "Right-click the Permanent Magnet to flip it SOUTH. Opposite poles attract the NORTH ship toward the magnet.",
                    "Reset the magnet to NORTH. One face-adjacent Polarity Inverter flips its field to SOUTH.",
                    "A second adjacent inverter cancels the first. The fixed magnet now emits NORTH again.",
                    "One inverter anywhere aboard changes this ship to SOUTH. The NORTH world magnet attracts it.",
                    "A second onboard inverter restores NORTH. Ship polarity follows the parity of all onboard inverters.",
                    "Mounted magnets add susceptibility, not ship polarity. Flip the onboard magnet: the ship remains NORTH."),
                    "magnetization:permanent_magnet", "magnetization:electromagnet", "magnetization:kinetic_electromagnet", "magnetization:polarity_inverter"),
            custom("magnetic_excavator", "Operate the Excavator", Kind.EXCAVATOR, List.of(
                    "Place the Excavator in the world and wrench its active face toward the ore. This downward scan cone contains two ore blocks.",
                    "Open the GUI to set strength, scan range and concurrent pulls. These settings stay within the server limits.",
                    "Power it with external redstone or a redstone item in its internal slot. The slotted item is not consumed.",
                    "Each ore travels as its own moving block. The animation illustrates separate pulls; excavated terrain is consumed.",
                    "Arrival drops enter an adjacent inventory first. This barrel shows two Raw Iron drops; leftovers become dropped items.",
                    "Immune, protected or unbreakable blocks stop tunneling. Block entities are skipped by default."),
                    "magnetization:magnetic_excavator"),
            custom("repulsor_transport", "Convey and brake a magnetic ship", Kind.REPULSOR_TRANSPORT, List.of(
                    "Power the upward-facing world coils with redstone. Their cones repel an ordinary NORTH ship upward.",
                    "Right-click each coil with a Vector Core to install it. Open the GUI and choose EAST: conveyor thrust is perpendicular to UP.",
                    "The moving hull illustrates travel along the powered coils. Added conveyor thrust has a configurable speed limit.",
                    "Over the copper pad, eddy-current drag opposes motion. The slowing hull illustrates Lenz braking, not attraction."),
                    "magnetization:repulsor_coil", "magnetization:vector_core", "minecraft:copper_block"),
            custom("mr_fluid_bridge", "Build a switchable MR Fluid bridge", Kind.MR_FLUID_BRIDGE, List.of(
                    "Pour MR Fluid over a temporary floor between the banks. With no redstone or magnetic field, the source spreads into flowing cells.",
                    "Apply redstone to harden the fluid, then remove the temporary floor. The rigid cells form a walkable bridge and still conduct redstone.",
                    "Add a nearby magnet, then remove redstone. The magnetic field keeps the bridge rigid on its own.",
                    "Remove the magnet too. With both activations gone, original sources return and the fluid flows again. Flowing cells do not become extra sources."),
                    "magnetization:mr_fluid_bucket"),
            custom("field_strength_control", "Balance field strength", Kind.FIELD_STRENGTH_CONTROL, List.of(
                    "This Samarium-Cobalt magnet starts at MEDIUM. The examples use the default enabled Halbach bonus.",
                    "One face-adjacent magnet with the same pole adds one tier: MEDIUM becomes STRONG.",
                    "Two aligned neighbors still give only one bonus tier. The field remains STRONG.",
                    "Three aligned neighbors give two bonus tiers: EXTREME. Four through six give the same bonus; EXTREME is the ceiling.",
                    "Remove the aligned neighbors to return to the base MEDIUM field before adding dampeners.",
                    "One face-adjacent Hematite block lowers MEDIUM to WEAK. Each additional touching face lowers another tier.",
                    "A second Hematite block lowers WEAK to NONE: complete suppression. Dampeners must touch the emitter, not just each other."),
                    "magnetization:hematite_block", "magnetization:permanent_magnet"),
            custom("magnetizing_equipment", "Stamp equipment polarity", Kind.MAGNETIZING_EQUIPMENT, List.of(
                    "Open an Electromagnet and put metal equipment in its magnetizing slot. N, S and Clear act on that item, not on the emitter pole.",
                    "Choose N and equip the helmet. The NORTH magnet repels the NORTH-stamped wearer; an opposite SOUTH field attracts them.",
                    "Choose S and equip it again. The same NORTH field now attracts the SOUTH-stamped wearer. The arrows illustrate the response.",
                    "Choose Clear to remove the stamp. Metal armor still contributes susceptibility; without other stamps the wearer defaults to NORTH. Clear does not make metal nonmagnetic."),
                    "magnetization:electromagnet", "magnetization:kinetic_electromagnet"),
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
            custom("docking_signals", "Read a linked dock", Kind.DOCKING, List.of(
                    "Power a Magnetic Anchor to capture a nearby ship. It keeps that bound target; the moving hull here illustrates docking.",
                    "With an empty hand, sneak-click the anchor, then the switch. TARGET PRESENT outputs 15 while that bound ship is in range.",
                    "Select SETTLED with right-click. Distance, relative speed, and spin must stay within the dock limits for the stable dwell time.",
                    "After the stable dwell time, SETTLED outputs 15. Leaving tolerance or moving again clears the settled output.",
                    "ANALOG DISTANCE: nearer means stronger (0-15). Here 2 blocks in an 8-block range outputs 11. Direct redstone and comparators agree.",
                    "TARGET LOST outputs 15 after a previously seen bound ship leaves range or becomes unavailable. An anchor that has never seen a target does not trigger loss.",
                    "Two powered anchors bound to the same ship cooperate to damp its angular motion periodically. Both must capture that ship; powering off does not release a binding."
            ), "magnetization:magnetic_switch", "magnetization:magnetic_anchor"),
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
        TRACTOR,
        IMPRINT,
        RAILGUN_REMOTE,
        DOCKING,
        PYRRHOTITE_HEAT,
        GYROSTABILIZER,
        INDUCTION_PAD,
        MAGNETIC_BASICS,
        EXCAVATOR,
        REPULSOR_TRANSPORT,
        MR_FLUID_BRIDGE,
        FIELD_STRENGTH_CONTROL,
        MAGNETIZING_EQUIPMENT,
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
