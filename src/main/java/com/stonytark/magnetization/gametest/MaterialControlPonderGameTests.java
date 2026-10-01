package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server behavior behind the three material-control tutorials, independent of scripted Ponder states. */
@GameTestHolder("magnetization_regressions")
@PrefixGameTestTemplate(false)
public final class MaterialControlPonderGameTests {
    private MaterialControlPonderGameTests() {}

    @GameTest(template = "empty", batch = "ponder_mr_bridge", timeoutTicks = 160)
    public static void bridgeRequiresBothActivationsRemoved(GameTestHelper h) {
        var src = new BlockPos(1, 80, 1);
        h.assertTrue(!MagneticFields.isInField(h.getLevel(), h.absolutePos(src)), "Bridge control must begin outside any field");
        for (int x = 0; x <= 4; x++) {
            h.setBlock(new BlockPos(x, 79, 1), Blocks.STONE);
            h.setBlock(new BlockPos(x, 80, 0), Blocks.STONE);
            h.setBlock(new BlockPos(x, 80, 2), Blocks.STONE);
        }
        h.setBlock(src.west(), Blocks.STONE); h.setBlock(new BlockPos(4, 80, 1), Blocks.STONE);
        h.setBlock(src, MagBlocks.MR_FLUID_BLOCK.get());
        h.runAfterDelay(20, () -> {
            h.assertTrue(h.getBlockState(src).is(MagBlocks.MR_FLUID_BLOCK.get()), "Unactivated source hardened");
            h.assertTrue(!h.getBlockState(src.east()).getFluidState().isEmpty()
                    && !h.getBlockState(src.east()).getFluidState().isSource(), "MR source did not create real flow");
            h.setBlock(src.north(), Blocks.REDSTONE_BLOCK);
            h.runAfterDelay(20, () -> {
                for (int x = 1; x <= 3; x++) h.assertTrue(h.getBlockState(new BlockPos(x, 80, 1)).is(MagBlocks.HARDENED_MR_FLUID.get()), "Redstone bridge not rigid");
                for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(x, 79, 1), Blocks.AIR);
                h.assertTrue(!h.getBlockState(src).getCollisionShape(h.getLevel(), h.absolutePos(src)).isEmpty(), "Hardened bridge is not walkable");
                var magnet = src.above(2);
                h.setBlock(magnet, MagBlocks.PERMANENT_MAGNET.get());
                h.runAfterDelay(20, () -> {
                    h.assertTrue(MagneticFields.isInField(h.getLevel(), h.absolutePos(src)), "Second activation never became active");
                    h.setBlock(src.north(), Blocks.STONE);
                    h.runAfterDelay(20, () -> {
                        h.assertTrue(h.getBlockState(src).is(MagBlocks.HARDENED_MR_FLUID.get()), "Removing redstone incorrectly removed magnetic hardening");
                        h.setBlock(magnet, Blocks.AIR);
                        h.runAfterDelay(30, () -> {
                            h.assertTrue(h.getBlockState(src).is(MagBlocks.MR_FLUID_BLOCK.get()) && h.getBlockState(src).getFluidState().isSource(), "Source did not return with both activations removed");
                            for (int x = 2; x <= 3; x++) {
                                var state = h.getBlockState(new BlockPos(x, 80, 1));
                                h.assertTrue(!state.getFluidState().isSource(), "Reverted bridge duplicated a source");
                            }
                            h.assertTrue(!h.getBlockState(src.below()).getFluidState().isEmpty(), "Unsupported source did not flow down after bridge melted");
                            h.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "ponder_strength", timeoutTicks = 60)
    public static void nativeEmitterFollowsEveryStrengthStage(GameTestHelper h) {
        var relative = new BlockPos(2, 80, 2);
        var pos = h.absolutePos(relative);
        h.setBlock(relative, MagBlocks.SAMARIUM_COBALT_MAGNET.get());
        var emitter = (PermanentMagnetBlockEntity) h.getLevel().getBlockEntity(pos);
        var aligned = MagBlocks.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.POLARITY, MagneticPolarity.NORTH);
        Direction[] faces = {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};
        checkTier(h, emitter, pos, MagneticStrength.MEDIUM);
        for (int i = 0; i < faces.length; i++) {
            h.setBlock(relative.relative(faces[i]), aligned);
            checkTier(h, emitter, pos, i < 2 ? MagneticStrength.STRONG : MagneticStrength.EXTREME);
        }
        for (Direction face : faces) h.setBlock(relative.relative(face), Blocks.AIR);
        checkTier(h, emitter, pos, MagneticStrength.MEDIUM);
        h.setBlock(relative.west(), MagBlocks.HEMATITE_BLOCK.get());
        checkTier(h, emitter, pos, MagneticStrength.WEAK);
        h.setBlock(relative.east(), MagBlocks.HEMATITE_BLOCK.get());
        checkTier(h, emitter, pos, MagneticStrength.NONE);
        h.setBlock(relative, Blocks.AIR);
        h.succeed();
    }

    private static void checkTier(GameTestHelper h, PermanentMagnetBlockEntity emitter, BlockPos pos, MagneticStrength expected) {
        PermanentMagnetBlockEntity.serverTick(h.getLevel(), pos, emitter.getBlockState(), emitter);
        var actual = emitter.currentField();
        h.assertTrue(expected == MagneticStrength.NONE ? actual == null || actual.strength() == expected
                : actual != null && actual.strength() == expected, "Native emitter expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty", batch = "ponder_equipment", timeoutTicks = 80)
    public static void nativeButtonsReverseForceAndClearKeepsMetalResponse(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.setBlock(new BlockPos(1, 1, 1), MagBlocks.ELECTROMAGNET.get());
        var player = h.makeMockPlayer(GameType.CREATIVE);
        var menu = new EmitterMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos), pos,
                EmitterMenu.CAP_ARMOR | EmitterMenu.CAP_POLARITY);
        var helmet = new ItemStack(Items.IRON_HELMET);
        h.assertTrue(menu.getSlot(0).mayPlace(helmet), "Native magnetizing slot rejected helmet");
        menu.getSlot(0).set(helmet);
        var wearer = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        wearer.setNoAi(true); wearer.setNoGravity(true); wearer.setInvulnerable(true);
        var start = wearer.position();
        var field = new MagneticField(start.add(-2, 0, 0), new Vec3(0, 1, 0), MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        int[] buttons = {EmitterMenu.BUTTON_POLARITY_NORTH, EmitterMenu.BUTTON_POLARITY_SOUTH, EmitterMenu.BUTTON_POLARITY_CLEAR};
        double[] speeds = new double[3];
        for (int i = 0; i < buttons.length; i++) {
            final int stage = i;
            h.runAfterDelay(5 + 5 * i, () -> {
                h.assertTrue(menu.clickMenuButton(player, buttons[stage]), "Native polarity action rejected");
                var stamp = menu.armorStack().get(MagDataComponents.ARMOR_POLARITY.get());
                h.assertTrue(stamp == (stage == 0 ? MagneticPolarity.NORTH : stage == 1 ? MagneticPolarity.SOUTH : null), "Wrong stamp after menu action");
                wearer.setItemSlot(EquipmentSlot.HEAD, menu.armorStack().copy());
                wearer.setPos(start); wearer.setDeltaMovement(Vec3.ZERO);
                FieldApplicator.apply(h.getLevel(), field, true, true);
                speeds[stage] = wearer.getDeltaMovement().x;
                h.assertTrue(stage == 1 ? speeds[stage] < -1e-6 : speeds[stage] > 1e-6, "Wrong actual force direction at stage " + stage);
                if (stage == 2) {
                    h.assertTrue(speeds[2] < speeds[0], "Clear failed to remove the magnetized susceptibility bonus");
                    wearer.discard(); h.succeed();
                }
            });
        }
    }
}
