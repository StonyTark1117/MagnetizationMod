package com.stonytark.magnetization.menu;

/** Server-authoritative display snapshot shared by menus and compatibility surfaces. */
public record MachineDisplayData(
        int energyStored,
        int energyCapacity,
        int current,
        int capacity,
        int tier,
        int auxiliary,
        Status status
) {
    public enum Status { IDLE, ACTIVE, FORMED, INVALID, HOLDING, LAUNCHING, COOLDOWN }

    public MachineDisplayData {
        status = status == null ? Status.IDLE : status;
        capacity = Math.max(1, capacity);
    }

    public int statusCode() { return status.ordinal(); }
}
