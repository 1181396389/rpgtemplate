package com.rpgtemplate.forge.possession.client;
import com.rpgtemplate.forge.RpgTemplateMod;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;


import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.rpgtemplate.core.ui.UiLayout;
import com.rpgtemplate.forge.client.ClientKeys;
import com.rpgtemplate.forge.client.gui.UiPreferences;
import com.rpgtemplate.forge.client.gui.UiTheme;
import com.rpgtemplate.forge.possession.PossessionNet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.neoforged.neoforge.event.entity.player.PlayerEvent.NameFormat;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
    modid = "rpgtemplate",
    value = {Dist.CLIENT}
)
public final class ClientPossession {
    public static KeyMapping VIEW;
    public static KeyMapping CONTRACT;
    public static KeyMapping LEND;
    private static PossessionNet.State state = PossessionNet.State.NONE;
    private static long stateAt;
    private static int contractTotal = 1200;
    private static PossessionNet.Borrowed borrowed = PossessionNet.Borrowed.NONE;
    private static final Map<UUID, PossessionNet.BodyMap.Entry> BODIES = new HashMap<>();
    private static PossessedRenderer wide;
    private static PossessedRenderer slim;
    private static boolean rendering;
    static final PossessionNet.ClientSink SINK = new PossessionNet.ClientSink() {
        @Override
        public void state(PossessionNet.State var1) {
            if (var1.view() == 6 && ClientPossession.state.view() != 6) {
                ClientPossession.contractTotal = Math.max(1, var1.contractLeft());
            }

            ClientPossession.state = var1;
            ClientPossession.stateAt = System.currentTimeMillis();
            if (var1.view() != 4) {
                ClientPossession.borrowed = PossessionNet.Borrowed.NONE;
            }
        }

        @Override
        public void bodies(PossessionNet.BodyMap var1) {
            HashMap var2 = new HashMap<>(ClientPossession.BODIES);
            ClientPossession.BODIES.clear();

            for (PossessionNet.BodyMap.Entry var4 : var1.entries()) {
                ClientPossession.BODIES.put(var4.controller(), var4);
            }

            Minecraft var6 = Minecraft.getInstance();
            if (var6.level != null) {
                for (Player var5 : var6.level.players()) {
                    if (var2.containsKey(var5.getUUID()) || ClientPossession.BODIES.containsKey(var5.getUUID())) {
                        var5.refreshDisplayName();
                    }
                }
            }
        }

        @Override
        public void borrowed(PossessionNet.Borrowed var1) {
            ClientPossession.borrowed = var1;
        }
    };

    private ClientPossession() {
    }

    public static PossessionNet.State state() {
        return state;
    }

    public static boolean borrowing() {
        return borrowed.active() && state.view() == 4 && state.lending() && state.hostBorrow();
    }

    public static int borrowedCount() {
        return borrowed.skills().size();
    }

    public static PossessionNet.Borrowed borrowed() {
        return borrowed;
    }

    public static ResourceLocation hostSkin(UUID var0) {
        PossessionNet.BodyMap.Entry var1 = BODIES.get(var0);
        if (var1 == null) {
            return null;
        } else {
            PlayerInfo var2 = info(var1.host());
            return var2 != null ? var2.getSkin().texture() : DefaultPlayerSkin.get(var1.host()).texture();
        }
    }

    static boolean hostSlim(UUID var0) {
        PlayerInfo var1 = info(var0);
        PlayerSkin.Model var2 = var1 != null ? var1.getSkin().model() : DefaultPlayerSkin.get(var0).model();
        return var2 == PlayerSkin.Model.SLIM;
    }

