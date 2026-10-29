package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;

/**
 * 一无所有面板被动的类型 ID。数据在 data/rpgtemplate/rpg_templates/nothing_there.json 的 passives 中，
 * 所以这些被动会显示在模板面板里。效果实现见 NtPanel（夜视、异常免疫、击杀饱食）、NtNonHuman（非人体质）和 NtHungerMixin（延缓饥饿）。
 */
public final class NtPassives {
    public static final String NIGHT_VISION = "rpgtemplate:night_vision";
    public static final String DEBUFF_IMMUNITY = "rpgtemplate:debuff_immunity";
    public static final String NON_HUMAN = "rpgtemplate:non_human_body";
    public static final String HUNGER_SAVE = "rpgtemplate:hunger_save";
    public static final String KILL_FEED = "rpgtemplate:kill_feed";

    private NtPassives() {
    }

    public static Optional<TraitEntry> active(PlayerSystemData data, String type) {
        return TraitLookup.activePassive(data, type);
    }
}
