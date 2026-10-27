package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hides the vanilla player parts while a 一无所有 human is drawn. The replacement body is drawn by
 * NtBodyLayer. Hiding happens in Pre and is undone in Post, so nametags and other layers are unaffected.
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class NtBodyClient {
    private static boolean hiding;
    private static final boolean[] SAVED = new boolean[12];

    private NtBodyClient() {
    }

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre event) {
        if (!NtBody.isHuman(event.getEntity())) {
            return;
        }
        ModelPart[] parts = parts(event.getRenderer().m_7200_());
        for (int i = 0; i < parts.length; i++) {
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
        for (int i = 0; i < parts.length; i++) {
            parts[i].f_104207_ = SAVED[i];
        }
    }

    private static ModelPart[] parts(PlayerModel<AbstractClientPlayer> model) {
        return new ModelPart[] {
            model.f_102808_, model.f_102809_, model.f_102810_, model.f_102811_, model.f_102812_,
            model.f_102813_, model.f_102814_, model.f_103374_, model.f_103375_, model.f_103376_,
            model.f_103377_, model.f_103378_,
        };
    }
}
