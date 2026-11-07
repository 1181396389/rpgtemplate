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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * SSR 阶过渡模板：冲田总司（{@code rpgtemplate:okita_1}，新选组一番队队长·病弱的绝世天才剑士）。
 * <p>集极致神速【缩地】、因果必中级刺击【无明三段突】与刻入灵基的【肺结核病弱】于一体。</p>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class OkitaTemplate {
    public static final String TEMPLATE_ID = "rpgtemplate:okita_1";
    public static final String KEY_TB = "ok.tb";
    public static final String KEY_PARRY_UNTIL = "ok.parry_until";
    public static final String KEY_ENGAGE_AT = "ok.engage_at";

    private static final UUID GENIUS_SPEED_ID =
        UUID.nameUUIDFromBytes("rpgtemplate:okita_genius_speed".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> DISEASE_MSG_IDS = Set.of("wither", "starve", "drown", "magic", "indirectMagic");

    private OkitaTemplate() {
    }

    private static void addTuberculosisStrain(ServerPlayer player, PlayerSystemData data, double delta) {
        double next = Math.max(0.0, Math.min(100.0, data.value(KEY_TB, 0.0) + delta));
        data.setValue(KEY_TB, next);
        if (next >= 100.0) {
            data.setValue(KEY_TB, 58.0);
            TemplateHelper.selfStrainDamage(player, data, 0.09);
            player.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 70, 2, false, false, true));
            player.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 70, 1, false, false, true));
            TemplateHelper.burstOn(player, Fx.PUFF, 0xE03131, 0.28f, 16, 14);
            Msg.chat(player, Component.m_237115_("message.rpgtemplate.okita.hemoptysis")
                .m_130940_(ChatFormatting.RED));
        }
        SystemDataManager.markDirty(player);
    }

    /** 技能 1：缩地（Shukuchi）——瞬间踏入对手死角挥斩，重甲会缩短踏步距离并加重肺痨负担。 */
    public static final class ShukuchiSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(2.2))) {
                return;
            }
            ctx.cooldown(50);
            int armor = TemplateHelper.wornArmorCount(ctx.player);
            double maxRange = armor > 2 ? 6.5 : 10.5;
            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, maxRange, 1, 0.85);
            if (!hits.isEmpty()) {
                LivingEntity target = TargetUtil.living(hits.get(0).entity());
                if (target != null) {
                    Vec3 look = target.m_20154_();
                    double tx = target.m_20185_() - look.f_82479_ * 1.1;
                    double tz = target.m_20189_() - look.f_82481_ * 1.1;
                    ctx.player.m_6021_(tx, target.m_20186_(), tz);
                    float dmg = ctx.damage(1.0);
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), dmg);
                    TemplateHelper.burstOn(target, Fx.RING, 0xFFADC6, 0.38f, 14, 16);
                    ctx.data.setValue(KEY_ENGAGE_AT, (double) ctx.now);
                }
            } else {
                TemplateHelper.dashForward(ctx.player, armor > 2 ? 1.0 : 1.55, 0.12);
            }
            ctx.player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 60, 1, false, false, true));
            addTuberculosisStrain(ctx.player, ctx.data, armor > 2 ? 14.0 : 8.0);
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_12317_, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
    }

    /** 技能 2：誓言的羽织·心眼（伪）——1.5 秒绝境看破格挡架势，受击瞬间免疫并反斩。 */
    public static final class CoatOfOathsSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(2.6))) {
                return;
            }
            ctx.cooldown(120);
            ctx.data.setValue(KEY_PARRY_UNTIL, (double) (ctx.now + 30L));
            TemplateHelper.burstOn(ctx.player, Fx.RING, 0x66D9E8, 0.42f, 16, 14);
            Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.okita.parry_ready")
                .m_130940_(ChatFormatting.AQUA));
            SystemDataManager.markDirty(ctx.player);
        }
    }

    /** 技能 3：无明三段突（机制级因果必中破防三连刺，经四档机制判定，高肺痨负荷）。 */
    public static final class MumyoSandanzukiSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(5.8))) {
                return;
            }
            ctx.cooldown(160);
            TemplateHelper.dashForward(ctx.player, 1.35, 0.08);
            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 7.5, 1, 0.9);
            addTuberculosisStrain(ctx.player, ctx.data, 28.0);
            if (hits.isEmpty()) {
                return;
            }
            LivingEntity target = TargetUtil.living(hits.get(0).entity());
            if (target == null) {
                return;
            }
            ctx.data.setValue(KEY_ENGAGE_AT, (double) ctx.now);
            RuleJudge.Result check = RuleJudge.evaluate(ctx.player, ctx.data, target, StatType.SP);
            RuleJudge.notify(ctx.player, target, "无明三段突", check);

            float totalDamage = ctx.damage(1.0);
            float perThrust = totalDamage / 3.0f;
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                Fx.burst(sl, Fx.SPARK, 0xFFF3BF, 0.34f, 18, 0.25f,
                    target.m_20185_(), target.m_20186_() + target.m_20206_() * 0.55, target.m_20189_(),
                    26, 0.45, 0.45, 0.45);
            }

            switch (check.tier()) {
                case FULL -> {
                    float unarmored = compensateArmor(target, perThrust);
                    for (int i = 0; i < 3; i++) {
                        target.f_19802_ = 0;
                        TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), unarmored);
                    }
                }
                case MAJOR -> {
                    float pierced = perThrust + (compensateArmor(target, perThrust) - perThrust) * 0.60f;
                    for (int i = 0; i < 3; i++) {
                        target.f_19802_ = 0;
                        TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), pierced);
                    }
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 60, 0, false, false, true));
                }
                case MINOR -> {
                    target.f_19802_ = 0;
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), totalDamage * 0.50f);
                }
                case FAIL -> {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), totalDamage * 0.15f);
                    addTuberculosisStrain(ctx.player, ctx.data, 25.0);
                }
            }
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_12313_, SoundSource.PLAYERS, 1.0f, 1.3f);
            SystemDataManager.markDirty(ctx.player);
        }

        private static float compensateArmor(LivingEntity victim, float desiredDamage) {
            double armor = victim.m_21230_();
            if (armor <= 0.0) {
                return desiredDamage;
            }
            double mult = 1.0 - Math.min(20.0, armor) / 25.0;
            return mult > 0.15 ? (float) (desiredDamage / mult) : desiredDamage * 2.5f;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        // 1. 冲田输出：持剑/空手天然理心流剑气加成 + 绝境剑心（低血量增伤但微增病弱）
        Entity src = event.getSource().m_7639_();
        if (src instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            PlayerSystemData atkData = TemplateHelper.activeData(attacker, TEMPLATE_ID);
            if (atkData != null) {
                float amount = event.getAmount();
                if (TemplateHelper.holdingSwordOrEmpty(attacker)) {
                    amount *= 1.15f;
                }
                double maxHp = TemplateHelper.maxStat(atkData, StatType.HP);
                if (maxHp > 0.0 && atkData.hp() < maxHp * 0.50) {
                    amount *= 1.18f;
                }
                atkData.setValue(KEY_ENGAGE_AT, (double) attacker.m_9236_().m_46467_());
                event.setAmount(amount);
            }
        }

        // 2. 冲田受击：心眼看破反斩 + 肺结核病弱对毒素/凋零/法术易伤
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
            if (data == null) {
                return;
            }
            long now = player.m_9236_().m_46467_();
            if (data.value(KEY_PARRY_UNTIL, 0.0) > (double) now) {
                data.setValue(KEY_PARRY_UNTIL, 0.0);
                event.setCanceled(true);
                TemplateHelper.burstOn(player, Fx.SPARK, 0x99E9F2, 0.35f, 14, 16);
                if (src instanceof LivingEntity foe && foe != player) {
                    double maxSp = TemplateHelper.maxStat(data, StatType.SP);
                    float counterDmg = (float) Math.max(6.0, maxSp * 0.28 + data.level() * 1.4);
                    TargetUtil.hurt(foe, ModDamageTypes.technique(player), counterDmg);
                }
                SystemDataManager.markDirty(player);
                return;
            }

            String msgId = event.getSource().m_19385_();
            if ("wither".equals(msgId) || "starve".equals(msgId) || "drown".equals(msgId)) {
                event.setAmount(event.getAmount() * 1.35f);
            } else if (DISEASE_MSG_IDS.contains(msgId)) {
                event.setAmount(event.getAmount() * 1.15f);
            }
        }
    }

    /** 甜食控癖好：食用甜点平复咳嗽、大幅降低病弱值并获契合经验。 */
    @SubscribeEvent
    public static void onFinishFood(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        if (data == null || !TemplateHelper.isSweetFood(event.getItem())) {
            return;
        }
        data.setValue(KEY_TB, Math.max(0.0, data.value(KEY_TB, 0.0) - 35.0));
        TemplateHelper.healRatio(player, data, 0.06);
        DrawAttunementUnlock.grantQuirkXp(player, data, 6.0, "新选组甜党");
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.okita.dango_relief")
            .m_130940_(ChatFormatting.LIGHT_PURPLE));
    }

    /** 一番队剑士矜持：交战 5 秒内持剑迅速斩落敌人，获得额外契合经验。 */
    @SubscribeEvent
    public static void onSwiftKill(LivingDeathEvent event) {
        if (!(event.getSource().m_7639_() instanceof ServerPlayer killer) || killer == event.getEntity()) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(killer, TEMPLATE_ID);
        if (data == null || !TemplateHelper.holdingSwordOrEmpty(killer)) {
            return;
        }
        long now = killer.m_9236_().m_46467_();
        if (now - data.value(KEY_ENGAGE_AT, 0.0) <= 100.0) {
            DrawAttunementUnlock.grantQuirkXp(killer, data, 5.5, "一闪即决");
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        long now = player.m_9236_().m_46467_();
        if (now % 20L != 0L) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        AttributeInstance speed = player.m_21051_(Attributes.f_22279_);
        if (data == null) {
            if (speed != null && speed.m_22111_(GENIUS_SPEED_ID) != null) {
                speed.m_22120_(GENIUS_SPEED_ID);
            }
            return;
        }
        int armor = TemplateHelper.wornArmorCount(player);
        boolean lightFootwork = armor <= 2;
        if (speed != null) {
            boolean hasMod = speed.m_22111_(GENIUS_SPEED_ID) != null;
            if (lightFootwork && !hasMod) {
                speed.m_22118_(new AttributeModifier(
                    GENIUS_SPEED_ID, "okita_genius_speed", 0.16, AttributeModifier.Operation.MULTIPLY_BASE));
            } else if (!lightFootwork && hasMod) {
                speed.m_22120_(GENIUS_SPEED_ID);
            }
        }
        // 中毒状态下病弱急剧上升；静息时病弱平复（穿重甲恢复变慢）
        if (player.m_21023_(MobEffects.f_19614_)) {
            addTuberculosisStrain(player, data, 12.0);
        } else {
            double tb = data.value(KEY_TB, 0.0);
            if (tb > 0.0) {
                double recover = armor > 2 ? 2.0 : (player.m_6144_() ? 8.0 : 4.5);
                data.setValue(KEY_TB, Math.max(0.0, tb - recover));
                SystemDataManager.markDirty(player);
            }
        }
    }
}
