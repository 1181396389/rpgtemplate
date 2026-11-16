package com.rpgtemplate.cronus;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 假面骑士 Cronus 纯几何动态世界特效同步包（服务端 -> 客户端）。
 */
public final class CronusFxMsg {
    /** 开启【Pause】地面巨型罗马数字时钟法阵。 */
    public static final byte KIND_DOMAIN_PAUSE = 0;
    /** 触发【Restart】表针急速旋转与翠绿光柱碎裂爆发。 */
    public static final byte KIND_DOMAIN_RESTART = 1;
    /** 弹出原剧街机风动态矢量字：extra=0(HIT!), 1(GREAT!), 2(CRITICAL!), 3(GUARD!), 4(ITEM GET!)。 */
    public static final byte KIND_ARCADE_POPUP = 2;
    /** 释放【Critical Crews-Aid】脚下旋转表针与飞踢冲击环：extra=0(瞬踢), 1(蓄力回旋踢)。 */
    public static final byte KIND_RIDER_KICK = 3;
    /** 时停专属必杀悬停构造物：extra=0(链锯·断罪十字刻四刃悬停), 1(光束枪·12 枚包围网光弹悬停)。 */
    public static final byte KIND_PAUSE_TRAP = 4;
    /** 屏幕中央特摄风「绝版！ / CRITICAL CREWS-AID」处决演出横幅。 */
    public static final byte KIND_OUT_BANNER = 5;
    /** 三段跳 / 变身脚下微型时钟波纹。 */
    public static final byte KIND_CLOCK_RIPPLE = 6;
    /** 编年史变身完整 7.8s（234 帧原速）四层同步 3D 序列演出。 */
    public static final byte KIND_HENSHIN_SEQ = 7;
    /** 模块化 3D 序列特效：extra=0(放射绿雷), 1(天降巨型绿雷), 2(头顶卡牌快切), 3(黄金时钟成型), 4(黄金时钟逆转)。 */
    public static final byte KIND_SPRITE_FX = 8;
    /** 能量道具头顶悬浮能力图标：extra=能力编号(0高速/1肌肉/2钢铁/3跳跃/4回复), param=持续 ticks。 */
    public static final byte KIND_ENERGY_ICON = 9;
    /** 3D 能量光束/瞬移时空轨线：extra=0(缺陷护罩Ⅱ 双管翡翠激光), 1(时序瞬移金绿时空残影轨)。 */
    public static final byte KIND_BEAM_LINE = 10;

    public static final int BEAM_GUN = 0;
    public static final int BEAM_BLINK = 1;

    public static final int ENERGY_SPEED = 0;
    public static final int ENERGY_MUSCLE = 1;
    public static final int ENERGY_IRON = 2;
    public static final int ENERGY_JUMP = 3;
    public static final int ENERGY_RECOVERY = 4;

    public static final int SPRITE_LIGHTNING_ARC = 0;
    public static final int SPRITE_LIGHTNING_BOLT = 1;
    public static final int SPRITE_CARD_ROULETTE = 2;
    public static final int SPRITE_CLOCK_ASSEMBLE = 3;
    public static final int SPRITE_CLOCK_REWIND = 4;

    public static final int POPUP_HIT = 0;
    public static final int POPUP_GREAT = 1;
    public static final int POPUP_CRITICAL = 2;
    public static final int POPUP_GUARD = 3;
    public static final int POPUP_ITEM_GET = 4;

    public final byte kind;
    public final int entityId;
    public final double x;
    public final double y;
    public final double z;
    public final double tx;
    public final double ty;
    public final double tz;
    public final float param;
    public final int extra;

    public CronusFxMsg(byte kind, int entityId, double x, double y, double z, float param, int extra) {
        this(kind, entityId, x, y, z, x, y, z, param, extra);
    }

    public CronusFxMsg(byte kind, int entityId, double x, double y, double z,
                       double tx, double ty, double tz, float param, int extra) {
        this.kind = kind;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.param = param;
        this.extra = extra;
    }

    public static void encode(CronusFxMsg m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.m_130130_(m.entityId);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.param);
        buf.m_130130_(m.extra);
        if (m.kind == KIND_BEAM_LINE) {
            buf.writeDouble(m.tx);
            buf.writeDouble(m.ty);
            buf.writeDouble(m.tz);
        }
    }

    public static CronusFxMsg decode(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        int entityId = buf.m_130242_();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float param = buf.readFloat();
        int extra = buf.m_130242_();
        if (kind == KIND_BEAM_LINE) {
            double tx = buf.readDouble();
            double ty = buf.readDouble();
            double tz = buf.readDouble();
            return new CronusFxMsg(kind, entityId, x, y, z, tx, ty, tz, param, extra);
        }
        return new CronusFxMsg(kind, entityId, x, y, z, param, extra);
    }

    public static void handle(CronusFxMsg msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    private static void handleClient(CronusFxMsg msg) {
        com.rpgtemplate.cronus.client.CronusClientFx.accept(msg);
    }
}
