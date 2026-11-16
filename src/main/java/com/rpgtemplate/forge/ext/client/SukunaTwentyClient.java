package com.rpgtemplate.forge.ext.client;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.forge.client.gui.UiPreferences;
import com.rpgtemplate.forge.ext.SukunaTwentyRegistry;
import com.rpgtemplate.forge.ext.WorldRiftEntity;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "rpgtemplate",
    bus = Bus.MOD,
    value = {Dist.CLIENT}
)
public final class SukunaTwentyClient {
    private static boolean forgeHooked;

    private SukunaTwentyClient() {
    }

    @SubscribeEvent
    public static void onRenderers(RegisterRenderers var0) {
        var0.registerEntityRenderer(SukunaTwentyRegistry.WORLD_RIFT, WorldRiftRenderer::new);
        if (!forgeHooked) {
            forgeHooked = true;
            NeoForge.EVENT_BUS.addListener(SukunaTwentyClient::onCameraAngles);
        }
    }

    static void onCameraAngles(ComputeCameraAngles var0) {
        if (!UiPreferences.reducedMotion && !WorldRiftEntity.CLIENT_SEEN.isEmpty()) {
            Minecraft var1 = Minecraft.getInstance();
            if (var1.level != null && var1.player != null && !var1.isPaused()) {
                double var2 = (double)var1.level.getGameTime() + var0.getPartialTick();
                Vec3 var4 = var0.getCamera().getPosition();
                float var5 = 0.0F;
                Iterator var6 = WorldRiftEntity.CLIENT_SEEN.iterator();

                while (var6.hasNext()) {
                    WorldRiftEntity var7 = (WorldRiftEntity)var6.next();
                    if (!var7.isRemoved() && var7.level() == var1.level) {
                        float var8 = (float)((double)var7.tickCount + var0.getPartialTick());
                        float var9 = Math.max(1.0F, var7.travelTicks());
                        float var10 = var8 < var9 ? 1.0F : (float)Math.exp((double)(-(var8 - var9) / 6.0F));
                        if (var8 < 3.0F) {
                            var10 *= 1.6F;
                        }

                        double var11 = var4.distanceTo(var7.position());
                        double var13 = (double)var7.range() + 48.0;
                        if (!(var11 > var13)) {
                            float var15 = (float)(1.0 / (1.0 + var11 / 24.0));
                            var5 = Math.max(var5, var10 * var15 * var7.power());
                        }
                    } else {
                        var6.remove();
                    }
                }

                if (!(var5 <= 0.01F)) {
                    float var16 = Math.min(2.2F, var5 * 2.2F);
                    float var17 = (float)(Math.sin(var2 * 2.9) * 0.6 + Math.sin(var2 * 5.3) * 0.4) * var16;
                    float var18 = (float)(Math.sin(var2 * 3.7 + 1.3) * 0.6 + Math.sin(var2 * 6.1) * 0.4) * var16;
                    float var19 = (float)Math.sin(var2 * 4.3 + 0.7) * var16 * 0.5F;
                    var0.setYaw(var0.getYaw() + var17);
                    var0.setPitch(var0.getPitch() + var18);
                    var0.setRoll(var0.getRoll() + var19);
                }
            }
        }
    }
}
