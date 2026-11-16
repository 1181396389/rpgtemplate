package com.rpgtemplate.forge;

import com.mojang.logging.LogUtils;
import com.rpgtemplate.cronus.CronusItems;
import com.rpgtemplate.forge.king.KingConfig;
import com.rpgtemplate.forge.king.KingNetwork;
import com.rpgtemplate.forge.mask.PerceptionMaskBootstrap;
import com.rpgtemplate.forge.network.ModNetwork;
import com.rpgtemplate.forge.possession.PossessionNet;
import com.rpgtemplate.forge.registry.ModCapabilities;
import com.rpgtemplate.forge.registry.ModCreativeTabs;
import com.rpgtemplate.forge.registry.ModEntities;
import com.rpgtemplate.forge.registry.ModItems;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.nothingthere.NtBootstrap;
import com.rpgtemplate.nothingthere.NtConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 模组主入口类（NeoForge 1.21.1）。
 */
@Mod(RpgTemplateMod.MOD_ID)
public final class RpgTemplateMod {
    public static final String MOD_ID = "rpgtemplate";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static volatile IEventBus MOD_BUS;
    public static volatile ModContainer MOD_CONTAINER;

    public RpgTemplateMod(IEventBus bus, ModContainer container) {
        MOD_BUS = bus;
        MOD_CONTAINER = container;
        ModCapabilities.ATTACHMENTS.register(bus);
        ModItems.REGISTER.register(bus);
        ModEntities.REGISTER.register(bus);
        ModSounds.REGISTER.register(bus);
        ModCreativeTabs.REGISTER.register(bus);
        bus.addListener(ModConfigs::onLoading);
        bus.addListener(ModConfigs::onReloading);
        container.registerConfig(ModConfig.Type.SERVER, ModConfigs.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ModConfigs.CLIENT_SPEC);
        ModNetwork.register();
        PossessionNet.register();
        KingConfig.register(container);
        KingNetwork.register();
        NtConfig.register(container);
        NtBootstrap.init(bus);
        PerceptionMaskBootstrap.init(bus);
        CronusItems.init(bus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
