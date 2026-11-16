package com.rpgtemplate.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkillInputPayload(int slot, boolean pressed) implements CustomPacketPayload {
    public static final Type<SkillInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "skill_input"));
    public static final StreamCodec<FriendlyByteBuf, SkillInputPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> encode(msg, buf), SkillInputPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SkillInputPayload decode(FriendlyByteBuf buf) {
        return new SkillInputPayload(buf.readVarInt(), buf.readBoolean());
    }

    public static void encode(SkillInputPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot());
        buf.writeBoolean(msg.pressed());
    }
}
