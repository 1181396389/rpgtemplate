package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.skill.ChantScaling;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.ext.HeianForm;
import com.rpgtemplate.forge.ext.WorldRiftEntity;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.system.Msg;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public final class WorldSlashSkill implements Skill {
    public static final String ID = "rpgtemplate:world_slash";
    private static final int TAP_TICKS = 3;

    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        var2.stage = 0;
        var2.charging = false;
        var2.burst = false;
        ServerPlayer var3 = var1.player;
        var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.6F, 0.5F);
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        int var3 = var2.heldTicks(var1.now);
        if (var3 >= 3) {
            ServerPlayer var4 = var1.player;
            int var5 = Math.max(4, var1.integer("charge.ticks_per_stage", 14));
            JsonArray var6 = var1.array("charge.chant");
            int var7 = var6.size();
            if (!var2.charging) {
                var2.charging = true;
                var2.lastPulse = var1.now;
                Effects.chargeAura(var4, 0, var5 + 6);
            }

            int var8 = var1.integer("charge.slowness", 4);
            if (var8 >= 0) {
                var4.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, var8, false, false));
            }

            handSeal(var4, var2.stage, var7);
            int var9 = Math.min(var7, (var3 - 3) / var5);
            if (var2.stage < var9) {
                var2.stage++;
                String var10 = var6.get(var2.stage - 1).getAsString();
                Chant.say(var4, var10, var2.stage, var7, var1.num("charge.chant_audible_radius", 24.0), var1.bool("charge.chant_in_chat", true));
                Effects.chargeAura(var4, var2.stage, var5 + 6);
                var2.lastPulse = var1.now;
                if (var2.stage >= var7 && !var2.burst) {
                    var2.burst = true;
                    DismantleSkill.chargeComplete(var4);
                    Msg.actionBar(var4, Msg.tr("skill.world_slash.ready").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}));
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
        ServerPlayer var3 = var1.player;
        int var4 = var1.array("charge.chant").size();
        int var5 = var2.charging ? var2.stage : 0;
        boolean var6 = var4 <= 0 || var5 >= var4;
        TraitEntry var7 = var1.heian();
        double var8 = 1.0;
        boolean var10 = false;
        if (!var6) {
            if (!HeianForm.allowsChantSkip(var7)) {
                if (var5 > 0) {
                    Chant.clear(var3);
                }

                var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
                Msg.actionBar(var3, Msg.tr("skill.world_slash.fizzle", var5, var4).withStyle(ChatFormatting.GRAY));
                var1.cooldown(Math.max(1, var1.integer("fizzle_cooldown_ticks", 20)));
                return;
            }

            var8 = ChantScaling.skipPower(var5, var4, var1.num("skip.per_missing_stage", 0.2), var1.num("skip.min_power", 0.4));
            var10 = true;
        }

        double var11 = StatCalculator.maxStat(StatType.MP, var1.data, TemplateRegistry.get(), CoreConfig.get());
        double var13 = Math.max(var1.cost(), var11 * Math.max(0.0, Math.min(1.0, var1.num("max_mp_ratio", 0.25))));
        if (!var1.consume(var13)) {
            if (var5 > 0) {
                Chant.clear(var3);
            }
        } else {
            float var15 = var1.damage(var8);
            Vec3 var16 = var3.getLookAngle().normalize();
            Vec3 var17 = TargetUtil.left(var3.getYRot()).scale(-1.0);
            Vec3 var18 = TargetUtil.up(var3.getYRot(), var3.getXRot());
            boolean var19 = var1.bool("vertical_when_sneaking", true) && var3.isShiftKeyDown();
            Vec3 var20 = var19 ? var18 : var17;
            Vec3 var21 = var3.getEyePosition().add(var16.scale(0.6)).subtract(0.0, var19 ? 0.0 : 0.35, 0.0);
            WorldRiftEntity.Spec var22 = new WorldRiftEntity.Spec(
                clamp(var1.num("rift.range", 160.0), 8.0, 256.0),
                clamp(var1.num("rift.speed", 8.0), 1.0, 32.0),
                clamp(var1.num("rift.base_half", 1.5), 0.25, 16.0),
                clamp(var1.num("rift.spread", 0.45), 0.0, 2.0),
                clamp(var1.num("rift.max_half", 40.0), 0.5, 96.0),
                clamp(var1.num("rift.thickness", 1.0), 0.1, 15.0),
                clamp(var1.num("terrain.thickness", 1.0), 0.1, 15.0),
                Math.max(1, var1.integer("rift.pierce", 64)),
                clamp(var1.num("rift.player_damage_scale", 0.5), 0.0, 1.0),
                Math.max(4, var1.integer("rift.linger_ticks", 30)),
                var1.bool("terrain.enabled", true),
                Math.max(0, var1.integer("terrain.checks_per_tick", 8192)),
                Math.max(0, var1.integer("terrain.blocks_per_tick", 1024)),
                Math.max(0, var1.integer("terrain.max_blocks", 65536))
            );
            WorldRiftEntity var23 = WorldRiftEntity.spawn(var3, var21, var16, var20, var22, var15, (float)var8);
            if (!var10 && KingRules.active(var1.data)) {
                var23.kingRefund = var13 * 0.15;
            }

            releaseFx(var3, var21, var16, var20);
            if (var5 > 0) {
                Chant.clear(var3);
            }

            String var24 = String.format(Locale.ROOT, "%.0f", var8 * 100.0);
            Msg.actionBar(
                var3,
                (var10 ? Msg.tr("skill.world_slash.skip", var24) : Msg.tr("skill.world_slash.cast", String.format(Locale.ROOT, "%.0f", (double)var15)))
                    .withStyle(ChatFormatting.DARK_RED)
            );
            var1.cooldown();
        }
    }

    private static double clamp(double var0, double var2, double var4) {
        return Double.isFinite(var0) ? Math.max(var2, Math.min(var4, var0)) : var2;
    }

    private static void handSeal(ServerPlayer var0, int var1, int var2) {
        if ((var0.tickCount & 1) == 0) {
            ServerLevel var3 = var0.serverLevel();
            Vec3 var4 = var0.getLookAngle();
            Vec3 var5 = var0.getEyePosition().add(var4.scale(0.55)).subtract(0.0, 0.45, 0.0);
            double var6 = 0.55 - 0.12 * (double)Math.min(var1, Math.max(1, var2));
            Vec3 var8 = TargetUtil.left(var0.getYRot()).scale(-1.0);

            for (int var9 = 0; var9 < 6; var9++) {
                double var10 = (double)var0.tickCount * 0.35 + (double)var9 * Math.PI / 3.0;
                Vec3 var12 = var5.add(var8.scale(Math.cos(var10) * var6)).add(0.0, Math.sin(var10) * var6 * 0.6, 0.0);
                var3.sendParticles(
                    var1 >= var2 && var2 > 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, var12.x, var12.y, var12.z, 1, 0.0, 0.0, 0.0, 0.0
                );
            }
        }
    }

    private static void releaseFx(ServerPlayer var0, Vec3 var1, Vec3 var2, Vec3 var3) {
        ServerLevel var4 = var0.serverLevel();
        var0.swing(InteractionHand.MAIN_HAND, true);
        var0.swing(InteractionHand.OFF_HAND, true);
        var4.sendParticles(ParticleTypes.FLASH, var1.x, var1.y, var1.z, 1, 0.0, 0.0, 0.0, 0.0);
        var4.sendParticles(ParticleTypes.SONIC_BOOM, var1.x + var2.x * 2.0, var1.y + var2.y * 2.0, var1.z + var2.z * 2.0, 1, 0.0, 0.0, 0.0, 0.0);

        for (int var5 = -6; var5 <= 6; var5++) {
            Vec3 var6 = var1.add(var2.scale(1.5)).add(var3.scale((double)var5 * 0.35));
            var4.sendParticles(ParticleTypes.END_ROD, var6.x, var6.y, var6.z, 1, 0.0, 0.0, 0.0, 0.02);
        }

        for (float var8 : new float[]{0.5F, 0.75F, 1.0F}) {
            var4.playSound(null, var1.x, var1.y, var1.z, ModSounds.slash(), SoundSource.PLAYERS, 1.0F, var8);
        }
    }
}
