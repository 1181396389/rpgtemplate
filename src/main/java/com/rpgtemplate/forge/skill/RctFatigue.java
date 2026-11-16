package com.rpgtemplate.forge.skill;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.skill.KingMath;

public final class RctFatigue {
    public static final String KEY = "rct.fatigue";
    public static final String KEY_AT = "rct.fatigue_at";
    public static final String KEY_ACTIVE_UNTIL = "rct.active_until";
    public static final double DECAY_PER_TICK = 2.0;
    public static final double STEP_TICKS = 600.0;
    public static final double PER_STEP = 0.4;
    public static final double MAX_STEPS = 10.0;
    public static final double CAP = 6000.0;

    private RctFatigue() {
    }

    public static double ticks(PlayerSystemData var0, long var1) {
        if (KingMath.active(var0)) {
            return 0.0;
        } else {
            double var3 = var0.value("rct.fatigue", 0.0);
            if (var3 <= 0.0) {
                return 0.0;
            } else {
                double var5 = var0.value("rct.fatigue_at", (double)var1);
                return Math.max(0.0, var3 - Math.max(0.0, (double)var1 - var5) * 2.0);
            }
        }
    }

    public static double factor(double var0) {
        return 1.0 + Math.min(Math.max(0.0, var0) / 600.0, 10.0) * 0.4;
    }

    public static double factor(PlayerSystemData var0, long var1) {
        return factor(ticks(var0, var1));
    }

    public static double add(PlayerSystemData var0, long var1, double var3) {
        if (KingMath.active(var0)) {
            var0.removeValue("rct.fatigue");
            var0.removeValue("rct.fatigue_at");
            return 0.0;
        } else {
            double var5 = Math.min(6000.0, ticks(var0, var1) + Math.max(0.0, var3));
            var0.setValue("rct.fatigue", var5);
            var0.setValue("rct.fatigue_at", (double)var1);
            return var5;
        }
    }

    public static boolean active(PlayerSystemData var0, long var1) {
        return (double)var1 < var0.value("rct.active_until", 0.0);
    }
}
