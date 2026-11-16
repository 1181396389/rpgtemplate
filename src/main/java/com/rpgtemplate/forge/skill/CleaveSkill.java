package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.rpgtemplate.core.rule.RuleCheck;
import com.rpgtemplate.core.rule.RuleResult;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.entity.TechniqueEffectEntity;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModEntities;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.system.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CleaveSkill implements Skill {
    private static final int HIT_DELAY = 5;
    private static final int TAP_TICKS = 5;
    private static final int MODE_HOLD = 0;
    private static final int MODE_LOCKED = 1;
    private static final int MODE_CHANT = 3;

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        if (var1.modeUnlocked("sure_hit") && DomainExpansionSkill.active(var1.data, var1.now)) {
            this.sureHit(var1);
            var2.finish();
        } else {
            if (var2.sneakAtPress && var1.param("storm") != null && var1.array("charge.chant").size() > 0) {
                if (var1.modeUnlocked("storm")) {
                    var2.mode = 3;
                    return;
                }

                Msg.actionBar(var3, Msg.tr("skill.mode_locked", var1.str("storm.name", "捌・万象"), var1.modeUnlockLevel("storm")).withStyle(ChatFormatting.GRAY));
            }

            var2.mode = 0;
            var2.count = 0;
            var2.nextTick = var1.now + (long)Math.max(1, var1.integer("hold.windup_ticks", 8));
            this.singleStrike(var1);
        }
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        if (var2.mode != 3 && (var2.mode != 1 || !var2.charging)) {
            if (var2.mode == 0) {
                this.holdTick(var1, var2);
            }
        } else {
            this.chantTick(var1, var2);
        }
    }

    @Override
    public void onRelease(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        if (var2.mode != 3 && !var2.charging) {
            if (var2.count > 0) {
                Msg.actionBar(var3, Msg.tr("skill.cleave.hold_stop", var2.count + 1).withStyle(ChatFormatting.GRAY));
                var1.cooldown();
            }
        } else {
            Chant.clear(var3);
            if (var2.stage == 0) {
                this.singleStrike(var1);
            } else {
                List var6 = TargetUtil.ray(var3, var1.num("range", 4.0) * 1.5, 1, 0.6);
                Entity var4;
                LivingEntity var5;
                if (!var6.isEmpty() && (var5 = TargetUtil.living(var4 = ((TargetUtil.Hit)var6.get(0)).entity())) != null) {
                    double var7 = var1.arrayNum("charge.stage_multipliers", var2.stage, 1.0);
                    CleaveSkill.Strike var9 = evaluate(var1, var5, var7, true);
                    strike(var1, var9, var4, var5, 5, null);
                    swingSounds(var3);
                }

                var1.cooldown();
            }
        }
    }

    private void singleStrike(SkillContext var1) {
        ServerPlayer var2 = var1.player;
        List var3 = TargetUtil.ray(var2, var1.num("range", 4.0), 1, 0.6);
        if (var3.isEmpty()) {
            if (var1.param("hold") == null) {
                Msg.actionBar(var2, Msg.tr("skill.no_target").withStyle(ChatFormatting.GRAY));
            }
        } else {
            Entity var4 = ((TargetUtil.Hit)var3.get(0)).entity();
            LivingEntity var5 = TargetUtil.living(var4);
            if (var5 != null && var1.consume(var1.cost())) {
                CleaveSkill.Strike var6 = evaluate(var1, var5, 1.0, true);
                swingSounds(var2);
                strike(var1, var6, var4, var5, 5, null);
                Msg.actionBar(
                    var2,
                    Msg.tr(
                            "skill.rule_result",
                            var1.entry.name(),
                            Msg.tr("rule." + var6.result.name().toLowerCase(Locale.ROOT)).withStyle(color(var6.result)),
                            var1.data.level(),
                            var6.ruleLevel
                        )
                        .withStyle(ChatFormatting.WHITE)
                );
                if (var1.modeUnlocked("chain")) {
                    this.chain(var1, var6, var5);
                }

                var1.cooldown();
            }
        }
    }

    private static void swingSounds(ServerPlayer var0) {
        var0.swing(InteractionHand.MAIN_HAND, true);
        var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), ModSounds.slash(), SoundSource.PLAYERS, 2.0F, 1.0F);
        var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), ModSounds.slash(), SoundSource.PLAYERS, 2.0F, 1.25F);
    }

    private void holdTick(SkillContext var1, HeldState var2) {
        if (var1.param("hold") != null) {
            ServerPlayer var3 = var1.player;
            int var4 = var1.integer("hold.slowness", -1);
            if (var4 >= 0) {
                var3.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, var4, false, false));
            }

            if (var1.now >= var2.nextTick) {
                var2.nextTick = var1.now + (long)Math.max(1, var1.integer("hold.interval_ticks", 4));
                if (var2.count >= Math.max(1, var1.integer("hold.max_hits", 40))) {
                    Msg.actionBar(var3, Msg.tr("skill.cleave.hold_stop", var2.count + 1).withStyle(ChatFormatting.GRAY));
                    var1.cooldown();
                    var2.finish();
                } else {
                    List<TargetUtil.Hit> var5 = TargetUtil.ray(var3, var1.num("hold.range", 10.0), Math.max(1, var1.integer("hold.targets", 1)), 0.8);
                    if (!var5.isEmpty()) {
                        double var6 = var1.cost() * var1.num("hold.cost_multiplier", 0.5);
                        if (!var1.consume(var6)) {
                            Msg.actionBar(var3, Msg.tr("skill.cleave.hold_stop", var2.count + 1).withStyle(ChatFormatting.GRAY));
                            var1.cooldown();
                            var2.finish();
                        } else {
                            double var8 = var1.num("hold.damage_ratio", 0.45);
                            boolean var10 = var1.bool("hold.allow_execute", false);
                            double var11 = var1.num("hold.knockback", 0.7);
                            double var13 = var1.num("hold.knockback_up", 0.12);
                            int var15 = Math.max(0, var1.integer("hold.hit_delay_ticks", 2));

                            for (TargetUtil.Hit var17 : var5) {
                                Entity var18 = var17.entity();
                                LivingEntity var19 = TargetUtil.living(var18);
                                if (var19 != null) {
                                    CleaveSkill.Strike var20 = evaluate(var1, var19, var8, var10);
                                    Vec3 var21 = var19.position().subtract(new Vec3(var3.getX(), var3.getY(), var3.getZ()));
                                    var21 = new Vec3(var21.x, 0.0, var21.z);
                                    var21 = var21.lengthSqr() < 1.0E-6 ? new Vec3(var3.getLookAngle().x, 0.0, var3.getLookAngle().z) : var21;
                                    var21 = var21.normalize().scale(var11).add(0.0, var13, 0.0);
                                    strike(var1, var20, var18, var19, var15, var21);
                                }
                            }

                            var2.count++;
                            var3.swing(var2.count % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
                            var3.level()
                                .playSound(
                                    null,
                                    var3.getX(),
                                    var3.getY(),
                                    var3.getZ(),
                                    ModSounds.slash(),
                                    SoundSource.PLAYERS,
                                    1.4F,
                                    0.9F + var3.getRandom().nextFloat() * 0.5F
                                );
                            if (var2.count % 5 == 0) {
                                Msg.actionBar(var3, Msg.tr("skill.cleave.hold", var2.count + 1).withStyle(ChatFormatting.RED));
                            }
                        }
                    }
                }
            }
        }
    }

    private void chantTick(SkillContext var1, HeldState var2) {
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
            if ((var3 = var1.integer("charge.slowness", 3)) >= 0) {
                var5.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, var3, false, false));
            }

            DismantleSkill.bodyParticles(var5, ParticleTypes.CRIT, 2 + var2.stage * 2);
            int var9 = Math.min(var8, (var4 - 5) / var6);
            if (var2.stage < var9 && var2.mode == 3) {
                if (var1.consume(var1.cost(var1.num("charge.cost_per_stage", 0.0)))) {
                    var2.stage++;
                    Chant.say(
                        var5,
                        var7.get(var2.stage - 1).getAsString(),
                        var2.stage,
                        var8,
                        var1.num("charge.chant_audible_radius", 24.0),
                        var1.bool("charge.chant_in_chat", true)
                    );
                    Effects.chargeAura(var5, var2.stage, var6 + 6);
                    var2.lastPulse = var1.now;
                    DismantleSkill.bodyParticles(var5, ParticleTypes.CRIT, 10 + var2.stage * 8);
                    if (var2.stage >= var8) {
                        DismantleSkill.chargeComplete(var5);
                        this.fireStorm(var1);
                        var2.finish();
                        return;
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

    private void fireStorm(SkillContext var1) {
        ServerPlayer var3 = var1.player;
        if (!var1.consume(var1.cost() * var1.num("storm.cost_multiplier", 6.0))) {
            Chant.clear(var3);
        } else {
            RandomSource var4 = var3.getRandom();
            double var5 = var1.num("storm.target_range", 32.0);
            List var7 = TargetUtil.ray(var3, var5, 1, 1.0);
            Vec3 var8 = var7.isEmpty() ? TargetUtil.rayEnd(var3, var5) : ((TargetUtil.Hit)var7.get(0)).entity().position();
            double var9 = var1.num("storm.radius", 8.0);
            double var11 = var1.num("storm.depth", 3.0);
            double var13 = var1.num("storm.height", 10.0);
            AABB var15 = new AABB(var8.x - var9, var8.y - var11, var8.z - var9, var8.x + var9, var8.y + var13, var8.z + var9);
            int var16 = Math.max(1, var1.integer("storm.duration_ticks", 40));
            int var17 = Math.max(1, var1.integer("storm.count", 180));
            int var18 = Math.max(0, var1.integer("storm.flat_count", 60));
            int var19 = Math.max(1, var1.integer("storm.hit_interval_ticks", 4));
            double var20 = var1.num("storm.damage_ratio", 0.5);
            boolean var22 = var1.bool("storm.allow_execute", true);
            double var23 = var1.num("storm.knockback", 0.25);
            float var25 = (float)var1.num("storm.visual_size_min", 2.5);
            float var26 = (float)Math.max((double)var25, var1.num("storm.visual_size_max", 6.0));
            float var27 = var1.damage(var20);
            TerrainCutter var28 = TerrainCutter.forSlash(var3, var27, var1.num("storm.terrain_budget_scale", 6.0));
            double var29 = var1.num("storm.cut_box", 1.2);
            Msg.actionBar(
                var3,
                Msg.tr("skill.cleave.storm", var1.str("storm.name", "捌・万象"), var17)
                    .withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD})
            );
            var3.swing(InteractionHand.MAIN_HAND, true);
            var3.level().playSound(null, var8.x, var8.y, var8.z, ModSounds.critical(), SoundSource.PLAYERS, 2.0F, 0.6F);
            int var31 = (int)Math.ceil((double)var17 / (double)var16);
            int var32 = (int)Math.ceil((double)var18 / (double)var16);
            int[] var33 = new int[]{0, 0};

            for (int var2 = 0; var2 < var16; var2++) {
                int var34 = var2;
                SkillManager.schedule(
                    var3,
                    var2 + 1,
                    () -> {
                        for (int var29x = 0; var29x < var31 && var33[0] < var17; var29x++) {
                            Vec3 var28x = new Vec3(
                                var15.minX + var4.nextDouble() * (var15.maxX - var15.minX),
                                var8.y - var11 * 0.5 + var4.nextDouble() * var13 * 0.7,
                                var15.minZ + var4.nextDouble() * (var15.maxZ - var15.minZ)
                            );
                            float var30 = var25 + var4.nextFloat() * (var26 - var25);
                            boolean var31x = var4.nextInt(4) == 0;
                            cleaveAt(var3, var28x, var30, var30 * (0.6F + var4.nextFloat() * 0.6F), var31x ? 16719904 : 16777215, 24, 12 + var4.nextInt(8));
                            if (var28 != null && !var28.exhausted()) {
                                var28.cutBox(new AABB(var28x, var28x).inflate(var29));
                            }

                            var33[0]++;
                        }

                        for (int var36 = 0; var36 < var32 && var33[1] < var18; var36++) {
                            Vec3 var35x = new Vec3(
                                var15.minX + var4.nextDouble() * (var15.maxX - var15.minX),
                                var8.y + 0.1 + var4.nextDouble() * 2.5,
                                var15.minZ + var4.nextDouble() * (var15.maxZ - var15.minZ)
                            );
                            Effects.slash(
                                var3,
                                var35x,
                                var4.nextFloat() * 360.0F,
                                (var4.nextFloat() - 0.5F) * 60.0F,
                                var4.nextFloat() * 180.0F,
                                (float)(var9 * (0.5 + var4.nextDouble() * 0.8)),
                                0.25F,
                                var4.nextInt(3) == 0 ? 1 : 0,
                                10 + var4.nextInt(6)
                            );
                            var33[1]++;
                        }

                        if (var34 % 2 == 0) {
                            var3.level()
                                .playSound(
                                    null,
                                    var8.x + (var4.nextDouble() - 0.5) * var9,
                                    var8.y,
                                    var8.z + (var4.nextDouble() - 0.5) * var9,
                                    ModSounds.slash(),
                                    SoundSource.PLAYERS,
                                    2.5F,
                                    0.7F + var4.nextFloat() * 0.8F
                                );
                        }

                        if (var34 % var19 == 0) {
                            ArrayList<LivingEntity> var39 = new ArrayList<>();

                            for (Entity var33x : var3.level().getEntities(var3, var15, var1xx -> TargetUtil.valid(var3, var1xx))) {
                                LivingEntity var37 = TargetUtil.living(var33x);
                                if (var37 != null && !var39.contains(var37)) {
                                    var39.add(var37);
                                }
                            }

                            for (LivingEntity var41 : var39) {
                                CleaveSkill.Strike var38 = evaluate(var1, var41, var20, var22);
                                Vec3 var34x = var41.position().subtract(var8);
                                var34x = new Vec3(var34x.x, 0.0, var34x.z);
                                var34x = var34x.lengthSqr() < 1.0E-6 ? new Vec3(var4.nextDouble() - 0.5, 0.0, var4.nextDouble() - 0.5) : var34x;
                                var34x = var34x.normalize().scale(var23).add(0.0, 0.05, 0.0);
                                strike(var1, var38, var41, var41, 1, var34x);
                            }

                            var3.level()
                                .playSound(null, var8.x, var8.y, var8.z, ModSounds.explode(), SoundSource.PLAYERS, 2.0F, 0.6F + var4.nextFloat() * 0.3F);
                            var3.serverLevel().sendParticles(ParticleTypes.CRIT, var8.x, var8.y + 1.0, var8.z, 60, var9 * 0.5, 2.0, var9 * 0.5, 0.6);
                        }
                    }
                );
            }

            Chant.clear(var3);
            int var35 = var1.integer("storm.cooldown_ticks", 600);
            var1.cooldown(Math.max(var1.entry.cooldownTicks(), var35));
        }
    }

    static void cleaveAt(ServerPlayer var0, Vec3 var1, float var2, float var3, int var4, int var5, int var6) {
        TechniqueEffectEntity var7 = new TechniqueEffectEntity((EntityType<? extends TechniqueEffectEntity>)ModEntities.TECHNIQUE_EFFECT.get(), var0.level());
        var7.setPos(var1.x, var1.y - (double)var3 * 0.5, var1.z);
        var7.owner(var0.getUUID());
        var7.setup(1, var6, var2, var3, 0.0F, var4, Math.max(2, Math.min(24, var5)));
        var0.level().addFreshEntity(var7);
    }

    private void sureHit(SkillContext var1) {
        ServerPlayer var2 = var1.player;
        double var3 = var1.data.value("domain.radius", 20.0);
        List var5 = DomainExpansionSkill.targets(var2, DomainExpansionSkill.center(var1.data), var3);
        if (var5.isEmpty()) {
            Msg.actionBar(var2, Msg.tr("skill.no_target").withStyle(ChatFormatting.GRAY));
        } else if (var1.consume(var1.cost())) {
            int var6 = Math.max(1, var1.integer("sure_hit.max_targets", 3));
            var5.sort(Comparator.comparingDouble(LivingEntity::getHealth).reversed());
            swingSounds(var2);
            int var7 = Math.min(var6, var5.size());

            for (int var8 = 0; var8 < var7; var8++) {
                domainStrike(var1, (LivingEntity)var5.get(var8), 1.0, true, 5 + var8 * 3);
            }

            Msg.actionBar(var2, Msg.tr("skill.cleave.sure_hit", var1.str("sure_hit.name", "必中・捌"), var7).withStyle(ChatFormatting.DARK_RED));
            var1.cooldown();
        }
    }

    static void domainStrike(SkillContext var0, LivingEntity var1, double var2, boolean var4, int var5) {
        CleaveSkill.Strike var6 = evaluate(var0, var1, var2, var4);
        strike(var0, var6, var1, var1, var5, null, true);
    }

    private void chain(SkillContext var1, CleaveSkill.Strike var2, LivingEntity var3) {
        RuleResult var5 = parseResult(var1.str("chain.min_result", "MAJOR"), RuleResult.MAJOR);
        if (var2.result.ordinal() <= var5.ordinal()) {
            ServerPlayer var6 = var1.player;
            double var7 = Math.max(0.0, var1.num("chain.radius", 4.0));
            int var9 = Math.max(0, var1.integer("chain.max_targets", 2));
            if (!(var7 <= 0.0) && var9 != 0) {
                double var10 = Math.max(0.0, var1.num("chain.damage_ratio", 0.6));
                int var12 = Math.max(0, var1.integer("chain.delay_ticks", 3));
                boolean var13 = var1.bool("chain.allow_execute", true);
                ArrayList<LivingEntity> var14 = new ArrayList<>();
                AABB var15 = var3.getBoundingBox().inflate(var7);

                for (Entity var17 : var6.level().getEntities(var6, var15, var2x -> var2x != var3 && TargetUtil.valid(var6, var2x))) {
                    LivingEntity var4 = TargetUtil.living(var17);
                    if (var4 != null && var4 != var3 && !var14.contains(var4) && !((double)var4.distanceTo(var3) > var7)) {
                        var14.add(var4);
                    }
                }

                var14.sort(Comparator.comparingDouble(var1x -> var1x.distanceToSqr(var3)));
                int var20 = Math.min(var9, var14.size());

                for (int var21 = 0; var21 < var20; var21++) {
                    LivingEntity var19 = (LivingEntity)var14.get(var21);
                    CleaveSkill.Strike var18 = evaluate(var1, var19, var10, var13);
                    strike(var1, var18, var19, var19, 5 + var12 * (var21 + 1), null);
                }

                if (var20 > 0) {
                    Msg.actionBar(var6, Msg.tr("skill.cleave.chain", var20).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private static CleaveSkill.Strike evaluate(SkillContext var0, LivingEntity var1, double var2, boolean var4) {
        ServerPlayer var5 = var0.player;
        int var6 = TargetUtil.ruleLevel(var1);
        if (var1 instanceof ServerPlayer) {
            return new CleaveSkill.Strike(RuleResult.MINOR, var0.damage(var2), false, var6);
        } else {
            RuleResult var7 = RuleCheck.evaluate(var0.data.level(), var0.def.quality().ruleBonus(), var6, () -> var5.getRandom().nextDouble());
            double var8 = var1.getAttributeValue(Attributes.ARMOR) + var1.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            double var10 = (
                    (double)var0.damage(1.0) * (1.0 + var8 * var0.num("target_armor_toughness_ratio", 0.0))
                        + (double)var1.getMaxHealth() * var0.num("target_max_health_ratio", 0.0)
                )
                * var2;
            RuleResult var12 = var7 == RuleResult.FULL && !var4 ? RuleResult.MAJOR : var7;
            String var13 = "rule_check." + var12.name();
            String var14 = var0.str(var13 + ".effect", "multiplier");
            double var15 = var0.num(var13 + ".value", 1.0);
            boolean var17 = false;
            float dmgVal;
            switch (var14) {
                case "execute" -> {
                    var17 = true;
                    dmgVal = 2.1267647E37F;
                }
                case "max_health_ratio" -> dmgVal = (float)(var10 + (double)var1.getMaxHealth() * var15 * var2);
                default -> dmgVal = (float)(var10 * var15);
            }
            return new CleaveSkill.Strike(var7, dmgVal, var17, var6);
        }
    }

    private static void strike(SkillContext var0, CleaveSkill.Strike var1, Entity var2, LivingEntity var3, int var4, Vec3 var5) {
        strike(var0, var1, var2, var3, var4, var5, false);
    }

    private static void strike(SkillContext var0, CleaveSkill.Strike var1, Entity var2, LivingEntity var3, int var4, Vec3 var5, boolean var6) {
        ServerPlayer var7 = var0.player;

        Effects.cleave(var7, var3, switch (var1.result) {
            case FULL -> 14;
            case MAJOR -> 10;
            case MINOR -> 7;
            default -> 4;
        }, var1.execute);
        float var8 = var1.amount;
        boolean var9 = var1.execute;
        TerrainCutter var10 = TerrainCutter.forSlash(var7, Math.min(var1.amount, 1000000.0F), var0.num("terrain_budget_scale", 0.5));
        SkillManager.schedule(var7, var4, () -> {
            if (var10 != null) {
                double var9x = var9 ? 1.5 : 0.75;
                var10.cutBox(var3.getBoundingBox().inflate(var9x, 0.0, var9x).expandTowards(0.0, -0.5, 0.0).expandTowards(0.0, var9 ? 1.0 : 0.5, 0.0));
            }

            if (var2.isAlive()) {
                float var11 = var8 * (var6 ? 1.0F : KingRules.cleaveScale(var0, var3));
                if (var6) {
                    KingRules.domainHurt(var2, ModDamageTypes.technique(var7), var11);
                } else {
                    TargetUtil.hurt(var2, ModDamageTypes.technique(var7), var11);
                }

                if (var9 && var3.isAlive() && var2 != var3) {
                    TargetUtil.hurt(var3, ModDamageTypes.technique(var7), var8);
                }

                if (var5 != null && var3.isAlive()) {
                    var3.push(var5.x, var5.y, var5.z);
                    var3.hurtMarked = true;
                }

                burst(var7, var3, var9);
            }
        });
    }

    private static ChatFormatting color(RuleResult var0) {
        switch (var0) {
            case FULL:
                return ChatFormatting.DARK_RED;
            case MAJOR:
                return ChatFormatting.RED;
            case MINOR:
                return ChatFormatting.GOLD;
            default:
                return ChatFormatting.GRAY;
        }
    }

    private static RuleResult parseResult(String var0, RuleResult var1) {
        if (var0 == null) {
            return var1;
        } else {
            try {
                return RuleResult.valueOf(var0.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var3) {
                return var1;
            }
        }
    }

    private static void burst(ServerPlayer var0, LivingEntity var1, boolean var2) {
        Vec3 var3 = TargetUtil.center(var1);
        float var4 = var2 ? 1.6F : 1.0F;
        var0.level().playSound(null, var3.x, var3.y, var3.z, ModSounds.explode(), SoundSource.PLAYERS, 1.0F, 1.0F);
        var0.level().playSound(null, var3.x, var3.y, var3.z, ModSounds.crush(), SoundSource.PLAYERS, 0.6F * var4, 0.9F + var0.getRandom().nextFloat() * 0.2F);
        var0.serverLevel().sendParticles(ParticleTypes.CRIT, var3.x, var3.y, var3.z, (int)(15.0F * var4), 0.0, 0.0, 0.0, 0.4);
        var0.serverLevel().sendParticles(ParticleTypes.EXPLOSION, var3.x, var3.y, var3.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (var2) {
            var0.level().playSound(null, var3.x, var3.y, var3.z, ModSounds.critical(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static record Strike(RuleResult result, float amount, boolean execute, int ruleLevel) {
    }
}
