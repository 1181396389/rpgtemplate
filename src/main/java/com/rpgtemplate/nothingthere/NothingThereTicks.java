package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/** 一无所有的计时器：goodbye 起手结束的落地、茧孵化、流血跳伤。 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class NothingThereTicks {
    private NothingThereTicks() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Runs on both sides so the 一无所有 body size refreshes on the client too.
        NtBody.refreshIfChanged(event.getEntity());
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
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
    public static void onServerTick(ServerTickEvent.Post event) {
        if (true) {
            NtBleed.tick();
        }
    }
}
