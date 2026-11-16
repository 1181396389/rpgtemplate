package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class NtNonHuman {
    static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_non_human_speed");
    private static final Map<UUID, Double> LAST_SPEED = new HashMap<>();

    private NtNonHuman() {
    }

    static double speedBonus(PlayerSystemData data) {
        if (data == null) {
            return 0.0;
        }
        Optional<TraitEntry> trait = NtPassives.active(data, NtPassives.NON_HUMAN);
        if (trait.isEmpty()) {
            return 0.0;
        }
        double perLevel = TraitLookup.param(trait.get(), "speed_per_level", 0.01);
        double raw = data.level() * perLevel * NtConfig.speedScale();
        return Math.max(0.0, Math.min(NtConfig.speedCap(), raw));
    }

    static int jumpAmplifier(PlayerSystemData data, TraitEntry trait) {
        double levelsPerAmp = TraitLookup.param(trait, "jump_levels_per_amp", 15.0);
        int max = (int) TraitLookup.param(trait, "max_jump_amplifier", 2.0);
        int amp = levelsPerAmp <= 0.0 ? 0 : (int) (data.level() / levelsPerAmp);
        return Math.max(0, Math.min(max, amp));
    }

    static void syncSpeed(ServerPlayer player, PlayerSystemData data) {
        double target = speedBonus(data);
        UUID owner = player.getUUID();
        Double last = LAST_SPEED.get(owner);
        if (last != null && last == target) {
            return;
        }
        LAST_SPEED.put(owner, target);
        AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        inst.removeModifier(SPEED_ID);
        if (target > 0.0) {
            inst.addTransientModifier(new AttributeModifier(SPEED_ID, target,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
