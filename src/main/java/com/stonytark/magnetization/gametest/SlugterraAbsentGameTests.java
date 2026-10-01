package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.FerromagneticCompat;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("magnetization_slugterra_absent")
@PrefixGameTestTemplate(false)
public final class SlugterraAbsentGameTests {
    @GameTest(template = "empty")
    public static void absentPortLeavesVanillaMaterialsWorking(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("slugterra"), "Absent profile loaded Slugterra");
        h.assertTrue(FerromagneticCompat.isFerromagnetic(Blocks.IRON_ORE.defaultBlockState()), "Vanilla iron ore lost compatibility");
        h.succeed();
    }
}
