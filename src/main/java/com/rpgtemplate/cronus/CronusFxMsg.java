package com.rpgtemplate.cronus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 假面骑士 Cronus 纯几何动态世界特效同步包（服务端 -> 客户端，NeoForge 1.21.1 CustomPacketPayload）。
 */
public final class CronusFxMsg implements CustomPacketPayload {
    public static final Type<CronusFxMsg> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "cronus_fx"));
    public static final StreamCodec<FriendlyByteBuf, CronusFxMsg> STREAM_CODEC =
        StreamCodec.of((buf, msg) -> encode(msg, buf), CronusFxMsg::decode);

    public static final byte KIND_DOMAIN_PAUSE = 0;
    public static final byte KIND_DOMAIN_RESTART = 1;
    public static final byte KIND_ARCADE_POPUP = 2;
    public static final byte KIND_RIDER_KICK = 3;
    public static final byte KIND_PAUSE_TRAP = 4;
    public static final byte KIND_OUT_BANNER = 5;
    public static final byte KIND_CLOCK_RIPPLE = 6;
    public static final byte KIND_HENSHIN_SEQ = 7;
    public static final byte KIND_SPRITE_FX = 8;
    public static final byte KIND_ENERGY_ICON = 9;
    public static final byte KIND_BEAM_LINE = 10;
    public static final byte KIND_HENSHIN_FAST = 11;
    public static final byte KIND_SUIT_STATE = 12;
    public static final byte KIND_CONTINUOUS_BEAM = 13;
    public static final byte KIND_ENERGY_BLAST = 14;
    public static final byte KIND_CHAINSAW_CHARGE = 15;
    public static final byte KIND_FINISHER_FX = 16;
    public static final byte KIND_PAUSE_FROZEN_SYNC = 17;
    public static final byte KIND_PAUSE_HIT_SCAR = 18;

    public static final int FINISHER_CRITICAL_JUDGMENT = 0;
    public static final int FINISHER_CRITICAL_JUDGMENT_PAUSE = 1;
    public static final int FINISHER_CRITICAL_SACRIFICE = 2;
    public static final int FINISHER_CRITICAL_CREWS_AID = 3;
    public static final int FINISHER_SHUT_DOWN = 4;

    public static final int BEAM_GUN = 0;
    public static final int BEAM_BLINK = 1;
    public static final int BEAM_PAUSE_CANNON = 2;

    public static final int ENERGY_SPEED = 0;
    public static final int ENERGY_MUSCLE = 1;
    public static final int ENERGY_IRON = 2;
    public static final int ENERGY_JUMP = 3;
    public static final int ENERGY_RECOVERY = 4;

    public static final int SPRITE_LIGHTNING_ARC = 0;
    public static final int SPRITE_LIGHTNING_BOLT = 1;
    public static final int SPRITE_CARD_ROULETTE = 2;
    public static final int SPRITE_CLOCK_ASSEMBLE = 3;
    public static final int SPRITE_CLOCK_REWIND = 4;

    public static final int POPUP_HIT = 0;
    public static final int POPUP_GREAT = 1;
    public static final int POPUP_CRITICAL = 2;
    public static final int POPUP_GUARD = 3;
    public static final int POPUP_ITEM_GET = 4;

    private static final int[] EMPTY_IDS = new int[0];

    public final byte kind;
    public final int entityId;
    public final double x;
    public final double y;
    public final double z;
    public final double tx;
    public final double ty;
    public final double tz;
    public final float param;
    public final int extra;
    public final int[] intArray;

    public CronusFxMsg(byte kind, int entityId, double x, double y, double z, float param, int extra) {
        this(kind, entityId, x, y, z, x, y, z, param, extra, EMPTY_IDS);
    }

    public CronusFxMsg(byte kind, int entityId, double x, double y, double z,
                       double tx, double ty, double tz, float param, int extra) {
        this(kind, entityId, x, y, z, tx, ty, tz, param, extra, EMPTY_IDS);
    }

    public CronusFxMsg(byte kind, int entityId, double x, double y, double z,
                       double tx, double ty, double tz, float param, int extra, int[] intArray) {
        this.kind = kind;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.param = param;
        this.extra = extra;
        this.intArray = (intArray == null) ? EMPTY_IDS : intArray;
    }

    public static CronusFxMsg frozenSync(int ownerId, double x, double y, double z, boolean clear, int[] ids) {
        return new CronusFxMsg(KIND_PAUSE_FROZEN_SYNC, ownerId, x, y, z, x, y, z, 0f, clear ? 1 : 0, ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(CronusFxMsg m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeVarInt(m.entityId);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.param);
        buf.writeVarInt(m.extra);
        if (m.kind == KIND_BEAM_LINE || m.kind == KIND_CONTINUOUS_BEAM || m.kind == KIND_SUIT_STATE) {
            buf.writeDouble(m.tx);
            buf.writeDouble(m.ty);
            buf.writeDouble(m.tz);
        } else if (m.kind == KIND_PAUSE_FROZEN_SYNC) {
            int n = Math.min(m.intArray.length, 128);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeVarInt(m.intArray[i]);
            }
        }
    }

    public static CronusFxMsg decode(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        int entityId = buf.readVarInt();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float param = buf.readFloat();
        int extra = buf.readVarInt();
        if (kind == KIND_BEAM_LINE || kind == KIND_CONTINUOUS_BEAM || kind == KIND_SUIT_STATE) {
            double tx = buf.readDouble();
            double ty = buf.readDouble();
            double tz = buf.readDouble();
            return new CronusFxMsg(kind, entityId, x, y, z, tx, ty, tz, param, extra, EMPTY_IDS);
        }
        if (kind == KIND_PAUSE_FROZEN_SYNC) {
            int n = Math.max(0, Math.min(buf.readVarInt(), 128));
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = buf.readVarInt();
            }
            return new CronusFxMsg(kind, entityId, x, y, z, x, y, z, param, extra, ids);
        }
        return new CronusFxMsg(kind, entityId, x, y, z, x, y, z, param, extra, EMPTY_IDS);
    }

    public static void handle(CronusFxMsg msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                handleClient(msg);
            }
        });
    }

    private static void handleClient(CronusFxMsg msg) {
        com.rpgtemplate.cronus.client.CronusClientFx.accept(msg);
    }
}
