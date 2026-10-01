package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.client.OreCompassScanner;
import com.stonytark.magnetization.compat.FerromagneticCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity;
import com.stonytark.magnetization.content.item.OreCompassItem;
import com.stonytark.magnetization.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("magnetization_slugterra")
@PrefixGameTestTemplate(false)
public final class SlugterraOreGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void allCavernOresTuneScanAndExtract(final GameTestHelper h) throws Exception {
        h.assertTrue(net.neoforged.fml.ModList.get().isLoaded("slugterra"), "Slugterra fixture missing");
        final var level = h.getLevel();
        final var origin = h.absolutePos(new BlockPos(1, 12, 1));
        final var target = origin.east(3);
        level.setBlockAndUpdate(origin, MagBlocks.MAGNETIC_EXCAVATOR.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST));
        final var excavator = (MagneticExcavatorBlockEntity) level.getBlockEntity(origin);
        excavator.setRangeOverride(8);
        final var scan = MagneticExcavatorBlockEntity.class.getDeclaredMethod("findFerromagneticsInCone",
                net.minecraft.server.level.ServerLevel.class, Direction.class, int.class);
        scan.setAccessible(true);
        int checked = 0;
        final var compass = new ItemStack(MagItems.ORE_COMPASS.get());
        for (String metal : List.of("iron", "copper", "gold")) {
            final var tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse("slugterra:" + metal + "_ores"));
            final var ores = BuiltInRegistries.BLOCK.getTag(tag).orElseThrow();
            h.assertTrue(ores.size() == 15, "Expected fifteen cavern " + metal + " ores");
            for (var holder : ores) {
                final var ore = holder.value();
                final var id = BuiltInRegistries.BLOCK.getKey(ore);
                final var state = ore.defaultBlockState();
                level.setBlockAndUpdate(target, state);
                h.assertTrue(FerromagneticCompat.isFerromagnetic(state), "Not extractable: " + id);
                h.assertTrue(FerromagneticCompat.isFerromagnetic(new ItemStack(ore)), "Dropped ore not magnetic: " + id);
                h.assertTrue(OreCompassItem.matchesOre(compass, state), "Untuned compass ignores " + id);
                final var event = new AnvilUpdateEvent(compass, new ItemStack(ore), "", 0, h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
                NeoForge.EVENT_BUS.post(event);
                h.assertTrue(OreCompassItem.tunedOre(event.getOutput()) == ore, "Anvil did not tune " + id);
                h.assertTrue(event.getMaterialCost() == 1 && event.getCost() == 5, "Anvil costs changed");
                h.assertTrue(target.equals(OreCompassScanner.nearest(level, origin, id.toString(),
                        s -> OreCompassItem.matchesOre(event.getOutput(), s))), "Needle scan missed " + id);
                h.assertTrue(((List<?>) scan.invoke(excavator, level, Direction.EAST, 6)).contains(target),
                        "Excavator cone omitted " + id);
                checked++;
            }
        }
        h.assertTrue(checked == 45, "Incomplete ore coverage");
        final var state = level.getBlockState(target);
        final var tuned = compass.copy();
        tuned.set(MagDataComponents.TUNED_ORE.get(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        for (var setting : List.of(MagConfig.SLUGTERRA_ORES_ENABLED, MagConfig.SLUGTERRA_COMPAT_ENABLED)) {
            final boolean old = setting.get();
            try {
                setting.set(false);
                h.assertTrue(!OreCompassItem.matchesOre(tuned, state), "Disabled tuned compass still matches");
                h.assertTrue(((List<?>) scan.invoke(excavator, level, Direction.EAST, 6)).isEmpty(), "Disabled excavation still selects ores");
                h.assertTrue(!FerromagneticCompat.isFerromagnetic(new ItemStack(state.getBlock())), "Disabled ore pickup still active");
            } finally { setting.set(old); }
        }
        // Three representative ores must actually leave the world through the powered machine.
        final var positions = List.of(target, target.north(), target.south());
        final var metals = List.of("iron", "copper", "gold");
        for (int i = 0; i < 3; i++) level.setBlockAndUpdate(positions.get(i),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("slugterra:slugslate_" + metals.get(i) + "_ore")).defaultBlockState());
        final var stone = target.east();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        excavator.getRedstoneFuelSlot().setItem(0, new ItemStack(Items.REDSTONE));
        h.succeedWhen(() -> {
            h.assertTrue(positions.stream().allMatch(p -> level.getBlockState(p).isAir()), "Powered excavator has not extracted all three metals");
            h.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "Nonmetal outside pull path was extracted");
            level.removeBlock(origin, false);
        });
    }
}
