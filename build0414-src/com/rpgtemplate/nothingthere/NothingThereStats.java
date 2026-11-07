package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.RuleJudge;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 一无所有被动：高速回血（随等级缩放，精神/灵魂创伤下减半）、物理免疫（随等级提升，高阶对抗受四档判定约束）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NothingThereStats {
    static final String REGEN = "rpgtemplate:rapid_regen";
    static final String IMMUNITY = "rpgtemplate:physical_immunity";
    /** 不属于物理伤害的消息 ID（魔法、凋零、环境等）。 */
    private static final Set<String> NON_PHYSICAL = Set.of(
            "magic", "indirectMagic", "wither", "dragonBreath", "starve", "drown",
            "onFire", "inFire", "lava", "hotFloor", "outOfWorld", "sonic_boom", "indirectSonicBoom");

    private NothingThereStats() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        long now = player.m_284548_().m_46467_();
        if (now % 20L != 0L) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null || NtForms.inCocoon(player, data)) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, REGEN);
        if (trait.isEmpty()) {
            return;
        }
        double basePercent = TraitLookup.param(trait.get(), "base_percent_per_second", 0.015);
        double perLevel = TraitLookup.param(trait.get(), "per_level", 0.0004);
        double maxPercent = TraitLookup.param(trait.get(), "max_percent_per_second", 0.039);
        double percent = Math.min(maxPercent, basePercent + data.level() * perLevel);
        if (data.value(NtForms.KEY_MENTAL_WOUND_UNTIL, 0.0) > (double) now) {
            percent *= 0.5;
        }
        double max = StatCalculator.maxStat(StatType.HP, data, TemplateRegistry.get(), CoreConfig.get());
        if (data.hp() >= max) {
            return;
        }
        data.setHp(Math.min(max, data.hp() + max * percent));
        SystemDataManager.markDirty(player);
    }

    /** 高优先级：先于模组自己的受伤处理（LOW）减免物理伤害；若攻击者位格更高，按四档机制判定压制绝对免伤上限。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, IMMUNITY);
        if (trait.isEmpty()) {
            return;
        }
        DamageSource source = event.getSource();
        if (NtMimicCombat.fromMimic(source) || !isPhysical(source)) {
            return;
        }
        double perLevel = TraitLookup.param(trait.get(), "per_level", 0.025);
        double cap = TraitLookup.param(trait.get(), "max", 1.0);
        Entity src = source.m_7639_();
        if (src instanceof ServerPlayer attacker && attacker != player && SystemDataManager.available(attacker)) {
            PlayerSystemData attackerData = SystemDataManager.get(attacker);
            if (attackerData.isActivated()) {
                RuleJudge.Result check = RuleJudge.evaluate(attacker, attackerData, player, StatType.SP);
                cap = switch (check.tier()) {
                    case FULL -> Math.min(cap, 0.55);
                    case MAJOR -> Math.min(cap, 0.75);
                    case MINOR -> Math.min(cap, 0.90);
                    case FAIL -> cap;
                };
            }
        }
        double reduction = Math.min(cap, data.level() * perLevel);
        event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
    }

    static boolean isPhysical(DamageSource source) {
        if (ModDamageTypes.isSoulDamage(source) || ModDamageTypes.bypassesSystemHp(source)) {
            return false;
        }
        return !NON_PHYSICAL.contains(source.m_19385_());
    }
}
