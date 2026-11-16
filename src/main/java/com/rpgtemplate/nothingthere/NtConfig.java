package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.RpgTemplateMod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NtConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SPEED_SCALE;
    public static final ModConfigSpec.DoubleValue SPEED_CAP;
    private static boolean registered;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("一无所有：非人体质（总面板）").push("non_human_body");
        SPEED_SCALE = builder
            .comment("移速加成总倍率。1.0 = 按被动数值计算；0 = 关闭移速加成；可调高或调低。")
            .defineInRange("speed_scale", 1.0, 0.0, 5.0);
        SPEED_CAP = builder
            .comment("移速加成上限（小数，1.0 = 最多 +100% 移速）。")
            .defineInRange("speed_cap", 1.0, 0.0, 10.0);
        builder.pop();
        SPEC = builder.build();
    }

    private NtConfig() {
    }

    public static synchronized void register() {
        register(RpgTemplateMod.MOD_CONTAINER);
    }

    public static synchronized void register(ModContainer container) {
        if (registered || container == null) {
            return;
        }
        registered = true;
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "rpgtemplate-nothing-there-server.toml");
    }

    static double speedScale() {
        try {
            return SPEED_SCALE.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    static double speedCap() {
        try {
            return SPEED_CAP.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }
}
