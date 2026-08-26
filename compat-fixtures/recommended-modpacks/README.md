# Recommended-pack compatibility fixtures

These are development-only CurseForge manifests. They pin the two packs that
motivated the 1.4.2/1.4.3 compatibility audit; neither manifest is copied into
the release JAR, queried at runtime, or used as an activation condition.
Magnetization integrations activate only from individual mod IDs.

## Pinned manifests

| Fixture | Minecraft / loader | Files | Original Magnetization file |
|---|---|---:|---:|
| CreateSMP Polska 1.5 | 1.21.1 / NeoForge 21.1.236 | 325 | CurseForge 8332175 (1.2.2) |
| Create: Ground and Air 3.1 | 1.21.1 / NeoForge 21.1.241 | 184 | CurseForge 8649944 (1.4.1) |

`../recommended-modpacks.json` records both the downloaded-manifest digest and
the normalized fixture digest. Optional manifest entries stay optional when a
fixture is assembled; the audit must not silently install every `required:false`
entry.

## Integration classification

| Individual mod | Classification | Authoritative coverage |
|---|---|---|
| Sable / Create: Aeronautics / Simulated | Required parent runtime | Core GameTests, minimal server/client, ship regression suite |
| Create: AeroPortals | Direct optional bridge | Current and pack-pinned 1.1.2 transfer profiles |
| Create: Coasters Simulated | Direct optional bridge | Current and pack-pinned 0.1 cart profiles |
| Coasters: Magnetized | Direct optional bridge | Anchor behavior GameTest plus isolated client mixin boot |
| Coasters: Engineered | Direct optional recipe/API use | Recipe, FE, EMP, motor/regeneration, goggles/Jade ownership tests and client boot |
| Create: Coasters Extras / Track Styles | Inherited parent-mod behavior | Parent cart/inducer tests, complete material/style metadata, reload, and client boots; no duplicate physics hook |
| CBC Aeronautics Missiles | Direct optional bridge | Real missile containment/guidance GameTests and complete client stack boot |
| Create: Big Cannons, New Age, Crafts & Additions, TFMG, Tracks, Steam 'n' Rails, Copycats+, Diesel Generators, Ender Transmission, Cosmonautics | Existing direct optional bridges | One isolated published-mod GameTest profile per addon |
| JEI / JER | Informational integration | Pack-floor JEI client plus exactly one live registration of all 28 JER charts |
| Jade | Informational integration | Magnetization owns content providers; Sable owns sublevel ray tracing; isolated Jade profiles reject duplicate/error startup |
| Ponder | Parent documentation API | Core and optional scene catalogs plus isolated Ponder client |
| Ordinary inventory, fluid, FE, recipe, and `c:`-tag consumers | Standard API compatibility | Native NeoForge/Create contracts; no addon-specific hook |
| Remaining unrelated content, UI, performance, and cosmetic mods | No meaningful interaction | Full-pack client smoke catches classloading/resource conflicts; no gratuitous runtime detection |

## Full-pack acceptance

For each fixture:

1. Assemble only required manifest files and the pack's overrides.
2. Snapshot the bundled Magnetization JAR, manifest, and existing configuration.
3. Use `scripts/with-recommended-pack-candidate.sh` to install the exact candidate
   temporarily and run `scripts/run-recommended-pack-smoke.sh` on its private
   X server.
4. Run `scripts/verify-pack-config-migration.py` against the before/after TOML.
5. Let the wrapper restore the original JAR, then verify its filename/digest and
   the manifest digest. The candidate must be absent afterward.

The release is not accepted from a title-screen image alone: the client log must
show the candidate version/digest, every required individual mod ID, a complete
resource reload, a stable non-splash frame, and no Magnetization error/fatal.
