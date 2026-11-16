package com.rpgtemplate.forge.possession;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PossessionNet {
    public static volatile PossessionNet.ClientSink sink;

    private PossessionNet() {
    }

    private static int checkedCount(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n >= 0 && n <= max) {
            return n;
        }
        throw new DecoderException("Invalid possession list size: " + n);
    }

    public static void register() {
        // Registered via ModNetwork.onRegisterPayloads
    }

    public static void registerPayloads(PayloadRegistrar reg) {
        reg.playToServer(SoulAction.TYPE, SoulAction.STREAM_CODEC, SoulAction::handle);
        reg.playToClient(State.TYPE, State.STREAM_CODEC, (msg, ctx) -> client(ctx, () -> sink.state(msg)));
        reg.playToClient(BodyMap.TYPE, BodyMap.STREAM_CODEC, (msg, ctx) -> client(ctx, () -> sink.bodies(msg)));
        reg.playToClient(Borrowed.TYPE, Borrowed.STREAM_CODEC, (msg, ctx) -> client(ctx, () -> sink.borrowed(msg)));
    }

    private static void client(IPayloadContext ctx, Runnable action) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT && sink != null) {
                action.run();
            }
        });
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player != null && player.connection != null) {
            try {
                PacketDistributor.sendToPlayer(player, payload);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void sendAll(CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToAllPlayers(payload);
        } catch (RuntimeException ignored) {
        }
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public record BodyMap(List<BodyMap.Entry> entries) implements CustomPacketPayload {
        public static final Type<BodyMap> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "soul_body_map"));
        public static final StreamCodec<FriendlyByteBuf, BodyMap> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BodyMap::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void encode(BodyMap msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entries.size());
            for (BodyMap.Entry e : msg.entries) {
                buf.writeUUID(e.controller);
                buf.writeUUID(e.host);
                buf.writeUtf(e.hostName, 64);
            }
        }

        public static BodyMap decode(FriendlyByteBuf buf) {
            int n = PossessionNet.checkedCount(buf, 256);
            ArrayList<BodyMap.Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new BodyMap.Entry(buf.readUUID(), buf.readUUID(), buf.readUtf(64)));
            }
            return new BodyMap(list);
        }

        public record Entry(UUID controller, UUID host, String hostName) {
        }
    }

    public record Borrowed(boolean active, String lender, float mp, float maxMp, List<Borrowed.Skill> skills)
            implements CustomPacketPayload {
        public static final Type<Borrowed> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "soul_borrowed"));
        public static final StreamCodec<FriendlyByteBuf, Borrowed> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), Borrowed::decode);
        public static final Borrowed NONE = new Borrowed(false, "", 0.0F, 0.0F, List.of());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void encode(Borrowed msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.active);
            buf.writeUtf(msg.lender, 64);
            buf.writeFloat(msg.mp);
            buf.writeFloat(msg.maxMp);
            buf.writeVarInt(msg.skills.size());
            for (Borrowed.Skill s : msg.skills) {
                buf.writeUtf(s.name, 128);
                buf.writeVarInt(s.cdLeft);
                buf.writeBoolean(s.usable);
            }
        }

        public static Borrowed decode(FriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            String lender = buf.readUtf(64);
            float mp = buf.readFloat();
            float maxMp = buf.readFloat();
            int n = PossessionNet.checkedCount(buf, 64);
            ArrayList<Borrowed.Skill> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Borrowed.Skill(buf.readUtf(128), buf.readVarInt(), buf.readBoolean()));
            }
            return new Borrowed(active, lender, mp, maxMp, list);
        }

        public record Skill(String name, int cdLeft, boolean usable) {
        }
    }

    public interface ClientSink {
        void state(State state);

        void bodies(BodyMap map);

        void borrowed(Borrowed borrowed);
    }

    public record SoulAction(int action) implements CustomPacketPayload {
        public static final Type<SoulAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "soul_action"));
        public static final StreamCodec<FriendlyByteBuf, SoulAction> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SoulAction::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void encode(SoulAction msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.action);
        }

        public static SoulAction decode(FriendlyByteBuf buf) {
            return new SoulAction(buf.readByte());
        }

        public static void handle(SoulAction msg, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    Possession.onSoulAction(sp, msg.action);
                }
            });
        }
    }

    public record State(
        int view,
        String other,
        float progress,
        int battleLeft,
        boolean resonance,
        boolean sukunaSide,
        boolean watch,
        boolean lending,
        boolean hostBorrow,
        int contractLeft,
        int contractCd,
        boolean fingerLost
    ) implements CustomPacketPayload {
        public static final Type<State> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "soul_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), State::decode);
        public static final State NONE = new State(0, "", 0.0F, 0, false, false, false, false, false, 0, 0, false);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void encode(State msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.view);
            buf.writeUtf(msg.other, 64);
            buf.writeFloat(msg.progress);
            buf.writeVarInt(msg.battleLeft);
            buf.writeBoolean(msg.resonance);
            buf.writeBoolean(msg.sukunaSide);
            buf.writeBoolean(msg.watch);
            buf.writeBoolean(msg.lending);
            buf.writeBoolean(msg.hostBorrow);
            buf.writeVarInt(msg.contractLeft);
            buf.writeVarInt(msg.contractCd);
            buf.writeBoolean(msg.fingerLost);
        }

        public static State decode(FriendlyByteBuf buf) {
            return new State(
                buf.readByte(),
                buf.readUtf(64),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean()
            );
        }
    }
}
