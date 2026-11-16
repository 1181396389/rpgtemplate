package com.rpgtemplate.forge.skill;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.possession.Possession;
import com.rpgtemplate.forge.possession.SkillBorrow;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public final class SkillManager {
    public static final int SLOTS = 12;
    public static final String CD_PREFIX = "cd:";
    public static final String KEY_SEALED_UNTIL = "sealed_until";
    private static final int MAX_HOLD_TICKS = 600;
    private static final Map<UUID, HeldState> HELD = new ConcurrentHashMap<>();
    private static final Map<UUID, List<SkillManager.Task>> TASKS = new ConcurrentHashMap<>();

    private SkillManager() {
    }

    public static void onInput(ServerPlayer var0, int var1, boolean var2) {
        if (!Possession.interceptSkillInput(var0, var1, var2)) {
            ServerPlayer var3 = Possession.lenderFor(var0);
            if (var3 != null) {
                SkillBorrow.run(var0, var3, SystemDataManager.raw(var3), () -> onInput0(var0, var1, var2));
            } else {
                onInput0(var0, var1, var2);
            }
        }
    }

    private static void onInput0(ServerPlayer var0, int var1, boolean var2) {
        if (var1 >= 0 && var1 < 12 && !var0.isSpectator() && var0.isAlive()) {
            if (var2) {
                press(var0, var1);
            } else {
                release(var0, var1);
            }
        }
    }

    private static void press(ServerPlayer var0, int var1) {
        UUID var3 = var0.getUUID();
        PlayerSystemData var2;
        if (!HELD.containsKey(var3) && (var2 = SystemDataManager.get(var0)).isActivated()) {
            if (var2.loadedTemplateId() == null) {
                Msg.actionBar(var0, Msg.tr("skill.no_template").withStyle(ChatFormatting.GRAY));
            } else {
                SkillContext var4 = SkillContext.create(var0, var1);
                if (var4 == null) {
                    Msg.actionBar(var0, Msg.tr("skill.empty_slot", var1 + 1).withStyle(ChatFormatting.GRAY));
                } else {
                    if (KingRules.blocked(var0, var4.entry.id())) {
                        return;
                    }

                    Skill var5 = SkillRegistry.get(var4.entry.id()).orElse(null);
                    if (var5 == null) {
                        Msg.actionBar(var0, Msg.tr("skill.unimplemented", var4.entry.name()).withStyle(ChatFormatting.GRAY));
                    } else if (Possession.lendBlocked(var0, var4.entry.id())) {
                        Msg.actionBar(var0, Msg.tr("possession.lend.blocked", var4.entry.name()).withStyle(ChatFormatting.GRAY));
                    } else if (!var4.entry.unlockedAt(var4.inst.level())) {
                        Msg.actionBar(var0, Msg.tr("skill.locked", var4.entry.name(), var4.entry.unlockLevel()).withStyle(ChatFormatting.GRAY));
                    } else {
                        long var6 = var4.now;
                        double var8 = var2.value("sealed_until", 0.0);
                        if ((double)var6 < var8) {
                            Msg.actionBar(var0, Msg.tr("skill.sealed", seconds(var8 - (double)var6)).withStyle(ChatFormatting.DARK_RED));
                        } else if (DomainExpansionSkill.burnedOut(var2, var6) && !var4.bool("burnout_exempt", false)) {
                            Msg.actionBar(
                                var0,
                                Msg.tr("skill.burnout", var4.entry.name(), seconds(var2.value("burnout_until", 0.0) - (double)var6))
                                    .withStyle(ChatFormatting.DARK_RED)
                            );
                        } else {
                            double var10 = var2.value("cd:" + var4.entry.id(), 0.0);
                            if ((double)var6 < var10 && !var5.bypassCooldown(var4)) {
                                Msg.actionBar(var0, Msg.tr("skill.cooldown", var4.entry.name(), seconds(var10 - (double)var6)).withStyle(ChatFormatting.GRAY));
                            } else if (var5.holdable()) {
                                HeldState var12 = new HeldState(var1, var4.entry.id(), var6, var0.isShiftKeyDown());
                                HELD.put(var3, var12);
                                var5.onPress(var4, var12);
                                if (var12.finished()) {
                                    HELD.remove(var3);
                                }
                            } else {
                                var5.onPress(var4, null);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void release(ServerPlayer var0, int var1) {
        UUID var2 = var0.getUUID();
        HeldState var3 = HELD.get(var2);
        if (var3 != null && var3.slot == var1) {
            HELD.remove(var2);
            if (!var3.finished()) {
                SkillContext var4 = SkillContext.create(var0, var1);
                if (var4 != null && var4.entry.id().equals(var3.skillId)) {
                    SkillRegistry.get(var3.skillId).ifPresent(var2x -> var2x.onRelease(var4, var3));
                } else {
                    Chant.clear(var0);
                }
            }
        }
    }

    public static void tick(ServerPlayer var0) {
        ServerPlayer var1 = Possession.lenderFor(var0);
        if (var1 != null) {
            SkillBorrow.run(var0, var1, SystemDataManager.raw(var1), () -> tick0(var0));
        } else {
            tick0(var0);
        }
    }

    private static void tick0(ServerPlayer var0) {
        UUID var2 = var0.getUUID();
        long var3 = var0.serverLevel().getGameTime();
        DomainExpansionSkill.tick(var0);
        List var5 = TASKS.get(var2);
        if (var5 != null) {
            ArrayList<SkillManager.Task> var6 = new ArrayList<>();
            synchronized (var5) {
                Iterator var9 = var5.iterator();

                while (var9.hasNext()) {
                    SkillManager.Task var10 = (SkillManager.Task)var9.next();
                    if (var3 >= var10.runAt()) {
                        var6.add(var10);
                        var9.remove();
                    }
                }
            }

            for (SkillManager.Task var14 : var6) {
                if (var0.isAlive() && !var0.isRemoved()) {
                    var14.action().run();
                }
            }
        }

        HeldState var1;
        if ((var1 = HELD.get(var2)) != null) {
            SkillContext var13 = SkillContext.create(var0, var1.slot);
            if (var13 != null
                && var13.entry.id().equals(var1.skillId)
                && var0.isAlive()
                && !((double)var3 < SystemDataManager.get(var0).value("sealed_until", 0.0))) {
                Object var7 = SkillRegistry.get(var1.skillId).orElse(null);
                if (var7 == null) {
                    HELD.remove(var2);
                } else if (var1.heldTicks(var3) > 600) {
                    HELD.remove(var2);
                    ((Skill)var7).onRelease(var13, var1);
                } else {
                    ((Skill)var7).onHoldTick(var13, var1);
                    if (var1.finished()) {
                        HELD.remove(var2);
                    }
                }
            } else {
                HELD.remove(var2);
                Chant.clear(var0);
            }
        }
    }

    public static void schedule(ServerPlayer var0, int var1, Runnable var2) {
        long var3 = var0.serverLevel().getGameTime() + (long)Math.max(0, var1);
        if (var1 <= 0) {
            var2.run();
        } else {
            List var5;
            List var7 = var5 = TASKS.computeIfAbsent(var0.getUUID(), var0x -> new ArrayList<>());
            synchronized (var7) {
                var5.add(new SkillManager.Task(var3, var2));
            }
        }
    }

    public static void cancel(ServerPlayer var0) {
        HELD.remove(var0.getUUID());
        TASKS.remove(var0.getUUID());
        Chant.clear(var0);
    }

    public static void forget(UUID var0) {
        DomainTerrainSweep.clear(var0);
        HELD.remove(var0);
        TASKS.remove(var0);
    }

    public static HeldState heldState(ServerPlayer var0) {
        return HELD.get(var0.getUUID());
    }

    public static boolean isHolding(ServerPlayer var0) {
        return HELD.containsKey(var0.getUUID());
    }

    public static void seal(ServerPlayer var0, int var1) {
        PlayerSystemData var2 = SystemDataManager.get(var0);
        DomainExpansionSkill.end(var0, "recalled");
        var2.setValue("sealed_until", (double)(var0.serverLevel().getGameTime() + (long)var1));
        cancel(var0);
        SystemDataManager.markDirty(var0);
    }

    private static String seconds(double var0) {
        return String.format(Locale.ROOT, "%.1f", var0 / 20.0);
    }

    private static record Task(long runAt, Runnable action) {
    }
}
