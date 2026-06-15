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

/**
 * Spawns a {@link GalliumGolem} from the same multiblock as a vanilla iron golem,
 * but built out of {@link com.stonytark.magnetization.registry.MagBlocks#SOLID_GALLIUM}
 * (T-shape topped by a carved pumpkin / jack o'lantern). Checked whenever a solid
 * gallium block or a pumpkin head is placed.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class GalliumGolemSpawnHandler {

    private static BlockPattern pattern;

    private GalliumGolemSpawnHandler() {}

    private static BlockPattern pattern() {
        if (pattern == null) {
            pattern = BlockPatternBuilder.start()
                    .aisle("~^~", "###", "~#~")
                    .where('^', BlockInWorld.hasState(s -> s.is(Blocks.CARVED_PUMPKIN) || s.is(Blocks.JACK_O_LANTERN)))
                    .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(MagBlocks.SOLID_GALLIUM.get())))
                    .where('~', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.AIR)))
                    .build();
        }
        return pattern;
    }

    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        final BlockState placed = event.getPlacedBlock();
        final boolean head = placed.is(Blocks.CARVED_PUMPKIN) || placed.is(Blocks.JACK_O_LANTERN);
        if (!head && !placed.is(MagBlocks.SOLID_GALLIUM.get())) return;

        final BlockPattern.BlockPatternMatch match = pattern().find(level, event.getPos());
        if (match == null) return;

        final GalliumGolem golem = MagEntities.GALLIUM_GOLEM.get().create(level);
        if (golem == null) return;
        golem.setPlayerCreated(true);
        final BlockPos body = match.getBlock(1, 1, 0).getPos();
        golem.moveTo(body.getX() + 0.5, body.getY() + 0.05, body.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(golem);
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
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
    }
}
