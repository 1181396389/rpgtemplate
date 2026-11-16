package com.rpgtemplate.cronus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 假面骑士 Cronus 全套专属原剧/MUGEN 音效注册表。
 * <p>完整覆盖编年史变身（完整版/快捷版）、Pause、Restart、Reset、时序瞬移、缺陷护罩Ⅱ切形态，
 * 以及光线枪/电锯/Critical Crews-Aid/绝版处决（Shut Down!）全部必杀技独立音效。</p>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusSounds {
    public static final ResourceLocation HENSHIN_ID = new ResourceLocation("rpgtemplate", "cronus_henshin");
    public static SoundEvent HENSHIN;

    public static final ResourceLocation HENSHIN_FAST_ID = new ResourceLocation("rpgtemplate", "cronus_henshin_fast");
    public static SoundEvent HENSHIN_FAST;

    public static final ResourceLocation PAUSE_ID = new ResourceLocation("rpgtemplate", "cronus_pause");
    public static SoundEvent PAUSE;

    public static final ResourceLocation RESTART_ID = new ResourceLocation("rpgtemplate", "cronus_restart");
    public static SoundEvent RESTART;

    public static final ResourceLocation RESET_ID = new ResourceLocation("rpgtemplate", "cronus_reset");
    public static SoundEvent RESET;

    public static final ResourceLocation BLINK_ID = new ResourceLocation("rpgtemplate", "cronus_blink");
    public static SoundEvent BLINK;

    public static final ResourceLocation MODE_SWITCH_ID = new ResourceLocation("rpgtemplate", "cronus_mode_switch");
    public static SoundEvent MODE_SWITCH;

    public static final ResourceLocation KIMEWAZA_ID = new ResourceLocation("rpgtemplate", "cronus_kimewaza");
    public static SoundEvent KIMEWAZA;

    public static final ResourceLocation KIME_CJ_BEAM_ID = new ResourceLocation("rpgtemplate", "cronus_kime_cj_beam");
    public static SoundEvent KIME_CJ_BEAM;

    public static final ResourceLocation KIME_CJ_PAUSE_ID = new ResourceLocation("rpgtemplate", "cronus_kime_cj_pause");
    public static SoundEvent KIME_CJ_PAUSE;

    public static final ResourceLocation KIME_CJ_ORBS_ID = new ResourceLocation("rpgtemplate", "cronus_kime_cj_orbs");
    public static SoundEvent KIME_CJ_ORBS;

    public static final ResourceLocation ORB_EXPLODE_ID = new ResourceLocation("rpgtemplate", "cronus_orb_explode");
    public static SoundEvent ORB_EXPLODE;

    public static final ResourceLocation KIME_CS_WAVE_ID = new ResourceLocation("rpgtemplate", "cronus_kime_cs_wave");
    public static SoundEvent KIME_CS_WAVE;

    public static final ResourceLocation KIME_CS_CHARGE_ID = new ResourceLocation("rpgtemplate", "cronus_kime_cs_charge");
    public static SoundEvent KIME_CS_CHARGE;

    public static final ResourceLocation KIME_CS_SLASH_ID = new ResourceLocation("rpgtemplate", "cronus_kime_cs_slash");
    public static SoundEvent KIME_CS_SLASH;

    public static final ResourceLocation CREWS_AID_ID = new ResourceLocation("rpgtemplate", "cronus_crews_aid");
    public static SoundEvent CREWS_AID;

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
            HENSHIN_FAST = registerOne(helper, HENSHIN_FAST_ID);
            PAUSE = registerOne(helper, PAUSE_ID);
            RESTART = registerOne(helper, RESTART_ID);
            RESET = registerOne(helper, RESET_ID);
            BLINK = registerOne(helper, BLINK_ID);
            MODE_SWITCH = registerOne(helper, MODE_SWITCH_ID);
            KIMEWAZA = registerOne(helper, KIMEWAZA_ID);
            KIME_CJ_BEAM = registerOne(helper, KIME_CJ_BEAM_ID);
            KIME_CJ_PAUSE = registerOne(helper, KIME_CJ_PAUSE_ID);
            KIME_CJ_ORBS = registerOne(helper, KIME_CJ_ORBS_ID);
            ORB_EXPLODE = registerOne(helper, ORB_EXPLODE_ID);
            KIME_CS_WAVE = registerOne(helper, KIME_CS_WAVE_ID);
            KIME_CS_CHARGE = registerOne(helper, KIME_CS_CHARGE_ID);
            KIME_CS_SLASH = registerOne(helper, KIME_CS_SLASH_ID);
            CREWS_AID = registerOne(helper, CREWS_AID_ID);
            OUT_EXECUTE = registerOne(helper, OUT_EXECUTE_ID);
            ITEM_GET = registerOne(helper, ITEM_GET_ID);
        });
    }

    private static SoundEvent registerOne(RegisterEvent.RegisterHelper<SoundEvent> helper, ResourceLocation id) {
        SoundEvent se = SoundEvent.m_262824_(id);
        helper.register(id, se);
        return se;
    }

    /** 完整变身（技能 1）：直接播放完整版原剧编年史变身音效（cronus_henshin.ogg）。 */
    public static void playHenshin(ServerPlayer p) {
        playCustom(p, HENSHIN, 1.0f, 1.0f);
    }

    /** 快速变身（右键卡带）：直接播放 9 秒后切入的原剧编年史变身音效（cronus_henshin_fast.ogg）。 */
    public static void playHenshinFast(ServerPlayer p) {
        playCustom(p, HENSHIN_FAST, 1.0f, 1.0f);
    }

    /** 【Pause】时间暂停原声（按键触发 + PAUSE! + 巨型时钟骤停）。 */
    public static void playPause(ServerPlayer p) {
        playCustom(p, PAUSE, 1.0f, 1.0f);
    }

    /** 【Restart】时间重开原声（RE-START!）。 */
    public static void playRestart(ServerPlayer p) {
        playCustom(p, RESTART, 1.0f, 1.0f);
    }

    /** 【Reset】时间回溯原声（RE-SET! + 翠白时光倒流闪光）。 */
    public static void playReset(ServerPlayer p) {
        playCustom(p, RESET, 1.0f, 1.0f);
    }

    /** 【时序瞬移】残影瞬移音效。 */
    public static void playBlink(ServerPlayer p) {
        playCustom(p, BLINK, 0.95f, 1.0f);
    }

    /** 【Gashacon 缺陷护罩Ⅱ】光线枪 ⇄ 电锯形态切换提示音。 */
    public static void playModeSwitch(ServerPlayer p) {
        playCustom(p, MODE_SWITCH, 0.95f, 1.0f);
    }

    /** 兼容入口：通用 Kimewaza 音效。 */
    public static void playKimewaza(ServerPlayer p) {
        playCustom(p, KIMEWAZA, 1.0f, 1.0f);
    }

    /** 光线枪（非时停）必杀技一：CRITICAL JUDGMENT 持续绿色激光。 */
    public static void playKimeCjBeam(ServerPlayer p) {
        playCustom(p, KIME_CJ_BEAM, 1.0f, 1.0f);
    }

    /** 光线枪（时停）必杀技一：CRITICAL JUDGMENT 瞬发贯穿激光炮。 */
    public static void playKimeCjPause(ServerPlayer p) {
        playCustom(p, KIME_CJ_PAUSE, 1.0f, 1.0f);
    }

    /** 光线枪必杀技二：CRITICAL JUDGMENT 4 发追踪巨弹齐射。 */
    public static void playKimeCjOrbs(ServerPlayer p) {
        playCustom(p, KIME_CJ_ORBS, 1.0f, 1.0f);
    }

    /** 光线枪必杀技二：追踪巨弹命中大爆炸原声。 */
    public static void playOrbExplode(ServerLevel sl, double x, double y, double z, boolean fast) {
        playAt(sl, x, y, z, ORB_EXPLODE, 1.05f, fast ? 1.08f : 1.0f);
    }

    /** 电锯必杀技一：CRITICAL SACRIFICE 巨大绿色回旋光轮波。 */
    public static void playKimeCsWave(ServerPlayer p) {
        playCustom(p, KIME_CS_WAVE, 1.0f, 1.0f);
    }

    /** 电锯必杀技二（蓄能）：CRITICAL SACRIFICE 绿能聚刃激活。 */
    public static void playKimeCsCharge(ServerPlayer p) {
        playCustom(p, KIME_CS_CHARGE, 1.0f, 1.0f);
    }

    /** 电锯必杀技二（引爆）：CRITICAL SACRIFICE 下一击巨额引爆斩击原声。 */
    public static void playKimeCsSlash(ServerLevel sl, double x, double y, double z) {
        playAt(sl, x, y, z, KIME_CS_SLASH, 1.15f, 1.0f);
    }

    /** 【Critical Crews-Aid】时计飞踢 / 蓄力旋转回旋踢原声。 */
    public static void playCrewsAid(ServerPlayer p) {
        playCustom(p, CREWS_AID, 1.0f, 1.0f);
    }

    /** 【时停绝版处决】OUT / Shut Down! 终结爆发原声。 */
    public static void playOutExecute(ServerPlayer p) {
        playCustom(p, OUT_EXECUTE, 1.10f, 1.0f);
    }

    /** 【能量道具抽取】ITEM GET! 提示音。 */
    public static void playItemGet(ServerPlayer p) {
        playCustom(p, ITEM_GET, 0.92f, 1.0f);
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
            SoundEvents.f_11983_, SoundSource.PLAYERS, 0.55f, 1.75f);
    }

    private static void playCustom(ServerPlayer p, SoundEvent custom, float volume, float pitch) {
        if (custom != null && p != null) {
            p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(),
                custom, SoundSource.MASTER, volume, pitch);
        }
    }

    private static void playAt(ServerLevel sl, double x, double y, double z, SoundEvent custom, float volume, float pitch) {
        if (custom != null && sl != null) {
            sl.m_6263_(null, x, y, z, custom, SoundSource.MASTER, volume, pitch);
        }
    }
}
