package com.rpgtemplate.forge.fx;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FxMsg implements CustomPacketPayload {
    public static final Type<FxMsg> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "fx"));
    public static final StreamCodec<FriendlyByteBuf, FxMsg> STREAM_CODEC =
        StreamCodec.of((buf, msg) -> msg.encode(buf), FxMsg::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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

    public static void handle(FxMsg msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                handleClient(msg);
            }
        });
    }

    private static void handleClient(FxMsg msg) {
        com.rpgtemplate.forge.fx.client.FxRenderer.accept(msg);
    }
}
