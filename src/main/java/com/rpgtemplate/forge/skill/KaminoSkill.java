package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.ext.FugaArrowEntity;
import com.rpgtemplate.forge.ext.FugaImpact;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

public final class KaminoSkill implements Skill {
    public static final String ID = "rpgtemplate:kamino";
    private static final Map<UUID, FugaArrowEntity> HELD = new ConcurrentHashMap<>();
    public static volatile FugaArrowEntity lastLaunched;

    @Override
    public boolean holdable() {
        return true;
    }

    public static boolean openMode(SkillContext var0) {
        return var0.bool("open.enabled", true) && DomainExpansionSkill.active(var0.data, var0.now);
    }

    public static FugaArrowEntity heldArrow(ServerPlayer var0) {
        return HELD.get(var0.getUUID());
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        boolean var4 = openMode(var1);
        var2.charging = true;
        var2.lastPulse = var1.now;
        var2.mode = var4 ? 1 : 0;
        FugaArrowEntity var5 = HELD.remove(var3.getUUID());
        if (var5 != null && !var5.isRemoved() && var5.state() == 0) {
            var5.discard();
        }

        FugaArrowEntity var6 = FugaArrowEntity.held(var3, var4);
        var3.level().addFreshEntity(var6);
        HELD.put(var3.getUUID(), var6);
        Effects.chargeAura(var3, 0, 12);
        var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F);
        Chant.say(var3, var1.str("chant_name", "竈"), 1, var4 ? 2 : 1, var1.num("audible_radius", 24.0), true);
        Msg.actionBar(var3, Msg.tr(var4 ? "skill.kamino.charging_open" : "skill.kamino.charging").withStyle(var4 ? ChatFormatting.GOLD : ChatFormatting.RED));
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        boolean var4 = openMode(var1);
        FugaArrowEntity var5 = HELD.get(var3.getUUID());
        if (var5 == null || var5.isRemoved()) {
            var5 = FugaArrowEntity.held(var3, var4);
            var3.level().addFreshEntity(var5);
            HELD.put(var3.getUUID(), var5);
        }

        if (var4 != (var2.mode == 1)) {
            var2.mode = var4 ? 1 : 0;
            var5.setOpen(var4);
            if (var4) {
                var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
                Msg.actionBar(var3, Msg.tr("skill.kamino.charging_open").withStyle(ChatFormatting.GOLD));
            }
        }

