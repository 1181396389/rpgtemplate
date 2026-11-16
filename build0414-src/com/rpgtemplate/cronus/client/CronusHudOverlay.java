package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import java.util.List;
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
        PlayerSystemData data = ClientSystemData.get();
        boolean isCronus = data != null && data.isActivated()
            && CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId());
        List<CronusSpriteFx.EnergyIconFx> selfEnergyIcons = CronusSpriteFx.activeIconsFor(mc.f_91074_.m_19879_());
        if (!isCronus && outBannerTicks <= 0 && selfEnergyIcons.isEmpty()) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int sw = event.getWindow().m_85445_();
        int sh = event.getWindow().m_85446_();

        // 0. 第一人称顶部中央显示当前生效的能量道具硬币图标与剩余时长条（第三人称直接由角色头顶 3D 图标呈现）
        if (!selfEnergyIcons.isEmpty() && mc.f_91066_.m_92176_().m_90612_()) {
            renderActiveEnergyIconsHud(g, sw, selfEnergyIcons);
        }

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
                g.m_280056_(font, mainTxt, sw / 2 - font.m_92895_(mainTxt) / 2, cy - 9, (a << 24) | 0x38FF9C, true);
                g.m_280056_(font, subTxt, sw / 2 - font.m_92895_(subTxt) / 2, cy + 3, (a << 24) | 0xFFE066, true);
            }
        }
    }

    private static void renderActiveEnergyIconsHud(GuiGraphics g, int sw, List<CronusSpriteFx.EnergyIconFx> icons) {
        int count = icons.size();
        int cardW = 28;
        int cardH = 31;
        int gap = 6;
        int totalW = count * cardW + (count - 1) * gap;
        int startX = (sw - totalW) / 2;
        int y = 10;

        RenderSystem.enableBlend();
        for (int i = 0; i < count; i++) {
            CronusSpriteFx.EnergyIconFx icon = icons.get(i);
            int x = startX + i * (cardW + gap);
            int accent = energyAccentRgb(icon.itemKind);
            int remaining = Math.max(0, icon.maxAge - icon.age);
            float ratio = icon.maxAge > 0 ? Mth.m_14036_(remaining / (float) icon.maxAge, 0.0f, 1.0f) : 0.0f;

            float alpha = 1.0f;
            if (icon.itemKind != 4 && remaining < 45) {
                float freq = remaining < 20 ? 1.55f : 0.85f;
                alpha = 0.42f + 0.58f * (0.5f + 0.5f * Mth.m_14031_(icon.age * freq));
            } else if (icon.itemKind == 4 && ratio < 0.35f) {
                alpha = Mth.m_14036_(ratio / 0.35f, 0.0f, 1.0f);
            }

            // 精致暗底卡片与能量对应色调边框
            g.m_280509_(x, y, x + cardW, y + cardH, 0xA80B1318);
            g.m_280509_(x, y, x + cardW, y + 1, 0xCC000000 | accent);
            g.m_280509_(x, y + cardH - 1, x + cardW, y + cardH, 0x99000000 | accent);
            g.m_280509_(x, y, x + 1, y + cardH, 0x88000000 | accent);
            g.m_280509_(x + cardW - 1, y, x + cardW, y + cardH, 0x88000000 | accent);

            // 绘制 22x22 像素能量道具硬币（64x64 单元格于 256x128 图集）
            int col = icon.itemKind % 4;
            int row = icon.itemKind / 4;
            g.m_280246_(1.0f, 1.0f, 1.0f, alpha);
            g.m_280411_(CronusSpriteFx.ENERGY_ITEMS_TEX, x + 3, y + 2, 22, 22,
                col * 64.0f, row * 64.0f, 64, 64, 256, 128);
            g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);

            // 底部剩余时长进度条
            int barX = x + 3;
            int barY = y + cardH - 5;
            int barW = cardW - 6;
            g.m_280509_(barX, barY, barX + barW, barY + 2, 0x881A242D);
            int fillW = Math.max(0, Math.round(barW * ratio));
            if (fillW > 0) {
                g.m_280509_(barX, barY, barX + fillW, barY + 2, 0xFF000000 | accent);
            }
        }
        RenderSystem.disableBlend();
    }

    private static int energyAccentRgb(int itemKind) {
        return switch (itemKind) {
            case 0 -> 0xFFD43B; // 高速化
            case 1 -> 0xFF6B2B; // 肌肉化
            case 2 -> 0xC8D8E8; // 钢铁化
            case 3 -> 0x38A8FF; // 跳跃强化
            case 4 -> 0xFF5CA8; // 回复
            case 5 -> 0xC66BFF; // 混乱
            case 6 -> 0x45F2FF; // 透明化
            default -> 0x18F29A;
        };
    }
}
