package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Spawns a {@link GalliumGolem} from the same multiblock as a vanilla iron golem,
 * but built out of {@link com.stonytark.magnetization.registry.MagBlocks#SOLID_GALLIUM}
 * (T-shape topped by a carved pumpkin / jack o'lantern). Checked whenever a solid
 * gallium block or a pumpkin head is placed.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class GalliumGolemSpawnHandler {

    private record Spec(Supplier<? extends Block> material,
                        Supplier<? extends EntityType<? extends IronGolem>> entityType) {}

    private static final List<Spec> SPECS = List.of(
            new Spec(MagBlocks.SOLID_GALLIUM, MagEntities.GALLIUM_GOLEM),
            new Spec(MagBlocks.MAGNETITE_BLOCK, MagEntities.MAGNETITE_GOLEM),
            new Spec(MagBlocks.PYRRHOTITE_BLOCK, MagEntities.PYRRHOTITE_GOLEM),
            new Spec(MagBlocks.HEMATITE_BLOCK, MagEntities.HEMATITE_GOLEM),
            new Spec(MagBlocks.TITANOMAGNETITE_BLOCK, MagEntities.TITANOMAGNETITE_GOLEM));
    private static final java.util.Map<Block, BlockPattern> PATTERNS = new java.util.HashMap<>();

    private GalliumGolemSpawnHandler() {}

    private static BlockPattern pattern(final Block material) {
        return PATTERNS.computeIfAbsent(material, ignored -> BlockPatternBuilder.start()
                    .aisle("~^~", "###", "~#~")
                    .where('^', BlockInWorld.hasState(s -> s.is(Blocks.CARVED_PUMPKIN) || s.is(Blocks.JACK_O_LANTERN)))
                    .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(material)))
                    .where('~', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.AIR)))
                    .build());
    }

    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        trySpawn(level, event.getPos(), event.getEntity());
    }

    /** Resolve and consume a completed material pattern. Public so GameTests can
     * exercise the exact production matcher without synthesizing a placement event. */
    public static @Nullable IronGolem trySpawn(final Level level, final BlockPos placedPos,
                                               final @Nullable net.minecraft.world.entity.Entity builder) {
        if (level.isClientSide()) return null;
        final BlockState placed = level.getBlockState(placedPos);
        final boolean head = placed.is(Blocks.CARVED_PUMPKIN) || placed.is(Blocks.JACK_O_LANTERN);
        final Spec spec = SPECS.stream()
                .filter(candidate -> head || placed.is(candidate.material().get()))
                .filter(candidate -> pattern(candidate.material().get()).find(level, placedPos) != null)
                .findFirst().orElse(null);
        if (spec == null) return null;
        final BlockPattern.BlockPatternMatch match = pattern(spec.material().get()).find(level, placedPos);
        if (match == null) return null;

        final IronGolem golem = spec.entityType().get().create(level);
        if (golem == null) return null;
        golem.setPlayerCreated(true);
        if (golem instanceof MagneticGolem magnetic
                && builder instanceof net.minecraft.server.level.ServerPlayer player) {
            magnetic.setOwnerUuid(player.getUUID());
        }
        final BlockPos body = match.getBlock(1, 1, 0).getPos();
        golem.moveTo(body.getX() + 0.5, body.getY() + 0.05, body.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(golem);
        if (builder instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.advancements.CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
        }

        // Consume the structure: clear every pattern cell + break particles + updates.
        for (int x = 0; x < match.getWidth(); x++) {
            for (int y = 0; y < match.getHeight(); y++) {
                final BlockInWorld cell = match.getBlock(x, y, 0);
                level.setBlock(cell.getPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.levelEvent(2001, cell.getPos(), Block.getId(cell.getState()));
            }
        }
        for (int x = 0; x < match.getWidth(); x++) {
            for (int y = 0; y < match.getHeight(); y++) {
                level.updateNeighborsAt(match.getBlock(x, y, 0).getPos(), Blocks.AIR);
            }
        }
        return golem;
    }
}
