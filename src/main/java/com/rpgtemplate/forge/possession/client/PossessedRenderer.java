package com.rpgtemplate.forge.possession.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

public class PossessedRenderer extends PlayerRenderer {
    public static final ResourceLocation TATTOO = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/sukuna_tattoo.png");

    public PossessedRenderer(Context ctx, boolean slim) {
        super(ctx, slim);
        this.addLayer(new PossessedRenderer.TattooLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        ResourceLocation skin = ClientPossession.hostSkin(player.getUUID());
        return skin != null ? skin : super.getTextureLocation(player);
    }

    public void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, HumanoidArm arm) {
        PlayerModel<AbstractClientPlayer> model = this.getModel();
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart armPart = right ? model.rightArm : model.leftArm;
        ModelPart sleevePart = right ? model.rightSleeve : model.leftSleeve;
        armPart.visible = true;
        sleevePart.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        ResourceLocation skin = this.getTextureLocation(player);
        armPart.xRot = 0.0F;
        armPart.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        armPart.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TATTOO)), light, OverlayTexture.NO_OVERLAY);
        sleevePart.xRot = 0.0F;
        sleevePart.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
    }

    static final class TattooLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        TattooLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
        ) {
            if (!player.isInvisible()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(PossessedRenderer.TATTOO));
                this.getParentModel().renderToBuffer(pose, vc, light, LivingEntityRenderer.getOverlayCoords(player, 0.0F), -1);
            }
        }
    }
}
