package com.rpgtemplate.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RainbowBurstPayload(int kind, int accentColor) implements CustomPacketPayload {
    public static final Type<RainbowBurstPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "rainbow_burst"));
    public static final StreamCodec<FriendlyByteBuf, RainbowBurstPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> encode(msg, buf), RainbowBurstPayload::decode);

    public static final int KIND_ACTIVATE = 0;
    public static final int KIND_NEW_TEMPLATE = 1;
    public static final int KIND_ADVANCE = 2;

    public RainbowBurstPayload(int kind) {
        this(kind, 16777215);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static RainbowBurstPayload decode(FriendlyByteBuf buf) {
        return new RainbowBurstPayload(buf.readVarInt(), buf.readInt());
    }

    public static void encode(RainbowBurstPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.kind());
        buf.writeInt(msg.accentColor());
    }
}
