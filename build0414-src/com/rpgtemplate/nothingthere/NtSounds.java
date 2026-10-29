package com.rpgtemplate.nothingthere;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 一无所有 goodbye 音效（取自用户提供的 yiwusuoy 音频）。
 * WINDUP：按下时播放（goodbye 段，约 1 秒）。
 * HIT：出击（落地斩击）时播放（命中音效，约 1.3 秒）。
 * HOLLE：Holle 发射骨刺时播放（约 1.7 秒）。
 * 音效 JSON 合并在 assets/rpgtemplate/sounds.json（见 pack_nt_jar.py）。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NtSounds {
    public static final ResourceLocation WINDUP_ID = new ResourceLocation("rpgtemplate", "nt_goodbye_windup");
    public static SoundEvent WINDUP;
    public static final ResourceLocation HIT_ID = new ResourceLocation("rpgtemplate", "nt_goodbye_hit");
    public static SoundEvent HIT;
    public static final ResourceLocation HOLLE_ID = new ResourceLocation("rpgtemplate", "nt_holle");
    public static SoundEvent HOLLE;

    private NtSounds() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent registerEvent) {
        registerEvent.register(ForgeRegistries.Keys.SOUND_EVENTS, registerHelper -> {
            WINDUP = SoundEvent.m_262824_(WINDUP_ID);
            registerHelper.register(WINDUP_ID, WINDUP);
            HIT = SoundEvent.m_262824_(HIT_ID);
            registerHelper.register(HIT_ID, HIT);
            HOLLE = SoundEvent.m_262824_(HOLLE_ID);
            registerHelper.register(HOLLE_ID, HOLLE);
        });
    }
}
