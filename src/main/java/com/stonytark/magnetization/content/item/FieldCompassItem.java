package com.stonytark.magnetization.content.item;

import net.minecraft.world.item.Item;

/**
 * Passive in-hand/curio indicator. The needle direction is rendered via the
 * range-dispatch item model defined in
 * {@code assets/magnetization/items/field_compass.json}, which dispatches to
 * one of 32 frame models based on
 * {@link com.stonytark.magnetization.client.FieldCompassAngleProperty}. There's
 * no right-click action — pulling the compass out simply causes the needle to
 * lock on to the nearest active emitter, and the HUD overlay (registered in
 * {@link com.stonytark.magnetization.client.FieldCompassHud}) shows the
 * bearing + distance + polarity in text.
 *
 * <p>In the Magnetic Anomaly biome the needle spins erratically (same maths
 * the vanilla compass mixin uses) so the player sees "compasses don't work
 * here" consistently across both items.
 */
public class FieldCompassItem extends Item {
    public FieldCompassItem(final Properties props) {
        super(props);
    }
}
