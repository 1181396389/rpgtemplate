package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.NtBody;
import com.rpgtemplate.nothingthere.NtMimicBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * First-person hands for a 一无所有 human. Vanilla draws the first-person arm from the hand event. For the local
 * NT player with an empty hand, both arms are replaced: the right with the hammer, the left with the flesh arm
 * (and the blade in blade form or goodbye). Held items are left alone, except that the mimic blade gets the
 * attack pose (the hand root is turned around the arm pivot, so the blade moves with the arm).
 * Runs at LOW priority so the build-motion hand pose (BuildVisuals) is already applied to the stack.
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NtFirstPerson {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    /** First-person hand scale (x/z and y). 1 = vanilla hand size. */
    private static final float FP_SIZE = 1.0F;

    private NtFirstPerson() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer player = mc.f_91074_;
        if (player == null) {
            return;
        }
        // The event fires per hand. Map the hand to the arm side, so the hammer is always on the player's right.
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        boolean rightArm = mainHand == (player.m_5737_() == HumanoidArm.RIGHT);
        if (!event.getItemStack().m_41619_()) {
            // Held item: vanilla draws it. The mimic blade only gets the attack pose, applied to the hand root.
            if (rightArm && NtMimicBlade.isBlade(event.getItemStack())) {
                PlayerModel<?> model = playerModel(mc, player);
                float[] attack = NtMimicPose.attackRightArm(player, event.getPartialTick());
                if (model != null && attack != null) {
                    rotateAroundPivot(event.getPoseStack(), model.f_102811_, attack);
                }
            }
            return;
        }
        if (!NtBody.isHuman(player)) {
            return;
        }
        boolean goodbye = NtBody.goodbyePending(player);
        PlayerModel<?> model = playerModel(mc, player);
        if (model == null) {
            return;
        }
        event.setCanceled(true);

        // Attack pose of the right arm (the hammer hand). Null when no attack is playing.
        float[] attack = rightArm ? NtMimicPose.attackRightArm(player, event.getPartialTick()) : null;
        VertexConsumer vc = event.getMultiBufferSource().m_6299_(model.m_103119_(WHITE));
        // The swing and equip progress are the vanilla ones for this hand (the hand placement is applied inside).
        // Hand size: 1:1. Scaling by the third-person body ratio (NtBody.HITBOX_*) pushed the hammer into the middle
        // of the view when tested offline, so the first-person hands keep the vanilla size. Change FP_SIZE to resize.
        NtBodyMesh.drawArmFirstPerson(event.getPoseStack(), vc, event.getPackedLight(), OverlayTexture.f_118083_,
            rightArm ? model.f_102811_ : model.f_102812_, rightArm,
            event.getSwingProgress(), event.getEquipProgress(), goodbye, NtBody.bladeForm(player),
            NtBody.whipForm(player), NtBody.muscleForm(player), FP_SIZE, FP_SIZE, attack);
    }

    /**
     * Turns the current pose around the arm's pivot, in the order ModelPart uses (Z, then Y, then X).
     * The pivot is the model part's offset in model units (1/16 block).
     */
    private static void rotateAroundPivot(PoseStack pose, ModelPart arm, float[] angles) {
        float px = arm.f_104200_ / 16.0F;
        float py = arm.f_104201_ / 16.0F;
        float pz = arm.f_104202_ / 16.0F;
        pose.m_85837_(px, py, pz);
        if (angles[2] != 0.0F) {
            pose.m_252781_(new Quaternionf().rotationZ(angles[2]));
        }
        if (angles[1] != 0.0F) {
            pose.m_252781_(new Quaternionf().rotationY(angles[1]));
        }
        if (angles[0] != 0.0F) {
            pose.m_252781_(new Quaternionf().rotationX(angles[0]));
        }
        pose.m_85837_(-px, -py, -pz);
    }

    /** The model of the local player's renderer (the same PlayerModel the layer and the arm event use). */
    private static PlayerModel<?> playerModel(Minecraft mc, LocalPlayer player) {
        EntityRenderer<? super LocalPlayer> renderer = mc.m_91290_().m_114382_(player);
        if (!(renderer instanceof LivingEntityRenderer)) {
            return null;
        }
        Object model = ((LivingEntityRenderer<?, ?>) renderer).m_7200_();
        return model instanceof PlayerModel ? (PlayerModel<?>) model : null;
    }
}
