package com.rpgtemplate.forge.client.gui;

import com.rpgtemplate.core.ui.UiLayout;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.client.hud.SystemHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private int dragging = -1;
    private double offsetX;
    private double offsetY;
    private final long openedNanos = System.nanoTime();

    public HudEditorScreen(Screen var1) {
        super(Component.translatable("gui.rpgtemplate.ui.edit_hud"));
        this.parent = var1;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void render(GuiGraphics var1, int var2, int var3, float var4) {
        UiTheme.enterPush(var1, UiTheme.enter(this.openedNanos), 10.0F);
        this.renderContent(var1, var2, var3, var4);
        UiTheme.enterPop(var1);
    }

    private void renderContent(GuiGraphics var1, int var2, int var3, float var4) {
        var1.fill(0, 0, this.width, this.height, 1698719133);

        for (byte var5 = 12; var5 < this.width; var5 += 24) {
            var1.fill(var5, 0, var5 + 1, this.height, UiTheme.alpha(0.055F, 9362676));
        }

        for (byte var9 = 12; var9 < this.height; var9 += 24) {
            var1.fill(0, var9, this.width, var9 + 1, UiTheme.alpha(0.055F, 9362676));
        }

        SystemHud.renderForEditor(var1, var4);

        for (int var10 = 0; var10 < 2; var10++) {
            UiLayout.Rect var6 = var10 == 0 ? SystemHud.resourceBounds() : SystemHud.skillBounds();
            if (var6 != null) {
                UiTheme.border(
                    var1,
                    var6.x() - 2,
                    var6.y() - 2,
                    var6.w() + 4,
                    var6.h() + 4,
                    UiTheme.alpha(1.0F, this.dragging != var10 && !var6.contains((double)var2, (double)var3) ? 3492456 : 9362676)
                );
            }
        }

        if (this.dragging >= 0) {
            UiLayout.Rect var11 = this.dragging == 0 ? SystemHud.resourceBounds() : SystemHud.skillBounds();
            if (var11 != null) {
                var1.fill(var11.x(), 0, var11.x() + 1, this.height, UiTheme.alpha(0.25F, 9362676));
                var1.fill(0, var11.y(), this.width, var11.y() + 1, UiTheme.alpha(0.25F, 9362676));
            }
        }

        String var12 = Component.translatable("gui.rpgtemplate.ui.editor_hint").getString();
        int var13 = Math.min(this.width - 16, this.font.width(var12) + 24);
        int var7 = (this.width - var13) / 2;
        int var8 = this.height - 25;
        UiTheme.card(var1, var7, var8, var13, 18, 0.95F);
        UiTheme.text(var1, this.font, UiTheme.fit(this.font, var12, var13 - 16), var7 + 8, var8 + 5, 9362676);
    }

    public boolean mouseClicked(double var1, double var3, int var5) {
        UiTheme.pulse(var1, var3);
        if (var5 == 0) {
            for (int var6 = 1; var6 >= 0; var6--) {
                UiLayout.Rect var7 = var6 == 0 ? SystemHud.resourceBounds() : SystemHud.skillBounds();
                if (var7 != null && var7.contains(var1, var3)) {
                    this.dragging = var6;
                    this.offsetX = var1 - (double)var7.x();
                    this.offsetY = var3 - (double)var7.y();
                    return true;
                }
            }
        }

        return super.mouseClicked(var1, var3, var5);
    }

    public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
        if (this.dragging >= 0) {
            UiLayout.Rect var10 = this.dragging == 0 ? SystemHud.resourceBounds() : SystemHud.skillBounds();
            int var11 = UiLayout.anchor((int)(var1 - this.offsetX), var10.w(), this.width);
            int var12 = UiLayout.anchor((int)(var3 - this.offsetY), var10.h(), this.height);
            if (this.dragging == 0) {
                ModConfigs.CLIENT.hudX.set(var11);
                ModConfigs.CLIENT.hudY.set(var12);
            } else {
                UiPreferences.dockX = var11;
                UiPreferences.dockY = var12;
            }

            return true;
        } else {
            return super.mouseDragged(var1, var3, var5, var6, var8);
        }
    }

    public boolean mouseReleased(double var1, double var3, int var5) {
        if (this.dragging >= 0) {
            this.dragging = -1;
            this.save();
            return true;
        } else {
            return super.mouseReleased(var1, var3, var5);
        }
    }

    public boolean mouseScrolled(double var1, double var3, double var5) {
        double var7 = (double)Math.round(Mth.clamp((Double)ModConfigs.CLIENT.hudScale.get() + var5 * 0.1, 0.5, 3.0) * 10.0) / 10.0;
        ModConfigs.CLIENT.hudScale.set(var7);
        ModConfigs.CLIENT.hudScale.save();
        return true;
    }

    public boolean keyPressed(int var1, int var2, int var3) {
        if (var1 == 82) {
            ModConfigs.CLIENT.hudX.set(8);
            ModConfigs.CLIENT.hudY.set(8);
            ModConfigs.CLIENT.hudScale.set(1.0);
            UiPreferences.dockY = -1;
            UiPreferences.dockX = -1;
            this.save();
            return true;
        } else {
            return super.keyPressed(var1, var2, var3);
        }
    }

    private void save() {
        ModConfigs.CLIENT.hudX.save();
        UiPreferences.save();
    }

    public void onClose() {
        this.save();
        this.minecraft.setScreen(this.parent);
    }
}
