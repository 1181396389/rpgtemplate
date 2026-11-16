package com.rpgtemplate.forge.xp;
import com.rpgtemplate.forge.util.ItemNbt;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.xp.DiminishingTracker;
import com.rpgtemplate.core.xp.XpFormula;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.system.RuntimeState;
import com.rpgtemplate.forge.system.SystemActions;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.world.LongSetData;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags.Blocks;
import net.neoforged.neoforge.common.Tags.EntityTypes;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class XpEvents {
    public static final ResourceLocation SRC_KILL = RpgTemplateMod.id("kill");
    public static final ResourceLocation SRC_EAT = RpgTemplateMod.id("eat");
    public static final ResourceLocation SRC_MINE = RpgTemplateMod.id("mine");
    public static final ResourceLocation SRC_CRAFT = RpgTemplateMod.id("craft");
    public static final ResourceLocation SRC_SMELT = RpgTemplateMod.id("smelt");
    public static final ResourceLocation SRC_TRADE = RpgTemplateMod.id("trade");
    public static final ResourceLocation SRC_BREW = RpgTemplateMod.id("brew");
    public static final ResourceLocation SRC_ADVANCEMENT = RpgTemplateMod.id("advancement");
    public static final ResourceLocation SRC_EXPLORE = RpgTemplateMod.id("explore");
    private static final Map<UUID, Integer> TRADE_COUNTS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> TRADE_ITEMS = new ConcurrentHashMap<>();
    private static final Map<UUID, DiminishingTracker> REPEAT = new ConcurrentHashMap<>();

    private XpEvents() {
    }

    private static ServerPlayer owner(Object var0) {
        if (var0 instanceof ServerPlayer var1 && !var1.isSpectator() && SystemDataManager.get(var1).isActivated()) {
            return var1;
        }

        return null;
    }

    private static double repeat(ServerPlayer var0, String var1) {
        DiminishingTracker var2 = REPEAT.computeIfAbsent(
            var0.getUUID(),
            var0x -> new DiminishingTracker(
                    (Double)ModConfigs.SERVER.xpRepeatDecay.get(), (long)((Integer)ModConfigs.SERVER.xpRepeatWindow.get()).intValue(), 0.02
                )
        );
        return var2.hit(var1, var0.serverLevel().getGameTime());
    }

    public static void forget(UUID var0) {
        REPEAT.remove(var0);
        TRADE_COUNTS.remove(var0);
        TRADE_ITEMS.remove(var0);
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent var0) {
        if (var0.getSpawnType() == MobSpawnType.SPAWNER) {
            var0.getEntity().getPersistentData().putBoolean("rpgtemplate_spawner_origin", true);
        }
    }

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void onDeath(LivingDeathEvent var0) {
        ServerPlayer var1 = owner(var0.getSource().getEntity());
        if (var1 != null) {
            LivingEntity var2 = var0.getEntity();
            if (var2 != var1) {
                boolean var3 = var2.getType().is(EntityTypes.BOSSES);
                boolean var4 = var2.getPersistentData().getBoolean("rpgtemplate_spawner_origin");
                double var5 = XpFormula.kill(
                    (double)var2.getMaxHealth(),
                    (Double)ModConfigs.SERVER.xpKill.get(),
                    var3,
                    (Double)ModConfigs.SERVER.xpBossMultiplier.get(),
                    var4,
                    (Double)ModConfigs.SERVER.xpSpawnerMultiplier.get()
                );
                SystemActions.grantXp(var1, var5, SRC_KILL);
            }
        }
    }

    @SubscribeEvent
    public static void onEat(Finish var0) {
        ServerPlayer var1 = owner(var0.getEntity());
        if (var1 != null) {
            ItemStack var2 = var0.getItem();
            FoodProperties var3 = var2.getItem().getFoodProperties(var2, var1);
            if (var3 != null) {
                RuntimeState.State var4 = RuntimeState.of(var1.getUUID());
                String var5 = BuiltInRegistries.ITEM.getKey(var2.getItem()).toString();
                if (var5.equals(var4.lastFoodId)) {
                    var4.foodStreak++;
                } else {
                    var4.lastFoodId = var5;
                    var4.foodStreak = 0;
                }

                double var6 = Math.pow((Double)ModConfigs.SERVER.xpEatStreakDecay.get(), (double)var4.foodStreak);
                SystemActions.grantXp(
                    var1, XpFormula.eat(var3.nutrition(), var3.saturation(), (Double)ModConfigs.SERVER.xpEat.get()) * var6, SRC_EAT
                );
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void onBreak(BreakEvent var0) {
        if (var0.getLevel() instanceof ServerLevel var1 && !TerrainCutter.isCutting()) {
            ServerPlayer var9 = owner(var0.getPlayer());
            if (var9 != null && !var9.isCreative()) {
                long var3 = var0.getPos().asLong();
                if (!LongSetData.placed(var1).remove(var3)) {
                    BlockState var5 = var0.getState();
                    float var6 = var5.getDestroySpeed(var1, var0.getPos());
                    double var7 = XpFormula.mine(
                        var6, var5.is(Blocks.ORES), (Double)ModConfigs.SERVER.xpMine.get(), (Double)ModConfigs.SERVER.xpOreMultiplier.get()
                    );
                    SystemActions.grantXp(var9, var7, SRC_MINE);
                }
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void onPlace(EntityPlaceEvent var0) {
        if (var0.getLevel() instanceof ServerLevel var1 && owner(var0.getEntity()) != null) {
            LongSetData.placed(var1).add(var0.getPos().asLong());
        }
    }

    @SubscribeEvent
    public static void onCraft(ItemCraftedEvent var0) {
        ServerPlayer var1 = owner(var0.getEntity());
        if (var1 != null) {
            ItemStack var2 = var0.getCrafting();
            if (!var2.isEmpty()) {
                double var3 = (Double)ModConfigs.SERVER.xpCraft.get()
                    * (double)Math.max(1, var2.getCount())
                    * repeat(var1, "craft:" + BuiltInRegistries.ITEM.getKey(var2.getItem()));
                SystemActions.grantXp(var1, var3, SRC_CRAFT);
            }
        }
    }

    @SubscribeEvent
    public static void onSmelt(ItemSmeltedEvent var0) {
        ServerPlayer var1 = owner(var0.getEntity());
        if (var1 != null) {
            ItemStack var2 = var0.getSmelting();
            if (!var2.isEmpty()) {
                double var3 = (Double)ModConfigs.SERVER.xpSmelt.get()
                    * (double)Math.max(1, var2.getCount())
                    * repeat(var1, "smelt:" + BuiltInRegistries.ITEM.getKey(var2.getItem()));
                SystemActions.grantXp(var1, var3, SRC_SMELT);
            }
        }
    }

    public static void tickTrades(ServerPlayer var0) {
        int var1 = var0.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
        Integer var2 = TRADE_COUNTS.put(var0.getUUID(), var1);
        String var3 = TRADE_ITEMS.getOrDefault(var0.getUUID(), "trade");
        if (var0.containerMenu instanceof MerchantMenu var4) {
            ItemStack var7 = var4.getSlot(2).getItem();
            if (!var7.isEmpty()) {
                var3 = "trade:" + BuiltInRegistries.ITEM.getKey(var7.getItem());
                TRADE_ITEMS.put(var0.getUUID(), var3);
            }
        }

        if (var2 != null && var1 > var2 && owner(var0) != null) {
            for (int var6 = 0; var6 < Math.min(512, var1 - var2); var6++) {
                SystemActions.grantXp(var0, (Double)ModConfigs.SERVER.xpTrade.get() * repeat(var0, var3), SRC_TRADE);
            }
        }
    }

    @SubscribeEvent
    public static void onBrew(PlayerBrewedPotionEvent var0) {
        ServerPlayer var1 = owner(var0.getEntity());
        if (var1 != null) {
            double var2 = (Double)ModConfigs.SERVER.xpBrew.get() * repeat(var1, "brew:" + Objects.hashCode(ItemNbt.getTag(var0.getStack())));
            SystemActions.grantXp(var1, var2, SRC_BREW);
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent var0) {
        ServerPlayer var1 = owner(var0.getEntity());
        if (var1 != null && var0.getAdvancement().value().display().isPresent()) {
            SystemActions.grantXp(var1, (Double)ModConfigs.SERVER.xpAdvancement.get(), SRC_ADVANCEMENT);
        }
    }

    public static void tickExplore(ServerPlayer var0, PlayerSystemData var1) {
        RuntimeState.State var2 = RuntimeState.of(var0.getUUID());
        long var3 = ChunkPos.asLong(var0.blockPosition().getX() >> 4, var0.blockPosition().getZ() >> 4);
        String var5 = "visited_dim:" + var0.level().dimension().location();
        double var6 = 0.0;
        if (var1.value(var5, 0.0) <= 0.0) {
            var1.setValue(var5, 1.0);
            var6 += ModConfigs.SERVER.xpNewDimension.get();
        }

        if (var3 != var2.lastChunk) {
            var2.lastChunk = var3;
            if (LongSetData.explored(var0.serverLevel()).add(var3)) {
                var6 += ModConfigs.SERVER.xpNewChunk.get();
            }
        }

        if (var6 > 0.0) {
            SystemActions.grantXp(var0, var6, SRC_EXPLORE);
        }
    }
}
