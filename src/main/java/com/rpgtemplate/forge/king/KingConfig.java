package com.rpgtemplate.forge.king;

import com.rpgtemplate.forge.RpgTemplateMod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;

public final class KingConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final BooleanValue SHOW_ARMS;
    public static final DoubleValue HEIGHT;
    public static final DoubleValue WIDTH;
    private static boolean registered;

    public static synchronized void register() {
        register(RpgTemplateMod.MOD_CONTAINER);
    }

    public static synchronized void register(ModContainer container) {
        if (!registered && container != null) {
            registered = true;
            container.registerConfig(Type.SERVER, SPEC, "rpgtemplate-king-server.toml");
            container.registerConfig(Type.CLIENT, CLIENT_SPEC, "rpgtemplate-king-client.toml");
        }
    }

    static {
        Builder server = new Builder();
        HEIGHT = server.comment("Standing collision height; client receives server dimensions.").defineInRange("perfect_body_height", 2.2, 1.8, 3.0);
        WIDTH = server.defineInRange("perfect_body_width", 0.6, 0.4, 1.2);
        SPEC = server.build();
        Builder client = new Builder();
        SHOW_ARMS = client.define("show_extra_arms", true);
        CLIENT_SPEC = client.build();
    }
}
