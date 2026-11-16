package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;

/**
 * 蛋（茧态）期间禁止使用本模板的技能。被动不受影响（被动在事件里运作，不经过技能入口）。
 * 包装所有一无所有技能；蛋态下 onPress / onHoldTick / onRelease 都不转发。
 */
final class EggBlocked implements Skill {
    private final Skill inner;

    EggBlocked(Skill inner) {
        this.inner = inner;
    }

    private static boolean inEgg(SkillContext ctx) {
        return NtForms.inCocoon(ctx.player, ctx.data);
    }

    @Override
    public boolean holdable() {
        return inner.holdable();
    }

    @Override
    public boolean bypassCooldown(SkillContext ctx) {
        return !inEgg(ctx) && inner.bypassCooldown(ctx);
    }

    @Override
    public void onPress(SkillContext ctx, HeldState held) {
        if (!inEgg(ctx)) {
            inner.onPress(ctx, held);
        }
    }

    @Override
    public void onHoldTick(SkillContext ctx, HeldState held) {
        if (!inEgg(ctx)) {
            inner.onHoldTick(ctx, held);
        }
    }

    @Override
    public void onRelease(SkillContext ctx, HeldState held) {
        if (!inEgg(ctx)) {
            inner.onRelease(ctx, held);
        }
    }
}
