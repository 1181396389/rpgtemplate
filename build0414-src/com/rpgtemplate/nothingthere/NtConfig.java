package com.rpgtemplate.nothingthere;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 一无所有的服务端设置（rpgtemplate-nothing-there-server.toml，位于 serverconfig 目录）。
 * <ul>
 *   <li>non_human_body.speed_scale：非人体质移速加成的总倍率（总面板）。0 = 关闭移速加成。</li>
 *   <li>non_human_body.speed_cap：非人体质移速加成的上限（小数，1.0 = 最多 +100%）。</li>
 * </ul>
 * 注册在模组构造阶段完成（见 NothingThereSetup.onConstruct）。如果配置尚未加载，读取会回退到默认值，不会崩溃。
 */
public final class NtConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SPEED_SCALE;
    public static final ForgeConfigSpec.DoubleValue SPEED_CAP;
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
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
        if (registered) {
            return;
        }
        registered = true;
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, (IConfigSpec) SPEC,
            "rpgtemplate-nothing-there-server.toml");
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
