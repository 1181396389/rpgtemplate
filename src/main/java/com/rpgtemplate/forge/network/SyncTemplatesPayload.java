package com.rpgtemplate.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncTemplatesPayload(String json) implements CustomPacketPayload {
    public static final Type<SyncTemplatesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "sync_templates"));
    public static final StreamCodec<FriendlyByteBuf, SyncTemplatesPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> encode(msg, buf), SyncTemplatesPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncTemplatesPayload decode(FriendlyByteBuf buf) {
        return new SyncTemplatesPayload(buf.readUtf(4194304));
    }

    public static void encode(SyncTemplatesPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json(), 4194304);
    }
}
