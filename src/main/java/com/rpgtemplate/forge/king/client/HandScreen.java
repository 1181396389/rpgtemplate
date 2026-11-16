package com.rpgtemplate.forge.king.client;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.client.ClientSystemData;
import com.rpgtemplate.forge.client.gui.UiTheme;
import com.rpgtemplate.forge.king.KingConfig;
import com.rpgtemplate.forge.king.KingNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class HandScreen extends Screen {
    private final Screen parent;
    private final List<HandScreen.Hit> hits = new ArrayList<>();
    private int x;
    private int y;
    private int w;
    private int h;
    private float scroll;
    private float maxScroll;
    private final long openedNanos = System.nanoTime();

    public HandScreen(Screen var1) {
        super(Component.literal("完美之躯 · 独立手槽"));
        this.parent = var1;
    }

    protected void init() {
        this.w = Math.min(420, this.width - 16);
        this.h = Math.min(330, this.height - 16);
        this.x = (this.width - this.w) / 2;
        this.y = (this.height - this.h) / 2;
        KingNetwork.send(5, 0, true);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private void text(GuiGraphics var1, String var2, int var3, int var4, int var5) {
        var1.drawString(this.font, var2, var3, var4, var5);
    }

    private int lines(GuiGraphics var1, String var2, int var3, int var4) {
        for (FormattedCharSequence var6 : this.font.split(Component.literal(var2), this.w - 32)) {
            var1.drawString(this.font, var6, this.x + 16, var3, var4);
            var3 += 11;
        }

        return var3;
    }

    private void button(GuiGraphics var1, int var2, int var3, int var4, int var5, int var6, String var7, Runnable var8) {
        boolean var9 = var2 >= var4 && var2 < var4 + var6 && var3 >= var5 && var3 < var5 + 22;
        UiTheme.buttonSurface(var1, var4, var5, var6, 22, true, var9, 9362676);
        this.text(var1, var7, var4 + 7, var5 + 7, -15788246);
        this.hits.add(new HandScreen.Hit(var4, var5, var6, 22, var8));
    }

    public void render(GuiGraphics var1, int var2, int var3, float var4) {
        UiTheme.enterPush(var1, UiTheme.enter(this.openedNanos), 10.0F);
        this.renderContent(var1, var2, var3, var4);
        UiTheme.enterPop(var1);
    }

    private void renderContent(GuiGraphics var1, int var2, int var3, float var4) {
        this.hits.clear();
        UiTheme.backdrop(var1, this.width, this.height);
        UiTheme.card(var1, this.x, this.y, this.w, this.h, 0.96F);
        this.text(var1, "完美之躯  /  独立手槽", this.x + 16, this.y + 13, -16553567);
        KingNetwork.State var5 = KingClient.local();
        boolean var6 = var5 != null && var5.active();
        var1.enableScissor(this.x + 4, this.y + 34, this.x + this.w - 4, this.y + this.h - 34);
        int var7 = this.y + 40 - (int)this.scroll;
        var7 = this.lines(var1, var6 ? "点击手槽：与当前主手交换。选中后，额外攻击和使用键操作该手。" : "未载入二十指：手槽只可取回物品，不能放入或使用。", var7, -12102295) + 10;

        for (int var8 = 0; var8 < 2; var8++) {
            int var9 = var8;
            ItemStack var10 = var5 == null ? ItemStack.EMPTY : (var8 == 0 ? var5.third() : var5.fourth());
            UiTheme.card(var1, this.x + 16, var7, this.w - 32, 47, 0.85F);
            var1.renderItem(var10, this.x + 25, var7 + 16);
            var1.renderItemDecorations(this.font, var10, this.x + 25, var7 + 16);
            this.text(var1, (var8 == 0 ? "第三手" : "第四手") + (var5 != null && var5.selected() == var8 ? " · 已选中" : ""), this.x + 50, var7 + 8, -16553567);
            String var11 = var10.isEmpty() ? "空手" : var10.getHoverName().getString();
            this.text(var1, this.font.plainSubstrByWidth(var11, Math.max(10, this.w - 180)), this.x + 50, var7 + 25, -15788246);
            this.button(var1, var2, var3, this.x + this.w - 100, var7 + 13, 80, var6 ? "交换主手" : "取回", () -> KingNetwork.send(0, var9, true));
            var7 += 55;
        }

        PlayerSystemData var21 = ClientSystemData.get();
        long var22 = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();

        for (String var14 : new String[]{"king.amp", "king.basket"}) {
            double var15 = var21.value(var14, 0.0);
            String var17 = var14.equals("king.amp") ? "领域展延" : "弥虚葛笼";
            var7 = this.lines(
                    var1,
                    var17
                        + "："
                        + (
                            var15 > (double)var22
                                ? String.format(Locale.ROOT, "%.1f 秒 / %.1f%% 最大MP每秒", (var15 - (double)var22) / 20.0, var14.equals("king.amp") ? 1.5 : 2.0)
                                : "未开启（技能第三页）"
                        ),
                    var7,
                    -8635667
                )
                + 3;
        }

        var7 = this.lines(
            var1,
            "控制：选手 "
                + KingClient.SELECT.getTranslatedKeyMessage().getString()
                + " · 攻击/挖掘 "
                + KingClient.ATTACK.getTranslatedKeyMessage().getString()
                + " · 使用 "
                + KingClient.USE.getTranslatedKeyMessage().getString()
                + "。可在按键设置中修改。",
            var7 + 6,
            -12102295
        );
        var7 = this.lines(var1, "持续物品使用共用一个原版状态；使用期间主手临时托管，结束后归还。两手共享近战攻击恢复。", var7 + 6, -12102295);
        this.maxScroll = (float)Math.max(0, var7 + (int)this.scroll - (this.y + this.h - 40));
        var1.disableScissor();
        this.hits.removeIf(var1x -> var1x.y < this.y + 34 || var1x.y + var1x.h > this.y + this.h - 34);
        this.button(var1, var2, var3, this.x + 14, this.y + this.h - 28, (this.w - 40) / 2, KingClient.showArms ? "四臂显示：开" : "四臂显示：关", () -> {
            KingClient.showArms = !KingClient.showArms;
            KingConfig.SHOW_ARMS.set(KingClient.showArms);
            KingConfig.SHOW_ARMS.save();
        });
        this.button(var1, var2, var3, this.x + 26 + (this.w - 40) / 2, this.y + this.h - 28, (this.w - 40) / 2, "返回", this::onClose);
    }

    public boolean mouseClicked(double var1, double var3, int var5) {
        UiTheme.pulse(var1, var3);
        if (var5 == 0) {
            for (HandScreen.Hit var7 : this.hits) {
                if (var1 >= (double)var7.x && var1 < (double)(var7.x + var7.w) && var3 >= (double)var7.y && var3 < (double)(var7.y + var7.h)) {
                    var7.run.run();
                    return true;
                }
            }
        }

        return super.mouseClicked(var1, var3, var5);
    }

    public boolean mouseScrolled(double var1, double var3, double var5) {
        this.scroll = (float)Math.max(0.0, Math.min((double)this.maxScroll, (double)this.scroll - var5 * 22.0));
        return true;
    }

    private static record Hit(int x, int y, int w, int h, Runnable run) {
    }
}
