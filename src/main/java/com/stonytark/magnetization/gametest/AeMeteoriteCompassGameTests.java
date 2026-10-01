package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.meteorite.AeMeteoriteScanner;
import com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity;
import com.stonytark.magnetization.content.meteorite.MeteoriteFieldRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Exercises the server's actual AE2 registry key without replacing meteorite blocks. */
@GameTestHolder("magnetization_ae_meteorite")
@PrefixGameTestTemplate(false)
public final class AeMeteoriteCompassGameTests {
    private AeMeteoriteCompassGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void discoverySavedTargetDecayAndDisableSwitch(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("ae2"), "Run with the isolated AE2 test profile");
        final var level = helper.getLevel();
        final var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath("ae2", "meteorite"));
        helper.assertTrue(structure != null, "Pinned AE2 build must register ae2:meteorite");
        final boolean originalEnabled = MagConfig.AE2_METEORITE_HOOK_ENABLED.get();
        try {
            MagConfig.AE2_METEORITE_HOOK_ENABLED.set(true);
            final var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
            // Supply a deterministic vanilla piece as bounding-box metadata.
            // The integration reads only the actual registered AE2 structure key
            // and its start bounds; no AE2 imports or meteorite loot are changed.
            final var piece = new DesertPyramidPiece(RandomSource.create(1L),
                    chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
            final var start = new StructureStart(structure, chunk.getPos(), 0,
                    new PiecesContainer(List.of(piece)));
            chunk.setStartForStructure(structure, start);
            final var box = start.getBoundingBox();
            final BlockPos center = new BlockPos((box.minX() + box.maxX()) / 2,
                    (box.minY() + box.maxY()) / 2, (box.minZ() + box.maxZ()) / 2);

            AeMeteoriteScanner.onLevelUnload(new LevelEvent.Unload(level));
            AeMeteoriteScanner.onChunkLoad(new ChunkEvent.Load(chunk, false));
            final var entry = MeteoriteFieldRegistry.snapshot(level).stream()
                    .filter(candidate -> candidate.pos().equals(center)).findFirst().orElseThrow();
            final var target = MeteoriteFieldRegistry.compassTarget(level, center.getCenter());
            helper.assertTrue(target.position().orElseThrow().equals(center),
                    "A virtual AE2 source must be readable without a meteorite_core block entity");
            helper.assertTrue(target.dimension().equals(level.dimension().location()),
                    "Compass reading must carry the source dimension");
            helper.assertTrue(target.targetFor(level.dimension().location(), target.expiresAtTick(),
                    center.getCenter(), 512) == null, "Expired readings must stop tracking");

            // Re-scan as if this dimension was reloaded: SavedData registration
            // must retain the original charge time and avoid duplicate sources.
            final int count = MeteoriteFieldRegistry.activeCount(level);
            AeMeteoriteScanner.onLevelUnload(new LevelEvent.Unload(level));
            AeMeteoriteScanner.onChunkLoad(new ChunkEvent.Load(chunk, false));
            helper.assertTrue(MeteoriteFieldRegistry.activeCount(level) == count
                            && MeteoriteFieldRegistry.snapshot(level).contains(entry),
                    "Reload discovery must not duplicate or recharge saved sources");

            MagConfig.AE2_METEORITE_HOOK_ENABLED.set(false);
            helper.assertTrue(MeteoriteFieldRegistry.compassTarget(level, center.getCenter()).position().isEmpty(),
                    "Disabling the hook must clear saved AE2 compass targets");
            helper.assertTrue(MeteoriteFieldRegistry.snapshot(level).contains(entry),
                    "Disabling must preserve SavedData for later re-enabling");
            MagConfig.AE2_METEORITE_HOOK_ENABLED.set(true);
            helper.assertTrue(MeteoriteFieldRegistry.compassTarget(level, center.getCenter()).position().isPresent(),
                    "Re-enabling must resume the existing source without recharging it");

            // A dead source at the reading's exact origin cannot become a target.
            final BlockPos dead = center.offset(10000, 0, 0);
            MeteoriteFieldRegistry.register(level, dead, level.getGameTime() - MeteoriteCoreBlockEntity.decayTicks());
            helper.assertTrue(MeteoriteFieldRegistry.compassTarget(level, dead.getCenter()).position().isEmpty(),
                    "Fully-decayed saved meteorites must not be tracked");
            helper.succeed();
        } finally {
            MagConfig.AE2_METEORITE_HOOK_ENABLED.set(originalEnabled);
        }
    }
}
