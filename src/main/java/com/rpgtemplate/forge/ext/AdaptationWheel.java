package com.rpgtemplate.forge.ext;


import net.neoforged.fml.common.EventBusSubscriber;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.rpgtemplate.core.combat.AdaptationModel;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.WorldSlashDamage;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Vector3f;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class AdaptationWheel {
    public static final String TYPE = "rpgtemplate:adaptation_wheel";
    public static final String[] CATEGORIES = new String[]{
        "technique", "explosion", "fire", "projectile", "fall", "soul", "magic", "melee", "environment", "other"
    };
    private static final Map<UUID, AdaptationModel> MODELS = new HashMap<>();
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(0.95F, 0.78F, 0.32F), 1.2F);

    private AdaptationWheel() {
    }

    public static String category(DamageSource var0) {
        if (var0 != null
            && !WorldSlashDamage.is(var0)
            && !var0.is(DamageTypes.FELL_OUT_OF_WORLD)
            && !var0.is(DamageTypes.GENERIC_KILL)
            && !ModDamageTypes.bypassesSystemHp(var0)) {
            String var1 = var0.getMsgId();
            if (var1 != null && var1.startsWith("rpgtemplate.")) {
                return "technique";
            } else if (var0.is(DamageTypeTags.IS_EXPLOSION)) {
                return "explosion";
            } else if (var0.is(DamageTypeTags.IS_FIRE)) {
                return "fire";
            } else if (var0.is(DamageTypeTags.IS_PROJECTILE)) {
                return "projectile";
            } else if (var0.is(DamageTypeTags.IS_FALL)) {
                return "fall";
            } else if (ModDamageTypes.isSoulDamage(var0)) {
                return "soul";
            } else if (!var0.is(DamageTypes.MAGIC)
                && !var0.is(DamageTypes.INDIRECT_MAGIC)
                && !var0.is(DamageTypes.WITHER)
                && !var0.is(DamageTypes.DRAGON_BREATH)
                && !var0.is(DamageTypes.SONIC_BOOM)
                && !var0.is(DamageTypes.THORNS)) {
                Entity var2 = var0.getDirectEntity();
                if (var2 instanceof LivingEntity && var2 == var0.getEntity()) {
                    return "melee";
                } else {
                    return var0.getEntity() == null ? "environment" : "other";
                }
            } else {
                return "magic";
            }
        } else {
            return null;
        }
    }

    public static int allowedTurns(TraitEntry var0, int var1, int var2) {
        JsonElement var3 = var0.params() == null ? null : var0.params().get("turn_unlock_levels");
        if (var3 != null && var3.isJsonArray()) {
            JsonArray var4 = var3.getAsJsonArray();
            int var5 = 0;

            for (int var6 = 0; var6 < var4.size() && var6 < var2; var6++) {
                try {
                    if (var1 < var4.get(var6).getAsInt()) {
                        break;
                    }

                    var5 = var6 + 1;
                } catch (RuntimeException var8) {
                    break;
                }
            }

            return var5;
        } else {
            return var2;
        }
    }

    public static AdaptationModel.Params params(TraitEntry var0) {
        return new AdaptationModel.Params(
            TraitLookup.param(var0, "hits_per_turn", 4.0),
            TraitLookup.param(var0, "reduction_per_turn", 0.125),
            (int)TraitLookup.param(var0, "max_turns", 4.0),
            Math.round(TraitLookup.param(var0, "decay_seconds", 15.0) * 20.0),
            TraitLookup.param(var0, "heavy_weight", 2.0)
        );
    }

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void onHurt(LivingIncomingDamageEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && !(var0.getAmount() <= 0.0F)) {
            PlayerSystemData var18 = SystemDataManager.get(var1);
            TraitEntry var3 = var18.isActivated() ? TraitLookup.activePassive(var18, "rpgtemplate:adaptation_wheel").orElse(null) : null;
            if (var3 == null) {
                MODELS.remove(var1.getUUID());
                return;
            }

            String var4 = category(var0.getSource());
            if (var4 == null) {
                return;
            }

            AdaptationModel.Params var5 = params(var3);
            int var6 = var18.loadedTemplate().map(var0x -> var0x.level()).orElse(1);
            int var7 = allowedTurns(var3, var6, var5.maxTurns());
            double var8 = StatCalculator.maxStat(StatType.HP, var18, TemplateRegistry.get(), CoreConfig.get());
            double var10 = (Double)ModConfigs.SERVER.damageConversion.get();
            boolean var12 = var8 > 0.0 && (double)var0.getAmount() * var10 >= var8 * TraitLookup.param(var3, "heavy_hit_ratio", 0.15);
            long var13 = var1.serverLevel().getGameTime();
            AdaptationModel var15 = MODELS.computeIfAbsent(var1.getUUID(), var0x -> new AdaptationModel());
            AdaptationModel.Result var16 = var15.hit(var4, var13, var12, var5, var7);
            if (var16.multiplier() < 1.0) {
                var0.setAmount((float)((double)var0.getAmount() * var16.multiplier()));
            }

            if (var16.turned()) {
                turnFx(var1, var16.turnsAfter());
                if (TraitLookup.paramBool(var3, "show_message", true)) {
                    String var17 = String.format(Locale.ROOT, "%.0f", (1.0 - var5.multiplier(var16.turnsAfter())) * 100.0);
                    Msg.actionBar(
                        var1,
                        Msg.tr("passive.adaptation.turn", Msg.tr("passive.adaptation.cat." + var4), var16.turnsAfter(), var7, var17)
                            .withStyle(ChatFormatting.GOLD)
                    );
                }
            }

            return;
        }
    }

    private static void turnFx(ServerPlayer var0, int var1) {
        ServerLevel var2 = var0.serverLevel();
        double var3 = var0.getX();
        double var5 = var0.getY() + (double)var0.getBbHeight() + 0.9;
        double var7 = var0.getZ();
        double var9 = 0.9;
        double var11 = (double)var1 * Math.PI / 8.0;

        for (int var13 = 0; var13 < 32; var13++) {
            double var14 = var11 + (double)var13 * Math.PI * 2.0 / 32.0;
            var2.sendParticles(GOLD, var3 + Math.cos(var14) * var9, var5, var7 + Math.sin(var14) * var9, 1, 0.0, 0.0, 0.0, 0.0);
        }

        for (int var19 = 0; var19 < 8; var19++) {
            double var20 = var11 + (double)var19 * Math.PI / 4.0;

            for (int var16 = 1; var16 <= 3; var16++) {
                double var17 = var9 * (double)var16 / 3.0;
                var2.sendParticles(GOLD, var3 + Math.cos(var20) * var17, var5, var7 + Math.sin(var20) * var17, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        var2.playSound(null, var3, var5, var7, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8F, 0.7F + 0.1F * (float)var1);
        var2.playSound(null, var3, var5, var7, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    public static void forget(UUID var0) {
        MODELS.remove(var0);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            forget(var1.getUUID());
        }
    }

    @SubscribeEvent
    public static void onClone(Clone var0) {
        forget(var0.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent var0) {
        forget(var0.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent var0) {
        MODELS.clear();
        WorldRiftEntity.clearActive();
    }
}
