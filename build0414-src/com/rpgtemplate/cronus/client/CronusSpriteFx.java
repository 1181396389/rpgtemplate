package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 假面骑士 Cronus 特摄级 3D 空间多图层贴图序列渲染器（严格零原版廉价粒子）。
 * <p>基于实拍透明通道序列打包的 5 张高清纹理图集，在世界 3D 空间分层渲染：</p>
 * <ul>
 *   <li><b>编年史变身完整 7.8s（156 ticks，234 帧原速时间轴）</b>：编年史选卡面板升起悬停循环→放射翠绿闪电+卡带旋转飞入→金色发光罗马数字 I~XII 逐一成型→卡牌轰然砸落合体 + 天降巨型翠绿落雷。</li>
 *   <li><b>Pause 黄金罗马数字时钟光环</b>：起手快速成型 I~XII 并平铺叠加至地面时停时钟领域。</li>
 *   <li><b>Reset 黄金罗马数字逆转</b>：罗马数字序列帧倒放（XII→I）配合天降绿雷闪光。</li>
 *   <li><b>Restart / 骑士踢 / 武装必杀 / 绝版处决</b>：3D 交叉立体放射翠绿闪电与天降巨型翠绿落雷。</li>
 *   <li><b>能量道具抽取</b>：头顶编年史卡牌轮盘快切演出。</li>
 * </ul>
 */
public final class CronusSpriteFx {
    private static final class Atlas {
        final ResourceLocation texture;
        final int cols;
        final int rows;
        final int validFrames;
        final float padU;
        final float padV;
        // 原始 1920x1080 画布中的归一化裁切框，保证四层变身序列精准对齐
        final float nx0, ny0, nx1, ny1;
        final boolean additive;

        Atlas(String path, int cols, int rows, int validFrames, int atlasW, int atlasH,
              int cropX, int cropY, int cropW, int cropH, boolean additive) {
            this.texture = new ResourceLocation("rpgtemplate", path);
            this.cols = cols;
            this.rows = rows;
            this.validFrames = validFrames;
            this.padU = 0.6f / atlasW;
            this.padV = 0.6f / atlasH;
            this.nx0 = cropX / 1920.0f;
            this.ny0 = cropY / 1080.0f;
            this.nx1 = (cropX + cropW) / 1920.0f;
            this.ny1 = (cropY + cropH) / 1080.0f;
            this.additive = additive;
        }

        RenderType renderType() {
            return additive ? CronusRenderTypes.additive(texture) : CronusRenderTypes.translucent(texture);
        }
    }

    private static final Atlas ATLAS_CARD = new Atlas(
        "textures/fx/cronus_card_atlas.png", 8, 8, 64, 2048, 2048,
        420, 0, 1080, 1080, false);

    private static final Atlas ATLAS_CLOCK = new Atlas(
        "textures/fx/cronus_clock_atlas.png", 8, 8, 64, 2048, 1824,
        440, 120, 1080, 960, true);

    private static final Atlas ATLAS_GASHAT = new Atlas(
        "textures/fx/cronus_gashat_atlas.png", 6, 6, 36, 1536, 1392,
        776, 376, 752, 680, false);

    private static final Atlas ATLAS_ARC = new Atlas(
        "textures/fx/cronus_lightning_arc_atlas.png", 6, 6, 34, 2304, 1296,
        0, 0, 1920, 1080, true);

    private static final Atlas ATLAS_BOLT = new Atlas(
        "textures/fx/cronus_lightning_bolt_atlas.png", 6, 4, 24, 1920, 1152,
        350, 0, 1240, 1080, true);

    /** 假面骑士克洛诺斯变身背部巨型钟表（完整呈现后于合体瞬间沿中线左右裂开）。 */
    private static final ResourceLocation HENSHIN_CLOCK_TEX =
        new ResourceLocation("rpgtemplate", "textures/fx/cronus_henshin_clock.png");
    /** 假面骑士 Ex-Aid 像素风能量道具硬币图集（4x2，最近邻锐利采样）：0高速/1肌肉/2钢铁/3跳跃/4回复/5混乱/6透明/7光环。 */
    public static final ResourceLocation ENERGY_ITEMS_TEX =
        new ResourceLocation("rpgtemplate", "textures/fx/cronus_energy_items.png");
    /** 假面骑士 Ex-Aid 漫画特摄风高清打击贴图集（2x3）：0=HIT!/1=GREAT!/2=CRITICAL!/3=GUARD!/4=ITEM GET!/5=OUT!。 */
    private static final ResourceLocation HIT_STICKERS_TEX =
        new ResourceLocation("rpgtemplate", "textures/fx/cronus_hit_stickers.png");
    private static final ResourceLocation WHITE_TEX =
        new ResourceLocation("minecraft", "textures/misc/white.png");

    /** 原速完整 7.8s 变身演出（234 帧 @ 30fps = 156 MC ticks）。 */
    private static final int HENSHIN_MAX_TICKS = 158;
    /** 快捷版快速变身（~2.2s = 44 MC ticks，仅展示卡片 + 闪电 + 时钟）。 */
    private static final int HENSHIN_FAST_TICKS = 44;

    private static final List<HenshinSeq> HENSHINS = new ArrayList<>();
    private static final List<SpriteBurst> BURSTS = new ArrayList<>();
    private static final List<EnergyIconFx> ENERGY_ICONS = new ArrayList<>();

    public static final class EnergyIconFx {
        public final int entityId;
        public final int itemKind;
        public double x, y, z;
        public int age;
        public int maxAge;

        EnergyIconFx(int entityId, int itemKind, double x, double y, double z, int maxAge) {
            this.entityId = entityId;
            this.itemKind = itemKind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxAge = maxAge;
        }
    }

    private static final class HenshinSeq {
        final int entityId;
        final boolean fast;
        final int maxAge;
        double x, y, z;
        float yawDeg;
        int age;

        HenshinSeq(int entityId, double x, double y, double z, float yawDeg, boolean fast) {
            this.entityId = entityId;
            this.fast = fast;
            this.maxAge = fast ? HENSHIN_FAST_TICKS : HENSHIN_MAX_TICKS;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yawDeg = yawDeg;
        }
    }

    private static final class SpriteBurst {
        final int kind; // 0=Arc, 1=Bolt, 2=CardRoulette, 3=ClockAssemble, 4=ClockRewind
        final int entityId;
        double x, y, z;
        final float scale;
        float yawDeg;
        int age;
        final int maxAge;

        SpriteBurst(int kind, int entityId, double x, double y, double z, float scale, float yawDeg, int maxAge) {
            this.kind = kind;
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.yawDeg = yawDeg;
            this.maxAge = maxAge;
        }
    }

    private CronusSpriteFx() {
    }

    public static void clear() {
        HENSHINS.clear();
        BURSTS.clear();
        ENERGY_ICONS.clear();
    }

    public static boolean hasActive() {
        return !HENSHINS.isEmpty() || !BURSTS.isEmpty() || !ENERGY_ICONS.isEmpty();
    }

    public static boolean isEntityInHenshin(int entityId) {
        for (HenshinSeq h : HENSHINS) {
            if (h.entityId == entityId) {
                return true;
            }
        }
        return false;
    }

