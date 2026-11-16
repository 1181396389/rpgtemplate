package com.rpgtemplate.nothingthere;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class GoreMsg implements CustomPacketPayload {
    public static final Type<GoreMsg> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_gore"));
    public static final StreamCodec<FriendlyByteBuf, GoreMsg> STREAM_CODEC =
        StreamCodec.of((buf, msg) -> msg.encode(buf), GoreMsg::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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

    public static void handle(GoreMsg msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                handleClient(msg);
            }
        });
    }

    private static void handleClient(GoreMsg msg) {
        com.rpgtemplate.nothingthere.client.NtGoreFx.accept(msg);
    }
}
