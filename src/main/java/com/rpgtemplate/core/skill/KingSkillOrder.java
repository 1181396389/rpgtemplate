package com.rpgtemplate.core.skill;

import com.rpgtemplate.core.template.SkillEntry;
import java.util.ArrayList;
import java.util.List;

public final class KingSkillOrder {
    public static List<SkillEntry> appendDefenses(List<SkillEntry> var0) {
        ArrayList var1 = new ArrayList(var0);

        for (String var3 : List.of("rpgtemplate:domain_amplification", "rpgtemplate:hollow_wicker_basket")) {
            SkillEntry var4 = null;

            for (SkillEntry var6 : var0) {
                if (var6.id().equals(var3)) {
                    var4 = var6;
                }
            }

            if (var4 != null) {
                var1.remove(var4);
                var1.add(var4);
            }
        }

        return var1;
    }
}
