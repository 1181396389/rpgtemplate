package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.cronus.CronusItems;
import java.lang.reflect.Method;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.ItemStack;

/**
 * 挂载到玩家模型的假面骑士 Cronus 专属 3D 渲染层：
 * <ul>
 *   <li>处于变身态时：完整绘制 3D 假面骑士 Cronus 装甲外观（含动态风衣、腰带、头冠、肩甲与自发光层）。</li>
 *   <li>人形态但腰间（护腿槽位）装备了【故障驱动器Ⅱ】时：仅绘制贴合腰部的 3D 故障驱动器Ⅱ腰带。</li>
 * </ul>
 */
public final class CronusSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CronusSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void attach(PlayerRenderer renderer) {
        try {
            Method add = LivingEntityRenderer.class.getDeclaredMethod("m_115326_", RenderLayer.class);
            add.setAccessible(true);
            add.invoke(renderer, new CronusSuitLayer(renderer));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to attach CronusSuitLayer", e);
        }
    }

    @Override
    public void m_6494_(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                        float netHeadYaw, float headPitch) {
        if (player.m_20145_()) {
            return;
        }
        int overlay = LivingEntityRenderer.m_115338_(player, 0.0f);
        if (CronusSuitAppearance.isTransformed(player)) {
            CronusSuitModel.get().renderFullSuit(
                pose, buffers, light, overlay, this.m_117386_(), player,
                limbSwing, limbSwingAmount, partialTick);
            return;
        }
        ItemStack wornDriver = CronusItems.getWornDriver(player);
        if (!wornDriver.m_41619_()) {
            CronusSuitModel.get().renderBeltOnly(
                pose, buffers, light, overlay, this.m_117386_(), CronusItems.isDriverLoaded(wornDriver));
        }
    }
}
