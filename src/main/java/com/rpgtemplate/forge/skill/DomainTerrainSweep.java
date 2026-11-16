package com.rpgtemplate.forge.skill;

import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class DomainTerrainSweep {
    private static final Map<UUID, DomainTerrainSweep.Sweep> STATES = new HashMap<>();

    private DomainTerrainSweep() {
    }

    public static void clear(UUID var0) {
        STATES.remove(var0);
    }

    public static void reset() {
        STATES.clear();
    }

    public static int broken(UUID var0) {
        DomainTerrainSweep.Sweep var1 = STATES.get(var0);
        return var1 == null ? 0 : var1.broken;
    }

    public static int budgetAtRadius(double var0, int var2) {
        return var2 <= 0 ? 0 : (int)Math.min(1000000.0, Math.max(1024.0, (double)var2 * var0 * var0 / 10000.0));
    }

    public static void tick(SkillContext var0, Vec3 var1, double var2, long var4, int var6) {
        if (var0.bool("terrain.enabled", true) && TerrainCutter.allowed(var0.player)) {
            ServerLevel var7 = var0.player.serverLevel();
            int var8 = (int)Math.min(200.0, Math.max(2.0, Math.floor(var2)));
            DomainTerrainSweep.Sweep var9 = STATES.get(var0.player.getUUID());
            if (var9 == null || var9.radius != var8 || var9.level != var7 || var9.center.distanceToSqr(var1) > 0.01) {
                int var10 = var9 != null && var9.level == var7 && var9.center.distanceToSqr(var1) <= 0.01 ? var9.broken : 0;
                var9 = new DomainTerrainSweep.Sweep(var7, var1, var8, Math.max(1, Math.min(16, var0.integer("terrain.depth", 15))));
                var9.broken = var10;
                STATES.put(var0.player.getUUID(), var9);
            }

            int var31 = budgetAtRadius((double)var8, Math.max(0, var0.integer("terrain.max_blocks_at_100", 640000)));
            int var11 = Math.min(
                Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameBlocks.get() / 4 / Math.max(1, var6)),
                Math.max(0, var0.integer("terrain.blocks_per_tick", 256))
            );
            var11 = Math.min(var11, var31 - var9.broken);
            int var12 = (Integer)ModConfigs.SERVER.terrainMaxBlocksPerSlash.get();
            if (var11 > 0 && var12 > 0 && var9.cursor < (long)var9.total * 512L) {
                TerrainCutter var13 = TerrainCutter.forSlash(
                    var0.player, var0.damage(3.0 * DomainExpansionSkill.progressionScale(var0)), ((double)var11 + 0.1) / (double)var12
                );
                if (var13 != null) {
                    var13.quiet().sharedFrame().limit(var11);
                    int var14 = Math.min(
                        Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameChecks.get() / 4 / Math.max(1, var6)),
                        Math.max(0, var0.integer("terrain.checks_per_tick", 4096))
                    );
                    long var15 = System.nanoTime();

                    for (int var17 = 0;
                        var17 < var14
                            && var9.cursor < (long)var9.total * 512L
                            && !var13.exhausted()
                            && var13.canWorkThisTick()
                            && System.nanoTime() - var15 < 2000000L;
                        var17++
                    ) {
                        long var18 = (var9.cursor / 512L * (long)var9.stride + (long)var9.offset) % (long)var9.total;
                        int var20 = (int)(var9.cursor++ % 512L);
                        int var21 = (int)(var18 % (long)var9.nx);
                        int var22 = (int)(var18 / (long)var9.nx % (long)var9.nx);
                        int var23 = (int)(var18 / (long)(var9.nx * var9.nx));
                        int var24 = var9.minX + var21 * 8 + (var20 & 7);
                        int var25 = var9.minZ + var22 * 8 + (var20 >> 3 & 7);
                        int var26 = var9.top - var23 * 8 - (var20 >> 6 & 7);
                        double var27 = (double)var24 + 0.5 - var1.x;
                        double var29 = (double)var25 + 0.5 - var1.z;
                        if (var26 >= var9.bottom
                            && !(var27 * var27 + var29 * var29 > (double)(var8 * var8))
                            && var7.hasChunk(var24 >> 4, var25 >> 4)
                            && (!(Math.abs((double)var24 + 0.5 - var0.player.getX()) < 2.0) || !(Math.abs((double)var25 + 0.5 - var0.player.getZ()) < 2.0))) {
                            var13.cut(new BlockPos(var24, var26, var25));
                        }
                    }

                    var9.broken = var9.broken + var13.broken();
                    KingRules.addEmbers(var0, var13.broken());
                    if (var13.broken() > 0 && var4 - var9.lastFx >= 8L) {
                        var9.lastFx = var4;
                        Vec3 var33 = var13.lastBrokenPosition();
                        var7.sendParticles(ParticleTypes.EXPLOSION, var33.x, var33.y, var33.z, 3, 1.5, 1.5, 1.5, 0.0);
                        var7.sendParticles(ParticleTypes.CLOUD, var33.x, var33.y, var33.z, 3, 1.5, 1.5, 1.5, 0.2);
                        var7.playSound(null, var33.x, var33.y, var33.z, ModSounds.crush(), SoundSource.PLAYERS, 0.6F, 1.0F);
                    }
                }
            }
        }
    }

    private static int gcd(int var0, int var1) {
        while (var1 != 0) {
            int var2 = var0 % var1;
            var0 = var1;
            var1 = var2;
        }

        return var0;
    }

    private static final class Sweep {
        final ServerLevel level;
        final Vec3 center;
        final int radius;
        final int bottom;
        final int top;
        final int minX;
        final int minZ;
        final int nx;
        final int total;
        final int stride;
        final int offset;
        long cursor;
        long lastFx = -20L;
        int broken;

        Sweep(ServerLevel var1, Vec3 var2, int var3, int var4) {
            this.level = var1;
            this.center = var2;
            this.radius = var3;
            this.bottom = Math.max(var1.getMinBuildHeight(), (int)Math.floor(var2.y) - var4);
            this.top = Math.min(var1.getMaxBuildHeight() - 1, (int)Math.floor(var2.y + DomainExpansionSkill.planeHeight((double)var3)));
            this.minX = (int)Math.floor(var2.x) - var3;
            this.minZ = (int)Math.floor(var2.z) - var3;
            this.nx = (2 * var3 + 8) / 8;
            int var5 = Math.max(1, (this.top - this.bottom + 8) / 8);
            this.total = this.nx * this.nx * var5;
            int var6 = Math.max(1, (int)((double)this.total * 0.61803398875));

            while (DomainTerrainSweep.gcd(var6, this.total) != 1) {
                var6++;
            }

            this.stride = var6;
            this.offset = Math.floorMod(this.minX * 31 + this.minZ * 17, this.total);
        }
    }
}
