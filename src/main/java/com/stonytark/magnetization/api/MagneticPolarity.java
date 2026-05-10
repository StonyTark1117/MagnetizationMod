package com.stonytark.magnetization.api;

import net.minecraft.util.StringRepresentable;

/**
 * Sign of a magnetic field source.
 *
 * <p>{@link #NORTH} and {@link #SOUTH} are mirror opposites: like polarities repel,
 * unlike polarities attract. {@link #NONE} produces no force regardless of strength.
 *
 * <p>Implements {@link StringRepresentable} so it can be used directly as an
 * {@code EnumProperty<MagneticPolarity>} on blockstates (e.g. the Permanent Magnet).
 */
public enum MagneticPolarity implements StringRepresentable {
    NORTH(+1, "north"),
    SOUTH(-1, "south"),
    NONE(0, "none");

    private final int sign;
    private final String serializedName;

    MagneticPolarity(final int sign, final String serializedName) {
        this.sign = sign;
        this.serializedName = serializedName;
    }

    public int sign() {
        return sign;
    }

    public MagneticPolarity opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case NONE -> NONE;
        };
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
