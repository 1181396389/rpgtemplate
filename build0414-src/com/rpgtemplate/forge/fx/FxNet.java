package com.rpgtemplate.forge.fx;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** 非粒子特效通道 rpgtemplate:fx（服务端 -> 附近客户端）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FxNet {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rpgtemplate", "fx"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private FxNet() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FxNet::register);
    }

    public static void register() {
        CHANNEL.registerMessage(0, FxMsg.class, FxMsg::encode, FxMsg::decode, FxMsg::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
