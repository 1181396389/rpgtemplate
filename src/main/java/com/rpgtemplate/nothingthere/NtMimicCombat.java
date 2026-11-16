package com.rpgtemplate.nothingthere;
import net.minecraft.world.item.ItemStack;
import com.rpgtemplate.forge.util.ItemNbt;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 殷红迷雾·原型拟态刀的持有者机制（设计见 nt-sword/DESIGN.md 第 8 节）。
 *
 * <p>所有状态都存在持有者的持久化 NBT 里（血雾、强壮、护盾、技能冷却），
 * 掉落物上的流血层数存在 {@link #BLEEDS} 中（服务端内存）。
 *
 * <p>占位数值全部集中在下方常量里，确认后改这里即可。
 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class NtMimicCombat {
    // ---- 占位数值（DESIGN 8.5） ----
    static final int MIST_MAX = 5;
    /** 左键三段：空闲超过该 tick 数则从第一段重新开始。 */
    static final long COMBO_WINDOW = 40L;
    /** 三段伤害倍率：左轻击 100%、右重击 150%、突刺重击 200%。 */
    static final double[] COMBO_MULT = {1.0, 1.5, 2.0};
    static final double MIST_PER_LAYER = 0.25;      // 血雾：攻击威力 +X/层（25%）
    static final double STRENGTH_PER_LAYER = 0.50;  // 强壮：攻击力 +X/层（50%，层数无上限）
    static final int STRENGTH_GAIN = 3;             // 屏息凝神：获得的强壮层数
    static final double NO_RESIST_MULT = 2.0;       // 对无抗性目标的伤害倍数
    static final int ANTI_RESIST_PERIOD = 10;       // 去除抗性的周期（tick）
    static final double ANTI_RESIST_RADIUS = 8.0;   // 去除抗性的范围（格）
    static final int BLEED_DELAY = 600;             // 流血延迟结算（tick，30 秒，从第一层算起，结算后清空）
    static final double BLEED_PCT = 0.02;           // 结算时每层：目标最大生命的 2%（百分比）
    static final int CLEANSE_PERIOD = 60;           // 解放后解除负面效果的周期（tick）
    static final double LIBERATED_INDEPENDENT = 5.0;
    static final double LIBERATED_CRIT = 3.0;       // 固定暴击，暴击伤害 3 倍
    static final double LIBERATED_DODGE_PARRY = 0.60;
    static final double LIBERATED_SHIELD = 1000.0;  // 解放时获得的护盾
    static final double SHIELD_MAX = 1000.0;        // 护盾上限（所有来源合计）
    static final int SHIELD_DECAY_DELAY = 200;      // 脱战后多久开始衰减（tick，10 秒）
    static final double SHIELD_DECAY_PER_SEC = 10.0; // 衰减速度（点/秒，占位：缓慢）
    static final double LIBERATED_EQUIV_LEVEL = 60.0; // 解放后物理免疫按 60 级计算（完全免疫）
    static final int DODGE_DECAY_HITS_PER_SEC = 1;  // 脱战后闪避成长每秒衰减的命中次数（占位：每秒 -1%）
    static final double GUDEBAI_MULT = 2.0;
    static final int SKILL_DURATION = 1200;         // 技能持续（tick，1 分钟）
    static final int SKILL_COOLDOWN = 200;          // 技能冷却（tick，10 秒）
    static final int BREATH_COOLDOWN = 100;         // 屏息凝神冷却（tick，5 秒）
    static final double PHYSICAL_PER_LEVEL = 0.025; // 与 NT 物理免疫相同
    static final double PHYSICAL_MAX = 1.0;
    // ---- 血雾弥漫（DESIGN 第 9 节，占位） ----
    static final int BM_COOLDOWN = 400;
    static final double BM_RADIUS = 3.0;            // 范围（格，近）
    static final double BM_HIT_BASE = 1000.0;       // 每段固定伤害（6 段合计 6000）
    static final double BM_HIT_MAX_PCT = 0.10;      // 6 段合计再加目标最大生命的 10%（每段各占 1/6）
    static final int BM_BLEED_LAYERS = 3;           // 每段每个目标施加
    static final int BM_DURATION = 40;
    static final int[] BM_HIT_TICKS = {14, 20, 26, 30, 34, 38};  // 与动作关键帧对齐
    // ---- 尸横遍野（DESIGN 第 10 节，占位） ----
    static final int CORPSE_COOLDOWN = 400;
    static final int CORPSE_ANIM_TICKS = 20;        // 尸横遍野动作长度（与 nt_mimic_corpses.json 一致）
    static final int CS_HIT_TICK = 12;              // 横向斩击的命中帧（与动作对齐）
    static final double CORPSE_RADIUS = 2.0;        // 范围（格，1~2 格）
    static final double CORPSE_DAMAGE = 4.0;        // 每个目标的伤害（低，主要靠斩杀和流血）
    static final double EXECUTE_RATIO = 0.30;       // 斩杀：目标生命不高于最大生命的 30% 时直接斩杀
    static final float EXECUTE_DAMAGE = 1.0e9f;     // 斩杀：极大伤害，等同直接死亡（仍会触发茧、无敌等死亡前的判定）
    static final int CORPSE_BLEED_LAYERS = 15;      // 每个目标施加

    static final String KEY_MIST = "rpgtemplate:mimic_mist";
    static final String KEY_STRENGTH = "rpgtemplate:mimic_strength";
    static final String KEY_SHIELD = "rpgtemplate:mimic_shield";
    static final String KEY_LIB_FLAG = "rpgtemplate:mimic_lib_flag";
    static final String KEY_SEL = "rpgtemplate:mimic_sel";
    static final String KEY_EGO = "rpgtemplate:mimic_ego"; // E.G.O展现中（死亡或换下拟态剑时清除）
    static final String KEY_COMBO_STEP = "rpgtemplate:mimic_combo_step";
    static final String KEY_COMBO_TICK = "rpgtemplate:mimic_combo_tick";
    static final String KEY_COMBO_MULT = "rpgtemplate:mimic_combo_mult";
    static final String KEY_ATTACK_CNT = "rpgtemplate:mimic_attack_cnt";
    static final String KEY_BM_START = "rpgtemplate:mimic_bm_start";
    static final String KEY_BM_NEXT = "rpgtemplate:mimic_bm_next";
    static final String KEY_CS_START = "rpgtemplate:mimic_cs_start";
    static final String KEY_CA_START = "rpgtemplate:mimic_ca_start";
    static final String KEY_COMBAT_TICK = "rpgtemplate:mimic_combat_tick"; // 最近一次造成或受到伤害的时刻

    /** 技能：平A 归类，按键待定。 */
    enum Skill {
        GUDEBAI("咕德拜"), HELLO("哈喽？"), SHELL("躯壳"), BREATH("屏息凝神"), BLOOD_MIST("血雾弥漫"), CORPSES("尸横遍野"), EGO("E.G.O展现");

        final String label;

        Skill(String label) {
            this.label = label;
        }
    }

    /** 服务端 ticking 标记：流血结算造成的伤害不触发攻击逻辑。 */
    static boolean ticking;
    /** 血雾弥漫的段伤害：不再每段都加流血层数。 */
    static boolean skillHit;

    private static final Map<LivingEntity, Bleed> BLEEDS = new WeakHashMap<>();

    private static final class Bleed {
        final ServerPlayer source;
        final long settleAt;
        int layers;

        Bleed(ServerPlayer source, long settleAt) {
            this.source = source;
            this.settleAt = settleAt;
            this.layers = 0;
        }
    }

    private NtMimicCombat() {
    }

    // ---- 状态读写 ----

    static boolean holding(LivingEntity e) {
        return e instanceof ServerPlayer p && NtMimicBlade.isBlade(p.getItemInHand(InteractionHand.MAIN_HAND));
    }

    static int mist(ServerPlayer p) {
        return p.getPersistentData().getInt(KEY_MIST);
    }

    static int strength(ServerPlayer p) {
        return p.getPersistentData().getInt(KEY_STRENGTH);
    }

    static double shield(ServerPlayer p) {
        return p.getPersistentData().getDouble(KEY_SHIELD);
    }

    /** E.G.O展现是否进行中（由 E.G.O展现 技能开启）。 */
    static boolean egoActive(ServerPlayer p) {
        return p.getPersistentData().getBoolean(KEY_EGO);
    }

    /** 解放 = 持剑且 E.G.O展现 进行中。 */
    static boolean liberated(ServerPlayer p) {
        return holding(p) && egoActive(p);
    }

    /** 技能列表：六个基础技能；血雾层数达到 MIST_MAX 时追加 E.G.O展现。 */
    static List<Skill> skillList(ServerPlayer p) {
        List<Skill> list = new ArrayList<>(Arrays.asList(Skill.values()));
        if (mist(p) < MIST_MAX) {
            list.remove(Skill.EGO);
        }
        return list;
    }

    static boolean active(ServerPlayer p, Skill skill) {
        return p.serverLevel().getGameTime() < p.getPersistentData().getLong(untilKey(skill));
    }

    static String untilKey(Skill skill) {
        return "rpgtemplate:mimic_until_" + skill.name();
    }

    static String cdKey(Skill skill) {
        return "rpgtemplate:mimic_cd_" + skill.name();
    }

    // ---- 技能使用（由 NtMimicBlade.use 调用） ----

    /** 右键：释放当前技能；潜行右键：切换技能。 */
    /** 剩余冷却（秒，向上取整，精确到秒）；0 表示就绪。 */
    static long cooldownSeconds(ServerPlayer p, Skill skill, long now) {
        long left = p.getPersistentData().getLong(cdKey(skill)) - now;
        return left <= 0L ? 0L : (left + 19L) / 20L;
    }

    /** 技能状态文字：“就绪”或“冷却 N 秒”。 */
    static String statusText(ServerPlayer p, Skill skill, long now) {
        if (skill == Skill.EGO) {
            return egoActive(p) ? "展现中" : "就绪";
        }
        long secs = cooldownSeconds(p, skill, now);
        return secs <= 0L ? "就绪" : "冷却 " + secs + " 秒";
    }

    /** 持剑时每秒在 action bar 显示当前技能与冷却。 */
    static void showSkillBar(ServerPlayer p, long now) {
        if (now % 20L != 0L) {
            return;
        }
        List<Skill> all = skillList(p);
        Skill skill = all.get(p.getPersistentData().getInt(KEY_SEL) % all.size());
        p.displayClientMessage(Component.literal("拟态技能：" + skill.label + " · " + statusText(p, skill, now)), true);
    }

    static void use(ServerPlayer p, boolean shift) {
        if (!holding(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData();
        List<Skill> all = skillList(p);
        int sel = tag.getInt(KEY_SEL) % all.size();
        long now = p.serverLevel().getGameTime();
        if (shift) {
            sel = (sel + 1) % all.size();
            tag.putInt(KEY_SEL, sel);
            p.sendSystemMessage(Component.literal("拟态技能：" + all.get(sel).label + "（" + statusText(p, all.get(sel), now) + "）"));
            return;
        }
        Skill skill = all.get(sel);
        if (now < tag.getLong(cdKey(skill))) {
            p.sendSystemMessage(Component.literal(skill.label + " 冷却中，还需 " + cooldownSeconds(p, skill, now) + " 秒"));
            return;
        }
        if (skill == Skill.EGO) {
            if (egoActive(p)) {
                p.sendSystemMessage(Component.literal("E.G.O展现 已在进行中"));
                return;
            }
            tag.putBoolean(KEY_EGO, true);
            NtMimicFx.cast(p, BM_RADIUS);
        } else if (skill == Skill.BREATH) {
            tag.putInt(KEY_STRENGTH, strength(p) + STRENGTH_GAIN);
            tag.putLong(cdKey(skill), now + BREATH_COOLDOWN);
        } else if (skill == Skill.CORPSES) {
            tag.putLong(cdKey(skill), now + CORPSE_COOLDOWN);
            tag.putLong(KEY_CS_START, now); // 斩击帧结算伤害
            tag.putLong(KEY_CA_START, now); // 动作开始（同步给客户端）
        } else if (skill == Skill.BLOOD_MIST) {
            tag.putLong(KEY_BM_START, now);
            tag.putInt(KEY_BM_NEXT, 0);
            tag.putLong(cdKey(skill), now + BM_COOLDOWN);
            NtMimicFx.cast(p, BM_RADIUS);
        } else {
            tag.putLong(untilKey(skill), now + SKILL_DURATION);
            tag.putLong(cdKey(skill), now + SKILL_COOLDOWN);
        }
        p.sendSystemMessage(Component.literal("拟态技能：" + skill.label));
    }

    /** 左键攻击（持剑）：三段循环 左轻击 → 右重击 → 突刺重击；空闲超过 COMBO_WINDOW 则从第一段重新开始。 */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !holding(p)) {
            return;
        }
        CompoundTag tag = p.getPersistentData();
        long now = p.serverLevel().getGameTime();
        boolean inWindow = tag.contains(KEY_COMBO_TICK) && now - tag.getLong(KEY_COMBO_TICK) <= COMBO_WINDOW;
        int step = inWindow ? (tag.getInt(KEY_COMBO_STEP) + 1) % COMBO_MULT.length : 0;
        tag.putInt(KEY_COMBO_STEP, step);
        tag.putLong(KEY_COMBO_TICK, now);
        tag.putDouble(KEY_COMBO_MULT, COMBO_MULT[step]);
        int count = (tag.getInt(KEY_ATTACK_CNT) + 1) & 0x3FFFFFF;
        tag.putInt(KEY_ATTACK_CNT, count);
        p.getEntityData().set(NtBody.DATA_ATTACK_ANIM, (count << 2) | (step + 1));
    }

    // ---- 伤害：攻击侧（最先执行，改写攻击伤害） ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurtAttacker(LivingIncomingDamageEvent event) {
        if (ticking || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Entity src = event.getSource().getEntity();
        if (!(src instanceof ServerPlayer attacker) || attacker == victim || !holding(attacker)) {
            return;
        }
        double mult = 1.0;
        if (victim.getEffect(NtForms.EFFECT_RESIST) == null) {
            mult *= NO_RESIST_MULT;
        }
        mult *= 1.0 + mist(attacker) * MIST_PER_LAYER;
        mult *= 1.0 + strength(attacker) * STRENGTH_PER_LAYER;
        if (active(attacker, Skill.GUDEBAI)) {
            mult *= GUDEBAI_MULT;
        }
        if (liberated(attacker)) {
            mult *= LIBERATED_INDEPENDENT * LIBERATED_CRIT;
        }
        if (!skillHit && "player".equals(event.getSource().getMsgId())) {
            double combo = attacker.getPersistentData().getDouble(KEY_COMBO_MULT);
            mult *= combo > 0.0 ? combo : 1.0;
        }
        float out = (float) (event.getAmount() * mult);
        event.setAmount(out);
        if (!skillHit) {
            addBleed(victim, attacker, 1);
        }
        if (active(attacker, Skill.HELLO)) {
            attacker.heal(out);
        }
    }

    private static void addBleed(LivingEntity victim, ServerPlayer source, int layers) {
        if (!victim.isAlive()) {
            return;
        }
        source.getEntityData().set(NtBody.DATA_BLEED_CNT, (NtBody.bleedCount(source) + 1) & 0x3FFFFFFF);
        Bleed bleed = BLEEDS.get(victim);
        if (bleed == null) {
            long now = source.serverLevel().getGameTime();
            bleed = new Bleed(source, now + BLEED_DELAY);
            BLEEDS.put(victim, bleed);
        }
        bleed.layers += layers;
    }

    // ---- 血雾弥漫：多段范围伤害 ----

    /** 每 tick（持剑时）：推进血雾弥漫的段，到点即结算。 */
    static void tickBloodMist(ServerPlayer p, long now) {
        CompoundTag tag = p.getPersistentData();
        if (!tag.contains(KEY_BM_START)) {
            return;
        }
        long start = tag.getLong(KEY_BM_START);
        if (start < 0L) {
            return;
        }
        long age = now - start;
        int next = tag.getInt(KEY_BM_NEXT);
        while (next < BM_HIT_TICKS.length && age >= BM_HIT_TICKS[next]) {
            bloodMistHit(p, next);
            next++;
        }
        tag.putInt(KEY_BM_NEXT, next);
        if (age >= BM_DURATION) {
            tag.putLong(KEY_BM_START, -1L);
        }
    }

    /** 每个玩家每 tick：把血雾/尸横遍野的动作标记同步给所有客户端（不要求持剑）。 */
    @SubscribeEvent
    public static void onPlayerAnimTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        syncAnim(p, p.serverLevel().getGameTime());
    }

    static void syncAnim(ServerPlayer p, long now) {
        CompoundTag tag = p.getPersistentData();
        long bm = tag.contains(KEY_BM_START) ? tag.getLong(KEY_BM_START) : -1L;
        long ca = tag.contains(KEY_CA_START) ? tag.getLong(KEY_CA_START) : -1L;
        boolean mist = bm >= 0L && now - bm < BM_DURATION;
        boolean corpse = ca >= 0L && now - ca < CORPSE_ANIM_TICKS;
        p.getEntityData().set(NtBody.DATA_MIST_ANIM, mist);
        p.getEntityData().set(NtBody.DATA_CORPSE_ANIM, corpse);
        p.getEntityData().set(NtBody.DATA_MIST_LAYERS, mist(p));
        p.getEntityData().set(NtBody.DATA_EGO, egoActive(p));
    }

    /** 每 tick（持剑时）：尸横遍野到斩击帧时结算一次。 */
    static void tickCorpse(ServerPlayer p, long now) {
        CompoundTag tag = p.getPersistentData();
        if (!tag.contains(KEY_CS_START)) {
            return;
        }
        long start = tag.getLong(KEY_CS_START);
        if (start < 0L || now - start < CS_HIT_TICK) {
            return;
        }
        tag.putLong(KEY_CS_START, -1L);
        corpseStrike(p);
    }

    /** 尸横遍野：范围内所有目标受到一次大量伤害，并施加 15 层流血。 */
    private static void corpseStrike(ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(CORPSE_RADIUS);
        List<LivingEntity> targets = p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive());
        for (LivingEntity e : targets) {
            addBleed(e, p, CORPSE_BLEED_LAYERS);
            skillHit = true; // 不再额外加 1 层
            boolean execute = e.getHealth() <= e.getMaxHealth() * EXECUTE_RATIO;
            try {
                TargetUtil.hurt(e, ModDamageTypes.technique(p), execute ? EXECUTE_DAMAGE : (float) CORPSE_DAMAGE);
            } finally {
                skillHit = false;
            }
        }
    }

    /** 第 index 段：范围内每个目标受到一次段伤害，并每段都加 3 层流血。 */
    private static void bloodMistHit(ServerPlayer p, int index) {
        AABB box = p.getBoundingBox().inflate(BM_RADIUS);
        List<LivingEntity> targets = p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive());
        for (LivingEntity e : targets) {
            addBleed(e, p, BM_BLEED_LAYERS);
            skillHit = true; // 段伤害不再额外加 1 层（上面已经加了 3 层）
            try {
                float amount = (float) (BM_HIT_BASE + e.getMaxHealth() * BM_HIT_MAX_PCT / BM_HIT_TICKS.length);
                TargetUtil.hurt(e, ModDamageTypes.technique(p), amount);
            } finally {
                skillHit = false;
            }
        }
        NtMimicFx.hit(p, index, BM_HIT_TICKS.length, BM_RADIUS, targets);
    }

    // ---- 伤害：受击侧（最后执行，躯壳 / 解放减伤 / 护盾） ----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurtVictim(LivingIncomingDamageEvent event) {
        if (ticking || event.getAmount() <= 0.0f) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer me) || !holding(me)) {
            return;
        }
        Entity src = event.getSource().getEntity();
        if (src == me) {
            return;
        }
        boolean physical = NothingThereStats.isPhysical(event.getSource());
        if (active(me, Skill.SHELL) && !physical) {
            event.setAmount(0.0f);
            return;
        }
        float amount = event.getAmount();
        if (liberated(me) && physical) {
            // 解放后视为 60 级，物理免疫为 100%（与持有者是否为 NT 无关）
            double reduction = Math.min(PHYSICAL_MAX, LIBERATED_EQUIV_LEVEL * PHYSICAL_PER_LEVEL);
            amount = (float) (amount * (1.0 - reduction));
        }
        CompoundTag tag = me.getPersistentData();
        double shield = tag.getDouble(KEY_SHIELD);
        if (shield > 0.0) {
            double absorbed = Math.min(shield, amount);
            tag.putDouble(KEY_SHIELD, shield - absorbed);
            amount -= (float) absorbed;
        }
        event.setAmount(Math.max(0.0f, amount));
    }

    // ---- 击杀：血雾 ----

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer dead) {
            resetState(dead);
        }
        Entity src = event.getSource().getEntity();
        if (src instanceof ServerPlayer killer && killer != victim && holding(killer)) {
            CompoundTag tag = killer.getPersistentData();
            int m = tag.getInt(KEY_MIST);
            if (m < MIST_MAX) {
                tag.putInt(KEY_MIST, m + 1);
            }
        }
    }

    /** 拟态攻击（持剑者造成的伤害）：一无所有的免伤（物理免疫、死亡记忆、咕德拜减伤）不生效。 */
    static boolean fromMimic(DamageSource source) {
        Entity src = source.getEntity();
        return src instanceof ServerPlayer p && holding(p);
    }

    /** 死亡清空战斗状态（DESIGN 8.5 第 4 项，假设）。 */
    static void resetState(ServerPlayer p) {
        CompoundTag tag = p.getPersistentData();
        tag.putInt(KEY_MIST, 0);
        tag.putInt(KEY_STRENGTH, 0);
        tag.putDouble(KEY_SHIELD, 0.0);
        tag.putBoolean(KEY_LIB_FLAG, false);
        tag.putBoolean(KEY_EGO, false);
    }

    // ---- 每 tick：去除抗性、解放检查、解除负面、流血结算 ----

    /** 战斗时刻：玩家造成或受到伤害时记录（流血结算不算）。 */
    @SubscribeEvent
    public static void trackCombat(LivingIncomingDamageEvent event) {
        if (ticking || event.getAmount() <= 0.0f) {
            return;
        }
        long now = event.getEntity().level().getGameTime();
        if (event.getEntity() instanceof ServerPlayer v) {
            v.getPersistentData().putLong(KEY_COMBAT_TICK, now);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer a) {
            a.getPersistentData().putLong(KEY_COMBAT_TICK, now);
        }
    }

    /** 闪避成长脱战衰减：脱战超过 SHIELD_DECAY_DELAY 后，每秒减少 DODGE_DECAY_HITS_PER_SEC 次命中，直到回到基础值。 */
    static void tickDodgeDecay(ServerPlayer p, long now) {
        if (now % 20L != 0L || now - p.getPersistentData().getLong(KEY_COMBAT_TICK) < SHIELD_DECAY_DELAY) {
            return;
        }
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        int hits = ItemNbt.getInt(held, NtMimicBlade.HITS_KEY);
        if (hits > 0) {
            ItemNbt.putInt(held, NtMimicBlade.HITS_KEY, Math.max(0, hits - DODGE_DECAY_HITS_PER_SEC));
        }
    }

    /** 护盾脱战衰减：脱战超过 SHIELD_DECAY_DELAY 后每秒减少 SHIELD_DECAY_PER_SEC。 */
    static void tickShield(ServerPlayer p, long now) {
        CompoundTag tag = p.getPersistentData();
        double shield = tag.getDouble(KEY_SHIELD);
        if (shield <= 0.0 || now - tag.getLong(KEY_COMBAT_TICK) < SHIELD_DECAY_DELAY) {
            return;
        }
        tag.putDouble(KEY_SHIELD, Math.max(0.0, shield - SHIELD_DECAY_PER_SEC / 20.0));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        tickShield(p, now);
        if (!holding(p)) {
            p.getPersistentData().putBoolean(KEY_EGO, false); // 换下拟态剑：E.G.O展现结束
            return;
        }
        showSkillBar(p, now);
        tickDodgeDecay(p, now);
        tickBloodMist(p, now);
        tickCorpse(p, now);
        CompoundTag tag = p.getPersistentData();

        boolean lib = liberated(p);
        boolean flag = tag.getBoolean(KEY_LIB_FLAG);
        if (lib && !flag) {
            tag.putDouble(KEY_SHIELD, Math.min(SHIELD_MAX, shield(p) + LIBERATED_SHIELD));
            tag.putBoolean(KEY_LIB_FLAG, true);
        } else if (!lib && flag) {
            tag.putBoolean(KEY_LIB_FLAG, false);
        }

        if (now % ANTI_RESIST_PERIOD == 0L) {
            AABB box = p.getBoundingBox().inflate(ANTI_RESIST_RADIUS);
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                if (e.getEffect(NtForms.EFFECT_RESIST) != null) {
                    e.removeEffect(NtForms.EFFECT_RESIST);
                }
            }
        }

        if (lib && now % CLEANSE_PERIOD == 0L) {
            for (var inst : new java.util.ArrayList<>(p.getActiveEffects())) {
                if (!inst.getEffect().value().isBeneficial()) {
                    p.removeEffect(inst.getEffect());
                    break;
                }
            }
        }
    }

    /** 流血：延迟结算。到点后按层数一次性结算（每层 BLEED_DAMAGE），然后清空。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BLEEDS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Bleed>> it = BLEEDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Bleed> entry = it.next();
            LivingEntity victim = entry.getKey();
            Bleed bleed = entry.getValue();
            if (!victim.isAlive() || bleed.layers <= 0) {
                it.remove();
                continue;
            }
            long now = bleed.source.serverLevel().getGameTime();
            if (now < bleed.settleAt) {
                continue;
            }
            it.remove();
            ticking = true;
            try {
                TargetUtil.hurt(victim, ModDamageTypes.technique(bleed.source), (float) (victim.getMaxHealth() * BLEED_PCT * bleed.layers));
            } finally {
                ticking = false;
            }
        }
    }
}
