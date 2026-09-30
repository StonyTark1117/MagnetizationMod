package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("magnetization_twilight")
@PrefixGameTestTemplate(false)
public final class TwilightLightningGameTests {
    @GameTest(template = "empty", timeoutTicks = 40, batch = "twilightNativeLightning")
    public static void nativeUrGhastCosmeticBoltDoesNotConvertLogs(final GameTestHelper helper) throws Exception {
        final double chance = MagConfig.LIRM_LOG_PETRIFY_CHANCE.get();
        final var boss = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("twilightforest:ur_ghast")).create(helper.getLevel());
        final var pos = helper.absolutePos(new BlockPos(2, 6, 2));
        try {
            MagConfig.LIRM_LOG_PETRIFY_CHANCE.set(1.0);
            helper.getLevel().setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
            final var summon = boss.getClass().getDeclaredMethod("spawnMinionGhastsAt", ServerLevel.class, int.class, int.class, int.class);
            summon.setAccessible(true);
            summon.invoke(boss, helper.getLevel(), pos.getX(), pos.getY() - 4, pos.getZ());
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(), "Native Ur-Ghast did not spawn lightning");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.OAK_LOG), "Cosmetic Ur-Ghast bolt caused LIRM");
            final var realBolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
            realBolt.setPos(Vec3.atBottomCenterOf(pos));
            helper.getLevel().addFreshEntity(realBolt);
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(MagBlocks.PETRIFIED_WOOD.get()),
                    "Real lightning stopped converting logs");
            helper.succeed();
        } finally {
            MagConfig.LIRM_LOG_PETRIFY_CHANCE.set(chance);
            boss.discard();
            for (var e : helper.getLevel().getEntities(null, new net.minecraft.world.phys.AABB(pos).inflate(16))) e.discard();
            helper.getLevel().removeBlock(pos, false);
        }
    }
}
