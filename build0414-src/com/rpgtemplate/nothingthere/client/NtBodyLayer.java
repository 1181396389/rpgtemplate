package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.nothingthere.NtBody;
import java.lang.reflect.Method;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
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
 * Draws the 一无所有 hand weapons on top of the vanilla player (see NtBodyMesh for what is drawn per form).
 * The pose already carries the player's body scale (NtRendererMixin), so the weapons are sized to the body and
 * follow the vanilla arm pose.
 */
public final class NtBodyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    /** Flesh texture of the cocoon egg (gen_egg_texture.py). */
    private static final ResourceLocation EGG_TEXTURE = new ResourceLocation("rpgtemplate", "textures/entity/nt_egg.png");

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
            LOG.info("[NtBody] hand layer attached to {}", renderer.getClass().getSimpleName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not add the 一无所有 hand layer", e);
        }
    }

    @Override
    public void m_6494_(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                        float netHeadYaw, float headPitch) {
        PlayerModel<AbstractClientPlayer> vanilla = this.m_117386_();
        RenderType type = vanilla.m_103119_(WHITE);
        if (NtBody.inCocoon(player)) {
            // the cocoon: the egg replaces the whole vanilla body (NtBodyClient hides it).
            // The flesh texture (textures/entity/nt_egg.png) covers the body; the eyes sample its white corner.
            pose.m_85836_();
            RenderType eggType = vanilla.m_103119_(EGG_TEXTURE);
            NtBodyMesh.drawEgg(pose, buffers.m_6299_(eggType), light, OverlayTexture.f_118083_);
            pose.m_85849_();
            return;
        }
        if (!NtBody.isHuman(player)) {
            return;
        }
        pose.m_85836_();
        // goodbye: the blade arm follows the video pose (NtGoodbyeAnim). The arms are restored after the draw.
        float now = (float) player.m_9236_().m_46467_() + partialTick;
        boolean flag = NtBody.goodbyePending(player);
        NtGoodbyeAnim.observe(player, flag, now);
        float t = NtGoodbyeAnim.elapsed(player, now);
        boolean anim = t >= 0f;
        // Holle cast: the left arm goes overhead (NtHolleAnim). Goodbye wins if both are running.
        boolean holleFlag = NtBody.hollePending(player);
        NtHolleAnim.observe(player, holleFlag, now);
        float holleT = NtHolleAnim.elapsed(player, now);
        // oh man: the right arm raises the hammer while charging and snaps down on a slam (NtSlamAnim).
        NtSlamAnim.observe(player, NtBody.ohCharging(player), NtBody.ohSlamming(player), now);
        float slamTheta = NtSlamAnim.theta(player);
        ModelPart right = vanilla.f_102811_;
        ModelPart left = vanilla.f_102812_;
        float savedRight = right.f_104203_;
        float savedLeft = left.f_104203_;
        try {
            if (anim) {
                // the torso lean is already in the part's xRot (NtGoodbyeLean); subtract it so the world angle is theta
                left.f_104203_ = (float) Math.toRadians(-NtGoodbyeAnim.theta(t)) - NtGoodbyeLean.leanRad(t);
                right.f_104203_ = savedRight + (float) Math.toRadians(NtGoodbyeAnim.rightOffset(t));
            } else if (holleT >= 0f) {
                left.f_104203_ = (float) Math.toRadians(-NtHolleAnim.theta(holleT));
            }
            if (!anim && slamTheta >= 0f) {
                // same convention as the Holle arm: negative xRot = arm forward / up
                right.f_104203_ = (float) Math.toRadians(-slamTheta);
            }
            NtBodyMesh.drawHands(pose, buffers.m_6299_(type), light, OverlayTexture.f_118083_,
                right, left, flag || anim, NtBody.bladeForm(player));
        } finally {
            right.f_104203_ = savedRight;
            left.f_104203_ = savedLeft;
        }
        pose.m_85849_();
    }
}
