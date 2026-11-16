package com.rpgtemplate.forge.mask.client;

import com.rpgtemplate.forge.mask.PerceptionBlockingMaskItem;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 认知阻碍面具客户端感知判定：
 * <ul>
 *   <li>其他玩家视线中，除非准星射线正对着佩戴者身体且无方块遮挡，否则佩戴者完全不可见。</li>
 *   <li>即便准星正对看见，名牌亦会被彻底隐藏，防止识别佩戴者身份。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
public final class PerceptionMaskClient {
    private static final double MAX_PERCEPTION_RANGE = 128.0;
    /** 准星命中盒微小宽容边（0.14 格），保证准星对准头部/躯干/肩部时稳定显现，偏离即立刻从感知消失。 */
    private static final double CROSSHAIR_MARGIN = 0.14;

    private PerceptionMaskClient() {
    }

    /** 返回 {@code true} 表示当前本地玩家视角应完全隐藏该目标玩家。 */
    public static boolean shouldHideFromViewer(AbstractClientPlayer target, float partialTick) {
        if (target == null || !PerceptionBlockingMaskItem.isWearingMask(target)) {
            return false;
        }
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer viewer = mc.f_91074_;
        if (viewer == null || mc.f_91073_ == null || target == viewer) {
            return false;
        }
        return !isCrosshairAimedAt(mc, viewer, target, partialTick);
    }

    /** 判定观察者的准星视线是否正对着目标玩家的插值包围盒且无固体遮挡。 */
    public static boolean isCrosshairAimedAt(Minecraft mc, LocalPlayer viewer,
                                             AbstractClientPlayer target, float pt) {
        double ix = Mth.m_14139_(pt, target.f_19854_, target.m_20185_());
        double iy = Mth.m_14139_(pt, target.f_19855_, target.m_20186_());
        double iz = Mth.m_14139_(pt, target.f_19856_, target.m_20189_());
        AABB box = target.m_20191_()
            .m_82386_(ix - target.m_20185_(), iy - target.m_20186_(), iz - target.m_20189_())
            .m_82400_(CROSSHAIR_MARGIN);

        Vec3 eye = viewer.m_20299_(pt);
        Vec3 look = viewer.m_20252_(pt).m_82541_();
        Vec3 end = eye.m_82549_(look.m_82490_(MAX_PERCEPTION_RANGE));

        Optional<Vec3> hit = box.m_82371_(eye, end);
        if (hit.isEmpty()) {
            return false;
        }
        Vec3 hitPos = hit.get();
        BlockHitResult blockHit = mc.f_91073_.m_45547_(new ClipContext(
            eye, hitPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
        if (blockHit.m_6662_() != HitResult.Type.MISS
            && blockHit.m_82450_().m_82557_(eye) < hitPos.m_82557_(eye) - 0.05) {
            return false;
        }
        return true;
    }

    /** 任何其他玩家即使通过准星看到了佩戴者，也无法看到佩戴者的头顶名牌。 */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ != null && target != mc.f_91074_ && PerceptionBlockingMaskItem.isWearingMask(target)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
