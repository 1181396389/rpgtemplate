package com.rpgtemplate.cronus.client;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.ClientSystemState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 假面骑士 Cronus 第一人称屏幕边缘时停滤镜、病毒过载噪点条纹与「绝版！」处决特摄横幅。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CronusHudOverlay {
    private static int outBannerTicks = 0;
    private static final int OUT_BANNER_MAX = 44;

    private CronusHudOverlay() {
    }

    public static void triggerOutBanner() {
        outBannerTicks = OUT_BANNER_MAX;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && outBannerTicks > 0) {
            outBannerTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || mc.f_91066_.f_92062_) {
            return;
        }
        PlayerSystemData data = ClientSystemState.data();
        boolean isCronus = data.isActivated() && CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId());
        if (!isCronus && outBannerTicks <= 0) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int sw = event.getWindow().m_85445_();
        int sh = event.getWindow().m_85446_();

        // 1. 【Pause】时停期间的屏幕边缘深翠绿时计暗角框
        if (isCronus && CronusKeys.isPaused(data)) {
            int edge = 14;
            g.m_280509_(0, 0, sw, edge, 0x38083A24);
            g.m_280509_(0, sh - edge, sw, sh, 0x38083A24);
            g.m_280509_(0, edge, edge, sh - edge, 0x2C083A24);
            g.m_280509_(sw - edge, edge, sw, sh - edge, 0x2C083A24);
            // 四角金绿精密刻度线
            g.m_280509_(6, 6, 36, 8, 0xAA18F29A);
            g.m_280509_(6, 6, 8, 36, 0xAA18F29A);
            g.m_280509_(sw - 36, 6, sw - 6, 8, 0xAA18F29A);
            g.m_280509_(sw - 8, 6, sw - 6, 36, 0xAA18F29A);
            g.m_280509_(6, sh - 8, 36, sh - 6, 0xAAD4AF37);
            g.m_280509_(sw - 36, sh - 8, sw - 6, sh - 6, 0xAAD4AF37);
        }

        // 2. Bugster 病毒负荷 ≥75% 时的屏幕边缘轻微数据故障扫描条
        if (isCronus && data.value(CronusKeys.KEY_VIRAL, 0.0) >= 75.0) {
            long t = System.currentTimeMillis() / 90L;
            int y1 = (int) ((t * 23L) % Math.max(1, sh - 6));
            int y2 = (int) (((t * 47L) + 91L) % Math.max(1, sh - 6));
            g.m_280509_(0, y1, 28, y1 + 2, 0x66FF2E93);
            g.m_280509_(sw - 34, y2, sw, y2 + 2, 0x6618F29A);
        }

        // 3. 「绝版！ / CRITICAL CREWS-AID」特摄处决横幅
        if (outBannerTicks > 0) {
            float prog = 1.0f - (outBannerTicks / (float) OUT_BANNER_MAX);
            float alphaF = prog < 0.12f ? (prog / 0.12f) : (prog > 0.78f ? (1.0f - prog) / 0.22f : 1.0f);
            int a = Mth.m_14045_((int) (alphaF * 235.0f), 0, 235);
            if (a > 12) {
                int cy = sh / 4;
                g.m_280509_(0, cy - 15, sw, cy + 16, ((a * 3 / 4) << 24) | 0x051811);
                g.m_280509_(0, cy - 16, sw, cy - 14, (a << 24) | 0x18F29A);
                g.m_280509_(0, cy + 15, sw, cy + 17, (a << 24) | 0xFFE066);

                Font font = mc.f_91062_;
                String mainTxt = Component.m_237115_("hud.rpgtemplate.cronus.out_banner").getString();
                String subTxt = "KIMEWAZA  ·  CRITICAL CREWS-AID  ·  OUT!";
                g.m_280653_(font, mainTxt, sw / 2, cy - 9, (a << 24) | 0x38FF9C);
                g.m_280653_(font, subTxt, sw / 2, cy + 3, (a << 24) | 0xFFE066);
            }
        }
    }
}
