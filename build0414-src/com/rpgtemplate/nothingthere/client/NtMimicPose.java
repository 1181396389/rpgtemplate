package com.rpgtemplate.nothingthere.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rpgtemplate.nothingthere.NtBody;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 拟态动作（血雾弥漫 / 尸横遍野）的客户端姿势。关键帧来自 assets/rpgtemplate/mimic_poses/*.json（不放在 animations/ 下：GeckoLib 会把该目录下所有 JSON 当作它的动画格式读取，导致崩溃）。
 *
 * 与 NtGoodbyeLean 相同的思路：服务端通过同步数据告诉所有客户端动作是否进行中；
 * 客户端在动作开始的上升沿记下开始时间，然后按关键帧插值，在 setupAnim 的 TAIL 写入姿势。
 * 动作期间直接覆盖手臂、躯干、腿的旋转（vanilla 每帧都会重新设置这些角度）。
 */
public final class NtMimicPose {
    private static final String[] PARTS = {"rightArm", "leftArm", "torso", "rightLeg", "leftLeg"};
    private static final float[] ZERO = {0f, 0f, 0f};

    private static final Anim MIST = load("nt_mimic_blood_mist.json");
    private static final Anim CORPSE = load("nt_mimic_corpses.json");
    /** 左键三段：下标 0 左轻击、1 右重击、2 突刺重击。 */
    private static final Anim[] ATTACK = {
        load("nt_mimic_attack_light.json"), load("nt_mimic_attack_heavy.json"), load("nt_mimic_attack_thrust.json")};

    private static final Map<UUID, Float> MIST_START = new HashMap<>();
    private static final Map<UUID, Float> CORPSE_START = new HashMap<>();
    private static final Map<UUID, Integer> ATTACK_LAST = new HashMap<>();
    private static final Map<UUID, Float> ATTACK_START = new HashMap<>();

    private NtMimicPose() {
    }

    /** 一个动作的关键帧。 */
    private static final class Anim {
        final float length;
        final List<Frame> frames;

        Anim(float length, List<Frame> frames) {
            this.length = length;
            this.frames = frames;
        }
    }

    private static final class Frame {
        final float tick;
        final String easing;
        final Map<String, float[]> parts = new HashMap<>();

        Frame(float tick, String easing) {
            this.tick = tick;
            this.easing = easing;
        }
    }

