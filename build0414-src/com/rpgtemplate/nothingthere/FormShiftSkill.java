package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** 变形: cycles to the next unlocked form. Changing form resets 适应. */
public final class FormShiftSkill implements Skill {
    static final double BASE_COST = 0.01;
    static final int COOLDOWN = 10;

    @Override
    public void onPress(SkillContext ctx, HeldState held) {
        PlayerSystemData data = ctx.data;
        int cur = NtForms.current(data);
        int next = cur;
        for (int i = 1; i < NtForms.NAMES.length; i++) {
            int candidate = (cur + i) % NtForms.NAMES.length;
            if (NtForms.unlocked(data, candidate)) {
                next = candidate;
                break;
            }
        }
        if (next == cur) {
            Msg.actionBar(ctx.player, (Component) Component.m_237113_("没有其他已解锁的形态").m_130940_(ChatFormatting.GRAY));
            return;
        }
        if (!ctx.consume(ctx.cost(BASE_COST))) {
            return;
        }
        data.setValue(NtForms.KEY_FORM, next);
        data.setValue(NtForms.KEY_ADAPT, 0.0);
        SystemDataManager.markDirty((Player) ctx.player);
        ctx.cooldown(COOLDOWN);
        Msg.actionBar(ctx.player, (Component) Component.m_237113_("形态：" + NtForms.NAMES[next]).m_130940_(ChatFormatting.AQUA));
    }
}
