package com.rpgtemplate.forge.king.client;
import net.minecraft.client.resources.PlayerSkin;


import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.rpgtemplate.forge.king.KingConfig;
import com.rpgtemplate.forge.king.KingNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
    modid = "rpgtemplate",
    value = {Dist.CLIENT},
    bus = Bus.MOD
)
public final class KingClient {
    public static final Map<UUID, KingNetwork.State> STATES = new HashMap<>();
    public static final KeyMapping SELECT = key("select", 71);
    public static final KeyMapping ATTACK = key("attack", 89);
    public static final KeyMapping USE = key("use", 72);
    public static final KeyMapping SCREEN = key("screen", 85);
    public static boolean showArms = true;
    private static boolean attackDown;
    private static boolean useDown;
    private static boolean useBridge;
    private static int selected;
    private static Object world;

    private static KeyMapping key(String var0, int var1) {
        return new KeyMapping("key.rpgtemplate.hand." + var0, KeyConflictContext.IN_GAME, Type.KEYSYM, var1, "key.categories.rpgtemplate");
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent var0) {
        for (KeyMapping var4 : new KeyMapping[]{SELECT, ATTACK, USE, SCREEN}) {
            var0.register(var4);
        }

        KingNetwork.CLIENT = KingClient::accept;
    }

    @SubscribeEvent
    public static void layers(AddLayers var0) {
        for (PlayerSkin.Model var2 : var0.getSkins()) {
            PlayerRenderer var3 = (PlayerRenderer)var0.getSkin(var2);
            if (var3 != null) {
                var3.addLayer(new FourArmLayer(var3, var0.getContext().getItemInHandRenderer()));
            }
        }
    }

    public static KingNetwork.State local() {
        Minecraft var0 = Minecraft.getInstance();
        return var0.player == null ? null : STATES.get(var0.player.getUUID());
    }

    public static void accept(KingNetwork.State var0) {
        if (STATES.size() > 256) {
            STATES.clear();
        }

        STATES.put(var0.id(), var0);
        Minecraft var1 = Minecraft.getInstance();
        if (var1.level != null) {
            Player var2 = var1.level.getPlayerByUUID(var0.id());
            if (var2 != null) {
                apply(var2, var0);
            }

            if (var1.player != null && var0.id().equals(var1.player.getUUID())) {
                selected = Math.min(1, var0.selected());
            }
        }
    }

    private static void apply(Player var0, KingNetwork.State var1) {
        boolean var2 = var0.getPersistentData().getBoolean("king.form") != var1.active()
            || var0.getPersistentData().getFloat("king.height") != var1.height()
            || var0.getPersistentData().getFloat("king.width") != var1.width();
        var0.getPersistentData().putBoolean("king.form", var1.active());
        var0.getPersistentData().putFloat("king.height", var1.height());
        var0.getPersistentData().putFloat("king.width", var1.width());
        if (var2) {
            var0.refreshDimensions();
        }
    }

    private static void bridgeUse() {
        Minecraft var0 = Minecraft.getInstance();
        boolean var1 = var0.player != null && var0.screen == null && USE.isDown() && local() != null && local().active();
        if (var1) {
            var0.options.keyUse.setDown(true);
            useBridge = true;
        } else if (useBridge) {
            Key var2 = var0.options.keyUse.getKey();
            long var3 = var0.getWindow().getWindow();
            int var5 = var2.getValue();
            boolean var6 = var5 >= 0 && (var2.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(var3, var5) == 1 : GLFW.glfwGetKey(var3, var5) == 1);
            var0.options.keyUse.setDown(var6);
            useBridge = false;
        }
    }

    @EventBusSubscriber(
        modid = "rpgtemplate",
        value = {Dist.CLIENT}
    )
    public static final class Events {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post var0) {
            if (true) {
                KingClient.bridgeUse();
            } else {
                KingClient.showArms = (Boolean)KingConfig.SHOW_ARMS.get();
                Minecraft var1 = Minecraft.getInstance();
                if (KingClient.world != var1.level) {
                    KingClient.world = var1.level;
                    KingClient.STATES.clear();
                    KingClient.attackDown = false;
                    KingClient.useDown = false;
                }

                boolean var2 = var1.player != null && var1.getConnection() != null;
                if (var2) {
                    KingNetwork.State var3 = KingClient.local();
                    if (var3 != null) {
                        KingClient.apply(var1.player, var3);
                    }
                }

                while (KingClient.SELECT.consumeClick()) {
                    if (var2 && var1.screen == null) {
                        KingClient.selected = 1 - KingClient.selected;
                        KingNetwork.send(4, KingClient.selected, true);
                    }
                }

                while (KingClient.SCREEN.consumeClick()) {
                    if (var2 && var1.screen == null) {
                        var1.setScreen(new HandScreen(null));
                    }
                }

                boolean var5 = var2 && var1.screen == null && KingClient.ATTACK.isDown();
                boolean var4 = var2 && var1.screen == null && KingClient.USE.isDown();
                if (var5 != KingClient.attackDown) {
                    KingClient.attackDown = var5;
                    if (var2) {
                        KingNetwork.send(2, KingClient.selected, var5);
                    }
                }

                if (var4 != KingClient.useDown) {
                    KingClient.useDown = var4;
                    if (var2) {
                        KingNetwork.send(3, KingClient.selected, var4);
                    }
                }

                while (KingClient.ATTACK.consumeClick()) {
                }

                while (KingClient.USE.consumeClick()) {
                }
            }
        }

        @SubscribeEvent
        public static void vanillaUse(InteractionKeyMappingTriggered var0) {
            if (KingClient.useBridge && var0.isUseItem()) {
                var0.setCanceled(true);
                var0.setSwingHand(false);
            }
        }

        @SubscribeEvent
        public static void join(EntityJoinLevelEvent var0) {
            if (var0.getEntity() instanceof Player var1) {
                KingNetwork.State var3 = KingClient.STATES.get(var1.getUUID());
                if (var3 != null) {
                    KingClient.apply(var1, var3);
                }
            }
        }

        @SubscribeEvent
        public static void leave(LoggingOut var0) {
            KingClient.STATES.clear();
            KingClient.useDown = false;
            KingClient.attackDown = false;
            KingClient.world = null;
        }
    }
}
