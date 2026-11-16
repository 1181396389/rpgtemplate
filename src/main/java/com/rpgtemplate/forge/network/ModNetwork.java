package com.rpgtemplate.forge.network;

import com.rpgtemplate.cronus.CronusFxMsg;
import com.rpgtemplate.forge.client.ClientPayloadHandler;
import com.rpgtemplate.forge.ext.ExtNetwork;
import com.rpgtemplate.forge.fx.FxMsg;
import com.rpgtemplate.forge.king.KingNetwork;
import com.rpgtemplate.forge.possession.PossessionNet;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.skillbar.TacticalSkillMsg;
import com.rpgtemplate.forge.system.PanelActions;
import com.rpgtemplate.nothingthere.GoreMsg;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 统一注册 rpgtemplate 全部 NeoForge 1.21.1 {@code CustomPacketPayload} 网络协议。
 */
@EventBusSubscriber(modid = "rpgtemplate", bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    public static final String PROTOCOL = "neoforge1211-1";

    private ModNetwork() {
    }

    public static void register() {
        // Payload registration now happens via RegisterPayloadHandlersEvent on the MOD event bus.
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL);

        // 1. Core system payloads
        reg.playToClient(SyncSystemDataPayload.TYPE, SyncSystemDataPayload.STREAM_CODEC, (msg, ctx) ->
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.onSystemData(msg);
            }));
        reg.playToClient(SyncTemplatesPayload.TYPE, SyncTemplatesPayload.STREAM_CODEC, (msg, ctx) ->
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.onTemplates(msg);
            }));
        reg.playToClient(RainbowBurstPayload.TYPE, RainbowBurstPayload.STREAM_CODEC, (msg, ctx) ->
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.onRainbow(msg);
            }));
        reg.playToClient(ChantPayload.TYPE, ChantPayload.STREAM_CODEC, (msg, ctx) ->
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.onChant(msg);
            }));
        reg.playToServer(SkillInputPayload.TYPE, SkillInputPayload.STREAM_CODEC, (msg, ctx) ->
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    SkillManager.onInput(sp, msg.slot(), msg.pressed());
                }
            }));
        reg.playToServer(PanelActionPayload.TYPE, PanelActionPayload.STREAM_CODEC, (msg, ctx) ->
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    PanelActions.handle(sp, msg.action(), msg.arg());
                }
            }));

        // 2. Cronus, NothingThere Gore, Non-particle Fx, Tactical Skill Bar
        reg.playToClient(CronusFxMsg.TYPE, CronusFxMsg.STREAM_CODEC, CronusFxMsg::handle);
        reg.playToClient(GoreMsg.TYPE, GoreMsg.STREAM_CODEC, GoreMsg::handle);
        reg.playToClient(FxMsg.TYPE, FxMsg.STREAM_CODEC, FxMsg::handle);
        reg.playToServer(TacticalSkillMsg.TYPE, TacticalSkillMsg.STREAM_CODEC, TacticalSkillMsg::handle);

        // 3. Sukuna Ext, King ExtraHands, Possession
        ExtNetwork.registerPayloads(reg);
        KingNetwork.registerPayloads(reg);
        PossessionNet.registerPayloads(reg);
    }
}
