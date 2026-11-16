package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.king.KingGridEcho;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.system.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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

public final class GridSlashSkill implements Skill {
    private static final int TAP_TICKS = 5;
    private static final int MODE_CHARGE = 0;
    private static final int MODE_LOCKED = 1;

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        var2.mode = 0;
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        int var3 = var2.heldTicks(var1.now);
        if (var3 >= 5) {
            ServerPlayer var4 = var1.player;
            int var5 = Math.max(4, var1.integer("charge.ticks_per_stage", 20));
            JsonArray var6 = var1.array("charge.chant");
            int var7 = var6.size();
            if (!var2.charging) {
                var2.charging = true;
                var2.lastPulse = var1.now;
                Effects.chargeAura(var4, 0, var5 + 6);
            }

            int var8;
            if ((var8 = var1.integer("charge.slowness", 3)) >= 0) {
                var4.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, var8, false, false));
            }

            DismantleSkill.bodyParticles(var4, var2.burst ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, 1 + var2.stage);
            int var9 = Math.min(var7, (var3 - 5) / var5);
            if (var2.stage < var9 && var2.mode == 0) {
                if (var1.consume(var1.cost(var1.num("charge.cost_per_stage", 0.0)))) {
                    var2.stage++;
                    Chant.say(
                        var4,
                        var6.get(var2.stage - 1).getAsString(),
                        var2.stage,
                        var7,
                        var1.num("charge.chant_audible_radius", 16.0),
                        var1.bool("charge.chant_in_chat", true)
                    );
                    Effects.chargeAura(var4, var2.stage, var5 + 6);
                    var2.lastPulse = var1.now;
                    DismantleSkill.bodyParticles(var4, ParticleTypes.CRIT, 8 + var2.stage * 6);
                    if (var2.stage >= var7) {
                        DismantleSkill.chargeComplete(var4);
                        var2.burst = true;
                    }
                } else {
                    var2.mode = 1;
                }
            }

            if (var1.now - var2.lastPulse >= (long)var5) {
                Effects.chargeAura(var4, var2.stage, var5 + 6);
                var2.lastPulse = var1.now;
            }
        }
    }

    @Override
    public void onRelease(SkillContext var1, HeldState var2) {
        int var3 = var1.array("charge.chant").size();
        int var4 = var2.charging ? var2.stage : 0;
        boolean var5 = var3 > 0 && var4 >= var3;
        if (var5 && var1.player.isShiftKeyDown() && var1.param("storm") != null) {
            if (var1.modeUnlocked("storm")) {
                this.fireStorm(var1, var4);
                return;
            }

            Msg.actionBar(
                var1.player, Msg.tr("skill.mode_locked", var1.str("storm.name", "十字・千刃"), var1.modeUnlockLevel("storm")).withStyle(ChatFormatting.GRAY)
            );
        }

        if (var5 && !var1.player.isShiftKeyDown() && var1.modeUnlocked("giant")) {
            this.fireGiant(var1, var4);
        } else {
            this.fireGrid(var1, var4, var3, var2.burst && var5);
        }
    }

    private void fireGiant(SkillContext var1, int var2) {
        if (!var1.consume(var1.cost() * var1.num("giant.cost_multiplier", 4.0))) {
            Chant.clear(var1.player);
        } else {
            GiantGridFlight.launch(var1, var1.damage(var1.arrayNum("charge.stage_multipliers", var2, 1.0) * 1.1));
            var1.player.swing(InteractionHand.MAIN_HAND, true);
            var1.player
                .level()
                .playSound(null, var1.player.getX(), var1.player.getY(), var1.player.getZ(), ModSounds.slash(), SoundSource.PLAYERS, 1.4F, 0.65F);
            Chant.clear(var1.player);
            Msg.actionBar(
                var1.player,
                Msg.tr("skill.grid.giant", (int)var1.num("giant.size", 192.0), (int)var1.num("giant.range", 192.0)).withStyle(ChatFormatting.DARK_RED)
            );
            var1.cooldown(Math.max(1, var1.integer("giant.cooldown_ticks", 60)));
        }
    }

    private void fireGrid(SkillContext var1, int var2, int var3, boolean var4) {
        ServerPlayer var5 = var1.player;
        if (!var1.consume(var1.cost())) {
            if (var2 > 0) {
                Chant.clear(var5);
            }
        } else {
            RandomSource var6 = var5.getRandom();
            double var7 = 1.0 + (double)var2 * var1.num("grid.size_per_stage", 0.7);
            double var9 = var1.num("grid.size", 10.0) * var7;
            int var11 = Math.max(2, var1.integer("grid.lines", 4) + (int)Math.round((double)var2 * var1.num("grid.lines_per_stage", 1.0)));
            double var12 = var1.arrayNum("charge.stage_multipliers", var2, 1.0) * (var4 ? 1.1 : 1.0);
            float var14 = var1.damage(var12);
            double var15 = var1.num("range", 32.0) * (1.0 + (double)var2 * var1.num("flight.range_per_stage", 0.2));
            double var17 = var1.num("flight.speed", 1.8) + (double)var2 * var1.num("flight.speed_per_stage", 0.3);
            int var19 = Math.max(1, var1.integer("grid.pierce", 64));
            int var20 = 1 + (int)Math.round((double)var2 * var1.num("grid.cut_lines_per_stage", 0.5));
            double var21 = var1.num("grid.terrain_budget_scale", 0.6) * (1.0 + (double)var2 * var1.num("grid.terrain_per_stage", 0.8));
            var21 = Math.max(
                var21,
                Math.min(32.0, var9 * var15 * (double)Math.max(1, var20) / (double)Math.max(1, (Integer)ModConfigs.SERVER.terrainMaxBlocksPerSlash.get()))
            );
            int var23 = var2 >= Math.max(1, var3 - 1) ? 1 : 0;
            float var24 = 1.0F + (float)var2 * 0.25F;
            DamageSource var25 = ModDamageTypes.technique(var5);
            Consumer<Entity> var26 = var4x -> {
                if (var4x.isAlive()) {
                    TargetUtil.hurt(var4x, var25, var14);
                    DismantleSkill.impactSound(var5, TargetUtil.center(var4x), var24);
                }
            };
            float var27 = var5.getYRot();
            float var28 = var5.getXRot();
            Vec3 var29 = DismantleSkill.muzzle(var5, var6, 0.0);
            Vec3 var30 = DismantleSkill.aimDir(var5, var29, var15);
            Vec3 var31 = TargetUtil.left(var27).scale(-1.0);
            Vec3 var32 = TargetUtil.up(var27, var28);
            Vec3 var33 = var29.add(var30.scale(var1.num("grid.forward_offset", 1.5)));
            FlyingSlash.Volley var34 = new FlyingSlash.Volley(var19);
            float var35 = (float)var9;

            for (int var36 = 0; var36 < var11; var36++) {
                double var37 = ((double)var36 / (double)(var11 - 1) - 0.5) * var9;
                Vec3 var39 = var33.add(var32.scale(var37));
                Vec3 var40 = var33.add(var31.scale(var37));
                FlyingSlash.Spec var41 = new FlyingSlash.Spec(var39, var30, var27, var28, 0.0F, var35, var23, var17, var15)
                    .cutLines(var20, 0.8)
                    .onTerrainHit(var2x -> DismantleSkill.impactSound(var5, var2x, var24 * 0.5F));
                FlyingSlash.Spec var42 = new FlyingSlash.Spec(var40, var30, var27, var28, 90.0F, var35, var23, var17, var15)
                    .cutLines(var20, 0.8)
                    .onTerrainHit(var2x -> DismantleSkill.impactSound(var5, var2x, var24 * 0.5F));
                FlyingSlash.launch(var5, var34, var41, TerrainCutter.forSlash(var5, var14, var21), var26);
                FlyingSlash.launch(var5, var34, var42, TerrainCutter.forSlash(var5, var14, var21), var26);
            }

            if (var3 > 0 && var2 >= var3 && KingRules.active(var1.data)) {
                SkillManager.schedule(
                    var5,
                    6,
                    () -> {
                        if (KingRules.active(var1.data)) {
                            FlyingSlash.Volley var20x = new FlyingSlash.Volley(var19);

                            for (int var21x = 0; var21x < var11; var21x++) {
                                double var22 = ((double)var21x / (double)(var11 - 1) - 0.5) * var9;
                                Vec3 var24x = var31.add(var32).normalize();
                                Vec3 var25x = var32.subtract(var31).normalize();
                                FlyingSlash.Spec var26x = new FlyingSlash.Spec(
                                    var33.add(var25x.scale(var22)), var30, var27, var28, 45.0F, var35, var23, var17, var15
                                );
                                FlyingSlash.Spec var27x = new FlyingSlash.Spec(
                                    var33.add(var24x.scale(var22)), var30, var27, var28, -45.0F, var35, var23, var17, var15
                                );
                                Consumer<Entity> var28x = var2xx -> {
                                    if (var2xx.isAlive()) {
                                        TargetUtil.hurt(var2xx, var25, var14 * 0.3F);
                                    }
                                };
                                FlyingSlash.launch(var5, var20x, var26x, null, var28x);
                                FlyingSlash.launch(var5, var20x, var27x, null, var28x);
                            }
                        }
                    }
                );
            }

            var5.swing(InteractionHand.MAIN_HAND, true);
            float var44 = 0.6F * (1.0F + 0.25F * (float)var2);

            for (float var47 : new float[]{0.65F, 1.0F}) {
                var5.level().playSound(null, var5.getX(), var5.getY(), var5.getZ(), ModSounds.slash(), SoundSource.PLAYERS, var44, var47);
            }

            Msg.actionBar(
                var5,
                Msg.tr("skill.grid.fire", var1.entry.name(), var11, var11, String.format(Locale.ROOT, "%.0f", var9))
                    .withStyle(var23 == 1 ? ChatFormatting.DARK_RED : ChatFormatting.RED)
            );
            if (var2 > 0) {
                Chant.clear(var5);
            }

            var1.cooldown();
        }
    }

    private void fireStorm(SkillContext var1, int var2) {
        ServerPlayer var3 = var1.player;
        if (!var1.consume(var1.cost() * var1.num("storm.cost_multiplier", 8.0))) {
            Chant.clear(var3);
        } else {
            RandomSource var4 = var3.getRandom();
            double var5 = var1.num("storm.target_range", 40.0);
            double var7 = var1.num("storm.radius", 20.0);
            List var9 = TargetUtil.ray(var3, var5, 1, 1.0);
            Vec3 var10 = var9.isEmpty() ? TargetUtil.rayEnd(var3, var5) : TargetUtil.center(((TargetUtil.Hit)var9.get(0)).entity());
            ArrayList var11 = new ArrayList();
            if (!var9.isEmpty() && TargetUtil.living(((TargetUtil.Hit)var9.get(0)).entity()) != null) {
                var11.add(TargetUtil.living(((TargetUtil.Hit)var9.get(0)).entity()));
            }

            AABB var12 = new AABB(var10, var10).inflate(var7);

            for (Entity var14 : var3.level().getEntities(var3, var12, var1x -> TargetUtil.valid(var3, var1x))) {
                LivingEntity var15 = TargetUtil.living(var14);
                if (var15 != null && !var11.contains(var15) && TargetUtil.center(var15).distanceTo(var10) <= var7) {
                    var11.add(var15);
                }
            }

            int var40 = Math.max(1, var1.integer("storm.count", 240));
            int var41 = Math.max(1, var1.integer("storm.duration_ticks", 60));
            int var42 = (int)Math.ceil((double)var40 / (double)var41);
            double var16 = var1.num("storm.distance", 8.0);
            double var18 = var1.num("storm.distance_random", 5.0);
            float var20 = (float)var1.num("storm.length", 10.0);
            double var21 = var1.num("storm.speed", 3.0);
            double var23 = var1.arrayNum("charge.stage_multipliers", var2, 1.0) * var1.num("storm.coefficient_scale", 0.3);
            float var25 = var1.damage(var23);
            KingGridEcho.storm(var1, var25);
            double var26 = Math.max(0.5, var1.num("storm.terrain_budget_scale", 0.5));
            int var28 = Math.max(1, var1.integer("storm.pierce", 8));
            double var29 = var1.num("storm.empty_spread", 6.0);
            DamageSource var31 = ModDamageTypes.technique(var3);
            int[] var32 = new int[]{0};
            int[] var33 = new int[]{0};
            HashMap<Integer, Integer> var34 = new HashMap<>();
            int var35 = Math.max(1, var1.integer("storm.max_hits_per_target", 6));
            Consumer<Entity> var36 = var6 -> {
                if (var6.isAlive()) {
                    int var7x = var34.getOrDefault(var6.getId(), 0);
                    if (var7x >= var35) {
                        return;
                    }

                    var34.put(var6.getId(), var7x + 1);
                    TargetUtil.hurt(var6, var31, var25);
                    int var8 = var33[0]++;
                    if (var8 % 4 == 0) {
                        DismantleSkill.impactSound(var3, TargetUtil.center(var6), 0.9F);
                    }
                }
            };
            Msg.actionBar(
                var3,
                Msg.tr("skill.grid.storm", var1.str("storm.name", "十字・千刃"), var40, var11.size())
                    .withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD})
            );
            var3.swing(InteractionHand.MAIN_HAND, true);
            var3.level().playSound(null, var10.x, var10.y, var10.z, ModSounds.critical(), SoundSource.PLAYERS, 2.0F, 0.5F);
            Vec3 var37 = var10;

            for (int var38 = 0; var38 < var41; var38++) {
                int var39 = var38;
                SkillManager.schedule(
                    var3,
                    var38,
                    () -> {
                        for (int var22 = 0; var22 < var42 && var32[0] < var40; var22++) {
                            LivingEntity var23x = null;
                            if (!var11.isEmpty()) {
                                for (int var24 = 0; var24 < var11.size(); var24++) {
                                    LivingEntity var25x = (LivingEntity)var11.get((var32[0] + var24) % var11.size());
                                    if (var25x.isAlive() && !var25x.isRemoved()) {
                                        var23x = var25x;
                                        break;
                                    }
                                }
                            }

                            Vec3 var26x;
                            float var47;
                            float var48;
                            if (var23x != null) {
                                var26x = TargetUtil.center(var23x);
                                var48 = var23x.getBbWidth();
                                var47 = var23x.getBbHeight();
                            } else {
                                var26x = var37.add(
                                    (var4.nextDouble() - 0.5) * var29 * 2.0, (var4.nextDouble() - 0.3) * var29, (var4.nextDouble() - 0.5) * var29 * 2.0
                                );
                                var48 = 1.0F;
                                var47 = 1.0F;
                            }

                            var26x = var26x.add(
                                (var4.nextDouble() - 0.5) * (double)var48,
                                (var4.nextDouble() - 0.5) * (double)var47 * 0.6,
                                (var4.nextDouble() - 0.5) * (double)var48
                            );
                            double var27 = var4.nextDouble() * Math.PI * 2.0;
                            double var29x = (var4.nextDouble() - 0.5) * 1.0;
                            Vec3 var31x = new Vec3(Math.cos(var27), var29x, Math.sin(var27)).normalize();
                            double var32x = var16 + var4.nextDouble() * var18;
                            Vec3 var34x = var26x.subtract(var31x.scale(var32x));
                            float var35x = (float)Math.toDegrees(Math.atan2(-var31x.x, var31x.z));
                            float var36x = (float)Math.toDegrees(-Math.atan2(var31x.y, Math.sqrt(var31x.x * var31x.x + var31x.z * var31x.z)));
                            float var37x = var20 * (0.8F + var4.nextFloat() * 0.6F);
                            float var38x = (var4.nextFloat() - 0.5F) * 30.0F;
                            int var39x = var4.nextInt(3) == 0 ? 1 : 0;
                            FlyingSlash.Volley var40x = new FlyingSlash.Volley(var28);

                            for (float var44 : new float[]{45.0F, -45.0F}) {
                                FlyingSlash.Spec var45 = new FlyingSlash.Spec(
                                        var34x, var31x, var35x, var36x, var44 + var38x, var37x, var39x, var21, var32x * 2.0
                                    )
                                    .cutLines(1, 0.8)
                                    .throughWalls(true);
                                FlyingSlash.launch(var3, var40x, var45, TerrainCutter.forSlash(var3, var25, var26), var36);
                            }

                            var32[0]++;
                        }

                        if (var39 % 4 == 0) {
                            Vec3 var46 = var11.isEmpty() ? var37 : TargetUtil.center((Entity)var11.get(var39 % var11.size()));
                            var3.level()
                                .playSound(null, var46.x, var46.y, var46.z, ModSounds.slash(), SoundSource.PLAYERS, 2.5F, 0.6F + var4.nextFloat() * 0.9F);
                        }

                        if (var39 % 20 == 0) {
                            var3.level()
                                .playSound(null, var37.x, var37.y, var37.z, ModSounds.explode(), SoundSource.PLAYERS, 2.0F, 0.5F + var4.nextFloat() * 0.3F);
                        }
                    }
                );
            }

            Chant.clear(var3);
            var1.cooldown(Math.max(var1.entry.cooldownTicks(), var1.integer("storm.cooldown_ticks", 240)));
        }
    }
}
