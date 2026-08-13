package com.stonytark.magnetization.compat.oreexcavation;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import oreexcavation.events.EventExcavate;
import oreexcavation.groups.BlockEntry;
import oreexcavation.handlers.MiningAgent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Optional Ore Excavation bridge.
 *
 * <p>Ore Excavation builds the initial material group before it posts its
 * {@code EventExcavate.Start}.  Adding our tag at that point preserves the
 * user's Ore Excavation configuration while allowing Magnetization's custom
 * ores and Helium-3 geodes to be excavated as contiguous deposits.  The class
 * is only resolved when the optional mod is present (see the guarded wiring in
 * {@code Magnetization}).
 */
public final class MagOreExcavationCompat {

    private static final BlockEntry MAGNETIZATION_ORES =
            new BlockEntry("magnetization:ore_excavation");

    private MagOreExcavationCompat() {}

    public static void wire(final net.neoforged.bus.api.IEventBus bus) {
        bus.register(MagOreExcavationCompat.class);
    }

    @SubscribeEvent
    public static void onExcavationStart(final EventExcavate.Start event) {
        final MiningAgent agent = event.getAgent();
        if (MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.get()
                && agent != null && agent.state.is(MagTags.ORE_EXCAVATION_BLOCKS)) {
            agent.blockGroup.add(MAGNETIZATION_ORES);
        }
    }
}
