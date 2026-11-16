package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 假面骑士 Cronus 世界特效网络广播辅助（NeoForge 1.21.1）。
 */
public final class CronusNet {
    private static final double RANGE = 64.0;

    private CronusNet() {
    }

    public static void sendNear(ServerLevel level, double x, double y, double z, CronusFxMsg msg) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, RANGE, msg);
    }

    public static void sendTrackingAndSelf(ServerPlayer player, CronusFxMsg msg) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, msg);
    }

    public static void sendTo(ServerPlayer target, CronusFxMsg msg) {
        if (target != null && target.connection != null) {
            PacketDistributor.sendToPlayer(target, msg);
        }
    }

    public static void syncSuitState(ServerPlayer player, boolean transformed) {
        syncSuitState(player, transformed, 0, 0);
    }

    public static void syncSuitState(ServerPlayer player, boolean transformed, int revealDelayTicks, int beltGashatDelayTicks) {
        if (player == null) {
            return;
        }
        sendTrackingAndSelf(player, new CronusFxMsg(
            CronusFxMsg.KIND_SUIT_STATE, player.getId(),
            player.getX(), player.getY(), player.getZ(),
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
        long now = player.level().getGameTime();
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
            long now = subject.level().getGameTime();
            remainReveal = (int) Math.max(0L, Math.round(data.value(CronusKeys.KEY_SUIT_REVEAL_TICK, 0.0)) - now);
            remainGashat = (int) Math.max(0L, Math.round(data.value(CronusKeys.KEY_BELT_GASHAT_TICK, 0.0)) - now);
        }
        sendTo(viewer, new CronusFxMsg(
            CronusFxMsg.KIND_SUIT_STATE, subject.getId(),
            subject.getX(), subject.getY(), subject.getZ(),
            remainGashat, 0.0, 0.0,
            remainReveal, transformed ? 1 : 0));
    }

    public static void popupOn(Entity target, int popupType) {
        if (target.level() instanceof ServerLevel sl) {
            sendNear(sl, target.getX(), target.getY() + target.getBbHeight() * 0.65, target.getZ(),
                new CronusFxMsg(CronusFxMsg.KIND_ARCADE_POPUP, target.getId(),
                    target.getX(), target.getY() + target.getBbHeight() * 0.65, target.getZ(),
                    1.0f, popupType));
        }
    }
}
