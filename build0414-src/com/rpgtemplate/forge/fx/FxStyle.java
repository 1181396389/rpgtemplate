package com.rpgtemplate.forge.fx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 把原版粒子类型映射为非粒子图元的样式（形状、颜色、尺寸、寿命、扩散）。
 * 供服务端 sendParticles 拦截与客户端 addParticle 拦截共用，不引用任何客户端类。
 */
public final class FxStyle {
    public final int shape;
    public final int rgb;
    public final float size;
    public final int life;
    public final float spread;

    private FxStyle(int shape, int rgb, float size, int life, float spread) {
        this.shape = shape;
        this.rgb = rgb;
        this.size = size;
        this.life = life;
        this.spread = spread;
    }

    public static FxStyle of(ParticleOptions options) {
        String path = "";
        try {
            var key = ForgeRegistries.PARTICLE_TYPES.getKey(options.m_6012_());
            if (key != null) {
                path = key.m_135815_();
            }
        } catch (RuntimeException ignored) {
            // 未注册的粒子类型：按默认样式处理
        }
        return switch (path) {
            case "dust" -> dust(options);
            case "flame", "small_flame" -> new FxStyle(Fx.SPARK, 0xFF8A2A, 0.14f, 16, 0.6f);
            case "lava" -> new FxStyle(Fx.SPARK, 0xFF6A1A, 0.14f, 18, 0.6f);
            case "soul_fire_flame", "soul" -> new FxStyle(Fx.SPARK, 0x3FD4FF, 0.16f, 18, 0.6f);
            case "crit" -> new FxStyle(Fx.SPARK, 0xFFE7A8, 0.18f, 14, 0.6f);
            case "enchanted_hit", "enchant" -> new FxStyle(Fx.SPARK, 0xC58BFF, 0.14f, 22, 1.0f);
            case "electric_spark" -> new FxStyle(Fx.SPARK, 0x7FE8FF, 0.14f, 12, 0.7f);
            case "end_rod" -> new FxStyle(Fx.SPARK, 0xFFFFFF, 0.12f, 16, 0.5f);
            case "portal", "reverse_portal", "witch" -> new FxStyle(Fx.SPARK, 0xB36BFF, 0.14f, 22, 1.4f);
            case "happy_villager" -> new FxStyle(Fx.SPARK, 0x5BFF6A, 0.12f, 18, 0.5f);
            case "angry_villager" -> new FxStyle(Fx.SPARK, 0xFF5555, 0.12f, 18, 0.5f);
            case "heart" -> new FxStyle(Fx.SPARK, 0xFF6B9A, 0.14f, 22, 0.5f);
            case "note" -> new FxStyle(Fx.SPARK, 0xFFD966, 0.12f, 22, 0.5f);
            case "sculk_soul", "sculk_charge_pop" -> new FxStyle(Fx.SPARK, 0x2FD6C8, 0.14f, 18, 0.6f);
            case "bubble", "bubble_pop", "underwater" -> new FxStyle(Fx.SPARK, 0x9FE6FF, 0.12f, 20, 0.4f);
            case "rain", "splash" -> new FxStyle(Fx.SPARK, 0x7FC8FF, 0.10f, 12, 0.3f);
            case "snowflake" -> new FxStyle(Fx.SPARK, 0xFFFFFF, 0.12f, 24, 0.4f);
            case "smoke", "large_smoke", "campfire_cosy_smoke", "campfire_signal_smoke", "cloud", "poof" ->
                    new FxStyle(Fx.PUFF, 0x8C8C8C, 0.7f, 36, 1.2f);
            case "dragon_breath" -> new FxStyle(Fx.PUFF, 0xB36BFF, 0.5f, 30, 0.8f);
            case "explosion", "explosion_emitter" -> new FxStyle(Fx.SPHERE, 0xFFB347, 0.22f, 12, 1.6f);
            case "sweep_attack" -> new FxStyle(Fx.RING, 0xFFFFFF, 0.08f, 10, 1.2f);
            case "sonic_boom" -> new FxStyle(Fx.RING, 0x7FE8FF, 0.12f, 12, 2.0f);
            default -> new FxStyle(Fx.SPARK, 0xFFFFFF, 0.12f, 14, 0.6f);
        };
    }

    /** DustParticleOptions 的 writeToString 格式为 "type r g b scale"（r,g,b 为 0..1 浮点）。 */
    private static FxStyle dust(ParticleOptions options) {
        int rgb = 0xFFFFFF;
        float scale = 1f;
        try {
            String[] parts = options.m_5942_().trim().split("\\s+");
            if (parts.length >= 5) {
                int r = clamp255(Float.parseFloat(parts[1]));
                int g = clamp255(Float.parseFloat(parts[2]));
                int b = clamp255(Float.parseFloat(parts[3]));
                rgb = (r << 16) | (g << 8) | b;
                scale = Float.parseFloat(parts[4]);
            }
        } catch (RuntimeException ignored) {
            // 解析失败：保持白色
        }
        return new FxStyle(Fx.SPARK, rgb, Math.max(0.06f, Math.min(0.3f, 0.12f * scale)), 18, 0.6f);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}
