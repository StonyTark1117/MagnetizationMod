package com.stonytark.magnetization.data;

import com.mojang.logging.LogUtils;
import com.stonytark.magnetization.Magnetization;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the 4 meteorite crater NBT templates that the jigsaw-mode
 * placement reads at runtime. Run with {@code ./gradlew runData}; output
 * lands in {@code src/generated/resources/data/magnetization/structure/meteorite/}.
 *
 * <p>Each template is a bowl-shaped carve filled with crater materials, plus
 * an {@code minecraft:structure_void} marker block at the bowl's centre that
 * the {@link com.stonytark.magnetization.worldgen.MeteoriteCenterMarkerProcessor}
 * promotes to {@code magnetization:meteorite_core} at instantiate-time. Four
 * size profiles ship: small (r=3, shallow), medium (r=4), large (r=5,
 * deeper), shallow (r=5 wide but flat-floored).
 *
 * <p>Why a data provider instead of hand-edited NBT files: the bowl shape is
 * deterministic from a few numeric tunables (radius, depth multiplier, fill
 * mix). Authoring four variants by hand in MCEdit would be tedious and would
 * lose the link between the procedural feature's intent and the template's
 * shape. Codegen keeps them in sync.
 */
public final class MagCraterTemplateProvider implements DataProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 1.21.1 data version. NBT structure files require this so vanilla can
     *  detect version mismatches and apply DataFixer migrations. */
    private static final int DATA_VERSION_1_21_1 = 3955;

    private final PackOutput output;

    public MagCraterTemplateProvider(final PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        final Path structDir = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(Magnetization.MOD_ID).resolve("structure").resolve("meteorite");

        final List<CompletableFuture<?>> futures = new ArrayList<>();
        futures.add(writeCrater(cache, structDir, "small",   3, 0.85f, 0xDEAD_0001L, false));
        futures.add(writeCrater(cache, structDir, "medium",  4, 1.00f, 0xDEAD_0002L, false));
        futures.add(writeCrater(cache, structDir, "large",   5, 1.10f, 0xDEAD_0003L, false));
        futures.add(writeCrater(cache, structDir, "shallow", 5, 0.55f, 0xDEAD_0004L, false));
        // Rare iron-oxide-family showcase: same shape as "medium" but the fill
        // mix swaps in maghemite/hematite/titanomagnetite blocks. Low pool
        // weight keeps it as a "I just hit the jackpot" find.
        futures.add(writeCrater(cache, structDir, "metallic", 4, 1.00f, 0xDEAD_0005L, true));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    private CompletableFuture<?> writeCrater(final CachedOutput cache, final Path dir,
                                              final String name, final int radius,
                                              final float depthMul, final long seed,
                                              final boolean metallic) {
        final CompoundTag tag = buildCrater(radius, depthMul, seed, metallic);
        final Path file = dir.resolve(name + ".nbt");
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(file.getParent());
                // CachedOutput wants gzipped NBT for .nbt structure files
                // (matches what NbtIo.writeCompressed produces and what
                // vanilla's StructureTemplateManager expects to read back).
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                NbtIo.writeCompressed(tag, baos);
                final byte[] bytes = baos.toByteArray();
                cache.writeIfNeeded(file, bytes, com.google.common.hash.Hashing.sha1().hashBytes(bytes));
            } catch (final IOException e) {
                LOGGER.error("Failed to write {}: {}", file, e.toString());
                throw new RuntimeException(e);
            }
        });
    }

    /** Build the structure NBT for one crater variant. Bowl carved as a
     *  hemispherical pit; floor + walls painted with the crater fill mix;
     *  centre cell marked with structure_void for the marker processor. The
     *  {@code metallic} flag swaps the fill palette to the iron-oxide family
     *  for the rare "jackpot" variant. */
    private static CompoundTag buildCrater(final int radius, final float depthMul, final long seed,
                                            final boolean metallic) {
        final Random rand = new Random(seed);
        final int diameter = radius * 2 + 1;
        final int maxDepth = Math.max(1, Math.round(radius * depthMul));
        final int sizeX = diameter, sizeY = maxDepth + 1, sizeZ = diameter;
        final int centreX = radius, centreZ = radius;

        final Palette palette = new Palette();
        final int airIdx       = palette.idFor("minecraft:air");
        final int voidIdx      = palette.idFor("minecraft:structure_void");
        // Standard mix:
        final int magBlockIdx     = palette.idFor(Magnetization.MOD_ID + ":magnetite_block");
        final int rawMagBlockIdx  = palette.idFor(Magnetization.MOD_ID + ":raw_magnetite_block");
        final int petrifiedIdx    = palette.idFor(Magnetization.MOD_ID + ":petrified_wood");
        final int cobbleIdx       = palette.idFor("minecraft:cobblestone");
        // Metallic-variant extras (only added to palette when used so the
        // standard templates stay small):
        final int maghemiteIdx       = metallic ? palette.idFor(Magnetization.MOD_ID + ":maghemite_block")     : -1;
        final int hematiteIdx        = metallic ? palette.idFor(Magnetization.MOD_ID + ":hematite_block")      : -1;
        final int titanomagnetiteIdx = metallic ? palette.idFor(Magnetization.MOD_ID + ":titanomagnetite_block"): -1;

        final List<int[]> placements = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final int distSq = dx * dx + dz * dz;
                if (distSq > radius * radius) continue;

                final float distFrac = (float) Math.sqrt(distSq) / radius;
                final int bowlDepth = Math.max(1, Math.round((1f - distFrac) * maxDepth));

                final int colX = centreX + dx;
                final int colZ = centreZ + dz;
                final int fillTop = maxDepth - bowlDepth;
                for (int y = 0; y <= fillTop; y++) {
                    final int idx = metallic
                            ? pickMetallicFillIdx(rand, distFrac, magBlockIdx, maghemiteIdx,
                                                  hematiteIdx, titanomagnetiteIdx, petrifiedIdx)
                            : pickFillIdx(rand, distFrac, magBlockIdx, rawMagBlockIdx,
                                          petrifiedIdx, cobbleIdx);
                    placements.add(new int[]{colX, y, colZ, idx});
                }
                for (int y = fillTop + 1; y < sizeY; y++) {
                    placements.add(new int[]{colX, y, colZ, airIdx});
                }
            }
        }
        placements.add(new int[]{centreX, 1, centreZ, voidIdx});

        return assembleNbt(sizeX, sizeY, sizeZ, palette, placements);
    }

    /** Mirror MeteoriteCraterFeature.pickCraterFill — closer to centre =
     *  more magnetite-heavy, outer edge fades to cobble/petrified_wood. */
    private static int pickFillIdx(final Random rand, final float distFrac,
                                    final int mag, final int rawMag,
                                    final int petrified, final int cobble) {
        final float roll = rand.nextFloat();
        if (distFrac < 0.5f) {
            if (roll < 0.55f) return mag;
            if (roll < 0.85f) return rawMag;
            return petrified;
        }
        if (roll < 0.35f) return rawMag;
        if (roll < 0.65f) return petrified;
        return cobble;
    }

    /** Iron-oxide-family mix for the rare metallic crater. Magnetite stays as
     *  the structural majority so it still reads as a meteorite, but each fill
     *  cell has a meaningful chance of landing on a rarer oxide block. */
    private static int pickMetallicFillIdx(final Random rand, final float distFrac,
                                            final int mag, final int maghemite,
                                            final int hematite, final int titanomagnetite,
                                            final int petrified) {
        final float roll = rand.nextFloat();
        if (distFrac < 0.5f) {
            // Inner core: titanomagnetite headline find, generous magnetite filler.
            if (roll < 0.40f) return mag;
            if (roll < 0.65f) return titanomagnetite;
            if (roll < 0.85f) return hematite;
            return maghemite;
        }
        // Outer rim: more maghemite/hematite, less of the headline ore.
        if (roll < 0.35f) return mag;
        if (roll < 0.60f) return maghemite;
        if (roll < 0.85f) return hematite;
        return petrified;
    }

    private static CompoundTag assembleNbt(final int sizeX, final int sizeY, final int sizeZ,
                                            final Palette palette, final List<int[]> blocks) {
        final CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", DATA_VERSION_1_21_1);

        // size
        final ListTag size = new ListTag();
        size.add(IntTag.valueOf(sizeX));
        size.add(IntTag.valueOf(sizeY));
        size.add(IntTag.valueOf(sizeZ));
        root.put("size", size);

        // palette
        final ListTag paletteTag = new ListTag();
        for (final String blockId : palette.ordered()) {
            final CompoundTag entry = new CompoundTag();
            entry.putString("Name", blockId);
            paletteTag.add(entry);
        }
        root.put("palette", paletteTag);

        // blocks
        final ListTag blocksTag = new ListTag();
        for (final int[] p : blocks) {
            final CompoundTag b = new CompoundTag();
            final ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(p[0]));
            pos.add(IntTag.valueOf(p[1]));
            pos.add(IntTag.valueOf(p[2]));
            b.put("pos", pos);
            b.putInt("state", p[3]);
            blocksTag.add(b);
        }
        root.put("blocks", blocksTag);

        // entities (empty but required)
        root.put("entities", new ListTag());
        return root;
    }

    @Override
    public String getName() {
        return "Meteorite Crater Templates";
    }

    /** Tiny ordered-set wrapper. Block IDs are interned by insertion order;
     *  {@link #ordered()} returns them in the same order as the palette indices
     *  hand out. */
    private static final class Palette {
        private final Map<String, Integer> indices = new HashMap<>();
        private final List<String> order = new ArrayList<>();
        int idFor(final String blockId) {
            return indices.computeIfAbsent(blockId, k -> {
                order.add(k);
                return order.size() - 1;
            });
        }
        List<String> ordered() {
            return order;
        }
    }
}
