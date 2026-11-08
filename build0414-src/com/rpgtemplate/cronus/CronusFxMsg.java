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
    public final float param;
    public final int extra;

    public CronusFxMsg(byte kind, int entityId, double x, double y, double z, float param, int extra) {
        this.kind = kind;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
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
    }

    public static CronusFxMsg decode(FriendlyByteBuf buf) {
        return new CronusFxMsg(
            buf.readByte(),
            buf.m_130242_(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.m_130242_());
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
