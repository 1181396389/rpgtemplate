package com.rpgtemplate.templates;

import com.rpgtemplate.forge.skill.SkillRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 注册 N / R / SR / SSR 四套过渡品阶模板的主动技能。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TransitionTemplatesSetup {
    private TransitionTemplatesSetup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // N · 佐藤和真
            SkillRegistry.register("rpgtemplate:kazuma_steal", new KazumaTemplate.StealSkill());
            SkillRegistry.register("rpgtemplate:kazuma_snipe", new KazumaTemplate.SnipeSkill());
            SkillRegistry.register("rpgtemplate:kazuma_lurk", new KazumaTemplate.LurkSkill());

            // R · 灶门炭治郎
            SkillRegistry.register("rpgtemplate:tanjiro_water_wheel", new TanjiroTemplate.WaterWheelSkill());
            SkillRegistry.register("rpgtemplate:tanjiro_flowing_dance", new TanjiroTemplate.FlowingDanceSkill());
            SkillRegistry.register("rpgtemplate:tanjiro_hinokami", new TanjiroTemplate.HinokamiSkill());

            // SR · 宇智波鼬
            SkillRegistry.register("rpgtemplate:itachi_crow_clone", new ItachiTemplate.CrowCloneSkill());
            SkillRegistry.register("rpgtemplate:itachi_amaterasu", new ItachiTemplate.AmaterasuSkill());
            SkillRegistry.register("rpgtemplate:itachi_tsukuyomi", new ItachiTemplate.TsukuyomiSkill());
        });
    }
}
