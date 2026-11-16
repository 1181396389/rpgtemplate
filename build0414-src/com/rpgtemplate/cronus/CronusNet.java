package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 假面骑士 Cronus 世界特效网络广播通道（{@code rpgtemplate:cronus_fx}）。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusNet {
    private static final String PROTOCOL = "1";
    private static final double RANGE = 64.0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("rpgtemplate", "cronus_fx"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals);

    private CronusNet() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(
                0,
                CronusFxMsg.class,
                CronusFxMsg::encode,
                CronusFxMsg::decode,
                CronusFxMsg::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        });
    }

    public static void sendNear(ServerLevel level, double x, double y, double z, CronusFxMsg msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(
            () -> new PacketDistributor.TargetPoint(x, y, z, RANGE, level.m_46472_())), msg);
    }

    public static void sendTrackingAndSelf(ServerPlayer player, CronusFxMsg msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }

    public static void sendTo(ServerPlayer target, CronusFxMsg msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), msg);
    }

    public static void syncSuitState(ServerPlayer player, boolean transformed) {
        syncSuitState(player, transformed, 0, 0);
    }

    public static void syncSuitState(ServerPlayer player, boolean transformed, int revealDelayTicks, int beltGashatDelayTicks) {
        if (player == null) {
            return;
        }
        sendTrackingAndSelf(player, new CronusFxMsg(
            CronusFxMsg.KIND_SUIT_STATE, player.m_19879_(),
            player.m_20185_(), player.m_20186_(), player.m_20189_(),
            Math.max(0, beltGashatDelayTicks), 0.0, 0.0,
            Math.max(0, revealDelayTicks), transformed ? 1 : 0));
    }

    public static void syncSuitState(ServerPlayer player, PlayerSystemData data) {
        if (player == null) {
            return;
        }
        boolean transformed = data != null && CronusKeys.isTransformed(data);
        if (!transformed) {
            syncSuitState(player, false, 0, 0);
            return;
        }
        long now = player.m_9236_().m_46467_();
        int remainReveal = (int) Math.max(0L, Math.round(data.value(CronusKeys.KEY_SUIT_REVEAL_TICK, 0.0)) - now);
        int remainGashat = (int) Math.max(0L, Math.round(data.value(CronusKeys.KEY_BELT_GASHAT_TICK, 0.0)) - now);
        syncSuitState(player, true, remainReveal, remainGashat);
    }

    public static void syncSuitStateTo(ServerPlayer subject, ServerPlayer viewer, PlayerSystemData data) {
        if (subject == null || viewer == null) {
            return;
        }
        boolean transformed = data != null && CronusKeys.isTransformed(data);
        int remainReveal = 0;
        int remainGashat = 0;
        if (transformed) {
            long now = subject.m_9236_().m_46467_();
            remainReveal = (int) Math.max(0L, Math.round(data.value(CronusKeys.KEY_SUIT_REVEAL_TICK, 0.0)) - now);
            remainGashat = (int) Math.max(0L, Math.round(data.value(CronusKeys.KEY_BELT_GASHAT_TICK, 0.0)) - now);
        }
        sendTo(viewer, new CronusFxMsg(
            CronusFxMsg.KIND_SUIT_STATE, subject.m_19879_(),
            subject.m_20185_(), subject.m_20186_(), subject.m_20189_(),
            remainGashat, 0.0, 0.0,
            remainReveal, transformed ? 1 : 0));
    }

    public static void popupOn(Entity target, int popupType) {
        if (target.m_9236_() instanceof ServerLevel sl) {
            sendNear(sl, target.m_20185_(), target.m_20186_() + target.m_20206_() * 0.65, target.m_20189_(),
                new CronusFxMsg(CronusFxMsg.KIND_ARCADE_POPUP, target.m_19879_(),
                    target.m_20185_(), target.m_20186_() + target.m_20206_() * 0.65, target.m_20189_(),
                    1.0f, popupType));
        }
    }
}
