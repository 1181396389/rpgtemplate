package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hides the vanilla arms and sleeves of a 一无所有 human: both arms are flesh (red) arm stubs drawn by NtBodyMesh.
 * Hiding happens in Pre and is undone in Post, so nametags and other layers are unaffected.
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
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
        ModelPart[] parts = parts(event.getRenderer().m_7200_());
        hidden = cocoon ? parts.length : 4;
        for (int i = 0; i < hidden; i++) {
            SAVED[i] = parts[i].f_104207_;
            parts[i].f_104207_ = false;
        }
        hiding = true;
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post event) {
        if (!hiding) {
            return;
        }
        hiding = false;
        ModelPart[] parts = parts(event.getRenderer().m_7200_());
        for (int i = 0; i < hidden; i++) {
            parts[i].f_104207_ = SAVED[i];
        }
    }

    /**
     * The vanilla parts of the PlayerModel. The first four are rightArm, leftArm, leftSleeve, rightSleeve (the
     * human case hides only these); the rest are the body parts hidden for a cocoon.
     */
    private static ModelPart[] parts(PlayerModel<AbstractClientPlayer> model) {
        return new ModelPart[] {
            model.f_102811_, model.f_102812_, model.f_103374_, model.f_103375_,
            model.f_102808_, model.f_102809_, model.f_102810_, model.f_102813_, model.f_102814_,
            model.f_103376_, model.f_103377_, model.f_103378_,
        };
    }
}
