package com.rpgtemplate.nothingthere;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** 血效果专用通道 rpgtemplate:nt_gore。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GoreNet {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rpgtemplate", "nt_gore"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private GoreNet() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(GoreNet::register);
    }

    public static void register() {
        CHANNEL.registerMessage(0, GoreMsg.class, GoreMsg::encode, GoreMsg::decode, GoreMsg::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }
}
