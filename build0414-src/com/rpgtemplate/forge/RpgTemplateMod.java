package com.rpgtemplate.forge;

import com.mojang.logging.LogUtils;
import com.rpgtemplate.forge.king.KingConfig;
import com.rpgtemplate.forge.king.KingNetwork;
import com.rpgtemplate.forge.network.ModNetwork;
import com.rpgtemplate.forge.possession.PossessionNet;
import com.rpgtemplate.forge.registry.ModCapabilities;
import com.rpgtemplate.forge.registry.ModCreativeTabs;
import com.rpgtemplate.forge.registry.ModEntities;
import com.rpgtemplate.forge.registry.ModItems;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.nothingthere.NtBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 模组入口类：覆盖基础包中仍引用已移除 {@code CraftBootstrap} 的旧入口，
 * 改由 {@link NtBootstrap#init()} 负责拟态刀物品与创造模式标签页注册。
 */
@Mod(RpgTemplateMod.MOD_ID)
public final class RpgTemplateMod {
    public static final String MOD_ID = "rpgtemplate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpgTemplateMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.REGISTER.register(bus);
        ModEntities.REGISTER.register(bus);
        ModSounds.REGISTER.register(bus);
        ModCreativeTabs.REGISTER.register(bus);
        bus.addListener(ModCapabilities::register);
        bus.addListener(ModConfigs::onLoading);
        bus.addListener(ModConfigs::onReloading);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ModConfigs.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ModConfigs.CLIENT_SPEC);
        ModNetwork.register();
        PossessionNet.register();
        KingConfig.register();
        KingNetwork.register();
        NtBootstrap.init();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
