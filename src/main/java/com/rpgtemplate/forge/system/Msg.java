package com.rpgtemplate.forge.system;

import com.rpgtemplate.core.template.Quality;
import com.rpgtemplate.core.template.TemplateDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

public final class Msg {
    private Msg() {
    }

    public static MutableComponent tr(String var0, Object... var1) {
        return Component.translatable("message.rpgtemplate." + var0, var1);
    }

    public static MutableComponent template(TemplateDefinition var0) {
        Quality var1 = var0.quality();
        String var2 = var1 == Quality.BASE ? "" : "［" + var1.name() + "］";
        return Component.literal(var2 + var0.displayName()).withStyle(var1x -> var1x.withColor(TextColor.fromRgb(var1.color() & 16777215)));
    }

    public static MutableComponent templateId(String var0) {
        return Component.literal(var0).withStyle(ChatFormatting.GRAY);
    }

    public static void chat(ServerPlayer var0, Component var1) {
        var0.sendSystemMessage(var1);
    }

    public static void actionBar(ServerPlayer var0, Component var1) {
        var0.displayClientMessage(var1, true);
    }

    public static MutableComponent system(Component var0) {
        return Component.literal("【万象模板】").withStyle(ChatFormatting.AQUA).append(var0);
    }
}
