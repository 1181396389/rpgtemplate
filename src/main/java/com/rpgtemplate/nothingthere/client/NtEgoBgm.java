package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtBody;
import com.rpgtemplate.nothingthere.NtSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * E.G.O展现 BGM（xuewu）。
 *
 * v34 路径：与技能音效同一类的普通 SimpleSoundInstance（非 tickable 子类、初始音量非 0），
 * 循环、相对定位、MASTER 声道、静态缓冲（stream:false）。展现结束时直接 stop（不再做包络淡出）。
 *
 * 原因：v33 的 tickable 子类以音量 0 启动，播放后 5 tick 未进入激活状态，且日志确认
 * 技能音效（同 MASTER 声道、主音量 100）可以听到。
 *
 * 诊断：日志前缀 [EgoBGM]。播放请求后 5 tick 检查 SoundManager.isActive；未激活则记录并重试一次。
 */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class NtEgoBgm {
    private static final Logger LOG = LogManager.getLogger("rpgtemplate/EgoBGM");
    private static final float VOLUME = 0.8F;
    /** 播放请求后多少 tick 检查一次是否处于激活状态。 */
    private static final int CHECK_AFTER_TICKS = 5;

    private static SimpleSoundInstance current;
    private static int checkCountdown = -1;
    private static boolean retried;

    private NtEgoBgm() {
    }

    private static void startTrack(Minecraft mc) {
        current = new SimpleSoundInstance(
                NtSounds.EGO_BGM_ID, SoundSource.MASTER, VOLUME, 1.0F,
                SoundInstance.createUnseededRandom(), true, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        mc.getSoundManager().play(current);
        checkCountdown = CHECK_AFTER_TICKS;
        LOG.info("[EgoBGM] play requested: event={} source=MASTER stream=false looping=true volume={} masterVolume={}",
                NtSounds.EGO_BGM_ID, VOLUME, mc.options.getSoundSourceVolume(SoundSource.MASTER));
    }

    private static void stopTrack(Minecraft mc) {
        if (current != null) {
            mc.getSoundManager().stop(current);
            current = null;
        }
        checkCountdown = -1;
    }

    /** 诊断：记录 Forge PlaySoundEvent 对 BGM 的处理（是否被其他模组取消/替换）。 */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getOriginalSound() == null
                || !NtSounds.EGO_BGM_ID.equals(event.getOriginalSound().getLocation())) {
            return;
        }
        LOG.info("[EgoBGM] PlaySoundEvent fired: cancelledOrReplaced={}", event.getSound() == null
                || event.getSound() != event.getOriginalSound());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        if (local == null || mc.level == null) {
            stopTrack(mc);
            retried = false;
            return;
        }
        boolean ego = NtBody.egoActive(local);
        if (ego) {
            if (current == null) {
                retried = false;
                startTrack(mc);
            }
        } else if (current != null) {
            stopTrack(mc);
        }
        if (current != null && checkCountdown > 0) {
            checkCountdown--;
        } else if (current != null && checkCountdown == 0) {
            checkCountdown = -1;
            boolean active = mc.getSoundManager().isActive(current);
            if (active) {
                LOG.info("[EgoBGM] active after {} ticks", CHECK_AFTER_TICKS);
            } else if (!retried) {
                retried = true;
                LOG.warn("[EgoBGM] NOT active after {} ticks; retrying once; masterVolume={} engine=[{}]",
                        CHECK_AFTER_TICKS, mc.options.getSoundSourceVolume(SoundSource.MASTER), mc.getSoundManager().getDebugString());
                mc.getSoundManager().stop(current);
                current = null;
                startTrack(mc);
            } else {
                LOG.error("[EgoBGM] still NOT active after retry; masterVolume={} engine=[{}]",
                        mc.options.getSoundSourceVolume(SoundSource.MASTER), mc.getSoundManager().getDebugString());
            }
        }
    }
}
