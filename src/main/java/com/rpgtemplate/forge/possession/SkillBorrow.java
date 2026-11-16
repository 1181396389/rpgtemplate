package com.rpgtemplate.forge.possession;

import com.rpgtemplate.core.data.PlayerSystemData;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class SkillBorrow {
    private static final ThreadLocal<SkillBorrow.Scope> SCOPE = new ThreadLocal<>();

    private SkillBorrow() {
    }

    public static void run(ServerPlayer var0, ServerPlayer var1, PlayerSystemData var2, Runnable var3) {
        SkillBorrow.Scope var4 = SCOPE.get();
        SCOPE.set(new SkillBorrow.Scope(var0.getUUID(), var1, var2));

        try {
            var3.run();
        } finally {
            if (var4 == null) {
                SCOPE.remove();
            } else {
                SCOPE.set(var4);
            }
        }
    }

    public static boolean active() {
        return SCOPE.get() != null;
    }

    public static PlayerSystemData dataFor(UUID var0) {
        SkillBorrow.Scope var1 = SCOPE.get();
        return var1 != null && var1.borrower.equals(var0) ? var1.data : null;
    }

    public static UUID lenderOf(UUID var0) {
        SkillBorrow.Scope var1 = SCOPE.get();
        return var1 != null && var1.borrower.equals(var0) ? var1.lender.getUUID() : null;
    }

    private static record Scope(UUID borrower, ServerPlayer lender, PlayerSystemData data) {
    }
}
