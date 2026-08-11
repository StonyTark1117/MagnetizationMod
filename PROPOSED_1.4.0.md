Proposed Plan

# Rare-Earth Magnet Progression: Samarium–Cobalt and Neodymium

## Magnet Progression

Add Samarium–Cobalt as a complete intermediate rare-earth magnet tier:

1. Existing natural minerals and crafted magnets.

2. Titanomagnetite.

3. Samarium–Cobalt.

4. Neodymium–Iron–Boron.

Samarium–Cobalt should be stronger than every existing magnet except Neodymium. It should be more expensive and more heat-resistant than  
Titanomagnetite, while remaining weaker than the final NdFeB magnet.

Do not add another natural magnetic mineral at this time. The existing Magnetite, Maghemite, Pyrrhotite, Hematite, and Titanomagnetite families already  
cover naturally magnetic materials. Samarium–Cobalt and NdFeB should be manufactured magnets rather than naturally spawning magnets.

## Samarium–Cobalt Resource Chain

Samarium and Cobalt should also be refined rather than spawning as finished metals.

Add naturally occurring precursor resources:

- Monazite:

  - Rare phosphate mineral and source of Samarium-bearing concentrate.

  - Can share some rare-earth processing infrastructure with Bastnäsite and Xenotime.

  - May produce a small radioactive waste byproduct to fit the mod’s existing radiation/gas systems if that can be represented cleanly.

- Cobaltite:

  - Natural cobalt-bearing mineral.

  - Produces Cobalt Concentrate and eventually Cobalt Powder or Cobalt Ingot.

  - Cobaltite is a resource, not a magnetic equipment tier by itself.

The Samarium–Cobalt route should be:

1. Crush or mill Monazite and Cobaltite.

2. Wash/haunt the crushed minerals with Create Encased Fans.

3. Produce Samarium-bearing Concentrate and Cobalt Concentrate.

4. Heat-process the Samarium concentrate into Samarium Oxide.

5. Reduce Samarium Oxide using the existing Hydrogen processing route.

6. Process Cobalt Concentrate into Cobalt Powder or Cobalt Ingot.

7. Heat-mix Samarium, Cobalt, and a small iron/ferromagnetic component into Samarium–Cobalt Alloy.

8. Press the alloy into ingots, plates, and magnet blanks.

9. Sinter and magnetize the blanks into Samarium–Cobalt Magnets.

Use a simplified SmCo alloy for the base recipe while leaving room for advanced grades later. The production logic should reflect that Samarium–Cobalt  
magnets are rare-earth alloys, not raw Samarium blocks.

## Samarium–Cobalt Equipment

Add a complete Samarium–Cobalt equipment family:

- Samarium–Cobalt Alloy Ingot.

- Samarium–Cobalt Alloy Block.

- Samarium–Cobalt Plate.

- Sword, pickaxe, axe, shovel, and hoe.

- Helmet, chestplate, leggings, and boots.

- Samarium–Cobalt Magnet.

Equipment should rank above Titanomagnetite and below NdFeB equipment.

Special property: Thermal Stability.

- Samarium–Cobalt tools have high durability and maintain their performance in hot environments.

- Samarium–Cobalt armor provides meaningful fire/lava damage reduction.

- A complete set may provide a stronger thermal-protection bonus, but should not grant unconditional Fire Resistance unless testing shows it is  
appropriate for the mod’s endgame.

- Samarium–Cobalt remains ferromagnetic and interacts with the mod’s magnetic fields.

- It should not provide Neodymium’s magnetic harvesting or full-set Magnetic Anchoring bonuses.

This creates a clear identity:

- Samarium–Cobalt: heat-resistant aerospace/industrial material.

- NdFeB: maximum magnetic strength and advanced magnetic equipment.

## Aerospace and Create Integration

Samarium–Cobalt should have optional high-temperature and aerospace integrations because of its resistance to demagnetization and use in aerospace  
systems.

Base functionality:

- SmCo magnets work in Magnetization’s Homopolar Motor, MHD Jet, and other magnet-slot machines.

- Their machine potency is above Titanomagnetite and below Neodymium.

- Their burn duration and machine performance should remain stable under high-temperature processing.

Create: Aeronautics integration:

- Add SmCo Alloy or SmCo Magnets as optional recipe ingredients for high-tier aircraft, propulsion, guidance, gyroscopic, or electromagnetic components  
where the existing recipe APIs support it.

- Prefer SmCo over NdFeB for components that represent high-temperature engines or aerospace machinery.

- Keep the integration recipe-based rather than changing Aeronautics behavior directly.

Create: Cosmonautics-style integration:

