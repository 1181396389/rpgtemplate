package com.rpgtemplate.forge.ext;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class SukunaPassives {
    public static final String MULTI_JUMP = "rpgtemplate:multi_jump";
    public static final String CURSED_SENSE = "rpgtemplate:cursed_sense";
    public static final String UNDYING = "rpgtemplate:undying_body";
    public static final String FLAME = "rpgtemplate:flame_affinity";
    public static final String SLASH = "rpgtemplate:slash_affinity";
    public static final String KEY_UNDYING_CD = "passive.undying_until";
    private static final Map<UUID, Integer> AIR_JUMPS = new ConcurrentHashMap<>();

    private SukunaPassives() {
    }

    public static int maxJumps(TraitEntry var0, int var1) {
        double var2 = TraitLookup.param(var0, "base_jumps", 5.0);
        double var4 = TraitLookup.param(var0, "levels_per_jump", 0.0);
        double var6 = TraitLookup.param(var0, "max_jumps", 10.0);
        double var8 = var2 + (var4 > 0.0 ? Math.floor((double)var1 / var4) : 0.0);
        return (int)Math.max(1.0, Math.min(var6, var8));
    }

    public static int templateLevel(PlayerSystemData var0) {
        return var0.loadedTemplate().map(var0x -> var0x.level()).orElse(0);
    }

    private static Optional<TraitEntry> passive(ServerPlayer var0, String var1) {
        if (!SystemDataManager.available(var0)) {
            return Optional.empty();
        } else {
            PlayerSystemData var2 = SystemDataManager.get(var0);
            return !var2.isActivated() ? Optional.empty() : TraitLookup.activePassive(var2, var1);
        }
    }

    public static int airJumpsUsed(ServerPlayer var0) {
        return AIR_JUMPS.getOrDefault(var0.getUUID(), 0);
    }

    public static boolean onAirJump(ServerPlayer var0, int var1) {
        Optional var2 = passive(var0, "rpgtemplate:multi_jump");
        if (!var2.isEmpty() && !var0.onGround() && !var0.isSpectator()) {
            int var3 = maxJumps((TraitEntry)var2.get(), templateLevel(SystemDataManager.get(var0)));
            int var4 = AIR_JUMPS.getOrDefault(var0.getUUID(), 0);
            if (var4 + 1 > var3 - 1) {
                return false;
            } else {
                AIR_JUMPS.put(var0.getUUID(), var4 + 1);
                var0.fallDistance = 0.0F;
                ServerLevel var5 = var0.serverLevel();
                var5.sendParticles(ParticleTypes.CLOUD, var0.getX(), var0.getY(), var0.getZ(), 10, 0.35, 0.05, 0.35, 0.02);
                var5.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var0.getX(), var0.getY(), var0.getZ(), 6, 0.3, 0.05, 0.3, 0.01);
                var5.playSound(var0, var0.getX(), var0.getY(), var0.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6F, 1.6F);
                return true;
            }
        } else {
            return false;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            if (var1.onGround() || var1.isInWater() || var1.onClimbable() || var1.getAbilities().flying) {
                AIR_JUMPS.remove(var1.getUUID());
            }

            if (var1.tickCount % 20 == 0 && var1.isOnFire() && passive(var1, "rpgtemplate:flame_affinity").isPresent()) {
                var1.clearFire();
            }

            Optional var8 = passive(var1, "rpgtemplate:cursed_sense");
            if (var8.isPresent()) {
                int var3 = (int)Math.max(10.0, TraitLookup.param((TraitEntry)var8.get(), "interval_ticks", 40.0));
                if (var1.tickCount % var3 == 0) {
                    double var4 = TraitLookup.param((TraitEntry)var8.get(), "radius", 24.0);

                    for (LivingEntity var7 : var1.serverLevel()
                        .getEntitiesOfClass(
                            LivingEntity.class,
                            var1.getBoundingBox().inflate(var4),
                            var3x -> var3x instanceof Enemy && var3x.isAlive() && var3x.distanceToSqr(var1.getX(), var1.getY(), var1.getZ()) <= var4 * var4
                        )) {
                        var7.addEffect(new MobEffectInstance(MobEffects.GLOWING, var3 + 20, 0, true, false));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent var0) {
        AIR_JUMPS.remove(var0.getEntity().getUUID());
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void onAttack(LivingIncomingDamageEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1
            && var0.getSource().is(DamageTypeTags.IS_FIRE)
            && passive(var1, "rpgtemplate:flame_affinity").isPresent()) {
            var0.setCanceled(true);
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void onHurt(LivingIncomingDamageEvent var0) {
        DamageSource var1 = var0.getSource();
        if (var1.getEntity() instanceof ServerPlayer var2 && var1.getDirectEntity() == var2 && "player".equals(var1.getMsgId())) {
            Optional var12 = passive(var2, "rpgtemplate:slash_affinity");
            if (var12.isEmpty()) {
                return;
            }

            double var4 = TraitLookup.param((TraitEntry)var12.get(), "melee_bonus", 0.15);
            var0.setAmount((float)((double)var0.getAmount() * (1.0 + var4)));
            LivingEntity var6 = var0.getEntity();
            SkillContext var7 = DomainExpansionSkill.skillContext(var2, "rpgtemplate:dismantle");
            float var8 = var7 == null ? 0.0F : var7.damage(TraitLookup.param((TraitEntry)var12.get(), "slash_coefficient_scale", 0.3));
            if (var8 > 0.0F && TargetUtil.valid(var2, var6)) {
                SkillManager.schedule(var2, 1, () -> {
                    if (var2.isAlive() && var6.isAlive() && var2.level() == var6.level() && TargetUtil.valid(var2, var6)) {
                        TargetUtil.hurt(var6, ModDamageTypes.technique(var2), var8);
                    }
                });
            }

            Vec3 var9 = TargetUtil.center(var6);

            try {
                Effects.slash(
                    var2, var9, var2.getYRot() + 90.0F, (var2.getRandom().nextFloat() - 0.5F) * 50.0F, var2.getRandom().nextFloat() * 180.0F, 2.2F, 0.22F, 0, 8
                );
            } catch (Throwable var11) {
            }

            var2.serverLevel().playSound(null, var9.x, var9.y, var9.z, ModSounds.slash(), SoundSource.PLAYERS, 0.5F, 1.4F);
            return;
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void onDeath(LivingDeathEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && !var0.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            Optional var10 = passive(var1, "rpgtemplate:undying_body");
            if (var10.isEmpty()) {
                return;
            }

            PlayerSystemData var3 = SystemDataManager.get(var1);
            long var4 = var1.serverLevel().getGameTime();
            if (var3.value("passive.undying_until", 0.0) > (double)var4) {
                return;
            }

            double var6 = TraitLookup.param((TraitEntry)var10.get(), "heal_ratio", 0.3);
            int var8 = (int)(TraitLookup.param((TraitEntry)var10.get(), "cooldown_seconds", 300.0) * 20.0);
            var0.setCanceled(true);
            var1.setHealth((float)Math.max(1.0, (double)var1.getMaxHealth() * var6));
            var3.setHp(Math.max(var3.hp(), StatCalculator.maxStat(StatType.HP, var3, TemplateRegistry.get(), CoreConfig.get()) * var6));
            var3.setValue("passive.undying_until", (double)(var4 + (long)var8));
            SystemDataManager.markDirty(var1);
            SystemDataManager.sync(var1);
            var1.clearFire();
            var1.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            var1.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
            ServerLevel var9 = var1.serverLevel();
            var9.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, var1.getX(), var1.getY() + 1.0, var1.getZ(), 60, 0.5, 0.8, 0.5, 0.4);
            var9.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var1.getX(), var1.getY() + 1.0, var1.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
            var9.playSound(null, var1.getX(), var1.getY(), var1.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
            Msg.chat(var1, Msg.system(Msg.tr("passive.undying.triggered", var8 / 20).withStyle(ChatFormatting.DARK_RED)));
            return;
        }
    }

    public static void resetAirJumps(ServerPlayer var0) {
        AIR_JUMPS.remove(var0.getUUID());
    }
}
