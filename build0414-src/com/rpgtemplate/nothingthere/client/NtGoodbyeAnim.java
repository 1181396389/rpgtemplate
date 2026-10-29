package com.rpgtemplate.nothingthere.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * goodbye attack pose for the hands, taken from nt_video.mp4 (first 3 s). See design/goodbye-anim-from-video.md.
 *
 * The client cannot see the server's strike time (the synced flag drops at the strike), so the animation starts
 * on the rising edge of the synced goodbye flag and runs for DURATION ticks on its own clock.
 *
 * Only the arms move. theta is the shoulder lift in degrees: 0 = hanging, 90 = forward, 180 = overhead.
 * The blade arm is the left arm; the right (stub) arm only sinks a little. The elbow stays straight, because the
 * arm is a single rigid part; the human range (shoulder -10..180) is respected.
 */
public final class NtGoodbyeAnim {
    /** Whole pose: windup (48) + strike (48..50, 0.1 s: a snap down) + recovery (50..60). */
    public static final int DURATION = 60;

    /** Keyframes: tick and shoulder lift (degrees). Cosine ease between keys. */
    private static final float[] KEY_TICK = {0f, 8f, 20f, 26f, 30f, 48f, 50f, 54f, 60f};
    private static final float[] KEY_THETA = {0f, -8f, 110f, 160f, 175f, 170f, 90f, 30f, 0f};
    /** The downswing (48..51) accelerates into the impact instead of easing in and out. */
    private static final float STRIKE_START = 48f;

    /** Hold tremor (the eerie part): 3 degrees at 4 Hz while the blade is held overhead (ticks 30..48). */
    private static final float TREMOR_DEG = 3f;
    private static final float TREMOR_HZ = 4f;
    private static final float TREMOR_START = 30f;
    private static final float TREMOR_END = 48f;

    /** Stub arm: sinks to -15 degrees over the windup, then comes back by tick 60. */
    private static final float RIGHT_SINK_DEG = -15f;
    private static final float RIGHT_SINK_END = 48f;

    /** Body lean and crouch (fraction 0..1 of the maximum), same timing as the arm pose. */
    private static final float[] LEAN_TICK = {0f, 20f, 30f, 48f, 50f, 58f};
    private static final float[] LEAN_F = {0f, 0.3f, 1f, 1f, 0.5f, 0f};
    /** Torso forward lean at full strength: 35 degrees (video, ticks 30..48). */
    public static final float LEAN_MAX_RAD = (float) Math.toRadians(35.0);
    /** Hips lower by this many model pixels at full crouch (1 px = 1/16 block). */
    public static final float CROUCH_PX = 3f;
    /** Front leg thigh lifts forward 52 degrees; back leg swings 14 degrees backward at full crouch. */
    public static final float FRONT_LEG_RAD = 0.9f;
    public static final float BACK_LEG_RAD = -0.25f;

    private static final Map<UUID, Boolean> LAST_FLAG = new HashMap<>();
    private static final Map<UUID, Float> START = new HashMap<>();

    private NtGoodbyeAnim() {
    }

    /** Called once per render of a player. flag = the synced goodbye-pending flag. now = game time (ticks). */
    public static void observe(Player player, boolean flag, float now) {
        UUID id = player.m_20148_();
        boolean before = LAST_FLAG.getOrDefault(id, Boolean.FALSE);
        if (flag && !before) {
            START.put(id, now);
        }
        LAST_FLAG.put(id, flag);
    }

    /** Elapsed ticks since the strike started, or -1 when no animation is running. */
    public static float elapsed(Player player, float now) {
        Float start = START.get(player.m_20148_());
        if (start == null) {
            return -1f;
        }
        float t = now - start;
        if (t < 0f || t >= DURATION) {
            return -1f;
        }
        return t;
    }

    /** Shoulder lift of the blade arm in degrees for elapsed tick t (0..DURATION). */
    public static float theta(float t) {
        for (int i = 0; i < KEY_TICK.length - 1; i++) {
            if (t <= KEY_TICK[i + 1]) {
                float span = KEY_TICK[i + 1] - KEY_TICK[i];
                float u = span <= 0f ? 1f : (t - KEY_TICK[i]) / span;
                float uc = clamp01(u);
                float e = KEY_TICK[i] == STRIKE_START
                    ? uc * uc                                   // accelerate into the impact
                    : 0.5f - 0.5f * (float) Math.cos(Math.PI * uc);
                float base = KEY_THETA[i] + (KEY_THETA[i + 1] - KEY_THETA[i]) * e;
                return base + tremor(t);
            }
        }
        return 0f;
    }

    /** Lean fraction 0..1 for elapsed tick t. */
    public static float leanFraction(float t) {
        if (t <= 0f || t >= LEAN_TICK[LEAN_TICK.length - 1]) {
            return 0f;
        }
        for (int i = 0; i < LEAN_TICK.length - 1; i++) {
            if (t <= LEAN_TICK[i + 1]) {
                float span = LEAN_TICK[i + 1] - LEAN_TICK[i];
                float u = span <= 0f ? 1f : (t - LEAN_TICK[i]) / span;
                float e = 0.5f - 0.5f * (float) Math.cos(Math.PI * clamp01(u));
                return LEAN_F[i] + (LEAN_F[i + 1] - LEAN_F[i]) * e;
            }
        }
        return 0f;
    }

    /** Stub arm offset in degrees for elapsed tick t. */
    public static float rightOffset(float t) {
        if (t <= RIGHT_SINK_END) {
            float u = clamp01(t / RIGHT_SINK_END);
            return RIGHT_SINK_DEG * (0.5f - 0.5f * (float) Math.cos(Math.PI * u));
        }
        float back = clamp01((t - RIGHT_SINK_END) / (DURATION - RIGHT_SINK_END));
        return RIGHT_SINK_DEG * (1f - back);
    }

    private static float tremor(float t) {
        if (t < TREMOR_START || t > TREMOR_END) {
            return 0f;
        }
        return TREMOR_DEG * (float) Math.sin(2.0 * Math.PI * TREMOR_HZ * (t / 20.0));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
