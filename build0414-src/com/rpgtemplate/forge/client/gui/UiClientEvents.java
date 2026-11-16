package com.rpgtemplate.forge.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Styles tooltips shown inside {@link SystemPanelScreen} and {@link HudEditorScreen} to match
 * the v4 dark glass palette (deep indigo background + violet-to-cyan border).
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class UiClientEvents {
    private UiClientEvents() {
    }

    @SubscribeEvent
    public static void tooltip(RenderTooltipEvent.Color event) {
        Screen screen = Minecraft.m_91087_().f_91080_;
        if (screen instanceof SystemPanelScreen || screen instanceof HudEditorScreen) {
            event.setBackground(0xF20C0A24);
            event.setBorderStart(UiTheme.alpha(0.92f, UiTheme.E_VIOLET));
            event.setBorderEnd(UiTheme.alpha(0.92f, UiTheme.E_CYAN));
        }
    }
}
