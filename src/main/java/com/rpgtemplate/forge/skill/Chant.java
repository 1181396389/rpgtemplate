package com.rpgtemplate.forge.skill;

import com.rpgtemplate.forge.network.ChantPayload;
import com.rpgtemplate.forge.network.PacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class Chant {
    private Chant() {
    }

    public static void say(ServerPlayer var0, String var1, int var2, int var3, double var4) {
        say(var0, var1, var2, var3, var4, true);
    }

    public static void say(ServerPlayer var0, String var1, int var2, int var3, double var4, boolean var6) {
        if (var1 != null && !var1.isBlank()) {
            var0.sendSystemMessage(Component.literal("「" + var1 + "」").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}));
            var0.serverLevel()
                .sendParticles(ParticleTypes.CRIT, var0.getX(), var0.getY() + (double)var0.getBbHeight() * 0.5, var0.getZ(), 2, 0.3, 0.7, 0.3, 0.0);
            if (var4 > 0.0) {
                MutableComponent var7 = Component.literal(var0.getGameProfile().getName() + "：「" + var1 + "」")
                    .withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.ITALIC});
                double var8 = var4 * var4;

                for (ServerPlayer var11 : var0.serverLevel().players()) {
                    if (var11 != var0 && var11.distanceToSqr(var0) <= var8) {
                        var11.sendSystemMessage(var7);
                    }
                }
            }
        }
    }

    public static void clear(ServerPlayer var0) {
        PacketDistributor.sendToPlayer(var0, new ChantPayload("", 0, 0, true));
    }
}
