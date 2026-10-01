package com.stonytark.magnetization.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import java.util.List;

/** Opt-in dedicated-server fixture for the real goggles renderer and packet path. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagneticEngineeringAudit {
    private static int ticks;
    private static ServerSubLevel ship;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("magnetization.audit.engineering")) return;
        var player = event.getServer().getPlayerList().getPlayerByName("EngineeringAudit");
        if (player == null) return;
        var level = player.serverLevel();
        ticks++;
        if (ticks == 1) {
            player.setGameMode(GameType.CREATIVE);
            player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
            level.setDayTime(6000);
            for (int x = -8; x <= 18; x++) for (int z = -8; z <= 8; z++) level.setBlock(new BlockPos(x,64,z), Blocks.SMOOTH_STONE.defaultBlockState(),3);
            var shaft = MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            level.setBlock(new BlockPos(8,65,0), shaft,3); level.setBlock(new BlockPos(11,65,0),shaft,3);
            level.setBlock(new BlockPos(7,65,0), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
            level.setBlock(new BlockPos(12,65,0),MagBlocks.KINETIC_ELECTROMAGNET.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
            level.setBlock(new BlockPos(4,65,-2),MagBlocks.SAMARIUM_COBALT_MAGNETIC_SHAFT.get().defaultBlockState(),3);
            level.setBlock(new BlockPos(5,65,-2),MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState(),3);
            level.setBlock(new BlockPos(6,65,-2),MagBlocks.NEODYMIUM_MAGNETIC_SHAFT.get().defaultBlockState(),3);
            var base = new BlockPos(0,65,0);
            for (var pos : List.of(base,base.east(),base.above())) level.setBlock(pos,Blocks.IRON_BLOCK.defaultBlockState(),3);
            ship = SubLevelAssemblyHelper.assembleBlocks(level,base,List.of(base,base.east(),base.above()),new BoundingBox3i(0,65,0,2,67,1));
            var anchorPos = new BlockPos(3,65,0);
            level.setBlock(anchorPos,MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState(),3);
            var anchor = (MagneticAnchorBlockEntity) level.getBlockEntity(anchorPos);
            var saved = anchor.saveWithoutMetadata(level.registryAccess()); saved.putUUID("BoundShip",ship.getUniqueId()); anchor.loadWithComponents(saved,level.registryAccess());
            level.setBlock(new BlockPos(3,65,2),MagBlocks.MAGNETIC_SWITCH.get().defaultBlockState(),3);
            var sw = (MagneticSwitchBlockEntity)level.getBlockEntity(new BlockPos(3,65,2)); sw.linkAnchor(anchorPos); sw.cycleMode();
            MagConfig.MAX_ACCEL_PER_TICK.set(.01d);
        }
        if (ticks == 20) {
            var motor=(CreativeMotorBlockEntity)level.getBlockEntity(new BlockPos(7,65,0)); motor.generatedSpeed.setValue(32); motor.updateGeneratedRotation();
        }
        if (ticks < 300) player.teleportTo(11.5,65,4.2); // receiving shaft, within normal block reach
        else if (ticks < 600) player.teleportTo(3.5,65,5.5);
        else if(ticks<900) player.teleportTo(.5,65,5.5);
        else player.teleportTo(5.5,65,2);
        player.setYRot(180); player.setXRot(ticks < 600 || ticks >= 900 ? 17 : 5);
        if (ticks > 20 && ship != null) {
            var body=RigidBodyHandle.of(ship);
            if(body != null) body.addLinearAndAngularVelocity(body.getLinearVelocity(new Vector3d()).negate(),body.getAngularVelocity(new Vector3d()).negate());
            // Exercise the production applicator and reporting path with two known sources.
            for (double x : new double[]{-3,4}) {
                var field=new MagneticField(new Vec3(x,66,.5), new Vec3(1,0,0),MagneticPolarity.SOUTH,MagneticStrength.STRONG,MagneticField.Shape.OMNIDIRECTIONAL,16,10000);
                FieldApplicator.applyToSubLevelsOnly(level,field,null,s -> s.getUniqueId().equals(ship.getUniqueId()));
            }
        }
        if(ticks == 40) org.slf4j.LoggerFactory.getLogger("magnetization/engineering-audit").info("ENGINEERING_SERVER_READY ship={}",ship.getUniqueId());
    }
}
