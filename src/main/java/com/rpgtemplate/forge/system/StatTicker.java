package com.rpgtemplate.forge.system;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.BlackFlash;
import com.rpgtemplate.forge.possession.Possession;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.trait.TraitEffects;
import com.rpgtemplate.forge.xp.XpEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class StatTicker {
    private StatTicker() {
    }

    public static void tick(ServerPlayer var0) {
        XpEvents.tickTrades(var0);
        PlayerSystemData var1 = SystemDataManager.get(var0);
        if (!var1.isActivated() && Possession.lenderFor(var0) != null) {
            SkillManager.tick(var0);
        }

        if (var1.isActivated()) {
            long var2 = var0.serverLevel().getGameTime();
            RuntimeState.State var4 = RuntimeState.of(var0.getUUID());
            TemplateRegistry var5 = TemplateRegistry.get();
            CoreConfig var6 = CoreConfig.get();
            SkillManager.tick(var0);
            double var7 = (Double)ModConfigs.SERVER.sprintSpCost.get();
            if (var7 > 0.0 && var0.isSprinting() && !var0.isCreative() && !var0.isSpectator()) {
                double var9 = StatCalculator.maxStat(StatType.SP, var1, var5, var6);
                var1.setSp(Math.max(0.0, var1.sp() - var9 * var7 / 20.0));
                var4.lastSpUseTick = var2;
                if (var1.sp() <= 0.0 && !var4.exhausted) {
                    var4.exhausted = true;
                    var0.setSprinting(false);
                    Msg.actionBar(var0, Msg.tr("stat.exhausted").withStyle(ChatFormatting.GRAY));
                }

                SystemDataManager.markDirty(var0);
            }

            if (var4.exhausted) {
                if (var0.isSprinting()) {
                    var0.setSprinting(false);
                }

                if (var2 % 10L == 0L) {
                    var0.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, true, false, true));
                }
            }

            if (var2 % 20L == 0L) {
                perSecond(var0, var1, var4, var2, var5, var6);
            }

            if (var2 % 4L == 0L) {
                SystemDataManager.flushIfDirty(var0);
            }
        }
    }

    private static void perSecond(ServerPlayer var0, PlayerSystemData var1, RuntimeState.State var2, long var3, TemplateRegistry var5, CoreConfig var6) {
        double var7 = StatCalculator.maxStat(StatType.HP, var1, var5, var6);
        double var9 = StatCalculator.maxStat(StatType.MP, var1, var5, var6);
        double var11 = StatCalculator.maxStat(StatType.SP, var1, var5, var6);
        boolean var13 = false;
        if (var1.hp() < var7 && var3 - var2.lastHurtTick >= (long)((Integer)ModConfigs.SERVER.hpRegenDelay.get()).intValue()) {
            var1.setHp(Math.min(var7, var1.hp() + var7 * (Double)ModConfigs.SERVER.hpRegen.get()));
            var13 = true;
        }

        if (var1.mp() < var9 && !SkillManager.isHolding(var0)) {
            double var14 = BlackFlash.mpRegenMultiplier(var1, var3);
            var1.setMp(Math.min(var9, var1.mp() + var9 * (Double)ModConfigs.SERVER.mpRegen.get() * var14));
            var13 = true;
        }

        if (var1.sp() < var11 && !var0.isSprinting() && var3 - var2.lastSpUseTick >= (long)((Integer)ModConfigs.SERVER.spRegenDelay.get()).intValue()) {
            var1.setSp(Math.min(var11, var1.sp() + var11 * (Double)ModConfigs.SERVER.spRegen.get()));
            var13 = true;
        }

        if (var2.exhausted && var1.sp() >= var11 * (Double)ModConfigs.SERVER.exhaustionRecover.get()) {
            var2.exhausted = false;
            var0.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            Msg.actionBar(var0, Msg.tr("stat.recovered").withStyle(ChatFormatting.GREEN));
        }

        double var22 = var1.value("hp_max_penalty", 0.0);
        if (var22 > 0.0) {
            double var16 = TraitLookup.loadedNegative(var1, "rpgtemplate:soul_incomplete")
                .map(var0x -> TraitLookup.param(var0x, "recover_per_second_ratio", 0.002))
                .orElse(1.0);
            double var18 = StatCalculator.maxStatRaw(StatType.HP, var1, var5, var6);
            double var20 = var22 - var18 * var16;
            if (var20 <= 1.0E-9) {
                var1.removeValue("hp_max_penalty");
            } else {
                var1.setValue("hp_max_penalty", var20);
            }

            var13 = true;
        }

        TraitEffects.perSecond(var0, var1);
        XpEvents.tickExplore(var0, var1);
        if (var13) {
            SystemDataManager.markDirty(var0);
        }
    }
}
