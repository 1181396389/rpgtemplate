package com.rpgtemplate.forge.combat;

import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.system.PanelActions;
import com.rpgtemplate.forge.system.SystemDataManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

public final class TerrainCutter {
    public static final TagKey<Block> SLASH_IMMUNE = TagKey.create(Registries.BLOCK, RpgTemplateMod.id("slash_immune"));
    private static final ThreadLocal<Boolean> CUTTING = ThreadLocal.withInitial(() -> false);
    private final ServerPlayer player;
    private final ServerLevel level;
    private final double maxHardness;
    private final boolean drop;
    private final boolean blockEntities;
    private final boolean direct;
    private int budget;
    private int broken;
    private boolean quiet;
    private final LongOpenHashSet tried = new LongOpenHashSet();
    private final AABB protect;
    private boolean frameLimited;
    private Consumer<Vec3> firstHit;
    private boolean queueWarned;
    private Vec3 lastBrokenPosition;

    public TerrainCutter quiet() {
        this.quiet = true;
        return this;
    }

    public TerrainCutter limit(int var1) {
        this.budget = Math.min(this.budget, Math.max(0, var1));
        return this;
    }

    private TerrainCutter(ServerPlayer var1, double var2, int var4) {
        this.player = var1;
        this.level = var1.serverLevel();
        this.maxHardness = var2;
        this.budget = var4;
        this.drop = (Boolean)ModConfigs.SERVER.terrainDropBlocks.get();
        this.blockEntities = (Boolean)ModConfigs.SERVER.terrainBreakBlockEntities.get();
        this.direct = (Boolean)ModConfigs.SERVER.terrainDirectRemoval.get();
        this.protect = var1.getBoundingBox().inflate(0.3).expandTowards(0.0, -1.0, 0.0);
    }

    public static boolean isCutting() {
        return CUTTING.get();
    }

    public static boolean allowed(ServerPlayer var0) {
        if (!(Boolean)ModConfigs.SERVER.terrainDestruction.get()) {
            return false;
        } else if ((Boolean)ModConfigs.SERVER.terrainRespectMobGriefing.get() && !var0.serverLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        } else {
            return !var0.mayBuild() ? false : PanelActions.setting(SystemDataManager.get(var0), "terrain_destruction");
        }
    }

    public static TerrainCutter forSlash(ServerPlayer var0, float var1) {
        return forSlash(var0, var1, 1.0);
    }

    public static TerrainCutter forSlash(ServerPlayer var0, float var1, double var2) {
        if (!allowed(var0)) {
            return null;
        } else {
            double var4 = (Double)ModConfigs.SERVER.terrainBaseHardness.get();
            double var6 = (Double)ModConfigs.SERVER.terrainMaxHardness.get();
            double var8 = Math.min(Math.max(var6, 100.0), var4 * (1.0 + (double)Math.max(0.0F, var1) / 20.0));
            int var10 = (int)Math.round((double)((Integer)ModConfigs.SERVER.terrainMaxBlocksPerSlash.get()).intValue() * var2);
            if (var10 > 0) {
                var10 = Math.max(var10, 8192);
            }

            return var10 <= 0 ? null : new TerrainCutter(var0, var8, var10);
        }
    }

    public TerrainCutter reserveSweep(double var1, double var3) {
        if (this.budget > 0) {
            double var5 = Math.max(1.0, var1 + 2.0) * Math.max(1.0, var3 + 2.0) * 18.0;
            this.budget = Math.max(this.budget, (int)Math.min(1048576.0, Math.ceil(var5)));
        }

        return this;
    }

    public double maxHardness() {
        return this.maxHardness;
    }

    public int broken() {
        return this.broken;
    }

    public boolean exhausted() {
        return this.budget <= 0;
    }

    public boolean canCutState(BlockPos var1, BlockState var2) {
        if (var2.isAir()) {
            return false;
        } else if (var2.getBlock() instanceof LiquidBlock) {
            return false;
        } else if (var2.is(SLASH_IMMUNE)) {
            return false;
        } else {
            float var3 = var2.getDestroySpeed(this.level, var1);
            return !(var3 < 0.0F) && !((double)var3 > this.maxHardness) ? this.blockEntities || !var2.hasBlockEntity() : false;
        }
    }

    public static boolean blocks(ServerLevel var0, BlockPos var1, TerrainCutter var2) {
        if (!var0.isLoaded(var1)) {
            return true;
        } else {
            BlockState var3 = var0.getBlockState(var1);
            return var3.getCollisionShape(var0, var1).isEmpty() ? false : var2 == null || !var2.canCutState(var1, var3);
        }
    }

    private boolean remember(long var1) {
        if (this.tried.size() >= 65536) {
            this.tried.clear();
        }

        return this.tried.add(var1);
    }

    public TerrainCutter sharedFrame() {
        this.frameLimited = true;
        return this;
    }

