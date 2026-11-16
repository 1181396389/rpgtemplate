package com.rpgtemplate.cronus.client;

import com.rpgtemplate.cronus.CronusItems;
import com.rpgtemplate.cronus.CronusKeys;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 假面骑士 Cronus 物品动态模型谓词注册（客户端）：
 * <ul>
 *   <li>{@code rpgtemplate:driver_loaded}：故障驱动器Ⅱ 是否已插入编年史转玩卡带。</li>
 *   <li>{@code rpgtemplate:bugvisor_mode}：Gashacon 缺陷护罩Ⅱ 当前形态（0 = 电锯模式，1 = 光线枪模式）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusClientSetup {
    private CronusClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                CronusItems.BUGGLE_DRIVER_II.get(),
                new ResourceLocation("rpgtemplate", "driver_loaded"),
                (stack, level, entity, seed) -> CronusItems.isDriverLoaded(stack) ? 1.0f : 0.0f);

            ItemProperties.register(
                CronusItems.GASHACON_BUGVISOR_II.get(),
                new ResourceLocation("rpgtemplate", "bugvisor_mode"),
                (stack, level, entity, seed) ->
                    CronusItems.bugvisorMode(stack) == CronusKeys.MODE_GUN ? 1.0f : 0.0f);
        });
    }
}
