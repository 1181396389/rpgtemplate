package com.rpgtemplate.core.skill;

import com.rpgtemplate.core.data.PlayerSystemData;

public final class KingMath {
    public static boolean active(PlayerSystemData var0) {
        return var0 != null && var0.isActivated() && "rpgtemplate:sukuna_20_fingers".equals(var0.loadedTemplateId());
    }

    public static KingMath.Healing healing(double var0, double var2, double var4, double var6) {
        if (Double.isFinite(var0)
            && Double.isFinite(var2)
            && Double.isFinite(var4)
            && Double.isFinite(var6)
            && !(var2 <= 0.0)
            && !(var6 < 0.0)
            && !(var4 < var6 * 0.15)
            && !(var0 >= var2)) {
            double var8 = Math.min(Math.max(0.0, var2 - var0), var2 * 0.015);
            double var10 = Math.max(1.0, var6 * 0.0075) * var8 / (var2 * 0.015);
            return var4 + 1.0E-9 < var10 ? new KingMath.Healing(0.0, 0.0) : new KingMath.Healing(var8, var10);
        } else {
            return new KingMath.Healing(0.0, 0.0);
        }
    }

    public static double domainRefund(double var0, double var2, double var4, double var6) {
        return Math.max(0.0, var0) * 0.3 * Math.max(0.0, Math.min(1.0, (var4 - var6) / Math.max(1.0, var4 - var2)));
    }

    public static record Healing(double hp, double mp) {
    }
}
