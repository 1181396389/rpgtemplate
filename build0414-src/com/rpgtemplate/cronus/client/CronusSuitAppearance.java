package com.rpgtemplate.cronus.client;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 假面骑士 Cronus 客户端变身外观同步表与渲染切换控制：
 * <ul>
 *   <li>本地玩家实时读取 {@link ClientSystemData}，零延迟生效（含背包人物预览与第三人称切换）。</li>
 *   <li>联机其他玩家通过 {@code CronusFxMsg.KIND_SUIT_STATE} 实时同步变身状态。</li>
 *   <li>变身期间隐藏原版皮肤部件并替换第一人称手臂为 Cronus 黑绿装甲护臂（原版护甲视觉同步由 {@code CronusArmorHideMixin} 隐藏，防御属性完全保留）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class CronusSuitAppearance {
    private static final Map<Integer, Boolean> REMOTE_TRANSFORMED = new ConcurrentHashMap<>();

    private static boolean hidingVanilla;
    private static final boolean[] SAVED_VIS = new boolean[12];

    private CronusSuitAppearance() {
    }

    public static void setRemoteTransformed(int entityId, boolean transformed) {
        if (transformed) {
            REMOTE_TRANSFORMED.put(entityId, Boolean.TRUE);
        } else {
            REMOTE_TRANSFORMED.remove(entityId);
        }
    }

    public static void clear() {
        REMOTE_TRANSFORMED.clear();
    }

    /** 判断指定实体当前在客户端视角是否处于假面骑士 Cronus 变身装甲形态。 */
    public static boolean isTransformed(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ != null && player.m_19879_() == mc.f_91074_.m_19879_()) {
            PlayerSystemData local = ClientSystemData.get();
            if (local != null && local.isActivated() && CronusKeys.TEMPLATE_ID.equals(local.loadedTemplateId())) {
                return CronusKeys.isTransformed(local);
            }
            return false;
        }
        return Boolean.TRUE.equals(REMOTE_TRANSFORMED.get(player.m_19879_()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!isTransformed(player)) {
            return;
        }
        ModelPart[] parts = allParts(event.getRenderer().m_7200_());
        for (int i = 0; i < parts.length; i++) {
            SAVED_VIS[i] = parts[i].f_104207_;
            parts[i].f_104207_ = false;
        }
        hidingVanilla = true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!hidingVanilla) {
            return;
        }
        hidingVanilla = false;
        ModelPart[] parts = allParts(event.getRenderer().m_7200_());
        for (int i = 0; i < parts.length; i++) {
            parts[i].f_104207_ = SAVED_VIS[i];
        }
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        if (!isTransformed(player)) {
            return;
        }
        event.setCanceled(true);
        CronusSuitModel.get().renderFirstPersonArm(
            event.getPoseStack(),
            event.getMultiBufferSource(),
            event.getPackedLight(),
            OverlayTexture.f_118083_,
            event.getArm());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static ModelPart[] allParts(PlayerModel<AbstractClientPlayer> model) {
        return new ModelPart[] {
            model.f_102808_, model.f_102809_, model.f_102810_,
            model.f_102811_, model.f_102812_, model.f_102813_, model.f_102814_,
            model.f_103374_, model.f_103375_, model.f_103376_, model.f_103377_, model.f_103378_,
        };
    }
}
