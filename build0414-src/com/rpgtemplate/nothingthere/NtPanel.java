package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 一无所有面板被动：夜视、异常免疫、非人体质（移速与跳跃，见 NtNonHuman）、击杀饱食。延缓饥饿在 NtHungerMixin 中实现。
 * 所有效果都是隐藏、无粒子的环境效果；如果被动未激活，就不会添加。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NtPanel {
    static final long REFRESH_TICKS = 20L;   // 每秒刷新一次效果
    static final long SYNC_TICKS = 10L;     // 每半秒同步一次形态属性
    static final int EFFECT_TICKS = 60;      // 效果持续 3 秒，刷新时间早于过期时间

    /** 异常免疫会阻止的效果。减速不在列表中，因为茧使用减速（等级 255）。 */
    static final List<MobEffect> BLOCKED = List.of(
        MobEffects.f_19610_,      // 失明
        MobEffects.f_216964_,     // 黑暗
        MobEffects.f_19604_,      // 反胃
        MobEffects.f_19613_,      // 虚弱
        MobEffects.f_19614_,      // 中毒
        MobEffects.f_19599_,      // 挖掘疲劳
        MobEffects.f_19612_);     // 饥饿

    private NtPanel() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        long tick = player.m_9236_().m_46467_();
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
            player.m_7292_(new MobEffectInstance(MobEffects.f_19611_, EFFECT_TICKS, 0, true, false, false));
        }
        TraitLookup.activePassive(data, NtPassives.NON_HUMAN).ifPresent(trait -> {
            int jump = NtNonHuman.jumpAmplifier(data, trait);
            player.m_7292_(new MobEffectInstance(MobEffects.f_19603_, EFFECT_TICKS, jump, true, false, false));
        });
        if (NtPassives.active(data, NtPassives.DEBUFF_IMMUNITY).isPresent()) {
            for (MobEffect effect : BLOCKED) {
                player.m_21195_(effect);
            }
        }
    }

    /** 异常免疫：在效果即将添加时直接拒绝。 */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffect effect = event.getEffectInstance().m_19544_();
        if (!BLOCKED.contains(effect)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data != null && NtPassives.active(data, NtPassives.DEBUFF_IMMUNITY).isPresent()) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** 击杀饱食：玩家击杀生物后恢复饱食度与饱和度。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity source = event.getSource().m_7639_();
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
            player.m_36324_().m_38707_(food, saturation);
        });
    }
}
