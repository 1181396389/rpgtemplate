package com.rpgtemplate.forge.possession;

import com.rpgtemplate.forge.ext.ShrineEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SoulRealm {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("rpgtemplate", "soul_realm"));
    public static final int RADIUS = 20;
    public static final int FLOOR_Y = 64;
    private static final Set<Integer> BUILT = new HashSet<>();
    private static final Map<Integer, ShrineEntity> SHRINES = new HashMap<>();

    private SoulRealm() {
    }

    public static ServerLevel level(MinecraftServer var0) {
        return var0.getLevel(KEY);
    }

    public static BlockPos center(int var0) {
        return new BlockPos(var0 * 4096, 64, 0);
    }

    public static boolean inRealm(ServerPlayer var0) {
        return var0.level().dimension() == KEY;
    }

    public static void reset() {
        BUILT.clear();
        SHRINES.clear();
    }

    public static boolean enter(ServerPlayer var0, int var1) {
        ServerLevel var2 = level(var0.getServer());
        if (var2 == null) {
            return false;
        } else {
            BlockPos var3 = center(var1);
            ensureBuilt(var2, var1);
            Possession.tp(var0, var2, (double)var3.getX() + 0.5, 64.0, (double)var3.getZ() + 7.5, 180.0F, 0.0F);
            ensureShrine(var2, var1, var0);
            return true;
        }
    }

    public static void clamp(ServerPlayer var0, int var1) {
        BlockPos var2 = center(var1);
        double var3 = var0.getX() - ((double)var2.getX() + 0.5);
        double var5 = var0.getZ() - ((double)var2.getZ() + 0.5);
        double var7 = Math.sqrt(var3 * var3 + var5 * var5);
        if (var7 > 21.5 || var0.getY() < 61.0 || var0.getY() > 104.0) {
            Possession.tp(var0, var0.serverLevel(), (double)var2.getX() + 0.5, 64.0, (double)var2.getZ() + 7.5, var0.getYRot(), var0.getXRot());
            var0.fallDistance = 0.0F;
        }
    }

    public static void ensureBuilt(ServerLevel var0, int var1) {
        if (!BUILT.contains(var1) || var0.getBlockState(center(var1).below()).getBlock() == Blocks.AIR) {
            build(var0, center(var1), var1);
            BUILT.add(var1);
        }
    }

    public static void ensureShrine(ServerLevel var0, int var1, ServerPlayer var2) {
        ShrineEntity var3 = SHRINES.get(var1);
        if (var3 == null || var3.isRemoved() || var3.level() != var0) {
            BlockPos var4 = center(var1);
            var3 = ShrineEntity.create(var2, (double)var4.getX() + 0.5, 64.0, (double)var4.getZ() - 9.5, 0.0F, 0.9F);
            var3.markRealm();
            var0.addFreshEntity(var3);
            SHRINES.put(var1, var3);
        }
    }

    private static void build(ServerLevel var0, BlockPos var1, int var2) {
        RandomSource var3 = RandomSource.create((long)var2 * 7919L + 13L);
        byte var4 = 20;
        byte var5 = 2;
        BlockState var6 = Blocks.BARRIER.defaultBlockState();

        for (int var7 = -var4 - 3; var7 <= var4 + 3; var7++) {
            for (int var8 = -var4 - 3; var8 <= var4 + 3; var8++) {
                double var9 = Math.sqrt((double)(var7 * var7 + var8 * var8));
                BlockPos var11 = var1.offset(var7, 0, var8);
                if (!(var9 <= (double)var4 + 0.5)) {
                    if (var9 <= (double)var4 + 2.5) {
                        for (int var22 = -3; var22 <= 40; var22++) {
                            var0.setBlock(var11.above(var22), var6, var5);
                        }
                    }
                } else {
                    var0.setBlock(var11.below(3), Blocks.BLACKSTONE.defaultBlockState(), var5);
                    var0.setBlock(var11.below(2), Blocks.RED_CONCRETE.defaultBlockState(), var5);
                    boolean var12 = var9 > (double)var4 - 3.5 || var8 < -5 && Math.abs(var7) < 9 && var8 > -15;
                    if (var12) {
                        float var13 = var3.nextFloat();
                        BlockState var14 = var13 < 0.35F
                            ? Blocks.BONE_BLOCK.defaultBlockState()
                            : (var13 < 0.7F ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                        var0.setBlock(var11.below(), var14, var5);
                    } else {
                        var0.setBlock(var11.below(), Blocks.RED_STAINED_GLASS.defaultBlockState(), var5);
                    }

                    for (int var24 = 0; var24 < 40; var24++) {
                        var0.setBlock(var11.above(var24), Blocks.AIR.defaultBlockState(), var5);
                    }

                    var0.setBlock(var11.above(40), var6, var5);
                }
            }
        }

        for (int var17 = 0; var17 < 46; var17++) {
            double var19 = var3.nextDouble() * Math.PI * 2.0;
            double var10 = 6.0 + var3.nextDouble() * (double)(var4 - 7);
            int var23 = Mth.floor(Math.cos(var19) * var10);
            int var25 = Mth.floor(Math.sin(var19) * var10);
            if ((Math.abs(var23) >= 3 || var25 <= 3 || var25 >= 11) && (Math.abs(var23) >= 10 || var25 >= -4 || var25 <= -16)) {
                BlockPos var26 = var1.offset(var23, 0, var25);
                int var15 = 1 + var3.nextInt(3);

                for (int var16 = 0; var16 < var15; var16++) {
                    var0.setBlock(
                        var26.above(var16),
                        (BlockState)Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, var3.nextBoolean() ? Axis.X : Axis.Z),
                        var5
                    );
                }

                BlockState var27 = var3.nextInt(3) == 0 ? Blocks.WITHER_SKELETON_SKULL.defaultBlockState() : Blocks.SKELETON_SKULL.defaultBlockState();
                var0.setBlock(var26.above(var15), (BlockState)var27.setValue(SkullBlock.ROTATION, var3.nextInt(16)), var5);
            }
        }

        for (int var18 = 0; var18 < 16; var18++) {
            double var20 = (double)var18 * Math.PI * 2.0 / 16.0;
            BlockPos var21 = var1.offset(Mth.floor(Math.cos(var20) * ((double)var4 - 1.5)), 0, Mth.floor(Math.sin(var20) * ((double)var4 - 1.5)));
            var0.setBlock(var21, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), var5);
            var0.setBlock(var21.above(), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), var5);
            var0.setBlock(var21.above(2), Blocks.SOUL_LANTERN.defaultBlockState(), var5);
        }

        var0.setBlock(var1.offset(-4, 0, -4), Blocks.SOUL_CAMPFIRE.defaultBlockState(), var5);
        var0.setBlock(var1.offset(4, 0, -4), Blocks.SOUL_CAMPFIRE.defaultBlockState(), var5);
    }

    public static boolean keepShrine(ShrineEntity var0) {
        int var1 = (int)Math.round(var0.getX() / 4096.0);
        return Possession.realmOccupied(var1) || var0.level().getNearestPlayer(var0, 64.0) != null;
    }
}