- If Create: Cosmonautics or a similar spaceflight mod is installed, add conditional recipes using Samarium–Cobalt Magnets for spacecraft motors,  
guidance systems, high-temperature propulsion components, or other advanced aerospace machinery.

- These recipes must be guarded by mod\_loaded and the existing compatibility configuration system.

- The integration must never become a hard dependency.

- If the target mod does not expose stable recipe hooks or identifiable aerospace components, provide only tag-based material compatibility rather than  
fragile mixins.

TFMG and Immersive Engineering:

- TFMG may provide industrial blasting, cobalt/steel processing, high-temperature alloying, and electrical alternatives.

- Immersive Engineering may provide crusher, arc furnace, and metal press alternatives.

- Both remain optional processing accelerators.

## Neodymium Relationship

Keep the existing NdFeB plan, with the following changes:

- Neodymium remains the strongest magnet and equipment tier.

- NdFeB equipment is made from Neodymium–Iron–Boron Alloy, not pure Neodymium.

- Dysprosium remains a scarce additive required for the highest-grade NdFeB magnet and equipment.

- Samarium–Cobalt does not replace Dysprosium or provide an alternate route to NdFeB.

- Alex’s Caves Azure and Scarlet Neodymium remain optional inputs to the Neodymium branch.

- Samarium–Cobalt and NdFeB have separate alloy-processing chains, but share Create processing infrastructure and rare-earth tags where appropriate.

## Potency Targets

Extend MagneticMaterials with separate material families:

- Titanomagnetite: current strongest native mineral.

- Samarium–Cobalt Alloy: above Titanomagnetite.

- NdFeB Alloy: above Samarium–Cobalt.

- Finished Neodymium Magnet: highest special potency.

Initial target:

- SmCo Alloy: approximately 1.2–1.4× Titanomagnetite.

- Finished SmCo Magnet: approximately 1.3–1.5× Titanomagnetite.

- NdFeB Alloy equipment: approximately 1.5–1.8× Titanomagnetite.

- Finished NdFeB Magnet: approximately 1.75–2× Titanomagnetite.

These values should be tuned against motor RPM, stress capacity, MHD thrust, FE draw, and magnet burn duration. Keep the existing  
MagneticStrength.EXTREME emitter tier unchanged.

## Required Data and Interfaces

- Monazite and Cobaltite ore blocks, worldgen, loot, and configuration.

- Samarium and Cobalt concentrate, oxide, powder, and ingot forms.

- Samarium–Cobalt Alloy ingots, plates, blocks, tools, armor, and magnets.

- Existing Neodymium/Bastnäsite/Xenotime/Borax/NdFeB content from the prior plan.

- Separate tool tiers and armor materials for SmCo and NdFeB.

- Thermal-damage behavior for SmCo armor.

- Magnetic harvesting behavior for NdFeB tools.

- Magnetic Anchoring behavior for full NdFeB armor.

- Create processing recipes and optional TFMG/Immersive Engineering alternatives.

- Optional Aeronautics and Cosmonautics recipe compatibility.

- Common tags for Samarium, Cobalt, SmCo Alloy, rare-earth concentrates, oxides, powders, plates, ingots, storage blocks, and machine magnets.

- Models, textures, language, creative-tab entries, loot, worldgen, recipes, and GameTests.

## Test Plan

- Monazite and Cobaltite generate as precursor resources only.

- Samarium and Cobalt do not spawn as finished metals.

- SmCo processing works with Create and Magnetization alone.

- SmCo equipment ranks above Titanomagnetite and below NdFeB.

- SmCo armor reduces fire/lava damage without unintentionally granting full immunity.

- SmCo magnets function in magnet-slot machines and remain below NdFeB potency.

- NdFeB remains the strongest magnet and equipment tier.

- Alex’s Caves Neodymium compatibility remains optional and functional.

- TFMG, Immersive Engineering, Aeronautics, and Cosmonautics compatibility recipes load only when their mods are present.

- Optional compatibility absence tests confirm no hard dependencies were introduced.

- Existing magnet tiers and recipes remain functional.

- Existing unrelated Air Separator worktree changes are preserved.

## Assumptions

- Samarium–Cobalt is added as a full equipment and magnet tier.

- Monazite and Cobaltite are natural precursors; Samarium and Cobalt are refined products.

- Samarium–Cobalt is weaker than Neodymium but stronger than Titanomagnetite.

- SmCo’s defining gameplay identity is heat resistance and aerospace compatibility.

- NdFeB’s defining gameplay identity is maximum magnetic strength and advanced magnetic interaction.

- No new machine is required for either rare-earth branch.

- Create: Aeronautics and any Cosmonautics-style mod integrations remain optional recipe/tag compatibility, not hard dependencies.


