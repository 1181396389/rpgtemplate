package com.rpgtemplate.forge.ext.client;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.client.ClientSystemData;
import com.rpgtemplate.forge.ext.ExtNetwork;
import com.rpgtemplate.forge.ext.FugaArrowEntity;
import com.rpgtemplate.forge.ext.SukunaPassives;
import com.rpgtemplate.forge.system.TraitLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate",
    value = {Dist.CLIENT}
)
public final class ExtClientForge {
    private static int airJumps;
    private static boolean wasJumpDown;

    private ExtClientForge() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(Pre var0) {
        FugaArrowEntity var1 = FugaArrowEntity.CLIENT_HELD.get(var0.getEntity().getId());
        if (var1 != null && !var1.isRemoved() && var1.state() == 0) {
            PlayerModel var2 = (PlayerModel)var0.getRenderer().getModel();
            if (var0.getEntity().getMainArm() == HumanoidArm.RIGHT) {
                var2.rightArmPose = ArmPose.BOW_AND_ARROW;
            } else {
                var2.leftArmPose = ArmPose.BOW_AND_ARROW;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(LoggingOut var0) {
        FugaArrowEntity.CLIENT_HELD.clear();
        airJumps = 0;
    }

    public static int clientMaxJumps() {
        PlayerSystemData var0 = ClientSystemData.get();
        TraitEntry var1 = clientPassive(var0, "rpgtemplate:multi_jump");
        return var1 == null ? 1 : SukunaPassives.maxJumps(var1, SukunaPassives.templateLevel(var0));
    }

    private static TraitEntry clientPassive(PlayerSystemData var0, String var1) {
        if (var0 != null && var0.isActivated() && var0.loadedTemplateId() != null) {
            for (TraitEntry var3 : TemplateRegistry.client().effectivePassives(var0.loadedTemplateId())) {
                if (var3.type().equals(var1)) {
                    return TraitLookup.unlocked(var0, var3) ? var3 : null;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post var0) {
        if (true) {
            Minecraft var1 = Minecraft.getInstance();
            LocalPlayer var2 = var1.player;
            if (var2 != null && !var1.isPaused()) {
                if (ExtClientMod.RADIUS_UP != null) {
                    while (ExtClientMod.RADIUS_UP.consumeClick()) {
                        ExtNetwork.sendToServer(new ExtNetwork.DomainRadius(1, var2.isShiftKeyDown()));
                    }

                    while (ExtClientMod.RADIUS_DOWN.consumeClick()) {
                        ExtNetwork.sendToServer(new ExtNetwork.DomainRadius(-1, var2.isShiftKeyDown()));
                    }
                }

                boolean var3 = var1.options.keyJump.isDown();
                if (!var2.onGround()
                    && !var2.isInWater()
                    && !var2.isInLava()
                    && !var2.onClimbable()
                    && !var2.getAbilities().flying
                    && !var2.isPassenger()
                    && !var2.isFallFlying()) {
                    if (var3 && !wasJumpDown && var1.screen == null) {
                        PlayerSystemData var4 = ClientSystemData.get();
                        TraitEntry var5 = clientPassive(var4, "rpgtemplate:multi_jump");
                        if (var5 != null) {
                            int var6 = SukunaPassives.maxJumps(var5, SukunaPassives.templateLevel(var4));
                            if (airJumps < var6 - 1) {
                                airJumps++;
                                doAirJump(var2, TraitLookup.param(var5, "jump_power", 0.6));
                                ExtNetwork.sendToServer(new ExtNetwork.AirJump(airJumps));
                            }
                        }
                    }

                    wasJumpDown = var3;
                } else {
                    airJumps = 0;
                    wasJumpDown = var3;
                }
            }
        }
    }

    private static void doAirJump(LocalPlayer var0, double var1) {
        MobEffectInstance var3 = var0.getEffect(MobEffects.JUMP);
        if (var3 != null) {
            var1 += 0.1 * (double)(var3.getAmplifier() + 1);
        }

        Vec3 var4 = var0.getDeltaMovement();
        float var5 = var0.input.forwardImpulse;
        float var6 = var0.input.leftImpulse;
        double var7 = var4.x * 0.6;
        double var9 = var4.z * 0.6;
        if (Math.abs(var5) > 0.001F || Math.abs(var6) > 0.001F) {
            double var11 = Math.sqrt((double)(var5 * var5 + var6 * var6));
            double var13 = (double)var5 / var11;
            double var15 = (double)var6 / var11;
            double var17 = Math.toRadians((double)var0.getYRot());
            double var19 = var0.isSprinting() ? 0.42 : 0.32;
            var7 = (-Math.sin(var17) * var13 + Math.cos(var17) * var15) * var19;
            var9 = (Math.cos(var17) * var13 + Math.sin(var17) * var15) * var19;
        }

        var0.setDeltaMovement(var7, var1, var9);
        var0.fallDistance = 0.0F;

        for (int var21 = 0; var21 < 10; var21++) {
            double var12 = (double)var21 / 10.0 * Math.PI * 2.0;
            var0.level()
                .addParticle(
                    ParticleTypes.CLOUD,
                    var0.getX() + Math.cos(var12) * 0.4,
                    var0.getY(),
                    var0.getZ() + Math.sin(var12) * 0.4,
                    Math.cos(var12) * 0.08,
                    -0.02,
                    Math.sin(var12) * 0.08
                );
        }

        var0.playSound(SoundEvents.PHANTOM_FLAP, 0.5F, 1.6F + (float)airJumps * 0.05F);
    }
}
