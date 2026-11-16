package com.rpgtemplate.forge.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, "rpgtemplate");
    public static final DeferredHolder<SoundEvent, SoundEvent> JJC_SWORD_SWEEP = register("jjc.sword_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> JJC_CRUSH = register("jjc.crush");
    public static final DeferredHolder<SoundEvent, SoundEvent> JJC_CRITICAL = register("jjc.critical");
    public static final DeferredHolder<SoundEvent, SoundEvent> JJC_WIND_CHIME = register("jjc.wind_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> JJC_SLOW_MOTION_END = register("jjc.slow_motion_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> JJC_PIANO_HORROR = register("jjc.piano_horror");
    public static final boolean HAS_JJC = ModSounds.class.getResource("/assets/rpgtemplate/sounds/jjc/sword_sweep.ogg") != null;

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("rpgtemplate", name)));
    }

    public static SoundEvent slash() {
        return HAS_JJC ? JJC_SWORD_SWEEP.get() : SoundEvents.PLAYER_ATTACK_SWEEP;
    }

    public static SoundEvent crush() {
        return HAS_JJC ? JJC_CRUSH.get() : SoundEvents.PLAYER_ATTACK_CRIT;
    }

    public static SoundEvent critical() {
        return HAS_JJC ? JJC_CRITICAL.get() : SoundEvents.PLAYER_ATTACK_CRIT;
    }

    public static SoundEvent explode() {
        return SoundEvents.GENERIC_EXPLODE.value();
    }

    private ModSounds() {
    }
}
