package com.rpgtemplate.forge.king;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.skill.GiantGridFlight;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class KingGridEcho {
    private static final Map<UUID, KingGridEcho.Flight> FLIGHTS = new LinkedHashMap<>();

    public static void launch(
        ServerPlayer var0, Vec3 var1, Vec3 var2, Vec3 var3, Vec3 var4, double var5, int var7, double var8, double var10, float var12, int var13
    ) {
        if (KingRules.active(var0) && (FLIGHTS.size() < 16 || FLIGHTS.containsKey(var0.getUUID()))) {
            FLIGHTS.put(
                var0.getUUID(),
                new KingGridEcho.Flight(var0, var1, var2, var3.add(var4).normalize(), var4.subtract(var3).normalize(), var5, var7, var8, var10, var12, var13)
            );
        }
    }

    public static void storm(SkillContext var0, float var1) {
        double var2 = var0.num("grid.size", 12.0) * (1.0 + 3.0 * var0.num("grid.size_per_stage", 0.8));
        launch(
            var0.player,
            var0.player.getEyePosition(),
            var0.player.getLookAngle().normalize(),
            TargetUtil.left(var0.player.getYRot()).scale(-1.0),
            TargetUtil.up(var0.player.getYRot(), var0.player.getXRot()),
            var2,
            var0.integer("grid.lines", 8) + 3 * var0.integer("grid.lines_per_stage", 3),
            var0.num("range", 40.0),
            var0.num("flight.speed", 2.0),
            var1 * 0.3F,
            var0.integer("grid.pierce", 64)
        );
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post var0) {
        if (true) {
            Iterator var1 = FLIGHTS.values().iterator();

            while (var1.hasNext()) {
                KingGridEcho.Flight var2 = (KingGridEcho.Flight)var1.next();
                ServerPlayer var3 = var2.level.getServer().getPlayerList().getPlayer(var2.owner);
                if (var3 == null || !KingRules.active(var3) || !var3.isAlive() || var3.serverLevel() != var2.level) {
                    var1.remove();
                } else if (++var2.age >= 6) {
                    double var4 = (double)(var2.age - 6) * var2.speed;
                    double var6 = Math.min(var2.range, var4 + var2.speed);
                    if (var4 >= var2.range) {
                        var1.remove();
                    } else {
                        Vec3 var8 = var2.origin.add(var2.dir.scale(var6));
                        Vec3 var9 = var2.origin.add(var2.dir.scale(var4));
                        double var10 = var2.size * 0.5;
                        double var12 = (Math.abs(var2.right.x) + Math.abs(var2.up.x)) * var10 + 2.0;
                        double var14 = (Math.abs(var2.right.y) + Math.abs(var2.up.y)) * var10 + 2.0;
                        double var16 = (Math.abs(var2.right.z) + Math.abs(var2.up.z)) * var10 + 2.0;
                        AABB var18 = new AABB(
                            Math.min(var9.x, var8.x) - var12,
                            Math.min(var9.y, var8.y) - var14,
                            Math.min(var9.z, var8.z) - var16,
                            Math.max(var9.x, var8.x) + var12,
                            Math.max(var9.y, var8.y) + var14,
                            Math.max(var9.z, var8.z) + var16
                        );

                        for (Entity var20 : var2.level.getEntities(var3, var18, var1x -> TargetUtil.valid(var3, var1x))) {
                            LivingEntity var21 = TargetUtil.living(var20);
                            int var22 = var21 == null ? var20.getId() : var21.getId();
                            if (var2.hit.size() >= var2.pierce) {
                                break;
                            }

                            if (!var2.hit.contains(var22)
                                && GiantGridFlight.intersects(
                                    TargetUtil.center(var20).subtract(var2.origin),
                                    var2.dir,
                                    var2.right,
                                    var2.up,
                                    var4,
                                    var6,
                                    var2.size,
                                    var2.lines,
                                    (double)Math.max(var20.getBbWidth(), var20.getBbHeight()) * 0.5
                                )) {
                                var2.hit.add(var22);
                                TargetUtil.hurt(var20, ModDamageTypes.technique(var3), var2.damage);
                            }
                        }

                        if (var2.age % 4 == 2) {
                            int var25 = Math.min(9, var2.lines);
                            float var26 = var3.getYRot();
                            float var27 = var3.getXRot();

                            for (int var28 = 0; var28 < var25; var28++) {
                                double var23 = ((double)var28 / (double)(var25 - 1) - 0.5) * var2.size;
                                Effects.slash(var3, var8.add(var2.up.scale(var23)), var2.yaw, var2.pitch, 45.0F, (float)var2.size, 0.12F, 1, 5);
                                Effects.slash(var3, var8.add(var2.right.scale(var23)), var2.yaw, var2.pitch, -45.0F, (float)var2.size, 0.12F, 1, 5);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent var0) {
        FLIGHTS.clear();
    }

    private static final class Flight {
        final UUID owner;
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 dir;
        final Vec3 right;
        final Vec3 up;
        final double size;
        final double range;
        final double speed;
        final int lines;
        final int pierce;
        final float damage;
        final float yaw;
        final float pitch;
        final Set<Integer> hit = new HashSet<>();
        int age;

        Flight(ServerPlayer var1, Vec3 var2, Vec3 var3, Vec3 var4, Vec3 var5, double var6, int var8, double var9, double var11, float var13, int var14) {
            this.owner = var1.getUUID();
            this.level = var1.serverLevel();
            this.origin = var2;
            this.dir = var3;
            this.right = var4;
            this.up = var5;
            this.size = var6;
            this.lines = var8;
            this.range = var9;
            this.speed = var11;
            this.damage = var13;
            this.pierce = var14;
            this.yaw = var1.getYRot();
            this.pitch = var1.getXRot();
        }
    }
}
