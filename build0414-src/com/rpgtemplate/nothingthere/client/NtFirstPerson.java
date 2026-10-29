package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
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

/**
 * First-person hands for a 一无所有 human. Vanilla draws the first-person arm from the hand event. For the local
 * NT player with an empty hand, both arms are replaced: the right with the hammer, the left with the flesh arm
 * (and the blade in blade form or goodbye). Held items are left alone.
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
        if (player == null || !NtBody.isHuman(player) || !event.getItemStack().m_41619_()) {
            return;
        }
        // The event fires per hand. Map the hand to the arm side, so the hammer is always on the player's right.
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        boolean rightArm = mainHand == (player.m_5737_() == HumanoidArm.RIGHT);
        boolean goodbye = NtBody.goodbyePending(player);
        PlayerModel<?> model = playerModel(mc, player);
        if (model == null) {
            return;
        }
        event.setCanceled(true);

        VertexConsumer vc = event.getMultiBufferSource().m_6299_(model.m_103119_(WHITE));
        // The swing and equip progress are the vanilla ones for this hand (the hand placement is applied inside).
        // Hand size: 1:1. Scaling by the third-person body ratio (NtBody.HITBOX_*) pushed the hammer into the middle
        // of the view when tested offline, so the first-person hands keep the vanilla size. Change FP_SIZE to resize.
        NtBodyMesh.drawArmFirstPerson(event.getPoseStack(), vc, event.getPackedLight(), OverlayTexture.f_118083_,
            rightArm ? model.f_102811_ : model.f_102812_, rightArm,
            event.getSwingProgress(), event.getEquipProgress(), goodbye, NtBody.bladeForm(player),
            NtBody.whipForm(player), NtBody.muscleForm(player), FP_SIZE, FP_SIZE);
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
