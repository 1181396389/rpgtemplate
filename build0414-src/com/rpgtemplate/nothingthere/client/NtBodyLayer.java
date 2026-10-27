package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.nothingthere.NtBody;
import java.lang.reflect.Method;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Draws the 一无所有 body as a sculpted 3D mesh (gen/gen_nt_mesh.py -> NtBodyMesh) for human-form players.
 * The mesh is posed with the vanilla part offsets and rotations, so walking, sneaking and swinging still
 * animate. The scale is applied around the feet (y = 1.5 blocks in model space), so the body grows upward.
 * The mesh uses per-vertex colours on the plain white texture.
 */
public final class NtBodyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    /** Visual scale on top of the vanilla player scale (0.9375): 2.0 wide, 1.6 tall = 3 blocks tall. */
    private static final float VISUAL_XZ = 2.0F;
    private static final float VISUAL_Y = 1.6F;
    private static final float FEET_Y = 1.5F;

    private static final Logger LOG = LogManager.getLogger("rpgtemplate/NtBody");

    public NtBodyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Adds this layer to a player renderer. addLayer is protected in the SRG class, so it is called by name. */
    public static void attach(PlayerRenderer renderer) {
        try {
            Method add = LivingEntityRenderer.class.getDeclaredMethod("m_115326_", RenderLayer.class);
            add.setAccessible(true);
            add.invoke(renderer, new NtBodyLayer(renderer));
            LOG.info("[NtBody] layer attached to {}", renderer.getClass().getSimpleName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not add the 一无所有 body layer", e);
        }
    }

    @Override
    public void m_6494_(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                        float netHeadYaw, float headPitch) {
        if (!NtBody.isHuman(player)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> vanilla = this.m_117386_();
        RenderType type = vanilla.m_103119_(WHITE);
        pose.m_85836_();
        pose.m_85837_(0.0, FEET_Y, 0.0);
        pose.m_85841_(VISUAL_XZ, VISUAL_Y, VISUAL_XZ);
        pose.m_85837_(0.0, -FEET_Y, 0.0);
        NtBodyMesh.drawBody(pose, buffers.m_6299_(type), light, OverlayTexture.f_118083_,
            vanilla.f_102808_, vanilla.f_102810_, vanilla.f_102811_, vanilla.f_102812_,
            vanilla.f_102813_, vanilla.f_102814_, NtBody.goodbyePending(player));
        pose.m_85849_();
    }
}
