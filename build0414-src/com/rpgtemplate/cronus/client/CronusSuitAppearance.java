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
 *   <li>本地玩家实时读取 {@link ClientSystemData}，联机玩家通过 {@code CronusFxMsg.KIND_SUIT_STATE} 实时同步变身状态与动画延时。</li>
 *   <li><b>变身动画过后再出现盔甲</b>：变身演出播放期间（完整 7.8s 至 150 ticks / 快捷 2.2s 至 39 ticks）保持人形态并佩戴 3D 故障驱动器Ⅱ；
 *       动画收束后正式隐藏原版皮肤与护甲，显现假面骑士 Cronus 全套 3D 装甲及第一人称护臂！</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class CronusSuitAppearance {
    private static final class SuitState {
        final long revealAtTick;
        final long beltGashatAtTick;

        SuitState(long revealAtTick, long beltGashatAtTick) {
            this.revealAtTick = revealAtTick;
            this.beltGashatAtTick = beltGashatAtTick;
        }
    }

    private static final Map<Integer, SuitState> SUIT_STATES = new ConcurrentHashMap<>();

    private static boolean hidingVanilla;
    private static final boolean[] SAVED_VIS = new boolean[12];

    private CronusSuitAppearance() {
    }

    private static long clientGameTick() {
        Minecraft mc = Minecraft.m_91087_();
        return mc.f_91073_ != null ? mc.f_91073_.m_46467_() : 0L;
    }

    public static void setSuitState(int entityId, boolean transformed, int revealDelayTicks, int beltGashatDelayTicks) {
        if (transformed) {
            long now = clientGameTick();
            SUIT_STATES.put(entityId, new SuitState(
                now + Math.max(0, revealDelayTicks),
                now + Math.max(0, beltGashatDelayTicks)));
        } else {
            SUIT_STATES.remove(entityId);
        }
    }

    public static void clear() {
        SUIT_STATES.clear();
    }

    /** 判断指定实体当前在逻辑上是否处于假面骑士 Cronus 变身态（含正在播放变身动画期间）。 */
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
        return SUIT_STATES.containsKey(player.m_19879_());
    }

    /**
     * 判断指定实体当前是否应该显现假面骑士 Cronus 全套 3D 装甲并隐藏原版人物与护甲。
     * 仅在变身动画结束后返回 {@code true}！
     */
    public static boolean isSuitVisible(LivingEntity entity) {
        if (!isTransformed(entity)) {
            return false;
        }
        long now = clientGameTick();
        SuitState st = SUIT_STATES.get(entity.m_19879_());
        if (st != null) {
            return now >= st.revealAtTick;
        }
        // 兜底（如重进存档时尚未收到首包）：对比本地同步的揭晓 tick
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ != null && entity.m_19879_() == mc.f_91074_.m_19879_()) {
            PlayerSystemData local = ClientSystemData.get();
            if (local != null) {
                long revealTick = Math.round(local.value(CronusKeys.KEY_SUIT_REVEAL_TICK, 0.0));
                return revealTick <= 0L || now >= revealTick;
            }
        }
        return true;
    }

    /** 是否正处于变身动画演出阶段（已触发变身但 3D 装甲尚未显现，此时人形态腰间显现 3D 故障驱动器Ⅱ）。 */
    public static boolean isHenshinAnimating(LivingEntity entity) {
        return isTransformed(entity) && !isSuitVisible(entity);
    }

    /** 变身动画期间，腰间 3D 故障驱动器Ⅱ是否已接住并点亮插卡显示（右键变身立即点亮，技能变身在卡带精准飞入腰带瞬间点亮）。 */
    public static boolean isHenshinBeltGashatDocked(LivingEntity entity) {
        if (!isTransformed(entity)) {
            return false;
        }
        long now = clientGameTick();
        SuitState st = SUIT_STATES.get(entity.m_19879_());
        if (st != null) {
            return now >= st.beltGashatAtTick;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ != null && entity.m_19879_() == mc.f_91074_.m_19879_()) {
            PlayerSystemData local = ClientSystemData.get();
            if (local != null) {
                long dockTick = Math.round(local.value(CronusKeys.KEY_BELT_GASHAT_TICK, 0.0));
                return dockTick <= 0L || now >= dockTick;
            }
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!isSuitVisible(player)) {
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
        if (!isSuitVisible(player)) {
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
