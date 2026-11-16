package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TravelerVaultStorage;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 假面骑士 Cronus 专属装备（故障驱动器Ⅱ / 编年史转玩卡带）面板「物品」界面存取、防复制唯一性管理，
 * 以及变身自动赋予 / 解除自动回收的【Gashacon 缺陷护罩Ⅱ】全生命周期管理器。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class CronusGearManager {
    public static final int STATE_STORED = 0;
    public static final int STATE_HELD = 1;
    public static final int STATE_DROPPED_NEARBY = 2;
    public static final int STATE_LOST = 3;
    public static final int STATE_IN_DRIVER = 4;

    private static final double NEARBY_DROP_RANGE = 64.0;

    private CronusGearManager() {
    }

    // ==================== 面板「物品」界面操作入口 ====================

    public static void handleDriverTake(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        long activeToken = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        if (activeToken > 0L) {
            if (hasOwnedDriver(player, player.m_20148_(), activeToken)) {
                updateDriverState(player, data, STATE_HELD);
                Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_driver_held")
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
            ItemEntity nearby = findNearbyDroppedDriver(player, player.m_20148_(), activeToken);
            if (nearby != null) {
                updateDriverState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.m_237110_("message.rpgtemplate.cronus.gear_dropped_nearby",
                    (int) Math.floor(nearby.m_20185_()),
                    (int) Math.floor(nearby.m_20186_()),
                    (int) Math.floor(nearby.m_20189_()))
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
        }

        long nextToken = Math.max(1L, (long) data.value(CronusKeys.KEY_DRIVER_SEQ, 0.0) + 1L);
        ItemStack stack = new ItemStack(CronusItems.BUGGLE_DRIVER_II.get());
        CronusItems.stampOwned(stack, player.m_20148_(), nextToken);
        if (!player.m_150109_().m_36054_(stack)) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_inv_full")
                .m_130940_(ChatFormatting.RED));
            return;
        }

        data.setValue(CronusKeys.KEY_DRIVER_SEQ, (double) nextToken);
        data.setValue(CronusKeys.KEY_DRIVER_TOKEN, (double) nextToken);
        data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) STATE_HELD);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.m_9236_().m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(),
            SoundEvents.f_11983_, SoundSource.PLAYERS, 0.75f, 1.25f);
        Msg.actionBar(player, Component.m_237115_(activeToken > 0L
            ? "message.rpgtemplate.cronus.gear_driver_reclaimed"
            : "message.rpgtemplate.cronus.gear_driver_taken")
            .m_130940_(ChatFormatting.GREEN));
    }

    public static void handleDriverStore(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        long activeToken = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        boolean[] hadLoadedGashat = {false};
        boolean removed = removePlayerDrivers(player, player.m_20148_(), activeToken, hadLoadedGashat);
        if (!removed) {
            syncGearStates(player);
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_not_found")
                .m_130940_(ChatFormatting.YELLOW));
            return;
        }
        data.setValue(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) STATE_STORED);
        // 若驱动器内插有编年史卡带，一并安全收回面板，绝不吞卡！
        if (hadLoadedGashat[0]) {
            data.setValue(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
            data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) STATE_STORED);
        }
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        TravelerVaultStorage.syncVaultCount(player);
        player.m_9236_().m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(),
            SoundEvents.f_11736_, SoundSource.PLAYERS, 0.7f, 1.15f);
        Msg.actionBar(player, Component.m_237115_(hadLoadedGashat[0]
            ? "message.rpgtemplate.cronus.gear_both_stored"
            : "message.rpgtemplate.cronus.gear_driver_stored")
            .m_130940_(ChatFormatting.AQUA));
    }

    public static void handleGashatTake(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        long activeToken = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        if (activeToken > 0L) {
            if (isGashatInsideAnyDriver(player, player.m_20148_(), activeToken)) {
                updateGashatState(player, data, STATE_IN_DRIVER);
                Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_gashat_in_driver")
                    .m_130940_(ChatFormatting.GREEN));
                return;
            }
            if (hasOwnedGashat(player, player.m_20148_(), activeToken)) {
                updateGashatState(player, data, STATE_HELD);
                Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_gashat_held")
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
            ItemEntity nearby = findNearbyDroppedGashat(player, player.m_20148_(), activeToken);
            if (nearby != null) {
                updateGashatState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.m_237110_("message.rpgtemplate.cronus.gear_dropped_nearby",
                    (int) Math.floor(nearby.m_20185_()),
                    (int) Math.floor(nearby.m_20186_()),
                    (int) Math.floor(nearby.m_20189_()))
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
        }

        long nextToken = Math.max(1L, (long) data.value(CronusKeys.KEY_GASHAT_SEQ, 0.0) + 1L);
        ItemStack stack = new ItemStack(CronusItems.CHRONICLE_GASHAT.get());
        CronusItems.stampOwned(stack, player.m_20148_(), nextToken);
        if (!player.m_150109_().m_36054_(stack)) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_inv_full")
                .m_130940_(ChatFormatting.RED));
            return;
        }

        data.setValue(CronusKeys.KEY_GASHAT_SEQ, (double) nextToken);
        data.setValue(CronusKeys.KEY_GASHAT_TOKEN, (double) nextToken);
        data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) STATE_HELD);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.m_9236_().m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(),
            SoundEvents.f_11983_, SoundSource.PLAYERS, 0.75f, 1.45f);
        Msg.actionBar(player, Component.m_237115_(activeToken > 0L
            ? "message.rpgtemplate.cronus.gear_gashat_reclaimed"
            : "message.rpgtemplate.cronus.gear_gashat_taken")
            .m_130940_(ChatFormatting.GREEN));
    }

    public static void handleGashatStore(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        long activeToken = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        boolean removed = removePlayerGashats(player, player.m_20148_(), activeToken);
        if (!removed) {
            // 若卡带当前插在驱动器内，直接从驱动器中收回至面板
            removed = clearLoadedGashatFromDrivers(player, player.m_20148_(), activeToken);
        }
        if (!removed) {
            syncGearStates(player);
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_not_found")
                .m_130940_(ChatFormatting.YELLOW));
            return;
        }
        data.setValue(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) STATE_STORED);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        TravelerVaultStorage.syncVaultCount(player);
        player.m_9236_().m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(),
            SoundEvents.f_11736_, SoundSource.PLAYERS, 0.7f, 1.25f);
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gear_gashat_stored")
            .m_130940_(ChatFormatting.AQUA));
    }

    /** 一键拿取整套变身器（故障驱动器Ⅱ + 假面骑士编年史转玩卡带）。 */
    public static void handleTakeSet(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        int dState = computeDriverState(player, data);
        int gState = computeGashatState(player, data);
        if (dState == STATE_STORED || dState == STATE_LOST) {
            handleDriverTake(player);
        }
        if (gState == STATE_STORED || gState == STATE_LOST) {
            handleGashatTake(player);
        }
        syncGearStates(player);
    }

    // ==================== Gashacon 缺陷护罩Ⅱ 自动赋予 / 同步 / 回收 ====================

    public static void grantSummonedBugvisor(ServerPlayer player, PlayerSystemData data) {
        if (player == null || data == null) {
            return;
        }
        int mode = CronusKeys.weaponMode(data);
        float bonus = CronusKeys.bugvisorAttackBonus(data);

        // 若背包或副手已有缺陷护罩Ⅱ，直接同步模式与实时 1/3 技能攻击加成
        ItemStack existing = findAnyBugvisor(player);
        if (!existing.m_41619_()) {
            CronusItems.setBugvisorMode(existing, mode);
            CronusItems.updateBugvisorBonusTag(existing, bonus);
            return;
        }

        ItemStack stack = new ItemStack(CronusItems.GASHACON_BUGVISOR_II.get());
        stack.m_41784_().m_128379_(CronusItems.TAG_BUGVISOR_BOUND, true);
        stack.m_41784_().m_128362_(CronusItems.TAG_OWNER, player.m_20148_());
        CronusItems.setBugvisorMode(stack, mode);
        CronusItems.updateBugvisorBonusTag(stack, bonus);

        if (!player.m_150109_().m_36054_(stack)) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.bugvisor_inv_full")
                .m_130940_(ChatFormatting.YELLOW));
        }
    }

    public static void retractSummonedBugvisor(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Inventory inv = player.m_150109_();
        clearBoundBugvisorList(inv.f_35974_);
        clearBoundBugvisorList(inv.f_35976_);
        clearBoundBugvisorList(inv.f_35975_);
        ItemStack carried = player.f_36096_.m_142621_();
        if (CronusItems.isBugvisor(carried) && CronusItems.isBugvisorBound(carried)) {
            player.f_36096_.m_142503_(ItemStack.f_41583_);
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(player);
        if (vault != null) {
            clearBoundBugvisorContainer(vault);
        }
        clearBoundBugvisorContainer(player.m_36327_());
    }

    // ==================== 服务端周期维护（模板获得解锁 + 状态同步 + 武器生命周期） ====================

    public static void tickPlayer(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }

        // 1. 首次检测到拥有假面骑士 Cronus 模板 → 自动解锁并提示驱动器与卡带已储存在系统「物品」界面
        if (CronusKeys.ownsCronusTemplate(data)) {
            if (data.value(CronusKeys.KEY_GEAR_UNLOCKED, 0.0) < 0.5) {
                data.setValue(CronusKeys.KEY_GEAR_UNLOCKED, 1.0);
                data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) STATE_STORED);
                data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) STATE_STORED);
                SystemDataManager.markDirty((Player) player);
                SystemDataManager.sync(player);
                Msg.chat(player, Component.m_237115_("message.rpgtemplate.cronus.gear_unlocked")
                    .m_130940_(ChatFormatting.GREEN));
            }
            if (player.f_19797_ % 20 == 0) {
                purgeStaleGear(player, data);
                syncGearStates(player);
            }
        }

        // 2. 变身武装【Gashacon 缺陷护罩Ⅱ】与变身常驻增益（夜视/水下呼吸）生命周期守护
        boolean transformedCronus = CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId())
            && CronusKeys.isTransformed(data);
        if (transformedCronus) {
            if (player.f_19797_ % 20 == 0) {
                grantSummonedBugvisor(player, data);
            }
        } else {
            // 若已卸下 Cronus 模板或解除变身，回收自动赋予的缺陷护罩Ⅱ与 Cronus 常驻夜视/水下呼吸
            if (data.value(CronusKeys.KEY_BUFFS_APPLIED, 0.0) > 0.5
                || (CronusKeys.isTransformed(data) && !CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId()))) {
                CronusKeys.clearTransformedState(player, data);
                SystemDataManager.markDirty((Player) player);
                SystemDataManager.sync(player);
            } else if (player.f_19797_ % 20 == 0) {
                retractSummonedBugvisor(player);
            }
        }
    }

    public static void syncGearStates(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!CronusKeys.ownsCronusTemplate(data)) {
            return;
        }
        int liveD = computeDriverState(player, data);
        int liveG = computeGashatState(player, data);
        int prevD = (int) Math.round(data.value(CronusKeys.KEY_DRIVER_STATE, 0.0));
        int prevG = (int) Math.round(data.value(CronusKeys.KEY_GASHAT_STATE, 0.0));
        if (liveD != prevD || liveG != prevG) {
            data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) liveD);
            data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) liveG);
            SystemDataManager.markDirty((Player) player);
            SystemDataManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().m_32055_();
        if (!CronusItems.isBugvisor(stack) || !CronusItems.isBugvisorBound(stack)) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer sp) {
            PlayerSystemData active = CronusKeys.activeData(sp);
            if (active != null && CronusKeys.isTransformed(active)) {
                sp.m_150109_().m_36054_(stack);
                Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.bugvisor_no_drop")
                    .m_130940_(ChatFormatting.GRAY));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(ie -> {
            ItemStack s = ie.m_32055_();
            return CronusItems.isBugvisor(s) && CronusItems.isBugvisorBound(s);
        });
    }

    // ==================== 内部检索、状态判定与防复制清理 ====================

    private static boolean canAccessCronusGear(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return false;
        }
        return CronusKeys.ownsCronusTemplate(SystemDataManager.get(player));
    }

    private static void updateDriverState(ServerPlayer player, PlayerSystemData data, int state) {
        data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) state);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
    }

    private static void updateGashatState(ServerPlayer player, PlayerSystemData data, int state) {
        data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) state);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
    }

    private static int computeDriverState(ServerPlayer player, PlayerSystemData data) {
        long token = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        if (token <= 0L) {
            return STATE_STORED;
        }
        if (hasOwnedDriver(player, player.m_20148_(), token)) {
            return STATE_HELD;
        }
        if (findNearbyDroppedDriver(player, player.m_20148_(), token) != null) {
            return STATE_DROPPED_NEARBY;
        }
        return STATE_LOST;
    }

    private static int computeGashatState(ServerPlayer player, PlayerSystemData data) {
        long token = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        if (token <= 0L) {
            return STATE_STORED;
        }
        if (isGashatInsideAnyDriver(player, player.m_20148_(), token)) {
            return STATE_IN_DRIVER;
        }
        if (hasOwnedGashat(player, player.m_20148_(), token)) {
            return STATE_HELD;
        }
        if (findNearbyDroppedGashat(player, player.m_20148_(), token) != null) {
            return STATE_DROPPED_NEARBY;
        }
        return STATE_LOST;
    }

    private static boolean matchesOwnedDriver(ItemStack s, UUID ownerId, long token) {
        if (!CronusItems.isDriver(s)) {
            return false;
        }
        UUID o = CronusItems.ownerOf(s);
        long t = CronusItems.tokenOf(s);
        if (o == null || t <= 0L) {
            return token <= 0L;
        }
        return ownerId.equals(o) && (token <= 0L || t == token);
    }

    private static boolean matchesOwnedGashat(ItemStack s, UUID ownerId, long token) {
        if (!CronusItems.isGashat(s)) {
            return false;
        }
        UUID o = CronusItems.ownerOf(s);
        long t = CronusItems.tokenOf(s);
        if (o == null || t <= 0L) {
            return token <= 0L;
        }
        return ownerId.equals(o) && (token <= 0L || t == token);
    }

    private static boolean driverHoldsGashat(ItemStack s, UUID ownerId, long gToken) {
        if (!CronusItems.isDriver(s) || !CronusItems.isDriverLoaded(s)) {
            return false;
        }
        UUID go = CronusItems.loadedGashatOwner(s);
        long gt = CronusItems.loadedGashatToken(s);
        if (go == null || gt <= 0L) {
            return gToken <= 0L;
        }
        return ownerId.equals(go) && (gToken <= 0L || gt == gToken);
    }

    private static boolean hasOwnedDriver(ServerPlayer p, UUID ownerId, long token) {
        Inventory inv = p.m_150109_();
        for (ItemStack s : inv.f_35974_) if (matchesOwnedDriver(s, ownerId, token)) return true;
        for (ItemStack s : inv.f_35976_) if (matchesOwnedDriver(s, ownerId, token)) return true;
        if (matchesOwnedDriver(p.f_36096_.m_142621_(), ownerId, token)) return true;
        if (containerHasDriver(p.m_36327_(), ownerId, token)) return true;
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        return vault != null && containerHasDriver(vault, ownerId, token);
    }

    private static boolean hasOwnedGashat(ServerPlayer p, UUID ownerId, long token) {
        Inventory inv = p.m_150109_();
        for (ItemStack s : inv.f_35974_) if (matchesOwnedGashat(s, ownerId, token)) return true;
        for (ItemStack s : inv.f_35976_) if (matchesOwnedGashat(s, ownerId, token)) return true;
        if (matchesOwnedGashat(p.f_36096_.m_142621_(), ownerId, token)) return true;
        if (containerHasGashat(p.m_36327_(), ownerId, token)) return true;
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        return vault != null && containerHasGashat(vault, ownerId, token);
    }

    private static boolean isGashatInsideAnyDriver(ServerPlayer p, UUID ownerId, long gToken) {
        Inventory inv = p.m_150109_();
        for (ItemStack s : inv.f_35974_) if (driverHoldsGashat(s, ownerId, gToken)) return true;
        for (ItemStack s : inv.f_35976_) if (driverHoldsGashat(s, ownerId, gToken)) return true;
        if (driverHoldsGashat(p.f_36096_.m_142621_(), ownerId, gToken)) return true;
        Container ender = p.m_36327_();
        for (int i = 0; i < ender.m_6643_(); i++) if (driverHoldsGashat(ender.m_8020_(i), ownerId, gToken)) return true;
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            for (int i = 0; i < vault.m_6643_(); i++) if (driverHoldsGashat(vault.m_8020_(i), ownerId, gToken)) return true;
        }
        return false;
    }

    private static boolean containerHasDriver(Container c, UUID ownerId, long token) {
        for (int i = 0; i < c.m_6643_(); i++) {
            if (matchesOwnedDriver(c.m_8020_(i), ownerId, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containerHasGashat(Container c, UUID ownerId, long token) {
        for (int i = 0; i < c.m_6643_(); i++) {
            if (matchesOwnedGashat(c.m_8020_(i), ownerId, token)) {
                return true;
            }
        }
        return false;
    }

    private static ItemEntity findNearbyDroppedDriver(ServerPlayer p, UUID ownerId, long token) {
        if (!(p.m_9236_() instanceof ServerLevel sl)) {
            return null;
        }
        for (ItemEntity ie : sl.m_6443_(ItemEntity.class,
            p.m_20191_().m_82377_(NEARBY_DROP_RANGE, NEARBY_DROP_RANGE, NEARBY_DROP_RANGE),
            e -> e.m_6084_() && matchesOwnedDriver(e.m_32055_(), ownerId, token))) {
            return ie;
        }
        return null;
    }

    private static ItemEntity findNearbyDroppedGashat(ServerPlayer p, UUID ownerId, long token) {
        if (!(p.m_9236_() instanceof ServerLevel sl)) {
            return null;
        }
        for (ItemEntity ie : sl.m_6443_(ItemEntity.class,
            p.m_20191_().m_82377_(NEARBY_DROP_RANGE, NEARBY_DROP_RANGE, NEARBY_DROP_RANGE),
            e -> e.m_6084_() && (matchesOwnedGashat(e.m_32055_(), ownerId, token)
                || driverHoldsGashat(e.m_32055_(), ownerId, token)))) {
            return ie;
        }
        return null;
    }

    private static boolean removePlayerDrivers(ServerPlayer p, UUID ownerId, long token, boolean[] hadGashat) {
        boolean removed = false;
        Inventory inv = p.m_150109_();
        removed |= removeDriversInList(inv.f_35974_, ownerId, token, hadGashat);
        removed |= removeDriversInList(inv.f_35976_, ownerId, token, hadGashat);
        ItemStack carried = p.f_36096_.m_142621_();
        if (matchesOwnedDriver(carried, ownerId, token)) {
            if (CronusItems.isDriverLoaded(carried)) hadGashat[0] = true;
            p.f_36096_.m_142503_(ItemStack.f_41583_);
            removed = true;
        }
        removed |= removeDriversInContainer(p.m_36327_(), ownerId, token, hadGashat);
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            removed |= removeDriversInContainer(vault, ownerId, token, hadGashat);
        }
        return removed;
    }

    private static boolean removeDriversInList(NonNullList<ItemStack> list, UUID ownerId, long token, boolean[] hadGashat) {
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (matchesOwnedDriver(s, ownerId, token)) {
                if (CronusItems.isDriverLoaded(s)) hadGashat[0] = true;
                list.set(i, ItemStack.f_41583_);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean removeDriversInContainer(Container c, UUID ownerId, long token, boolean[] hadGashat) {
        boolean removed = false;
        for (int i = 0; i < c.m_6643_(); i++) {
            ItemStack s = c.m_8020_(i);
            if (matchesOwnedDriver(s, ownerId, token)) {
                if (CronusItems.isDriverLoaded(s)) hadGashat[0] = true;
                c.m_6836_(i, ItemStack.f_41583_);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean removePlayerGashats(ServerPlayer p, UUID ownerId, long token) {
        boolean removed = false;
        Inventory inv = p.m_150109_();
        removed |= removeGashatsInList(inv.f_35974_, ownerId, token);
        removed |= removeGashatsInList(inv.f_35976_, ownerId, token);
        ItemStack carried = p.f_36096_.m_142621_();
        if (matchesOwnedGashat(carried, ownerId, token)) {
            p.f_36096_.m_142503_(ItemStack.f_41583_);
            removed = true;
        }
        removed |= removeGashatsInContainer(p.m_36327_(), ownerId, token);
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            removed |= removeGashatsInContainer(vault, ownerId, token);
        }
        return removed;
    }

    private static boolean removeGashatsInList(NonNullList<ItemStack> list, UUID ownerId, long token) {
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (matchesOwnedGashat(list.get(i), ownerId, token)) {
                list.set(i, ItemStack.f_41583_);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean removeGashatsInContainer(Container c, UUID ownerId, long token) {
        boolean removed = false;
        for (int i = 0; i < c.m_6643_(); i++) {
            if (matchesOwnedGashat(c.m_8020_(i), ownerId, token)) {
                c.m_6836_(i, ItemStack.f_41583_);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean clearLoadedGashatFromDrivers(ServerPlayer p, UUID ownerId, long gToken) {
        boolean cleared = false;
        Inventory inv = p.m_150109_();
        for (ItemStack s : inv.f_35974_) {
            if (driverHoldsGashat(s, ownerId, gToken)) {
                CronusItems.clearDriverLoaded(s);
                cleared = true;
            }
        }
        for (ItemStack s : inv.f_35976_) {
            if (driverHoldsGashat(s, ownerId, gToken)) {
                CronusItems.clearDriverLoaded(s);
                cleared = true;
            }
        }
        Container ender = p.m_36327_();
        for (int i = 0; i < ender.m_6643_(); i++) {
            ItemStack s = ender.m_8020_(i);
            if (driverHoldsGashat(s, ownerId, gToken)) {
                CronusItems.clearDriverLoaded(s);
                cleared = true;
            }
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            for (int i = 0; i < vault.m_6643_(); i++) {
                ItemStack s = vault.m_8020_(i);
                if (driverHoldsGashat(s, ownerId, gToken)) {
                    CronusItems.clearDriverLoaded(s);
                    cleared = true;
                }
            }
        }
        return cleared;
    }

    /** 清理已作废的旧代驱动器或卡带，防止遗失补领后复制。 */
    private static void purgeStaleGear(ServerPlayer p, PlayerSystemData data) {
        UUID selfId = p.m_20148_();
        long validDriver = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        long validGashat = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        Inventory inv = p.m_150109_();
        purgeStaleList(inv.f_35974_, selfId, validDriver, validGashat);
        purgeStaleList(inv.f_35976_, selfId, validDriver, validGashat);
    }

    private static void purgeStaleList(NonNullList<ItemStack> list, UUID selfId, long validDriver, long validGashat) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (CronusItems.isDriver(s)) {
                UUID o = CronusItems.ownerOf(s);
                long t = CronusItems.tokenOf(s);
                if (selfId.equals(o) && t > 0L && t != validDriver) {
                    list.set(i, ItemStack.f_41583_);
                }
            } else if (CronusItems.isGashat(s)) {
                UUID o = CronusItems.ownerOf(s);
                long t = CronusItems.tokenOf(s);
                if (selfId.equals(o) && t > 0L && t != validGashat) {
                    list.set(i, ItemStack.f_41583_);
                }
            }
        }
    }

    private static ItemStack findAnyBugvisor(ServerPlayer p) {
        Inventory inv = p.m_150109_();
        for (ItemStack s : inv.f_35974_) {
            if (CronusItems.isBugvisor(s)) return s;
        }
        for (ItemStack s : inv.f_35976_) {
            if (CronusItems.isBugvisor(s)) return s;
        }
        ItemStack carried = p.f_36096_.m_142621_();
        if (CronusItems.isBugvisor(carried)) {
            return carried;
        }
        Container ender = p.m_36327_();
        for (int i = 0; i < ender.m_6643_(); i++) {
            ItemStack s = ender.m_8020_(i);
            if (CronusItems.isBugvisor(s)) return s;
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            for (int i = 0; i < vault.m_6643_(); i++) {
                ItemStack s = vault.m_8020_(i);
                if (CronusItems.isBugvisor(s)) return s;
            }
        }
        return ItemStack.f_41583_;
    }

    private static void clearBoundBugvisorList(NonNullList<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (CronusItems.isBugvisor(s) && CronusItems.isBugvisorBound(s)) {
                list.set(i, ItemStack.f_41583_);
            }
        }
    }

    private static void clearBoundBugvisorContainer(Container c) {
        for (int i = 0; i < c.m_6643_(); i++) {
            ItemStack s = c.m_8020_(i);
            if (CronusItems.isBugvisor(s) && CronusItems.isBugvisorBound(s)) {
                c.m_6836_(i, ItemStack.f_41583_);
            }
        }
    }
}
