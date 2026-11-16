package com.rpgtemplate.forge.api;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.rule.RuleCheck;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.system.SystemActions;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class RpgTemplateApi {
    private RpgTemplateApi() {
    }

    public static boolean isSystemOwner(Player var0) {
        return SystemDataManager.get(var0).isActivated();
    }

    public static Optional<PlayerSystemData> getSnapshot(Player var0) {
        PlayerSystemData var1 = SystemDataManager.get(var0);
        return var1.isActivated() ? Optional.of(var1.copy()) : Optional.empty();
    }

    public static int getLevel(Player var0) {
        PlayerSystemData var1 = SystemDataManager.get(var0);
        return var1.isActivated() ? var1.level() : -1;
    }

    public static double getMaxStat(Player var0, StatType var1) {
        return StatCalculator.maxStat(var1, SystemDataManager.get(var0), TemplateRegistry.get(), CoreConfig.get());
    }

    public static void grantXp(ServerPlayer var0, double var1, ResourceLocation var3) {
        SystemActions.grantXp(var0, var1, var3);
    }

    public static int getRuleLevel(LivingEntity var0) {
        if (var0 instanceof Player var1) {
            PlayerSystemData var2 = SystemDataManager.get(var1);
            if (var2.isActivated()) {
                return var2.level();
            }
        }

        return RuleCheck.ruleLevelFromMaxHealth((double)var0.getMaxHealth());
    }
}
