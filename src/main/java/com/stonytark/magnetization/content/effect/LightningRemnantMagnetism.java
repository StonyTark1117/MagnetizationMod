package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning Induced Remnant Magnetism (LIRM). Lightning strikes seed magnetism into
 * the world in two concrete ways:
 *
 * <ul>
 *   <li><b>Struck living entities</b> with worn metal armor or a held metal tool get
 *       one piece stamped with a random {@link MagneticPolarity} (NORTH/SOUTH).
 *       Already-magnetized gear is skipped — repeat strikes don't churn polarity.</li>
 *   <li><b>Blocks under the strike point</b>: every {@code #minecraft:logs} block
 *       within a small radius has a high probability of converting to
 *       {@link MagBlocks#PETRIFIED_WOOD}. Real LIRM doesn't do this; the mod's
 *       mythology does — lightning briefly mineralises whatever wood is in its
 *       discharge column.</li>
 * </ul>
 *
 * <p>The two effects fire from different events because lightning bolts can spawn
 * without a struck entity (random storm strikes), and entities can be struck
 * without nearby logs (rooftop hits). Hooking both gives even coverage.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class LightningRemnantMagnetism {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/LIRM");

    /** Block-conversion radius around a lightning bolt's spawn position. */
    private static final int CONVERSION_RADIUS = 3;
    /** Per-log probability of petrification once it falls in the conversion radius. */
    private static final double LOG_PETRIFICATION_CHANCE = 0.75d;
    /** Radius of the one-shot "magnetic shockwave" pulse on every lightning strike.
     *  Pulls every ferromagnetic item entity within range toward the struck entity. */
    private static final double SHOCKWAVE_RADIUS = 12.0d;
    /** Pull velocity applied to items inside the shockwave radius. Decays with distance. */
    private static final double SHOCKWAVE_STRENGTH = 0.8d;

    private LightningRemnantMagnetism() {}

    // ------------- entity-strike LIRM -------------

    @SubscribeEvent
    public static void onStruck(final EntityStruckByLightningEvent event) {
        if (!enabled()) {
            if (MagConfig.debugLogging()) LOG.info("LIRM struck-event suppressed: feature disabled in config");
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Gather all unmagnetized metal armor + held tools as candidates.
        final List<ItemStack> candidates = new ArrayList<>(6);
        for (final ItemStack armor : target.getArmorSlots()) {
            if (armor.is(MagTags.METAL_ARMOR) && !alreadyMagnetized(armor)) candidates.add(armor);
        }
        for (final ItemStack hand : new ItemStack[]{target.getMainHandItem(), target.getOffhandItem()}) {
            if (hand.is(MagTags.METAL_TOOLS) && !alreadyMagnetized(hand)) candidates.add(hand);
        }
        if (candidates.isEmpty()) {
            if (MagConfig.debugLogging())
                LOG.info("LIRM strike on {} produced no stamp — no eligible metal armor/tools",
                        target.getType().toShortString());
            return;
        }

        final ItemStack picked = candidates.get(target.level().random.nextInt(candidates.size()));
        final MagneticPolarity pol = target.level().random.nextBoolean()
                ? MagneticPolarity.NORTH : MagneticPolarity.SOUTH;
        picked.set(MagDataComponents.ARMOR_POLARITY.get(), pol);
        // Mark as temporary — the LIRM tick handler will decay then clear it.
        Lirm.stamp(picked, target.level().getGameTime());

        if (MagConfig.debugLogging()) {
            LOG.info("LIRM stamp: target={} item={} polarity={} candidates={}",
                    target.getType().toShortString(), picked.getItem(), pol.getSerializedName(),
                    candidates.size());
        }

        if (target.level() instanceof ServerLevel server) {
            shockwave(server, target, pol);
        }

        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.translatable("lirm.magnetization.struck",
                            Component.translatable("tooltip.magnetization.polarity." + pol.getSerializedName())
                                    .withStyle(pol == MagneticPolarity.NORTH ? ChatFormatting.AQUA : ChatFormatting.RED)
                    ).withStyle(ChatFormatting.GOLD), false);
        }
    }

    /**
     * One-shot magnetic shockwave that fires at every successful LIRM stamping.
     * Three things happen at once so the player sees, hears, and feels the strike:
     * <ol>
     *   <li>A burst of polarity-tinted particles around the target — the "field flash".</li>
     *   <li>An amethyst-resonate sound (also used by hoe dowsing) — sells the "magnetic event".</li>
     *   <li>Every ferromagnetic item entity within {@link #SHOCKWAVE_RADIUS} gets a
     *       velocity nudge toward the target — without nearby emitters, this is the only
     *       gameplay-visible magnetic effect the player gets out of LIRM, and the user
     *       reported the previous quiet stamp was unnoticeable.</li>
     * </ol>
     */
    private static void shockwave(final ServerLevel server, final LivingEntity target, final MagneticPolarity pol) {
        final Vec3 center = target.position().add(0, target.getBbHeight() * 0.5d, 0);

        // Particles + sound. sendParticles with the target as the listener arg means
        // players outside view distance won't get spammed.
        server.sendParticles(
                pol == MagneticPolarity.NORTH ? MagParticles.MAG_NORTH.get() : MagParticles.MAG_SOUTH.get(),
                center.x, center.y, center.z,
                40, 1.2, 1.2, 1.2, 0.15);
        server.playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.WEATHER, 1.4f, 1.6f);

        // Pull ferromagnetic items toward the target — the visible "magnetic burst."
        final AABB box = AABB.ofSize(center, 2 * SHOCKWAVE_RADIUS, 2 * SHOCKWAVE_RADIUS, 2 * SHOCKWAVE_RADIUS);
        for (final ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, box,
                e -> !e.getItem().isEmpty() && e.getItem().is(MagTags.FERROMAGNETIC_ITEMS))) {
            final Vec3 toCenter = center.subtract(item.position());
            final double dist = toCenter.length();
            if (dist < 0.5 || dist > SHOCKWAVE_RADIUS) continue;
            final double falloff = 1.0d - dist / SHOCKWAVE_RADIUS;
            final Vec3 nudge = toCenter.normalize().scale(SHOCKWAVE_STRENGTH * falloff);
            item.setDeltaMovement(item.getDeltaMovement().add(nudge));
            item.hasImpulse = true;
        }
    }

    private static boolean alreadyMagnetized(final ItemStack stack) {
        final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        return pol != null && pol != MagneticPolarity.NONE;
    }

    // ------------- bolt-spawn block conversion -------------

    @SubscribeEvent
    public static void onLightningSpawn(final EntityJoinLevelEvent event) {
        if (!enabled()) return;
        final Entity e = event.getEntity();
        if (!(e instanceof LightningBolt)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Note: EntityJoinLevelEvent fires before tick #1 — we run the conversion
        // here rather than in onStruck so storm bolts that miss every entity still
        // produce petrified wood under their column.
        petrifyLogsAround(level, e.blockPosition());

        // Ground-strike temporary field. The strike's actual surface point is
        // the heightmap top under the bolt; we register a temp field there
        // (probability-gated, with petrified-forest biome bias) so any
        // lightning has a chance to leave magnetism behind.
        final BlockPos surface = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                e.blockPosition()).below();
        if (!level.getBlockState(surface).isAir()) {
            TemporaryLirmFields.maybeRegisterGroundStrike(level, surface, level.getGameTime());
        }
    }

    private static void petrifyLogsAround(final ServerLevel level, final BlockPos center) {
        final BlockState target = MagBlocks.PETRIFIED_WOOD.get().defaultBlockState();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final long now = level.getGameTime();
        int converted = 0;
        for (int dx = -CONVERSION_RADIUS; dx <= CONVERSION_RADIUS; dx++) {
            for (int dy = -CONVERSION_RADIUS; dy <= CONVERSION_RADIUS; dy++) {
                for (int dz = -CONVERSION_RADIUS; dz <= CONVERSION_RADIUS; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    final BlockState here = level.getBlockState(cursor);
                    if (!here.is(BlockTags.LOGS)) continue;
                    if (level.random.nextDouble() >= LOG_PETRIFICATION_CHANCE) continue;
                    final BlockPos at = cursor.immutable();
                    level.setBlock(at, target, Block.UPDATE_CLIENTS);
                    // Newly petrified wood emits a weak temporary magnetic
                    // field that decays to zero over 30 s. The block itself
                    // stays petrified forever — the field is the LIRM half.
                    TemporaryLirmFields.registerPetrifiedLog(level, at, now);
                    converted++;
                }
            }
        }
        if (MagConfig.debugLogging() && converted > 0) {
            LOG.info("LIRM bolt at {} petrified {} log(s)", center.toShortString(), converted);
        }
    }

    private static boolean enabled() {
        try { return MagConfig.LIRM_ENABLED.get(); } catch (Throwable t) { return true; }
    }
}
