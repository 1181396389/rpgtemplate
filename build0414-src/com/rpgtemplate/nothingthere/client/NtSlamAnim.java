package com.rpgtemplate.nothingthere.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * oh man 下砸动作（客户端，右臂持锤）：按住时把锤子举过头顶并有轻微颤抖，松开并确认砸下后急速劈下，
 * 停在身前地面附近，随后收回。松开但未达到砸下（点按）时直接收回，不做劈下。
 *
 * 角度约定与 NtHolleAnim 相同：0 = 自然下垂，90 = 水平前举，165 = 举过头顶（度）。
 * 状态由同步标志驱动：DATA_OH_CHARGE（按住）与 DATA_OH_SLAM（砸下）的上升沿/下降沿。
 * 时间按游戏 tick 推进（渲染时传入 game time + partialTick），与帧率无关。
 */
public final class NtSlamAnim {
    static final float RAISE_TICKS = 10f;     // 举起到头顶的时长
    static final float RAISE_DEG = 165f;      // 举过头顶
    static final float TREMOR_DEG = 2.5f;     // 举起保持时的轻微颤抖幅度（度）
    static final float SNAP_TICKS = 3f;       // 急速劈下的时长
    static final float SNAP_DEG = 35f;        // 劈到身前地面附近
    static final float HOLD_TICKS = 3f;       // 落地后的停顿
    static final float RECOVER_TICKS = 8f;    // 收回到自然下垂

    private enum State { IDLE, CHARGE, SNAP, HOLD, RECOVER }

    private static final class Track {
        State state = State.IDLE;
        float t;
        float from;
        float theta;
        float last = Float.NaN;
        boolean prevCharge;
        boolean prevSlam;
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private NtSlamAnim() {
    }

    /** 每次渲染时调用一次。charge = 同步的蓄力标志，slam = 同步的砸下标志，now = 游戏时间（tick）。 */
    public static void observe(Player player, boolean charge, boolean slam, float now) {
        Track tr = TRACKS.computeIfAbsent(player.m_20148_(), k -> new Track());
        float dt = Float.isNaN(tr.last) ? 0f : clamp(now - tr.last, 0f, 2f);
        tr.last = now;
        boolean chargeRise = charge && !tr.prevCharge;
        boolean chargeFall = !charge && tr.prevCharge;
        boolean slamRise = slam && !tr.prevSlam;
        tr.prevCharge = charge;
        tr.prevSlam = slam;

        if (slamRise && tr.state != State.SNAP && tr.state != State.HOLD) {
            toState(tr, State.SNAP);
        } else {
            switch (tr.state) {
                case IDLE -> {
                    if (chargeRise) {
                        toState(tr, State.CHARGE);
                    }
                }
                case CHARGE -> {
                    if (!charge) {
                        // 松开但未砸下（点按）：直接收回
                        toState(tr, State.RECOVER);
                    }
                }
                case RECOVER -> {
                    if (chargeRise) {
                        toState(tr, State.CHARGE);
                    }
                }
                default -> {
                }
            }
        }
        advance(tr, dt);
    }

    /** 当前手臂角度（度）；动作未进行时返回 -1。 */
    public static float theta(Player player) {
        Track tr = TRACKS.get(player.m_20148_());
        if (tr == null || tr.state == State.IDLE) {
            return -1f;
        }
        return tr.theta;
    }

    private static void toState(Track tr, State next) {
        tr.from = tr.theta;
        tr.t = 0f;
        tr.state = next;
    }

    private static void advance(Track tr, float dt) {
        tr.t += dt;
        switch (tr.state) {
            case CHARGE -> {
                float base = lerp(tr.from, RAISE_DEG, ease(tr.t / RAISE_TICKS));
                // 举起完成后轻微颤抖（淡入，避免跳变）
                float amp = TREMOR_DEG * clamp((tr.t - RAISE_TICKS) / 4f, 0f, 1f);
                tr.theta = base + amp * (float) Math.sin(tr.t * 1.3f);
            }
            case SNAP -> {
                float u = clamp(tr.t / SNAP_TICKS, 0f, 1f);
                tr.theta = lerp(tr.from, SNAP_DEG, u * u);   // 先慢后快的劈下
                if (tr.t >= SNAP_TICKS) {
                    tr.state = State.HOLD;
                    tr.t = 0f;
                    tr.from = SNAP_DEG;
                    tr.theta = SNAP_DEG;
                }
            }
            case HOLD -> {
                tr.theta = SNAP_DEG;
                if (tr.t >= HOLD_TICKS) {
                    toState(tr, State.RECOVER);
                }
            }
            case RECOVER -> {
                tr.theta = lerp(tr.from, 0f, ease(tr.t / RECOVER_TICKS));
                if (tr.t >= RECOVER_TICKS) {
                    tr.state = State.IDLE;
                    tr.theta = 0f;
                }
            }
            default -> tr.theta = 0f;
        }
    }

    private static float ease(float u) {
        float c = clamp(u, 0f, 1f);
        return 0.5f - 0.5f * (float) Math.cos(Math.PI * c);
    }

    private static float lerp(float a, float b, float u) {
        return a + (b - a) * u;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
