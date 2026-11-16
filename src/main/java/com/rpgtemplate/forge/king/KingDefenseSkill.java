package com.rpgtemplate.forge.king;

import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.system.SystemDataManager;

public final class KingDefenseSkill implements Skill {
    private final boolean basket;

    public KingDefenseSkill(boolean var1) {
        this.basket = var1;
    }

    @Override
    public boolean bypassCooldown(SkillContext var1) {
        return KingRules.defense(var1.data, this.basket ? "king.basket" : "king.amp", var1.now);
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        if (KingRules.active(var1.player)) {
            String var3 = this.basket ? "king.basket" : "king.amp";
            if (KingRules.defense(var1.data, var3, var1.now)) {
                KingRules.stop(var1.player, var3, true);
            } else if (!this.basket && DomainExpansionSkill.active(var1.data, var1.now)) {
                KingRules.message(var1.player, "请先收束自己的领域，再开启领域展延。");
            } else if (!this.basket && SkillManager.isHolding(var1.player)) {
                KingRules.message(var1.player, "请先结束当前施放。");
            } else {
                double var4 = KingRules.max(var1.data, StatType.MP) * (this.basket ? 0.02 : 0.015);
                if (var1.consume(var4)) {
                    KingRules.stop(var1.player, this.basket ? "king.amp" : "king.basket", false);
                    if (this.basket) {
                        ExtraHands.release(var1.player);
                    }

                    var1.data.setValue(var3, (double)(var1.now + (long)(this.basket ? 300 : 200)));
                    var1.data.setValue(var3 + ".last", (double)var1.now);
                    SystemDataManager.markDirty(var1.player);
                    ExtraHands.sync(var1.player);
                    KingRules.message(var1.player, this.basket ? "弥虚葛笼：额外双臂结印，全部正常伤害减免 60%（虚空、/kill除外）。" : "领域展延：全部正常伤害减免 35%（虚空、/kill除外），生得术式暂时封锁。");
                }
            }
        }
    }
}
