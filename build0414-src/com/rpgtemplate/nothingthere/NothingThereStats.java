package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 一无所有被动：高速回血（每秒百分比回复，不暂停）、物理免疫（随等级提升）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NothingThereStats {
    static final String REGEN = "rpgtemplate:rapid_regen";
    static final String IMMUNITY = "rpgtemplate:physical_immunity";
    /** 不属于物理伤害的消息 ID（魔法、凋零、环境等）。 */
    private static final Set<String> NON_PHYSICAL = Set.of(
            "magic", "indirectMagic", "wither", "dragonBreath", "starve", "drown",
            "onFire", "inFire", "lava", "hotFloor", "outOfWorld");

    private NothingThereStats() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.m_284548_().m_46467_() % 20L != 0L) {
            return;
        }
        if (!SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, REGEN);
        if (trait.isEmpty()) {
            return;
        }
        double percent = TraitLookup.param(trait.get(), "percent_per_second", 0.05);
        double max = StatCalculator.maxStat(StatType.HP, data, TemplateRegistry.get(), CoreConfig.get());
        if (data.hp() >= max) {
            return;
        }
        data.setHp(Math.min(max, data.hp() + max * percent));
        SystemDataManager.markDirty(player);
    }

    /** 高优先级：先于模组自己的受伤处理（LOW）减免物理伤害。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, IMMUNITY);
        if (trait.isEmpty()) {
            return;
        }
        DamageSource source = event.getSource();
        if (!isPhysical(source)) {
            return;
        }
        double perLevel = TraitLookup.param(trait.get(), "per_level", 0.025);
        double cap = TraitLookup.param(trait.get(), "max", 1.0);
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