        int var6 = Math.max(1, var4 ? var1.integer("open.charge_ticks", var1.integer("charge_ticks", 30)) : var1.integer("charge_ticks", 30));
        int var7 = var2.heldTicks(var1.now);
        var5.setCharge((float)var7 / (float)var6);
        if (var1.heian() == null) {
            var3.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false));
        }

        ServerLevel var8 = var3.serverLevel();
        Vec3 var9 = FugaArrowEntity.handPos(var3, 1.0F);
        int var10 = 1 + var7 * 6 / var6;
        var8.sendParticles(var4 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, var9.x, var9.y, var9.z, var10, 0.3, 0.3, 0.3, 0.02);
        if (var7 % 4 == 0) {
            var8.sendParticles(ParticleTypes.LAVA, var9.x, var9.y, var9.z, 1, 0.1, 0.1, 0.1, 0.0);
        }

        if (var1.now - var2.lastPulse >= 10L) {
            var2.lastPulse = var1.now;
            Effects.chargeAura(var3, Math.min(3, var7 * 3 / var6), 14);
            var3.level()
                .playSound(
                    null,
                    var3.getX(),
                    var3.getY(),
                    var3.getZ(),
                    SoundEvents.FIRE_AMBIENT,
                    SoundSource.PLAYERS,
                    1.0F,
                    0.8F + 0.6F * Math.min(1.0F, (float)var7 / (float)var6)
                );
        }

        if (var7 >= var6 && var2.stage == 0) {
            var2.stage = 1;
            var2.burst = true;
            var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.7F);
            var8.sendParticles(ParticleTypes.LAVA, var9.x, var9.y, var9.z, 12, 0.3, 0.3, 0.3, 0.0);
            Msg.actionBar(var3, Msg.tr(var4 ? "skill.kamino.ready_open" : "skill.kamino.ready").withStyle(var4 ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
    }

    @Override
    public void onRelease(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        FugaArrowEntity var4 = HELD.remove(var3.getUUID());
        boolean var5 = var2.mode == 1 && openMode(var1);
        if (var2.stage < 1) {
            discard(var4);
            Chant.clear(var3);
            Msg.actionBar(var3, Msg.tr("skill.kamino.cancelled").withStyle(ChatFormatting.GRAY));
        } else {
            double var6 = StatCalculator.maxStat(StatType.HP, var1.data, TemplateRegistry.get(), CoreConfig.get());
            double var8 = Math.max(0.0, var6 * (var5 ? var1.num("open.hp_cost_ratio", var1.num("hp_cost_ratio", 0.0)) : var1.num("hp_cost_ratio", 0.0)));
            if (var1.data.hp() <= var8 + 1.0) {
                discard(var4);
                Chant.clear(var3);
                Msg.actionBar(var3, Msg.tr("skill.kamino.no_hp").withStyle(ChatFormatting.RED));
            } else {
                double var10 = var1.cost() * (var5 ? var1.num("open.cost_multiplier", 1.5) : 1.0);
                if (!var1.consume(var10)) {
                    discard(var4);
                    Chant.clear(var3);
                } else {
                    if (var8 > 0.0) {
                        var1.data.setHp(var1.data.hp() - var8);
                        SystemDataManager.markDirty(var3);
                    }

                    if (var5) {
                        List var12 = chant(var1);
                        Object var13 = var12.size() > 1 ? var12.get(var12.size() - 1) : "開";
                        Chant.say(var3, (String)var13, 2, 2, var1.num("audible_radius", 24.0) * 2.0, true);
                    }

                    var1.cooldown();
                    var3.swing(InteractionHand.MAIN_HAND, true);
                    float var21 = var1.damage(1.0) * (float)(var5 ? var1.num("open.damage_multiplier", 3.0) : 1.0);
                    FugaImpact.Spec var22 = FugaImpact.Spec.read(var1, var5, var21);
                    if (var5) {
                        KingRules.boostFlame(var1, var22);
                    }

                    double var14 = Math.max(0.5, var5 ? var1.num("open.speed", 3.5) : var1.num("speed", 3.0));
                    double var16 = Math.max(4.0, var5 ? var1.num("open.range", 96.0) : var1.num("range", 64.0));
                    if (var4 == null || var4.isRemoved()) {
                        var4 = FugaArrowEntity.held(var3, var5);
                        var3.level().addFreshEntity(var4);
                    }

                    Vec3 var18 = FugaArrowEntity.handPos(var3, 1.0F);
                    var4.setPos(var18.x, var18.y, var18.z);
                    Vec3 var19 = DismantleSkill.aimDir(var3, var18, var16).normalize();
                    var4.launch(var19, var14, var16, var22);
                    lastLaunched = var4;
                    ServerLevel var20 = var3.serverLevel();
                    var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.5F);
                    var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0F, 0.6F);
                    if (var5) {
                        var3.level().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2F, 1.4F);
                    }

                    var20.sendParticles(ParticleTypes.FLAME, var18.x, var18.y, var18.z, var5 ? 60 : 30, 0.2, 0.2, 0.2, 0.15);
                    Msg.actionBar(
                        var3,
                        Msg.tr(var5 ? "skill.kamino.fired_open" : "skill.kamino.fired", String.format("%.1f", var21))
                            .withStyle(var5 ? ChatFormatting.GOLD : ChatFormatting.RED)
                    );
                }
            }
        }
    }

    private static void discard(FugaArrowEntity var0) {
        if (var0 != null && !var0.isRemoved() && var0.state() == 0) {
            var0.discard();
        }
    }

    private static List<String> chant(SkillContext var0) {
        JsonArray var1 = var0.array("open.chant");
        ArrayList var2 = new ArrayList();
        if (var1 != null) {
            for (JsonElement var4 : var1) {
                var2.add(var4.getAsString());
            }
        }

        if (var2.isEmpty()) {
            var2.add("竈");
            var2.add("開");
        }

        return var2;
    }
}
