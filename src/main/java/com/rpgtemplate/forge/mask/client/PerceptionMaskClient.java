package com.rpgtemplate.forge.mask.client;
import net.neoforged.neoforge.common.util.TriState;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 认知阻碍面具客户端感知判定：
 * <ul>
 *   <li>其他玩家视线中，除非准星射线正对着佩戴者身体且无方块遮挡，否则佩戴者完全不可见。</li>
 *   <li>即便准星正对看见，名牌亦会被彻底隐藏，防止识别佩戴者身份。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT)
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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer viewer = mc.player;
        if (viewer == null || mc.level == null || target == viewer) {
            return false;
        }
        return !isCrosshairAimedAt(mc, viewer, target, partialTick);
    }

    /** 判定观察者的准星视线是否正对着目标玩家的插值包围盒且无固体遮挡。 */
    public static boolean isCrosshairAimedAt(Minecraft mc, LocalPlayer viewer,
                                             AbstractClientPlayer target, float pt) {
        double ix = Mth.lerp(pt, target.xo, target.getX());
        double iy = Mth.lerp(pt, target.yo, target.getY());
        double iz = Mth.lerp(pt, target.zo, target.getZ());
        AABB box = target.getBoundingBox()
            .move(ix - target.getX(), iy - target.getY(), iz - target.getZ())
            .inflate(CROSSHAIR_MARGIN);

        Vec3 eye = viewer.getEyePosition(pt);
        Vec3 look = viewer.getViewVector(pt).normalize();
        Vec3 end = eye.add(look.scale(MAX_PERCEPTION_RANGE));

        Optional<Vec3> hit = box.clip(eye, end);
        if (hit.isEmpty()) {
            return false;
        }
        Vec3 hitPos = hit.get();
        BlockHitResult blockHit = mc.level.clip(new ClipContext(
            eye, hitPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
        if (blockHit.getType() != HitResult.Type.MISS
            && blockHit.getLocation().distanceToSqr(eye) < hitPos.distanceToSqr(eye) - 0.05) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && target != mc.player && PerceptionBlockingMaskItem.isWearingMask(target)) {
            event.setCanRender(TriState.FALSE);
        }
    }
}
