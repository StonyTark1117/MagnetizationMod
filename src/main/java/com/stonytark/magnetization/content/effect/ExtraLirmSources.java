package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticStrength;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Additional sources that seed entries into {@link TemporaryLirmFields}:
 *
 * <ul>
 *   <li><b>Explosions</b> — every detonation leaves a residual magnetic
 *       field at its center. Range scales as 2× the blast radius, tier rises
 *       with size (WEAK &lt; 4, MEDIUM 4–7, STRONG ≥7). Charged creepers
 *       (default radius 6) hit MEDIUM with a 12-block range and get an extra
 *       multiplier so the player notices them specifically.</li>
 *   <li><b>Iron Golem death</b> — a brief WEAK field at the golem's corpse
 *       position. Real-world analog: the iron body's magnetic domains briefly
 *       re-align as it falls apart.</li>
 *   <li><b>Ferromagnetic ore break</b> — small chance per break to seed a
 *       tiny WEAK field at the broken cell. Gated on a low probability so
 *       routine mining doesn't flood the registry.</li>
 * </ul>
 *
 * <p>All three funnel through {@link TemporaryLirmFields#register} so decay
 * timing and applicator behavior stay uniform across sources.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class ExtraLirmSources {

    /** Multiplier on explosion radius for the residual field's range. 2.0 lets
     *  a TNT (r=4) leave an 8-block field, charged-creeper (r=6) a 12-block field. */
    private static final double EXPLOSION_RANGE_MULT = 2.0d;

    /** Extra multiplier applied to charged-creeper explosions only — they
     *  should feel categorically different from a normal creeper, not just
     *  "the radius is bigger." 1.6× brings their field to ~19 blocks. */
    private static final double CHARGED_CREEPER_FIELD_MULT = 1.6d;

    /** Range thresholds: explosions with radius below these get the listed tier. */
    private static final float TIER_BOUNDARY_WEAK_TO_MEDIUM = 4.0f;
    private static final float TIER_BOUNDARY_MEDIUM_TO_STRONG = 7.0f;

    /** Per-break probability of seeding a tiny residual field on a ferromagnetic
     *  ore break. Tuned low so routine mining doesn't drown the registry. */
    private static final double ORE_BREAK_FIELD_CHANCE = 0.05d;

    /** Field params for the iron-golem-death residual. */
    private static final double GOLEM_DEATH_RANGE = 5.0d;
    private static final MagneticStrength GOLEM_DEATH_TIER = MagneticStrength.WEAK;

    /** Field params for ore-break residuals. */
    private static final double ORE_BREAK_RANGE = 3.0d;
    private static final MagneticStrength ORE_BREAK_TIER = MagneticStrength.WEAK;

    private ExtraLirmSources() {}

    @SubscribeEvent
    public static void onExplosionDetonate(final ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final Explosion explosion = event.getExplosion();
        final float radius = explosion.radius();
        if (radius <= 0.1f) return;

        double range = radius * EXPLOSION_RANGE_MULT;
        // Charged creepers get the extra-large field the player can't miss.
        if (explosion.getDirectSourceEntity() instanceof Creeper creeper && creeper.isPowered()) {
            range *= CHARGED_CREEPER_FIELD_MULT;
        }

        final MagneticStrength tier = tierForRadius(radius);
        final Vec3 origin = explosion.center();
        TemporaryLirmFields.registerRandomPolarity(server, origin, tier, range,
                server.getGameTime());
    }

    private static MagneticStrength tierForRadius(final float radius) {
        if (radius < TIER_BOUNDARY_WEAK_TO_MEDIUM) return MagneticStrength.WEAK;
        if (radius < TIER_BOUNDARY_MEDIUM_TO_STRONG) return MagneticStrength.MEDIUM;
        return MagneticStrength.STRONG;
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (!(entity instanceof IronGolem)) return;
        if (!(entity.level() instanceof ServerLevel server)) return;
        // Iron-rich body coming apart — brief residual at its corpse position.
        final Vec3 origin = entity.position().add(0, entity.getBbHeight() * 0.5d, 0);
        TemporaryLirmFields.registerRandomPolarity(server, origin,
                GOLEM_DEATH_TIER, GOLEM_DEATH_RANGE, server.getGameTime());
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (!event.getState().is(MagTags.FERROMAGNETIC_BLOCKS)) return;
        if (server.random.nextDouble() >= ORE_BREAK_FIELD_CHANCE) return;
        final BlockPos pos = event.getPos();
        TemporaryLirmFields.registerRandomPolarity(server, Vec3.atCenterOf(pos),
                ORE_BREAK_TIER, ORE_BREAK_RANGE, server.getGameTime());
    }
}
