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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * N 阶过渡模板：佐藤和真（{@code rpgtemplate:kazuma_1}）。
 * <p>定位：易满级（Lv.10）的前期实用主义冒险者，强运博弈、偷窃缴械、潜伏背刺，同时受限于最弱职阶身板与浅薄魔力池。</p>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class KazumaTemplate {
    public static final String TEMPLATE_ID = "rpgtemplate:kazuma_1";
    public static final String KEY_STEALTH_UNTIL = "kz.stealth_until";
    public static final String KEY_LUCK_SAVE_UNTIL = "kz.luck_save_until";
    public static final String KEY_MANA_STRAIN_UNTIL = "kz.mana_strain_until";
    public static final String KEY_COWARD_UNTIL = "kz.coward_until";

    private static final UUID LUCK_MOD_ID =
        UUID.nameUUIDFromBytes("rpgtemplate:kazuma_luck_bonus".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> HEAVY_IMPACT_IDS = Set.of(
        "explosion", "explosion.player", "anvil", "fallingBlock", "fallingStalactite", "flyIntoWall", "mob");

    private KazumaTemplate() {
    }

    private static double manaStrainMult(PlayerSystemData data, long now) {
        return data.value(KEY_MANA_STRAIN_UNTIL, 0.0) > (double) now ? 1.35 : 1.0;
    }

    private static void markManaCast(PlayerSystemData data, long now) {
        data.setValue(KEY_MANA_STRAIN_UNTIL, (double) (now + 80L));
    }

    /** 技能 1：偷窃（Steal）——四档机制判定缴械博弈技。 */
    public static final class StealSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            double cost = ctx.cost(4.0) * manaStrainMult(ctx.data, ctx.now);
            if (!ctx.consume(cost)) {
                return;
            }
            markManaCast(ctx.data, ctx.now);
            ctx.cooldown(100);
            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 12.0, 1, 0.5);
            if (hits.isEmpty()) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.kazuma.steal_miss")
                    .m_130940_(ChatFormatting.GRAY));
                return;
            }
            LivingEntity target = TargetUtil.living(hits.get(0).entity());
            if (target == null) {
                return;
            }
            float dmg = ctx.damage(1.0);
            TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), dmg);
            TemplateHelper.burstOn(target, Fx.SPARK, 0x7CFF9E, 0.22f, 12, 12);

            RuleJudge.Result check = RuleJudge.evaluate(ctx.player, ctx.data, target, StatType.MP);
            RuleJudge.notify(ctx.player, target, "偷窃", check);
            switch (check.tier()) {
                case FULL -> {
                    disarmTarget(ctx.player, target);
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 80, 1, false, false, true));
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 60, 0, false, false, true));
                }
                case MAJOR -> {
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 60, 1, false, false, true));
                    target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 50, 0, false, false, true));
                }
                case MINOR -> target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 25, 0, false, false, true));
                case FAIL -> ctx.player.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 25, 1, false, false, true));
            }
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_11983_, SoundSource.PLAYERS, 0.9f, 1.35f);
            SystemDataManager.markDirty(ctx.player);
        }

        private static void disarmTarget(ServerPlayer caster, LivingEntity target) {
            ItemStack main = target.m_21205_();
            if (main.m_41619_()) {
                return;
            }
            if (target instanceof ServerPlayer victimPlayer) {
                Inventory inv = victimPlayer.m_150109_();
                for (int i = 9; i < 36; i++) {
                    if (inv.m_8020_(i).m_41619_()) {
                        inv.m_6836_(i, main.m_41777_());
                        target.m_21008_(InteractionHand.MAIN_HAND, ItemStack.f_41583_);
                        Msg.actionBar(caster, Component.m_237115_("message.rpgtemplate.kazuma.steal_disarm")
                            .m_130940_(ChatFormatting.GREEN));
                        return;
                    }
                }
            } else if (target instanceof Mob mob) {
                ItemStack stolen = main.m_41777_();
                stolen.m_41764_(1);
                target.m_21008_(InteractionHand.MAIN_HAND, ItemStack.f_41583_);
                if (!caster.m_150109_().m_36054_(stolen)) {
                    caster.m_36176_(stolen, false);
                }
                Msg.actionBar(caster, Component.m_237115_("message.rpgtemplate.kazuma.steal_disarm")
                    .m_130940_(ChatFormatting.GREEN));
            }
        }
    }

    /** 技能 2：狙击（Snipe）——远距离强运暴击射击。 */
    public static final class SnipeSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!ctx.consume(ctx.cost(3.0))) {
                return;
            }
            ctx.cooldown(50);
            boolean crit = ctx.player.m_217043_().m_188500_() < 0.35;
            float dmg = ctx.damage(crit ? 1.55 : 1.0);
            if (ctx.data.value(KEY_STEALTH_UNTIL, 0.0) > (double) ctx.now) {
                dmg *= 1.45f;
                ctx.data.setValue(KEY_STEALTH_UNTIL, 0.0);
            }
            List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 28.0, 1, 0.35);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                Vec3 eye = ctx.player.m_146892_();
                Vec3 look = ctx.player.m_20154_();
                for (int i = 1; i <= 8; i++) {
                    Fx.burst(sl, Fx.SPARK, crit ? 0xFFE066 : 0x9EE7FF, 0.14f, 8, 0.02f,
                        eye.f_82479_ + look.f_82479_ * i * 3.0,
                        eye.f_82480_ + look.f_82480_ * i * 3.0,
                        eye.f_82481_ + look.f_82481_ * i * 3.0, 2, 0.04, 0.04, 0.04);
                }
            }
            for (TargetUtil.Hit hit : hits) {
                if (TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), dmg)) {
                    LivingEntity living = TargetUtil.living(hit.entity());
                    if (living != null) {
                        TemplateHelper.burstOn(living, Fx.RING, crit ? 0xFFD43B : 0x74C0FC, 0.3f, 10, 10);
                    }
                    if (crit) {
                        Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.kazuma.snipe_crit")
                            .m_130940_(ChatFormatting.GOLD));
                    }
                }
            }
            ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                SoundEvents.f_11699_, SoundSource.PLAYERS, 1.0f, 1.25f);
            SystemDataManager.markDirty(ctx.player);
        }
    }

    /** 技能 3：潜伏（Lurk）——隐匿气息等待背刺。 */
    public static final class LurkSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            double cost = ctx.cost(3.5) * manaStrainMult(ctx.data, ctx.now);
            if (!ctx.consume(cost)) {
                return;
            }
            markManaCast(ctx.data, ctx.now);
            ctx.cooldown(140);
            ctx.data.setValue(KEY_STEALTH_UNTIL, (double) (ctx.now + 100L));
            ctx.player.m_7292_(new MobEffectInstance(MobEffects.f_19609_, 100, 0, false, false, true)); // INVISIBILITY
            ctx.player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 100, 0, false, false, true)); // MOVEMENT_SPEED
            TemplateHelper.burstOn(ctx.player, Fx.PUFF, 0x8CE99A, 0.25f, 14, 10);
            SystemDataManager.markDirty(ctx.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        // 1. 和真作为攻击者：潜伏破隐爆发 / 背刺癖好经验 / 被围殴怯战降近战伤害
        Entity src = event.getSource().m_7639_();
        if (src instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            PlayerSystemData atkData = TemplateHelper.activeData(attacker, TEMPLATE_ID);
            if (atkData != null) {
                long now = attacker.m_9236_().m_46467_();
                float amount = event.getAmount();
                boolean stealthed = atkData.value(KEY_STEALTH_UNTIL, 0.0) > (double) now;
                boolean fromBehind = event.getEntity().m_20154_().m_82526_(attacker.m_20154_()) > 0.35;
                if (stealthed) {
                    amount *= 1.45f;
                    atkData.setValue(KEY_STEALTH_UNTIL, 0.0);
                    attacker.m_21195_(MobEffects.f_19609_);
                }
                if (stealthed || fromBehind) {
                    double maxSp = TemplateHelper.maxStat(atkData, StatType.SP);
                    atkData.setSp(Math.min(maxSp, atkData.sp() + maxSp * 0.04));
                    DrawAttunementUnlock.grantQuirkXp(attacker, atkData, 3.5, "实用主义奇袭");
                }
                if (atkData.value(KEY_COWARD_UNTIL, 0.0) > (double) now && attacker.m_20270_(event.getEntity()) < 5.0f) {
                    amount *= 0.90f;
                }
                event.setAmount(amount);
            }
        }

        // 2. 和真作为受击者：最弱职阶身板易伤 + 强运保命
        if (event.getEntity() instanceof ServerPlayer victim) {
            PlayerSystemData vicData = TemplateHelper.activeData(victim, TEMPLATE_ID);
            if (vicData != null) {
                String msgId = event.getSource().m_19385_();
                if (HEAVY_IMPACT_IDS.contains(msgId)) {
                    event.setAmount(event.getAmount() * 1.20f);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathPrevent(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = TemplateHelper.activeData(player, TEMPLATE_ID);
        if (data == null) {
            return;
        }
        long now = player.m_9236_().m_46467_();
        if (data.value(KEY_LUCK_SAVE_UNTIL, 0.0) <= (double) now && player.m_217043_().m_188500_() < 0.25) {
            event.setCanceled(true);
            player.m_21153_(2.0f);
            data.setHp(Math.max(5.0, TemplateHelper.maxStat(data, StatType.HP) * 0.10));
            data.setValue(KEY_LUCK_SAVE_UNTIL, (double) (now + 1200L));
            SystemDataManager.markDirty(player);
            Msg.chat(player, Component.m_237115_("message.rpgtemplate.kazuma.luck_save")
                .m_130940_(ChatFormatting.GOLD));
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
        AttributeInstance luck = player.m_21051_(Attributes.f_22286_);
        if (data == null) {
            if (luck != null && luck.m_22111_(LUCK_MOD_ID) != null) {
                luck.m_22120_(LUCK_MOD_ID);
            }
            return;
        }
        if (luck != null && luck.m_22111_(LUCK_MOD_ID) == null) {
            luck.m_22118_(new AttributeModifier(LUCK_MOD_ID, "kazuma_luck", 4.0, AttributeModifier.Operation.ADDITION));
        }
        List<Entity> threats = player.m_9236_().m_6249_(player, player.m_20191_().m_82400_(10.0),
            e -> e instanceof Enemy && e.m_6084_());
        if (!threats.isEmpty()) {
            player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 30, 0, true, false, false));
        }
        if (threats.size() >= 3) {
            data.setValue(KEY_COWARD_UNTIL, (double) (now + 40L));
            SystemDataManager.markDirty(player);
        }
    }
}
