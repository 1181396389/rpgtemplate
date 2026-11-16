package com.rpgtemplate.forge.system;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.RpgTemplateMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class PassiveApplier {
    public static final String STRONG_LUCK = "rpgtemplate:strong_luck";
    public static final String REINFORCEMENT = "rpgtemplate:cursed_energy_reinforcement";
    private static final ResourceLocation LUCK_ID = RpgTemplateMod.id("strong_luck");
    private static final ResourceLocation SPEED_ID = RpgTemplateMod.id("reinforcement_speed");
    private static final ResourceLocation KB_ID = RpgTemplateMod.id("reinforcement_knockback");
    private static final ResourceLocation ATTACK_ID = RpgTemplateMod.id("reinforcement_attack");

    private PassiveApplier() {
    }

    public static void refresh(ServerPlayer player) {
        PlayerSystemData data = SystemDataManager.get(player);
        boolean active = data.isActivated();
        TraitEntry luck = active ? TraitLookup.loadedPassive(data, STRONG_LUCK).orElse(null) : null;
        set(player, Attributes.LUCK, LUCK_ID, luck == null ? 0.0 : TraitLookup.param(luck, "luck_attribute", 1.0), Operation.ADD_VALUE);
        TraitEntry reinf = active ? TraitLookup.loadedPassive(data, REINFORCEMENT).orElse(null) : null;
        if (reinf == null) {
            set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, Operation.ADD_MULTIPLIED_BASE);
            set(player, Attributes.KNOCKBACK_RESISTANCE, KB_ID, 0.0, Operation.ADD_VALUE);
            set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, 0.0, Operation.ADD_VALUE);
        } else {
            double factor = reinforcementFactor(data, reinf);
            double sp = StatCalculator.maxStat(StatType.SP, data, TemplateRegistry.get(), CoreConfig.get());
            set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, TraitLookup.param(reinf, "movement_speed", 0.0) * factor, Operation.ADD_MULTIPLIED_BASE);
            set(player, Attributes.KNOCKBACK_RESISTANCE, KB_ID, Math.min(1.0, TraitLookup.param(reinf, "knockback_resistance", 0.0) * factor), Operation.ADD_VALUE);
            set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, sp * TraitLookup.param(reinf, "attack_damage_sp_ratio", 0.0), Operation.ADD_VALUE);
        }
    }

    public static double reinforcementFactor(PlayerSystemData data, TraitEntry entry) {
        int lvl = data.loadedTemplate().map(TemplateInstance::level).orElse(1);
        return 1.0 + TraitLookup.param(entry, "per_template_level", 0.0) * (double) Math.max(0, lvl - 1);
    }

    public static double damageReduction(PlayerSystemData data) {
        return 0.0;
    }

    private static void set(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id, double val, Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
            if (val != 0.0 && Double.isFinite(val)) {
                inst.addTransientModifier(new AttributeModifier(id, val, op));
            }
        }
    }

    public static double drawLuck(PlayerSystemData data) {
        return TraitLookup.loadedPassive(data, STRONG_LUCK).map(e -> TraitLookup.param(e, "draw_luck", 0.0)).orElse(0.0);
    }
}
