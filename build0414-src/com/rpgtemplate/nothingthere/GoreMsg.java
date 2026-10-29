package com.rpgtemplate.nothingthere;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 血效果消息（服务端 -> 附近客户端）。只传参数，形状由客户端用 seed 生成。
 * WOUND：entityId = 被击中的生物，count = 持续 tick，radius = 强度。
 * CAST_MIST：entityId = 施法者，count = 血雾团数，radius = 血雾范围半径。
 */
public final class GoreMsg {
    public static final int WOUND = 0;
    public static final int CAST_MIST = 1;

    public final int kind;
    public final double x;
    public final double y;
    public final double z;
    public final double dx;
    public final double dy;
    public final double dz;
    public final float radius;
    public final int count;
    public final int seed;
    public final int entityId;

    public GoreMsg(int kind, double x, double y, double z, double dx, double dy, double dz,
                   float radius, int count, int seed, int entityId) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.radius = radius;
        this.count = count;
        this.seed = seed;
        this.entityId = entityId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dx);
        buf.writeDouble(dy);
        buf.writeDouble(dz);
        buf.writeFloat(radius);
        buf.writeInt(count);
        buf.writeInt(seed);
        buf.writeInt(entityId);
    }

    public static GoreMsg decode(FriendlyByteBuf buf) {
        int kind = buf.readByte();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        double dx = buf.readDouble();
        double dy = buf.readDouble();
        double dz = buf.readDouble();
        float radius = buf.readFloat();
        int count = buf.readInt();
        int seed = buf.readInt();
        int entityId = buf.readInt();
        return new GoreMsg(kind, x, y, z, dx, dy, dz, radius, count, seed, entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(this)));
        ctx.setPacketHandled(true);
    }

    /** 客户端入口（只在客户端调用，服务端不会加载客户端类）。 */
    private static void handleClient(GoreMsg msg) {
        com.rpgtemplate.nothingthere.client.NtGoreFx.accept(msg);
    }
}
