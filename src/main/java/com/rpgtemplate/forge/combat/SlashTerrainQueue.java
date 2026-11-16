package com.rpgtemplate.forge.combat;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.terrain.VoxelLine;
import com.rpgtemplate.forge.ModConfigs;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class SlashTerrainQueue {
    private static final Deque<SlashTerrainQueue.Job> JOBS = new ArrayDeque<>();
    private static final int MAX_JOBS = 8192;
    private static final int CHECKS = 4096;
    private static final int BREAKS = 256;
    private static final long NANOS = 3000000L;

    private SlashTerrainQueue() {
    }

    private static void submit(TerrainCutter var0, SlashTerrainQueue.Cursor var1) {
        if (JOBS.size() >= 8192) {
            var0.queueWarning();
        } else {
            JOBS.addLast(new SlashTerrainQueue.Job(var0, var1));
        }
    }

    public static void line(TerrainCutter var0, Vec3 var1, Vec3 var2, double var3) {
        submit(var0, new SlashTerrainQueue.Line(var1, var2, var3));
    }

    public static void box(TerrainCutter var0, final AABB var1) {
        submit(var0, new SlashTerrainQueue.Cursor() {
            final int minX = (int)Math.floor(var1.minX);
            final int maxX = (int)Math.floor(var1.maxX);
            final int minY = (int)Math.floor(var1.minY);
            final int maxY = (int)Math.floor(var1.maxY);
            final int minZ = (int)Math.floor(var1.minZ);
            final int maxZ = (int)Math.floor(var1.maxZ);
            int x = this.minX;
            int y = this.maxY;
            int z = this.minZ;

            @Override
            public BlockPos next() {
                if (this.y < this.minY) {
                    return null;
                } else {
                    BlockPos var1x = new BlockPos(this.x, this.y, this.z);
                    if (++this.z > this.maxZ) {
                        this.z = this.minZ;
                        if (++this.x > this.maxX) {
                            this.x = this.minX;
                            this.y--;
                        }
                    }

                    return var1x;
                }
            }
        });
    }

    public static void sweep(
        TerrainCutter var0, final Vec3 var1, final Vec3 var2, final Vec3 var3, final Vec3 var4, final double var5, final int var7, final double var8
    ) {
        submit(
            var0,
            new SlashTerrainQueue.Cursor() {
                final int steps = Math.max(1, (int)Math.ceil(var2.subtract(var1).length() / 0.5));
                int step;
                int band;
                SlashTerrainQueue.Line current;

                @Override
                public BlockPos next() {
                    while (this.step <= this.steps) {
                        if (this.current == null) {
                            Vec3 var1x = var1.add(var2.subtract(var1).scale((double)this.step / (double)this.steps))
                                .add(var4.scale(((double)this.band - (double)(var7 - 1) * 0.5) * var8));
                            this.current = new SlashTerrainQueue.Line(var1x, var3, var5);
                        }

                        BlockPos var2x = this.current.next();
                        if (var2x != null) {
                            return var2x;
                        }

                        this.current = null;
                        if (++this.band >= var7) {
                            this.band = 0;
                            this.step++;
                        }
                    }

                    return null;
                }
            }
        );
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post var0) {
        if (!JOBS.isEmpty()) {
            int var1 = Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameChecks.get() / 4);
            int var2 = Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameBlocks.get() / 4);
            long var3 = System.nanoTime();
            int var5 = 0;
            int var6 = 0;

            while (!JOBS.isEmpty() && var5 < var1 && var6 < var2 && System.nanoTime() - var3 < 3000000L) {
                SlashTerrainQueue.Job var7 = JOBS.removeFirst();
                if (var7.cutter.queueValid() && !var7.cutter.exhausted()) {
                    if (++var7.slices > 120000) {
                        var7.cutter.queueWarning();
                    } else {
                        if (!var7.cutter.canWorkThisTick()) {
                            JOBS.addFirst(var7);
                            break;
                        }

                        boolean var8 = false;
                        int var9 = 0;

                        while (true) {
                            if (var9 < 128
                                && var5 < var1
                                && var6 < var2
                                && System.nanoTime() - var3 < 3000000L
                                && !var7.cutter.exhausted()
                                && var7.cutter.canWorkThisTick()) {
                                BlockPos var10 = var7.cursor.next();
                                if (var10 != null) {
                                    var5++;
                                    if (var7.cutter.cut(var10)) {
                                        var6++;
                                    }

                                    var9++;
                                    continue;
                                }

                                var8 = true;
                            }

                            if (!var8 && !var7.cutter.exhausted()) {
                                JOBS.addLast(var7);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent var0) {
        JOBS.clear();
    }

    private interface Cursor {
        BlockPos next();
    }

    private static final class Job {
        final TerrainCutter cutter;
        final SlashTerrainQueue.Cursor cursor;
        int slices;

        Job(TerrainCutter var1, SlashTerrainQueue.Cursor var2) {
            this.cutter = var1;
            this.cursor = var2;
        }
    }

    private static final class Line implements SlashTerrainQueue.Cursor {
        final Vec3 center;
        final Vec3 side;
        final double length;
        Iterator<VoxelLine.Cell> cells;
        VoxelLine.Cell cell;
        int face = 7;
        private static final int[] DX = new int[]{0, 1, -1, 0, 0, 0, 0};
        private static final int[] DY = new int[]{0, 0, 0, 1, -1, 0, 0};
        private static final int[] DZ = new int[]{0, 0, 0, 0, 0, 1, -1};

        Line(Vec3 var1, Vec3 var2, double var3) {
            this.center = var1;
            this.side = var2;
            this.length = var3;
        }

        @Override
        public BlockPos next() {
            if (this.cells == null) {
                Vec3 var1 = this.side.normalize().scale(this.length * 0.5);
                Vec3 var2 = this.center.subtract(var1);
                Vec3 var3 = this.center.add(var1);
                this.cells = VoxelLine.cells(var2.x, var2.y, var2.z, var3.x, var3.y, var3.z).iterator();
            }

            if (this.face == 7) {
                if (!this.cells.hasNext()) {
                    return null;
                }

                this.cell = this.cells.next();
                this.face = 0;
            }

            int var4 = this.face++;
            return new BlockPos(this.cell.x() + DX[var4], this.cell.y() + DY[var4], this.cell.z() + DZ[var4]);
        }
    }
}
