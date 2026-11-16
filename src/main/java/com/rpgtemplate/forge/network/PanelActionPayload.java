package com.rpgtemplate.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PanelActionPayload(String action, String arg) implements CustomPacketPayload {
    public static final Type<PanelActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "panel_action"));
    public static final StreamCodec<FriendlyByteBuf, PanelActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> encode(msg, buf), PanelActionPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static PanelActionPayload decode(FriendlyByteBuf buf) {
        return new PanelActionPayload(buf.readUtf(64), buf.readUtf(512));
    }

    public static void encode(PanelActionPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action(), 64);
        buf.writeUtf(msg.arg(), 512);
    }
}
