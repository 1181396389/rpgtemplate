package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DismantleSkill implements Skill {
    static final int TAP_TICKS = 5;
    static final int MODE_CHARGE = 0;
    static final int MODE_LOCKED = 1;
    static final int MODE_BARRAGE = 2;
    static final int PATTERN_LEFT = 0;
    static final int PATTERN_RIGHT = 1;
    static final int PATTERN_CROSS = 2;
    static final float LEFT_ROLL = 45.0F;
    static final float RIGHT_ROLL = -45.0F;
    static final double DEFAULT_SPEED = 1.6;
    static final double DEFAULT_RANGE = 28.0;
    private static final String KEY_ALTERNATE = "dismantle.alternate";

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        if (var2.sneakAtPress && var1.param("barrage") != null) {
            var2.mode = 2;
            var2.count = 0;
            var2.nextTick = var1.now + (long)Math.max(0, var1.integer("barrage.windup_ticks", 2));
            if (var2.nextTick <= var1.now) {
                this.barrageTick(var1, var2);
            }
        }
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        if (var2.mode == 2) {
            this.barrageTick(var1, var2);
        } else {
            this.chargeTick(var1, var2);
        }
    }

    @Override
    public void onRelease(SkillContext var1, HeldState var2) {
        if (var2.mode == 2) {
            this.endBarrage(var1, var2, true, true);
        } else {
            int var3 = var1.array("charge.chant").size();
            int var4 = var2.charging ? var2.stage : 0;
            if (var1.param("volley") != null && var4 >= var1.integer("volley.min_stage", 2) && var1.player.isShiftKeyDown()) {
                if (var1.modeUnlocked("volley")) {
                    this.fireVolley(var1, var4, var3, var2.burst);
                    return;
                }

                if (var1.modeLocked("volley")) {
                    Msg.actionBar(
                        var1.player,
                        Msg.tr("skill.mode_locked", var1.str("volley.name", "一斉掃射"), var1.modeUnlockLevel("volley")).withStyle(ChatFormatting.GRAY)
                    );
                }
            }

            if (spiderAimed(var1, var4)) {
                if (var1.modeUnlocked("spider")) {
                    this.fireSpider(var1, var4, var3, var2.burst);
                    return;
                }

                if (var1.modeLocked("spider")) {
                    Msg.actionBar(
                        var1.player,
                        Msg.tr("skill.mode_locked", Msg.tr("skill.dismantle.spider_name"), var1.modeUnlockLevel("spider")).withStyle(ChatFormatting.GRAY)
                    );
                }
            }

            this.fire(var1, var4, var3, var2.burst);
        }
    }

    private static boolean spiderAimed(SkillContext var0, int var1) {
        return var0.param("spider") != null
            && var1 >= var0.integer("spider.min_stage", 1)
            && (double)var0.player.getXRot() >= var0.num("spider.min_pitch", 50.0);
    }

    static double damageRadiusBonus(SkillContext var0, String var1, float var2, double var3, double var5, double var7) {
        double var9 = Math.max(0.001, var0.num(var1 + ".damage_radius_ref", var3));
        double var11 = Math.max(0.0, var0.num(var1 + ".damage_radius_k", var5));
        double var13 = Math.max(0.0, var0.num(var1 + ".damage_radius_max", var7));
        double var15 = var11 * (Math.log(1.0 + (double)Math.max(0.0F, var2) / var9) / Math.log(2.0));
        return Math.min(var13, var15);
    }

    private void fireSpider(SkillContext var1, int var2, int var3, boolean var4) {
        ServerPlayer var10 = var1.player;
        double var11 = var1.cost() * Math.max(0.0, var1.num("spider.cost_multiplier", 3.0));
        if (!var1.consume(var11)) {
            if (var2 > 0) {
                Chant.clear(var10);
            }
        } else {
            boolean var13 = var3 > 0 && var2 >= var3;
            double var14 = var1.num("spider.reach", 10.0);
            Vec3 var16 = TargetUtil.rayEnd(var10, var14);
            int var17 = groundY(var10.serverLevel(), var16, 6);
            Vec3 var18 = new Vec3(var16.x, (double)var17 + 1.0, var16.z);
            double var19 = var1.arrayNum("charge.stage_multipliers", var2, 1.0) * var1.num("spider.coefficient_scale", 1.2) * (var4 && var13 ? 1.1 : 1.0);
            float var21 = var1.damage(var19);
            double var22 = damageRadiusBonus(var1, "spider", var21, 20.0, 0.35, 2.5);
            double var24 = var1.num("spider.radius", 6.0) * (1.0 + (double)var2 * var1.num("spider.radius_per_stage", 0.5)) * (1.0 + var22);
            int var26 = Math.max(2, var1.integer("spider.rays", 8) / 2 * 2);
            int var27 = Math.max(0, var1.integer("spider.rings", 2) + (int)Math.floor(var22));
            int var28 = Math.max(1, var1.integer("spider.depth", 1) + (int)Math.floor(var22 * var1.num("spider.damage_depth_scale", 0.5)));
            int var29 = var2 >= Math.max(1, var3 - 1) ? 1 : 0;
            float var30 = 1.0F + (float)var2 * 0.25F;
            double var31 = var1.num("flight.terrain_budget_scale", 1.0)
                * var1.num("spider.terrain_budget_scale", 2.0)
                * (1.0 + (double)var2 * 0.5)
                * (1.0 + var22)
                * (1.0 + var22);
            TerrainCutter var33 = TerrainCutter.forSlash(var10, var21, var31);
            if (var33 != null) {
                for (int var34 = 0; var34 < var28 && !var33.exhausted(); var34++) {
                    Vec3 var38 = new Vec3(var18.x, (double)(var17 - var34) + 0.5, var18.z);

                    for (int var37 = 0; var37 < var26 / 2; var37++) {
                        double var35 = Math.PI * (double)var37 / (double)(var26 / 2);
                        var33.cutLine(var38, new Vec3(Math.cos(var35), 0.0, Math.sin(var35)), var24 * 2.0);
                    }

                    for (int var53 = 1; var53 <= var27; var53++) {
                        double var51 = var24 * (double)var53 / ((double)var27 + 1.0);
                        int var39 = Math.max(8, (int)(var51 * 4.0));

                        for (int var9 = 0; var9 < var39; var9++) {
                            double var40 = (Math.PI * 2) * (double)var9 / (double)var39;
                            double var7 = (Math.PI * 2) * (double)(var9 + 1) / (double)var39;
                            Vec3 var42 = var38.add(var51 * Math.cos(var40), 0.0, var51 * Math.sin(var40));
                            Vec3 var43 = var38.add(var51 * Math.cos(var7), 0.0, var51 * Math.sin(var7));
                            Vec3 var44 = var43.subtract(var42);
                            double var45 = var44.length();
                            if (var45 > 1.0E-4) {
                                var33.cutLine(var42.add(var43).scale(0.5), var44.scale(1.0 / var45), var45);
                            }
                        }
                    }
                }
            }

            DamageSource var50 = ModDamageTypes.technique(var10);
            double var52 = var1.num("spider.launch", 0.35);
            int var54 = var1.integer("spider.slow_ticks", 60);
            int var55 = var1.integer("spider.slow_amplifier", 2);
            AABB var56 = new AABB(var18.x - var24, (double)(var17 - var28), var18.z - var24, var18.x + var24, (double)var17 + 3.5, var18.z + var24);
            int var49 = 0;

            for (Entity var41 : var10.level().getEntities(var10, var56, var1x -> TargetUtil.valid(var10, var1x))) {
                double var62 = var41.getX() - var18.x;
                double var48;
                if (!(var62 * var62 + (var48 = var41.getZ() - var18.z) * var48 > var24 * var24) && TargetUtil.hurt(var41, var50, var21)) {
                    var49++;
                    LivingEntity var6 = TargetUtil.living(var41);
                    if (var6 != null) {
                        if (var54 > 0) {
                            var6.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, var54, Math.max(0, var55)));
                        }

                        if (var52 > 0.0) {
                            var6.push(0.0, var52, 0.0);
                            var6.hurtMarked = true;
                        }
                    }

                    impactSound(var10, TargetUtil.center(var41), var30);
                }
            }

            Vec3 var58 = new Vec3(var18.x, (double)var17 + 1.08, var18.z);

            for (int var5 = 0; var5 < var26 / 2; var5++) {
                float var59 = (float)(180.0 * (double)var5 / (double)(var26 / 2));
                Effects.slash(
                    var10, var58, var59, 90.0F, 0.0F, (float)(var24 * 2.0), (0.35F + 0.1F * (float)var2) * (float)(1.0 + var22 * 0.5), var29, 14 + var2 * 2
                );
            }

            for (int var47 = 1; var47 <= var27; var47++) {
                double var60 = var24 * (double)var47 / ((double)var27 + 1.0);
                int var64 = Math.max(24, (int)(var60 * 3.0));

                for (int var66 = 0; var66 < var64; var66++) {
                    double var68 = (Math.PI * 2) * (double)var66 / (double)var64;
                    var10.serverLevel()
                        .sendParticles(
                            ParticleTypes.CRIT, var58.x + var60 * Math.cos(var68), var58.y, var58.z + var60 * Math.sin(var68), 2, 0.1, 0.05, 0.1, 0.05
                        );
                }
            }

            var10.swing(InteractionHand.MAIN_HAND, true);
            float var61 = 0.6F * (1.0F + 0.2F * (float)var2);

            for (float var69 : new float[]{0.5F, 0.75F, 1.0F}) {
                var10.level().playSound(null, var10.getX(), var10.getY(), var10.getZ(), ModSounds.slash(), SoundSource.PLAYERS, var61, var69);
            }

            var10.level().playSound(null, var58.x, var58.y, var58.z, ModSounds.explode(), SoundSource.PLAYERS, 1.2F, 0.7F);
            var10.level().playSound(null, var58.x, var58.y, var58.z, ModSounds.crush(), SoundSource.PLAYERS, 0.9F, 0.8F);
            Msg.actionBar(var10, Msg.tr("skill.dismantle.spider", var49).withStyle(var29 == 1 ? ChatFormatting.DARK_RED : ChatFormatting.RED));
            if (var2 > 0) {
                Chant.clear(var10);
            }

            var1.cooldown(Math.max(var1.entry.cooldownTicks(), var1.integer("spider.cooldown_ticks", 60)));
        }
    }

    static int groundY(ServerLevel var0, Vec3 var1, int var2) {
        BlockPos var3 = BlockPos.containing(var1.x, var1.y - 0.01, var1.z);

        for (int var4 = 0; var4 <= var2; var4++) {
            BlockPos var5 = var3.below(var4);
            if (!var0.getBlockState(var5).getCollisionShape(var0, var5).isEmpty()) {
                return var5.getY();
            }
        }

        return var3.getY() - var2;
    }

    private void fireVolley(SkillContext var1, int var2, int var3, boolean var4) {
        ServerPlayer var6 = var1.player;
        double var7 = var1.cost() * var1.num("volley.cost_multiplier", 4.0);
        if (!var1.consume(var7)) {
            Chant.clear(var6);
        } else {
            RandomSource var9 = var6.getRandom();
            int var10 = Math.max(2, var1.integer("volley.count", 12));
            double var11 = Math.max(5.0, var1.num("volley.spread_degrees", 48.0));
            int var13 = Math.max(0, var1.integer("volley.stagger_ticks", 1));
            boolean var14 = var3 > 0 && var2 >= var3;
            double var15 = var1.arrayNum("charge.stage_multipliers", var2, 1.0) * var1.num("volley.coefficient_scale", 0.55) * (var4 && var14 ? 1.1 : 1.0);
            float var17 = var1.damage(var15);
            double var18 = var1.num("range", 28.0) * (1.0 + (double)var2 * var1.num("flight.range_per_stage", 0.15));
            double var20 = var1.num("flight.speed", 1.6) + (double)var2 * var1.num("flight.speed_per_stage", 0.3);
            int var22 = Math.max(1, (var1.integer("flight.pierce", 3) + var2) / 2);
            float var23 = (float)(var1.num("length", 3.0) * (1.0 + (double)var2 * var1.num("length_per_stage", 0.6)) * 0.8);
            double var24 = var1.num("flight.terrain_budget_scale", 1.0) * var1.num("volley.terrain_budget_scale", 3.0) / (double)var10;
            int var26 = var2 >= Math.max(1, var3 - 1) ? 1 : 0;
            float var27 = 1.0F + (float)var2 * 0.25F;
            DamageSource var28 = ModDamageTypes.technique(var6);
            Consumer<Entity> var29 = var4x -> {
                if (var4x.isAlive()) {
                    TargetUtil.hurt(var4x, var28, var17);
                    impactSound(var6, TargetUtil.center(var4x), var27);
                }
            };
            float var30 = var6.getYRot();
            float var31 = var6.getXRot();
            Vec3 var32 = muzzle(var6, var9, 0.0);
            Vec3 var33 = aimDir(var6, var32, var18).normalize();
            Vec3 var34 = TargetUtil.left(var30).scale(-1.0);
            Vec3 var35 = TargetUtil.up(var30, var31);
            Msg.actionBar(var6, Msg.tr("skill.dismantle.volley", var1.str("volley.name", "一斉掃射"), var10).withStyle(ChatFormatting.DARK_RED));
            var6.swing(InteractionHand.MAIN_HAND, true);

            for (int var5 = 0; var5 < var10; var5++) {
                double var36 = var10 == 1 ? 0.0 : (double)var5 / (double)(var10 - 1) - 0.5;
                double var38 = Math.toRadians(var36 * var11);
                double var40 = Math.toRadians((var9.nextDouble() - 0.5) * var11 * 0.25);
                Vec3 var42 = var33.scale(Math.cos(var38)).add(var34.scale(Math.sin(var38))).add(var35.scale(Math.sin(var40))).normalize();
                float var43 = (var5 % 2 == 0 ? 45.0F : -45.0F) + (var9.nextFloat() - 0.5F) * 12.0F;
                float var44 = (float)Math.toDegrees(Math.atan2(-var42.x, var42.z));
                float var45 = (float)Math.toDegrees(-Math.atan2(var42.y, Math.sqrt(var42.x * var42.x + var42.z * var42.z)));
                TerrainCutter var46 = TerrainCutter.forSlash(var6, var17, var24);
                FlyingSlash.Volley var47 = new FlyingSlash.Volley(var22);
                Runnable var48 = () -> {
                    FlyingSlash.Spec var17x = new FlyingSlash.Spec(var32, var42, var44, var45, var43, var23, var26, var20, var18)
                        .cutLines(1, 0.8)
                        .onTerrainHit(var2xx -> impactSound(var6, var2xx, var27 * 0.6F));
                    FlyingSlash.launch(var6, var47, var17x, var46, var29);
                    var6.level()
                        .playSound(null, var6.getX(), var6.getY(), var6.getZ(), ModSounds.slash(), SoundSource.PLAYERS, 0.6F, 0.9F + var9.nextFloat() * 0.4F);
                };
                int var49 = var5 * var13;
                if (var49 > 0) {
                    SkillManager.schedule(var6, var49, var48);
                } else {
                    var48.run();
                }
            }

            int var50 = var1.integer("volley.cooldown_ticks", 0);
            if (var50 > 0) {
                var1.cooldown(var50);
            } else {
                var1.cooldown();
            }
        }
    }

    private void chargeTick(SkillContext var1, HeldState var2) {
        int var4 = var2.heldTicks(var1.now);
        if (var4 >= 5) {
            ServerPlayer var5 = var1.player;
            int var6 = Math.max(4, var1.integer("charge.ticks_per_stage", 20));
            JsonArray var7 = var1.array("charge.chant");
            int var8 = var7.size();
            if (!var2.charging) {
                var2.charging = true;
                var2.lastPulse = var1.now;
                Effects.chargeAura(var5, 0, var6 + 6);
            }

            int var3;
            if ((var3 = var1.integer("charge.slowness", 4)) >= 0) {
                var5.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, var3, false, false));
            }

            bodyParticles(var5, var2.burst ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, 2 + var2.stage);
            int var9 = Math.min(var8, (var4 - 5) / var6);
            if (var2.stage < var9 && var2.mode == 0) {
                double var10 = var1.cost(var1.num("charge.cost_per_stage", 0.0));
                if (var1.consume(var10)) {
                    var2.stage++;
                    String var12 = var7.get(var2.stage - 1).getAsString();
                    Chant.say(var5, var12, var2.stage, var8, var1.num("charge.chant_audible_radius", 16.0), var1.bool("charge.chant_in_chat", true));
                    Effects.chargeAura(var5, var2.stage, var6 + 6);
                    var2.lastPulse = var1.now;
                    bodyParticles(var5, ParticleTypes.CRIT, 8 + var2.stage * 6);
                    if (var2.stage >= var8) {
                        chargeComplete(var5);
                        var2.burst = true;
                    }
                } else {
                    var2.mode = 1;
                }
            }

            if (var1.now - var2.lastPulse >= (long)var6) {
                Effects.chargeAura(var5, var2.stage, var6 + 6);
                var2.lastPulse = var1.now;
            }
        }
    }

    static void chargeComplete(ServerPlayer var0) {
        Vec3 var1 = new Vec3(var0.getX(), var0.getY() + (double)var0.getBbHeight() * 0.75, var0.getZ())
            .add(var0.getLookAngle().scale(1.0 + (double)var0.getBbWidth()));
        var0.serverLevel().sendParticles(ParticleTypes.FLASH, var1.x, var1.y, var1.z, 1, 0.0, 0.0, 0.0, 0.0);
        var0.level().playSound(null, var1.x, var1.y, var1.z, ModSounds.explode(), SoundSource.PLAYERS, 0.7F, 1.0F);
        var0.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, var1.x, var1.y, var1.z, 20, 0.25, 0.25, 0.25, 1.0);
    }

    private void fire(SkillContext var1, int var2, int var3, boolean var4) {
        ServerPlayer var6 = var1.player;
        if (!var1.consume(var1.cost())) {
            if (var2 > 0) {
                Chant.clear(var6);
            }
        } else {
            boolean var7 = var3 > 0 && var2 >= var3;
            RandomSource var5;
            int var8 = pattern(var1, var2, var7, var5 = var6.getRandom());
            if (var8 == 2) {
                var8 = var5.nextBoolean() ? 0 : 1;
            }

            boolean var9 = var4 && var7 && var1.bool("charge.world_cut", false);
            double var10 = var1.arrayNum("charge.stage_multipliers", var2, 1.0);
            float var12 = var1.damage(var10);
            double var13 = var1.num("range", 28.0) * (1.0 + (double)var2 * var1.num("flight.range_per_stage", 0.15)) * (var9 ? 1.5 : 1.0);
            double var15 = var1.num("flight.speed", 1.6) + (double)var2 * var1.num("flight.speed_per_stage", 0.3);
            int var17 = var1.integer("flight.pierce", 3) + var2;
            float var18 = (float)(var1.num("length", 3.0) * (1.0 + (double)var2 * var1.num("length_per_stage", 0.6)));
            int var19 = 1 + (int)Math.round((double)var2 * var1.num("charge.cut_lines_per_stage", 0.5)) + (var9 ? 1 : 0);
            double var20 = var1.num("flight.terrain_budget_scale", 1.0) * (1.0 + (double)var2 * var1.num("charge.terrain_per_stage", 0.5)) * (var9 ? 1.5 : 1.0);
            int var22 = var2 >= Math.max(1, var3 - 1) ? 1 : 0;
            float var23 = 1.0F + (float)var2 * 0.25F;
            DamageSource var24 = ModDamageTypes.technique(var6);
            Consumer<Entity> var25 = var6x -> {
                if (var6x.isAlive()) {
                    if (TargetUtil.hurt(var6x, var24, var12) && var7) {
                        KingRules.echo(var1, var6x, var12 * 0.3F);
                    }

                    impactSound(var6, TargetUtil.center(var6x), var23);
                }
            };
            Consumer<Vec3> var26 = var2x -> impactSound(var6, var2x, var23 * 0.8F);
            float var27 = var6.getYRot();
            float var28 = var6.getXRot();
            Vec3 var29 = muzzle(var6, var5, 0.0);
            Vec3 var30 = aimDir(var6, var29, var13);
            float var31 = (float)var1.num("roll_jitter", 8.0);
            float var32 = (var8 == 0 ? 45.0F : -45.0F) + (var5.nextFloat() - 0.5F) * var31;
            TerrainCutter var33 = TerrainCutter.forSlash(var6, var12, var20);
            FlyingSlash.Volley var34 = new FlyingSlash.Volley(var17);
            double var35 = 0.8 * (1.0 + (double)var2 * 0.15);
            Runnable var37 = () -> {
                FlyingSlash.Spec var20x = new FlyingSlash.Spec(var29, var30, var27, var28, var32, var18, var22, var15, var13)
                    .cutLines(var19, var35)
                    .throughWalls(var9)
                    .onTerrainHit(var26);
                FlyingSlash.launch(var6, var34, var20x, var33, var25);
            };
            var6.swing(InteractionHand.MAIN_HAND, true);
            float var38 = 0.5F * (1.0F + 0.2F * (float)var2) + (var9 ? 0.2F : 0.0F);

            for (float var42 : new float[]{0.5F, 0.75F, 1.0F}) {
                var6.level().playSound(null, var6.getX(), var6.getY(), var6.getZ(), ModSounds.slash(), SoundSource.PLAYERS, var38, var42);
            }

            int var43 = Math.max(0, var1.integer("fire_delay_ticks", 0));
            if (var43 > 0) {
                SkillManager.schedule(var6, var43, var37);
            } else {
                var37.run();
            }

            if (var9) {
                Msg.actionBar(var6, Msg.tr("skill.dismantle.world_cut").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}));
            }

            if (var2 > 0) {
                Chant.clear(var6);
            }

            var1.cooldown();
        }
    }

    static int pattern(SkillContext var0, int var1, boolean var2, RandomSource var3) {
        JsonArray var5 = var0.array("patterns");
        String var6 = var2 ? "cross" : "random";
        JsonElement var4;
        if (!var5.isEmpty() && (var4 = var5.get(Math.min(Math.max(0, var1), var5.size() - 1))).isJsonPrimitive()) {
            var6 = var4.getAsString();
        }

        String var8 = var6.toLowerCase(Locale.ROOT);
        switch (var8) {
            case "left":
                return 0;
            case "right":
                return 1;
            case "cross":
            case "x":
                return 2;
            case "alternate":
                boolean var10 = var0.data.value("dismantle.alternate", 0.0) <= 0.0;
                var0.data.setValue("dismantle.alternate", var10 ? 1.0 : 0.0);
                SystemDataManager.markDirty(var0.player);
                return var10 ? 0 : 1;
            default:
                return var3.nextBoolean() ? 0 : 1;
        }
    }

    static Vec3 aimDir(ServerPlayer var0, Vec3 var1, double var2) {
        Vec3 var4 = var0.getEyePosition().add(var0.getLookAngle().scale(var2));
        Vec3 var5 = var4.subtract(var1);
        return var5.lengthSqr() < 1.0E-6 ? var0.getLookAngle() : var5.normalize();
    }

    static Vec3 muzzle(ServerPlayer var0, RandomSource var1, double var2) {
        Vec3 var4 = var0.getLookAngle();
        Vec3 var5 = var0.getEyePosition().add(var4.scale(0.8)).add(0.0, -0.25, 0.0);
        if (var2 > 0.0) {
            var5 = var5.add(TargetUtil.left(var0.getYRot()).scale((var1.nextDouble() - 0.5) * var2)).add(0.0, (var1.nextDouble() - 0.5) * var2 * 0.5, 0.0);
        }

        return var5;
    }

    static void impactSound(ServerPlayer var0, Vec3 var1, float var2) {
        RandomSource var3 = var0.getRandom();
        var0.level().playSound(null, var1.x, var1.y, var1.z, ModSounds.explode(), SoundSource.PLAYERS, 0.8F * var2, 0.8F + var3.nextFloat() * 0.2F);
        var0.level().playSound(null, var1.x, var1.y, var1.z, ModSounds.crush(), SoundSource.PLAYERS, 0.5F * var2, 0.9F + var3.nextFloat() * 0.2F);
        var0.serverLevel().sendParticles(ParticleTypes.CRIT, var1.x, var1.y, var1.z, Math.max(4, (int)(8.0F * var2)), 0.3, 0.3, 0.3, 0.4);
    }

    static void bodyParticles(ServerPlayer var0, ParticleOptions var1, int var2) {
        var0.serverLevel()
            .sendParticles(
                var1,
                var0.getX(),
                var0.getY() + (double)var0.getBbHeight() * 0.5,
                var0.getZ(),
                var2,
                (double)var0.getBbWidth() * 0.5,
                (double)var0.getBbHeight() * 0.5,
                (double)var0.getBbWidth() * 0.5,
                0.0
            );
    }

    private static int minHits(SkillContext var0) {
        return Math.max(1, var0.integer("barrage.hits", 5));
    }

    private static int maxHits(SkillContext var0) {
        return Math.max(minHits(var0), var0.integer("barrage.max_hits", minHits(var0) * 3));
    }

    private static int interval(SkillContext var0) {
        return Math.max(1, var0.integer("barrage.interval_ticks", 2));
    }

    private static int crossEvery(SkillContext var0) {
        return Math.max(0, var0.integer("barrage.cross_every", 3));
    }

    private static boolean isCross(int var0, int var1) {
        return var0 > 0 && (var1 + 1) % var0 == 0;
    }

    private static double costPerHit(SkillContext var0, int var1) {
        double var2 = var0.cost() * var0.num("barrage.cost_multiplier", 2.5) / (double)minHits(var0);
        return isCross(crossEvery(var0), var1) ? var2 * var0.num("barrage.cross_cost_multiplier", 1.5) : var2;
    }

    private void barrageTick(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        int var4 = var1.integer("barrage.slowness", 6);
        if (var4 >= 0) {
            var3.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, var4, false, false));
        }

        if (var1.now >= var2.nextTick) {
            if (var2.count >= maxHits(var1)) {
                this.endBarrage(var1, var2, false, true);
                var2.finish();
            } else if (!var1.consume(costPerHit(var1, var2.count))) {
                this.endBarrage(var1, var2, false, false);
                var2.finish();
            } else {
                barrageHit(var3, var1, var2.count);
                var2.count++;
                var2.nextTick = var1.now + (long)interval(var1);
            }
        }
    }

    private void endBarrage(SkillContext var1, HeldState var2, boolean var3, boolean var4) {
        ServerPlayer var5 = var1.player;
        int var6 = interval(var1);
        int var7 = 0;
        if (var3) {
            int var9 = Math.max(0, minHits(var1) - var2.count);

            for (int var8 = 0; var8 < var9 && var1.consume(costPerHit(var1, var2.count + var8)); var8++) {
                var7++;
            }

            for (int var11 = 0; var11 < var7; var11++) {
                int var10 = var2.count + var11;
                SkillManager.schedule(var5, var11 * var6, () -> barrageHit(var5, var1, var10));
            }

            var2.count += var7;
        }

        if (var2.count > 0) {
            var1.cooldown(var1.entry.cooldownTicks() + var7 * var6);
            if (var4) {
                Msg.actionBar(var5, Msg.tr("skill.barrage.stop", var2.count).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void barrageHit(ServerPlayer var0, SkillContext var1, int var2) {
        float var6 = var1.damage(var1.num("barrage.coefficient_per_hit", 0.35), 1.0);
        double var7 = var1.num("range", 28.0);
        double var9 = var1.num("flight.speed", 1.6);
        double var11 = var1.num("flight.terrain_budget_scale", 1.0) * var1.num("barrage.terrain_budget_scale", 0.25);
        float var13 = (float)var1.num("barrage.length", 2.2);
        boolean var14 = isCross(crossEvery(var1), var2);
        if (var14) {
            var6 *= (float)var1.num("barrage.cross_damage_scale", 1.5);
            var13 *= (float)var1.num("barrage.cross_length_scale", 1.6);
            var11 *= 1.5;
        }

        RandomSource var15 = var0.getRandom();
        float var16 = var0.getYRot();
        float var17 = var0.getXRot();
        Vec3 var18 = muzzle(var0, var15, var14 ? 0.4 : 1.2);
        DamageSource var19 = ModDamageTypes.technique(var0);
        List var20 = TargetUtil.ray(var0, var7, 1, 0.6);
        Vec3 var4;
        if (var20.isEmpty()) {
            Vec3 var21 = TargetUtil.left(var16)
                .scale((var15.nextDouble() - 0.5) * 1.2)
                .add(TargetUtil.up(var16, var17).scale((var15.nextDouble() - 0.5) * 1.2));
            var4 = var0.getEyePosition().add(var0.getLookAngle().scale(var7)).add(var14 ? Vec3.ZERO : var21);
        } else {
            Entity var5 = ((TargetUtil.Hit)var20.get(0)).entity();
            float var3 = var5.getBbWidth();
            float var31 = var5.getBbHeight();
            var4 = var14
                ? TargetUtil.center(var5)
                : TargetUtil.center(var5)
                    .add(TargetUtil.left(var16).scale((var15.nextDouble() - 0.5) * (double)var3))
                    .add(TargetUtil.up(var16, var17).scale((var15.nextDouble() - 0.5) * (double)var31 * 0.7));
        }

        Vec3 var32 = var4.subtract(var18).normalize();
        float var30 = var6;
        Consumer<Entity> var23 = var5x -> {
            if (var5x.isAlive()) {
                TargetUtil.hurt(var5x, var19, var30);
                if (var14 || var2 % 2 == 0) {
                    impactSound(var0, TargetUtil.center(var5x), var14 ? 0.9F : 0.6F);
                }
            }
        };
        if (var14) {
            FlyingSlash.Volley var24 = new FlyingSlash.Volley(4);

            for (float var28 : new float[]{45.0F, -45.0F}) {
                float var29 = var28 + (var15.nextFloat() - 0.5F) * 8.0F;
                FlyingSlash.launch(var0, var24, var18, var32, var16, var17, var29, var13, 1, var9 + 0.4, var7, TerrainCutter.forSlash(var0, var6, var11), var23);
            }

            var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), ModSounds.slash(), SoundSource.PLAYERS, 2.0F, 1.4F);
        } else {
            float var33 = (var2 % 2 == 0 ? 45.0F : -45.0F) + (var15.nextFloat() - 0.5F) * 20.0F;
            FlyingSlash.launch(
                var0,
                new FlyingSlash.Volley(2),
                var18,
                var32,
                var16,
                var17,
                var33,
                var13,
                0,
                var9 + 0.4,
                var7,
                TerrainCutter.forSlash(var0, var6, var11),
                var23
            );
        }

        var0.swing(InteractionHand.MAIN_HAND, true);
        var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), ModSounds.slash(), SoundSource.PLAYERS, 2.0F, 2.0F);
    }
}
