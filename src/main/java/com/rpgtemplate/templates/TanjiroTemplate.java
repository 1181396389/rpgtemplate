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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * R 阶过渡模板：灶门炭治郎（{@code rpgtemplate:tanjiro_1}）。
 * <p>定位：稳健的近战呼吸剑士（Lv.20），全集中水之呼吸攻守均衡，强开火之神神乐具备越阶爆发但会积累【缺氧负荷】触发肺腑反噬。</p>
 */
@EventBusSubscriber(modid = "rpgtemplate")
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
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1, false, false, true));
            TemplateHelper.selfStrainDamage(player, data, 0.06);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.tanjiro.hypoxia_overload")
                .withStyle(ChatFormatting.RED));
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
            ctx.player.fallDistance = 0.0f; // 化解坠落距离
            ctx.data.setValue(KEY_BREATH_UNTIL, (double) (ctx.now + 120L));
            float dmg = ctx.damage(1.0);
            if (ctx.player.level() instanceof ServerLevel sl) {
                Fx.burst(sl, Fx.RING, 0x3BC9DB, 0.55f, 14, 0.15f,
                    ctx.player.getX(), ctx.player.getY() + 1.0, ctx.player.getZ(), 16, 0.8, 0.5, 0.8);
            }
            List<Entity> targets = ctx.player.level().getEntities(
                ctx.player, ctx.player.getBoundingBox().inflate(3.8), e -> TargetUtil.valid(ctx.player, e));
            for (Entity entity : targets) {
                TargetUtil.hurt(entity, ModDamageTypes.technique(ctx.player), dmg);
            }
            ctx.player.level().playSound(null, ctx.player.getX(), ctx.player.getY(), ctx.player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.15f);
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
            ctx.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false, true));
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
                Msg.actionBar(ctx.player, Component.translatable("message.rpgtemplate.tanjiro.cough_locked")
                    .withStyle(ChatFormatting.RED));
                return;
            }
            if (!ctx.consume(ctx.cost(11.0))) {
                return;
            }
            ctx.cooldown(130);
            TemplateHelper.dashForward(ctx.player, 0.85, 0.22);
            float baseDmg = ctx.damage(1.0);
            if (ctx.player.level() instanceof ServerLevel sl) {
                Fx.burst(sl, Fx.RING, 0xFF6B35, 0.65f, 18, 0.22f,
                    ctx.player.getX(), ctx.player.getY() + 1.1, ctx.player.getZ(), 24, 1.1, 0.6, 1.1);
            }
            List<Entity> targets = ctx.player.level().getEntities(
                ctx.player, ctx.player.getBoundingBox().inflate(4.2), e -> TargetUtil.valid(ctx.player, e));
            for (Entity entity : targets) {
                LivingEntity lv = TargetUtil.living(entity);
                float dmg = (lv != null && lv.getType().is(EntityTypeTags.UNDEAD)) ? baseDmg * 1.35f : baseDmg; // UNDEAD 增伤
                if (TargetUtil.hurt(entity, ModDamageTypes.technique(ctx.player), dmg) && lv != null) {
                    lv.igniteForSeconds(5);
                    TemplateHelper.burstOn(lv, Fx.SPHERE, 0xFF922B, 0.32f, 14, 12);
                }
            }
            ctx.player.level().playSound(null, ctx.player.getX(), ctx.player.getY(), ctx.player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.05f);
            addHypoxia(ctx.player, ctx.data, 42.0, ctx.now);
        }
    }

    /** 癖好：温柔本性——绝不挥刀伤害村民或幼年动物。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackInnocent(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(attacker, TEMPLATE_ID);
        if (data == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof AbstractVillager || (victim instanceof AgeableMob ageable && ageable.isBaby())) {
            event.setCanceled(true);
            Msg.actionBar(attacker, Component.translatable("message.rpgtemplate.tanjiro.gentle_heart")
                .withStyle(ChatFormatting.GREEN));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingIncomingDamageEvent event) {
        // 1. 炭治郎输出：隙之线破绽斩击 + 呼吸韵律加成 + 正面头槌契合经验
        Entity src = event.getSource().getEntity();
        if (src instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            PlayerSystemData atkData = TemplateHelper.activeData(attacker, TEMPLATE_ID);
            if (atkData != null) {
                long now = attacker.level().getGameTime();
                float amount = event.getAmount();
                LivingEntity victim = event.getEntity();
                if (atkData.value(KEY_BREATH_UNTIL, 0.0) > (double) now && !isOverloaded(atkData, now)) {
                    amount *= 1.15f;
                }
                if (victim.getHealth() <= victim.getMaxHealth() * 0.40f) {
                    amount *= 1.25f; // 隙之线
                }
                if (attacker.distanceTo(victim) <= 3.5f) {
                    DrawAttunementUnlock.grantQuirkXp(attacker, atkData, 3.0, "全集中迎击");
                }
                event.setAmount(amount);
            }
        }

        // 2. 炭治郎受击：水下/缺氧过载肺腑易伤 vs 正面迎击铁头减伤
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
            if (data != null) {
                long now = player.level().getGameTime();
                boolean drowning = "drown".equals(event.getSource().getMsgId()) || player.getAirSupply() < player.getMaxAirSupply();
                if (drowning || data.value(KEY_HYPOXIA, 0.0) >= 60.0 || isOverloaded(data, now)) {
                    event.setAmount(event.getAmount() * 1.25f);
                } else if (src instanceof LivingEntity foe && player.distanceTo(foe) <= 3.2f) {
                    event.setAmount(event.getAmount() * 0.90f); // 石头的头近身抗冲击
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        if (now % 20L != 0L) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        if (data == null) {
            return;
        }
        double hypoxia = data.value(KEY_HYPOXIA, 0.0);
        if (hypoxia > 0.0) {
            double decay = player.isShiftKeyDown() ? 10.0 : 5.0; // 蹲伏调息恢复更快
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
