package com.stonytark.magnetization.compat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AircraftImpulseLimiterTest {
    @Test void sustainedNominalForceAndBunchedDeliveryStayBoundedAndReverse() {
        for (double limit : new double[]{1, 2}) {
            for (double force : new double[]{200, 800, 2400, 8000}) {
                Vec3 velocity=Vec3.ZERO;
                for(int i=0;i<140;i++) velocity=AircraftImpulseLimiter.addImpulse(velocity,new Vec3(-force/9*.05,0,0),limit);
                assertEquals(-limit,velocity.x,1e-9);
                for(int i=0;i<140;i++) velocity=AircraftImpulseLimiter.addImpulse(velocity,new Vec3(force/9*.05,0,0),limit);
                assertEquals(limit,velocity.x,1e-9);
            }
        }
    }
    @Test void existingFastNativeFlightIsPreservedAndCanBeBraked() {
        Vec3 nativeFlight=new Vec3(0,0,5);
        assertEquals(nativeFlight,AircraftImpulseLimiter.addImpulse(nativeFlight,new Vec3(0,0,1),2));
        assertEquals(new Vec3(0,0,4),AircraftImpulseLimiter.addImpulse(nativeFlight,new Vec3(0,0,-1),2));
        assertEquals(nativeFlight,AircraftImpulseLimiter.addImpulse(nativeFlight,Vec3.ZERO,2));
    }
    @Test void fieldsDeflectAtAndAboveTheLimitWithoutIncreasingExistingSpeed() {
        for (double speed : new double[]{1, 1.8, 5}) {
            Vec3 nativeFlight=new Vec3(0,0,speed);
            Vec3 attracted=AircraftImpulseLimiter.addImpulse(nativeFlight,new Vec3(-4,0,0),1);
            assertTrue(attracted.x < 0, "Fast native flight must still respond to perpendicular attraction");
            assertEquals(speed,attracted.length(),1e-9);
            assertTrue(attracted.z > 0, "Deflection must retain the native forward component");
            Vec3 repelled=AircraftImpulseLimiter.addImpulse(attracted,new Vec3(10,0,0),1);
            assertTrue(repelled.x > 0, "Polarity reversal must redirect fast flight");
            assertEquals(speed,repelled.length(),1e-9);
        }
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