    /** 在实体头顶生成或刷新对应能量道具能力的悬浮硬币图标（持续至能力结束）。 */
    public static void spawnEnergyIcon(int entityId, int itemKind, double x, double y, double z, int durationTicks) {
        int clampedKind = Mth.m_14045_(itemKind, 0, 6);
        int dur = Math.max(20, durationTicks);
        for (EnergyIconFx icon : ENERGY_ICONS) {
            if (icon.entityId == entityId && icon.itemKind == clampedKind) {
                icon.age = 0;
                icon.maxAge = dur;
                return;
            }
        }
        if (ENERGY_ICONS.size() >= 32) {
            ENERGY_ICONS.remove(0);
        }
        ENERGY_ICONS.add(new EnergyIconFx(entityId, clampedKind, x, y, z, dur));
    }

    /** 供第一人称 HUD 读取当前本地玩家身上正在生效的能量道具图标列表。 */
    public static List<EnergyIconFx> activeIconsFor(int entityId) {
        List<EnergyIconFx> list = new ArrayList<>();
        for (EnergyIconFx icon : ENERGY_ICONS) {
            if (icon.entityId == entityId) {
                list.add(icon);
            }
        }
        return list;
    }

    /** 触发完整 7.8s（234 帧原速）四层同步 3D 变身演出。 */
    public static void spawnHenshin(int entityId, double x, double y, double z) {
        spawnHenshinInternal(entityId, x, y, z, false);
    }

    /** 触发快捷版 2.2s（44 ticks）快速变身演出：仅展示卡片 + 闪电 + 时钟！ */
    public static void spawnFastHenshin(int entityId, double x, double y, double z) {
        spawnHenshinInternal(entityId, x, y, z, true);
    }

    private static void spawnHenshinInternal(int entityId, double x, double y, double z, boolean fast) {
        Minecraft mc = Minecraft.m_91087_();
        float yaw = 0f;
        if (mc.f_91073_ != null) {
            Entity e = mc.f_91073_.m_6815_(entityId);
            if (e != null) {
                yaw = e.m_146908_();
                x = e.m_20185_();
                y = e.m_20186_();
                z = e.m_20189_();
            }
        }
        HENSHINS.removeIf(h -> h.entityId == entityId);
        HENSHINS.add(new HenshinSeq(entityId, x, y, z, yaw, fast));
    }

    /**
     * 触发模块化 3D 序列特效：
     * {@code 0}=放射绿雷, {@code 1}=天降巨型绿雷, {@code 2}=头顶卡牌轮盘快切,
     * {@code 3}=发光黄金罗马数字成型, {@code 4}=发光黄金罗马数字逆转（Reset）。
     */
    public static void spawnBurst(int kind, int entityId, double x, double y, double z, float scale) {
        if (BURSTS.size() >= 36) {
            BURSTS.remove(0);
        }
        Minecraft mc = Minecraft.m_91087_();
        float yaw = (float) (Math.random() * 360.0);
        if (mc.f_91073_ != null && entityId >= 0) {
            Entity e = mc.f_91073_.m_6815_(entityId);
            if (e != null) {
                yaw = e.m_146908_();
            }
        }
        int maxAge = switch (kind) {
            case 0 -> 22; // 放射绿雷 (~1.1s)
            case 1 -> 18; // 天降落雷 (~0.9s)
            case 2 -> 28; // 能量道具头顶抽卡快切 (~1.4s)
            case 3 -> 30; // Pause 黄金时钟数字成型 (~1.5s)
            case 4 -> 26; // Reset 黄金时钟数字逆转 (~1.3s)
            default -> 20;
        };
        BURSTS.add(new SpriteBurst(kind, entityId, x, y, z, Math.max(0.4f, scale), yaw, maxAge));
    }

    public static void tick(Minecraft mc) {
        if (mc.f_91073_ == null) {
            clear();
            return;
        }
        Iterator<HenshinSeq> hit = HENSHINS.iterator();
        while (hit.hasNext()) {
            HenshinSeq h = hit.next();
            Entity e = mc.f_91073_.m_6815_(h.entityId);
            if (e != null && e.m_6084_()) {
                h.x = e.m_20185_();
                h.y = e.m_20186_();
                h.z = e.m_20189_();
            }
            if (++h.age >= h.maxAge) {
                hit.remove();
            }
        }
        Iterator<SpriteBurst> bit = BURSTS.iterator();
        while (bit.hasNext()) {
            SpriteBurst b = bit.next();
            if (b.entityId >= 0 && (b.kind == 2 || b.kind == 3 || b.kind == 4)) {
                Entity e = mc.f_91073_.m_6815_(b.entityId);
                if (e != null && e.m_6084_()) {
                    b.x = e.m_20185_();
                    b.y = e.m_20186_();
                    b.z = e.m_20189_();
                    b.yawDeg = e.m_146908_();
                }
            }
            if (++b.age >= b.maxAge) {
                bit.remove();
            }
        }
        Iterator<EnergyIconFx> eit = ENERGY_ICONS.iterator();
        while (eit.hasNext()) {
            EnergyIconFx icon = eit.next();
            Entity e = mc.f_91073_.m_6815_(icon.entityId);
            if (e != null && e.m_6084_()) {
                icon.x = e.m_20185_();
                icon.y = e.m_20186_();
                icon.z = e.m_20189_();
            }
            if (++icon.age >= icon.maxAge) {
                eit.remove();
            }
        }
    }

    /**
     * 在 Pause / Restart 地面时钟领域上平铺叠加高清发光黄金罗马数字环（取 {@code cronus_clock_atlas} 完整 12 数字终帧）。
     */
    public static void renderGroundClockOverlay(MultiBufferSource.BufferSource src, Matrix4f mat,
                                                double cx, double y, double cz, float radius,
                                                float rotationRad, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        RenderType rt = ATLAS_CLOCK.renderType();
        VertexConsumer vc = src.m_6299_(rt);
        // 完整 12 罗马数字圆环位于 cronus_clock_atlas 第 63 帧（右下角）
        // 在 1080x960 裁切框内，圆环外接半径约占帧宽 ~0.35，中心约在 (u=0.50, v=0.69)
        // 我们以平铺正方形四顶点直接对齐时钟中心，令金色发光数字紧贴时钟外环内侧！
        float halfW = radius * 1.18f;
        float halfH = radius * 1.05f;
        // 将图像中时钟中心 (0.50, 0.69) 映射到世界 (cx, cz)
        float u0 = cellU0(ATLAS_CLOCK, 63);
        float u1 = cellU1(ATLAS_CLOCK, 63);
        float v0 = cellV0(ATLAS_CLOCK, 63);
        float v1 = cellV1(ATLAS_CLOCK, 63);

        // 四角局部偏移（将纹理纵轴 0.69 对齐到原点，保证 12 个发光数字严丝合缝同心圆）
        float lx0 = -halfW;
        float lx1 = halfW;
        float lz0 = -halfH * 1.38f; // 顶部 (v=0)
        float lz1 = halfH * 0.62f;  // 底部 (v=1)
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);

