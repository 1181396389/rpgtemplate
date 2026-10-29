package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * oh man：点按为直锤（快速单体），按住至少 1 秒松开为砸下（范围砸击 + 击退）。
 * 消耗与冷却在松开时结算。
 */
public final class OhManSkill implements Skill {
    static final int SLAM_MIN_TICKS = 20;     // 1 秒，达到后为砸下
    static final int SLAM_FULL_TICKS = 60;    // 3 秒，砸下伤害倍率封顶
    static final double DIRECT_RANGE = 4.5;
    static final double DIRECT_WIDTH = 0.3;
    static final double DIRECT_BASE_COST = 0.02;
    static final int DIRECT_COOLDOWN = 6;     // 0.3 秒
    static final double SLAM_RADIUS = 4.0;
    static final double SLAM_BASE_COST = 0.1;
    static final int SLAM_COOLDOWN = 30;      // 1.5 秒
    static final double SLAM_KNOCKBACK = 0.6;
    static final int CHARGE_MAX_TICKS = 100;  // 蓄力动作的保险时长（5 秒）：松开时立即清除
    static final int SLAM_ANIM_TICKS = 8;     // 砸下动作（客户端 NtSlamAnim）的同步窗口

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext ctx, HeldState held) {
        // 按下只记录时长，结算在松开时进行。同时开始蓄力动作（右臂举过头顶，客户端 NtSlamAnim）。
        ctx.data.setValue(NtForms.KEY_OH_CHARGE_UNTIL, (double) (ctx.now + CHARGE_MAX_TICKS));
        SystemDataManager.markDirty(ctx.player);
    }

    @Override
    public void onRelease(SkillContext ctx, HeldState held) {
        // 松开即结束蓄力动作；砸下（若成立）会在 slam() 里再开启砸下动作。
        PlayerSystemData data = ctx.data;
        data.setValue(NtForms.KEY_OH_CHARGE_UNTIL, 0.0);
        SystemDataManager.markDirty(ctx.player);
        int ticks = held == null ? 0 : held.heldTicks(ctx.now);
        if (ticks < SLAM_MIN_TICKS) {
            direct(ctx);
        } else {
            slam(ctx, ticks);
        }
    }

    /** 点按：按当前形态决定攻击方式。形态 0 为直锤（原有行为）。 */
    private static void direct(SkillContext ctx) {
        if (!ctx.consume(ctx.cost(DIRECT_BASE_COST))) {
            return;
        }
        ctx.cooldown(DIRECT_COOLDOWN);
        switch (NtForms.current(ctx.data)) {
            case 1 -> NtForms.sweep(ctx.player, ctx.data, 3.5, 120.0, ctx.damage(0.8), 0.0, 0.0);       // 手刀：横斩
            case 2 -> bone(ctx);                                                                         // 骨刺连射
            case 3 -> bite(ctx);                                                                         // 贪食之口
            case 4 -> NtForms.sweep(ctx.player, ctx.data, 5.0, 60.0, ctx.damage(1.2), 1.2, 0.0);       // 脊骨鞭
            case 5 -> NtForms.sweep(ctx.player, ctx.data, 3.5, 120.0, ctx.damage(1.0), 0.6, 0.10);     // 全收
            default -> hammer(ctx);
        }
    }

    private static void hammer(SkillContext ctx) {
        float damage = ctx.damage(1.0);
        List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, DIRECT_RANGE, 1, DIRECT_WIDTH);
        for (TargetUtil.Hit hit : hits) {
            TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), damage);
        }
    }

    /** 骨刺连射：沿视线最多 3 个目标，每个 0.5 倍。 */
    private static void bone(SkillContext ctx) {
        float damage = ctx.damage(0.5);
        for (TargetUtil.Hit hit : TargetUtil.ray(ctx.player, 16.0, 3, 0.5)) {
            TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), damage);
        }
    }

    /** 贪食之口：近距离单体咬击，按伤害 15% 吸血。 */
    private static void bite(SkillContext ctx) {
        float damage = ctx.damage(1.2);
        List<TargetUtil.Hit> hits = TargetUtil.ray(ctx.player, 3.5, 1, 0.4);
        for (TargetUtil.Hit hit : hits) {
            if (TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), damage)) {
                NtForms.heal(ctx.player, ctx.data, damage * 0.15);
            }
        }
    }

    private static void slam(SkillContext ctx, int ticks) {
        if (!ctx.consume(ctx.cost(SLAM_BASE_COST))) {
            return;
        }
        ctx.cooldown(SLAM_COOLDOWN);
        // 砸下动作：服务端写入截止时间，NtBody 同步给所有客户端（右臂急速劈下）。
        ctx.data.setValue(NtForms.KEY_OH_SLAM_UNTIL, (double) (ctx.now + SLAM_ANIM_TICKS));
        SystemDataManager.markDirty(ctx.player);
        double charge = Math.min(1.0, (ticks - SLAM_MIN_TICKS) / (double) (SLAM_FULL_TICKS - SLAM_MIN_TICKS));
        float damage = ctx.damage(3.0 * (1.0 + charge));
        double px = ctx.player.m_20185_();
        double pz = ctx.player.m_20189_();
        ServerLevel level = (ServerLevel) ctx.player.m_9236_();
        // 落点血效果：地面血溅 + 血雾环 + 血滴飞溅（范围与砸击半径一致）
        NtGore.castMist(level, ctx.player);
        AABB box = ctx.player.m_20191_().m_82400_(SLAM_RADIUS);
        List<Entity> found = ctx.player.m_9236_().m_6249_(ctx.player, box, e -> TargetUtil.valid(ctx.player, e));
        for (Entity entity : found) {
            double dx = entity.m_20185_() - px;
            double dz = entity.m_20189_() - pz;
            if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS) {
                continue;
            }
            if (!TargetUtil.hurt(entity, ModDamageTypes.technique(ctx.player), damage)) {
                continue;
            }
            NtGore.hitBurst(level, entity);
            LivingEntity living = TargetUtil.living(entity);
            if (living != null) {
                double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                living.m_5997_(dx / len * SLAM_KNOCKBACK, 0.4, dz / len * SLAM_KNOCKBACK);
            }
        }
    }
}
