package com.rpgtemplate.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChantPayload(String line, int stage, int maxStage, boolean clear) implements CustomPacketPayload {
    public static final Type<ChantPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "chant"));
    public static final StreamCodec<FriendlyByteBuf, ChantPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> encode(msg, buf), ChantPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ChantPayload decode(FriendlyByteBuf buf) {
        return new ChantPayload(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void encode(ChantPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.line(), 256);
        buf.writeVarInt(msg.stage());
        buf.writeVarInt(msg.maxStage());
        buf.writeBoolean(msg.clear());
    }
}
