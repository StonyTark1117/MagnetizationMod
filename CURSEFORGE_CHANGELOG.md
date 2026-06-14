
# TLDR: Most magnets have guis now and theres a bunch more magnet types, along with overhauls to the optional biomes, and compatibility with a bunch more mods.
#

---

# What's new since 1.0.1

## 1.1.3 — Tag-cycle crash defense + emitter GUI fix

### Stability
- **Defensive mixin against `DependencySorter.isCyclic` StackOverflow at datapack load.** Vanilla's tag-cycle check recurses through the dependency multimap without a visited-set, so a self-referencing tag in any upstream mod can blow the JVM stack and prevent world creation. The mixin adds memoization so re-entering an already-walked node short-circuits to false; real cycles are still caught, vanilla just gracefully logs the broken tag instead of crashing. This protects every cross-mod tag reference in our datapack — and every other mod's references — from this class of upstream bug.
- Found via `spartan_weaponry_unofficial`, whose `data/c/tags/item/ingots/aluminum.json` lists `#c:ingots/aluminum` as a child of itself. With `accessories` + `accessories_compat_layer` also installed, any mod referencing `#c:ingots/aluminum` (Magnetization being one) crashed at world create. World creation now succeeds; vanilla logs `Couldn't load tag c:ingots/aluminum` and continues.

### Fixes
- **Emitter strength buttons no longer toggle.** Clicking the currently-selected tier a second time used to clear the override and fall back to the default (STRONG), which read as "the button silently downgraded itself" — most visible when clicking EXTREME twice. Buttons are now idempotent: each click sets that tier and stays. Clicking STRONG explicitly produces the default state if you want it.

---

## 1.1.2 — Anomaly biome overhaul + new ores

### New ores
- **Maghemite, Pyrrhotite, Hematite, Titanomagnetite** — four new ferrous ore families with stone + deepslate variants. Each one feeds a unique block-tier emitter (see below).
- **Magnetite** itself slowly oxidises to Maghemite over time when exposed to air. Off by default — turn it on in config if you want the decay loop.

### New themed blocks
- **Anomaly Stone family** — smooth and cobbled, plus stairs / slabs / walls / stonecutter shortcuts.
- **Magnetic Gravel** — falls under gravity like vanilla gravel, with a small chance to drop raw magnetite or maghemite instead of flint.
- A sparse patch of magnetic gravel now generates rarely in normal overworld biomes so survival players who never reach an Anomaly biome can still find a small supply.

### Anomaly biome rebuilt
- New themed surface — Anomaly Stone bulk, dense iron-oxide ore veining, magnetic gravel patches, rare raw-magnetite outcrops. (Workaround for an upstream surface-rule conflict between TerraBlender and Citadel; non-Citadel installs are unaffected.)
- Properly hard to stumble into on a fresh world again — biome weight reduced to TerraBlender's minimum on top of the existing extremely-rare rarity.

### New block-tier emitters
- **Pyrrhotite Block** — heat-activated. Inert on its own; broadcasts a NORTH field whose strength rises with the heat source applied to it.
- **Hematite Lens** — multiblock polarity filter. Caps an emitter and only lets one polarity through.
- **Titanomagnetite Block + Imprint Module** — the block records a nearby emitter's full config; the hand-held Imprint Module captures the recording and projects it onto another emitter elsewhere. No manual re-tuning.

### Meteorite content
- **Meteorite Cores** — rare fallen-meteor structures with a decaying magnetic field at the centre. Feed any ferromagnetic item to reset the timer and turn one into a permanent power source.
- **Meteorite Sapling** — craft one from a fragment and it grows into a fresh core over ~30 in-game minutes.
- **Cosmic Compass** — points at the nearest meteorite core.
- If **Applied Energistics 2** is installed, every AE2 meteor structure also emits the same decaying field (no extra blocks placed). Toggle in config.

### New items
- **Magnetic Elytra** — ferrous elytra variant with custom worn-cape texture.
- **Pyrrhotite Catalyst** — tiered radius upgrade for the Pyrrhotite Block's heat-source detection.

