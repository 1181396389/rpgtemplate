package com.rpgtemplate.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncSystemDataPayload(String json) implements CustomPacketPayload {
    public static final Type<SyncSystemDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "sync_system_data"));
    public static final StreamCodec<FriendlyByteBuf, SyncSystemDataPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> encode(msg, buf), SyncSystemDataPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncSystemDataPayload decode(FriendlyByteBuf buf) {
        return new SyncSystemDataPayload(buf.readUtf(1048576));
    }

    public static void encode(SyncSystemDataPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json(), 1048576);
    }
}
