package com.rpgtemplate.forge.skill;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 机制级技能（如：必死、绝对催眠、概念破防、削减生命上限等）的四档等级/属性对抗判定器。
 *
 * <p>判定结果由强至弱分为四档：
 * <ol>
 *   <li>{@link Tier#FULL} — 1. 完全生效</li>
 *   <li>{@link Tier#MAJOR} — 2. 显著干扰</li>
 *   <li>{@link Tier#MINOR} — 3. 微弱牵制</li>
 *   <li>{@link Tier#FAIL} — 4. 判定失败</li>
 * </ol>
 */
public final class RuleJudge {
    private static final String CUSTOM_RULE_LEVEL_KEY = "rpgtemplate.rule_level";
    private static final long NOTIFY_COOLDOWN_TICKS = 15L;
    private static final Map<UUID, Long> LAST_NOTIFY = new ConcurrentHashMap<>();
    private static final Map<LivingEntity, Long> LAST_VICTIM_NOTIFY = new WeakHashMap<>();

    private RuleJudge() {
    }

    public enum Tier {
        FULL("message.rpgtemplate.rule.full", ChatFormatting.GOLD),
        MAJOR("message.rpgtemplate.rule.major", ChatFormatting.AQUA),
        MINOR("message.rpgtemplate.rule.minor", ChatFormatting.YELLOW),
        FAIL("message.rpgtemplate.rule.fail", ChatFormatting.RED);

        private final String langKey;
        private final ChatFormatting color;

        Tier(String langKey, ChatFormatting color) {
            this.langKey = langKey;
            this.color = color;
        }

        public String langKey() {
            return this.langKey;
        }

        public ChatFormatting color() {
            return this.color;
        }
    }

    public record Result(Tier tier, int casterRuleLevel, int targetRuleLevel, int delta) {
    }

    /**
     * 对施放者与目标进行等级 + 模板品质规则加成 + 面板主属性修正的四档对抗判定。
     */
    public static Result evaluate(ServerPlayer caster, PlayerSystemData casterData,
                                  LivingEntity target, StatType affinity) {
        int casterLv = casterData == null ? 1 : Math.max(1, casterData.level());
        int casterBonus = qualityRuleBonus(casterData);
        int casterPower = casterLv + casterBonus;

        int targetPower = 1;
        int statShift = 0;
        if (target instanceof ServerPlayer targetPlayer && SystemDataManager.available(targetPlayer)) {
            PlayerSystemData targetData = SystemDataManager.get(targetPlayer);
            if (targetData.isActivated()) {
                int targetLv = Math.max(1, targetData.level());
                int targetBonus = qualityRuleBonus(targetData);
                targetPower = targetLv + targetBonus;
                if (casterData != null && affinity != null) {
                    double cStat = Math.max(1.0, StatCalculator.maxStat(
                        affinity, casterData, TemplateRegistry.get(), CoreConfig.get()));
                    double tStat = Math.max(1.0, StatCalculator.maxStat(
                        affinity, targetData, TemplateRegistry.get(), CoreConfig.get()));
                    double ratioLog = Math.log(cStat / tStat);
                    statShift = (int) Math.max(-8L, Math.min(8L, Math.round(ratioLog * 4.0)));
                }
            }
        } else if (target != null && target.getPersistentData().m_128441_(CUSTOM_RULE_LEVEL_KEY)) {
            targetPower = Math.max(1, target.getPersistentData().m_128451_(CUSTOM_RULE_LEVEL_KEY));
        }

        int effectiveCaster = Math.max(1, casterPower + statShift);
        int delta = effectiveCaster - targetPower;
        Tier tier;
        if (delta >= 0) {
            tier = Tier.FULL;
        } else if (delta >= -10) {
            tier = Tier.MAJOR;
        } else if (delta >= -25) {
            tier = Tier.MINOR;
        } else {
            tier = Tier.FAIL;
        }
        return new Result(tier, effectiveCaster, targetPower, delta);
    }

    /** 向施放者（及玩家目标）发送标准化的四档机制判定反馈，内置短间隔节流防止多段刷屏。 */
    public static void notify(ServerPlayer caster, LivingEntity target, String skillName, Result result) {
        if (caster == null || result == null) {
            return;
        }
        long now = caster.m_9236_().m_46467_();
        UUID cid = caster.m_20148_();
        long prev = LAST_NOTIFY.getOrDefault(cid, Long.MIN_VALUE / 2);
        if (now - prev >= NOTIFY_COOLDOWN_TICKS) {
            LAST_NOTIFY.put(cid, now);
            Component tierComp = Component.m_237115_(result.tier().langKey()).m_130940_(result.tier().color());
            Component msg = Component.m_237110_("message.rpgtemplate.skill.rule_result",
                skillName, tierComp, result.casterRuleLevel(), result.targetRuleLevel())
                .m_130940_(ChatFormatting.GRAY);
            Msg.actionBar(caster, msg);
        }
        if (target instanceof ServerPlayer victim && victim != caster && result.tier() != Tier.FULL) {
            long vPrev = LAST_VICTIM_NOTIFY.getOrDefault(victim, Long.MIN_VALUE / 2);
            if (now - vPrev >= NOTIFY_COOLDOWN_TICKS) {
                LAST_VICTIM_NOTIFY.put(victim, now);
                Component tierComp = Component.m_237115_(result.tier().langKey()).m_130940_(result.tier().color());
                Component msg = Component.m_237110_("message.rpgtemplate.skill.rule_result",
                    skillName, tierComp, result.casterRuleLevel(), result.targetRuleLevel())
                    .m_130940_(ChatFormatting.GRAY);
                Msg.actionBar(victim, msg);
            }
        }
    }

    private static int qualityRuleBonus(PlayerSystemData data) {
        if (data == null) {
            return 0;
        }
        return data.loadedTemplate()
            .flatMap(inst -> TemplateRegistry.get().find(inst.templateId()))
            .map(TemplateDefinition::quality)
            .map(q -> q.ruleBonus())
            .orElse(0);
    }
}
