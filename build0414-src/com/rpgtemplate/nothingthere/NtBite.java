package com.rpgtemplate.nothingthere;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 贪食之口（饕餮）：每次命中都会削减目标的最大生命，每层 −5%，最多 10 层（即 −50%）。
 * 10 秒（200 tick）没有再次命中则恢复。修饰符是临时的，不会永久保存在目标身上。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NtBite {
    static final int MAX_STACKS = 10;
    static final double PER_STACK = 0.05;
    static final int DURATION_TICKS = 200;
    private static final UUID MOD_ID = UUID.nameUUIDFromBytes("rpgtemplate:nt_bite".getBytes(StandardCharsets.UTF_8));

    private static final Map<LivingEntity, Bite> BITES = new WeakHashMap<>();

    private static final class Bite {
        int stacks;
        int ticksLeft;
    }

    private NtBite() {
    }

    /** 命中一次：层数 +1（不超过上限），刷新持续时间。 */
    static void hit(LivingEntity victim) {
        if (!victim.m_6084_()) {
            return;
        }
        Bite bite = BITES.computeIfAbsent(victim, k -> new Bite());
        bite.stacks = Math.min(MAX_STACKS, bite.stacks + 1);
        bite.ticksLeft = DURATION_TICKS;
        apply(victim, bite.stacks);
    }

    private static void apply(LivingEntity victim, int stacks) {
        AttributeInstance inst = victim.m_21051_(Attributes.f_22276_);
        if (inst == null) {
            return;
        }
        inst.m_22120_(MOD_ID);
        if (stacks > 0) {
            inst.m_22118_(new AttributeModifier(MOD_ID, "rpgtemplate:nt_bite", -PER_STACK * stacks,
                AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BITES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Bite>> it = BITES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Bite> entry = it.next();
            LivingEntity victim = entry.getKey();
            Bite bite = entry.getValue();
            if (!victim.m_6084_()) {
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
