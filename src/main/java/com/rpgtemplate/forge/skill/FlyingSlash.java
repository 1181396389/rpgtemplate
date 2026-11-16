package com.rpgtemplate.forge.skill;

import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.entity.Effects;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class FlyingSlash {
    private FlyingSlash() {
    }

    static int launch(
        ServerPlayer var0,
        FlyingSlash.Volley var1,
        Vec3 var2,
        Vec3 var3,
        float var4,
        float var5,
        float var6,
        float var7,
        int var8,
        double var9,
        double var11,
        TerrainCutter var13,
        Consumer<Entity> var14
    ) {
        return launch(var0, var1, new FlyingSlash.Spec(var2, var3, var4, var5, var6, var7, var8, var9, var11), var13, var14);
    }

    static int launch(ServerPlayer var0, FlyingSlash.Volley var1, FlyingSlash.Spec var2, TerrainCutter var3, Consumer<Entity> var4) {
        ServerLevel var5 = var0.serverLevel();
        Vec3 var6 = var2.dir.normalize();
        double var7 = 0.0;
        if (!var2.throughWalls && var3 == null) {
            for (double var9 = 0.5; var7 + 0.5 <= var2.range; var7 += 0.5) {
                Vec3 var11 = var2.origin.add(var6.scale(var7 + 0.5));
                if (TerrainCutter.blocks(var5, BlockPos.containing(var11), var3)) {
                    break;
                }
            }
        } else {
            var7 = var2.range;
        }

        var7 = Math.max(var7, 0.5);
        if (var3 != null) {
            var3.reserveSweep((double)var2.length, var7);
        }

        Vec3 var24 = var2.origin.add(var6.scale(var7));
        int var10 = Effects.flyingSlash(var0, var2.origin, var24, var2.yaw, var2.pitch, var2.roll, var2.length, var2.variant, var2.speed);
        Vec3 var25 = TargetUtil.perpendicular(var2.yaw, var2.pitch, var2.roll + 90.0F, 1.0);
        Vec3 var12 = TargetUtil.perpendicular(var2.yaw, var2.pitch, var2.roll, 1.0);
        double var13 = var7 / (double)var10;
        double var15 = (double)var2.length * 0.5;
        Vec3 var17 = var2.origin;

        for (int var18 = 1; var18 <= var10; var18++) {
            double var19 = (double)(var18 - 1) * var13;
            double var21 = (double)var18 * var13;
            SkillManager.schedule(var0, var18, () -> stepSegment(var0, var1, var17, var6, var25, var12, var19, var21, var15, var3, var4, var2));
        }

        return var10;
    }

    private static void stepSegment(
        ServerPlayer var0,
        FlyingSlash.Volley var1,
        Vec3 var2,
        Vec3 var3,
        Vec3 var4,
        Vec3 var5,
        double var6,
        double var8,
        double var10,
        TerrainCutter var12,
        Consumer<Entity> var13,
        FlyingSlash.Spec var14
    ) {
        if (var0.isAlive() && !var0.isRemoved()) {
            Vec3 var15 = var2.add(var3.scale(var6));
            Vec3 var16 = var2.add(var3.scale(var8));
            if (var1.pierceLeft > 0) {
                AABB var17 = new AABB(var15, var16).inflate(var10 + 0.3);
                List<Entity> var18 = var0.serverLevel().getEntities(var0, var17, var1x -> TargetUtil.valid(var0, var1x));
                var18.sort((var1x, var2x) -> Double.compare(var1x.distanceToSqr(var15), var2x.distanceToSqr(var15)));

                for (Entity var20 : var18) {
                    if (var1.pierceLeft <= 0) {
                        break;
                    }

                    if (!var1.hitIds.contains(var20.getId())) {
                        AABB var21 = var20.getBoundingBox();
                        double var22 = var10 + Math.max(var21.getXsize(), var21.getYsize()) * 0.5;
                        if (!(segmentDistance(TargetUtil.center(var20), var15, var16) > var22)) {
                            var1.hitIds.add(var20.getId());
                            if (TargetUtil.living(var20) != null) {
                                var1.hitIds.add(TargetUtil.living(var20).getId());
                            }

                            var1.pierceLeft--;
                            var13.accept(var20);
                        }
                    }
                }
            }

            if (var12 != null && !var12.exhausted()) {
                int var24 = Math.max(16, var14.cutLines);
                var12.onFirstTerrainHit(var14.onTerrainHit);
                var12.cutSweep(var15, var16, var4, var5, var10 * 2.0, var24, 1.0);
            }
        }
    }

    static double segmentDistance(Vec3 var0, Vec3 var1, Vec3 var2) {
        Vec3 var3 = var2.subtract(var1);
        double var4 = var3.lengthSqr();
        double var6 = var4 < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, var0.subtract(var1).dot(var3) / var4));
        return var0.distanceTo(var1.add(var3.scale(var6)));
    }

    static final class Spec {
        final Vec3 origin;
        final Vec3 dir;
        final float yaw;
        final float pitch;
        final float roll;
        final float length;
        final int variant;
        final double speed;
        final double range;
        int cutLines = 1;
        double cutSpacing = 0.6;
        boolean throughWalls;
        Consumer<Vec3> onTerrainHit;

        Spec(Vec3 var1, Vec3 var2, float var3, float var4, float var5, float var6, int var7, double var8, double var10) {
            this.origin = var1;
            this.dir = var2;
            this.yaw = var3;
            this.pitch = var4;
            this.roll = var5;
            this.length = var6;
            this.variant = var7;
            this.speed = var8;
            this.range = var10;
        }

        FlyingSlash.Spec cutLines(int var1, double var2) {
            this.cutLines = Math.max(1, var1);
            this.cutSpacing = var2;
            return this;
        }

        FlyingSlash.Spec throughWalls(boolean var1) {
            this.throughWalls = var1;
            return this;
        }

        FlyingSlash.Spec onTerrainHit(Consumer<Vec3> var1) {
            this.onTerrainHit = var1;
            return this;
        }
    }

    static final class Volley {
        final Set<Integer> hitIds = new HashSet<>();
        int pierceLeft;

        Volley(int var1) {
            this.pierceLeft = var1;
        }
    }
}
