package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.skill.RuleJudge;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "rpgtemplate")
public final class NtBite {
    static final int MAX_STACKS = 10;
    static final double PER_STACK = 0.05;
    static final int DURATION_TICKS = 200;
    private static final ResourceLocation MOD_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_bite");

    private static final Map<LivingEntity, Bite> BITES = new WeakHashMap<>();

    private static final class Bite {
        int stacks;
        int ticksLeft;
    }

    private NtBite() {
    }

    static void hit(ServerPlayer attacker, PlayerSystemData data, LivingEntity victim) {
        if (!victim.isAlive()) {
            return;
        }
        NtDrawbacks.markFed(attacker, data);
        RuleJudge.Result check = RuleJudge.evaluate(attacker, data, victim, StatType.SP);
        int cap = switch (check.tier()) {
            case FULL -> MAX_STACKS;
            case MAJOR -> 6;
            case MINOR -> 2;
            case FAIL -> 0;
        };
        if (cap <= 0) {
            RuleJudge.notify(attacker, victim, "贪食之口侵蚀", check);
            return;
        }
        Bite bite = BITES.computeIfAbsent(victim, k -> new Bite());
        bite.stacks = Math.min(cap, bite.stacks + 1);
        bite.ticksLeft = DURATION_TICKS;
        apply(victim, bite.stacks);
    }

    private static void apply(LivingEntity victim, int stacks) {
        AttributeInstance inst = victim.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) {
            return;
        }
        inst.removeModifier(MOD_ID);
        if (stacks > 0) {
            inst.addTransientModifier(new AttributeModifier(MOD_ID, -PER_STACK * stacks,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BITES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Bite>> it = BITES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Bite> entry = it.next();
            LivingEntity victim = entry.getKey();
            Bite bite = entry.getValue();
            if (!victim.isAlive()) {
                it.remove();
                continue;
            }
            bite.ticksLeft--;
            if (bite.ticksLeft <= 0) {
                apply(victim, 0);
                it.remove();
            }
        }
    }
}
