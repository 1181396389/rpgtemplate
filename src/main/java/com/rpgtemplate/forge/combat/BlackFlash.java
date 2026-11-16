package com.rpgtemplate.forge.combat;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.skill.Chant;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Vector3f;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class BlackFlash {
    public static final String TYPE = "rpgtemplate:black_flash";
    public static final String KEY_ZONE_UNTIL = "black_flash.zone_until";
    public static final String KEY_STREAK = "black_flash.streak";
    private static final Map<ServerPlayer, Long> READY = new WeakHashMap<>();

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void captureAttack(AttackEntityEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            if (var1.getAttackStrengthScale(0.5F) >= 0.9F) {
                READY.put(var1, var1.level().getGameTime());
            } else {
                READY.remove(var1);
            }
        }
    }

    private BlackFlash() {
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void onMelee(LivingIncomingDamageEvent var0) {
        DamageSource var1 = var0.getSource();
        if (var1.getEntity() instanceof ServerPlayer var2 && var1.getDirectEntity() == var2 && "player".equals(var1.getMsgId())) {
            Long var16 = READY.remove(var2);
            LivingEntity var4 = var0.getEntity();
            if (var4 != var2 && var4.isAlive() && !(var0.getAmount() <= 0.0F)) {
                PlayerSystemData var5 = SystemDataManager.get(var2);
                if (var5.isActivated()) {
                    TraitEntry var6 = TraitLookup.activePassive(var5, "rpgtemplate:black_flash").orElse(null);
                    if (var6 != null) {
                        long var7 = var2.level().getGameTime();
                        boolean var9 = inZone(var5, var7);
                        double var10 = var9 ? TraitLookup.param(var6, "zone_chance", 0.25) : TraitLookup.param(var6, "chance", 0.08);
                        if (var16 != null
                            && var16 == var7
                            && var7 >= (long)var5.value("black_flash.next_trigger", 0.0)
                            && !(var2.getRandom().nextDouble() >= var10)) {
                            var5.setValue("black_flash.next_trigger", (double)var7 + 12.0);
                            int var12 = Math.max(1, (int)TraitLookup.param(var6, "max_streak", 4.0));
                            int var13 = Math.min(var12, var9 ? (int)var5.value("black_flash.streak", 0.0) + 1 : 1);
                            double var14 = Math.min(
                                2.5, TraitLookup.param(var6, "multiplier", 2.0) + TraitLookup.param(var6, "streak_bonus", 0.1) * (double)(var13 - 1)
                            );
                            var0.setAmount((float)((double)var0.getAmount() * Math.max(1.0, var14)));
                            var5.setValue("black_flash.zone_until", (double)(var7 + (long)Math.max(0, (int)TraitLookup.param(var6, "zone_ticks", 300.0))));
                            var5.setValue("black_flash.streak", (double)var13);
                            SystemDataManager.markDirty(var2);
                            present(var2, var4, var6, var13, var12, var14);
                        }
                    }
                }
            }
        }
    }

    private static void present(ServerPlayer var0, LivingEntity var1, TraitEntry var2, int var3, int var4, double var5) {
        ServerLevel var7 = (ServerLevel)var0.level();
        Vec3 var8 = var1.position().add(0.0, (double)(var1.getBbHeight() * 0.6F), 0.0);
        var7.sendParticles(ParticleTypes.FLASH, var8.x, var8.y, var8.z, 1, 0.0, 0.0, 0.0, 0.0);
        var7.sendParticles(ParticleTypes.SQUID_INK, var8.x, var8.y, var8.z, 10, 0.35, 0.35, 0.35, 0.12);
        var7.sendParticles(ParticleTypes.ELECTRIC_SPARK, var8.x, var8.y, var8.z, 6, 0.5, 0.5, 0.5, 0.35);
        var7.sendParticles(ParticleTypes.CRIT, var8.x, var8.y, var8.z, 8, 0.4, 0.4, 0.4, 0.6);

        for (int var9 = 0; var9 < 5; var9++) {
            double var10 = (double)var9 * Math.PI * 2.0 / 5.0;

            for (int var12 = 0; var12 < 8; var12++) {
                double var13 = (double)var12 * 0.13;
                double var15 = var12 % 2 == 0 ? 0.07 : -0.07;
                var7.sendParticles(
                    new DustParticleOptions(new Vector3f(0.75F, 0.02F, 0.04F), 0.85F),
                    var8.x + Math.cos(var10) * var13,
                    var8.y + Math.sin(var10) * var13 + var15,
                    var8.z,
                    1,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                );
            }
        }

        var7.playSound(null, var8.x, var8.y, var8.z, ModSounds.critical(), SoundSource.PLAYERS, 1.0F, 0.75F + 0.08F * (float)var3);
        var7.playSound(null, var8.x, var8.y, var8.z, ModSounds.explode(), SoundSource.PLAYERS, 0.9F, 0.6F);
        var7.playSound(null, var8.x, var8.y, var8.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.45F, 1.7F);
        Vec3 var17 = var1.position().subtract(var0.position()).multiply(1.0, 0.0, 1.0);
        if (var17.lengthSqr() > 1.0E-4) {
            var17 = var17.normalize().scale(0.55);
            var1.push(var17.x, 0.25, var17.z);
            var1.hurtMarked = true;
        }

        String var19 = TraitLookup.paramStr(var2, "chant", "黒閃");
        if (!var19.isEmpty()) {
            Chant.say(var0, var19, Math.max(1, var3), Math.max(1, var4), TraitLookup.param(var2, "audible_radius", 16.0), false);
        }

        Msg.actionBar(
            var0,
            Msg.tr("black_flash.hit", String.format(Locale.ROOT, "%.2f", var5), var3).withStyle(var3 >= var4 ? ChatFormatting.DARK_RED : ChatFormatting.RED)
        );
    }

    public static boolean inZone(PlayerSystemData var0, long var1) {
        return (double)var1 < var0.value("black_flash.zone_until", 0.0);
    }

    public static int zoneLeft(PlayerSystemData var0, long var1) {
        return (int)Math.max(0.0, var0.value("black_flash.zone_until", 0.0) - (double)var1);
    }

    public static int streak(PlayerSystemData var0, long var1) {
        return inZone(var0, var1) ? (int)var0.value("black_flash.streak", 0.0) : 0;
    }

    public static double cooldownScale(PlayerSystemData var0, long var1) {
        if (!inZone(var0, var1)) {
            return 1.0;
        } else {
            TraitEntry var3 = TraitLookup.activePassive(var0, "rpgtemplate:black_flash").orElse(null);
            return var3 == null ? 1.0 : Mth.clamp(TraitLookup.param(var3, "zone_cooldown_scale", 0.7), 0.1, 1.0);
        }
    }

    public static double mpRegenMultiplier(PlayerSystemData var0, long var1) {
        if (!inZone(var0, var1)) {
            return 1.0;
        } else {
            TraitEntry var3 = TraitLookup.activePassive(var0, "rpgtemplate:black_flash").orElse(null);
            return var3 == null ? 1.0 : Math.max(0.0, TraitLookup.param(var3, "zone_mp_regen_multiplier", 2.0));
        }
    }
}
