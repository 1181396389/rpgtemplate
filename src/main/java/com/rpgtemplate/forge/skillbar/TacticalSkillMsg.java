package com.rpgtemplate.forge.skillbar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class TacticalSkillMsg implements CustomPacketPayload {
    public static final Type<TacticalSkillMsg> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "tactical_skill"));
    public static final StreamCodec<FriendlyByteBuf, TacticalSkillMsg> STREAM_CODEC =
        StreamCodec.of((buf, msg) -> encode(msg, buf), TacticalSkillMsg::decode);

    public static final byte KIND_SKILL_PRESS = 0;
    public static final byte KIND_SKILL_RELEASE = 1;
    public static final byte KIND_AIR_ACTION = 2;

    public static final int AIR_JUMP = 0;
    public static final int AIR_HOVER_START = 1;
    public static final int AIR_HOVER_STOP = 2;

    public final byte kind;
    public final int value;

    public TacticalSkillMsg(byte kind, int value) {
        this.kind = kind;
        this.value = value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(TacticalSkillMsg msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.kind);
        buf.writeVarInt(msg.value);
    }

    public static TacticalSkillMsg decode(FriendlyByteBuf buf) {
        return new TacticalSkillMsg(buf.readByte(), buf.readVarInt());
    }

    public static void handle(TacticalSkillMsg msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) {
                return;
            }
            switch (msg.kind) {
                case KIND_SKILL_PRESS -> ServerSkillInvoker.pressSlot(sender, msg.value);
                case KIND_SKILL_RELEASE -> ServerSkillInvoker.releaseSlot(sender, msg.value);
                case KIND_AIR_ACTION -> com.rpgtemplate.cronus.CronusCombatAndPassives.handleAirAction(sender, msg.value);
                default -> {
                }
            }
        });
    }
}