    /** TAIL of setupAnim: 写入本帧的拟态动作姿势（若正在播放）。 */
    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        float now = (float) player.m_9236_().m_46467_() + partial;
        applyOne(model, player, NtBody.mistAnimPending(player), MIST_START, MIST, now);
        applyOne(model, player, NtBody.corpseAnimPending(player), CORPSE_START, CORPSE, now);
        applyAttack(model, player, now);
    }

    private static void applyOne(PlayerModel<?> model, Player player, boolean flag,
                                 Map<UUID, Float> starts, Anim anim, float now) {
        if (anim == null) {
            return;
        }
        UUID id = player.m_20148_();
        if (!flag) {
            starts.remove(id);
            return;
        }
        Float start = starts.get(id);
        if (start == null) {
            start = now;
            starts.put(id, start);
        }
        float t = now - start;
        if (t < 0f || t > anim.length) {
            return;
        }
        ModelPart[] parts = {model.f_102811_, model.f_102812_, model.f_102810_, model.f_102813_, model.f_102814_};
        for (int i = 0; i < PARTS.length; i++) {
            float[] v = value(anim, PARTS[i], t);
            ModelPart part = parts[i];
            part.f_104203_ = v[0]; // xRot = pitch
            part.f_104204_ = v[1]; // yRot = yaw
            part.f_104205_ = v[2]; // zRot = roll
        }
    }

    /** 攻击段播放中的动画与段内时间（tick）。 */
    private static final class Playing {
        final Anim anim;
        final float t;

        Playing(Anim anim, float t) {
            this.anim = anim;
            this.t = t;
        }
    }

    /** 左键三段攻击：同步值变化即从头播放对应段；不在播放中（或已播完）返回 null。 */
    private static Playing attackPlaying(Player player, float now) {
        UUID id = player.m_20148_();
        int code = NtBody.attackAnimValue(player);
        if (code == 0) {
            ATTACK_LAST.remove(id);
            ATTACK_START.remove(id);
            return null;
        }
        Integer last = ATTACK_LAST.get(id);
        if (last == null || last != code) {
            ATTACK_LAST.put(id, code);
            ATTACK_START.put(id, now);
        }
        int step = (code & 3) - 1;
        if (step < 0 || step >= ATTACK.length || ATTACK[step] == null) {
            return null;
        }
        float t = now - ATTACK_START.get(id);
        if (t < 0f || t > ATTACK[step].length) {
            return null;
        }
        return new Playing(ATTACK[step], t);
    }

    /** TAIL of setupAnim 用：写入身体模型的攻击姿势（播放结束后保持静止，直到下一次攻击）。 */
    private static void applyAttack(PlayerModel<?> model, Player player, float now) {
        Playing playing = attackPlaying(player, now);
        if (playing == null) {
            return;
        }
        ModelPart[] parts = {model.f_102811_, model.f_102812_, model.f_102810_, model.f_102813_, model.f_102814_};
        for (int i = 0; i < PARTS.length; i++) {
            float[] v = value(playing.anim, PARTS[i], playing.t);
            ModelPart part = parts[i];
            part.f_104203_ = v[0];
            part.f_104204_ = v[1];
            part.f_104205_ = v[2];
        }
    }

    /**
     * 第一人称用：右臂当前攻击姿势 {pitch, yaw, roll}（弧度）。不在攻击中返回 null。
     * 第一人称不走 setupAnim，所以由 NtFirstPerson 直接取值套到手上。
     */
    public static float[] attackRightArm(Player player, float partial) {
        float now = (float) player.m_9236_().m_46467_() + partial;
        Playing playing = attackPlaying(player, now);
        return playing == null ? null : value(playing.anim, "rightArm", playing.t);
    }

    /** t（tick）时某部位的 {pitch, yaw, roll}：在相邻两帧之间按下一帧的缓动插值。 */
    private static float[] value(Anim anim, String part, float t) {
        Frame prev = null;
        Frame next = null;
        for (Frame f : anim.frames) {
            if (f.tick <= t) {
                prev = f;
            } else {
                next = f;
                break;
            }
        }
        if (prev == null) {
            return ZERO;
        }
        if (next == null) {
            return vec(prev, part);
        }
        float span = next.tick - prev.tick;
        float k = span <= 0f ? 1f : (t - prev.tick) / span;
        k = ease(next.easing, k);
        float[] a = vec(prev, part);
        float[] b = vec(next, part);
        return new float[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static float[] vec(Frame f, String part) {
        return f.parts.getOrDefault(part, ZERO);
    }

    private static float ease(String easing, float k) {
        k = Math.max(0f, Math.min(1f, k));
        if ("easeIn".equals(easing)) {
            return k * k;
        }
        if ("easeOut".equals(easing)) {
            return 1f - (1f - k) * (1f - k);
        }
        if ("easeInOut".equals(easing)) {
            return k * k * (3f - 2f * k);
        }
        return k;
    }

    /** 读取 classpath 中的关键帧 JSON（与 BuildMotion 相同的读取方式）。失败时返回 null（不播放）。 */
    private static Anim load(String file) {
        try (Reader reader = new InputStreamReader(
                NtMimicPose.class.getResourceAsStream("/assets/rpgtemplate/mimic_poses/" + file),
                StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            float length = root.get("length_ticks").getAsFloat();
            List<Frame> frames = new ArrayList<>();
            for (var el : root.getAsJsonArray("frames")) {
                JsonObject fo = el.getAsJsonObject();
                Frame f = new Frame(fo.get("tick").getAsFloat(),
                        fo.has("easing") ? fo.get("easing").getAsString() : "linear");
                JsonObject parts = fo.getAsJsonObject("parts");
                for (String name : parts.keySet()) {
                    JsonObject p = parts.getAsJsonObject(name);
                    f.parts.put(name, new float[] {
                        p.has("pitch") ? p.get("pitch").getAsFloat() : 0f,
                        p.has("yaw") ? p.get("yaw").getAsFloat() : 0f,
                        p.has("roll") ? p.get("roll").getAsFloat() : 0f});
                }
                frames.add(f);
            }
            return new Anim(length, frames);
        } catch (Exception e) {
            return null;
        }
    }
}
