package com.rpgtemplate.nothingthere;

import com.google.gson.JsonObject;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 一无所有的死亡侧逻辑：茧（复活）、死亡记忆（记录击杀者）、击杀进化（计数与解锁）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NothingThereDeath {
    static final String COCOON = "rpgtemplate:cocoon_revive";
    private static final Logger LOG = LogManager.getLogger("rpgtemplate/Cocoon");
    static final String KILL_EVOLVE = "rpgtemplate:kill_evolve";
    static final String DEATH_MEMORY = "rpgtemplate:death_memory";

    private NothingThereDeath() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity killer = event.getSource().m_7639_();
        if (victim instanceof ServerPlayer player) {
            if (tryCocoon(event, player)) {
                return; // in a cocoon: no death, so no death memory
            }
            LOG.info("[Cocoon] player death not prevented: source={} cocoon_hatch={} cocoon_until={}",
                event.getSource().m_19385_(),
                NtForms.activeData(player) == null ? "no-data" : NtForms.activeData(player).value(NtForms.KEY_COCOON_HATCH, 0.0),
                NtForms.activeData(player) == null ? "no-data" : NtForms.activeData(player).value(NtForms.KEY_COCOON_UNTIL, 0.0));
            clearGoodbye(player);
            recordDeathMemory(player, killer);
        }
        if (killer instanceof ServerPlayer hunter && hunter != victim) {
            countKill(hunter, victim);
        }
    }

    /**
     * 蛋（茧）：死亡时取消死亡，HP 留 1，进入茧态（减速 255、无敌），茧期间 hatch_ticks 后孵化满血复活。
     * 冷却从死亡开始计算；冷却 = 20 分钟，每级减少 1%，最低 0 分钟（v16 起）。
     */
    private static boolean tryCocoon(LivingDeathEvent event, ServerPlayer player) {
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return false;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, COCOON);
        if (trait.isEmpty()) {
            return false;
        }
        long now = player.m_284548_().m_46467_();
        // 已经在蛋态中：继续挡下这次死亡，但不重置孵化计时、不检查冷却、不重复发消息。
        // 之前每次死亡都会把孵化推迟 6 秒，高频攻击下孵化永远到不了（用户反馈：来不及复活就死亡）。
        if (NtForms.inCocoon(player, data)) {
            event.setCanceled(true);
            player.m_21153_(1.0f);
            data.setHp(1.0);
            SystemDataManager.markDirty((Player) player);
            return true;
        }
        if (data.value(NtForms.KEY_COCOON_UNTIL, 0.0) > (double) now) {
            LOG.info("[Cocoon] on cooldown, death not prevented (until={}, now={})",
                data.value(NtForms.KEY_COCOON_UNTIL, 0.0), now);
            return false;
        }
        double baseMinutes = TraitLookup.param(trait.get(), "base_cooldown_minutes", 20.0);
        double perLevel = TraitLookup.param(trait.get(), "cooldown_reduction_per_level", 0.01);
        double minMinutes = TraitLookup.param(trait.get(), "min_cooldown_minutes", 0.0);
        long hatchTicks = Math.round(TraitLookup.param(trait.get(), "hatch_ticks", 120.0));
        // 冷却可以降到 0（用户修正：茧的冷却下限为 0）。之前上限为 0.99，高等级时仍剩 12 秒，
        // 而孵化只要 6 秒，所以孵化后马上再死会被冷却挡住。
        double reduction = Math.min(1.0, data.level() * perLevel);
        double minutes = Math.max(minMinutes, baseMinutes * (1.0 - reduction));
        long cooldownTicks = Math.round(minutes * 60.0 * 20.0);
        String nextText = cooldownTicks <= 0 ? "随时可用" : (minutes < 1.0
            ? "约 " + Math.round(minutes * 60.0) + " 秒后" : "约 " + Math.round(minutes) + " 分钟后");

        event.setCanceled(true);
        player.m_21153_(1.0f);
        data.setHp(1.0);
        data.setValue(NtForms.KEY_COCOON_UNTIL, (double) (now + cooldownTicks));
        data.setValue(NtForms.KEY_COCOON_HATCH, (double) (now + hatchTicks));
        clearGoodbye(player);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.m_7292_(new MobEffectInstance(NtForms.EFFECT_SLOW, (int) hatchTicks + 10, 255, false, false, false));
        Msg.chat(player, (Component) Component.m_237113_("蛋：你化为蛋，将在 " + String.format("%.1f", hatchTicks / 20.0)
            + " 秒后孵化。下次可用：" + nextText + "。")
            .m_130940_(ChatFormatting.DARK_PURPLE));
        return true;
    }

    /** 茧孵化：满血复活，解除茧态。 */
    static void hatch(ServerPlayer player, PlayerSystemData data) {
        data.setValue(NtForms.KEY_COCOON_HATCH, 0.0);
        double maxHp = StatCalculator.maxStat(StatType.HP, data, TemplateRegistry.get(), CoreConfig.get());
        data.setHp(maxHp);
        player.m_21153_(player.m_21233_());
        player.m_21195_(NtForms.EFFECT_SLOW);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        Msg.chat(player, (Component) Component.m_237113_("蛋孵化：你重新站了起来。")
            .m_130940_(ChatFormatting.DARK_PURPLE));
    }

    /** 死亡或进入茧时，取消尚未落地的 goodbye。 */
    private static void clearGoodbye(ServerPlayer player) {
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        data.setValue(NtForms.KEY_GB_STRIKE_AT, 0.0);
        data.setValue(NtForms.KEY_GB_GUARD_UNTIL, 0.0);
    }

    /** 死亡记忆：记录击杀者的次数，之后该击杀者造成的伤害被减免（见 NothingThereCombat）。 */
    private static void recordDeathMemory(ServerPlayer player, Entity killer) {
        if (killer == null || killer == player) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null || TraitLookup.activePassive(data, DEATH_MEMORY).isEmpty()) {
            return;
        }
        String key = NtForms.KEY_DM + killer.m_20148_();
        data.setValue(key, data.value(key, 0.0) + 1.0);
        SystemDataManager.markDirty((Player) player);
    }

    /** 击杀进化：按类型计数，按顺序解锁形态 2、3、4，全部解锁后解锁形态 5（全收）。 */
    private static void countKill(ServerPlayer hunter, LivingEntity victim) {
        String category = victim instanceof Player ? "player"
            : victim instanceof Enemy ? "monster"
            : victim instanceof Animal ? "animal" : null;
        if (category == null) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(hunter);
        if (data == null) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, KILL_EVOLVE);
        if (trait.isEmpty()) {
            return;
        }
        String key = NtForms.KEY_KILL + category;
        data.setValue(key, data.value(key, 0.0) + 1.0);
        checkUnlocks(hunter, data, trait.get());
        SystemDataManager.markDirty((Player) hunter);
        SystemDataManager.sync(hunter);
    }

    private static void checkUnlocks(ServerPlayer hunter, PlayerSystemData data, TraitEntry trait) {
        String[] categories = {"monster", "player", "animal"};
        int[] forms = {2, 3, 4};
        double[] defaults = {30.0, 5.0, 50.0};
        for (int i = 0; i < forms.length; i++) {
            if (NtForms.unlocked(data, forms[i])) {
                continue;
            }
            double need = threshold(trait, categories[i], defaults[i]);
            if (data.value(NtForms.KEY_KILL + categories[i], 0.0) < need) {
                break; // later forms wait for this one (unlock order)
            }
            NtForms.unlock(data, forms[i]);
            announce(hunter, NtForms.NAMES[forms[i]]);
        }
        if (!NtForms.unlocked(data, 5) && NtForms.unlocked(data, 2)
                && NtForms.unlocked(data, 3) && NtForms.unlocked(data, 4)) {
            NtForms.unlock(data, 5);
            announce(hunter, NtForms.NAMES[5]);
        }
    }

    private static double threshold(TraitEntry trait, String category, double fallback) {
        JsonObject params = trait.params();
        if (params == null || !params.has("thresholds")) {
            return fallback;
        }
        JsonObject thresholds = params.getAsJsonObject("thresholds");
        return thresholds.has(category) ? thresholds.get(category).getAsDouble() : fallback;
    }

    private static void announce(ServerPlayer hunter, String formName) {
        Msg.chat(hunter, (Component) Component.m_237113_("击杀进化：解锁形态【" + formName + "】")
            .m_130940_(ChatFormatting.LIGHT_PURPLE));
    }
}