    static PlayerInfo info(UUID var0) {
        ClientPacketListener var1 = Minecraft.getInstance().getConnection();
        return var1 == null ? null : var1.getPlayerInfo(var0);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post var0) {
        if (true) {
            Minecraft var1 = Minecraft.getInstance();
            if (var1.player != null && var1.getConnection() != null) {
                int var2 = state.view();
                if (var2 == 2 && var1.screen == null) {
                    while (var1.options.keyJump.consumeClick()) {
                        PossessionNet.sendToServer(new PossessionNet.SoulAction(0));
                    }

                    while (var1.options.keyAttack.consumeClick()) {
                        PossessionNet.sendToServer(new PossessionNet.SoulAction(0));
                    }
                }

                if (VIEW != null) {
                    while (VIEW.consumeClick()) {
                        if (var1.screen == null) {
                            if (var2 == 3 && Screen.hasControlDown()) {
                                PossessionNet.sendToServer(new PossessionNet.SoulAction(3));
                            } else if ((var2 != 3 || !Screen.hasShiftDown()) && var2 != 6) {
                                if (var2 == 3) {
                                    PossessionNet.sendToServer(new PossessionNet.SoulAction(1));
                                } else if (var2 == 4) {
                                    PossessionNet.sendToServer(new PossessionNet.SoulAction(3));
                                }
                            } else {
                                PossessionNet.sendToServer(new PossessionNet.SoulAction(2));
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void onRenderPlayer(Pre var0) {
        Player var1;
        if (!rendering && (var1 = var0.getEntity()) instanceof AbstractClientPlayer) {
            AbstractClientPlayer var2 = (AbstractClientPlayer)var1;
            PossessionNet.BodyMap.Entry var3 = BODIES.get(var2.getUUID());
            if (var3 != null) {
                PossessedRenderer var4 = hostSlim(var3.host()) ? slim : wide;
                if (var4 != null) {
                    var0.setCanceled(true);
                    rendering = true;

                    try {
                        float var5 = var0.getPartialTick();
                        float var6 = Mth.lerp(var5, var2.yRotO, var2.getYRot());
                        var4.render(var2, var6, var5, var0.getPoseStack(), var0.getMultiBufferSource(), var0.getPackedLight());
                    } finally {
                        rendering = false;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent var0) {
        AbstractClientPlayer var1 = var0.getPlayer();
        PossessionNet.BodyMap.Entry var2 = BODIES.get(var1.getUUID());
        if (var2 != null) {
            PossessedRenderer var3 = hostSlim(var2.host()) ? slim : wide;
            if (var3 != null) {
                var0.setCanceled(true);
                var3.renderArm(var0.getPoseStack(), var0.getMultiBufferSource(), var0.getPackedLight(), var1, var0.getArm());
            }
        }
    }

    @SubscribeEvent
    public static void onName(NameFormat var0) {
        Player var1 = var0.getEntity();
        if (var1.level().isClientSide) {
            PossessionNet.BodyMap.Entry var2 = BODIES.get(var1.getUUID());
            if (var2 != null) {
                var0.setDisplayname(Component.literal(var2.hostName()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(LoggingOut var0) {
        state = PossessionNet.State.NONE;
        borrowed = PossessionNet.Borrowed.NONE;
        BODIES.clear();
    }

    static String key(KeyMapping var0) {
        return var0 == null ? "?" : var0.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
    }

    static String secs(int var0) {
        return String.format(Locale.ROOT, "%.1f", (double)var0 / 20.0);
    }

    static void renderHud(GuiGraphics var0, int var1, int var2) {
        Minecraft var3 = Minecraft.getInstance();
        if (!var3.options.hideGui && var3.player != null && var3.screen == null) {
            Font var4 = var3.font;
            PossessionNet.State var5 = state;
            int var6 = (int)Math.min(10L, Math.max(0L, (System.currentTimeMillis() - stateAt) / 50L));
            if (var5.view() != 0) {
                if (var5.view() == 2) {
                    renderBattle(var0, var4, var5, var1, var2, var6);
                } else {
                    float var7 = Math.min(1.0F, ((float)var1 - 24.0F) / 440.0F);
                    var7 = Math.max(0.3F, var7);
                    short var8 = 240;
                    int var9 = var5.view() == 3 ? 104 : (var5.view() == 6 ? 69 : (var5.view() == 1 ? 69 : 50));
                    int var10 = var1 - (int)Math.ceil((double)((float)var8 * var7)) - 8;
                    byte var11 = 8;
                    var0.pose().pushPose();
                    var0.pose().translate((float)var10, (float)var11, 220.0F);
                    var0.pose().scale(var7, var7, 1.0F);
                    UiTheme.card(var0, 0, 0, var8, var9, 0.88F);
                    UiTheme.emblem(var0, 10, 9, var5.view() == 6 ? 16746392 : 9362676);
                    UiTheme.text(var0, var4, Component.translatable("hud.rpgtemplate.ui.soul").getString(), 31, 9, 9362676);
                    UiTheme.right(var0, var4, String.format(Locale.ROOT, "%02d", var5.view()), 230, 9, 7045535);
                    int var12 = 29;
                    switch (var5.view()) {
                        case 1:
                            row(var0, var4, Component.translatable("hud.rpgtemplate.possession.finger").getString(), 10, var12, 220, 15660287);
                            String var13 = Component.translatable(
                                    var5.fingerLost() ? "hud.rpgtemplate.possession.finger_lost" : "hud.rpgtemplate.possession.finger_hint"
                                )
                                .getString();
                            var12 += 13;
                            row(var0, var4, var13, 10, var12, 220, 10532045);
                        case 2:
                        default:
                            break;
                        case 3:
                            row(
                                var0,
                                var4,
                                Component.translatable("hud.rpgtemplate.possession.trapped", new Object[]{var5.other()}).getString(),
                                10,
                                var12,
                                220,
                                15660287
                            );
                            String var14 = Component.translatable(
                                    var5.watch() ? "hud.rpgtemplate.possession.view_watch" : "hud.rpgtemplate.possession.view_realm",
                                    new Object[]{var5.other()}
                                )
                                .getString();
                            var12 += 14;
                            row(var0, var4, var14, 10, var12, 220, 10532045);
                            var14 = key(VIEW);
                            var12 += 17;
                            action(var0, var4, var14, "hud.rpgtemplate.possession.key_view", var12, 10532045);
                            int var15 = Math.max(0, var5.contractCd() - var6);
                            String var16 = Component.translatable("hud.rpgtemplate.possession.key_contract").getString()
                                + (var15 > 0 ? " · " + secs(var15) + "s" : "");
                            var14 = "Shift+" + key(VIEW) + "  /  " + var16;
                            var12 += 14;
                            row(var0, var4, var14, 10, var12, 220, var15 > 0 ? 7045535 : 9362676);
                            var14 = "Ctrl+" + key(VIEW);
                            String var17 = var5.lending() ? "hud.rpgtemplate.possession.key_lend_on" : "hud.rpgtemplate.possession.key_lend_off";
                            var12 += 14;
                            action(var0, var4, var14, var17, var12, var5.lending() ? 9099960 : 10532045);
                            break;
                        case 4:
                            row(
                                var0,
                                var4,
                                Component.translatable("hud.rpgtemplate.possession.host", new Object[]{var5.other()}).getString(),
                                10,
                                var12,
                                220,
                                15660287
                            );
                            if (var5.lending()) {
                                action(
                                    var0,
                                    var4,
                                    key(VIEW),
                                    var5.hostBorrow() ? "hud.rpgtemplate.possession.host_borrow_on" : "hud.rpgtemplate.possession.host_borrow_off",
                                    var12 + 13,
                                    9362676
                                );
                            }
                            break;
                        case 5:
                            row(
                                var0,
                                var4,
                                Component.translatable("hud.rpgtemplate.possession.possessing", new Object[]{var5.other()}).getString(),
                                10,
                                var12,
                                220,
                                15660287
                            );
                            break;
                        case 6:
                            int var18 = Math.max(0, var5.contractLeft() - var6);
                            row(
                                var0,
                                var4,
                                Component.translatable("hud.rpgtemplate.possession.contract", new Object[]{secs(var18)}).getString(),
                                10,
                                var12,
                                220,
                                16746392
                            );
                            row(
                                var0,
                                var4,
                                Component.translatable("hud.rpgtemplate.possession.contract_hint", new Object[]{key(VIEW)}).getString(),
                                10,
                                var12 + 13,
                                220,
                                10532045
                            );
                            UiTheme.bar(var0, 10, 58, 220, 3, (double)var18, (double)contractTotal, 16746392);
                            break;
                        case 7:
                            row(
                                var0,
                                var4,
                                Component.translatable("hud.rpgtemplate.possession.host_trapped", new Object[]{var5.other()}).getString(),
                                10,
                                var12,
                                220,
                                10532045
                            );
                    }

                    var0.pose().popPose();
                    if (var5.view() == 4 && borrowing()) {
                        renderBorrowed(var0, var4, var1, var2);
                    }
                }
            }
        }
    }

    static void renderBattle(GuiGraphics var0, Font var1, PossessionNet.State var2, int var3, int var4, int var5) {
        int var6 = Math.min(354, var3 - 24);
        int var7 = Math.min(132, var4 - 24);
        int var8 = (var3 - var6) / 2;
        int var9 = (var4 - var7) / 2;
        UiTheme.card(var0, var8, var9, var6, var7, 0.95F);
        UiTheme.emblem(var0, var8 + 13, var9 + 13, 9362676);
        row(
            var0,
            var1,
            Component.translatable("hud.rpgtemplate.possession.battle", new Object[]{secs(Math.max(0, var2.battleLeft() - var5))}).getString(),
            var8 + 35,
            var9 + 14,
            var6 - 50,
            15660287
        );
        String var10 = Component.translatable("hud.rpgtemplate.ui." + (var2.resonance() ? "resonance" : "contest")).getString();
        row(var0, var1, var10, var8 + 14, var9 + 33, var6 - 28, var2.resonance() ? 9362676 : 10532045);
        int var11 = var8 + 14;
        int var12 = var9 + 52;
        int var13 = var6 - 28;
        var0.fill(var11, var12, var11 + var13, var12 + 8, -920070);
        int var14 = var11 + (int)((float)var13 * Math.max(0.0F, Math.min(1.0F, (var2.progress() + 100.0F) / 200.0F)));
        var0.fill(var11, var12, var14, var12 + 8, UiTheme.alpha(0.85F, 9362676));
        var0.fill(var14, var12, var11 + var13, var12 + 8, UiTheme.alpha(0.85F, 16746392));
        var0.fill(var14 - 1, var12 - 3, var14 + 1, var12 + 11, -15788246);
        var0.fill(var11 + var13 / 2, var12 - 2, var11 + var13 / 2 + 1, var12 + 10, -1156638934);
        String var15 = Minecraft.getInstance().player.getGameProfile().getName();
        String var16 = Component.translatable("hud.rpgtemplate.possession.side_host", new Object[]{var2.sukunaSide() ? var2.other() : var15}).getString();
        String var17 = Component.translatable("hud.rpgtemplate.possession.side_sukuna", new Object[]{var2.sukunaSide() ? var15 : var2.other()}).getString();
        row(var0, var1, var16, var11, var12 + 17, var13 / 2 - 5, 9362676);
        String var18 = UiTheme.fit(var1, var17, var13 / 2 - 5);
        UiTheme.right(var0, var1, var18, var11 + var13, var12 + 17, 16746392);
        Minecraft var19 = Minecraft.getInstance();
        MutableComponent var20 = Component.translatable(
            var2.resonance() ? "hud.rpgtemplate.possession.resonance" : "hud.rpgtemplate.possession.mash",
            new Object[]{var19.options.keyJump.getTranslatedKeyMessage(), var19.options.keyAttack.getTranslatedKeyMessage()}
        );
        row(var0, var1, var20.getString(), var8 + 14, var9 + var7 - 33, var6 - 28, var2.resonance() ? 9362676 : 15660287);
        row(
            var0,
            var1,
            Component.translatable(var2.sukunaSide() ? "hud.rpgtemplate.possession.goal_sukuna" : "hud.rpgtemplate.possession.goal_host").getString(),
            var8 + 14,
            var9 + var7 - 18,
            var6 - 28,
            10532045
        );
    }

    static void renderBorrowed(GuiGraphics var0, Font var1, int var2, int var3) {
        PossessionNet.Borrowed var4 = borrowed;
        List var5 = var4.skills();
        int var6 = ClientKeys.page();
        int var7 = var6 * 4;
        int var8 = Math.max(0, Math.min(4, var5.size() - var7));
        short var9 = 220;
        int var10 = 58 + var8 * 22;
        float var11 = Math.min(1.0F, ((float)var2 - 24.0F) / 440.0F);
        int var12 = UiPreferences.dockX < 0 ? var2 - (int)((float)var9 * var11) - 8 : UiPreferences.dockX;
        int var13 = UiPreferences.dockY < 0 ? var3 - (int)((float)var10 * var11) - 48 : UiPreferences.dockY;
        var12 = UiLayout.anchor(var12, (int)Math.ceil((double)((float)var9 * var11)), var2);
        var13 = UiLayout.anchor(var13, (int)Math.ceil((double)((float)var10 * var11)), var3);
        var0.pose().pushPose();
        var0.pose().translate((float)var12, (float)var13, 220.0F);
        var0.pose().scale(var11, var11, 1.0F);
        UiTheme.card(var0, 0, 0, var9, var10, 0.88F);
        row(var0, var1, Component.translatable("hud.rpgtemplate.possession.borrowed", new Object[]{var4.lender()}).getString(), 10, 9, 200, 9362676);
        row(var0, var1, String.format(Locale.ROOT, "MP %.0f / %.0f", var4.mp(), var4.maxMp()), 10, 23, 200, 7588341);
        UiTheme.bar(var0, 10, 36, 200, 3, (double)var4.mp(), (double)var4.maxMp(), 7588341);
        row(var0, var1, ClientKeys.pageMark(var6) + " / " + Math.max(1, (var5.size() + 3) / 4) + "    " + ClientKeys.pageKeyLabel(), 10, 45, 200, 10532045);
        byte var14 = 60;

        for (int var15 = 0; var15 < var8; var15++) {
            PossessionNet.Borrowed.Skill var16 = (PossessionNet.Borrowed.Skill)var5.get(var7 + var15);
            String var17 = UiTheme.fit(var1, ClientKeys.physicalKeyLabel(var15), 26);
            UiTheme.key(var0, var1, var17, 10, var14);
            String var18 = var16.cdLeft() > 0 ? secs(var16.cdLeft()) + "s" : Component.translatable("hud.rpgtemplate.ui.ready").getString();
            row(var0, var1, var16.name(), 48, var14 + 2, 152 - var1.width(var18) - 7, var16.usable() ? 15660287 : 7045535);
            UiTheme.right(var0, var1, var18, 210, var14 + 2, var16.cdLeft() > 0 ? 9362676 : 9099960);
            var14 += 22;
        }

        var0.pose().popPose();
    }

    static void row(GuiGraphics var0, Font var1, String var2, int var3, int var4, int var5, int var6) {
        UiTheme.text(var0, var1, UiTheme.fit(var1, var2, var5), var3, var4, var6);
    }

    static void action(GuiGraphics var0, Font var1, String var2, String var3, int var4, int var5) {
        row(var0, var1, var2 + "  /  " + Component.translatable(var3).getString(), 10, var4, 220, var5);
    }

    static void panel(GuiGraphics var0, int var1, int var2, int var3, int var4) {
        UiTheme.card(var0, var1, var2, var3, var4, 0.88F);
    }

    static void center(GuiGraphics var0, Font var1, Component var2, int var3, int var4, int var5) {
        var0.drawString(var1, var2, (var3 - var1.width(var2)) / 2, var4, var5);
    }

    @EventBusSubscriber(
        modid = "rpgtemplate",
        value = {Dist.CLIENT},
        bus = Bus.MOD
    )
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent var0) {
            PossessionNet.sink = ClientPossession.SINK;
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent var0) {
            ClientPossession.CONTRACT = ClientPossession.VIEW = new KeyMapping(
                "key.rpgtemplate.soul_view", KeyConflictContext.IN_GAME, Type.KEYSYM, 66, "key.categories.rpgtemplate"
            );
            ClientPossession.LEND = ClientPossession.VIEW;
            var0.register(ClientPossession.VIEW);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiLayersEvent var0) {
            var0.registerAboveAll(RpgTemplateMod.id("possession"), (gui, dt) -> ClientPossession.renderHud(gui, gui.guiWidth(), gui.guiHeight()));
        }

        @SubscribeEvent
        public static void onLayers(AddLayers var0) {
            Context var1 = var0.getContext();
            ClientPossession.wide = new PossessedRenderer(var1, false);
            ClientPossession.slim = new PossessedRenderer(var1, true);
        }
    }
}
