package com.rpgtemplate.nothingthere;
import net.minecraft.core.Holder;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 一无所有面板被动：夜视、异常免疫、非人体质（移速与跳跃，见 NtNonHuman）、击杀饱食。延缓饥饿在 NtHungerMixin 中实现。
 * 所有效果都是隐藏、无粒子的环境效果；如果被动未激活，就不会添加。
 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class NtPanel {
    static final long REFRESH_TICKS = 20L;   // 每秒刷新一次效果
    static final long SYNC_TICKS = 10L;     // 每半秒同步一次形态属性
    static final int EFFECT_TICKS = 60;      // 效果持续 3 秒，刷新时间早于过期时间

    /** 异常免疫会阻止的效果。减速不在列表中，因为茧使用减速（等级 255）。 */
    static final List<Holder<MobEffect>> BLOCKED = List.of(
        MobEffects.BLINDNESS,      // 失明
        MobEffects.DARKNESS,     // 黑暗
        MobEffects.CONFUSION,      // 反胃
        MobEffects.WEAKNESS,      // 虚弱
        MobEffects.POISON,      // 中毒
        MobEffects.DIG_SLOWDOWN,      // 挖掘疲劳
        MobEffects.HUNGER);     // 饥饿

    private NtPanel() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long tick = player.level().getGameTime();
        PlayerSystemData data = NtForms.activeData(player);
        int form = data == null ? -1 : NtForms.current(data);
        if (tick % SYNC_TICKS == 0L) {
            NtFormEffects.syncAttributes(player, form);
            NtNonHuman.syncSpeed(player, data);
        }
        if (data == null || tick % REFRESH_TICKS != 0L) {
            return;
        }
        if (NtPassives.active(data, NtPassives.NIGHT_VISION).isPresent()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS, 0, true, false, false));
        }
        TraitLookup.activePassive(data, NtPassives.NON_HUMAN).ifPresent(trait -> {
            int jump = NtNonHuman.jumpAmplifier(data, trait);
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_TICKS, jump, true, false, false));
        });
        if (NtPassives.active(data, NtPassives.DEBUFF_IMMUNITY).isPresent()) {
            for (Holder<MobEffect> effect : BLOCKED) {
                player.removeEffect(effect);
            }
        }
    }

    /** 异常免疫：在效果即将添加时直接拒绝。 */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Holder<MobEffect> effect = event.getEffectInstance().getEffect();
        if (!BLOCKED.contains(effect)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data != null && NtPassives.active(data, NtPassives.DEBUFF_IMMUNITY).isPresent()) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** 击杀饱食：玩家击杀生物后恢复饱食度与饱和度。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        LivingEntity victim = event.getEntity();
        if (!(source instanceof ServerPlayer player) || source == victim) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        TraitLookup.activePassive(data, NtPassives.KILL_FEED).ifPresent(trait -> {
            int food = (int) TraitLookup.param(trait, "food", 4.0);
            float saturation = (float) TraitLookup.param(trait, "saturation", 2.4);
            player.getFoodData().eat(food, saturation);
        });
    }
}
