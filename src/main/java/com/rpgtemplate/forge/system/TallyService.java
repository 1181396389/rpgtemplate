package com.rpgtemplate.forge.system;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.item.TallyBinding;
import com.rpgtemplate.forge.world.TallyWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class TallyService {
    private TallyService() {
    }

    public static void onUse(ServerPlayer var0, ItemStack var1) {
        TallyWorldData var2 = TallyWorldData.get(var0.server);
        TallyBinding var3 = TallyBinding.get(var1);
        if (!var2.hasOwner()) {
            if (var3 != null && !var3.owner().equals(var0.getUUID())) {
                Msg.chat(var0, Msg.system(Msg.tr("tally.counterfeit").withStyle(ChatFormatting.GRAY)));
            } else {
                var2.bind(var0, var0.serverLevel().getGameTime());
                TallyBinding.set(var1, new TallyBinding(var0.getUUID(), var0.getGameProfile().getName()));
                var0.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
                SystemActions.activate(var0);
            }
        } else if (var2.isOwner(var0)) {
            if (var3 != null && var3.owner().equals(var0.getUUID())) {
                PlayerSystemData var4 = SystemDataManager.get(var0);
                if (!var4.isActivated()) {
                    SystemActions.activate(var0);
                } else {
                    SystemActions.sendStatus(var0, var0);
                }
            } else {
                Msg.chat(var0, Msg.system(Msg.tr("tally.counterfeit").withStyle(ChatFormatting.GRAY)));
            }
        } else {
            Msg.chat(var0, Msg.system(Msg.tr("tally.already_bound", var2.ownerName()).withStyle(ChatFormatting.RED)));
        }
    }
}