        float ax = (float) (cx + lx0 * cos - lz0 * sin);
        float az = (float) (cz + lx0 * sin + lz0 * cos);
        float bx = (float) (cx + lx1 * cos - lz0 * sin);
        float bz = (float) (cz + lx1 * sin + lz0 * cos);
        float dx = (float) (cx + lx1 * cos - lz1 * sin);
        float dz = (float) (cz + lx1 * sin + lz1 * cos);
        float ex = (float) (cx + lx0 * cos - lz1 * sin);
        float ez = (float) (cz + lx0 * sin + lz1 * cos);

        float yf = (float) y;
        emitTexQuad(vc, mat,
            ax, yf, az, u0, v0,
            bx, yf, bz, u1, v0,
            dx, yf, dz, u1, v1,
            ex, yf, ez, u0, v1,
            1.0f, 1.0f, 1.0f, Mth.m_14036_(alpha, 0f, 1f));
        src.m_109912_(rt);
    }

    /** 在 RenderLevelStageEvent (AFTER_PARTICLES) 中统一绘制全部 3D 序列图集特效与头顶能量道具图标。 */
    public static void renderAll(Minecraft mc, MultiBufferSource.BufferSource src,
                                 PoseStack pose, Vec3 cam, float pt) {
        if (HENSHINS.isEmpty() && BURSTS.isEmpty() && ENERGY_ICONS.isEmpty()) {
            return;
        }
        pose.m_85836_();
        pose.m_85837_(-cam.f_82479_, -cam.f_82480_, -cam.f_82481_);
        Matrix4f mat = pose.m_85850_().m_252922_();

        for (HenshinSeq h : HENSHINS) {
            renderHenshinSequence(mc, src, mat, cam, h, pt);
        }
        for (SpriteBurst b : BURSTS) {
            renderSpriteBurst(mc, src, mat, cam, b, pt);
        }
        renderEnergyIcons(mc, src, mat, cam, pt);

        pose.m_85849_();
    }

    // ==================== 完整 7.8s 四层同步 3D 编年史变身演出 ====================

    private static void renderHenshinSequence(Minecraft mc, MultiBufferSource.BufferSource src,
                                              Matrix4f mat, Vec3 cam, HenshinSeq h, float pt) {
        double px = h.x, py = h.y, pz = h.z;
        Entity ent = mc.f_91073_ == null ? null : mc.f_91073_.m_6815_(h.entityId);
        if (ent != null) {
            px = Mth.m_14139_(pt, ent.f_19854_, ent.m_20185_());
            py = Mth.m_14139_(pt, ent.f_19855_, ent.m_20186_());
            pz = Mth.m_14139_(pt, ent.f_19856_, ent.m_20189_());
        }

        boolean selfFirstPerson = ent != null && ent == mc.f_91074_ && mc.f_91066_.m_92176_().m_90612_();
        // 以变身瞬间的水平朝向建立 3D 舞台坐标系（左-右、上、前）
        double yawRad = Math.toRadians(h.yawDeg);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        // 自动根据镜头在角色前方或后方选择水平右向轴，保证无论第一人称、第三人称背视还是正面特写，罗马数字 I~XII 与卡牌均绝不左右镜像！
        double camDotFwd = (cam.f_82479_ - px) * fwdX + (cam.f_82481_ - pz) * fwdZ;
        double sideSign = (!selfFirstPerson && camDotFwd > 0.25) ? 1.0 : -1.0;
        double rightX = sideSign * Math.cos(yawRad);
        double rightZ = sideSign * Math.sin(yawRad);

        // 第一人称下将整个 3D 舞台温和前推 1.9 格避免贴脸裁切；第三人称/联机他人视角紧贴身体立体分层包裹
        double depthShift = selfFirstPerson ? 1.90 : 0.0;
        double stageW = 6.8;
        double stageH = 3.825; // 精确 16:9 比例
        // 锚定原画布 (nx=0.50, ny=0.80) 到玩家腰胸高度 py + 1.05
        double originX = px + fwdX * depthShift;
        double originY = py + 1.05;
        double originZ = pz + fwdZ * depthShift;

        // 快捷版快速变身（~2.2s，仅显示：时钟 + 卡片 + 闪电，无卡带飞入与慢速悬停等待）
        if (h.fast) {
            float tTick = h.age + pt;

            // ① 时钟层 A：背部巨型编年史钟表快速显现 → 1.1s 合体瞬间沿中线左右裂开 → 随落雷淡出
            float clockFrame;
            if (tTick < 8.0f) {
                clockFrame = 1.0f + (tTick / 8.0f) * 29.0f;
            } else if (tTick < 21.0f) {
                clockFrame = 30.0f + ((tTick - 8.0f) / 13.0f) * 146.0f;
            } else if (tTick < 35.0f) {
                clockFrame = 176.0f + ((tTick - 21.0f) / 14.0f) * 38.0f;
            } else {
                clockFrame = 214.0f + Mth.m_14036_((tTick - 35.0f) / 9.0f, 0.0f, 1.0f) * 22.0f;
            }
            double clockDepth = selfFirstPerson ? 0.88 : -0.78;
            float rearViewAlpha = (!selfFirstPerson && camDotFwd < -0.35) ? 0.78f : 1.0f;
            renderHenshinClock(src, mat,
                originX + fwdX * clockDepth, py + 1.92, originZ + fwdZ * clockDepth,
                rightX, rightZ, clockFrame, rearViewAlpha);

            // ① 时钟层 B：金色发光罗马数字 I~XII 急速成型环绕
            if (tTick <= 38.0f) {
                int cIdx;
                float cAlpha = 1.0f;
                if (tTick <= 22.0f) {
                    float p = Mth.m_14036_(tTick / 22.0f, 0.0f, 1.0f);
                    cIdx = Math.min(ATLAS_CLOCK.validFrames - 1, (int) (p * (ATLAS_CLOCK.validFrames - 1)));
                } else {
                    cIdx = ATLAS_CLOCK.validFrames - 1;
                    cAlpha = tTick > 30.0f ? Mth.m_14036_((38.0f - tTick) / 8.0f, 0.0f, 1.0f) : 1.0f;
                }
                drawStageLayer(src, mat, ATLAS_CLOCK, cIdx,
                    originX + fwdX * 0.58, originY, originZ + fwdZ * 0.58,
                    rightX, rightZ, stageW, stageH, cAlpha);
            }

            // ② 卡片层：编年史卡片闪现 → 迅猛砸落合体
            if (tTick <= 30.0f) {
                int cardIdx;
                if (tTick <= 16.0f) {
                    float p = Mth.m_14036_(tTick / 16.0f, 0.0f, 1.0f);
                    cardIdx = (int) (p * 26.0f);
                } else {
                    float p = Mth.m_14036_((tTick - 16.0f) / 12.0f, 0.0f, 1.0f);
                    cardIdx = Math.min(ATLAS_CARD.validFrames - 1, 51 + (int) (p * 12.0f));
                }
                float cardAlpha = tTick > 26.5f ? Mth.m_14036_((30.0f - tTick) / 3.5f, 0.0f, 1.0f) : 0.96f;
                double cardDepth = tTick >= 18.0f ? 0.16 : -0.22;
                drawStageLayer(src, mat, ATLAS_CARD, cardIdx,
                    originX + fwdX * cardDepth, originY, originZ + fwdZ * cardDepth,
                    rightX, rightZ, stageW, stageH, cardAlpha);
            }

            // ③ 闪电层 A：双正交立体放射翠绿闪电
            if (tTick >= 5.0f && tTick <= 25.0f) {
                float p = Mth.m_14036_((tTick - 5.0f) / 18.0f, 0.0f, 1.0f);
                int aIdx = Math.min(ATLAS_ARC.validFrames - 1, (int) (p * (ATLAS_ARC.validFrames - 1)));
                float aAlpha = tTick > 21.0f ? Mth.m_14036_((25.0f - tTick) / 4.0f, 0.0f, 1.0f) : 1.0f;
                drawStageLayer(src, mat, ATLAS_ARC, aIdx,
                    originX + fwdX * 0.05, originY, originZ + fwdZ * 0.05,
                    rightX, rightZ, stageW, stageH, aAlpha);
                drawStageLayer(src, mat, ATLAS_ARC, aIdx,
                    originX, originY, originZ,
                    fwdX, fwdZ, stageW * 0.85, stageH * 0.95, aAlpha * 0.72f);
            }

            // ③ 闪电层 B：合体天降三面星形立体巨型翠绿落雷
            if (tTick >= 22.0f && tTick <= 43.5f) {
                float p = Mth.m_14036_((tTick - 22.0f) / 19.0f, 0.0f, 1.0f);
                int bIdx = Math.min(ATLAS_BOLT.validFrames - 1, (int) (p * (ATLAS_BOLT.validFrames - 1)));
                float bAlpha = tTick > 39.0f ? Mth.m_14036_((43.5f - tTick) / 4.5f, 0.0f, 1.0f) : 1.0f;
                renderVolumetricBolt(src, mat, originX, py, originZ, 1.25f, yawRad, bIdx, bAlpha);
            }
            return;
        }

        // 30fps 原剧时间轴帧号：1 tick (50ms) = 1.5 帧
        float tFrame = 1.0f + (h.age + pt) * 1.5f;

        // Layer 0（背部巨型编年史钟表）：变身初段于背后完整浮现（对应图1），合体瞬间沿中线向左右优雅裂开（对应图2）
        if (tFrame >= 1.0f && tFrame <= 236.0f) {
            double clockDepth = selfFirstPerson ? 0.88 : -0.78;
            float rearViewAlpha = (!selfFirstPerson && camDotFwd < -0.35) ? 0.78f : 1.0f;
            renderHenshinClock(src, mat,
                originX + fwdX * clockDepth, py + 1.92, originZ + fwdZ * clockDepth,
                rightX, rightZ, tFrame, rearViewAlpha);
        }

        // Layer 1（后上纵深）：编年史选卡面板（帧 1..211：升起→头顶悬停轮切→200..211 轰然砸落合体）
        if (tFrame >= 1.0f && tFrame <= 212.5f) {
            float prog = Mth.m_14036_((tFrame - 1.0f) / 210.0f, 0.0f, 1.0f);
            int idx = Math.min(ATLAS_CARD.validFrames - 1, (int) (prog * (ATLAS_CARD.validFrames - 1)));
            float alpha = tFrame > 209.0f ? Mth.m_14036_((212.5f - tFrame) / 3.5f, 0f, 1f) : 0.96f;
            // 砸落瞬间（200..211）略微前移包裹躯干
            double cardDepth = tFrame >= 198.0f ? 0.15 : -0.22;
            drawStageLayer(src, mat, ATLAS_CARD, idx,
                originX + fwdX * cardDepth, originY, originZ + fwdZ * cardDepth,
                rightX, rightZ, stageW, stageH, alpha);
        }

        // Layer 2（立体周身）：放射翠绿闪电（帧 31..64：双正交立体交叉平面）
        if (tFrame >= 31.0f && tFrame <= 65.5f) {
            float prog = Mth.m_14036_((tFrame - 31.0f) / 33.0f, 0.0f, 1.0f);
            int idx = Math.min(ATLAS_ARC.validFrames - 1, (int) (prog * (ATLAS_ARC.validFrames - 1)));
            float alpha = tFrame > 61.0f ? Mth.m_14036_((65.5f - tFrame) / 4.5f, 0f, 1f) : 1.0f;
            drawStageLayer(src, mat, ATLAS_ARC, idx,
                originX + fwdX * 0.05, originY, originZ + fwdZ * 0.05,
                rightX, rightZ, stageW, stageH, alpha);
            // 90° 正交交叉层，令侧面视角同样具备立体雷网
            drawStageLayer(src, mat, ATLAS_ARC, idx,
                originX, originY, originZ,
                fwdX, fwdZ, stageW * 0.85, stageH * 0.95, alpha * 0.72f);
        }

        // Layer 3（腰带精准对齐）：3D 编年史卡带旋转飞入，精准对准腰间 3D 故障驱动器Ⅱ 左上插卡槽位直接插入！
        // 帧 34..126 旋转飞入插槽；帧 126..136 无缝淡出交接给腰间 3D 驱动器亮起的实体卡带！
        if (tFrame >= 34.0f && tFrame <= 136.0f) {
            int idx;
            if (tFrame <= 126.0f) {
                float prog = Mth.m_14036_((tFrame - 34.0f) / 92.0f, 0.0f, 1.0f);
                idx = Math.min(ATLAS_GASHAT.validFrames - 1, (int) (prog * (ATLAS_GASHAT.validFrames - 1)));
            } else {
                idx = ATLAS_GASHAT.validFrames - 1;
            }
            float alpha = tFrame > 126.0f ? Mth.m_14036_((136.0f - tFrame) / 10.0f, 0f, 1f) : 1.0f;

            // 跟随玩家真实躯干朝向（不随镜头前后镜像翻转），确保第三人称与联机视角下卡带精准插入腰带右胯斜插槽
            double bodyYawDeg = h.yawDeg;
            if (ent instanceof LivingEntity living) {
                bodyYawDeg = Mth.m_14189_(pt, living.f_20884_, living.f_20883_);
            }
            double bYawRad = Math.toRadians(bodyYawDeg);
            double gFwdX = -Math.sin(bYawRad);
            double gFwdZ = Math.cos(bYawRad);
            double bodyLeftX = Math.cos(bYawRad);
            double bodyLeftZ = Math.sin(bYawRad);

            // 按 0.85x 比例使卡带尺寸与腰间 3D 故障驱动器Ⅱ 实体卡带 1:1 匹配，并精确解算锚点使末帧卡带直插腰带插槽：
            // 末帧中心精确落于：身体右胯 -0.145 * bodyLeft，腰带槽位高 py + 0.965，腰带正面 +0.21 * gFwd！
            double gScale = 0.85;
            double gStageW = stageW * gScale;
            double gStageH = stageH * gScale;
            double crouchDrop = (ent != null && ent.m_6047_()) ? -0.20 : 0.0;
            double gOriginX = selfFirstPerson
                ? (originX + fwdX * 0.25 + rightX * 0.137)
                : (px + gFwdX * 0.21 + bodyLeftX * 0.137);
            double gOriginY = selfFirstPerson
                ? (py + 0.88)
                : (py + 0.520 + crouchDrop);
            double gOriginZ = selfFirstPerson
                ? (originZ + fwdZ * 0.25 + rightZ * 0.137)
                : (pz + gFwdZ * 0.21 + bodyLeftZ * 0.137);
            double gRightX = selfFirstPerson ? rightX : bodyLeftX;
            double gRightZ = selfFirstPerson ? rightZ : bodyLeftZ;

            drawStageLayer(src, mat, ATLAS_GASHAT, idx,
                gOriginX, gOriginY, gOriginZ,
                gRightX, gRightZ, gStageW, gStageH, alpha);
        }

        // Layer 4（最前辉光层）：金色发光罗马数字 I~XII 逐一飞入成型（帧 72..193 成型，193..214 满环脉动）
        if (tFrame >= 72.0f && tFrame <= 216.0f) {
            int idx;
            float alpha = 1.0f;
            if (tFrame <= 193.0f) {
                float prog = Mth.m_14036_((tFrame - 72.0f) / 121.0f, 0.0f, 1.0f);
                idx = Math.min(ATLAS_CLOCK.validFrames - 1, (int) (prog * (ATLAS_CLOCK.validFrames - 1)));
            } else {
                idx = ATLAS_CLOCK.validFrames - 1;
                alpha = tFrame > 208.0f ? Mth.m_14036_((216.0f - tFrame) / 8.0f, 0f, 1f) : 1.0f;
            }
            drawStageLayer(src, mat, ATLAS_CLOCK, idx,
                originX + fwdX * 0.62, originY, originZ + fwdZ * 0.62,
                rightX, rightZ, stageW, stageH, alpha);
        }

            // Layer 5（终结天降落雷）：帧 211..234 三面星形立体巨型翠绿落雷从天穹劈落
        if (tFrame >= 211.0f && tFrame <= 236.0f) {
            float prog = Mth.m_14036_((tFrame - 211.0f) / 23.0f, 0.0f, 1.0f);
            int idx = Math.min(ATLAS_BOLT.validFrames - 1, (int) (prog * (ATLAS_BOLT.validFrames - 1)));
            float alpha = tFrame > 232.0f ? Mth.m_14036_((236.0f - tFrame) / 4.0f, 0f, 1f) : 1.0f;
            renderVolumetricBolt(src, mat, originX, py, originZ, 1.25f, yawRad, idx, alpha);
        }
    }

    /**
     * 假面骑士克洛诺斯变身背部巨型钟表：
     * <ul>
     *   <li>帧 1..30：在玩家背后缩放显现。</li>
     *   <li>帧 30..176：完整圆形钟表雄伟伫立背后，带细微机械脉动光泽。</li>
     *   <li>帧 176..214：沿竖直中线精准切分为左右两半并向两侧平稳滑开，裂口处闪耀翡翠金光缝。</li>
     *   <li>帧 214..236：伴随落雷继续微移并自然淡出。</li>
     * </ul>
     */
    private static void renderHenshinClock(MultiBufferSource.BufferSource src, Matrix4f mat,
                                           double cx, double cy, double cz,
                                           double rightX, double rightZ, float tFrame, float alphaMul) {
        float scale;
        float alpha;
        float splitRatio = 0.0f;

        if (tFrame < 30.0f) {
            float p = Mth.m_14036_((tFrame - 1.0f) / 29.0f, 0.0f, 1.0f);
            float ease = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
            scale = 0.36f + 0.64f * ease;
            alpha = Math.min(0.98f, p * 1.15f);
        } else if (tFrame < 176.0f) {
            scale = 1.0f + 0.012f * Mth.m_14031_((tFrame - 30.0f) * 0.14f);
            alpha = 0.98f;
        } else if (tFrame < 214.0f) {
            float p = Mth.m_14036_((tFrame - 176.0f) / 38.0f, 0.0f, 1.0f);
            // Smoothstep 机械开启曲线：起势稳健，中段利落裂开
            float ease = p * p * (3.0f - 2.0f * p);
            scale = 1.0f + 0.04f * ease;
            alpha = 0.98f;
            splitRatio = 0.68f * ease;
        } else {
            float p = Mth.m_14036_((tFrame - 214.0f) / 22.0f, 0.0f, 1.0f);
            scale = 1.04f + 0.05f * p;
            alpha = 0.98f * (1.0f - p);
            splitRatio = 0.68f + 0.24f * p;
        }
        alpha *= alphaMul;
        if (alpha <= 0.015f) {
            return;
        }

        double r = 1.86 * scale;
        double split = r * splitRatio;
        float yTop = (float) (cy + r);
        float yBot = (float) (cy - r);

        // 左半钟表 [u: 0.0..0.5] 与右半钟表 [u: 0.5..1.0]，未裂开时严丝合缝，裂开后左右对称展开
        RenderType rt = CronusRenderTypes.translucent(HENSHIN_CLOCK_TEX);
        VertexConsumer vc = src.m_6299_(rt);

        double lOut = -r - split;
        double lIn = -split;
        emitTexQuad(vc, mat,
            (float) (cx + rightX * lOut), yTop, (float) (cz + rightZ * lOut), 0.002f, 0.002f,
            (float) (cx + rightX * lIn),  yTop, (float) (cz + rightZ * lIn),  0.4995f, 0.002f,
            (float) (cx + rightX * lIn),  yBot, (float) (cz + rightZ * lIn),  0.4995f, 0.998f,
            (float) (cx + rightX * lOut), yBot, (float) (cz + rightZ * lOut), 0.002f, 0.998f,
            1.0f, 1.0f, 1.0f, alpha);

        double rIn = split;
        double rOut = r + split;
        emitTexQuad(vc, mat,
            (float) (cx + rightX * rIn),  yTop, (float) (cz + rightZ * rIn),  0.5005f, 0.002f,
            (float) (cx + rightX * rOut), yTop, (float) (cz + rightZ * rOut), 0.998f,  0.002f,
            (float) (cx + rightX * rOut), yBot, (float) (cz + rightZ * rOut), 0.998f,  0.998f,
            (float) (cx + rightX * rIn),  yBot, (float) (cz + rightZ * rIn),  0.5005f, 0.998f,
            1.0f, 1.0f, 1.0f, alpha);
        src.m_109912_(rt);

        // 裂开瞬间沿左右切口边缘绘制翡翠金色裂隙微光（纯几何加色辉光，无廉价粒子）
        if (splitRatio > 0.01f) {
            float seamAlpha = alpha * Math.min(1.0f, splitRatio * 3.5f) * 0.72f;
            double seamHalfH = r * 0.95;
            double seamW = 0.065;
            RenderType glowRt = CronusRenderTypes.additive(WHITE_TEX);
            VertexConsumer gvc = src.m_6299_(glowRt);
            emitTexQuad(gvc, mat,
                (float) (cx + rightX * (lIn - seamW)), (float) (cy + seamHalfH), (float) (cz + rightZ * (lIn - seamW)), 0f, 0f,
                (float) (cx + rightX * (lIn + seamW * 0.35)), (float) (cy + seamHalfH), (float) (cz + rightZ * (lIn + seamW * 0.35)), 1f, 0f,
                (float) (cx + rightX * (lIn + seamW * 0.35)), (float) (cy - seamHalfH), (float) (cz + rightZ * (lIn + seamW * 0.35)), 1f, 1f,
                (float) (cx + rightX * (lIn - seamW)), (float) (cy - seamHalfH), (float) (cz + rightZ * (lIn - seamW)), 0f, 1f,
                0.22f, 0.98f, 0.62f, seamAlpha);
            emitTexQuad(gvc, mat,
                (float) (cx + rightX * (rIn - seamW * 0.35)), (float) (cy + seamHalfH), (float) (cz + rightZ * (rIn - seamW * 0.35)), 0f, 0f,
                (float) (cx + rightX * (rIn + seamW)), (float) (cy + seamHalfH), (float) (cz + rightZ * (rIn + seamW)), 1f, 0f,
                (float) (cx + rightX * (rIn + seamW)), (float) (cy - seamHalfH), (float) (cz + rightZ * (rIn + seamW)), 1f, 1f,
                (float) (cx + rightX * (rIn - seamW * 0.35)), (float) (cy - seamHalfH), (float) (cz + rightZ * (rIn - seamW * 0.35)), 0f, 1f,
                0.98f, 0.88f, 0.35f, seamAlpha);
            src.m_109912_(glowRt);
        }
    }

    // ==================== 头顶悬浮能量道具能力硬币图标 ====================

    private static void renderEnergyIcons(Minecraft mc, MultiBufferSource.BufferSource src,
                                          Matrix4f mat, Vec3 cam, float pt) {
        if (ENERGY_ICONS.isEmpty()) {
            return;
        }
        boolean firstPerson = mc.f_91066_.m_92176_().m_90612_();
        int selfId = mc.f_91074_ == null ? -1 : mc.f_91074_.m_19879_();

        // 统计每个实体当前同时生效的图标数，多枚道具时在头顶整齐并排悬浮
        for (int i = 0; i < ENERGY_ICONS.size(); i++) {
            EnergyIconFx icon = ENERGY_ICONS.get(i);
            if (firstPerson && icon.entityId == selfId) {
                continue; // 第一人称交由 CronusHudOverlay 屏幕上方清晰呈现，避免遮挡视野
            }
            int slotIndex = 0;
            int totalForEntity = 0;
            for (int j = 0; j < ENERGY_ICONS.size(); j++) {
                if (ENERGY_ICONS.get(j).entityId == icon.entityId) {
                    if (j < i) {
                        slotIndex++;
                    }
                    totalForEntity++;
                }
            }

            double px = icon.x, py = icon.y, pz = icon.z;
            double entH = 1.85;
            Entity ent = mc.f_91073_ == null ? null : mc.f_91073_.m_6815_(icon.entityId);
            if (ent != null) {
                px = Mth.m_14139_(pt, ent.f_19854_, ent.m_20185_());
                py = Mth.m_14139_(pt, ent.f_19855_, ent.m_20186_());
                pz = Mth.m_14139_(pt, ent.f_19856_, ent.m_20189_());
                entH = ent.m_20206_();
            }

            float ageF = icon.age + pt;
            float remaining = Math.max(0.0f, icon.maxAge - ageF);

            // 弹出弹性缩放 + 硬币旋转登场，结束前 45 ticks 街机闪烁预警，末 6 ticks 收缩消失
            float scale = 1.0f;
            float spinWidth = 1.0f;
            float alpha = 1.0f;
            double riseExtra = 0.0;
            if (ageF < 10.0f) {
                float p = ageF / 10.0f;
                scale = p < 0.65f
                    ? (0.22f + (p / 0.65f) * 1.04f)
                    : (1.26f - ((p - 0.65f) / 0.35f) * 0.26f);
                spinWidth = Math.max(0.14f, Math.abs(Mth.m_14089_(p * (float) Math.PI * 2.0f)));
                riseExtra = (1.0f - p) * -0.25;
            } else if (icon.itemKind == 4 && icon.maxAge <= 60) {
                // 回复道具：轻盈上升并在后半段柔和淡出
                float p = ageF / (float) icon.maxAge;
                riseExtra = p * 0.38;
                alpha = p > 0.62f ? Mth.m_14036_((1.0f - p) / 0.38f, 0f, 1f) : 1.0f;
            } else if (remaining < 45.0f) {
                if (remaining < 6.0f) {
                    float p = remaining / 6.0f;
                    scale = 0.25f + 0.75f * p;
                    alpha = p;
                } else {
                    float freq = remaining < 20.0f ? 1.55f : 0.85f;
                    alpha = 0.36f + 0.64f * (0.5f + 0.5f * Mth.m_14031_(ageF * freq));
                }
            }

            double dx = cam.f_82479_ - px;
            double dz = cam.f_82481_ - pz;
            double hLen = Math.max(1e-4, Math.hypot(dx, dz));
            double rx = dz / hLen;
            double rz = -dx / hLen;

            double sideOff = (slotIndex - (totalForEntity - 1) * 0.5) * 0.82;
            double bob = 0.065 * Mth.m_14031_(ageF * 0.16f + slotIndex * 1.4f);
            double cx = px + rx * sideOff;
            double cy = py + entH + 0.74 + bob + riseExtra;
            double cz = pz + rz * sideOff;

            // 登场瞬间金色拾取扩散光环（图集 7 号格）
            if (ageF < 14.0f) {
                float hp = ageF / 14.0f;
                float haloR = 0.34f + hp * 0.58f;
                float haloA = (1.0f - hp) * 0.90f;
                drawEnergyAtlasCell(src, mat, cx, cy, cz, rx, rz, haloR, haloR, 7, haloA, true);
            }

            double halfH = 0.37 * scale;
            double halfW = halfH * spinWidth;
            drawEnergyAtlasCell(src, mat, cx, cy, cz, rx, rz, halfW, halfH, icon.itemKind, alpha, false);
        }
    }

    private static void drawEnergyAtlasCell(MultiBufferSource.BufferSource src, Matrix4f mat,
                                            double cx, double cy, double cz,
                                            double rx, double rz,
                                            double halfW, double halfH,
                                            int cellIdx, float alpha, boolean additive) {
        if (alpha <= 0.02f) {
            return;
        }
        int col = cellIdx % 4;
        int row = cellIdx / 4;
        float padU = 0.5f / 256.0f;
        float padV = 0.5f / 128.0f;
        float u0 = col * 0.25f + padU;
        float u1 = (col + 1) * 0.25f - padU;
        float v0 = row * 0.50f + padV;
        float v1 = (row + 1) * 0.50f - padV;

        RenderType rt = additive
            ? CronusRenderTypes.additive(ENERGY_ITEMS_TEX)
            : CronusRenderTypes.pixelTranslucent(ENERGY_ITEMS_TEX);
        VertexConsumer vc = src.m_6299_(rt);
        emitTexQuad(vc, mat,
            (float) (cx - rx * halfW), (float) (cy + halfH), (float) (cz - rz * halfW), u0, v0,
            (float) (cx + rx * halfW), (float) (cy + halfH), (float) (cz + rz * halfW), u1, v0,
            (float) (cx + rx * halfW), (float) (cy - halfH), (float) (cz + rz * halfW), u1, v1,
            (float) (cx - rx * halfW), (float) (cy - halfH), (float) (cz - rz * halfW), u0, v1,
            1.0f, 1.0f, 1.0f, Mth.m_14036_(alpha, 0f, 1f));
        src.m_109912_(rt);
    }

    // ==================== 高清特摄漫画打击贴图（HIT! / GREAT! / CRITICAL! / GUARD! / ITEM GET! / OUT!） ====================

    /**
     * 在 3D 世界坐标绘制朝向镜头的高清打击贴图（支持漫画动态倾角与闪白受击帧）。
     * {@code cellIdx}: 0=HIT!, 1=GREAT!, 2=CRITICAL!, 3=GUARD!, 4=ITEM GET!, 5=OUT!。
     */
    public static void renderHitSticker(MultiBufferSource.BufferSource src, Matrix4f mat, Vec3 cam,
                                        double x, double y, double z, int cellIdx,
                                        float halfW, float halfH, float tiltRad, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        double dx = cam.f_82479_ - x;
        double dy = cam.f_82480_ - y;
        double dz = cam.f_82481_ - z;
        double len = Math.max(1e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double fx = dx / len, fy = dy / len, fz = dz / len;
        double rx0 = fz, rz0 = -fx;
        double rLen = Math.max(1e-4, Math.hypot(rx0, rz0));
        rx0 /= rLen;
        rz0 /= rLen;
        double ux0 = fy * rz0;
        double uy0 = fz * rx0 - fx * rz0;
        double uz0 = -fy * rx0;

        // 围绕面向轴施加漫画倾角 tiltRad
        double ct = Math.cos(tiltRad);
        double st = Math.sin(tiltRad);
        double rx = rx0 * ct + ux0 * st;
        double ry = uy0 * st;
        double rz = rz0 * ct + uz0 * st;
        double ux = ux0 * ct - rx0 * st;
        double uy = uy0 * ct;
        double uz = uz0 * ct - rz0 * st;

        int col = cellIdx % 2;
        int row = cellIdx / 2;
        float padU = 1.0f / 1024.0f;
        float padV = 1.0f / 1024.0f;
        float u0 = col * 0.5f + padU;
        float u1 = (col + 1) * 0.5f - padU;
        float v0 = (row * 341.0f) / 1024.0f + padV;
        float v1 = ((row + 1) * 341.0f) / 1024.0f - padV;

        RenderType rt = CronusRenderTypes.translucent(HIT_STICKERS_TEX);
        VertexConsumer vc = src.m_6299_(rt);
        emitTexQuad(vc, mat,
            (float) (x - rx * halfW + ux * halfH), (float) (y - ry * halfW + uy * halfH), (float) (z - rz * halfW + uz * halfH), u0, v0,
            (float) (x + rx * halfW + ux * halfH), (float) (y + ry * halfW + uy * halfH), (float) (z + rz * halfW + uz * halfH), u1, v0,
            (float) (x + rx * halfW - ux * halfH), (float) (y + ry * halfW - uy * halfH), (float) (z + rz * halfW - uz * halfH), u1, v1,
            (float) (x - rx * halfW - ux * halfH), (float) (y - ry * halfW - uy * halfH), (float) (z - rz * halfW - uz * halfH), u0, v1,
            1.0f, 1.0f, 1.0f, Mth.m_14036_(alpha, 0f, 1f));
        src.m_109912_(rt);
    }

    // ==================== 拆分复用的模块化 3D 技能特效 ====================

    private static void renderSpriteBurst(Minecraft mc, MultiBufferSource.BufferSource src,
                                          Matrix4f mat, Vec3 cam, SpriteBurst b, float pt) {
        double px = b.x, py = b.y, pz = b.z;
        if (b.entityId >= 0 && mc.f_91073_ != null && (b.kind == 2 || b.kind == 3 || b.kind == 4)) {
            Entity e = mc.f_91073_.m_6815_(b.entityId);
            if (e != null) {
                px = Mth.m_14139_(pt, e.f_19854_, e.m_20185_());
                py = Mth.m_14139_(pt, e.f_19855_, e.m_20186_());
                pz = Mth.m_14139_(pt, e.f_19856_, e.m_20189_());
            }
        }
        float t = Mth.m_14036_((b.age + pt) / (float) b.maxAge, 0.0f, 1.0f);
        float fadeOut = t > 0.80f ? Mth.m_14036_((1.0f - t) / 0.20f, 0f, 1f) : 1.0f;

        switch (b.kind) {
            case 0 -> {
                // 放射翠绿闪电（双面立体交叉 + 朝向相机）
                int idx = Math.min(ATLAS_ARC.validFrames - 1, (int) (t * (ATLAS_ARC.validFrames - 1)));
                double w = 4.6 * b.scale;
                double h = 2.59 * b.scale;
                double ang = Math.toRadians(b.yawDeg);
                double cy = py + 1.1;
                drawCenteredVerticalQuad(src, mat, ATLAS_ARC, idx, px, cy, pz,
                    Math.cos(ang), Math.sin(ang), w, h, fadeOut);
                drawCenteredVerticalQuad(src, mat, ATLAS_ARC, idx, px, cy, pz,
                    -Math.sin(ang), Math.cos(ang), w, h, fadeOut * 0.85f);
            }
            case 1 -> {
                // 天降巨型翠绿落雷（三面星形交叉，高度 6.5 * scale 格）
                int idx = Math.min(ATLAS_BOLT.validFrames - 1, (int) (t * (ATLAS_BOLT.validFrames - 1)));
                renderVolumetricBolt(src, mat, px, py, pz, b.scale, Math.toRadians(b.yawDeg), idx, fadeOut);
            }
            case 2 -> {
                // 能量道具抽取：头顶编年史卡牌轮盘快切（复用 frames 8..57 的多角色选卡段）
                int frameRange = 49;
                int idx = 8 + Math.min(frameRange, (int) (t * frameRange));
                double dx = cam.f_82479_ - px;
                double dz = cam.f_82481_ - pz;
                double len = Math.max(1e-4, Math.hypot(dx, dz));
                // 面向观察者的水平右向轴
                double rx = dz / len;
                double rz = -dx / len;
                float pop = t < 0.15f ? (0.4f + (t / 0.15f) * 0.6f) : 1.0f;
                double w = 3.2 * b.scale * pop;
                double h = 3.2 * b.scale * pop;
                // 因为选卡画面在 1080x1080 裁切框顶部 y=0..0.28，直接把画布中心放在玩家头顶略下方即可让卡牌悬于头顶！
                drawCenteredVerticalQuad(src, mat, ATLAS_CARD, idx, px, py + 1.65, pz, rx, rz, w, h, fadeOut * 0.96f);
            }
            case 3, 4 -> {
                // 3 = Pause 黄金罗马数字快速成型（0->63）；4 = Reset 黄金罗马数字逆转回溯（63->0）
                int idx = (b.kind == 3)
                    ? Math.min(ATLAS_CLOCK.validFrames - 1, (int) (t * (ATLAS_CLOCK.validFrames - 1)))
                    : Math.max(0, (ATLAS_CLOCK.validFrames - 1) - (int) (t * (ATLAS_CLOCK.validFrames - 1)));
                Entity ent = mc.f_91073_ == null ? null : mc.f_91073_.m_6815_(b.entityId);
                boolean selfFirst = ent != null && ent == mc.f_91074_ && mc.f_91066_.m_92176_().m_90612_();
                double yawRad = Math.toRadians(b.yawDeg);
                double fwdX = -Math.sin(yawRad);
                double fwdZ = Math.cos(yawRad);
                double camDotFwd = (cam.f_82479_ - px) * fwdX + (cam.f_82481_ - pz) * fwdZ;
                double sideSign = (!selfFirst && camDotFwd > 0.25) ? 1.0 : -1.0;
                double rightX = sideSign * Math.cos(yawRad);
                double rightZ = sideSign * Math.sin(yawRad);
                double fwdOff = selfFirst ? 1.85 : 0.68;
                double w = 3.6 * b.scale;
                double h = 3.2 * b.scale;
                drawCenteredVerticalQuad(src, mat, ATLAS_CLOCK, idx,
                    px + fwdX * fwdOff, py + 1.55, pz + fwdZ * fwdOff,
                    rightX, rightZ, w, h, fadeOut);
            }
            default -> {
            }
        }
    }

    // ==================== 3D 几何面片绘制基元 ====================

    /** 按 1920x1080 归一化裁切框把单张图集精准投影到 3D 舞台平面上（锚点 (0.50, 0.80) 对齐 origin）。 */
    private static void drawStageLayer(MultiBufferSource.BufferSource src, Matrix4f mat,
                                       Atlas atlas, int frameIdx,
                                       double ox, double oy, double oz,
                                       double rightX, double rightZ,
                                       double stageW, double stageH, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        double xL = (atlas.nx0 - 0.50) * stageW;
        double xR = (atlas.nx1 - 0.50) * stageW;
        double yTop = (0.80 - atlas.ny0) * stageH;
        double yBot = (0.80 - atlas.ny1) * stageH;

        float u0 = cellU0(atlas, frameIdx);
        float u1 = cellU1(atlas, frameIdx);
        float v0 = cellV0(atlas, frameIdx);
        float v1 = cellV1(atlas, frameIdx);

        RenderType rt = atlas.renderType();
        VertexConsumer vc = src.m_6299_(rt);
        emitTexQuad(vc, mat,
            (float) (ox + rightX * xL), (float) (oy + yTop), (float) (oz + rightZ * xL), u0, v0,
            (float) (ox + rightX * xR), (float) (oy + yTop), (float) (oz + rightZ * xR), u1, v0,
            (float) (ox + rightX * xR), (float) (oy + yBot), (float) (oz + rightZ * xR), u1, v1,
            (float) (ox + rightX * xL), (float) (oy + yBot), (float) (oz + rightZ * xL), u0, v1,
            1.0f, 1.0f, 1.0f, Mth.m_14036_(alpha, 0f, 1f));
        src.m_109912_(rt);
    }

    /** 三面星形交叉立体天降落雷（0° / 60° / 120°），从任意视角看都是饱满的 3D 绿雷柱。 */
    private static void renderVolumetricBolt(MultiBufferSource.BufferSource src, Matrix4f mat,
                                             double x, double footY, double z,
                                             float scale, double baseYawRad, int frameIdx, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        double halfW = 1.95 * scale;
        double height = 6.6 * scale;
        float u0 = cellU0(ATLAS_BOLT, frameIdx);
        float u1 = cellU1(ATLAS_BOLT, frameIdx);
        float v0 = cellV0(ATLAS_BOLT, frameIdx);
        float v1 = cellV1(ATLAS_BOLT, frameIdx);
        float yBot = (float) (footY - 0.15);
        float yTop = (float) (footY + height);

        RenderType rt = ATLAS_BOLT.renderType();
        VertexConsumer vc = src.m_6299_(rt);
        for (int p = 0; p < 3; p++) {
            double ang = baseYawRad + (Math.PI * p / 3.0);
            double rx = Math.cos(ang) * halfW;
            double rz = Math.sin(ang) * halfW;
            float a = (p == 0) ? alpha : alpha * 0.78f;
            emitTexQuad(vc, mat,
                (float) (x - rx), yTop, (float) (z - rz), u0, v0,
                (float) (x + rx), yTop, (float) (z + rz), u1, v0,
                (float) (x + rx), yBot, (float) (z + rz), u1, v1,
                (float) (x - rx), yBot, (float) (z - rz), u0, v1,
                1.0f, 1.0f, 1.0f, Mth.m_14036_(a, 0f, 1f));
        }
        src.m_109912_(rt);
    }

    private static void drawCenteredVerticalQuad(MultiBufferSource.BufferSource src, Matrix4f mat,
                                                 Atlas atlas, int frameIdx,
                                                 double cx, double cy, double cz,
                                                 double rightX, double rightZ,
                                                 double width, double height, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        double hw = width * 0.5;
        double hh = height * 0.5;
        float u0 = cellU0(atlas, frameIdx);
        float u1 = cellU1(atlas, frameIdx);
        float v0 = cellV0(atlas, frameIdx);
        float v1 = cellV1(atlas, frameIdx);

        RenderType rt = atlas.renderType();
        VertexConsumer vc = src.m_6299_(rt);
        emitTexQuad(vc, mat,
            (float) (cx - rightX * hw), (float) (cy + hh), (float) (cz - rightZ * hw), u0, v0,
            (float) (cx + rightX * hw), (float) (cy + hh), (float) (cz + rightZ * hw), u1, v0,
            (float) (cx + rightX * hw), (float) (cy - hh), (float) (cz + rightZ * hw), u1, v1,
            (float) (cx - rightX * hw), (float) (cy - hh), (float) (cz - rightZ * hw), u0, v1,
            1.0f, 1.0f, 1.0f, Mth.m_14036_(alpha, 0f, 1f));
        src.m_109912_(rt);
    }

    private static float cellU0(Atlas a, int idx) {
        return (idx % a.cols) / (float) a.cols + a.padU;
    }

    private static float cellU1(Atlas a, int idx) {
        return ((idx % a.cols) + 1) / (float) a.cols - a.padU;
    }

    private static float cellV0(Atlas a, int idx) {
        return (idx / a.cols) / (float) a.rows + a.padV;
    }

    private static float cellV1(Atlas a, int idx) {
        return ((idx / a.cols) + 1) / (float) a.rows - a.padV;
    }

    private static void emitTexQuad(VertexConsumer vc, Matrix4f mat,
                                    float ax, float ay, float az, float au, float av,
                                    float bx, float by, float bz, float bu, float bv,
                                    float cx, float cy, float cz, float cu, float cv,
                                    float dx, float dy, float dz, float du, float dv,
                                    float r, float g, float b, float a) {
        vc.m_252986_(mat, ax, ay, az).m_85950_(r, g, b, a).m_7421_(au, av).m_5752_();
        vc.m_252986_(mat, bx, by, bz).m_85950_(r, g, b, a).m_7421_(bu, bv).m_5752_();
        vc.m_252986_(mat, cx, cy, cz).m_85950_(r, g, b, a).m_7421_(cu, cv).m_5752_();
        vc.m_252986_(mat, dx, dy, dz).m_85950_(r, g, b, a).m_7421_(du, dv).m_5752_();
    }
}
