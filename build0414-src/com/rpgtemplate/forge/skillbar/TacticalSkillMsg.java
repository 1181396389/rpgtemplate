package com.rpgtemplate.forge.skillbar;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 战术技能栏扩展与空中身法同步数据包（客户端 -> 服务端）。
 * <ul>
 *   <li>{@link #KIND_SKILL_PRESS} / {@link #KIND_SKILL_RELEASE}：支持 {@code Alt + 技能键 1~4} 免翻页直放副栏技能，
 *       以及长按翻页键战术轮盘选定释放任意槽位技能（完整兼容按住蓄力与松开结算）。</li>
 *   <li>{@link #KIND_AIR_ACTION}：同步多段跳（二段/三段跳）与空中按住 Shift 主动滞空状态。</li>
 * </ul>
 */
public final class TacticalSkillMsg {
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

    public static void encode(TacticalSkillMsg msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.kind);
        buf.m_130130_(msg.value);
    }

    public static TacticalSkillMsg decode(FriendlyByteBuf buf) {
        return new TacticalSkillMsg(buf.readByte(), buf.m_130242_());
    }

    public static void handle(TacticalSkillMsg msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
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
        ctx.setPacketHandled(true);
    }
}