### Player onboarding
- **Patchouli Field Manual** — craftable in-game guidebook covering basics, every emitter, ship polarity, and advanced topics. Three cheap recipes (Book + Raw Magnetite, Book + Iron Ingot, Book + Lodestone). Auto-given to new players on first login.

### Texture pass
- All 10 ferrous ore textures redone for clearer visual variance: magnetite (coal-style specks), maghemite (copper-style), pyrrhotite (gold-style), hematite (redstone-style), titanomagnetite (lapis-style).

### Fixes
- Deepslate Maghemite + stone Titanomagnetite now actually spawn (rarely) in their "wrong" rock layer so the variants aren't impossible to find.
- `/magnetization tp` lands on the actual visible surface instead of one block below.
- Dedicated server crash on boot fixed (client-only key bindings were being loaded on the server).
- Many cross-mod tag references hardened so tag-load doesn't fail when optional mods aren't installed.

---

## 1.1.1 — Cross-mod sweep + Repulsor Gun + FE/RF

### New items
- **Repulsor Gun** — hand-held conical pusher, paired with the Magnetic Grapple. Right-click fires a one-tick pulse along your look: ships in the cone get pushed, magnetized entities get knockback, items get nudged. Self-recoil triggers if your shot lands on a magnetic emitter — closer = harder kickback, so you can launch yourself off a lodestone.
- **Field Compass overhaul** — replaced the right-click scan with a passive vanilla-compass-style needle. HUD overlay above the hotbar shows bearing, distance, polarity, and strength whenever the compass is in your hand or a Curios charm slot. Inside the Anomaly biome the needle spins erratically.
- **Vanilla compass** also scrambles inside the Anomaly biome (themed match with Field Compass). Disable in config if you want vanilla untouched.

### FE/RF support
- The five redstone-powered emitters (Electromagnet, Anchor, Repulsor Coil, Tractor Beam, Excavator) now accept FE/RF from any tech mod — Mekanism, Thermal, IE generators, AE2, Create: Crafts & Additions, etc.
- Redstone is free and takes priority; if redstone is off and energy is enabled, the emitter drains its internal buffer.
- Server admins can disable either power source separately. Setting `allowRedstonePower = false` forces players onto FE/RF for harder-mode servers.

### Curios integration
- The mod now ships its own Curios slots: **charm** (Field Compass), **back** (Magnetic Grapple), **hands** (Repulsor Gun). Configurable keybinds for the two active items — defaults unbound so you pick keys with no vanilla conflict. Field Compass stays fully passive in its slot.

### Modded armor & tools recognised
- IE (Steel + Faraday + Hammer + Wirecutter + Revolver + Railgun), Iron's Spells (iron/netherite-plated armor + Cultist/Executioner/Knight swords), Cataclysm (Ignitium kit), Alex's Caves (Hazmat suit), Mekanism (Steel armor + tools), Twilight Forest (knightmetal / steeleaf / ironwood / fiery sets) — all accept polarity stamps from the Electromagnet GUI and feed our magnetized-armor pull system.

### Cross-mod ferromagnetic tag coverage
25+ mods auto-tagged so their metal blocks and items respond to fields without any config work: Magnetizing, Create: Magnetics, Simulated, Alex's Caves, Twilight Forest, Create: New Age, Create: Crafts & Additions, Mekanism, Aether, Iron Chests / Sophisticated Storage, **Immersive Engineering** (full architecture line — Tesla Coil, Floodlight, Transformers, Capacitors, fences, gates, posts, doors, trapdoors, catwalks, scaffolding, ladders, sheetmetal), Modular Golems / Extra Golems / Cataclysm bosses / BoMD, Quark Oretoises, Supplementaries, **Macaw's mods** (Doors / Fences / Lights / Bridges / Trapdoors / Windows — every metal variant), **Immersive Aircraft + Aviator Dreams** (all 15 aircraft entities + crafting components — aircraft get velocity injection directly).

