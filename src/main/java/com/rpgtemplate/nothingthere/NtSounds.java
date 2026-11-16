package com.rpgtemplate.nothingthere;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 一无所有 goodbye 音效（取自用户提供的 yiwusuoy 音频）。
 * WINDUP：按下时播放（goodbye 段，约 1 秒）。
 * HIT：出击（落地斩击）时播放（命中音效，约 1.3 秒）。
 * HOLLE：Holle 发射骨刺时播放（约 1.7 秒）。
 * 音效 JSON 合并在 assets/rpgtemplate/sounds.json（见 pack_nt_jar.py）。
 */
@EventBusSubscriber(modid = "rpgtemplate", bus = EventBusSubscriber.Bus.MOD)
public final class NtSounds {
    public static final ResourceLocation WINDUP_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_goodbye_windup");
    public static SoundEvent WINDUP;
    public static final ResourceLocation HIT_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_goodbye_hit");
    public static SoundEvent HIT;
    public static final ResourceLocation HOLLE_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_holle");
    public static SoundEvent HOLLE;
    public static final ResourceLocation EGO_BGM_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_ego_bgm");
    public static SoundEvent EGO_BGM;

    private NtSounds() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent registerEvent) {
        registerEvent.register(Registries.SOUND_EVENT, registerHelper -> {
            WINDUP = SoundEvent.createVariableRangeEvent(WINDUP_ID);
            registerHelper.register(WINDUP_ID, WINDUP);
            HIT = SoundEvent.createVariableRangeEvent(HIT_ID);
            registerHelper.register(HIT_ID, HIT);
            HOLLE = SoundEvent.createVariableRangeEvent(HOLLE_ID);
            registerHelper.register(HOLLE_ID, HOLLE);
            EGO_BGM = SoundEvent.createVariableRangeEvent(EGO_BGM_ID);
            registerHelper.register(EGO_BGM_ID, EGO_BGM);
        });
    }
}
