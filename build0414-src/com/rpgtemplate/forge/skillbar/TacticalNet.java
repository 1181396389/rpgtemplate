package com.rpgtemplate.forge.skillbar;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 战术双层技能栏与空中身法通道（{@code rpgtemplate:tactical_net}）。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TacticalNet {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("rpgtemplate", "tactical_net"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals);

    private TacticalNet() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CHANNEL.registerMessage(
            0,
            TacticalSkillMsg.class,
            TacticalSkillMsg::encode,
            TacticalSkillMsg::decode,
            TacticalSkillMsg::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)));
    }
}
