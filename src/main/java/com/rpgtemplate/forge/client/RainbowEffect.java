package com.rpgtemplate.forge.client;

import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.client.gui.UiPreferences;
import com.rpgtemplate.forge.client.gui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class RainbowEffect {
    private static long until;
    private static int accent;

    private RainbowEffect() {
    }

    public static void clear() {
        until = 0L;
    }

    public static void start(int var0, int var1) {
        Minecraft var2 = Minecraft.getInstance();
        if ((Boolean)ModConfigs.CLIENT.rainbowAura.get() && var2.player != null && var2.level != null) {
            until = System.currentTimeMillis() + 3000L;
            accent = UiTheme.quality(var1);
            var2.level
                .playLocalSound(
                    var2.player.getX(), var2.player.getY(), var2.player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45F, 1.15F, false
                );
        }
    }

    public static void render(GuiGraphics var0) {
        Minecraft var1 = Minecraft.getInstance();
        if (System.currentTimeMillis() < until && var1.screen == null && !var1.options.hideGui) {
            int var2 = Math.min(220, var0.guiWidth() - 24);
            byte var3 = 32;
            int var4 = (var0.guiWidth() - var2) / 2;
            float var5 = (float)(3000L - (until - System.currentTimeMillis())) / 1000.0F;
            int var6 = var0.guiHeight() - 89 + (UiPreferences.reducedMotion ? 0 : (int)((1.0F - UiTheme.ease(var5 / 0.35F)) * 10.0F));
            UiTheme.card(var0, var4, var6, var2, var3, 0.95F);
            UiTheme.border(var0, var4 - 1, var6 - 1, var2 + 2, var3 + 2, UiTheme.alpha(0.35F, accent));
            UiTheme.emblem(var0, var4 + 10, var6 + 8, accent);
            String var7 = Component.translatable("hud.rpgtemplate.ui.reward", new Object[]{ClientKeys.PANEL.getTranslatedKeyMessage()}).getString();
            UiTheme.text(var0, var1.font, UiTheme.fit(var1.font, var7, var2 - 44), var4 + 32, var6 + 8, 15660287);
            UiTheme.bar(var0, var4 + 32, var6 + 23, var2 - 44, 2, (double)(until - System.currentTimeMillis()), 3000.0, 9362676);
        }
    }
}
