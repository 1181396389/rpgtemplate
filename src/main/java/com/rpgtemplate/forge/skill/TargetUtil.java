package com.rpgtemplate.forge.skill;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.rule.RuleCheck;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.entity.TechniqueEffectEntity;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.entity.PartEntity;

public final class TargetUtil {
    private TargetUtil() {
    }

    public static Vec3 rayEnd(ServerPlayer var0, double var1) {
        Vec3 var3 = var0.getEyePosition();
        Vec3 var4 = var3.add(var0.getLookAngle().scale(var1));
        BlockHitResult var5 = var0.level().clip(new ClipContext(var3, var4, Block.COLLIDER, Fluid.NONE, var0));
        return var5.getType() == Type.MISS ? var4 : var5.getLocation();
    }

    public static List<TargetUtil.Hit> ray(ServerPlayer var0, double var1, int var3, double var4) {
        Vec3 var6 = var0.getEyePosition();
        Vec3 var7 = rayEnd(var0, var1);
        AABB var8 = new AABB(var6, var7).inflate(1.0 + var4);
        ArrayList<TargetUtil.Hit> var9 = new ArrayList<>();

        for (Entity var11 : var0.level().getEntities(var0, var8, var1x -> valid(var0, var1x))) {
            AABB var12 = var11.getBoundingBox().inflate(var4 + (double)var11.getPickRadius());
            Optional<Vec3> var13 = var12.clip(var6, var7);
            if (var12.contains(var6)) {
                var9.add(new TargetUtil.Hit(var11, var6, 0.0));
            } else {
                var13.ifPresent(var3x -> var9.add(new TargetUtil.Hit(var11, var3x, var3x.distanceTo(var6))));
            }
        }

        var9.sort(Comparator.comparingDouble(TargetUtil.Hit::distance));
        ArrayList var16 = new ArrayList();
        HashSet var17 = new HashSet();

        for (TargetUtil.Hit var19 : var9) {
            LivingEntity var14 = living(var19.entity());
            int var15 = var14 != null ? var14.getId() : var19.entity().getId();
            if (var17.add(var15)) {
                var16.add(var19);
                if (var16.size() >= var3) {
                    break;
                }
            }
        }

        return var16;
    }

    public static boolean valid(ServerPlayer var0, Entity var1) {
        if (var1 == var0 || !var1.isAlive() || var1.isSpectator() || var1 instanceof TechniqueEffectEntity) {
            return false;
        } else if (!(var1 instanceof LivingEntity) && !(var1 instanceof PartEntity)) {
            return false;
        } else {
            if (var1 instanceof OwnableEntity var2 && var0.getUUID().equals(var2.getOwnerUUID())) {
                return false;
            }

            return true;
        }
    }

    public static LivingEntity living(Entity var0) {
        if (var0 instanceof LivingEntity) {
            return (LivingEntity)var0;
        } else {
            if (var0 instanceof PartEntity var1) {
                Entity var2 = var1.getParent();
                if (var2 instanceof LivingEntity) {
                    return (LivingEntity)var2;
                }
            }

            return null;
        }
    }

    public static boolean hurt(Entity var0, DamageSource var1, float var2) {
        if (!(var2 <= 0.0F) && var0.isAlive()) {
            var0.invulnerableTime = 0;
            LivingEntity var3 = living(var0);
            if (var3 != null) {
                var3.invulnerableTime = 0;
            }

            return var0.hurt(var1, var2);
        } else {
            return false;
        }
    }

    public static Vec3 center(Entity var0) {
        return var0.getBoundingBox().getCenter();
    }

    public static int ruleLevel(LivingEntity var0) {
        if (var0 instanceof ServerPlayer var1) {
            PlayerSystemData var2 = SystemDataManager.get(var1);
            if (var2.isActivated()) {
                return var2.level();
            }
        }

        String var3 = BuiltInRegistries.ENTITY_TYPE.getKey(var0.getType()).toString();
        Integer var4 = ModConfigs.bossRuleLevelMap().get(var3);
        return var4 != null ? var4 : RuleCheck.ruleLevelFromMaxHealth((double)var0.getMaxHealth());
    }

    public static Vec3 forward(float var0, float var1) {
        return Vec3.directionFromRotation(var1, var0);
    }

    public static Vec3 left(float var0) {
        float var1 = var0 * (float) (Math.PI / 180.0);
        return new Vec3((double)Mth.cos(var1), 0.0, (double)Mth.sin(var1));
    }

    public static Vec3 up(float var0, float var1) {
        return forward(var0, var1).cross(left(var0));
    }

    public static Vec3 perpendicular(float var0, float var1, float var2, double var3) {
        float var5 = var2 * (float) (Math.PI / 180.0);
        return left(var0).scale((double)(-Mth.sin(var5)) * var3).add(up(var0, var1).scale((double)Mth.cos(var5) * var3));
    }

    public static record Hit(Entity entity, Vec3 point, double distance) {
    }
}
