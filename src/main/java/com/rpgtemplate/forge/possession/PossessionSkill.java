package com.rpgtemplate.forge.possession;

import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.skill.Chant;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class PossessionSkill implements Skill {
    @Override
    public boolean holdable() {
        return true;
    }

    @Override
    public void onPress(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        if (Possession.recOf(var3.getUUID()) != null) {
            Msg.actionBar(var3, Msg.tr("possession.busy").withStyle(ChatFormatting.GRAY));
            var2.finish();
        } else if (SkillBorrow.active()) {
            var2.finish();
        } else {
            var2.charging = true;
            int var4 = var1.integer("hold_ticks", 30);
            Chant.say(var3, var1.str("chant", "夺舍"), 1, 1, var1.num("audible_radius", 16.0), true);
            var3.serverLevel().playSound(null, var3.getX(), var3.getY(), var3.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.5F);
        }
    }

    @Override
    public void onHoldTick(SkillContext var1, HeldState var2) {
        ServerPlayer var3 = var1.player;
        int var4 = var2.heldTicks(var1.now);
        int var5 = var1.integer("hold_ticks", 30);
        ServerLevel var6 = var3.serverLevel();
        if (var4 % 3 == 0) {
            double var7 = Math.min(1.0, (double)var4 / (double)var5);
            var6.sendParticles(ParticleTypes.SOUL, var3.getX(), var3.getY() + 1.0, var3.getZ(), (int)(3.0 + 10.0 * var7), 0.4, 0.8, 0.4, 0.02);
            var6.sendParticles(ParticleTypes.SMOKE, var3.getX(), var3.getY() + 0.2, var3.getZ(), 4, 0.5, 0.1, 0.5, 0.01);
        }

        if (var4 % 10 == 0) {
            var3.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 3, false, false, false));
        }

        if (var4 >= var5) {
            var2.finish();
            Chant.clear(var3);
            if (!var1.consume(var1.cost())) {
                return;
            }

            if (KingRules.active(var1.player)) {
                var1.data.setValue("king.possession_paid", var1.cost());
                SystemDataManager.markDirty(var1.player);
            }

            if (Possession.startFinger(var1)) {
                var1.cooldown();
            }
        }
    }

    @Override
    public void onRelease(SkillContext var1, HeldState var2) {
        Chant.clear(var1.player);
        Msg.actionBar(var1.player, Msg.tr("possession.cancelled").withStyle(ChatFormatting.GRAY));
    }
}
