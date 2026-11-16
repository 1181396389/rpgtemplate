package com.rpgtemplate.forge.ext;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FugaImpact {
    private FugaImpact() {
    }

    public static FugaBlastEntity explode(ServerPlayer var0, ServerLevel var1, Vec3 var2, Entity var3, FugaImpact.Spec var4) {
        double var5 = var4.radius();
        int var7 = 0;
        if (var3 != null && var3.isAlive() && TargetUtil.valid(var0, var3)) {
            if (TargetUtil.hurt(var3, ModDamageTypes.technique(var0), var4.damage)) {
                var7++;
            }

            var3.igniteForSeconds(var4.fireSeconds);
        }

        AABB var8 = new AABB(var2, var2).inflate(var5);

        for (Entity var10 : var1.getEntities(var0, var8, var2x -> var2x != var3 && TargetUtil.valid(var0, var2x))) {
            Vec3 var11 = TargetUtil.center(var10);
            double var12 = var11.distanceTo(var2);
            if (!(var12 > var5)) {
                double var14 = 1.0 - 0.6 * (var12 / var5);
                if (TargetUtil.hurt(var10, ModDamageTypes.technique(var0), (float)((double)var4.damage * var4.aoeScale * var14))) {
                    var7++;
                }

                var10.igniteForSeconds(var4.fireSeconds);
                Vec3 var16 = var11.subtract(var2);
                var16 = var16.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : var16.normalize();
                double var17 = var4.knockback * var14;
                var10.push(var16.x * var17, 0.35 + 0.5 * var17, var16.z * var17);
                var10.hurtMarked = true;
            }
        }

        var1.sendParticles(ParticleTypes.EXPLOSION_EMITTER, var2.x, var2.y, var2.z, var4.open ? 6 : 3, var5 * 0.25, var5 * 0.15, var5 * 0.25, 0.0);
        var1.sendParticles(ParticleTypes.FLAME, var2.x, var2.y, var2.z, var4.open ? 600 : 300, var5 * 0.4, var5 * 0.3, var5 * 0.4, 0.35);
        var1.sendParticles(ParticleTypes.LAVA, var2.x, var2.y, var2.z, var4.open ? 120 : 60, var5 * 0.3, var5 * 0.2, var5 * 0.3, 0.0);
        var1.sendParticles(ParticleTypes.LARGE_SMOKE, var2.x, var2.y + var5 * 0.3, var2.z, var4.open ? 240 : 120, var5 * 0.5, var5 * 0.4, var5 * 0.5, 0.06);
        float var19 = (float)Math.min(16.0, 4.0 + var5 * 0.5);
        var1.playSound(null, var2.x, var2.y, var2.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, var19, 0.55F);
        var1.playSound(null, var2.x, var2.y, var2.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, var19, 0.5F);
        if (var4.open) {
            var1.playSound(null, var2.x, var2.y, var2.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, var19 * 0.6F, 0.6F);
        }

        TerrainCutter var20 = null;
        double var21 = var5 * var4.craterRatio;
        if (var21 >= 1.0) {
            double var13 = (Math.PI * 4.0 / 3.0) * var21 * var21 * var21 * 0.85;
            int var15 = (int)Math.min((double)var4.maxBlocks, var13 + 64.0);
            var20 = TerrainCutter.forSlash(var0, var4.damage * (var4.open ? 3.0F : 1.5F), Math.max(1.0, (double)var15 / 160.0));
        }

        FugaBlastEntity var22 = FugaBlastEntity.create(var0, var1, var2, var5, var5 * var4.pillarHeightRatio, var4, var20, var21);
        var1.addFreshEntity(var22);
        Msg.actionBar(
            var0,
            Msg.tr(var4.open ? "skill.kamino.impact_open" : "skill.kamino.impact", String.format("%.1f", var4.damage), String.format("%.0f", var5), var7)
                .withStyle(var4.open ? ChatFormatting.GOLD : ChatFormatting.RED)
        );
        return var22;
    }

    public static final class Spec {
        public float damage;
        public boolean open;
        public double baseRadius = 6.0;
        public double radiusK = 0.55;
        public double radiusRef = 40.0;
        public double maxRadius = 24.0;
        public double aoeScale = 0.75;
        public double craterRatio = 0.75;
        public double pillarHeightRatio = 3.0;
        public double pillarRadiusRatio = 0.45;
        public double pillarDamageScale = 0.06;
        public double knockback = 1.6;
        public int fireSeconds = 8;
        public int lifetime = 80;
        public int blocksPerTick = 2500;
        public int maxBlocks = 60000;
        public int fireBlocks = 60;

        public double radius() {
            double var1 = this.baseRadius
                * (1.0 + this.radiusK * (Math.log(1.0 + (double)Math.max(0.0F, this.damage) / Math.max(1.0, this.radiusRef)) / Math.log(2.0)));
            return Math.max(this.baseRadius, Math.min(this.maxRadius, var1));
        }

        public static FugaImpact.Spec read(SkillContext var0, boolean var1, float var2) {
            FugaImpact.Spec var3 = new FugaImpact.Spec();
            var3.damage = var2;
            var3.open = var1;
            String var4 = var1 ? "open." : "explosion.";
            var3.baseRadius = var0.num(var4 + "base_radius", var1 ? 10.0 : 6.0);
            var3.radiusK = var0.num(var4 + "radius_k", var1 ? 0.65 : 0.55);
            var3.radiusRef = var0.num(var4 + "radius_ref", 40.0);
            var3.maxRadius = var0.num(var4 + "max_radius", var1 ? 48.0 : 24.0);
            var3.aoeScale = var0.num(var4 + "aoe_scale", var1 ? 0.85 : 0.75);
            var3.craterRatio = var0.num(var4 + "crater_ratio", var1 ? 0.85 : 0.75);
            var3.pillarHeightRatio = var0.num(var4 + "pillar_height_ratio", var1 ? 5.0 : 3.0);
            var3.pillarRadiusRatio = var0.num(var4 + "pillar_radius_ratio", var1 ? 0.5 : 0.45);
            var3.pillarDamageScale = var0.num(var4 + "pillar_damage_scale", var1 ? 0.08 : 0.06);
            var3.knockback = var0.num(var4 + "knockback", var1 ? 2.6 : 1.6);
            var3.fireSeconds = var0.integer(var4 + "fire_seconds", var1 ? 12 : 8);
            var3.lifetime = var0.integer(var4 + "lifetime_ticks", var1 ? 120 : 80);
            var3.blocksPerTick = var0.integer(var4 + "blocks_per_tick", var1 ? 4000 : 2500);
            var3.maxBlocks = var0.integer(var4 + "max_blocks", var1 ? 160000 : '\uea60');
            var3.fireBlocks = var0.integer(var4 + "fire_blocks", var1 ? 160 : 60);
            return var3;
        }
    }
}
