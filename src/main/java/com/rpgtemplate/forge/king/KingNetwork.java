package com.rpgtemplate.forge.king;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class KingNetwork {
    public static Consumer<KingNetwork.State> CLIENT = s -> {};

    private KingNetwork() {
    }

    public static void register() {
        // Registered via ModNetwork.onRegisterPayloads
    }

    public static void registerPayloads(PayloadRegistrar reg) {
        reg.playToServer(Input.TYPE, Input.STREAM_CODEC, Input::handle);
        reg.playToClient(State.TYPE, State.STREAM_CODEC, State::handle);
    }

    public static void send(int action, int hand, boolean down) {
        PacketDistributor.sendToServer(new KingNetwork.Input(action, hand, down));
    }

    public static void sendTrackingAndSelf(ServerPlayer player, KingNetwork.State state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, state);
    }

    public static void sendToPlayer(ServerPlayer player, KingNetwork.State state) {
        if (player != null && player.connection != null) {
            PacketDistributor.sendToPlayer(player, state);
        }
    }

    public record Input(int action, int hand, boolean down) implements CustomPacketPayload {
        public static final Type<Input> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "king_hands_input"));
        public static final StreamCodec<FriendlyByteBuf, Input> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), Input::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void encode(Input msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.action);
            buf.writeByte(msg.hand);
            buf.writeBoolean(msg.down);
        }

        static Input decode(FriendlyByteBuf buf) {
            return new Input(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readBoolean());
        }

        static void handle(Input msg, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp && msg.action <= 5 && msg.hand <= 1) {
                    ExtraHands.input(sp, msg.action, msg.hand, msg.down);
                }
            });
        }
    }

    public record State(UUID id, boolean active, float height, float width, boolean basket, int selected, ItemStack third, ItemStack fourth)
            implements CustomPacketPayload {
        public static final Type<State> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "king_hands_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), State::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void encode(State msg, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(msg.id);
            buf.writeBoolean(msg.active);
            buf.writeFloat(msg.height);
            buf.writeFloat(msg.width);
            buf.writeBoolean(msg.basket);
            buf.writeByte(msg.selected);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.third == null ? ItemStack.EMPTY : msg.third);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.fourth == null ? ItemStack.EMPTY : msg.fourth);
        }

        static State decode(RegistryFriendlyByteBuf buf) {
            return new State(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readUnsignedByte(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
            );
        }

        static void handle(State msg, IPayloadContext ctx) {
            ctx.enqueueWork(() -> KingNetwork.CLIENT.accept(msg));
        }
    }
}
