package dev.noir.network;

import dev.noir.module.Module;
import dev.noir.util.MathUtil;

/**
 * Decides the rotation that goes into each tick's movement packet.
 *
 * <ul>
 *   <li>At most one rotation per tick: the highest-priority request of the tick.</li>
 *   <li>Pitch is clamped to [-90, 90]; yaw is continuous (never jumps by 360).</li>
 *   <li>Deltas are multiples of vanilla's mouse step for the current sensitivity, like real mouse input.</li>
 *   <li>With no request, the server rotation walks back to the real view and then
 *       matches it exactly, at which point vanilla stops sending rotation.</li>
 * </ul>
 */
public final class RotationManager {
    private float serverYaw;
    private float serverPitch;
    private boolean initialized;
    private Request pending;
    private Module owner;
    private boolean overriding;

    private record Request(Module module, float yaw, float pitch, float speed, int priority) {}

    void request(Module module, float yaw, float pitch, float speed, int priority) {
        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) return;
        if (pending == null || priority > pending.priority) {
            pending = new Request(module, yaw, MathUtil.clamp(pitch, -90f, 90f), Math.max(0f, speed), priority);
        }
    }

    void release(Module module) {
        if (pending != null && pending.module == module) pending = null;
    }

    /**
     * Called exactly once per movement-packet decision.
     *
     * @return {yaw, pitch} to put in the packet. Equal to the real view when nothing overrides it.
     */
    public float[] resolve(float realYaw, float realPitch, float step, float returnSpeed) {
        realPitch = MathUtil.clamp(realPitch, -90f, 90f);
        if (!initialized) {
            serverYaw = realYaw;
            serverPitch = realPitch;
            initialized = true;
        }
        Request r = pending;
        pending = null;

        if (r == null && serverYaw == realYaw && serverPitch == realPitch) {
            owner = null;
            overriding = false;
            return new float[] {realYaw, realPitch};
        }

        float targetYaw = r != null ? r.yaw : realYaw;
        float targetPitch = r != null ? r.pitch : realPitch;
        float speed = r != null ? r.speed : returnSpeed;
        if (r != null) {
            owner = r.module;
            overriding = true;
        }

        float dy = MathUtil.wrapDegrees(targetYaw - serverYaw);
        float dp = targetPitch - serverPitch;
        float dist = (float) Math.hypot(dy, dp);
        if (speed > 0 && dist > speed) {
            dy *= speed / dist;
            dp *= speed / dist;
        }
        if (step > 0) {
            dy = Math.round(dy / step) * step;
            dp = Math.round(dp / step) * step;
        }
        float ny = serverYaw + dy;
        float np = MathUtil.clamp(serverPitch + dp, -90f, 90f);

        if (r == null) {
            float remaining = (float) Math.hypot(MathUtil.wrapDegrees(realYaw - ny), realPitch - np);
            if (remaining <= Math.max(step * 1.5f, 0.01f)) {
                // Back in sync: use the real values exactly so vanilla's change check goes quiet.
                ny = realYaw;
                np = realPitch;
                owner = null;
                overriding = false;
            }
        }
        serverYaw = ny;
        serverPitch = np;
        return new float[] {ny, np};
    }

    /** The module whose rotation is in effect, or null when none (or the server sees the real view). */
    public Module owner() {
        return owner;
    }

    public boolean isOverriding() {
        return overriding;
    }

    public float serverYaw() {
        return serverYaw;
    }

    public float serverPitch() {
        return serverPitch;
    }

    public boolean initialized() {
        return initialized;
    }

    /** Called on world change and when the server teleports/rotates us. */
    public void sync(float yaw, float pitch) {
        serverYaw = yaw;
        serverPitch = MathUtil.clamp(pitch, -90f, 90f);
        initialized = true;
        pending = null;
        owner = null;
        overriding = false;
    }

    public void reset() {
        initialized = false;
        pending = null;
        owner = null;
        overriding = false;
    }
}
