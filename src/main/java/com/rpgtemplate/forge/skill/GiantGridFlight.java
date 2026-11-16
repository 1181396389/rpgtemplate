package com.rpgtemplate.forge.skill;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.terrain.GridRaster;
import com.rpgtemplate.core.terrain.VoxelLine;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.king.KingGridEcho;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
public final class GiantGridFlight {
    private static final Map<UUID, GiantGridFlight.Flight> ACTIVE = new LinkedHashMap<>();
    private static final int GLOBAL_CHECKS = 4096;
    private static final int GLOBAL_BREAKS = 256;
    private static final long TERRAIN_NANOS = 3000000L;
    private static final int MAX_AGE = 6000;

    private GiantGridFlight() {
    }

    private static double[] xyz(Vec3 var0) {
        return new double[]{var0.x, var0.y, var0.z};
    }

    public static void launch(SkillContext var0, float var1) {
        ServerPlayer var2 = var0.player;
        if (ACTIVE.size() >= 16 && !ACTIVE.containsKey(var2.getUUID())) {
            var2.displayClientMessage(Component.literal("网格处理队列已满，请稍后重试。"), true);
        } else {
            GiantGridFlight.Flight var3 = ACTIVE.remove(var2.getUUID());
            if (var3 != null) {
                var2.displayClientMessage(Component.literal("新的网格已替换上一轮尚未完成的地形处理。"), true);
            }

            double var4 = Math.max(8.0, Math.min(200.0, var0.num("giant.size", 192.0)));
            double var6 = Math.max(1.0, Math.min(256.0, var0.num("giant.range", 192.0)));
            double var8 = Math.max(1.0, Math.min(8.0, var0.num("giant.speed", 4.0)));
            int var10 = Math.max(3, Math.min(49, var0.integer("giant.lines", 33)));
            if (var10 % 2 == 0) {
                var10++;
            }

            Vec3 var11 = var2.getLookAngle().normalize();
            Vec3 var12 = TargetUtil.left(var2.getYRot()).scale(-1.0);
            Vec3 var13 = TargetUtil.up(var2.getYRot(), var2.getXRot()).normalize();
            Vec3 var14 = var2.getEyePosition().add(var11.scale(1.8));
            Effects.giantGrid(var2, var14, var11, var2.getYRot(), var2.getXRot(), (float)var4, var10, var8, var6);
            KingGridEcho.launch(
                var2, var14, var11, var12, var13, var4, var10, var6, var8, var1 * 0.3F, Math.max(1, Math.min(1024, var0.integer("giant.pierce", 256)))
            );
            ACTIVE.put(
                var2.getUUID(),
                new GiantGridFlight.Flight(
                    var2,
                    var14,
                    var11,
                    var12,
                    var13,
                    var4,
                    var10,
                    var6,
                    var8,
                    var1,
                    Math.max(1, Math.min(1024, var0.integer("giant.pierce", 256))),
                    Math.max(0, Math.min(65536, var0.integer("giant.terrain_checks_per_tick", 65536))),
                    Math.max(0, Math.min(8192, var0.integer("giant.terrain_blocks_per_tick", 8192))),
                    Math.max(0, Math.min(1048576, var0.integer("giant.terrain_max_blocks", 262144)))
                )
            );
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post var0) {
        if (!ACTIVE.isEmpty()) {
            int var2 = Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameChecks.get() / 4);
            int var3 = Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameBlocks.get() / 4);
            long var4 = 3000000L;
            ArrayList<UUID> var6 = new ArrayList<>(ACTIVE.keySet());
            Collections.rotate(var6, 1);

            for (UUID var8 : var6) {
                GiantGridFlight.Flight var1 = ACTIVE.remove(var8);
                if (var1 != null) {
                    ACTIVE.put(var8, var1);
                }
            }

            int var25 = Math.max(1, ACTIVE.size());
            Iterator var26 = ACTIVE.values().iterator();

            while (var26.hasNext()) {
                GiantGridFlight.Flight var24 = (GiantGridFlight.Flight)var26.next();
                ServerPlayer var9 = var24.server.getPlayerList().getPlayer(var24.owner);
                if (var9 != null && var9.isAlive() && var9.serverLevel() == var24.level && ++var24.age <= 6000) {
                    double var10 = Math.min(var24.range, (double)Math.max(0, var24.age - 1) * var24.speed);
                    double var12 = Math.min(var24.range, (double)var24.age * var24.speed);
                    if (var10 < var24.range) {
                        var24.hitEntities(var9, var10, var12);
                    }

                    if (var24.checkLimit != 0 && var24.blockLimit != 0 && var24.maxBlocks != 0 && TerrainCutter.allowed(var9)) {
                        int var16 = Math.min(var24.checkLimit, var2 / Math.max(1, var25));
                        int var17 = Math.min(var24.blockLimit, var3 / Math.max(1, var25));
                        var25--;
                        int var18 = (Integer)ModConfigs.SERVER.terrainMaxBlocksPerSlash.get();
                        int var19 = Math.min(var17, var24.maxBlocks - var24.broken);
                        if (var18 <= 0) {
                            if (var12 >= var24.range) {
                                var26.remove();
                            }
                        } else if (var19 <= 0) {
                            if (var24.broken >= var24.maxBlocks) {
                                var9.displayClientMessage(Component.literal("网格已达到本次方块破坏上限（" + var24.maxBlocks + "），其余位置停止处理。"), true);
                                var26.remove();
                            }
                        } else {
                            TerrainCutter var20 = TerrainCutter.forSlash(var9, var24.damage, ((double)var19 + 0.1) / (double)var18);
                            if (var20 != null) {
                                var20.quiet().sharedFrame().limit(var19);
                                long var21 = System.nanoTime();

                                VoxelLine.Cell var14;
                                int var15;
                                for (var15 = 0;
                                    var15 < var16
                                        && !var20.exhausted()
                                        && var20.canWorkThisTick()
                                        && System.nanoTime() - var21 < var4
                                        && (var14 = var24.raster.next(var12)) != null;
                                    var15++
                                ) {
                                    BlockPos var23 = new BlockPos(var14.x(), var14.y(), var14.z());
                                    var20.cut(var23);
                                }

                                var4 = Math.max(0L, var4 - (System.nanoTime() - var21));
                                var2 -= var15;
                                var3 -= var20.broken();
                                var24.broken = var24.broken + var20.broken();
                                if (var20.broken() > 0 && var24.age - var24.lastImpactAge >= 8) {
                                    DismantleSkill.impactSound(var9, var20.lastBrokenPosition(), 1.0F);
                                    var24.lastImpactAge = var24.age;
                                }

                                if (var24.raster.finished()) {
                                    var26.remove();
                                }
                            }
                        }
                    } else {
                        if (var12 >= var24.range) {
                            var26.remove();
                        }

                        var25--;
                    }
                } else {
                    if (var9 != null && var24.age > 6000) {
                        var9.displayClientMessage(Component.literal("网格地形处理已达到安全时限。"), true);
                    }

                    var26.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent var0) {
        ACTIVE.clear();
    }

    public static boolean intersects(Vec3 var0, Vec3 var1, Vec3 var2, Vec3 var3, double var4, double var6, double var8, int var10, double var11) {
        double var13 = var0.dot(var1);
        double var15 = var0.dot(var2);
        double var17 = var0.dot(var3);
        double var19 = var8 * 0.5;
        if (!(var13 < var4 - var11) && !(var13 > var6 + var11) && !(Math.abs(var15) > var19 + var11) && !(Math.abs(var17) > var19 + var11)) {
            double var21 = var8 / ((double)var10 - 1.0);
            return wireDistance(var15, var19, var21) <= var11 + 0.25 || wireDistance(var17, var19, var21) <= var11 + 0.25;
        } else {
            return false;
        }
    }

    private static double wireDistance(double var0, double var2, double var4) {
        return Math.abs(var0 - (-var2 + Math.rint((var0 + var2) / var4) * var4));
    }

    private static final class Flight {
        final UUID owner;
        final MinecraftServer server;
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
        final int checkLimit;
        final int blockLimit;
        final int maxBlocks;
        final float damage;
        final GridRaster raster;
        final Set<Integer> hit = new HashSet<>();
        int age;
        int broken;
        int lastImpactAge = -20;

        Flight(
            ServerPlayer var1,
            Vec3 var2,
            Vec3 var3,
            Vec3 var4,
            Vec3 var5,
            double var6,
            int var8,
            double var9,
            double var11,
            float var13,
            int var14,
            int var15,
            int var16,
            int var17
        ) {
            this.owner = var1.getUUID();
            this.server = var1.getServer();
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
            this.checkLimit = var15;
            this.blockLimit = var16;
            this.maxBlocks = var17;
            this.raster = new GridRaster(
                GiantGridFlight.xyz(var2), GiantGridFlight.xyz(var3), GiantGridFlight.xyz(var4), GiantGridFlight.xyz(var5), var6, var8, var9
            );
        }

        void hitEntities(ServerPlayer var1, double var2, double var4) {
            if (this.hit.size() < this.pierce) {
                Vec3 var6 = this.origin.add(this.dir.scale(var2));
                Vec3 var7 = this.origin.add(this.dir.scale(var4));
                double var8 = this.size * 0.5;
                double var10 = (Math.abs(this.right.x) + Math.abs(this.up.x)) * var8 + 2.0;
                double var12 = (Math.abs(this.right.y) + Math.abs(this.up.y)) * var8 + 2.0;
                double var14 = (Math.abs(this.right.z) + Math.abs(this.up.z)) * var8 + 2.0;
                AABB var16 = new AABB(
                    Math.min(var6.x, var7.x) - var10,
                    Math.min(var6.y, var7.y) - var12,
                    Math.min(var6.z, var7.z) - var14,
                    Math.max(var6.x, var7.x) + var10,
                    Math.max(var6.y, var7.y) + var12,
                    Math.max(var6.z, var7.z) + var14
                );

                for (Entity var18 : this.level.getEntities(var1, var16, var1x -> TargetUtil.valid(var1, var1x))) {
                    LivingEntity var19 = TargetUtil.living(var18);
                    int var20 = var19 == null ? var18.getId() : var19.getId();
                    if (!this.hit.contains(var20)) {
                        double var21 = (double)Math.max(var18.getBbWidth(), var18.getBbHeight()) * 0.5;
                        if (GiantGridFlight.intersects(
                            TargetUtil.center(var18).subtract(this.origin), this.dir, this.right, this.up, var2, var4, this.size, this.lines, var21
                        )) {
                            this.hit.add(var20);
                            TargetUtil.hurt(var18, ModDamageTypes.technique(var1), this.damage);
                            if (this.hit.size() >= this.pierce) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
