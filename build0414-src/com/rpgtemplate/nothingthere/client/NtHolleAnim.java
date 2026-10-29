package com.rpgtemplate.nothingthere.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * Holle 施法动作（客户端）：左臂举过头顶，右腿前抬、髋部下压，近似单膝下跪的姿势，发射后收回。
 *
 * 模型的腿与手臂都是刚性的（一个部件），无法真正弯膝，所以"下跪"是用右腿前抬 + 压低髋部近似的。
 * 与 NtGoodbyeAnim 相同：服务端的同步标志上升沿开始计时，然后在本地按 DURATION 播放。
 * 关节角度保持在人体范围内：肩部上举不超过 180°，腿部前摆不超过约 70°。
 */
public final class NtHolleAnim {
    /** 整段动作：举手（0..6）、保持（6..16）、收回（16..24）。 */
    public static final int DURATION = 24;

    private static final float[] KEY_TICK = {0f, 6f, 16f, 24f};
    /** 左臂抬起角（度）：0 = 垂下，165 = 举过头顶（左臂前举）。 */
    private static final float[] KEY_THETA = {0f, 165f, 165f, 0f};
    /** 腿部姿势强度 0..1（同一时间轴）。 */
    private static final float[] KEY_LEG = {0f, 1f, 1f, 0f};

    /** 右腿（前腿）前抬 ~70°，左腿（后腿）后撤 ~17°，髋部压低 5 像素（1 像素 = 1/16 方块）。 */
    public static final float FRONT_LEG_RAD = 1.22f;
    public static final float BACK_LEG_RAD = -0.30f;
    public static final float CROUCH_PX = 5f;

    private static final Map<UUID, Boolean> LAST_FLAG = new HashMap<>();
    private static final Map<UUID, Float> START = new HashMap<>();

    private NtHolleAnim() {
    }

    /** 每次渲染时调用一次。flag = 同步的 Holle 施法标志，now = 游戏时间（tick）。 */
    public static void observe(Player player, boolean flag, float now) {
        UUID id = player.m_20148_();
        boolean before = LAST_FLAG.getOrDefault(id, Boolean.FALSE);
        if (flag && !before) {
            START.put(id, now);
        }
        LAST_FLAG.put(id, flag);
    }

    /** 从施法开始经过的 tick 数；动作未进行时返回 -1。 */
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

    /** 左臂抬起角（度），t 为施法后经过的 tick。 */
    public static float theta(float t) {
        return curve(t, KEY_THETA);
    }

    /** 腿部姿势强度 0..1。 */
    public static float legFraction(float t) {
        return curve(t, KEY_LEG);
    }

    private static float curve(float t, float[] values) {
        for (int i = 0; i < KEY_TICK.length - 1; i++) {
            if (t <= KEY_TICK[i + 1]) {
                float span = KEY_TICK[i + 1] - KEY_TICK[i];
                float u = span <= 0f ? 1f : clamp01((t - KEY_TICK[i]) / span);
                float e = 0.5f - 0.5f * (float) Math.cos(Math.PI * u);
                return values[i] + (values[i + 1] - values[i]) * e;
            }
        }
        return values[values.length - 1];
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
