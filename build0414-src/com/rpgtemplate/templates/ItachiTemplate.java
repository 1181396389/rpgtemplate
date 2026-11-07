package com.rpgtemplate.templates;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.fx.Fx;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.RuleJudge;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * SR 阶过渡模板：宇智波鼬（{@code rpgtemplate:itachi_1}，晓组织潜伏期·身患绝症的绝世幻术天才）。
 * <p>完美继承万花筒写轮眼才华与膏肓沉疴、瞳力失明磨损及甜食控/厌战一击制敌癖好。</p>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class ItachiTemplate {
    public static final String TEMPLATE_ID = "rpgtemplate:itachi_1";
    public static final String KEY_STRAIN = "it.strain";
    public static final String KEY_COUGH_ICD = "it.cough_icd";
    public static final String KEY_CROW_DODGE_ICD = "it.crow_icd";

    private static final Map<LivingEntity, BlackFlame> BLACK_FLAMES = new WeakHashMap<>();

    private static final class BlackFlame {
        final ServerPlayer caster;
        final float perTickDamage;
        int secondsLeft;

        BlackFlame(ServerPlayer caster, float perTickDamage, int secondsLeft) {
            this.caster = caster;
            this.perTickDamage = perTickDamage;
            this.secondsLeft = secondsLeft;
        }
    }

    private ItachiTemplate() {
    }

    private static void addMangekyoStrain(ServerPlayer player, PlayerSystemData data, double delta, long now) {
        double strain = Math.max(0.0, Math.min(100.0, data.value(KEY_STRAIN, 0.0) + delta));
        data.setValue(KEY_STRAIN, strain);
        if (strain >= 70.0) {
            player.m_7292_(new MobEffectInstance(MobEffects.f_216964_, 60, 0, false, false, true)); // DARKNESS
        }
        if (strain >= 92.0) {
            player.m_7292_(new MobEffectInstance(MobEffects.f_19610_, 55, 0, false, false, true)); // BLINDNESS
            triggerIllnessCough(player, data, now, true);
        }
        SystemDataManager.markDirty(player);
    }

    private static void triggerIllnessCough(ServerPlayer player, PlayerSystemData data, long now, boolean force) {
        if (!force && data.value(KEY_COUGH_ICD, 0.0) > (double) now) {
            return;
        }
        data.setValue(KEY_COUGH_ICD, (double) (now + 240L));
        TemplateHelper.selfStrainDamage(player, data, 0.07);
        player.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 50, 0, false, false, true));
        player.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 40, 1, false, false, true));
        TemplateHelper.burstOn(player, Fx.PUFF, 0xC92A2A, 0.24f, 14, 10);
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.itachi.illness_cough")
            .m_130940_(ChatFormatting.DARK_RED));
        SystemDataManager.markDirty(player);
    }

    /** 技能 1：乌鸦分身·魔幻枷杭之术（鸦群后撤瞬身 + 四档幻术定身）。 */
    public static final class CrowCloneSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(3.2))) {
                return;
            }
            ctx.cooldown(65);
            TemplateHelper.burstOn(ctx.player, Fx.PUFF, 0x212529, 0.35f, 18, 18);
            TemplateHelper.dashForward(ctx.player, -0.95, 0.20); // 化鸦后撤拉开身位

            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 16.0, 1, 0.6);
            if (!hits.isEmpty()) {
                LivingEntity target = TargetUtil.living(hits.get(0).entity());
                if (target != null) {
                    RuleJudge.Result check = RuleJudge.evaluate(ctx.player, ctx.data, target, StatType.MP);
                    RuleJudge.notify(ctx.player, target, "魔幻·枷杭之术", check);
                    float baseDmg = ctx.damage(1.0);
                    switch (check.tier()) {
                        case FULL -> {
                            TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), baseDmg);
                            target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 55, 4, false, false, true));
                            target.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 55, 1, false, false, true));
                            refundGeniusMp(ctx.data, 0.04);
                        }
                        case MAJOR -> {
                            TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), baseDmg * 0.75f);
                            target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 35, 2, false, false, true));
                        }
                        case MINOR -> {
                            TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), baseDmg * 0.40f);
                            target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 20, 0, false, false, true));
                        }
                        case FAIL -> TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), baseDmg * 0.15f);
                    }
                    TemplateHelper.burstOn(target, Fx.PUFF, 0x862E9C, 0.28f, 14, 14);
                }
            }
            SystemDataManager.markDirty(ctx.player);
        }
    }

    /** 技能 2：万花筒·天照（视线焦距点燃不灭黑炎，累积瞳力磨损）。 */
    public static final class AmaterasuSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(6.0))) {
                return;
            }
            ctx.cooldown(110);
            addMangekyoStrain(ctx.player, ctx.data, 22.0, ctx.now);
            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 22.0, 1, 0.55);
            if (!hits.isEmpty()) {
                LivingEntity target = TargetUtil.living(hits.get(0).entity());
                if (target != null) {
                    float impact = ctx.damage(1.0);
                    if (TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), impact)) {
                        target.m_20254_(7);
                        BLACK_FLAMES.put(target, new BlackFlame(ctx.player, Math.max(1.5f, impact * 0.22f), 6));
                        TemplateHelper.burstOn(target, Fx.SPHERE, 0x1A1B1E, 0.38f, 20, 20);
                        TemplateHelper.burstOn(target, Fx.SPARK, 0x9C36B5, 0.22f, 16, 12);
                    }
                }
            }
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_11705_, SoundSource.PLAYERS, 0.9f, 0.75f);
        }
    }

    /** 技能 3：万花筒·月读（机制级绝对精神支配幻术，严格经四档判定，失败遭受瞳力反噬）。 */
    public static final class TsukuyomiSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(8.5))) {
                return;
            }
            ctx.cooldown(170);
            addMangekyoStrain(ctx.player, ctx.data, 30.0, ctx.now);
            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 18.0, 1, 0.65);
            if (hits.isEmpty()) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.itachi.no_target")
                    .m_130940_(ChatFormatting.GRAY));
                return;
            }
            LivingEntity target = TargetUtil.living(hits.get(0).entity());
            if (target == null) {
                return;
            }
            RuleJudge.Result check = RuleJudge.evaluate(ctx.player, ctx.data, target, StatType.MP);
            RuleJudge.notify(ctx.player, target, "月读", check);
            float fullDamage = ctx.damage(1.0);
            TemplateHelper.burstOn(target, Fx.RING, 0xE03131, 0.50f, 22, 22);
            TemplateHelper.burstOn(target, Fx.SPHERE, 0x111111, 0.35f, 22, 16);

            switch (check.tier()) {
                case FULL -> {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), fullDamage);
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 70, 6, false, false, true));
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19610_, 70, 0, false, false, true));
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 70, 1, false, false, true));
                    refundGeniusMp(ctx.data, 0.06);
                }
                case MAJOR -> {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), fullDamage * 0.70f);
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 40, 2, false, false, true));
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19610_, 40, 0, false, false, true));
                }
                case MINOR -> {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), fullDamage * 0.35f);
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 20, 1, false, false, true));
                }
                case FAIL -> {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), fullDamage * 0.10f);
                    TemplateHelper.selfStrainDamage(ctx.player, ctx.data, 0.08);
                    ctx.player.m_7292_(new MobEffectInstance(MobEffects.f_19610_, 45, 0, false, false, true));
                    Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.itachi.tsukuyomi_backlash")
                        .m_130940_(ChatFormatting.RED));
                }
            }
            SystemDataManager.markDirty(ctx.player);
        }
    }

    private static void refundGeniusMp(PlayerSystemData data, double ratio) {
        double maxMp = TemplateHelper.maxStat(data, StatType.MP);
        data.setMp(Math.min(maxMp, data.mp() + maxMp * ratio));
    }

    /** 写轮眼洞察偏转弹道 + 绝症体魄受重创易伤与咯血。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        if (data == null) {
            return;
        }
        long now = player.m_9236_().m_46467_();
        // 写轮眼洞察：投射物有概率化为乌鸦替身无伤闪避（内置 4 秒冷却）
        if (event.getSource().m_7640_() instanceof Projectile
            && data.value(KEY_CROW_DODGE_ICD, 0.0) <= (double) now
            && data.value(KEY_STRAIN, 0.0) < 85.0
            && player.m_217043_().m_188500_() < 0.35) {
            data.setValue(KEY_CROW_DODGE_ICD, (double) (now + 80L));
            event.setCanceled(true);
            TemplateHelper.burstOn(player, Fx.PUFF, 0x212529, 0.30f, 14, 14);
            SystemDataManager.markDirty(player);
            return;
        }

        // 绝症低耐久身躯：物理近战伤害额外 +18%；血量过低时引发咯血反噬
        String msgId = event.getSource().m_19385_();
        if ("player".equals(msgId) || "mob".equals(msgId)) {
            event.setAmount(event.getAmount() * 1.18f);
        }
        double maxHp = TemplateHelper.maxStat(data, StatType.HP);
        if (maxHp > 0.0 && data.hp() < maxHp * 0.45) {
            triggerIllnessCough(player, data, now, false);
        }
    }

    /** 甜食控癖好：食用甜点舒缓绝症与万花筒瞳力磨损，并获得契合经验。 */
    @SubscribeEvent
    public static void onFinishFood(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        if (data == null || !TemplateHelper.isSweetFood(event.getItem())) {
            return;
        }
        double cur = data.value(KEY_STRAIN, 0.0);
        data.setValue(KEY_STRAIN, Math.max(0.0, cur - 28.0));
        TemplateHelper.healRatio(player, data, 0.05);
        DrawAttunementUnlock.grantQuirkXp(player, data, 5.0, "三色丸子甜食癖");
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.itachi.sweet_relief")
            .m_130940_(ChatFormatting.LIGHT_PURPLE));
    }

    /** 厌战一击制敌癖好：迅速终结目标获得额外契合经验。 */
    @SubscribeEvent
    public static void onCleanKill(LivingDeathEvent event) {
        if (!(event.getSource().m_7639_() instanceof ServerPlayer killer) || killer == event.getEntity()) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(killer, TEMPLATE_ID);
        if (data != null) {
            DrawAttunementUnlock.grantQuirkXp(killer, data, 4.5, "速战制敌");
        }
    }

    /** 玩家静息平复万花筒瞳力磨损。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.m_9236_().m_46467_() % 20L != 0L) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        if (data == null) {
            return;
        }
        double strain = data.value(KEY_STRAIN, 0.0);
        if (strain > 0.0) {
            double recover = player.m_6144_() ? 7.0 : 3.5;
            data.setValue(KEY_STRAIN, Math.max(0.0, strain - recover));
            SystemDataManager.markDirty(player);
        }
    }

    /** 天照黑炎每秒持续灼烧结算。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BLACK_FLAMES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, BlackFlame>> it = BLACK_FLAMES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, BlackFlame> entry = it.next();
            LivingEntity target = entry.getKey();
            BlackFlame flame = entry.getValue();
            if (!target.m_6084_() || target.m_9236_().m_46467_() % 20L != 0L) {
                if (!target.m_6084_()) {
                    it.remove();
                }
                continue;
            }
            flame.secondsLeft--;
            TargetUtil.hurt(target, ModDamageTypes.technique(flame.caster), flame.perTickDamage);
            TemplateHelper.burstOn(target, Fx.PUFF, 0x1A1B1E, 0.25f, 10, 6);
            if (flame.secondsLeft <= 0) {
                it.remove();
            }
        }
    }
}
