package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.ext.ShrineEntity;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DomainExpansionSkill implements Skill {
    public static final String ID = "rpgtemplate:domain_expansion";
    public static final String KEY_UNTIL = "domain.until";
    public static final String KEY_X = "domain.x";
    public static final String KEY_Y = "domain.y";
    public static final String KEY_Z = "domain.z";
    public static final String KEY_RADIUS = "domain.radius";
    public static final String KEY_SLOT = "domain.slot";
    public static final String KEY_STARTED = "domain.started";
    public static final String KEY_NEXT_TICK = "domain.next_tick";
    public static final String KEY_NEXT_CLEAVE = "domain.next_cleave";
    public static final String KEY_LAST_DRAIN = "domain.last_drain";
    public static final String KEY_RESIZED = "domain.resized";
    public static final String KEY_HEIGHT = "domain.height";
    public static final String KEY_BURNOUT_UNTIL = "burnout_until";
    public static final String KEY_PREF_RADIUS = "setting.domain_radius";
    private static final int CRIMSON = 11010048;
    private static final int MODE_CAST = 1;
    private static final Map<UUID, ShrineEntity> SHRINES = new ConcurrentHashMap<>();

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public boolean bypassCooldown(SkillContext var1) {
        return active(var1.data, var1.now);
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        if (active(var1.data, var1.now)) {
            KingRules.domainRefund(var1);
            end(var3, "recalled");
            var2.finish();
        } else {
            var2.mode = 1;
            var2.stage = 0;
            var2.lastPulse = var1.now;
            var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), (SoundEvent)ModSounds.JJC_WIND_CHIME.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
            double var4 = preferredRadius(var1);
            Msg.actionBar(var3, Msg.tr("skill.domain.casting", fmt(var4)).withStyle(ChatFormatting.DARK_PURPLE));
            this.castTick(var1, var2);
        }
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        if (var2.mode == 1) {
            this.castTick(var1, var2);
        }
    }

    @Override
    public void onRelease(SkillContext var1, HeldState var2) {
        if (var2.mode == 1 && !var2.finished()) {
            Chant.clear(var1.player);
            Msg.actionBar(var1.player, Msg.tr("skill.domain.cancelled").withStyle(ChatFormatting.GRAY));
        }
    }

    private void castTick(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        ServerLevel var4 = var3.serverLevel();
        int var5 = Math.max(2, var1.integer("cast_ticks", 40));
        int var6 = var2.heldTicks(var1.now);
        if (var1.heian() == null) {
            var3.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 5, false, false));
        }

        List var7 = chant(var1);
        int var8 = Math.min(var7.size(), 1 + var6 * var7.size() / var5);

        while (var2.stage < var8) {
            var2.stage++;
            Chant.say(var3, (String)var7.get(var2.stage - 1), var2.stage, var7.size(), var1.num("audible_radius", 48.0), true);
            Effects.chargeAura(var3, var2.stage, var5 / Math.max(1, var7.size()) + 6);
        }

        Vec3 var9 = var3.getEyePosition().add(var3.getLookAngle().scale(0.6)).subtract(0.0, 0.4, 0.0);
        var4.sendParticles(ParticleTypes.CRIMSON_SPORE, var9.x, var9.y, var9.z, 4, 0.2, 0.2, 0.2, 0.0);
        var4.sendParticles(ParticleTypes.SMOKE, var9.x, var9.y, var9.z, 2, 0.15, 0.15, 0.15, 0.0);
        double var10 = 1.0 + 4.0 * (double)var6 / (double)var5;

        for (int var12 = 0; var12 < 6; var12++) {
            double var13 = (double)var6 * 0.35 + (double)var12 / 6.0 * Math.PI * 2.0;
            var4.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                var3.getX() + Math.cos(var13) * var10,
                var3.getY() + 0.1,
                var3.getZ() + Math.sin(var13) * var10,
                1,
                0.0,
                0.0,
                0.0,
                0.0
            );
        }

        if (var6 >= var5) {
            if (var1.consume(var1.cost() * costScale(var1, preferredRadius(var1)))) {
                this.expand(var1);
            } else {
                Chant.clear(var3);
            }

            var2.finish();
        }
    }

    public static double minRadius(SkillContext var0) {
        return Math.max(2.0, var0.num("resize.min_radius", 6.0));
    }

    public static double maxRadius(SkillContext var0) {
        return Math.max(minRadius(var0), var0.num("resize.max_radius", 200.0));
    }

    public static double preferredRadius(SkillContext var0) {
        double var1 = var0.num("radius", 20.0);
        double var3 = var0.data.value("setting.domain_radius", var1);
        return Math.max(minRadius(var0), Math.min(maxRadius(var0), var3));
    }

    public static double planeHeight(double var0) {
        return Math.max(8.0, Math.min(40.0, var0 * 0.25));
    }

    public static void adjustRadius(ServerPlayer var0, int var1, boolean var2) {
        if (SystemDataManager.available(var0)) {
            SkillContext var3 = skillContext(var0, "rpgtemplate:domain_expansion");
            if (var3 == null) {
                Msg.actionBar(var0, Msg.tr("skill.domain.radius_none").withStyle(ChatFormatting.GRAY));
            } else {
                PlayerSystemData var4 = var3.data;
                double var5 = Math.max(1.0, var2 ? var3.num("resize.step_big", 20.0) : var3.num("resize.step", 5.0));
                double var7 = active(var4, var3.now) ? var4.value("domain.radius", preferredRadius(var3)) : preferredRadius(var3);
                double var9 = Math.max(minRadius(var3), Math.min(maxRadius(var3), var7 + (double)Math.signum((float)var1) * var5));
                var4.setValue("setting.domain_radius", var9);
                if (active(var4, var3.now)) {
                    var4.setValue("domain.radius", var9);
                    var4.setValue("domain.height", planeHeight(var9));
                    var4.setValue("domain.resized", 1.0);
                    ShrineEntity var11 = SHRINES.get(var0.getUUID());
                    if (var11 != null && !var11.isRemoved()) {
                        var0.serverLevel()
                            .sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var11.getX(), var11.getY() + 6.0, var11.getZ(), 30, 2.0, 2.0, 2.0, 0.05);
                    }
                }

                SystemDataManager.markDirty(var0);
                var0.level()
                    .playSound(
                        null, var0.getX(), var0.getY(), var0.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, var9 < var7 ? 1.4F : 0.8F
                    );
                Msg.actionBar(
                    var0,
                    Msg.tr(
                            active(var4, var3.now) ? "skill.domain.resize" : "skill.domain.radius_pref",
                            fmt(var9),
                            fmt(damageScale(var3, var9)),
                            fmt(costScale(var3, var9))
                        )
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                );
            }
        }
    }

    static double floorY(ServerLevel var0, double var1, double var3, double var5) {
        MutableBlockPos var7 = new MutableBlockPos();
        int var8 = (int)Math.floor(var1);
        int var9 = (int)Math.floor(var5);
        int var10 = (int)Math.floor(var3 + 0.01);

        for (int var11 = 0; var11 <= 12; var11++) {
            var7.set(var8, var10 - var11 - 1, var9);
            if (!var0.hasChunkAt(var7) || var0.isOutsideBuildHeight(var7)) {
                break;
            }

            if (!var0.getBlockState(var7).getCollisionShape(var0, var7).isEmpty()) {
                return (double)(var10 - var11);
            }
        }

        return Math.floor(var3);
    }

    private void expand(SkillContext var1) {
        ServerPlayer var2 = var1.player;
        PlayerSystemData var3 = var1.data;
        ServerLevel var4 = var2.serverLevel();
        double var5 = preferredRadius(var1);
        int var7 = Math.max(20, var1.integer("duration_ticks", 300));
        Vec3 var8 = new Vec3(var2.getX(), floorY(var4, var2.getX(), var2.getY(), var2.getZ()), var2.getZ());
        var3.setValue("domain.until", (double)(var1.now + (long)var7));
        var3.setValue("domain.x", var8.x);
        var3.setValue("domain.y", var8.y);
        var3.setValue("domain.z", var8.z);
        var3.setValue("domain.radius", var5);
        var3.setValue("domain.height", planeHeight(var5));
        var3.setValue("domain.slot", (double)var1.slot);
        var3.setValue("domain.started", (double)var1.now);
        var3.setValue("king.domain_cost", var1.cost());
        var3.setValue("king.embers", 0.0);
        var3.setValue("domain.next_tick", (double)(var1.now + 5L));
        var3.setValue("domain.next_cleave", (double)(var1.now + 20L));
        var3.setValue("domain.last_drain", (double)var1.now);
        var1.cooldown();
        SystemDataManager.markDirty(var2);
        var2.swing(InteractionHand.MAIN_HAND, true);
        var4.playSound(null, var8.x, var8.y, var8.z, (SoundEvent)ModSounds.JJC_SLOW_MOTION_END.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
        var4.playSound(null, var8.x, var8.y, var8.z, (SoundEvent)ModSounds.JJC_PIANO_HORROR.get(), SoundSource.PLAYERS, 3.0F, 0.75F);
        double var9 = Math.min(var5, 64.0);

        for (int var11 = 0; var11 < 24; var11++) {
            double var12 = (double)var11 / 24.0 * Math.PI * 2.0;
            Effects.beam(var2, var8.add(0.0, 1.0, 0.0), var8.add(Math.cos(var12) * var9, 1.0, Math.sin(var12) * var9), 11010048);
        }

        var4.sendParticles(ParticleTypes.EXPLOSION_EMITTER, var8.x, var8.y + 1.0, var8.z, 1, 0.0, 0.0, 0.0, 0.0);
        var4.sendParticles(ParticleTypes.CRIMSON_SPORE, var8.x, var8.y + 1.0, var8.z, 200, var9 * 0.5, 2.0, var9 * 0.5, 0.0);
        spawnShrine(var1, var8);

        for (int var14 = 1; var14 <= 8; var14++) {
            double var15 = Math.min(var5, 96.0) * (double)var14 / 8.0;
            SkillManager.schedule(
                var2,
                var14 * 3,
                () -> {
                    if (active(var3, var2.serverLevel().getGameTime())) {
                        for (int var5x = 0; var5x < 12; var5x++) {
                            double var6 = (double)var5x * Math.PI / 6.0;
                            Effects.slash(
                                var2, var8.add(Math.cos(var6) * var15, 0.5, Math.sin(var6) * var15), (float)Math.toDegrees(var6), 0.0F, 0.0F, 8.0F, 0.3F, 1, 8
                            );
                        }
                    }
                }
            );
        }

        Msg.chat(var2, Msg.system(Msg.tr("skill.domain.expanded", var1.entry.name(), fmt(var5), var7 / 20).withStyle(ChatFormatting.DARK_PURPLE)));
    }

    private static void spawnShrine(SkillContext var0, Vec3 var1) {
        ServerPlayer var2 = var0.player;
        if (var0.bool("shrine.enabled", true)) {
            removeShrine(var2);
            ServerLevel var3 = var2.serverLevel();
            float var4 = var2.getYRot();
            double var5 = Math.toRadians((double)var4);
            double var7 = -Math.sin(var5);
            double var9 = Math.cos(var5);
            double var11 = var0.num("shrine.offset", 7.0);
            double var13 = var1.x - var7 * var11;
            double var15 = var1.z - var9 * var11;
            double var17 = var1.y;
            BlockPos var19 = BlockPos.containing(var13, var17, var15);
            double var20;
            if (var3.hasChunkAt(var19)
                && Math.abs((var20 = (double)var3.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var19.getX(), var19.getZ())) - var17) <= 6.0) {
                var17 = var20;
            }

            ShrineEntity var22 = ShrineEntity.create(var2, var13, var17, var15, var4, (float)var0.num("shrine.scale", 1.0));
            var3.addFreshEntity(var22);
            SHRINES.put(var2.getUUID(), var22);
            var3.sendParticles(ParticleTypes.LARGE_SMOKE, var13, var17 + 1.0, var15, 80, 3.0, 1.0, 3.0, 0.05);
            var3.playSound(null, var13, var17, var15, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0F, 0.5F);
        }
    }

    public static void reset() {
        SHRINES.clear();
        DomainTerrainSweep.reset();
    }

    public static ShrineEntity shrine(ServerPlayer var0) {
        ShrineEntity var1 = SHRINES.get(var0.getUUID());
        return var1 != null && !var1.isRemoved() ? var1 : null;
    }

    private static void removeShrine(ServerPlayer var0) {
        ShrineEntity var1 = SHRINES.remove(var0.getUUID());
        if (var1 != null && !var1.isRemoved()) {
            ServerLevel var2 = var0.serverLevel();
            var2.sendParticles(ParticleTypes.LARGE_SMOKE, var1.getX(), var1.getY() + 4.0, var1.getZ(), 60, 3.0, 3.0, 3.0, 0.03);
            var1.discard();
        }
    }

    public static void end(ServerPlayer var0, String var1) {
        PlayerSystemData var2 = SystemDataManager.get(var0);
        removeShrine(var0);
        DomainTerrainSweep.clear(var0.getUUID());
        if (!(var2.value("domain.until", 0.0) <= 0.0)) {
            var2.removeValue("king.embers");
            var2.removeValue("king.domain_cost");
            long var3 = var0.serverLevel().getGameTime();
            SkillContext var5 = context(var0, var2);
            int var6 = var5 != null ? Math.max(0, var5.integer("burnout.duration_ticks", 1200)) : 1200;
            Vec3 var7 = center(var2);
            double var8 = var2.value("domain.radius", 20.0);

            for (String var13 : new String[]{
                "domain.until",
                "domain.x",
                "domain.y",
                "domain.z",
                "domain.radius",
                "domain.height",
                "domain.slot",
                "domain.started",
                "domain.next_tick",
                "domain.next_cleave",
                "domain.last_drain",
                "domain.resized"
            }) {
                var2.removeValue(var13);
            }

            if (var6 > 0) {
                var2.setValue("burnout_until", (double)(var3 + (long)var6));
            }

            SystemDataManager.markDirty(var0);
            ServerLevel var14 = var0.serverLevel();
            var14.playSound(null, var7.x, var7.y, var7.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0F, 0.6F);
            var14.playSound(null, var7.x, var7.y, var7.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
            double var15 = Math.min(var8, 64.0);
            var14.sendParticles(ParticleTypes.LARGE_SMOKE, var7.x, var7.y + 1.0, var7.z, 120, var15 * 0.4, 1.5, var15 * 0.4, 0.02);
            Msg.chat(var0, Msg.system(Msg.tr("skill.domain.ended." + var1, var6 / 20).withStyle(ChatFormatting.GRAY)));
        }
    }

    public static void tick(ServerPlayer var0) {
        PlayerSystemData var1 = SystemDataManager.get(var0);
        double var2 = var1.value("domain.until", 0.0);
        if (var2 <= 0.0) {
            if (SHRINES.containsKey(var0.getUUID())) {
                removeShrine(var0);
            }
        } else {
            long var4 = var0.serverLevel().getGameTime();
            if (!var0.isAlive()) {
                end(var0, "dead");
            } else if ((double)var4 >= var2) {
                end(var0, "expired");
            } else {
                SkillContext var6 = context(var0, var1);
                if (var6 == null) {
                    end(var0, "recalled");
                } else {
                    double var7 = var1.value("domain.radius", var6.num("radius", 20.0));
                    var1.setValue("king.domain_upkeep", var6.num("mp_cost_per_second", 6.0) * costScale(var6, var7));
                    Vec3 var9 = center(var1);
                    double var10 = var0.getX() - var9.x;
                    double var12;
                    if (Math.sqrt(var10 * var10 + (var12 = var0.getZ() - var9.z) * var12) > var7 + 2.0
                        || var0.getY() < var9.y - 6.0
                        || var0.getY() > var9.y + planeHeight(var7) + 8.0) {
                        end(var0, "recalled");
                    } else if (!drain(var6, var1, var4, var7)) {
                        end(var0, "exhausted");
                    } else {
                        if (shrine(var0) == null && var4 % 20L == 0L) {
                            spawnShrine(var6, var9);
                        }

                        boundary(var0, var9, var7, var4);
                        DomainTerrainSweep.tick(var6, var9, var7, var4, Math.max(1, SHRINES.size()));
                        if ((double)var4 >= var1.value("domain.next_tick", 0.0)) {
                            var1.setValue("domain.next_tick", (double)(var4 + (long)Math.max(1, var6.integer("tick_interval", 10))));
                            sureHitDismantle(var6, var1, var9, var7);
                        }

                        if ((double)var4 >= var1.value("domain.next_cleave", 0.0)) {
                            var1.setValue("domain.next_cleave", (double)(var4 + (long)cleaveInterval(var6)));
                            domainCleave(var6, var9, var7);
                        }

                        SystemDataManager.markDirty(var0);
                    }
                }
            }
        }
    }

    private static boolean drain(SkillContext var0, PlayerSystemData var1, long var2, double var4) {
        long var6 = (long)var1.value("domain.last_drain", (double)var2);
        if (var2 - var6 < 20L) {
            return true;
        } else {
            var1.setValue("domain.last_drain", (double)var2);
            double var8 = StatCalculator.maxStat(StatType.HP, var1, TemplateRegistry.get(), CoreConfig.get());
            double var10 = costScale(var0, var4);
            double var12 = var8 * var0.num("hp_cost_ratio_per_second", 0.02) * var10;
            double var14 = var0.num("mp_cost_per_second", 6.0) * var10;
            if (!(var1.hp() - var12 < var8 * 0.15) && !(var1.mp() < var14)) {
                var1.setHp(var1.hp() - var12);
                var1.setMp(var1.mp() - var14);
                return true;
            } else {
                return false;
            }
        }
    }

    static double surfaceY(ServerLevel var0, double var1, double var3, double var5, double var7) {
        int var9 = (int)Math.floor(var1);
        int var10;
        if (!var0.hasChunk(var9 >> 4, (var10 = (int)Math.floor(var3)) >> 4)) {
            return var5;
        } else {
            double var11 = (double)var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var9, var10);
            return var11 >= var5 - 2.0 && var11 <= var5 + var7 ? var11 : var5;
        }
    }

    private static Vec3 randomPlanePoint(ServerPlayer var0, Vec3 var1, double var2, RandomSource var4) {
        double var5 = var1.x;
        double var7 = var1.z;
        double var9 = var2;
        if (var2 > 64.0 && var4.nextFloat() < 0.7F) {
            var5 = var0.getX();
            var7 = var0.getZ();
            var9 = 48.0;
        }

        for (int var11 = 0; var11 < 6; var11++) {
            double var12 = var4.nextDouble() * Math.PI * 2.0;
            double var14 = Math.sqrt(var4.nextDouble()) * var9 * 0.95;
            double var16 = var5 + Math.cos(var12) * var14;
            double var18;
            if ((var16 - var1.x) * (var16 - var1.x) + ((var18 = var7 + Math.sin(var12) * var14) - var1.z) * (var18 - var1.z) <= var2 * var2) {
                return new Vec3(var16, var1.y, var18);
            }
        }

        return new Vec3(var1.x, var1.y, var1.z);
    }

    private static void boundary(ServerPlayer var0, Vec3 var1, double var2, long var4) {
        if (var4 % 10L == 0L) {
            Effects.domainField(var0, var1, (float)var2);
        }

        ServerLevel var6 = var0.serverLevel();
        RandomSource var7 = var0.getRandom();
        double var8 = planeHeight(var2);
        if (var4 % 4L == 0L) {
            int var10 = (int)Math.min(24.0, Math.max(8.0, var2 * 0.25));

            for (int var11 = 0; var11 < var10; var11++) {
                double var12 = var7.nextDouble() * Math.PI * 2.0;
                double var14 = var1.x + Math.cos(var12) * var2;
                double var16 = var1.z + Math.sin(var12) * var2;
                double var18 = var1.y + var7.nextDouble() * 4.0;
                var6.sendParticles(ParticleTypes.CRIMSON_SPORE, var14, var18, var16, 1, 0.0, 0.0, 0.0, 0.0);
                if (var11 % 3 == 0) {
                    var6.sendParticles(ParticleTypes.SMOKE, var14, var1.y + 0.2, var16, 1, 0.1, 0.3, 0.1, 0.0);
                }
            }

            int var20 = (int)Math.max(1.0, Math.min(12.0, var2 / 10.0));
            if (var4 % 40L == 0L) {
                var6.playSound(null, var1.x, var1.y, var1.z, ModSounds.slash(), SoundSource.PLAYERS, 0.8F, 0.75F);
            }
        }
    }

    private static void sureHitDismantle(SkillContext var0, PlayerSystemData var1, Vec3 var2, double var3) {
        ServerPlayer var5 = var0.player;
        ServerLevel var6 = var5.serverLevel();
        SkillContext var7 = skillContext(var5, "rpgtemplate:dismantle");
        double var8 = var0.num("dismantle_coefficient_scale", 0.65) * damageScale(var0, var3) * progressionScale(var0);
        float var10 = var7 != null ? var7.damage(var8) : var0.damage(var8);
        List<LivingEntity> var11 = targets(var5, var2, var3);
        DamageSource var12 = ModDamageTypes.technique(var5);
        Object var13 = null;
        RandomSource var14 = var5.getRandom();

        for (LivingEntity var16 : var11) {
            Vec3 var17 = TargetUtil.center(var16);
            Effects.slash(
                var5,
                var17,
                var14.nextFloat() * 360.0F,
                (var14.nextFloat() - 0.5F) * 40.0F,
                var14.nextBoolean() ? 45.0F : -45.0F,
                2.0F + var16.getBbWidth() * 2.0F,
                0.3F,
                1,
                8
            );
            KingRules.domainHurt(var16, var12, var10);
            if (var13 != null && !((TerrainCutter)var13).exhausted()) {
                ((TerrainCutter)var13).cutBox(var16.getBoundingBox().inflate(0.5, 0.0, 0.5).expandTowards(0.0, -0.4, 0.0));
            }
        }

        if (!var11.isEmpty()) {
            var6.playSound(null, var2.x, var2.y, var2.z, ModSounds.slash(), SoundSource.PLAYERS, 1.5F, 0.8F);
        }

        if (var13 != null) {
            double var24 = planeHeight(var3);
            int var25 = (int)Math.max(2.0, Math.min(25.0, var3 / 8.0));

            for (int var18 = 0; var18 < var25 && !((TerrainCutter)var13).exhausted(); var18++) {
                Vec3 var19 = randomPlanePoint(var5, var2, var3, var14);
                double var20 = surfaceY(var6, var19.x, var19.z, var2.y, var24) - 0.5;
                double var22 = var14.nextDouble() * Math.PI * 2.0;
                ((TerrainCutter)var13)
                    .cutLine(new Vec3(var19.x, var20, var19.z), new Vec3(Math.cos(var22), 0.0, Math.sin(var22)), 2.0 + var14.nextDouble() * 4.0);
            }
        }
    }

    private static void domainCleave(SkillContext var0, Vec3 var1, double var2) {
        ServerPlayer var4 = var0.player;
        SkillContext var5 = skillContext(var4, "rpgtemplate:cleave");
        if (var5 != null) {
            boolean var6 = var5.modeUnlocked("sure_hit");
            double var7 = var0.num("cleave_coefficient_scale", 1.1) * damageScale(var0, var2) * progressionScale(var0);
            List<LivingEntity> var9 = targets(var4, var1, var2);
            if (var6) {
                int var10 = Math.max(1, var5.integer("sure_hit.max_targets", 3));
                var9.sort(Comparator.comparingDouble(LivingEntity::getHealth).reversed());
                int var11 = 0;

                for (LivingEntity var13 : var9) {
                    if (var11++ >= var10) {
                        break;
                    }

                    CleaveSkill.domainStrike(var5, var13, var7, true, var11 * 3);
                }

                if (var11 > 0) {
                    Msg.actionBar(var4, Msg.tr("skill.domain.sure_hit_cleave", Math.min(var11, var10)).withStyle(ChatFormatting.DARK_RED));
                }
            } else {
                double var15 = var0.num("cleave_threshold_hp_ratio", 0.5);
                int var16 = 0;

                for (LivingEntity var14 : var9) {
                    if (var14.getMaxHealth() > 0.0F && (double)(var14.getHealth() / var14.getMaxHealth()) >= var15) {
                        CleaveSkill.domainStrike(var5, var14, var7, false, var16 * 3);
                        var16++;
                    }
                }
            }
        }
    }

    public static boolean active(PlayerSystemData var0, long var1) {
        return (double)var1 < var0.value("domain.until", 0.0);
    }

    public static boolean burnedOut(PlayerSystemData var0, long var1) {
        return (double)var1 < var0.value("burnout_until", 0.0);
    }

    public static boolean inside(PlayerSystemData var0, long var1, Vec3 var3) {
        if (!active(var0, var1)) {
            return false;
        } else {
            Vec3 var4 = center(var0);
            double var5 = var3.x - var4.x;
            double var7 = var3.z - var4.z;
            double var9 = var0.value("domain.radius", 0.0);
            return var5 * var5 + var7 * var7 <= var9 * var9 && var3.y >= var4.y - 2.0 && var3.y <= var4.y + planeHeight(var9);
        }
    }

    public static Vec3 center(PlayerSystemData var0) {
        return new Vec3(var0.value("domain.x", 0.0), var0.value("domain.y", 0.0), var0.value("domain.z", 0.0));
    }

    public static List<LivingEntity> targets(ServerPlayer var0, Vec3 var1, double var2) {
        double var4 = planeHeight(var2);
        AABB var6 = new AABB(var1.x - var2, var1.y - 2.0, var1.z - var2, var1.x + var2, var1.y + var4, var1.z + var2);
        ArrayList<LivingEntity> var7 = new ArrayList<>();
        HashSet var8 = new HashSet();

        for (Entity var10 : var0.serverLevel().getEntities(var0, var6, var1x -> TargetUtil.valid(var0, var1x))) {
            LivingEntity var11 = TargetUtil.living(var10);
            if (var11 != null && var8.add(var11.getId())) {
                double var12 = var11.getX() - var1.x;
                double var14 = var11.getZ() - var1.z;
                double var16 = var11.getY();
                double var18 = var16 + (double)var11.getBbHeight();
                if (!(var12 * var12 + var14 * var14 > var2 * var2) && !(var18 < var1.y - 2.0) && !(var16 > var1.y + var4)) {
                    var7.add(var11);
                }
            }
        }

        var7.sort(Comparator.comparingDouble(var1x -> {
            double var2x = var1x.getX() - var1.x;
            double var4x = var1x.getZ() - var1.z;
            return var2x * var2x + var4x * var4x;
        }));
        return var7;
    }

    public static double progressionScale(SkillContext var0) {
        return var0.num("tier_damage_scale", 1.0) * (1.0 + (double)Math.max(0, var0.level() - 1) * var0.num("level_damage_per_level", 0.025));
    }

    static double damageScale(SkillContext var0, double var1) {
        double var3 = var0.num("radius", 20.0);
        double var5 = minRadius(var0);
        double var7 = var0.num("resize.damage_scale_at_min", 2.0);
        if (!(var1 >= var3) && !(var3 <= var5)) {
            double var9 = (var3 - var1) / (var3 - var5);
            return 1.0 + (var7 - 1.0) * Math.max(0.0, Math.min(1.0, var9));
        } else {
            return 1.0;
        }
    }

    static double costScale(SkillContext var0, double var1) {
        double var3 = var0.num("radius", 20.0);
        double var5 = maxRadius(var0);
        double var7 = var0.num("resize.cost_scale_at_max", 4.0);
        if (!(var1 <= var3) && !(var5 <= var3)) {
            double var9 = (var1 - var3) / (var5 - var3);
            return 1.0 + (var7 - 1.0) * Math.max(0.0, Math.min(1.0, var9));
        } else {
            return 1.0;
        }
    }

    private static int cleaveInterval(SkillContext var0) {
        SkillContext var1 = skillContext(var0.player, "rpgtemplate:cleave");
        int var2 = var1 != null && var1.modeUnlocked("sure_hit") ? var1.integer("sure_hit.interval_ticks", 40) : -1;
        return Math.max(10, var2 > 0 ? var2 : var0.integer("cleave_interval_ticks", 40));
    }

    private static TerrainCutter terrainCutter(SkillContext var0, float var1) {
        return !var0.bool("terrain.enabled", true) ? null : TerrainCutter.forSlash(var0.player, var1, var0.num("terrain.budget_scale", 4.0));
    }

    private static SkillContext context(ServerPlayer var0, PlayerSystemData var1) {
        int var2 = (int)var1.value("domain.slot", -1.0);
        SkillContext var3 = var2 >= 0 ? SkillContext.create(var0, var2) : null;
        return var3 != null && var3.entry.id().equals("rpgtemplate:domain_expansion") ? var3 : skillContext(var0, "rpgtemplate:domain_expansion");
    }

    public static SkillContext skillContext(ServerPlayer var0, String var1) {
        int var2 = slotOf(var0, var1);
        return var2 < 0 ? null : SkillContext.create(var0, var2);
    }

    public static int slotOf(ServerPlayer var0, String var1) {
        PlayerSystemData var2 = SystemDataManager.get(var0);
        if (var2.loadedTemplateId() == null) {
            return -1;
        } else {
            List var3 = TemplateRegistry.get().effectiveSkills(var2.loadedTemplateId());

            for (int var4 = 0; var4 < var3.size(); var4++) {
                if (((SkillEntry)var3.get(var4)).id().equals(var1)) {
                    return var4;
                }
            }

            return -1;
        }
    }

    private static List<String> chant(SkillContext var0) {
        JsonArray var1 = var0.array("chant");
        ArrayList var2 = new ArrayList();
        if (var1 != null) {
            for (JsonElement var4 : var1) {
                var2.add(var4.getAsString());
            }
        }

        if (var2.isEmpty()) {
            var2.add("領域展開");
        }

        return var2;
    }

    static String fmt(double var0) {
        return String.format(Locale.ROOT, var0 == Math.floor(var0) ? "%.0f" : "%.1f", var0);
    }
}
