package com.rpgtemplate.forge.event;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.command.RpgTemplateCommand;
import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.system.PassiveApplier;
import com.rpgtemplate.forge.system.RuntimeState;
import com.rpgtemplate.forge.system.StatTicker;
import com.rpgtemplate.forge.system.SystemActions;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.template.TemplateReloadListener;
import com.rpgtemplate.forge.template.TemplateSync;
import com.rpgtemplate.forge.world.TallyWorldData;
import com.rpgtemplate.forge.xp.XpEvents;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class GameEvents {
    private GameEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent var0) {
        RpgTemplateCommand.register(var0.getDispatcher());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent var0) {
        var0.addListener(new TemplateReloadListener());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent var0) {
        (var0.getPlayer() == null ? var0.getPlayerList().getPlayers() : List.of(var0.getPlayer())).forEach(var0x -> {
            TemplateSync.send(var0x);
            SystemDataManager.sync(var0x);
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            XpEvents.tickTrades(var1);
            PlayerSystemData var4 = SystemDataManager.get(var1);
            TallyWorldData var3 = TallyWorldData.get(var1.server);
            if (var4.isActivated() && !var3.isOwner(var1)) {
                RpgTemplateMod.LOGGER.info("[RPG转生模板] {} 不再是虎符持有者，清除系统数据", var1.getGameProfile().getName());
                var4.reset();
            }

            PassiveApplier.refresh(var1);
            SystemDataManager.sync(var1);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && SystemDataManager.available(var1)) {
            DomainExpansionSkill.end(var1, "recalled");
        }

        UUID var3 = var0.getEntity().getUUID();
        SystemActions.clearPending(var3);
        SkillManager.forget(var3);
        RuntimeState.forget(var3);
        XpEvents.forget(var3);
        SystemDataManager.forget(var3);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PlayerSystemData var3 = SystemDataManager.get(var1);
            if (var3.isActivated()) {
                StatCalculator.refill(var3, TemplateRegistry.get(), CoreConfig.get());
            }

            SkillManager.cancel(var1);
            RuntimeState.forget(var1.getUUID());
            PassiveApplier.refresh(var1);
            SystemDataManager.sync(var1);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerChangedDimensionEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            DomainExpansionSkill.end(var1, "recalled");
            SkillManager.cancel(var1);
            PassiveApplier.refresh(var1);
            SystemDataManager.sync(var1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && !var1.isRemoved()) {
            SystemActions.tickPendingLoad(var1);
            StatTicker.tick(var1);
        }
    }
}
