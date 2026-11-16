package com.rpgtemplate.forge.ext;

import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ExtNetwork {
    private ExtNetwork() {
    }

    public static void register() {
        // Registered via ModNetwork.onRegisterPayloads
    }

    public static void registerPayloads(PayloadRegistrar reg) {
        reg.playToServer(AirJump.TYPE, AirJump.STREAM_CODEC, AirJump::handle);
        reg.playToServer(DomainRadius.TYPE, DomainRadius.STREAM_CODEC, DomainRadius::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public record AirJump(int count) implements CustomPacketPayload {
        public static final Type<AirJump> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "ext_air_jump"));
        public static final StreamCodec<FriendlyByteBuf, AirJump> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), AirJump::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void encode(AirJump msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.count);
        }

        public static AirJump decode(FriendlyByteBuf buf) {
            return new AirJump(buf.readVarInt());
        }

        public static void handle(AirJump msg, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    SukunaPassives.onAirJump(sp, msg.count);
                }
            });
        }
    }

    public record DomainRadius(int direction, boolean big) implements CustomPacketPayload {
        public static final Type<DomainRadius> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "ext_domain_radius"));
        public static final StreamCodec<FriendlyByteBuf, DomainRadius> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), DomainRadius::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void encode(DomainRadius msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.direction);
            buf.writeBoolean(msg.big);
        }

        public static DomainRadius decode(FriendlyByteBuf buf) {
            return new DomainRadius(buf.readByte(), buf.readBoolean());
        }

        public static void handle(DomainRadius msg, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    DomainExpansionSkill.adjustRadius(sp, msg.direction, msg.big);
                }
            });
        }
    }
}
