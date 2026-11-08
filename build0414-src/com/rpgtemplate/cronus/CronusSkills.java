package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.templates.TemplateHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 假面骑士 Cronus（{@code rpgtemplate:cronus_1}）全部 7 个主动技能实现。
 */
public final class CronusSkills {
    private CronusSkills() {
    }

    private static boolean checkDriverReady(ServerPlayer player, PlayerSystemData data, long now) {
        if (data.value(CronusKeys.KEY_OVERHEAT_UNTIL, 0.0) > (double) now) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.overheated")
                .m_130940_(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /** 技能 1：编年史变身（檀正宗人形态 ⇄ 假面骑士 Cronus 切换）。 */
    public static final class HenshinSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (CronusKeys.isTransformed(ctx.data)) {
                if (CronusKeys.isPaused(ctx.data)) {
                    CronusPauseManager.restart(ctx.player, ctx.data, 1.20, false);
                }
                ctx.data.setValue(CronusKeys.KEY_TRANSFORMED, 0.0);
                ctx.data.setValue(CronusKeys.KEY_TIME_DEF, 0.0);
                ctx.cooldown(30);
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.henshin_off")
                    .m_130940_(ChatFormatting.GRAY));
                SystemDataManager.markDirty((Player) ctx.player);
                return;
            }

            if (!ctx.consume(ctx.cost(4.0))) {
                return;
            }
            ctx.cooldown(40);
            ctx.data.setValue(CronusKeys.KEY_TRANSFORMED, 1.0);
            ctx.data.setValue(CronusKeys.KEY_TRANSFORM_SINCE, (double) ctx.now);
            ctx.data.setValue(CronusKeys.KEY_TIME_DEF, 0.05);
            CronusSounds.playHenshin(ctx.player);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_() + 0.05, ctx.player.m_20189_(), 3.6f, 1));
            }
            Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.henshin_on")
                .m_130940_(ChatFormatting.GREEN));
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    /** 技能 2：Pause / Restart（时间暂停与时间重开领域切换）。 */
    public static final class PauseSkill implements Skill {
        @Override
        public boolean bypassCooldown(SkillContext ctx) {
            return CronusKeys.isPaused(ctx.data);
        }

        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (CronusKeys.isPaused(ctx.data)) {
                CronusPauseManager.restart(ctx.player, ctx.data, 1.20, true);
                ctx.cooldown(70);
                return;
            }
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (!ctx.consume(ctx.cost(6.0))) {
                return;
            }
            CronusKeys.addViralStrain(ctx.player, ctx.data, 8.0, ctx.now);
            CronusPauseManager.startPause(ctx.player, ctx.data, ctx.now);
        }
    }

    /** 技能 3：故障驱动器 II · 武装斩射（潜行点按零消耗切换「链锯 / 光束枪」模式；常态释放对应斩击或三连光束）。 */
    public static final class ZweiWeaponSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)) {
                return;
            }
            int curMode = CronusKeys.weaponMode(ctx.data);
            if (ctx.player.m_6144_()) {
                int next = (curMode == CronusKeys.MODE_CHAINSAW) ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW;
                ctx.data.setValue(CronusKeys.KEY_WEAPON_MODE, (double) next);
                ctx.cooldown(8);
                ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    SoundEvents.f_11736_, SoundSource.PLAYERS, 0.8f, next == CronusKeys.MODE_GUN ? 1.45f : 0.85f);
                Msg.actionBar(ctx.player, Component.m_237115_(next == CronusKeys.MODE_GUN
                        ? "message.rpgtemplate.cronus.mode_gun"
                        : "message.rpgtemplate.cronus.mode_chainsaw")
                    .m_130940_(ChatFormatting.AQUA));
                SystemDataManager.markDirty((Player) ctx.player);
                return;
            }

            if (!ctx.consume(ctx.cost(2.8))) {
                return;
            }
            if (curMode == CronusKeys.MODE_CHAINSAW) {
                ctx.cooldown(32);
                TemplateHelper.dashForward(ctx.player, 0.55, 0.08);
                float dmg = ctx.damage(1.15);
                List<LivingEntity> targets = coneTargets(ctx.player, 4.6, 0.35);
                for (LivingEntity t : targets) {
                    TargetUtil.hurt(t, ModDamageTypes.technique(ctx.player), dmg);
                }
                ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    SoundEvents.f_12317_, SoundSource.PLAYERS, 0.9f, 0.9f);
            } else {
                ctx.cooldown(28);
                float perShot = ctx.damage(0.42);
                List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 26.0, 2, 0.45);
                for (TargetUtil.Hit hit : hits) {
                    for (int i = 0; i < 3; i++) {
                        TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), perShot);
                    }
                }
                ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    SoundEvents.f_11736_, SoundSource.PLAYERS, 0.85f, 1.55f);
            }
        }
    }

    /** 技能 4：能量道具抽取（仅变身态可用；智能随机获取高速化/肌肉化/钢铁化/跳跃强化/回复道具并弹出 ITEM GET!）。 */
    public static final class EnergyItemSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)) {
                return;
            }
            if (!ctx.consume(ctx.cost(4.5))) {
                return;
            }
            ctx.cooldown(140);
            double maxHp = CronusKeys.maxStat(ctx.data, StatType.HP);
            int roll = (ctx.data.hp() < maxHp * 0.45)
                ? 4
                : ctx.player.m_217043_().m_188503_(5);

            String msgKey;
            switch (roll) {
                case 0 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_SPEED_UNTIL, (double) (ctx.now + 240L));
                    msgKey = "message.rpgtemplate.cronus.item_speed";
                }
                case 1 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_MUSCLE_UNTIL, (double) (ctx.now + 240L));
                    msgKey = "message.rpgtemplate.cronus.item_muscle";
                }
                case 2 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_IRON_UNTIL, (double) (ctx.now + 240L));
                    msgKey = "message.rpgtemplate.cronus.item_iron";
                }
                case 3 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_JUMP_UNTIL, (double) (ctx.now + 240L));
                    msgKey = "message.rpgtemplate.cronus.item_jump";
                }
                default -> {
                    TemplateHelper.healRatio(ctx.player, ctx.data, 0.22);
                    CronusKeys.addViralStrain(ctx.player, ctx.data, -12.0, ctx.now);
                    msgKey = "message.rpgtemplate.cronus.item_recovery";
                }
            }
            CronusSounds.playItemGet(ctx.player);
            CronusNet.popupOn(ctx.player, CronusFxMsg.POPUP_ITEM_GET);
            Msg.actionBar(ctx.player, Component.m_237115_(msgKey).m_130940_(ChatFormatting.YELLOW));
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    /**
     * 技能 5：Critical Crews-Aid（时计飞踢 / 蓄力旋转回旋踢）。
     * <ul>
     *   <li>点按（&lt;10 ticks）：迅捷突进时计踢。</li>
     *   <li>长按（≥10 ticks）：展开脚下旋转时钟指针发动 360° 旋转回旋踢，赋予 1.5x 击败经验加成；
     *   若在【Pause】期间命中生命低于 15% 且被完全冻结（FULL）的目标，直接触发「绝版哒！」处决并引爆 1.35x Restart！</li>
     * </ul>
     */
    public static final class CrewsAidSkill implements Skill {
        private static final String KEY_CHARGE_START = "cr.kick_charge";

        @Override
        public boolean holdable() {
            return true;
        }

        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)) {
                return;
            }
            ctx.data.setValue(KEY_CHARGE_START, (double) ctx.now);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_() + 0.05, ctx.player.m_20189_(), 2.2f, 0));
            }
        }

        @Override
        public void onHoldTick(SkillContext ctx, HeldState held) {
            double start = ctx.data.value(KEY_CHARGE_START, -1.0);
            if (start < 0.0) {
                return;
            }
            long heldTicks = ctx.now - (long) start;
            if (heldTicks == 10L) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.spin_ready")
                    .m_130940_(ChatFormatting.GREEN));
                ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    SoundEvents.f_11983_, SoundSource.PLAYERS, 0.8f, 1.8f);
            }
            if (heldTicks >= 26L) {
                ctx.data.setValue(KEY_CHARGE_START, -1.0);
                executeKick(ctx, true);
            }
        }

        @Override
        public void onRelease(SkillContext ctx, HeldState held) {
            double start = ctx.data.value(KEY_CHARGE_START, -1.0);
            if (start < 0.0) {
                return;
            }
            ctx.data.setValue(KEY_CHARGE_START, -1.0);
            long heldTicks = ctx.now - (long) start;
            executeKick(ctx, heldTicks >= 10L);
        }

        private void executeKick(SkillContext ctx, boolean spinKick) {
            double cost = ctx.cost(spinKick ? 8.5 : 5.5);
            if (!ctx.consume(cost)) {
                return;
            }
            ctx.cooldown(spinKick ? 130 : 75);
            CronusSounds.playKimewaza(ctx.player);

            if (!spinKick) {
                TemplateHelper.dashForward(ctx.player, 1.15, 0.18);
                ctx.player.f_19789_ = 0.0f;
                float dmg = ctx.damage(1.0);
                for (LivingEntity target : coneTargets(ctx.player, 5.0, 0.25)) {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), dmg);
                }
                if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_RIDER_KICK, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_() + 0.1, ctx.player.m_20189_(), 3.2f, 0));
                }
                return;
            }

            // 蓄力旋转回旋踢：开启 5 秒 1.5x 经验倍率窗口 + 时停中对 <15% HP（OUT）完全冻结目标触发「绝版」处决！
            ctx.data.setValue(CronusKeys.KEY_SPIN_XP_UNTIL, (double) (ctx.now + 100L));
            TemplateHelper.dashForward(ctx.player, 0.90, 0.26);
            ctx.player.f_19789_ = 0.0f;
            CronusKeys.addViralStrain(ctx.player, ctx.data, 10.0, ctx.now);

            boolean wasPaused = CronusKeys.isPaused(ctx.data);
            float dmg = ctx.damage(1.45);
            AABB sweep = ctx.player.m_20191_().m_82377_(4.8, 2.2, 4.8);
            List<LivingEntity> victims = ctx.player.m_9236_().m_45976_(LivingEntity.class, sweep);

            List<LivingEntity> outExecutions = new ArrayList<>();
            for (LivingEntity v : victims) {
                if (v == ctx.player || !v.m_6084_()) {
                    continue;
                }
                if (wasPaused
                    && v.m_21223_() <= v.m_21233_() * 0.15f
                    && CronusPauseManager.isTargetFullFrozenBy(ctx.player, v)) {
                    outExecutions.add(v);
                } else {
                    TargetUtil.hurt(v, ModDamageTypes.technique(ctx.player), dmg);
                }
            }

            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_RIDER_KICK, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_() + 0.1, ctx.player.m_20189_(), 4.8f, 1));
            }

            // 若在时停中释放旋转踢，强制触发 1.35x 倍率【Restart】爆发，并对 OUT 目标降下「绝版！」处决
            if (wasPaused) {
                CronusPauseManager.restart(ctx.player, ctx.data, 1.35, false);
                if (!outExecutions.isEmpty()) {
                    CronusSounds.playOutExecute(ctx.player);
                    if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                        CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            new CronusFxMsg(CronusFxMsg.KIND_OUT_BANNER, ctx.player.m_19879_(),
                                ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(), 1.0f, 0));
                    }
                    for (LivingEntity exec : outExecutions) {
                        if (exec.m_6084_()) {
                            float lethal = Math.max(dmg * 3.0f, exec.m_21233_() * 1.25f);
                            TargetUtil.hurt(exec, ModDamageTypes.technique(ctx.player), lethal);
                            CronusNet.popupOn(exec, CronusFxMsg.POPUP_CRITICAL);
                        }
                    }
                    DrawAttunementUnlock.grantQuirkXp(ctx.player, ctx.data,
                        14.0 * outExecutions.size(), "「绝版哒！」社长裁决");
                    Msg.chat(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.out_execute")
                        .m_130940_(ChatFormatting.GREEN));
                }
            }
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    /**
     * 技能 6：Kimewaza · 编年史武装必杀（根据「链锯/光束枪」×「常态/Pause」自动派生 4 种必杀形态）。
     * <ul>
     *   <li>链锯 + 常态：Critical Sacrifice（突进十字重斩）</li>
     *   <li>链锯 + 时停：断罪十字刻（在时停目标四周布下四柄悬停光锯，Restart 时同步绞杀）</li>
     *   <li>光束枪 + 常态：Critical Judgement（扇形七重审判光束）</li>
     *   <li>光束枪 + 时停：全方位死角包围网（在时停目标周围布下 12 枚悬停光弹，Restart 时全方位轰击）</li>
     * </ul>
     */
    public static final class KimewazaSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (!ctx.consume(ctx.cost(10.5))) {
                return;
            }
            ctx.cooldown(200);
            CronusKeys.addViralStrain(ctx.player, ctx.data, 14.0, ctx.now);
            CronusSounds.playKimewaza(ctx.player);

            int mode = CronusKeys.weaponMode(ctx.data);
            boolean paused = CronusKeys.isPaused(ctx.data);

            if (mode == CronusKeys.MODE_CHAINSAW && !paused) {
                TemplateHelper.dashForward(ctx.player, 1.35, 0.15);
                float dmg = ctx.damage(1.0);
                for (LivingEntity t : coneTargets(ctx.player, 6.2, 0.15)) {
                    TargetUtil.hurt(t, ModDamageTypes.technique(ctx.player), dmg);
                }
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime_sacrifice")
                    .m_130940_(ChatFormatting.GREEN));
            } else if (mode == CronusKeys.MODE_CHAINSAW) {
                float trapDmg = ctx.damage(1.28);
                List<LivingEntity> targets = coneTargets(ctx.player, 12.0, -0.2);
                int count = 0;
                for (LivingEntity t : targets) {
                    CronusPauseManager.addSuspendedTrap(ctx.player, ctx.data, t, trapDmg, 0);
                    if (++count >= 4) {
                        break;
                    }
                }
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime_cross_trap")
                    .m_130940_(ChatFormatting.GOLD));
            } else if (!paused) {
                float dmg = ctx.damage(1.05);
                List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 28.0, 4, 0.9);
                for (TargetUtil.Hit h : hits) {
                    TargetUtil.hurt(h.entity(), ModDamageTypes.technique(ctx.player), dmg);
                }
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime_judgement")
                    .m_130940_(ChatFormatting.AQUA));
            } else {
                float trapDmg = ctx.damage(1.32);
                List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 24.0, 3, 1.1);
                if (hits.isEmpty()) {
                    for (LivingEntity t : coneTargets(ctx.player, 14.0, -0.3)) {
                        CronusPauseManager.addSuspendedTrap(ctx.player, ctx.data, t, trapDmg, 1);
                        break;
                    }
                } else {
                    for (TargetUtil.Hit h : hits) {
                        LivingEntity liv = TargetUtil.living(h.entity());
                        if (liv != null) {
                            CronusPauseManager.addSuspendedTrap(ctx.player, ctx.data, liv, trapDmg, 1);
                        }
                    }
                }
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime_surround_trap")
                    .m_130940_(ChatFormatting.GOLD));
            }
        }
    }

    /** 技能 7：Reset · 时间回溯（将自身生命值、空间坐标与负面异常回溯至 4 秒前，累积较高病毒过载）。 */
    public static final class ResetSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            CronusCombatAndPassives.RewindSnapshot snap = CronusCombatAndPassives.findRewindSnapshot(ctx.player, ctx.now);
            if (snap == null) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.reset_empty")
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
            if (!ctx.consume(ctx.cost(12.0))) {
                return;
            }
            ctx.cooldown(360);

            // 还原空间坐标与朝向，生命值取「当前值」与「4秒前快照」中的较高者，净化负面状态
            ctx.player.m_6021_(snap.x, snap.y, snap.z);
            ctx.player.m_146922_(snap.yRot);
            ctx.player.m_146926_(snap.xRot);
            ctx.player.m_20256_(Vec3.f_82478_);
            ctx.player.f_19789_ = 0.0f;
            ctx.player.f_19864_ = true;
            ctx.player.m_20095_(); // clearFire

            double maxSysHp = CronusKeys.maxStat(ctx.data, StatType.HP);
            ctx.data.setHp(Math.min(maxSysHp, Math.max(ctx.data.hp(), snap.sysHp)));
            ctx.player.m_21153_(Math.min(ctx.player.m_21233_(), Math.max(ctx.player.m_21223_(), snap.vanillaHp)));

            ctx.player.m_21195_(MobEffects.f_19614_); // POISON
            ctx.player.m_21195_(MobEffects.f_19615_); // WITHER
            ctx.player.m_21195_(MobEffects.f_19597_); // SLOWNESS
            ctx.player.m_21195_(MobEffects.f_19613_); // WEAKNESS
            ctx.player.m_21195_(MobEffects.f_19610_); // BLINDNESS
            ctx.player.m_21195_(MobEffects.f_216964_); // DARKNESS

            CronusSounds.playRestart(ctx.player);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, snap.x, snap.y, snap.z,
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, ctx.player.m_19879_(),
                        snap.x, snap.y + 0.05, snap.z, 4.5f, 2));
            }
            Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.reset_done")
                .m_130940_(ChatFormatting.GOLD));
            CronusKeys.addViralStrain(ctx.player, ctx.data, 24.0, ctx.now);
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    private static List<LivingEntity> coneTargets(ServerPlayer player, double range, double minDot) {
        Vec3 eye = player.m_146892_();
        Vec3 look = player.m_20154_().m_82541_();
        AABB box = player.m_20191_().m_82377_(range, range * 0.6, range);
        List<LivingEntity> out = new ArrayList<>();
        double rSq = range * range;
        for (LivingEntity e : player.m_9236_().m_45976_(LivingEntity.class, box)) {
            if (e == player || !e.m_6084_() || e.m_20280_(player) > rSq) {
                continue;
            }
            Vec3 dir = e.m_20191_().m_82399_().m_82546_(eye).m_82541_();
            if (dir.m_82526_(look) >= minDot) {
                out.add(e);
            }
        }
        return out;
    }
}
