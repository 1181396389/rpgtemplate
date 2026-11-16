package com.rpgtemplate.forge.skill;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.skill.SkillFormula;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ReverseCursedTechniqueSkill implements Skill {
    public static final String WOUND_KEY = "hp_max_penalty";
    private static final int MODE_SELF = 0;
    private static final int MODE_OUTPUT = 1;

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        var2.mode = var2.sneakAtPress && var1.param("output") != null ? 1 : 0;
        var2.lastPulse = -4611686018427387904L;
        var1.player.playNotifySound((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
        this.onHoldTick(var1, var2);
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        int var3 = pulse(var1);
        if (var1.now - var2.lastPulse >= (long)var3) {
            var2.lastPulse = var1.now;
            if (var2.mode == 1) {
                this.output(var1, var2);
            } else {
                this.self(var1, var2);
            }
        }
    }

    private static int pulse(SkillContext var0) {
        return Math.max(1, var0.integer("pulse_ticks", 5));
    }

    private void self(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        double var4 = StatCalculator.maxStat(StatType.HP, var1.data, TemplateRegistry.get(), CoreConfig.get());
        boolean var6 = var1.bool("heal_vanilla_health", true);
        boolean var7 = var1.data.hp() >= var4 - 1.0E-6;
        boolean var8 = !var6 || var3.getHealth() >= var3.getMaxHealth();
        double var9 = var1.data.value("hp_max_penalty", 0.0);
        boolean var11 = var9 > 0.0 && var1.modeUnlocked("soul_repair");
        if (var7 && var8 && !hasCleansable(var3) && !var11) {
            Msg.actionBar(var3, Msg.tr("skill.rct_full").withStyle(ChatFormatting.GRAY));
        } else {
            double var15 = RctFatigue.factor(var1.data, var1.now);
            double var13 = KingRules.active(var1.data) && var1.data.hp() < var4 * 0.25 ? 1.25 : 1.0;
            if (!var1.consume(var1.cost() * var13 / var15)) {
                var2.finish();
            } else {
                double var19 = var1.heal(var1.entry.coefficient()) * var13 / var15;
                var1.data.setHp(Math.min(var4, var1.data.hp() + var19));
                if (var6) {
                    var3.heal((float)SkillFormula.toVanilla(var19, (Double)ModConfigs.SERVER.damageConversion.get()));
                }

                if (var11) {
                    double var21 = StatCalculator.maxStatRaw(StatType.HP, var1.data, TemplateRegistry.get(), CoreConfig.get());
                    double var23 = var21 * Math.max(0.0, var1.num("soul_repair.ratio_per_pulse", 0.01)) / var15;
                    double var25 = var9 - var23;
                    if (var25 <= 1.0E-6) {
                        var1.data.removeValue("hp_max_penalty");
                        Msg.actionBar(var3, Msg.tr("skill.rct.soul_repaired").withStyle(ChatFormatting.AQUA));
                    } else {
                        var1.data.setValue("hp_max_penalty", var25);
                    }

                    SystemDataManager.markDirty(var3);
                }

                cleanse(var3, var1.integer("cleanse_ticks_per_pulse", 20));
                afterPulse(var1, var15);
                Effects.rctAura(var3, var3, pulse(var1), wispCount(var1, var15));
            }
        }
    }

    private void output(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        Vec3 var4 = touchCenter(var3, var1.num("output.reach", 1.4));
        double var5 = var1.num("output.radius", 1.2);
        List<LivingEntity> var7 = touchTargets(var3, var4, var5);
        Effects.rctAuraAt(var3, var4, 0.3F, 0.3F, pulse(var1), Math.max(1, pulse(var1) / 2));
        if (var7.isEmpty()) {
            Msg.actionBar(var3, Msg.tr("skill.rct_no_touch").withStyle(ChatFormatting.GRAY));
        } else {
            boolean var8 = var1.bool("output.damage_undead", true);
            ArrayList<LivingEntity> var9 = new ArrayList<>();

            for (LivingEntity var11 : var7) {
                if (isUndead(var11) ? var8 : var11.getHealth() < var11.getMaxHealth() || hasCleansable(var11)) {
                    var9.add(var11);
                }
            }

            if (var9.isEmpty()) {
                Msg.actionBar(var3, Msg.tr("skill.rct_target_full").withStyle(ChatFormatting.GRAY));
            } else {
                double var21 = RctFatigue.factor(var1.data, var1.now);
                if (!var1.consume(var1.cost() / var21)) {
                    var2.finish();
                } else {
                    double var12 = var1.num("output.heal_ratio", 0.7);
                    double var14 = var1.num("output.undead_max_health_ratio", 0.03);
                    int var16 = var1.integer("cleanse_ticks_per_pulse", 20);

                    for (LivingEntity var18 : var9) {
                        if (isUndead(var18)) {
                            float var19 = (float)((double)var1.damage(var1.entry.coefficient() * var12, 1.0) / var21 + (double)var18.getMaxHealth() * var14);
                            TargetUtil.hurt(var18, ModDamageTypes.technique(var3), var19);
                            if (var18.isAlive() && !var18.isDeadOrDying()) {
                                Effects.rctAura(var3, var18, pulse(var1), wispCount(var1, var21));
                            } else {
                                purify(var3, var18);
                            }
                        } else {
                            double var22 = var1.heal(var1.entry.coefficient()) * var12 / var21;
                            var18.heal((float)SkillFormula.toVanilla(var22, (Double)ModConfigs.SERVER.damageConversion.get()));
                            cleanse(var18, var16);
                            Effects.rctAura(var3, var18, pulse(var1), wispCount(var1, var21));
                        }
                    }

                    afterPulse(var1, var21);
                }
            }
        }
    }

    public static Vec3 touchCenter(ServerPlayer var0, double var1) {
        return var0.getEyePosition().add(var0.getLookAngle().scale(var1)).add(0.0, -0.25, 0.0);
    }

    public static List<LivingEntity> touchTargets(ServerPlayer var0, Vec3 var1, double var2) {
        double var4 = var2 * var2;
        return var0.serverLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(var1, var1).inflate(var2),
                var4x -> var4x != var0
                        && var4x.isAlive()
                        && !var4x.isSpectator()
                        && !(var4x instanceof ArmorStand)
                        && distSqr(var4x.getBoundingBox(), var1) <= var4
            );
    }

    private static double distSqr(AABB var0, Vec3 var1) {
        double var2 = Math.max(Math.max(var0.minX - var1.x, var1.x - var0.maxX), 0.0);
        double var4 = Math.max(Math.max(var0.minY - var1.y, var1.y - var0.maxY), 0.0);
        double var6 = Math.max(Math.max(var0.minZ - var1.z, var1.z - var0.maxZ), 0.0);
        return var2 * var2 + var4 * var4 + var6 * var6;
    }

    public static boolean isUndead(LivingEntity var0) {
        return var0.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "undead"))) || var0.isInvertedHealAndHarm();
    }

    private static void afterPulse(SkillContext var0, double var1) {
        int var3 = pulse(var0);
        RctFatigue.add(var0.data, var0.now, var0.num("fatigue_per_tick", 6.0) * (double)var3);
        var0.data.setValue("rct.active_until", (double)(var0.now + (long)var3 + 4L));
        SystemDataManager.markDirty(var0.player);
    }

    private static int wispCount(SkillContext var0, double var1) {
        return Math.max(1, (int)Math.round((double)pulse(var0) * var0.num("wisp_density", 1.0) / var1));
    }

    private static void purify(ServerPlayer var0, LivingEntity var1) {
        Effects.rctBurst(var0, var1);
        var0.level()
            .playSound(
                null, var1.getX(), var1.getY() + (double)var1.getBbHeight() * 0.5, var1.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.4F
            );
    }

    public static boolean hasCleansable(LivingEntity var0) {
        for (MobEffectInstance var2 : var0.getActiveEffects()) {
            if (cleansable(var2)) {
                return true;
            }
        }

        return false;
    }

    private static boolean cleansable(MobEffectInstance var0) {
        return var0.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && var0.getDuration() >= 0 && var0.getDuration() != Integer.MAX_VALUE;
    }

    public static void cleanse(LivingEntity var0, int var1) {
        if (var1 > 0) {
            for (MobEffectInstance var3 : new ArrayList<>(var0.getActiveEffects())) {
                if (cleansable(var3)) {
                    int var4 = var3.getDuration() - var1;
                    var0.removeEffect(var3.getEffect());
                    if (var4 > 0) {
                        var0.addEffect(new MobEffectInstance(var3.getEffect(), var4, var3.getAmplifier(), var3.isAmbient(), var3.isVisible(), var3.showIcon()));
                    }
                }
            }
        }
    }
}
