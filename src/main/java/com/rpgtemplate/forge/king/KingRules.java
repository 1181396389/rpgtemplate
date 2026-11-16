package com.rpgtemplate.forge.king;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.skill.KingMath;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.ext.FugaImpact;
import com.rpgtemplate.forge.possession.SkillBorrow;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.RctFatigue;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class KingRules {
    public static final String ID = "rpgtemplate:sukuna_20_fingers";
    public static final String AMP = "king.amp";
    public static final String BASKET = "king.basket";
    private static final Map<UUID, KingRules.Chain> CHAINS = new HashMap<>();

    public static boolean active(PlayerSystemData var0) {
        return KingMath.active(var0);
    }

    public static boolean active(Player var0) {
        return SystemDataManager.available(var0) && active(SystemDataManager.raw(var0));
    }

    public static double max(PlayerSystemData var0, StatType var1) {
        return StatCalculator.maxStat(var1, var0, TemplateRegistry.get(), CoreConfig.get());
    }

    public static void message(ServerPlayer var0, String var1) {
        var0.displayClientMessage(Component.literal(var1), true);
    }

    public static boolean defense(PlayerSystemData var0, String var1, long var2) {
        return active(var0) && var0.value(var1, 0.0) > (double)var2;
    }

    public static void refund(ServerPlayer var0, double var1) {
        if (active(var0) && Double.isFinite(var1) && !(var1 <= 0.0)) {
            PlayerSystemData var3 = SystemDataManager.raw(var0);
            var3.setMp(Math.min(max(var3, StatType.MP), var3.mp() + var1));
            SystemDataManager.markDirty(var0);
        }
    }

    public static boolean blocked(ServerPlayer var0, String var1) {
        if (SkillBorrow.lenderOf(var0.getUUID()) == null
            || !var1.equals("rpgtemplate:domain_amplification") && !var1.equals("rpgtemplate:hollow_wicker_basket")) {
            if (!active(var0)) {
                return false;
            } else {
                PlayerSystemData var2 = SystemDataManager.raw(var0);
                if (defense(var2, "king.amp", var0.serverLevel().getGameTime())
                    && Set.of(
                            "rpgtemplate:dismantle",
                            "rpgtemplate:cleave",
                            "rpgtemplate:grid_slash",
                            "rpgtemplate:kamino",
                            "rpgtemplate:world_slash",
                            "rpgtemplate:domain_expansion"
                        )
                        .contains(var1)) {
                    message(var0, "领域展延中不能施放生得术式，请先关闭展延。");
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            message(var0, "此技能需要二十指本体，不能借用。");
            return true;
        }
    }

    public static void echo(SkillContext var0, Entity var1, float var2) {
        if (active(var0.data)) {
            SkillManager.schedule(var0.player, 6, () -> {
                if (active(var0.player) && var1.isAlive() && !var1.isRemoved() && var1.level() == var0.player.level()) {
                    TargetUtil.hurt(var1, ModDamageTypes.technique(var0.player), var2);
                }
            });
        }
    }

    public static float cleaveScale(SkillContext var0, LivingEntity var1) {
        if (!active(var0.player)) {
            return 1.0F;
        } else {
            long var2 = var0.player.serverLevel().getGameTime();
            KingRules.Chain var4 = CHAINS.get(var0.player.getUUID());
            int var5 = var4 != null && var4.target.equals(var1.getUUID()) && var2 - var4.time <= 60L ? Math.min(5, var4.count + 1) : 0;
            CHAINS.put(var0.player.getUUID(), new KingRules.Chain(var1.getUUID(), var2, var5));
            return 1.0F + (float)var5 * 0.05F;
        }
    }

    public static boolean domainHurt(Entity var0, DamageSource var1, float var2) {
        return TargetUtil.hurt(var0, var1, var2);
    }

    public static void addEmbers(SkillContext var0, int var1) {
        if (var1 > 0 && active(var0.data)) {
            var0.data.setValue("king.embers", Math.min(1500.0, var0.data.value("king.embers", 0.0) + (double)var1));
        }
    }

    public static void boostFlame(SkillContext var0, FugaImpact.Spec var1) {
        if (active(var0.data)) {
            double var2 = var1.radius();
            var1.damage = (float)((double)var1.damage * (1.0 + Math.min(0.3, var0.data.value("king.embers", 0.0) * 2.0E-4)));
            var1.baseRadius = var2;
            var1.maxRadius = var2;
            var1.radiusK = 0.0;
            var0.data.removeValue("king.embers");
            SystemDataManager.markDirty(var0.player);
        }
    }

    public static void domainRefund(SkillContext var0) {
        if (active(var0.data)) {
            double var1 = var0.data.value("domain.until", 0.0);
            double var3 = var0.data.value("domain.started", (double)var0.now);
            double var5 = var0.data.value("king.domain_cost", 0.0);
            var0.data.removeValue("king.domain_cost");
            refund(var0.player, KingMath.domainRefund(var5, var3, var1, (double)var0.now));
        }
    }

    public static void possessionResolved(UUID var0, boolean var1) {
        MinecraftServer var2 = ServerLifecycleHooks.getCurrentServer();
        if (var2 != null) {
            ServerPlayer var3 = var2.getPlayerList().getPlayer(var0);
            if (var3 != null) {
                PlayerSystemData var4 = SystemDataManager.raw(var3);
                double var5 = var4.value("king.possession_paid", 0.0);
                var4.removeValue("king.possession_paid");
                if (!var1) {
                    refund(var3, var5 * 0.2);
                }

                SystemDataManager.markDirty(var3);
            }
        }
    }

    public static void stop(ServerPlayer var0, String var1, boolean var2) {
        PlayerSystemData var3 = SystemDataManager.raw(var0);
        if (!(var3.value(var1, 0.0) <= 0.0)) {
            var3.removeValue(var1);
            var3.removeValue(var1 + ".last");
            String var4 = var1.equals("king.basket") ? "rpgtemplate:hollow_wicker_basket" : "rpgtemplate:domain_amplification";
            var3.setValue("cd:" + var4, (double)(var0.serverLevel().getGameTime() + (long)(var1.equals("king.basket") ? 500 : 400)));
            SystemDataManager.markDirty(var0);
            if (var2) {
                message(var0, var1.equals("king.basket") ? "弥虚葛笼已解除；进入冷却。" : "领域展延已解除；进入冷却。");
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void reduce(LivingIncomingDamageEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && active(var1)) {
            PlayerSystemData var6 = SystemDataManager.raw(var1);
            long var3 = var1.serverLevel().getGameTime();
            float var5 = defenseDamageScale(
                defense(var6, "king.amp", var3), defense(var6, "king.basket", var3), ModDamageTypes.bypassesSystemHp(var0.getSource())
            );
            var0.setAmount(var0.getAmount() * var5);
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && SystemDataManager.available(var1)) {
            PlayerSystemData var16 = SystemDataManager.raw(var1);
            long var3 = var1.serverLevel().getGameTime();
            boolean var5 = active(var16) && var1.isAlive() && !var1.isSpectator();

            for (String var9 : new String[]{"king.amp", "king.basket"}) {
                double var10 = var16.value(var9, 0.0);
                if (!(var10 <= 0.0)) {
                    if (var5 && !(var10 <= (double)var3) && !(var16.value("sealed_until", 0.0) > (double)var3)) {
                        long var12 = (long)var16.value(var9 + ".last", (double)var3);
                        if (var3 - var12 >= 20L) {
                            double var14 = max(var16, StatType.MP) * (var9.equals("king.basket") ? 0.02 : 0.015);
                            if (var16.mp() + 1.0E-9 < var14) {
                                stop(var1, var9, true);
                            } else {
                                var16.setMp(var16.mp() - var14);
                                var16.setValue(var9 + ".last", (double)var3);
                                SystemDataManager.markDirty(var1);
                            }
                        }
                    } else {
                        stop(var1, var9, true);
                    }
                }
            }

            if (!var5) {
                CHAINS.remove(var1.getUUID());
            } else if (var1.tickCount % 20 == 0) {
                if (var16.value("rct.fatigue", 0.0) > 0.0) {
                    var16.removeValue("rct.fatigue");
                    var16.removeValue("rct.fatigue_at");
                    SystemDataManager.markDirty(var1);
                }

                HeldState var17 = SkillManager.heldState(var1);
                if ((var17 == null || !"rpgtemplate:reverse_cursed_technique".equals(var17.skillId)) && !RctFatigue.active(var16, var3)) {
                    KingMath.Healing var18 = KingMath.healing(var16.hp(), max(var16, StatType.HP), var16.mp(), max(var16, StatType.MP));
                    if (!(var18.hp() <= 0.0)) {
                        var16.setHp(var16.hp() + var18.hp());
                        var16.setMp(var16.mp() - var18.mp());
                        SystemDataManager.markDirty(var1);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerLoggedOutEvent var0) {
        CHAINS.remove(var0.getEntity().getUUID());
        if (var0.getEntity() instanceof ServerPlayer var1 && SystemDataManager.available(var1)) {
            stop(var1, "king.amp", false);
            stop(var1, "king.basket", false);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent var0) {
        CHAINS.clear();
    }

    public static float defenseDamageScale(boolean var0, boolean var1, boolean var2) {
        if (var2) {
            return 1.0F;
        } else if (var1) {
            return 0.4F;
        } else {
            return var0 ? 0.65F : 1.0F;
        }
    }

    private static record Chain(UUID target, long time, int count) {
    }
}
