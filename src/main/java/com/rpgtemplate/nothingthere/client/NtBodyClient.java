package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * Hides the vanilla arms and sleeves of a 一无所有 human: both arms are flesh (red) arm stubs drawn by NtBodyMesh.
 * Hiding happens in Pre and is undone in Post, so nametags and other layers are unaffected.
 */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class NtBodyClient {
    private static boolean hiding;
    /** How many parts the current render hid (4 for a human, all 12 for a cocoon). */
    private static int hidden;
    private static final boolean[] SAVED = new boolean[12];

    private NtBodyClient() {
    }

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        boolean cocoon = NtBody.inCocoon(player);
        if (!cocoon && !NtBody.isHuman(player)) {
            return;
        }
        // a human: only the arms and sleeves (the flesh arms are drawn by the layer). A cocoon: the whole body.
        ModelPart[] parts = parts(event.getRenderer().getModel());
        hidden = cocoon ? parts.length : 4;
        for (int i = 0; i < hidden; i++) {
            SAVED[i] = parts[i].visible;
            parts[i].visible = false;
        }
        hiding = true;
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post event) {
        if (!hiding) {
            return;
        }
        hiding = false;
        ModelPart[] parts = parts(event.getRenderer().getModel());
        for (int i = 0; i < hidden; i++) {
            parts[i].visible = SAVED[i];
        }
    }

    /**
     * The vanilla parts of the PlayerModel. The first four are rightArm, leftArm, leftSleeve, rightSleeve (the
     * human case hides only these); the rest are the body parts hidden for a cocoon.
     */
    private static ModelPart[] parts(PlayerModel<AbstractClientPlayer> model) {
        return new ModelPart[] {
            model.rightArm, model.leftArm, model.leftSleeve, model.rightSleeve,
            model.head, model.hat, model.body, model.rightLeg, model.leftLeg,
            model.leftPants, model.rightPants, model.jacket,
        };
    }
}
