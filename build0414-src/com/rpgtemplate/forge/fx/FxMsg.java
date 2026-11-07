package com.rpgtemplate.forge.fx;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 非粒子特效消息：形状 + 颜色 + 尺寸 + 寿命 + 扩散 + 一组点（x,y,z 依次排列）。
 * 客户端为每个点生成一个几何图元（火花、烟团、环、球壳），不使用任何粒子。
 */
public final class FxMsg {
    public final int shape;
    public final int rgb;
    public final float size;
    public final int life;
    public final float spread;
    public final int seed;
    public final double[] pts;

    public FxMsg(int shape, int rgb, float size, int life, float spread, int seed, double[] pts) {
        this.shape = shape;
        this.rgb = rgb;
        this.size = size;
        this.life = life;
        this.spread = spread;
        this.seed = seed;
        this.pts = pts;
    }

    public int count() {
        return pts.length / 3;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(shape);
        buf.writeInt(rgb);
        buf.writeFloat(size);
        buf.writeInt(life);
        buf.writeFloat(spread);
        buf.writeInt(seed);
        buf.writeInt(pts.length / 3);
        for (double d : pts) {
            buf.writeDouble(d);
        }
    }

    public static FxMsg decode(FriendlyByteBuf buf) {
        int shape = buf.readByte();
        int rgb = buf.readInt();
        float size = buf.readFloat();
        int life = buf.readInt();
        float spread = buf.readFloat();
        int seed = buf.readInt();
        int n = buf.readInt();
        double[] pts = new double[n * 3];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = buf.readDouble();
        }
        return new FxMsg(shape, rgb, size, life, spread, seed, pts);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(this)));
        ctx.setPacketHandled(true);
    }

    /** 客户端入口（只在客户端调用，服务端不会加载客户端类）。 */
    private static void handleClient(FxMsg msg) {
        com.rpgtemplate.forge.fx.client.FxRenderer.accept(msg);
    }
}
