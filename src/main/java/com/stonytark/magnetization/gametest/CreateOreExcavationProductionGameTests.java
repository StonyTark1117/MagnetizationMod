package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.CompletableFuture;
import static com.stonytark.magnetization.gametest.NativeCompatTestSupport.*;

@GameTestHolder("magnetization_create_ore_excavation")
@PrefixGameTestTemplate(false)
public final class CreateOreExcavationProductionGameTests {
    private static final String[] VEINS = {"magnetite", "maghemite", "pyrrhotite", "hematite", "titanomagnetite",
            "lithium", "bastnasite", "monazite", "cobaltite", "borax", "helium_3"};
    private static final String[] OUTPUTS = {"raw_magnetite", "raw_maghemite", "raw_pyrrhotite", "raw_hematite",
            "raw_titanomagnetite", "raw_lithium", "bastnasite_concentrate", "monazite_concentrate",
            "cobaltite_concentrate", "boron_dust"};

    @GameTest(template = "empty", timeoutTicks = 2200, batch = "coeNativeProduction")
    public static void nativeDrillAndExtractorProduceEveryMagnetizationResource(GameTestHelper h) throws Exception {
        Object[] active = new Object[2];
        h.onEachTick(() -> {
            if (active[0] instanceof KineticBlockEntity kinetic && active[1] instanceof net.minecraft.world.level.block.entity.BlockEntity controller
                    && !controller.isRemoved()) {
                kinetic.setSpeed(256);
                kinetic.calculateStressApplied();
            }
        });
        for (int i = 0; i < VEINS.length; i++) {
            int index = i;
            h.runAfterDelay(i * 180L + 1, () -> produce(h, index, active));
        }
        h.runAfterDelay(2000, h::succeed);
    }

    private static void produce(GameTestHelper h, int index, Object[] active) {
        BlockPos base = h.absolutePos(new BlockPos(2, 40, 2));
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 2, 2)))
            h.getLevel().setBlockAndUpdate(p, p.getY() == base.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        String id = "createoreexcavation:" + (index == 10 ? "extractor" : "drilling_machine");
        var item = stack(id);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180);
        var hit = new BlockHitResult(Vec3.atCenterOf(base.below()), Direction.UP, base.below(), false);
        h.assertTrue(((BlockItem) item.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                item, hit)).consumesAction(), "Could not place native " + id);
        final var controller = h.getLevel().getBlockEntity(base.above());
        h.assertTrue(controller != null, "Native multiblock placement created no controller");
        Object data;
        try {
            data = Class.forName("com.tom.createores.OreDataAttachment").getMethod("getData", LevelChunk.class)
                    .invoke(null, h.getLevel().getChunkAt(base));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        call(data, "setRecipe", new Class<?>[]{ResourceLocation.class}, ResourceLocation.parse("magnetization:ore_vein_type/" + VEINS[index]));
        call(data, "setLoaded", new Class<?>[]{boolean.class}, true);
        call(data, "setExtractedAmount", new Class<?>[]{long.class}, 0L);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack("createoreexcavation:drill"));
        call(controller, "onClick", new Class<?>[]{net.minecraft.world.entity.player.Player.class, InteractionHand.class},
                player, InteractionHand.MAIN_HAND);
        var kinetics = new java.util.ArrayList<KineticBlockEntity>();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 1, 1)))
            if (h.getLevel().getBlockEntity(p) instanceof KineticBlockEntity k) kinetics.add(k);
        h.assertTrue(kinetics.size() == 1, "Native excavator has no unique rotational input");
        KineticBlockEntity kinetic = kinetics.getFirst();
        // Supply rotation at the native multiblock port. Its normal ticker selects the
        // chunk vein/recipe, validates the installed drill, progresses and extracts.
        active[0] = kinetic;
        active[1] = controller;
        h.runAfterDelay(160, () -> {
            h.assertTrue((long) call(call(data, "save"), "extracted") > 0,
                    "Native " + VEINS[index] + " machine did not debit vein resources");
            if (index == 10) {
                var tank = (IFluidHandler) field(controller, "fluidTankOut");
                h.assertTrue(tank.getFluidInTank(0).is(com.stonytark.magnetization.registry.MagFluids.HELIUM_3.get())
                        && tank.getFluidInTank(0).getAmount() >= 1000, "Native extraction produced no Helium-3");
            } else {
                var inventory = field(controller, "inventory");
                int slots = (int) call(inventory, "getSlots");
                boolean found = false;
                for (int slot = 0; slot < slots; slot++) {
                    var result = (net.minecraft.world.item.ItemStack) call(inventory, "getStackInSlot", new Class<?>[]{int.class}, slot);
                    found |= result.is(stack("magnetization:" + OUTPUTS[index]).getItem()) && result.getCount() > 0;
                }
                h.assertTrue(found, "Native drilling produced no " + OUTPUTS[index]);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 6000, batch = "coeRealReload")
    public static void realReloadRemovesAndRestoresVeinsAndMachineRecipes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean master = MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.get();
        boolean bastnasite = MagConfig.CREATE_ORE_EXCAVATION_BASTNASITE_VEIN_ENABLED.get();
        cleanup(h, () -> {
            MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(master);
            MagConfig.CREATE_ORE_EXCAVATION_BASTNASITE_VEIN_ENABLED.set(bastnasite);
        });
        MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(true);
        MagConfig.CREATE_ORE_EXCAVATION_BASTNASITE_VEIN_ENABLED.set(false);
        afterReload(h, server.reloadResources(server.getPackRepository().getSelectedIds()), () -> {
            assertPair(h, "bastnasite", false);
            assertPair(h, "magnetite", true);
            assertPair(h, "monazite", true);
            MagConfig.CREATE_ORE_EXCAVATION_BASTNASITE_VEIN_ENABLED.set(true);
            MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(false);
            afterReload(h, server.reloadResources(server.getPackRepository().getSelectedIds()), () -> {
                for (String vein : VEINS) assertPair(h, vein, false);
                MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(true);
                afterReload(h, server.reloadResources(server.getPackRepository().getSelectedIds()), () -> {
                    for (String vein : VEINS) assertPair(h, vein, true);
                    MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(master);
                    MagConfig.CREATE_ORE_EXCAVATION_BASTNASITE_VEIN_ENABLED.set(bastnasite);
                    afterReload(h, server.reloadResources(server.getPackRepository().getSelectedIds()), h::succeed);
                });
            });
        });
    }

    private static void assertPair(GameTestHelper h, String vein, boolean present) {
        for (String path : new String[]{"ore_vein_type/", vein.equals("helium_3") ? "extracting/" : "drilling/"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("magnetization:" + path + vein))
                    .isPresent() == present, "Real reload did not " + (present ? "restore " : "remove ") + path + vein);
    }

    private static void afterReload(GameTestHelper h, CompletableFuture<Void> future, Runnable next) {
        // Never join an unfinished server reload: applying it needs the server thread.
        h.runAfterDelay(1, () -> {
            if (!future.isDone()) afterReload(h, future, next);
            else { future.join(); next.run(); }
        });
    }
}
