package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;

/** Holle: a fast bone spike fired along the look direction, hitting the first target within 32 blocks. */
public final class HolleSkill implements Skill {
    static final double BASE_COST = 0.05;
    static final int COOLDOWN = 40;      // 2 秒
    static final double RANGE = 32.0;
    static final double WIDTH = 0.5;

    @Override
    public void onPress(SkillContext ctx, HeldState held) {
        if (!ctx.consume(ctx.cost(BASE_COST))) {
            return;
        }
        ctx.cooldown(COOLDOWN);
        // JSON coefficient is 1.8; ctx.damage(1.0) applies it.
        float damage = ctx.damage(1.0);
        for (TargetUtil.Hit hit : TargetUtil.ray(ctx.player, RANGE, 1, WIDTH)) {
            TargetUtil.hurt(hit.entity(), ModDamageTypes.technique(ctx.player), damage);
        }
    }
}
