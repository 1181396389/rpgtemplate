package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.skill.SkillRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** 一无所有模板：启动时注册本模板的技能（蛋态下禁用，见 EggBlocked）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NothingThereSetup {
    private NothingThereSetup() {
    }

    /** 构造阶段注册服务端设置（非人体质的移速总面板）。必须在模组构造期间调用，所以放在这里。 */
    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        NtConfig.register();
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        NtBody.init();
        event.enqueueWork(() -> {
            SkillRegistry.register("rpgtemplate:oh_man", new EggBlocked(new OhManSkill()));
            SkillRegistry.register("rpgtemplate:holle", new EggBlocked(new HolleSkill()));
            SkillRegistry.register("rpgtemplate:goodbye", new EggBlocked(new GoodbyeSkill()));
            SkillRegistry.register("rpgtemplate:form_shift", new EggBlocked(new FormShiftSkill()));
        });
    }
}
