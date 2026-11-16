package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusFxMsg;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 假面骑士 Cronus 第一人称 HUD 特效渲染器：
 * <ul>
 *   <li>【Pause】时停屏幕边缘翠绿暗角与精密金绿刻度</li>
 *   <li>Bugster 病毒过载边缘故障扫描条</li>
 *   <li>必杀技特摄演出：对角线暗黑翠绿横幅（{@code CRITICAL JUDGMENT / CRITICAL SACRIFICE / CRITICAL CREWS-AID}）、
 *       巨型旋转编年史时计、翠绿碎屏时间裂缝以及「{@code SHUT DOWN!} / 绝版！」终结处决印章</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CronusHudOverlay {
    private static final ResourceLocation FINISHER_BANNERS_TEX =
        new ResourceLocation("rpgtemplate", "textures/fx/cronus_finisher_banners.png");
    private static final ResourceLocation GLASS_SHATTER_TEX =
        new ResourceLocation("rpgtemplate", "textures/fx/cronus_glass_shatter.png");
    private static final ResourceLocation HENSHIN_CLOCK_TEX =
        new ResourceLocation("rpgtemplate", "textures/fx/cronus_henshin_clock.png");

    private static int finisherStyle = -1;
    private static int finisherTicks = 0;
    private static int finisherMaxTicks = 1;
    /** 【Pause】开启瞬间冷翠时空冻结闪屏倒计时（18 ticks）。 */
    private static int pauseEnterTicks = 0;
    private static final int PAUSE_ENTER_MAX = 18;
    /** 【Restart】时间重开瞬间翠金碎屏解冻脉冲倒计时（16 ticks）。 */
    private static int restartFlashTicks = 0;
    private static final int RESTART_FLASH_MAX = 16;

    private CronusHudOverlay() {
    }

    /** 触发【Pause】开启瞬间冷翠时钟扫屏演出。 */
    public static void triggerPauseEnter() {
        pauseEnterTicks = PAUSE_ENTER_MAX;
        restartFlashTicks = 0;
    }

    /** 触发【Restart】时间重开瞬间翠金碎屏与时间复流脉冲。 */
    public static void triggerRestartFlash() {
        restartFlashTicks = RESTART_FLASH_MAX;
        pauseEnterTicks = 0;
    }

    /** 触发必杀技对角线特摄切入演出（不干扰操作视角与战斗准星）。 */
    public static void triggerFinisherCutIn(int style) {
        finisherStyle = style;
        finisherMaxTicks = switch (style) {
            case CronusFxMsg.FINISHER_SHUT_DOWN -> 56;
            case CronusFxMsg.FINISHER_CRITICAL_CREWS_AID -> 42;
            case CronusFxMsg.FINISHER_CRITICAL_JUDGMENT_PAUSE -> 38;
            default -> 34;
        };
        finisherTicks = finisherMaxTicks;
    }

    /** 兼容旧版「绝版！」横幅触发入口，直接衔接至完整的 SHUT DOWN! 处决演出。 */
    public static void triggerOutBanner() {
        if (finisherStyle != CronusFxMsg.FINISHER_SHUT_DOWN || finisherTicks < 20) {
            triggerFinisherCutIn(CronusFxMsg.FINISHER_SHUT_DOWN);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null) {
            finisherTicks = 0;
            finisherStyle = -1;
            pauseEnterTicks = 0;
            restartFlashTicks = 0;
            return;
        }
        if (!mc.m_91104_()) {
            if (finisherTicks > 0) {
                finisherTicks--;
                if (finisherTicks <= 0) {
                    finisherStyle = -1;
                }
            }
            if (pauseEnterTicks > 0) {
                pauseEnterTicks--;
            }
            if (restartFlashTicks > 0) {
                restartFlashTicks--;
            }
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
        if (!isCronus && finisherTicks <= 0 && pauseEnterTicks <= 0 && restartFlashTicks <= 0 && selfEnergyIcons.isEmpty()) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int sw = event.getWindow().m_85445_();
        int sh = event.getWindow().m_85446_();
        float pt = event.getPartialTick();

        // 0. 第一人称顶部中央显示当前生效的能量道具硬币图标与剩余时长条（第三人称直接由角色头顶 3D 图标呈现）
        if (!selfEnergyIcons.isEmpty() && mc.f_91066_.m_92176_().m_90612_()) {
            renderActiveEnergyIconsHud(g, sw, selfEnergyIcons);
        }

        // 1. 【Pause】时停氛围：多层羽化冷翡翠暗角 + 四角精密钟表刻度 + 顶部 PAUSE 剩余秒数与悬停伤害实时读数
        if (isCronus && CronusKeys.isPaused(data)) {
            renderPauseAtmosphereHud(g, mc.f_91062_, sw, sh, data);
        }
        // 1b. 【Pause】开启瞬间冷翠时空定格闪屏 + 编年史时计扫影
        if (pauseEnterTicks > 0) {
            renderPauseEnterFlash(g, sw, sh, pt);
        }
        // 1c. 【Restart】时间重开瞬间翠金碎屏复流脉冲
        if (restartFlashTicks > 0) {
            renderRestartResumeFlash(g, sw, sh, pt);
        }

        // 2. Bugster 病毒负荷 ≥75% 时的屏幕边缘轻微数据故障扫描条
        if (isCronus && data.value(CronusKeys.KEY_VIRAL, 0.0) >= 75.0) {
            long t = System.currentTimeMillis() / 90L;
            int y1 = (int) ((t * 23L) % Math.max(1, sh - 6));
            int y2 = (int) (((t * 47L) + 91L) % Math.max(1, sh - 6));
            g.m_280509_(0, y1, 28, y1 + 2, 0x66FF2E93);
            g.m_280509_(sw - 34, y2, sw, y2 + 2, 0x6618F29A);
        }

        // 3. 必杀技特摄对角线切入演出 + 编年史巨钟 + 翠绿碎屏时间裂缝 + SHUT DOWN! 处决印章
        if (finisherTicks > 0 && finisherStyle >= 0) {
            renderFinisherCutIn(g, mc.f_91062_, sw, sh, pt);
        }
    }

    private static void renderFinisherCutIn(GuiGraphics g, Font font, int sw, int sh, float pt) {
        float elapsed = (finisherMaxTicks - finisherTicks) + pt;
        float prog = Mth.m_14036_(elapsed / (float) Math.max(1, finisherMaxTicks), 0.0f, 1.0f);
        boolean isShutDown = (finisherStyle == CronusFxMsg.FINISHER_SHUT_DOWN);
        boolean isCrewsAid = (finisherStyle == CronusFxMsg.FINISHER_CRITICAL_CREWS_AID);
        boolean isPauseCj = (finisherStyle == CronusFxMsg.FINISHER_CRITICAL_JUDGMENT_PAUSE);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // A. 发动瞬间翠绿/翠金能量闪光（前 6 ticks 快速闪过，营造原视频发动必杀的瞬间冲击）
        if (elapsed < 7.0f) {
            float flash = (1.0f - elapsed / 7.0f);
            int fa = Mth.m_14045_((int) (flash * flash * (isShutDown ? 110.0f : 72.0f)), 0, 120);
            int flashRgb = (isPauseCj || isShutDown) ? 0x66FFAA : 0x18F29A;
            g.m_280509_(0, 0, sw, sh, (fa << 24) | flashRgb);
        }

        // B. 屏幕上下极窄能量聚焦光条（强化特摄必杀构图，绝不遮挡准星）
        float railAlpha = prog < 0.12f ? (prog / 0.12f) : (prog > 0.80f ? (1.0f - prog) / 0.20f : 1.0f);
        int ra = Mth.m_14045_((int) (railAlpha * 185.0f), 0, 185);
        int railRgb = (isPauseCj || isShutDown) ? 0xFFE066 : 0x18F29A;
        g.m_280509_(0, 0, sw, 3, (ra << 24) | railRgb);
        g.m_280509_(0, sh - 3, sw, sh, (ra << 24) | railRgb);

        // C. 旋转编年史巨钟（时停光线枪必杀 / Critical Crews-Aid / SHUT DOWN! 专属背景层）
        if (isPauseCj || isCrewsAid || isShutDown) {
            float clockFade = prog < 0.14f ? (prog / 0.14f) : (prog > 0.72f ? (1.0f - prog) / 0.28f : 1.0f);
            float clockAlpha = Mth.m_14036_(clockFade * (isShutDown ? 0.56f : 0.44f), 0.0f, 0.65f);
            if (clockAlpha > 0.02f) {
                int clockSize = Math.round(Math.min(sw, sh) * (isPauseCj ? 0.62f : 0.78f));
                float cx = isPauseCj ? (sw * 0.78f) : (sw * 0.72f);
                float cy = isPauseCj ? (sh * 0.56f) : (sh * 0.52f);
                float spinDeg = elapsed * (isShutDown ? 3.8f : 2.6f);
                float popScale = 0.86f + 0.16f * Math.min(1.0f, elapsed / 7.0f);

                PoseStack pose = g.m_280168_();
                pose.m_85836_();
                pose.m_252880_(cx, cy, 0.0f);
                pose.m_252781_(Axis.f_252403_.m_252977_(spinDeg));
                pose.m_85841_(popScale, popScale, 1.0f);
                g.m_280246_(0.78f, 1.0f, 0.86f, clockAlpha);
                g.m_280411_(HENSHIN_CLOCK_TEX, -clockSize / 2, -clockSize / 2, clockSize, clockSize,
                    0.0f, 0.0f, 512, 512, 512, 512);
                g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
                pose.m_85849_();
            }
        }

        // D. 翠绿时间碎屏玻璃裂缝层（Critical Crews-Aid 踢击爆裂瞬间 & SHUT DOWN! 绝版处决碎屏！）
        if (isShutDown || isCrewsAid) {
            float shatterStart = isShutDown ? 10.0f : 14.0f;
            float shatterEnd = isShutDown ? 46.0f : 34.0f;
            if (elapsed >= shatterStart && elapsed <= shatterEnd) {
                float sp = (elapsed - shatterStart) / (shatterEnd - shatterStart);
                float sa = sp < 0.15f ? (sp / 0.15f) : (1.0f - (sp - 0.15f) / 0.85f);
                sa *= isShutDown ? 0.68f : 0.42f;
                if (sa > 0.02f) {
                    float driftScale = 1.0f + sp * 0.08f;
                    PoseStack pose = g.m_280168_();
                    pose.m_85836_();
                    pose.m_252880_(sw * 0.5f, sh * 0.5f, 0.0f);
                    pose.m_85841_(driftScale, driftScale, 1.0f);
                    g.m_280246_(0.88f, 1.0f, 0.92f, Mth.m_14036_(sa, 0.0f, 0.80f));
                    g.m_280411_(GLASS_SHATTER_TEX, -sw / 2, -sh / 2, sw, sh,
                        0.0f, 0.0f, 512, 512, 512, 512);
                    g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
                    pose.m_85849_();
                }
            }
        }

        // E. 标志性 -23° 对角线暗黑翠绿必杀切入横幅（CRITICAL JUDGMENT / CRITICAL SACRIFICE / CRITICAL CREWS-AID）
        float ribbonMaxElapsed = isShutDown ? 28.0f : (float) finisherMaxTicks;
        if (elapsed <= ribbonMaxElapsed) {
            float rp = Mth.m_14036_(elapsed / ribbonMaxElapsed, 0.0f, 1.0f);
            float alpha;
            float slideX;
            if (rp < 0.14f) {
                float u = rp / 0.14f;
                float ease = 1.0f - (1.0f - u) * (1.0f - u) * (1.0f - u);
                alpha = u;
                slideX = Mth.m_14179_(ease, -sw * 0.52f, -14.0f);
            } else if (rp < 0.78f) {
                float u = (rp - 0.14f) / 0.64f;
                alpha = 0.94f;
                slideX = Mth.m_14179_(u, -14.0f, 22.0f);
            } else {
                float u = (rp - 0.78f) / 0.22f;
                alpha = (1.0f - u) * 0.94f;
                slideX = Mth.m_14179_(u * u, 22.0f, sw * 0.58f);
            }

            if (alpha > 0.02f) {
                int stripIdx = switch (finisherStyle) {
                    case CronusFxMsg.FINISHER_CRITICAL_SACRIFICE -> 1;
                    case CronusFxMsg.FINISHER_CRITICAL_CREWS_AID, CronusFxMsg.FINISHER_SHUT_DOWN -> 2;
                    default -> 0;
                };
                // 宽幅覆盖对角全屏且居中偏向画面上中段，保留玩家视野中心透明清晰感
                float diagLen = (float) Math.hypot(sw, sh) * 1.08f;
                int bannerW = Math.max(360, Math.round(diagLen));
                int bannerH = Math.max(38, Math.round(sh * 0.165f));
                float anchorY = sh * (isCrewsAid || isShutDown ? 0.44f : 0.36f);

                PoseStack pose = g.m_280168_();
                pose.m_85836_();
                pose.m_252880_(sw * 0.5f, anchorY, 0.0f);
                pose.m_252781_(Axis.f_252403_.m_252977_(-22.5f));
                pose.m_252880_(slideX, 0.0f, 0.0f);

                // 主必杀技横幅（高清 1024x128 单元格）
                g.m_280246_(1.0f, 1.0f, 1.0f, alpha);
                g.m_280411_(FINISHER_BANNERS_TEX, -bannerW / 2, -bannerH / 2, bannerW, bannerH,
                    0.0f, stripIdx * 128.0f, 1024, 128, 1024, 512);

                // 高速扫光高光带（沿横幅划过的金属反光闪耀）
                if (rp >= 0.14f && rp <= 0.62f) {
                    float gp = (rp - 0.14f) / 0.48f;
                    int gx = Math.round(Mth.m_14179_(gp, -bannerW * 0.45f, bannerW * 0.45f));
                    int ga = Mth.m_14045_((int) (Math.sin(gp * Math.PI) * 125.0f * alpha), 0, 140);
                    g.m_280509_(gx - 22, -bannerH / 2 + 6, gx + 22, bannerH / 2 - 6, (ga << 24) | 0xCCFFE8);
                }

                g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
                pose.m_85849_();
            }
        }

        // F. 时停绝版处决高潮印章：「SHUT DOWN!」 + 「绝版！」副标（还原视频 7:34 处决终结一击！）
        if (isShutDown && elapsed >= 18.0f) {
            float sp = Mth.m_14036_((elapsed - 18.0f) / (finisherMaxTicks - 18.0f), 0.0f, 1.0f);
            float punchScale = sp < 0.16f
                ? Mth.m_14179_(1.0f - (1.0f - sp / 0.16f) * (1.0f - sp / 0.16f), 1.48f, 1.0f)
                : 1.0f;
            float sa = sp < 0.10f ? (sp / 0.10f) : (sp > 0.76f ? (1.0f - sp) / 0.24f : 1.0f);
            if (sa > 0.02f) {
                int stampW = Math.max(240, Math.round(sw * 0.62f));
                int stampH = Math.max(32, Math.round(stampW * (128.0f / 1024.0f)));
                float cy = sh * 0.27f;

                PoseStack pose = g.m_280168_();
                pose.m_85836_();
                pose.m_252880_(sw * 0.5f, cy, 0.0f);
                pose.m_85841_(punchScale, punchScale, 1.0f);
                g.m_280246_(1.0f, 1.0f, 1.0f, Mth.m_14036_(sa, 0.0f, 1.0f));
                g.m_280411_(FINISHER_BANNERS_TEX, -stampW / 2, -stampH / 2, stampW, stampH,
                    0.0f, 3.0f * 128.0f, 1024, 128, 1024, 512);
                g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
                pose.m_85849_();

                int ta = Mth.m_14045_((int) (sa * 245.0f), 0, 245);
                if (ta > 14) {
                    String mainTxt = Component.m_237115_("hud.rpgtemplate.cronus.out_banner").getString();
                    g.m_280056_(font, mainTxt,
                        sw / 2 - font.m_92895_(mainTxt) / 2,
                        Math.round(cy + stampH * 0.5f + 4.0f),
                        (ta << 24) | 0x38FF9C, true);
                }
            }
        }

        RenderSystem.disableBlend();
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

    private static void renderPauseAtmosphereHud(GuiGraphics g, Font font, int sw, int sh, PlayerSystemData data) {
        long ms = System.currentTimeMillis();
        float breath = 0.78f + 0.22f * Mth.m_14031_(ms * 0.0042f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 极淡全屏冷翠色偏薄纱（营造“世界静止”的通透色温差，绝不影响视线清晰度）
        g.m_280509_(0, 0, sw, sh, 0x1004281C);

        // 多层羽化屏幕边缘冷翠暗角（由外到内四阶渐变，告别生硬黑边）
        int[] widths = {6, 14, 24, 38};
        int[] alphas = {0x44, 0x2A, 0x16, 0x0A};
        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            int prev = (i == 0) ? 0 : widths[i - 1];
            int col = (alphas[i] << 24) | 0x052C1E;
            g.m_280509_(0, prev, sw, w, col);
            g.m_280509_(0, sh - w, sw, sh - prev, col);
            g.m_280509_(prev, w, w, sh - w, col);
            g.m_280509_(sw - w, w, sw - prev, sh - w, col);
        }

        // 右下角极淡旋转编年史钟盘水印
        int dialSize = Math.max(76, Math.round(Math.min(sw, sh) * 0.22f));
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(sw - dialSize * 0.34f, sh - dialSize * 0.34f, 0.0f);
        pose.m_252781_(Axis.f_252403_.m_252977_((ms % 40000L) * 0.002f));
        g.m_280246_(0.45f, 1.0f, 0.78f, 0.14f * breath);
        g.m_280411_(HENSHIN_CLOCK_TEX, -dialSize / 2, -dialSize / 2, dialSize, dialSize,
            0.0f, 0.0f, 512, 512, 512, 512);
        g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
        pose.m_85849_();

        // 四角精密金绿时计准星角标
        int aGreen = (Mth.m_14045_((int) (175 * breath), 90, 220) << 24) | 0x18F29A;
        int aGold = (Mth.m_14045_((int) (185 * breath), 95, 230) << 24) | 0xF5D056;
        g.m_280509_(8, 8, 42, 10, aGreen);
        g.m_280509_(8, 8, 10, 38, aGreen);
        g.m_280509_(sw - 42, 8, sw - 8, 10, aGreen);
        g.m_280509_(sw - 10, 8, sw - 8, 38, aGreen);
        g.m_280509_(8, sh - 10, 42, sh - 8, aGold);
        g.m_280509_(8, sh - 38, 10, sh - 8, aGold);
        g.m_280509_(sw - 42, sh - 10, sw - 8, sh - 8, aGold);
        g.m_280509_(sw - 10, sh - 38, sw - 8, sh - 8, aGold);

        // 顶部居中极简 PAUSE 遥测条（剩余维持秒数 + 悬停待爆发总伤害）
        double leftSec = Math.max(0.0, data.value(CronusKeys.KEY_PAUSE_LEFT_SEC, 0.0));
        double pendingDmg = Math.max(0.0, data.value(CronusKeys.KEY_PENDING_DMG, 0.0));
        String label = pendingDmg > 0.5
            ? String.format("PAUSE  %.1fs  |  BURST +%.0f", leftSec, pendingDmg)
            : String.format("PAUSE  %.1fs", leftSec);
        int textW = font.m_92895_(label);
        int pillW = Math.max(118, textW + 24);
        int pillX = (sw - pillW) / 2;
        int pillY = 44;
        g.m_280509_(pillX, pillY, pillX + pillW, pillY + 15, 0x9E071B15);
        g.m_280509_(pillX, pillY, pillX + pillW, pillY + 1, aGreen);
        g.m_280509_(pillX, pillY + 14, pillX + pillW, pillY + 15, pendingDmg > 0.5 ? aGold : 0x6618F29A);
        float secFrac = Mth.m_14036_((float) (leftSec / 16.0), 0.0f, 1.0f);
        int fillW = Math.round((pillW - 4) * secFrac);
        if (fillW > 0) {
            int barCol = leftSec < 3.0 ? 0xCCFF5E62 : 0xBB18F29A;
            g.m_280509_(pillX + 2, pillY + 12, pillX + 2 + fillW, pillY + 14, barCol);
        }
        g.m_280653_(font, Component.m_237113_(label), sw / 2, pillY + 3,
            pendingDmg > 0.5 ? 0xFFF29C : 0xA8FFDF);

        RenderSystem.disableBlend();
    }

    private static void renderPauseEnterFlash(GuiGraphics g, int sw, int sh, float pt) {
        float elapsed = (PAUSE_ENTER_MAX - pauseEnterTicks) + pt;
        float p = Mth.m_14036_(elapsed / (float) PAUSE_ENTER_MAX, 0.0f, 1.0f);
        float inv = 1.0f - p;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 前 6 ticks 瞬时冷翠反冲光幕
        if (p < 0.45f) {
            float fp = 1.0f - p / 0.45f;
            int a = Mth.m_14045_((int) (fp * fp * 115.0f), 0, 120);
            g.m_280509_(0, 0, sw, sh, (a << 24) | 0x20FFAA);
        }

        // 屏幕中央极速收敛并骤停的半透明巨型编年史时钟虚影
        int size = Math.round(Math.min(sw, sh) * (1.28f - 0.34f * (1.0f - inv * inv)));
        float clockAlpha = inv * inv * 0.42f;
        if (clockAlpha > 0.02f) {
            PoseStack pose = g.m_280168_();
            pose.m_85836_();
            pose.m_252880_(sw * 0.5f, sh * 0.5f, 0.0f);
            pose.m_252781_(Axis.f_252403_.m_252977_(-22.0f * inv * inv));
            g.m_280246_(0.72f, 1.0f, 0.88f, clockAlpha);
            g.m_280411_(HENSHIN_CLOCK_TEX, -size / 2, -size / 2, size, size,
                0.0f, 0.0f, 512, 512, 512, 512);
            g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
            pose.m_85849_();
        }
        RenderSystem.disableBlend();
    }

    private static void renderRestartResumeFlash(GuiGraphics g, int sw, int sh, float pt) {
        float elapsed = (RESTART_FLASH_MAX - restartFlashTicks) + pt;
        float p = Mth.m_14036_(elapsed / (float) RESTART_FLASH_MAX, 0.0f, 1.0f);
        float inv = 1.0f - p;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 暖金时间复流瞬闪 + 轻度翠金碎屏纹理扩开
        if (p < 0.42f) {
            float fp = 1.0f - p / 0.42f;
            int a = Mth.m_14045_((int) (fp * fp * 105.0f), 0, 110);
            g.m_280509_(0, 0, sw, sh, (a << 24) | 0xFFE57A);
        }
        float shatterAlpha = inv * 0.36f;
        if (shatterAlpha > 0.02f) {
            float sc = 1.0f + p * 0.14f;
            PoseStack pose = g.m_280168_();
            pose.m_85836_();
            pose.m_252880_(sw * 0.5f, sh * 0.5f, 0.0f);
            pose.m_85841_(sc, sc, 1.0f);
            g.m_280246_(1.0f, 0.96f, 0.72f, shatterAlpha);
            g.m_280411_(GLASS_SHATTER_TEX, -sw / 2, -sh / 2, sw, sh,
                0.0f, 0.0f, 512, 512, 512, 512);
            g.m_280246_(1.0f, 1.0f, 1.0f, 1.0f);
            pose.m_85849_();
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