    public boolean canWorkThisTick() {
        return !this.frameLimited || TerrainFrameBudget.available(this.player.getServer());
    }

    public boolean cut(BlockPos var1) {
        if (!this.canWorkThisTick()) {
            return false;
        } else if (!this.frameLimited) {
            return this.cutNow(var1);
        } else {
            int var2 = this.broken;
            long var3 = System.nanoTime();

            boolean var5;
            try {
                var5 = this.cutNow(var1);
            } finally {
                TerrainFrameBudget.account(System.nanoTime() - var3, this.broken - var2);
            }

            return var5;
        }
    }

    private boolean cutNow(BlockPos var1) {
        if (this.budget <= 0) {
            return false;
        } else if (!this.remember(var1.asLong())) {
            return false;
        } else if (this.level.isLoaded(var1) && !this.level.isOutsideBuildHeight(var1)) {
            if ((double)(var1.getX() + 1) > this.protect.minX
                && (double)var1.getX() < this.protect.maxX
                && (double)(var1.getY() + 1) > this.protect.minY
                && (double)var1.getY() < this.protect.maxY
                && (double)(var1.getZ() + 1) > this.protect.minZ
                && (double)var1.getZ() < this.protect.maxZ) {
                return false;
            } else {
                BlockState var2 = this.level.getBlockState(var1);
                if (!this.canCutState(var1, var2)) {
                    return false;
                } else if (!this.level.mayInteract(this.player, var1)) {
                    return false;
                } else if (this.direct && !var2.hasBlockEntity()) {
                    CUTTING.set(true);

                    try {
                        if (!this.level.setBlock(var1, this.level.getFluidState(var1).createLegacyBlock(), 2)) {
                            return false;
                        }
                    } finally {
                        CUTTING.set(false);
                    }

                    this.budget--;
                    this.broken++;
                    this.notifyFirstHit(var1);
                    return true;
                } else {
                    BreakEvent var3 = new BreakEvent(this.level, var1, var2, this.player);
                    CUTTING.set(true);

                    try {
                        NeoForge.EVENT_BUS.post(var3);
                    } finally {
                        CUTTING.set(false);
                    }

                    if (var3.isCanceled()) {
                        return false;
                    } else {
                        BlockEntity var4 = var2.hasBlockEntity() ? this.level.getBlockEntity(var1) : null;
                        if (this.drop || var4 != null) {
                            Block.dropResources(var2, this.level, var1, var4, this.player, ItemStack.EMPTY);
                        }

                        if (this.broken % (this.quiet ? 128 : 3) == 0) {
                            this.level.levelEvent(2001, var1, Block.getId(var2));
                        }

                        this.level.setBlock(var1, this.level.getFluidState(var1).createLegacyBlock(), 3);
                        this.budget--;
                        this.broken++;
                        this.notifyFirstHit(var1);
                        return true;
                    }
                }
            }
        } else {
            return false;
        }
    }

    public void cutLine(Vec3 var1, Vec3 var2, double var3) {
        if (!this.exhausted()) {
            SlashTerrainQueue.line(this.sharedFrame(), var1, var2, var3);
        }
    }

    public void cutBox(AABB var1) {
        if (!this.exhausted()) {
            SlashTerrainQueue.box(this.sharedFrame(), var1);
        }
    }

    public void cutSweep(Vec3 var1, Vec3 var2, Vec3 var3, Vec3 var4, double var5, int var7, double var8) {
        if (!this.exhausted()) {
            SlashTerrainQueue.sweep(this.sharedFrame(), var1, var2, var3, var4, var5, var7, var8);
        }
    }

    public Vec3 lastBrokenPosition() {
        return this.lastBrokenPosition;
    }

    public TerrainCutter onFirstTerrainHit(Consumer<Vec3> var1) {
        if (this.firstHit == null && this.broken == 0) {
            this.firstHit = var1;
        }

        return this;
    }

    private void notifyFirstHit(BlockPos var1) {
        this.lastBrokenPosition = new Vec3((double)var1.getX() + 0.5, (double)var1.getY() + 0.5, (double)var1.getZ() + 0.5);
        if (this.broken == 1 && this.firstHit != null) {
            Consumer var2 = this.firstHit;
            this.firstHit = null;
            var2.accept(new Vec3((double)var1.getX() + 0.5, (double)var1.getY() + 0.5, (double)var1.getZ() + 0.5));
        }
    }

    boolean queueValid() {
        return this.player.isAlive() && !this.player.isRemoved() && this.player.serverLevel() == this.level && allowed(this.player);
    }

    void queueWarning() {
        if (!this.queueWarned) {
            this.queueWarned = true;
            this.player.displayClientMessage(Component.literal("斩击地形队列达到安全上限，部分地形处理已取消。"), true);
        }
    }
}
