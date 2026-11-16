package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.RuleJudge;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "rpgtemplate")
public final class NtFormEffects {
    static final int FORM_SPIKE = 2;
    static final int FORM_BITE = 3;
    static final int FORM_WHIP = 4;
    static final int FORM_MUSCLE = 5;

    static final double SPIKE_REFLECT = 0.25;
    static final double SPIKE_SHOT = 0.5;
    static final long SPIKE_COOLDOWN = 15L;

    static boolean reflecting;

    private static final Map<UUID, Integer> LAST_FORM = new HashMap<>();
    private static final Map<UUID, Long> LAST_SPIKE = new HashMap<>();
    private static final Map<LivingEntity, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        float absorb = -1.0F;
        MobEffectInstance resist;
    }

    private record Mod(Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
    }

    private static ResourceLocation modId(String path) {
        return ResourceLocation.fromNamespaceAndPath("rpgtemplate", path);
    }

    private static final List<Mod> WHIP_MODS = List.of(
        new Mod(Attributes.ENTITY_INTERACTION_RANGE, modId("nt_whip_reach"), 2.0, AttributeModifier.Operation.ADD_VALUE),
        new Mod(Attributes.ATTACK_SPEED, modId("nt_whip_attack_speed"), 1000.0, AttributeModifier.Operation.ADD_VALUE));

    private static final List<Mod> MUSCLE_MODS = List.of(
        new Mod(Attributes.MAX_HEALTH, modId("nt_muscle_max_health"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        new Mod(Attributes.ATTACK_DAMAGE, modId("nt_muscle_attack_damage"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        new Mod(Attributes.ATTACK_SPEED, modId("nt_muscle_attack_speed"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        new Mod(Attributes.MOVEMENT_SPEED, modId("nt_muscle_movement_speed"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        new Mod(Attributes.ARMOR, modId("nt_muscle_armor"), 2.0, AttributeModifier.Operation.ADD_VALUE),
        new Mod(Attributes.ARMOR_TOUGHNESS, modId("nt_muscle_armor_toughness"), 1.0, AttributeModifier.Operation.ADD_VALUE),
        new Mod(Attributes.KNOCKBACK_RESISTANCE, modId("nt_muscle_knockback_resistance"), 0.1, AttributeModifier.Operation.ADD_VALUE),
        new Mod(Attributes.LUCK, modId("nt_muscle_luck"), 1.0, AttributeModifier.Operation.ADD_VALUE));

    private NtFormEffects() {
    }

    static void syncAttributes(ServerPlayer player, int form) {
        UUID owner = player.getUUID();
        Integer last = LAST_FORM.get(owner);
        if (last != null && last == form) {
            return;
        }
        LAST_FORM.put(owner, form);
        apply(player, WHIP_MODS, form == FORM_WHIP);
        apply(player, MUSCLE_MODS, form == FORM_MUSCLE);
    }

    private static void apply(ServerPlayer player, List<Mod> mods, boolean on) {
        for (Mod mod : mods) {
            AttributeInstance inst = player.getAttribute(mod.attr());
            if (inst == null) {
                continue;
            }
            inst.removeModifier(mod.id());
            if (on) {
                inst.addTransientModifier(new AttributeModifier(mod.id(), mod.amount(), mod.op()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.invulnerableTime <= 0) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data != null && NtForms.current(data) == FORM_WHIP) {
            RuleJudge.Result check = RuleJudge.evaluate(attacker, data, victim, StatType.SP);
            switch (check.tier()) {
                case FULL, MAJOR -> victim.invulnerableTime = 0;
                case MINOR -> victim.invulnerableTime = victim.invulnerableTime / 2;
                case FAIL -> { }
            }
        }
    }

    static void onPlayerHit(ServerPlayer attacker, LivingEntity victim, float amount) {
        if (reflecting || amount <= 0.0F || !victim.isAlive()) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data != null && NtForms.current(data) == FORM_BITE) {
            NtBite.hit(attacker, data, victim);
        }
    }

    static void onPlayerHurt(ServerPlayer player, LivingEntity attacker, float amount) {
        if (reflecting || amount <= 0.0F || attacker == player || !attacker.isAlive()) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null || NtForms.current(data) != FORM_SPIKE) {
            return;
        }
        double selfMaxHp = StatCalculator.maxStat(StatType.HP, data, TemplateRegistry.get(), CoreConfig.get());
        float reflectCap = (float) Math.max(8.0, selfMaxHp * 0.35 + data.level() * 1.5);
        float reflected = Math.min((float) (amount * SPIKE_REFLECT), reflectCap);
        reflecting = true;
        try {
            TargetUtil.hurt(attacker, ModDamageTypes.technique(player), reflected);
        } finally {
            reflecting = false;
        }
        long now = player.level().getGameTime();
        UUID owner = player.getUUID();
        long last = LAST_SPIKE.getOrDefault(owner, Long.MIN_VALUE / 2);
        if (now - last >= SPIKE_COOLDOWN) {
            LAST_SPIKE.put(owner, now);
            float spikeDmg = Math.min((float) (amount * SPIKE_SHOT), reflectCap * 1.4f);
            HolleSpike.launchToward(player, attacker, spikeDmg);
        }
    }

    static void suspendDefenses(LivingEntity victim) {
        float absorb = victim.getAbsorptionAmount();
        MobEffectInstance resist = victim.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (absorb <= 0.0F && resist == null) {
            return;
        }
        Pending pending = PENDING.computeIfAbsent(victim, k -> new Pending());
        if (absorb > 0.0F && pending.absorb < 0.0F) {
            pending.absorb = absorb;
            victim.setAbsorptionAmount(0.0F);
        }
        if (resist != null && pending.resist == null) {
            pending.resist = new MobEffectInstance(resist);
            victim.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Pending> entry = it.next();
            LivingEntity victim = entry.getKey();
            Pending pending = entry.getValue();
            if (victim.isAlive()) {
                if (pending.absorb >= 0.0F) {
                    victim.setAbsorptionAmount(pending.absorb);
                }
                if (pending.resist != null && victim.getEffect(MobEffects.DAMAGE_RESISTANCE) == null) {
                    victim.addEffect(pending.resist);
                }
            }
            it.remove();
        }
    }
}
