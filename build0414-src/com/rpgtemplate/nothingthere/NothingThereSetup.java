package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.skill.SkillRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** 一无所有模板：启动时注册本模板的技能（蛋态下禁用，见 EggBlocked）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NothingThereSetup {
    private NothingThereSetup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SkillRegistry.register("rpgtemplate:oh_man", new EggBlocked(new OhManSkill()));
            SkillRegistry.register("rpgtemplate:holle", new EggBlocked(new HolleSkill()));
            SkillRegistry.register("rpgtemplate:goodbye", new EggBlocked(new GoodbyeSkill()));
            SkillRegistry.register("rpgtemplate:form_shift", new EggBlocked(new FormShiftSkill()));
        });
    }
}
