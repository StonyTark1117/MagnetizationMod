package com.stonytark.magnetization.compat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AircraftImpulseLimiterTest {
    @Test void sustainedNominalForceAndBunchedDeliveryStayBoundedAndReverse() {
        Vec3 velocity=Vec3.ZERO;
        for(int i=0;i<140;i++) velocity=AircraftImpulseLimiter.addImpulse(velocity,new Vec3(-200d/9*.05,0,0),2);
        assertEquals(-2,velocity.x,1e-9);
        for(int i=0;i<140;i++) velocity=AircraftImpulseLimiter.addImpulse(velocity,new Vec3(200d/9*.05,0,0),2);
        assertEquals(2,velocity.x,1e-9);
    }
    @Test void existingFastNativeFlightIsPreservedAndCanBeBraked() {
        Vec3 nativeFlight=new Vec3(0,0,5);
        assertEquals(nativeFlight,AircraftImpulseLimiter.addImpulse(nativeFlight,new Vec3(0,0,1),2));
        assertEquals(new Vec3(0,0,4),AircraftImpulseLimiter.addImpulse(nativeFlight,new Vec3(0,0,-1),2));
        assertEquals(nativeFlight,AircraftImpulseLimiter.addImpulse(nativeFlight,Vec3.ZERO,2));
    }
    @Test void smallForcesAndPerpendicularNativeMotionKeepTheirDirection() {
        Vec3 initial=new Vec3(0,0,1);
        assertEquals(new Vec3(.1,0,1),AircraftImpulseLimiter.addImpulse(initial,new Vec3(.1,0,0),2));
        Vec3 limited=AircraftImpulseLimiter.addImpulse(initial,new Vec3(100,0,0),2);
        assertEquals(2,limited.length(),1e-9);
        assertEquals(1,limited.z,1e-9);
        assertEquals(Math.sqrt(3),limited.x,1e-9);
    }
}
