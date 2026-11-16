package com.rpgtemplate.forge.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * Styles tooltips shown inside {@link SystemPanelScreen} and {@link HudEditorScreen} to match
 * the v4 dark glass palette (deep indigo background + violet-to-cyan border).
 */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class UiClientEvents {
    private UiClientEvents() {
    }

    @SubscribeEvent
    public static void tooltip(RenderTooltipEvent.Color event) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof SystemPanelScreen || screen instanceof HudEditorScreen) {
            event.setBackground(0xF20C0A24);
            event.setBorderStart(UiTheme.alpha(0.92f, UiTheme.E_VIOLET));
            event.setBorderEnd(UiTheme.alpha(0.92f, UiTheme.E_CYAN));
        }
    }
}
