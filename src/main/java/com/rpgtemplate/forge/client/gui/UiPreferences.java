package com.rpgtemplate.forge.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.RpgTemplateMod;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.neoforged.fml.loading.FMLPaths;

public final class UiPreferences {
    public static boolean reducedMotion = false;
    public static boolean hudDetails = false;
    public static int dockX = -1;
    public static int dockY = -1;
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("rpgtemplate-ui.json");

    private UiPreferences() {
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject var0 = new JsonObject();
            var0.addProperty("reduced_motion", reducedMotion);
            var0.addProperty("hud_details", hudDetails);
            var0.addProperty("dock_x", dockX);
            var0.addProperty("dock_y", dockY);
            Path var1 = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(var1, var0.toString() + "\n", StandardCharsets.UTF_8);

            try {
                Files.move(var1, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException var3) {
                Files.move(var1, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception var4) {
            RpgTemplateMod.LOGGER.warn("[RPG UI] Unable to save preferences", var4);
        }
    }

    public static void applyPreset() {
        reducedMotion = false;
        hudDetails = false;
        dockY = -1;
        dockX = -1;
        save();
        ModConfigs.CLIENT.hudEnabled.set(true);
        ModConfigs.CLIENT.hudFlicker.set(0.0);
        ModConfigs.CLIENT.hudGlitch.set(false);
        ModConfigs.CLIENT.hudOpacity.set(0.85);
        ModConfigs.CLIENT.hudScale.set(1.0);
        ModConfigs.CLIENT.hudX.set(8);
        ModConfigs.CLIENT.hudY.set(8);
        ModConfigs.CLIENT.hudEnabled.save();
    }

    static {
        try {
            if (Files.exists(FILE)) {
                JsonObject var0 = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                if (var0.has("reduced_motion")) {
                    reducedMotion = var0.get("reduced_motion").getAsBoolean();
                }

                if (var0.has("hud_details")) {
                    hudDetails = var0.get("hud_details").getAsBoolean();
                }

                if (var0.has("dock_x")) {
                    dockX = Math.max(-1, var0.get("dock_x").getAsInt());
                }

                if (var0.has("dock_y")) {
                    dockY = Math.max(-1, var0.get("dock_y").getAsInt());
                }
            }
        } catch (Exception var1) {
            RpgTemplateMod.LOGGER.warn("[RPG UI] Unable to read preferences; safe defaults retained", var1);
        }
    }
}
