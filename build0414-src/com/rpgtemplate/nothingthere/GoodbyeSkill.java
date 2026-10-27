package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.combat.DamageHandler;
import com.rpgtemplate.forge.combat.WorldSlashDamage;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * goodbye（两段式）：
 * 1. 起手（WINDUP_TICKS）：不造成伤害；期间无敌（伤害归零），并获得抗性提升。
 * 2. 起手结束：巨大的落地斩击，落在玩家前方 6 格范围内。范围内所有生物都被强制命中（无视无敌帧与攻击取消）。
 * 落地后继续有 RECOVERY_TICKS 的减伤窗口。
 * 动画与数值均为占位，最终由设计再定。
 */
public final class GoodbyeSkill implements Skill {
    static final double BASE_COST = 0.15;
    static final int COOLDOWN = 60;            // 3 秒（从按下开始计）
    static final int WINDUP_TICKS = 20;        // 起手，占位
    static final int RECOVERY_TICKS = 20;      // 落地后的减伤窗口，占位
    static final double REACH = 6.0;
    /** 落地伤害倍率（作用于 JSON 系数 4.0 之上）；v15 为 1.0，本版为 5 倍，占位。 */
    static final double STRIKE_SCALE = 5.0;
    static final double AIRBORNE_MULT = 1.5;   // 草案：空中起手 ×1.5
    static final int RESIST_AMP = 1;           // 抗性提升 II（约 40%）
    static final double DAMAGE_REDUCTION = 0.3; // 减伤 30%（草案）

    @Override
    public void onPress(SkillContext ctx, HeldState held) {
        if (!ctx.consume(ctx.cost(BASE_COST))) {
            return;
        }
        ctx.cooldown(COOLDOWN);
        PlayerSystemData data = ctx.data;
        long now = ctx.now;
        boolean airborne = !ctx.player.m_20096_();
        int window = WINDUP_TICKS + RECOVERY_TICKS;
        data.setValue(NtForms.KEY_GB_STRIKE_AT, (double) (now + WINDUP_TICKS));
        data.setValue(NtForms.KEY_GB_GUARD_UNTIL, (double) (now + window));
        data.setValue(NtForms.KEY_GB_SLOT, (double) ctx.slot);
        data.setValue(NtForms.KEY_GB_AIRBORNE, airborne ? 1.0 : 0.0);
        ctx.player.m_7292_(new MobEffectInstance(NtForms.EFFECT_RESIST, window + 10, RESIST_AMP, false, false, false));
        SystemDataManager.markDirty(ctx.player);
    }

    /** 起手结束时由 NothingThereTicks 调用：结算落地斩击（强制命中范围内所有生物）。 */
    static void strike(ServerPlayer player, PlayerSystemData data) {
        SkillContext ctx = SkillContext.create(player, (int) data.value(NtForms.KEY_GB_SLOT, 0.0));
        if (ctx == null) {
            return;
        }
        boolean airborne = data.value(NtForms.KEY_GB_AIRBORNE, 0.0) > 0.5;
        Vec3 look = player.m_20154_();
        double lx = look.f_82479_;
        double lz = look.f_82481_;
        double len = Math.hypot(lx, lz);
        if (len < 1e-6) {
            lx = 0.0;
            lz = 1.0;
        } else {
            lx /= len;
            lz /= len;
        }
        double half = REACH / 2.0;
        double cx = player.m_20185_() + lx * half;
        double cz = player.m_20189_() + lz * half;
        double cy = player.m_20186_();
        AABB box = new AABB(cx - half, cy - 1.5, cz - half, cx + half, cy + 2.5, cz + half);
        float damage = (float) (ctx.damage(STRIKE_SCALE) * (airborne ? AIRBORNE_MULT : 1.0));
        Set<LivingEntity> targets = new LinkedHashSet<>();
        for (Entity entity : player.m_9236_().m_6249_(player, box, e -> TargetUtil.valid(player, e))) {
            LivingEntity living = TargetUtil.living(entity);
            if (living != null && living != player) {
                targets.add(living);
            }
        }
        for (LivingEntity target : targets) {
            forceHit(player, target, damage);
        }
    }

    /**
     * 强制命中：不走 hurt / attack 事件，因此无视无敌帧（invulnerableTime、伤害归零的茧态）
     * 以及其他模组在攻击事件里的取消（附身、火焰被动等）。
     * 玩家目标先经过系统生命（按其防御减免）再扣原版生命；生命归零时走原版死亡流程（茧仍可取消死亡）。
     */
    static void forceHit(ServerPlayer attacker, LivingEntity target, float damage) {
        float remaining = damage;
        if (target instanceof ServerPlayer sp) {
            PlayerSystemData targetData = NtForms.activeData(sp);
            if (targetData != null) {
                remaining = DamageHandler.absorb(sp, targetData, damage, false);
                SystemDataManager.markDirty(sp);
            }
        }
        if (remaining > 0.0f) {
            float after = target.m_21223_() - remaining;
            if (after > 0.0f) {
                target.m_21153_(after);
            } else {
                target.m_6667_(WorldSlashDamage.source(attacker));
            }
        }
        NtBleed.afterHit(target, attacker, damage);
    }
}
