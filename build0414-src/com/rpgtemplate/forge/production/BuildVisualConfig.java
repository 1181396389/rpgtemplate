package com.rpgtemplate.forge.production;
import net.minecraftforge.common.ForgeConfigSpec;import net.minecraftforge.fml.ModLoadingContext;import net.minecraftforge.fml.config.ModConfig;
public final class BuildVisualConfig {
 public enum Quality {OFF,LOW,HIGH,ULTRA}
 public static final ForgeConfigSpec SPEC;public static final ForgeConfigSpec.EnumValue<Quality> QUALITY;public static final ForgeConfigSpec.BooleanValue MOTION,REFRACTION,ADAPTIVE;public static final ForgeConfigSpec.IntValue LIMIT,SEGMENTS;
 static {var b=new ForgeConfigSpec.Builder();QUALITY=b.comment("Visual detail only; never changes server damage, radius or targets.").defineEnum("quality",Quality.ULTRA);MOTION=b.define("fullBodyMotion",true);REFRACTION=b.comment("Native scene-color refraction. Turn off first if another renderer conflicts.").define("sceneRefraction",true);ADAPTIVE=b.define("adaptiveVisualLOD",true);LIMIT=b.defineInRange("maxVisibleEffects",16,1,48);SEGMENTS=b.defineInRange("ultraRingSegments",192,32,512);SPEC=b.build();}
 public static void register(){ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,SPEC,"rpgtemplate-build-visuals.toml");}
}
