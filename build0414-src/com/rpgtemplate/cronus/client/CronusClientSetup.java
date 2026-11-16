package com.rpgtemplate.cronus.client;

import com.rpgtemplate.cronus.CronusEntities;
import com.rpgtemplate.cronus.CronusItems;
import com.rpgtemplate.cronus.CronusKeys;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 假面骑士 Cronus 客户端初始化：
 * <ul>
 *   <li>物品动态模型谓词注册（{@code rpgtemplate:driver_loaded}、{@code rpgtemplate:bugvisor_mode}）。</li>
 *   <li>注册光线枪激光弹、巨型绿色追踪爆炸弹丸与电锯回旋刃能量波的 3D 体积实体渲染器。</li>
 *   <li>将 3D 假面骑士 Cronus 装甲与腰带渲染层 {@link CronusSuitLayer} 挂载至全部玩家皮肤渲染器（default / slim）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusClientSetup {
    private CronusClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (CronusEntities.LASER_BOLT != null) {
            event.registerEntityRenderer(CronusEntities.LASER_BOLT, CronusLaserBoltRenderer::new);
        }
        if (CronusEntities.ENERGY_ORB != null) {
            event.registerEntityRenderer(CronusEntities.ENERGY_ORB, CronusEnergyOrbRenderer::new);
        }
        if (CronusEntities.SAW_WAVE != null) {
            event.registerEntityRenderer(CronusEntities.SAW_WAVE, CronusSawWaveRenderer::new);
        }
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

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                CronusSuitLayer.attach(renderer);
            }
        }
    }
}
