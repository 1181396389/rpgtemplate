package com.rpgtemplate.templates;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.fx.Fx;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * R 阶过渡模板：灶门炭治郎（{@code rpgtemplate:tanjiro_1}）。
 * <p>定位：稳健的近战呼吸剑士（Lv.20），全集中水之呼吸攻守均衡，强开火之神神乐具备越阶爆发但会积累【缺氧负荷】触发肺腑反噬。</p>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class TanjiroTemplate {
    public static final String TEMPLATE_ID = "rpgtemplate:tanjiro_1";
    public static final String KEY_HYPOXIA = "tj.hypoxia";
    public static final String KEY_BREATH_UNTIL = "tj.breath_until";
    public static final String KEY_COUGH_UNTIL = "tj.cough_until";

    private TanjiroTemplate() {
    }

    private static boolean isOverloaded(PlayerSystemData data, long now) {
        return data.value(KEY_COUGH_UNTIL, 0.0) > (double) now;
    }

    private static void addHypoxia(ServerPlayer player, PlayerSystemData data, double delta, long now) {
        double next = Math.max(0.0, Math.min(100.0, data.value(KEY_HYPOXIA, 0.0) + delta));
        data.setValue(KEY_HYPOXIA, next);
        if (next >= 100.0) {
            data.setValue(KEY_HYPOXIA, 65.0);
            data.setValue(KEY_COUGH_UNTIL, (double) (now + 100L));
            player.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 100, 1, false, false, true));
            player.m_7292_(new MobEffectInstance(MobEffects.f_19599_, 100, 1, false, false, true));
            TemplateHelper.selfStrainDamage(player, data, 0.06);
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.tanjiro.hypoxia_overload")
                .m_130940_(ChatFormatting.RED));
        }
        SystemDataManager.markDirty(player);
    }

    /** 技能 1：水之呼吸·贰之型 水车。 */
    public static final class WaterWheelSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(4.5))) {
                return;
            }
            ctx.cooldown(50);
            TemplateHelper.dashForward(ctx.player, 0.75, 0.36);
            ctx.player.f_19789_ = 0.0f; // 化解坠落距离
            ctx.data.setValue(KEY_BREATH_UNTIL, (double) (ctx.now + 120L));
            float dmg = ctx.damage(1.0);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                Fx.burst(sl, Fx.RING, 0x3BC9DB, 0.55f, 14, 0.15f,
                    ctx.player.m_20185_(), ctx.player.m_20186_() + 1.0, ctx.player.m_20189_(), 16, 0.8, 0.5, 0.8);
            }
            List<Entity> targets = ctx.player.m_9236_().m_6249_(
                ctx.player, ctx.player.m_20191_().m_82400_(3.8), e -> TargetUtil.valid(ctx.player, e));
            for (Entity entity : targets) {
                TargetUtil.hurt(entity, ModDamageTypes.technique(ctx.player), dmg);
            }
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_12317_, SoundSource.PLAYERS, 1.0f, 1.15f);
            SystemDataManager.markDirty(ctx.player);
        }
    }

    /** 技能 2：水之呼吸·叁之型 流流舞。 */
    public static final class FlowingDanceSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(5.5))) {
                return;
            }
            ctx.cooldown(70);
            TemplateHelper.dashForward(ctx.player, 1.25, 0.12);
            ctx.data.setValue(KEY_BREATH_UNTIL, (double) (ctx.now + 140L));
            ctx.player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 80, 1, false, false, true));
            float dmg = ctx.damage(1.0);
            for (TargetUtil.Hit hit : TargetUtil.ray(ctx.player, 6.5, 4, 1.1)) {
                if (TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), dmg)) {
                    LivingEntity lv = TargetUtil.living(hit.entity());
                    if (lv != null) {
                        TemplateHelper.burstOn(lv, Fx.SPARK, 0x4DABF7, 0.25f, 12, 10);
                    }
                }
            }
            addHypoxia(ctx.player, ctx.data, -8.0, ctx.now); // 水之呼吸平复部分缺氧负荷
        }
    }

    /** 技能 3：火之神神乐·圆舞（高爆发但积累显著缺氧反噬）。 */
    public static final class HinokamiSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (isOverloaded(ctx.data, ctx.now)) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.tanjiro.cough_locked")
                    .m_130940_(ChatFormatting.RED));
                return;
            }
            if (!ctx.consume(ctx.cost(11.0))) {
                return;
            }
            ctx.cooldown(130);
            TemplateHelper.dashForward(ctx.player, 0.85, 0.22);
            float baseDmg = ctx.damage(1.0);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                Fx.burst(sl, Fx.RING, 0xFF6B35, 0.65f, 18, 0.22f,
                    ctx.player.m_20185_(), ctx.player.m_20186_() + 1.1, ctx.player.m_20189_(), 24, 1.1, 0.6, 1.1);
            }
            List<Entity> targets = ctx.player.m_9236_().m_6249_(
                ctx.player, ctx.player.m_20191_().m_82400_(4.2), e -> TargetUtil.valid(ctx.player, e));
            for (Entity entity : targets) {
                LivingEntity lv = TargetUtil.living(entity);
                float dmg = (lv != null && lv.m_6336_() == MobType.f_21640_) ? baseDmg * 1.35f : baseDmg; // UNDEAD 增伤
                if (TargetUtil.hurt(entity, ModDamageTypes.technique(ctx.player), dmg) && lv != null) {
                    lv.m_20254_(5);
                    TemplateHelper.burstOn(lv, Fx.SPHERE, 0xFF922B, 0.32f, 14, 12);
                }
            }
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_11913_, SoundSource.PLAYERS, 1.0f, 1.05f);
            addHypoxia(ctx.player, ctx.data, 42.0, ctx.now);
        }
    }

    /** 癖好：温柔本性——绝不挥刀伤害村民或幼年动物。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackInnocent(LivingAttackEvent event) {
        if (!(event.getSource().m_7639_() instanceof ServerPlayer attacker)) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(attacker, TEMPLATE_ID);
        if (data == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof AbstractVillager || (victim instanceof AgeableMob ageable && ageable.m_6162_())) {
            event.setCanceled(true);
            Msg.actionBar(attacker, Component.m_237115_("message.rpgtemplate.tanjiro.gentle_heart")
                .m_130940_(ChatFormatting.GREEN));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        // 1. 炭治郎输出：隙之线破绽斩击 + 呼吸韵律加成 + 正面头槌契合经验
        Entity src = event.getSource().m_7639_();
        if (src instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            PlayerSystemData atkData = TemplateHelper.activeData(attacker, TEMPLATE_ID);
            if (atkData != null) {
                long now = attacker.m_9236_().m_46467_();
                float amount = event.getAmount();
                LivingEntity victim = event.getEntity();
                if (atkData.value(KEY_BREATH_UNTIL, 0.0) > (double) now && !isOverloaded(atkData, now)) {
                    amount *= 1.15f;
                }
                if (victim.m_21223_() <= victim.m_21233_() * 0.40f) {
                    amount *= 1.25f; // 隙之线
                }
                if (attacker.m_20270_(victim) <= 3.5f) {
                    DrawAttunementUnlock.grantQuirkXp(attacker, atkData, 3.0, "全集中迎击");
                }
                event.setAmount(amount);
            }
        }

        // 2. 炭治郎受击：水下/缺氧过载肺腑易伤 vs 正面迎击铁头减伤
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
            if (data != null) {
                long now = player.m_9236_().m_46467_();
                boolean drowning = "drown".equals(event.getSource().m_19385_()) || player.m_20146_() < player.m_6062_();
                if (drowning || data.value(KEY_HYPOXIA, 0.0) >= 60.0 || isOverloaded(data, now)) {
                    event.setAmount(event.getAmount() * 1.25f);
                } else if (src instanceof LivingEntity foe && player.m_20270_(foe) <= 3.2f) {
                    event.setAmount(event.getAmount() * 0.90f); // 石头的头近身抗冲击
                }
            }
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
        if (data == null) {
            return;
        }
        double hypoxia = data.value(KEY_HYPOXIA, 0.0);
        if (hypoxia > 0.0) {
            double decay = player.m_6144_() ? 10.0 : 5.0; // 蹲伏调息恢复更快
            data.setValue(KEY_HYPOXIA, Math.max(0.0, hypoxia - decay));
            SystemDataManager.markDirty(player);
        }
        if (!isOverloaded(data, now) && hypoxia < 50.0) {
            double maxSp = TemplateHelper.maxStat(data, StatType.SP);
            if (data.sp() < maxSp) {
                data.setSp(Math.min(maxSp, data.sp() + maxSp * 0.02));
                SystemDataManager.markDirty(player);
            }
        }
    }
}
