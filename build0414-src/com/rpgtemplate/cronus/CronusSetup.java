package com.rpgtemplate.cronus;

import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillRegistry;
import com.rpgtemplate.forge.skillbar.ServerSkillInvoker;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 假面骑士 Cronus（{@code rpgtemplate:cronus_1}）技能引导注册与后续自定义装甲模型挂载预留口。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusSetup {
    /**
     * 预留贴图路径：后续提供 Cronus 铠甲模型贴图时，只需将贴图置于此路径并在客户端 RenderLayer 挂载即可，
     * 变身态判定统一读取 {@link CronusKeys#KEY_TRANSFORMED}。
     */
    public static final ResourceLocation ARMOR_TEXTURE_HOOK =
        new ResourceLocation("rpgtemplate", "textures/entity/cronus_armor.png");

    private CronusSetup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerBoth("rpgtemplate:cronus_henshin", new CronusSkills.HenshinSkill());
            registerBoth("rpgtemplate:cronus_pause", new CronusSkills.PauseSkill());
            registerBoth("rpgtemplate:cronus_zwei", new CronusSkills.ZweiWeaponSkill());
            registerBoth("rpgtemplate:cronus_energy_item", new CronusSkills.EnergyItemSkill());
            registerBoth("rpgtemplate:cronus_crews_aid", new CronusSkills.CrewsAidSkill());
            registerBoth("rpgtemplate:cronus_kimewaza", new CronusSkills.KimewazaSkill());
            registerBoth("rpgtemplate:cronus_reset", new CronusSkills.ResetSkill());
        });
    }

    private static void registerBoth(String id, Skill skill) {
        SkillRegistry.register(id, skill);
        ServerSkillInvoker.registerMirror(id, skill);
    }
}