### Cross-mod lightning
- Lightning attacks from Iron's Spells, Cataclysm Scylla, Alex's Caves Tesla / Magnetron, IE Tesla Coil + razor shock + razor wire, and Twilight Forest now trigger LIRM (Lightning-Induced Remnant Magnetism) just like vanilla lightning bolts. Datapacks can extend the list.

### Admin commands
- `/magnetization help` — every subcommand with a one-line description, permission-aware.
- `/magnetization version` — print mod + Create + Sable + Aeronautics + NeoForge versions.
- `/magnetization stats` — count loaded emitters, live ships, magnetized entities.
- `/magnetization config show [filter]` — dump live config values.
- `/magnetization tp <biome> [player]` — warp a player into the anomaly / petrified forest.
- `/magnetization spawn_test_ship [size] [material]` — produce test contraptions on demand.
- Plus debug subcommands for energy buffers, Curios slots, and ship rotation.

### Grapple targeting expanded
- The Magnetic Grapple now hooks attractive emitters (unchanged), Sable ships with non-zero susceptibility, and magnetized living entities. Mobile targets are tracked each tick, so the grapple pulls toward where the ship/entity actually is, not where it was when you clicked.

### Repulsor Coil sensible default
- Default range is now **8 blocks** (was 128, which was nonsensical for a short-range pusher). GUI range buttons now step by 1 instead of 8 for finer tuning.

### Fixes
- A missing closing brace in the language file was silently breaking every translation in the mod. Fixed.
- Repulsor Gun was missing from the creative tab and JEI. Fixed.
- `/magnetization version` was reporting "Aeronautics absent" because of a wrong mod ID. Fixed.
- Temporary Magnet was missing from `#minecraft:mineable/pickaxe` and couldn't be efficiently mined. Fixed.

---

## 1.1.0 — Ship polarity + physics overhaul

### Ship polarity & susceptibility
- Ships now have a **polarity** (NORTH by default; flipped by parity of Polarity Inverters on board — 1 = SOUTH, 2 = NORTH, etc.) and a **susceptibility multiplier** derived from how many ferromagnetic blocks and magnets the ship carries.
- Magnets on a ship feed susceptibility but never polarity, so toggling a mounted electromagnet won't tear the contraption apart.
- Polarity and susceptibility are surfaced on Create goggles, Jade, WTHIT, and The One Probe: `On ship: NORTH ×1.4 (12 ferrous, 3 magnets, 1 inverter)`.

### Physics overhaul
- **Torque from off-center pulls** — extended ships rotate naturally when forces are applied off their center of mass, instead of sliding rigidly.
- **Multi-sample field integration** — each emitter samples a 3×3×3 grid across the target ship by default (configurable 1–7), so non-uniform fields read realistically on large ships.
- **Linear drag** — ships under sustained magnetic pull settle to a terminal velocity instead of accelerating forever.
- **Angular drag** — off-center torque doesn't keep ships spinning indefinitely.
- **Cumulative per-tick budget** — multiple emitters touching the same ship now share the acceleration cap, so you can't stack 10 electromagnets for a 10× pull.

### Big bug fixes
- **Magnets on ships no longer stick to the cardinal direction they were placed in** — the field origin and axis now follow the ship's pose correctly. Was the root cause of "spins near a magnet" and "repelled despite opposite poles" on rotated ships.
- **Inverter response time** — broke an inverter? Polarity now flips in ~50 ms instead of waiting up to 5 s.
- **Magnetizable entity tag trimmed** — vanilla zombies, husks, drowned, skeletons, strays, wither skeletons, vindicators, pillagers, ravager, and falling_block were all being pulled by magnets even with no metal on them. Now only iron golems, arrows, tridents (and any mob wearing metal armor) get pulled.
- **Advancement false-triggers** — several advancements were completing on the wrong action; all four corrected.

---

## Requirements

- NeoForge 21.1.230+
- Create 6.0.9+
- Sable 1.2.2+
- Create: Aeronautics 1.2.1+
- Simulated 1.2.1+
- TerraBlender 4.1.0.8+
