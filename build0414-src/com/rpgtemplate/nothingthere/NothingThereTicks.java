package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 一无所有的计时器：goodbye 起手结束的落地、茧孵化、流血跳伤。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NothingThereTicks {
    private NothingThereTicks() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Runs on both sides so the 一无所有 body size refreshes on the client too.
        NtBody.refreshIfChanged(event.player);
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        long now = player.m_284548_().m_46467_();
        double strikeAt = data.value(NtForms.KEY_GB_STRIKE_AT, 0.0);
        if (strikeAt > 0.0 && now >= strikeAt) {
            data.setValue(NtForms.KEY_GB_STRIKE_AT, 0.0);
            SystemDataManager.markDirty(player);
            GoodbyeSkill.strike(player, data);
        }
        double hatch = data.value(NtForms.KEY_COCOON_HATCH, 0.0);
        if (hatch > 0.0 && now >= hatch) {
            NothingThereDeath.hatch(player, data);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NtBleed.tick();
        }
    }
}
