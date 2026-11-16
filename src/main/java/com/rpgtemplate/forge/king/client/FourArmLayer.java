package com.rpgtemplate.forge.king.client;
import net.minecraft.util.FastColor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.forge.king.KingNetwork;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class FourArmLayer extends ItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final PlayerRenderer parent;
    private final ModelPart mouth;
    private final ModelPart teeth;
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/king_white.png");

    public FourArmLayer(PlayerRenderer var1, ItemInHandRenderer var2) {
        super(var1, var2);
        this.parent = var1;
        MeshDefinition var3 = new MeshDefinition();
        var3.getRoot().addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 7.0F, -2.3F, 7.0F, 3.0F, 0.25F), PartPose.ZERO);
        this.mouth = LayerDefinition.create(var3, 16, 16).bakeRoot();
        MeshDefinition var4 = new MeshDefinition();
        CubeListBuilder var5 = CubeListBuilder.create().texOffs(0, 0);

        for (int var6 = 0; var6 < 7; var6++) {
            var5.addBox(-3.1F + (float)var6 * 0.9F, 7.15F, -2.38F, 0.65F, 0.65F, 0.1F);
            var5.addBox(-3.1F + (float)var6 * 0.9F, 9.05F, -2.38F, 0.65F, 0.65F, 0.1F);
        }

        var4.getRoot().addOrReplaceChild("teeth", var5, PartPose.ZERO);
        this.teeth = LayerDefinition.create(var4, 16, 16).bakeRoot();
    }

    public void render(
        PoseStack var1, MultiBufferSource var2, int var3, AbstractClientPlayer var4, float var5, float var6, float var7, float var8, float var9, float var10
    ) {
        KingNetwork.State var11 = KingClient.STATES.get(var4.getUUID());
        if (KingClient.showArms && var11 != null && var11.active() && !var4.isInvisible()) {
            PlayerModel var12 = (PlayerModel)this.parent.getModel();
            this.arm(var1, var2, var3, var4, var12.rightArm, var11.third(), HumanoidArm.RIGHT, var11.basket(), var8);
            this.arm(var1, var2, var3, var4, var12.leftArm, var11.fourth(), HumanoidArm.LEFT, var11.basket(), var8);
            var1.pushPose();
            var12.body.translateAndRotate(var1);
            VertexConsumer var13 = var2.getBuffer(RenderType.entityCutout(WHITE));
            this.mouth.render(var1, var13, var3, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(1.0F, 0.2F, 0.035F, 0.055F));
            this.teeth.render(var1, var13, var3, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(1.0F, 0.94F, 0.9F, 0.83F));
            var1.popPose();
        }
    }

    private void arm(
        PoseStack var1, MultiBufferSource var2, int var3, AbstractClientPlayer var4, ModelPart var5, ItemStack var6, HumanoidArm var7, boolean var8, float var9
    ) {
        PartPose var10 = var5.storePose();
        boolean var11 = var5.visible;

        try {
            var5.visible = true;
            var5.xRot = var8 ? -1.35F : var5.xRot * 0.55F + 0.12F * (float)Math.sin((double)var9 * 0.08);
            var5.yRot = var8 ? (var7 == HumanoidArm.RIGHT ? -0.6F : 0.6F) : 0.0F;
            var5.zRot = var7 == HumanoidArm.RIGHT ? 0.2F : -0.2F;
            var1.pushPose();
            var1.translate(0.0, 0.37, 0.015);
            var5.render(var1, var2.getBuffer(RenderType.entityCutout(this.parent.getTextureLocation(var4))), var3, OverlayTexture.NO_OVERLAY);
            if (!var8 && !var6.isEmpty()) {
                this.renderArmWithItem(
                    var4,
                    var6,
                    var7 == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    var7,
                    var1,
                    var2,
                    var3
                );
            }

            var1.popPose();
        } finally {
            var5.loadPose(var10);
            var5.visible = var11;
        }
    }
}
