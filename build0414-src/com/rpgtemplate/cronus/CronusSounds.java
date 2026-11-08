package com.rpgtemplate.cronus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 假面骑士 Cronus 专属音效注册表（预留原剧音频接口 + 内置合成音效回退）。
 * <p>后续将对应 {@code .ogg} 放入 {@code assets/rpgtemplate/sounds/} 并合并 {@code cronus_sounds_fragment.json}
 * 即可直接播放原剧变身、Pause、Restart、Kimewaza 与「绝版！」音效，无需改动代码。</p>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusSounds {
    public static final ResourceLocation HENSHIN_ID = new ResourceLocation("rpgtemplate", "cronus_henshin");
    public static SoundEvent HENSHIN;

    public static final ResourceLocation PAUSE_ID = new ResourceLocation("rpgtemplate", "cronus_pause");
    public static SoundEvent PAUSE;

    public static final ResourceLocation RESTART_ID = new ResourceLocation("rpgtemplate", "cronus_restart");
    public static SoundEvent RESTART;

    public static final ResourceLocation KIMEWAZA_ID = new ResourceLocation("rpgtemplate", "cronus_kimewaza");
    public static SoundEvent KIMEWAZA;

    public static final ResourceLocation OUT_EXECUTE_ID = new ResourceLocation("rpgtemplate", "cronus_out_execute");
    public static SoundEvent OUT_EXECUTE;

    public static final ResourceLocation ITEM_GET_ID = new ResourceLocation("rpgtemplate", "cronus_item_get");
    public static SoundEvent ITEM_GET;

    private CronusSounds() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.SOUND_EVENTS, helper -> {
            HENSHIN = registerOne(helper, HENSHIN_ID);
            PAUSE = registerOne(helper, PAUSE_ID);
            RESTART = registerOne(helper, RESTART_ID);
            KIMEWAZA = registerOne(helper, KIMEWAZA_ID);
            OUT_EXECUTE = registerOne(helper, OUT_EXECUTE_ID);
            ITEM_GET = registerOne(helper, ITEM_GET_ID);
        });
    }

    private static SoundEvent registerOne(RegisterEvent.RegisterHelper<SoundEvent> helper, ResourceLocation id) {
        SoundEvent se = SoundEvent.m_262824_(id);
        helper.register(id, se);
        return se;
    }

    public static void playHenshin(ServerPlayer p) {
        playCustom(p, HENSHIN);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_11705_, SoundSource.PLAYERS, 0.9f, 1.35f);
    }

    public static void playPause(ServerPlayer p) {
        playCustom(p, PAUSE);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_12313_, SoundSource.PLAYERS, 1.0f, 0.55f);
    }

    public static void playRestart(ServerPlayer p) {
        playCustom(p, RESTART);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_12313_, SoundSource.PLAYERS, 1.0f, 1.55f);
    }

    public static void playKimewaza(ServerPlayer p) {
        playCustom(p, KIMEWAZA);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_12317_, SoundSource.PLAYERS, 1.0f, 1.25f);
    }

    public static void playOutExecute(ServerPlayer p) {
        playCustom(p, OUT_EXECUTE);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_11909_, SoundSource.PLAYERS, 1.0f, 0.75f);
    }

    public static void playItemGet(ServerPlayer p) {
        playCustom(p, ITEM_GET);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_12318_, SoundSource.PLAYERS, 0.9f, 1.65f);
    }

    private static void playCustom(ServerPlayer p, SoundEvent custom) {
        if (custom != null) {
            p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
                custom, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }
}
