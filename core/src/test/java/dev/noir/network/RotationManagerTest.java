package dev.noir.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationManagerTest {
    @Test
    void noRequestMeansRealView() {
        RotationManager r = new RotationManager();
        float[] out = r.resolve(12.5f, -3f, 0.1f, 40);
        assertEquals(12.5f, out[0]);
        assertEquals(-3f, out[1]);
        assertNull(r.owner());
    }

    @Test
    void highestPriorityWinsAndOnlyOnePerTick() {
        RotationManager r = new RotationManager();
        r.resolve(0, 0, 0, 40);
        r.request(null, 10, 0, 0, 1);
        r.request(null, 50, 0, 0, 5);
        r.request(null, 30, 0, 0, 3);
        float[] out = r.resolve(0, 0, 0, 40);
        assertEquals(50f, out[0], 1e-4);
        // Requests are consumed: next tick without a request heads back.
        float[] back = r.resolve(0, 0, 0, 1000);
        assertEquals(0f, back[0], 1e-4);
    }

    @Test
    void pitchIsClamped() {
        RotationManager r = new RotationManager();
        r.resolve(0, 0, 0, 40);
        r.request(null, 0, 170, 0, 1);
        assertEquals(90f, r.resolve(0, 0, 0, 40)[1]);
        r.request(null, 0, -500, 0, 1);
        assertEquals(-90f, r.resolve(0, 0, 0, 40)[1]);
    }

    @Test
    void speedLimitsTheStep() {
        RotationManager r = new RotationManager();
        r.resolve(0, 0, 0, 40);
        r.request(null, 90, 0, 20, 1);
        assertEquals(20f, r.resolve(0, 0, 0, 40)[0], 1e-4);
        r.request(null, 90, 0, 20, 1);
        assertEquals(40f, r.resolve(0, 0, 0, 40)[0], 1e-4);
    }

    @Test
    void yawTakesTheShortWayAndStaysContinuous() {
        RotationManager r = new RotationManager();
        r.resolve(170, 0, 0, 40);
        r.request(null, -170, 0, 0, 1);
        float yaw = r.resolve(170, 0, 0, 40)[0];
        assertEquals(190f, yaw, 1e-3, "should go +20 to 190, not -340");
    }

    @Test
    void deltasAreMultiplesOfTheMouseStep() {
        RotationManager r = new RotationManager();
        float step = 0.15f;
        r.resolve(0, 0, step, 40);
        r.request(null, 33.333f, 12.777f, 0, 1);
        float[] out = r.resolve(0, 0, step, 40);
        assertEquals(0, Math.abs(out[0] / step - Math.round(out[0] / step)), 1e-3);
        assertEquals(0, Math.abs(out[1] / step - Math.round(out[1] / step)), 1e-3);
    }

    @Test
    void returnsToRealViewExactlyThenGoesQuiet() {
        RotationManager r = new RotationManager();
        r.resolve(0, 0, 0.15f, 30);
        r.request(null, 120, 40, 0, 1);
        r.resolve(0, 0, 0.15f, 30);
        assertTrue(r.isOverriding());
        float[] out = null;
        for (int i = 0; i < 20; i++) out = r.resolve(5.25f, -2.4f, 0.15f, 30);
        assertEquals(5.25f, out[0]);
        assertEquals(-2.4f, out[1]);
        assertFalse(r.isOverriding());
    }

    @Test
    void nanRequestsAreIgnored() {
        RotationManager r = new RotationManager();
        r.resolve(0, 0, 0, 40);
        r.request(null, Float.NaN, 0, 0, 100);
        r.request(null, 10, Float.POSITIVE_INFINITY, 0, 100);
        assertEquals(0f, r.resolve(0, 0, 0, 40)[0]);
    }
}
