package com.rpgtemplate.cronus;
import com.rpgtemplate.forge.util.ItemNbt;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 假面骑士 Cronus 专属装备（故障驱动器Ⅱ / 编年史转玩卡带）面板「物品」界面存取、腰间佩戴、防复制唯一性管理，
 * 以及变身自动赋予 / 解除自动回收的【Gashacon 缺陷护罩Ⅱ】与 3D 变身外观同步管理器。
 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class CronusGearManager {
    public static final int STATE_STORED = 0;
    public static final int STATE_HELD = 1;
    public static final int STATE_DROPPED_NEARBY = 2;
    public static final int STATE_LOST = 3;
    public static final int STATE_IN_DRIVER = 4;
    public static final int STATE_WORN = 5;

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
            if (isWearingOwnedDriver(player, player.getUUID(), activeToken)) {
                updateDriverState(player, data, STATE_WORN);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_driver_worn")
                    .withStyle(ChatFormatting.GREEN));
                return;
            }
            if (hasOwnedDriver(player, player.getUUID(), activeToken)) {
                updateDriverState(player, data, STATE_HELD);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_driver_held")
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
            ItemEntity nearby = findNearbyDroppedDriver(player, player.getUUID(), activeToken);
            if (nearby != null) {
                updateDriverState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_dropped_nearby",
                    (int) Math.floor(nearby.getX()),
                    (int) Math.floor(nearby.getY()),
                    (int) Math.floor(nearby.getZ()))
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        long nextToken = Math.max(1L, (long) data.value(CronusKeys.KEY_DRIVER_SEQ, 0.0) + 1L);
        ItemStack stack = new ItemStack(CronusItems.BUGGLE_DRIVER_II.get());
        CronusItems.stampOwned(stack, player.getUUID(), nextToken);
        if (!player.getInventory().add(stack)) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_inv_full")
                .withStyle(ChatFormatting.RED));
            return;
        }

        data.setValue(CronusKeys.KEY_DRIVER_SEQ, (double) nextToken);
        data.setValue(CronusKeys.KEY_DRIVER_TOKEN, (double) nextToken);
        data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) STATE_HELD);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.75f, 1.25f);
        Msg.actionBar(player, Component.translatable(activeToken > 0L
            ? "message.rpgtemplate.cronus.gear_driver_reclaimed"
            : "message.rpgtemplate.cronus.gear_driver_taken")
            .withStyle(ChatFormatting.GREEN));
    }

    /** 一键将【故障驱动器Ⅱ】装备至腰间（护腿槽位），若当前穿着其他护腿则安全收入背包。 */
    public static void handleDriverEquip(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        long activeToken = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        if (CronusItems.isWearingDriver(player)) {
            updateDriverState(player, data, STATE_WORN);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_driver_worn")
                .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (activeToken > 0L) {
            ItemEntity nearby = findNearbyDroppedDriver(player, player.getUUID(), activeToken);
            if (nearby != null && !hasOwnedDriver(player, player.getUUID(), activeToken)) {
                updateDriverState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_dropped_nearby",
                    (int) Math.floor(nearby.getX()),
                    (int) Math.floor(nearby.getY()),
                    (int) Math.floor(nearby.getZ()))
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        // 若护腿槽已有其他护腿，先确认背包能安全收纳，绝不覆写玩家护腿
        ItemStack oldLegs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!oldLegs.isEmpty()) {
            ItemStack moved = oldLegs.copy();
            if (!player.getInventory().add(moved)) {
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_legs_full")
                    .withStyle(ChatFormatting.RED));
                return;
            }
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }

        // 若玩家背包/副手/仓库中已持有自己的驱动器，直接取出来佩戴；否则签发新驱动器并佩戴
        boolean[] hadGashat = {false};
        ItemStack toEquip;
        long tokenToUse;
        if (activeToken > 0L && hasOwnedDriver(player, player.getUUID(), activeToken)) {
            removePlayerDrivers(player, player.getUUID(), activeToken, hadGashat);
            tokenToUse = activeToken;
            toEquip = new ItemStack(CronusItems.BUGGLE_DRIVER_II.get());
            CronusItems.stampOwned(toEquip, player.getUUID(), tokenToUse);
        } else {
            tokenToUse = Math.max(1L, (long) data.value(CronusKeys.KEY_DRIVER_SEQ, 0.0) + 1L);
            data.setValue(CronusKeys.KEY_DRIVER_SEQ, (double) tokenToUse);
            toEquip = new ItemStack(CronusItems.BUGGLE_DRIVER_II.get());
            CronusItems.stampOwned(toEquip, player.getUUID(), tokenToUse);
        }
        if (CronusKeys.isTransformed(data)) {
            CronusItems.setDriverLoaded(toEquip, player.getUUID(), 0L);
        }
        player.setItemSlot(EquipmentSlot.LEGS, toEquip);
        data.setValue(CronusKeys.KEY_DRIVER_TOKEN, (double) tokenToUse);
        data.setValue(CronusKeys.KEY_DRIVER_STATE, (double) STATE_WORN);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        TravelerVaultStorage.syncVaultCount(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_DIAMOND, SoundSource.PLAYERS, 0.85f, 1.15f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.75f, 1.30f);
        Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.driver_equipped")
            .withStyle(ChatFormatting.GREEN));
    }

    public static void handleDriverStore(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (CronusKeys.isTransformed(data)) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_locked_in_henshin")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }
        long activeToken = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        boolean[] hadLoadedGashat = {false};
        boolean removed = removePlayerDrivers(player, player.getUUID(), activeToken, hadLoadedGashat);
        if (!removed) {
            syncGearStates(player);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_not_found")
                .withStyle(ChatFormatting.YELLOW));
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
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.15f);
        Msg.actionBar(player, Component.translatable(hadLoadedGashat[0]
            ? "message.rpgtemplate.cronus.gear_both_stored"
            : "message.rpgtemplate.cronus.gear_driver_stored")
            .withStyle(ChatFormatting.AQUA));
    }

    public static void handleGashatTake(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (data.value(CronusKeys.KEY_GASHAT_IN_HENSHIN, 0.0) > 0.5) {
            updateGashatState(player, data, STATE_IN_DRIVER);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_gashat_in_driver")
                .withStyle(ChatFormatting.GREEN));
            return;
        }
        long activeToken = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        if (activeToken > 0L) {
            if (isGashatInsideAnyDriver(player, player.getUUID(), activeToken)) {
                updateGashatState(player, data, STATE_IN_DRIVER);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_gashat_in_driver")
                    .withStyle(ChatFormatting.GREEN));
                return;
            }
            if (hasOwnedGashat(player, player.getUUID(), activeToken)) {
                updateGashatState(player, data, STATE_HELD);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_gashat_held")
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
            ItemEntity nearby = findNearbyDroppedGashat(player, player.getUUID(), activeToken);
            if (nearby != null) {
                updateGashatState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_dropped_nearby",
                    (int) Math.floor(nearby.getX()),
                    (int) Math.floor(nearby.getY()),
                    (int) Math.floor(nearby.getZ()))
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        long nextToken = Math.max(1L, (long) data.value(CronusKeys.KEY_GASHAT_SEQ, 0.0) + 1L);
        ItemStack stack = new ItemStack(CronusItems.CHRONICLE_GASHAT.get());
        CronusItems.stampOwned(stack, player.getUUID(), nextToken);
        if (!player.getInventory().add(stack)) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_inv_full")
                .withStyle(ChatFormatting.RED));
            return;
        }

        data.setValue(CronusKeys.KEY_GASHAT_SEQ, (double) nextToken);
        data.setValue(CronusKeys.KEY_GASHAT_TOKEN, (double) nextToken);
        data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) STATE_HELD);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.75f, 1.45f);
        Msg.actionBar(player, Component.translatable(activeToken > 0L
            ? "message.rpgtemplate.cronus.gear_gashat_reclaimed"
            : "message.rpgtemplate.cronus.gear_gashat_taken")
            .withStyle(ChatFormatting.GREEN));
    }

    public static void handleGashatStore(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (data.value(CronusKeys.KEY_GASHAT_IN_HENSHIN, 0.0) > 0.5) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_locked_in_henshin")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }
        long activeToken = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        boolean removed = removePlayerGashats(player, player.getUUID(), activeToken);
        if (!removed) {
            // 若卡带当前插在驱动器内，直接从驱动器中收回至面板
            removed = clearLoadedGashatFromDrivers(player, player.getUUID(), activeToken);
        }
        if (!removed) {
            syncGearStates(player);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_not_found")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }
        data.setValue(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) STATE_STORED);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        TravelerVaultStorage.syncVaultCount(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.25f);
        Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.gear_gashat_stored")
            .withStyle(ChatFormatting.AQUA));
    }

    /** 一键整备变身器：自动将【故障驱动器Ⅱ】佩戴至腰间，并取出【假面骑士编年史转玩卡带】至背包！ */
    public static void handleTakeSet(ServerPlayer player) {
        if (!canAccessCronusGear(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        int dState = computeDriverState(player, data);
        int gState = computeGashatState(player, data);
        if (dState == STATE_STORED || dState == STATE_LOST || dState == STATE_HELD) {
            handleDriverEquip(player);
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
        if (!existing.isEmpty()) {
            CronusItems.setBugvisorMode(existing, mode);
            CronusItems.updateBugvisorBonusTag(existing, bonus);
            return;
        }

        ItemStack stack = new ItemStack(CronusItems.GASHACON_BUGVISOR_II.get());
        ItemNbt.putBoolean(stack, CronusItems.TAG_BUGVISOR_BOUND, true);
        ItemNbt.putUUID(stack, CronusItems.TAG_OWNER, player.getUUID());
        CronusItems.setBugvisorMode(stack, mode);
        CronusItems.updateBugvisorBonusTag(stack, bonus);

        if (!player.getInventory().add(stack)) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.bugvisor_inv_full")
                .withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void retractSummonedBugvisor(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Inventory inv = player.getInventory();
        clearBoundBugvisorList(inv.items);
        clearBoundBugvisorList(inv.offhand);
        clearBoundBugvisorList(inv.armor);
        ItemStack carried = player.containerMenu.getCarried();
        if (CronusItems.isBugvisor(carried) && CronusItems.isBugvisorBound(carried)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(player);
        if (vault != null) {
            clearBoundBugvisorContainer(vault);
        }
        clearBoundBugvisorContainer(player.getEnderChestInventory());
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
                Msg.chat(player, Component.translatable("message.rpgtemplate.cronus.gear_unlocked")
                    .withStyle(ChatFormatting.GREEN));
            }
            if (player.tickCount % 20 == 0) {
                purgeStaleGear(player, data);
                syncGearStates(player);
            }
        }

        // 2. 变身武装【Gashacon 缺陷护罩Ⅱ】、3D 装甲同步与变身常驻增益（夜视/水下呼吸）生命周期守护
        //    （解除变身仅允许通过【编年史变身】技能触发，摘下驱动器不会误解除变身）
        boolean transformedCronus = CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId())
            && CronusKeys.isTransformed(data);
        if (transformedCronus) {
            long now = player.level().getGameTime();
            long revealTick = Math.round(data.value(CronusKeys.KEY_SUIT_REVEAL_TICK, 0.0));
            long beltGashatTick = Math.round(data.value(CronusKeys.KEY_BELT_GASHAT_TICK, 0.0));

            // 技能变身中当飞入卡带精准锁定腰带（tFrame=126）后，点亮佩戴中故障驱动器Ⅱ的插卡指示状态
            if (now >= beltGashatTick) {
                ItemStack wornDriver = CronusItems.getWornDriver(player);
                if (!wornDriver.isEmpty() && !CronusItems.isDriverLoaded(wornDriver)) {
                    CronusItems.setDriverLoaded(wornDriver, player.getUUID(), 0L);
                }
            }
            // 在变身动画结束、3D Cronus 装甲正式显现的瞬间同步赋予【Gashacon 缺陷护罩Ⅱ】
            if (now >= revealTick && (now == revealTick || player.tickCount % 20 == 0)) {
                grantSummonedBugvisor(player, data);
            }
            if (player.tickCount % 40 == 0) {
                CronusNet.syncSuitState(player, data);
            }
        } else {
            // 若已卸下 Cronus 模板或解除变身，回收自动赋予的缺陷护罩Ⅱ与 Cronus 常驻夜视/水下呼吸（并归还收纳的卡带）
            if (data.value(CronusKeys.KEY_BUFFS_APPLIED, 0.0) > 0.5
                || (CronusKeys.isTransformed(data) && !CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId()))) {
                CronusKeys.clearTransformedState(player, data);
                SystemDataManager.markDirty((Player) player);
                SystemDataManager.sync(player);
            } else if (player.tickCount % 20 == 0) {
                retractSummonedBugvisor(player);
            }
        }
    }

    /** 清理玩家身上/背包/仓库所有故障驱动器Ⅱ上的插卡亮灯标记（解除变身时调用）。 */
    public static void clearAllDriverLoadedFlags(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Inventory inv = player.getInventory();
        clearDriverLoadedInList(inv.armor);
        clearDriverLoadedInList(inv.items);
        clearDriverLoadedInList(inv.offhand);
        ItemStack carried = player.containerMenu.getCarried();
        if (CronusItems.isDriver(carried) && CronusItems.isDriverLoaded(carried)) {
            CronusItems.clearDriverLoaded(carried);
        }
        clearDriverLoadedInContainer(player.getEnderChestInventory());
        SimpleContainer vault = TravelerVaultStorage.containerOf(player);
        if (vault != null) {
            clearDriverLoadedInContainer(vault);
        }
    }

    private static void clearDriverLoadedInList(NonNullList<ItemStack> list) {
        for (ItemStack s : list) {
            if (CronusItems.isDriver(s) && CronusItems.isDriverLoaded(s)) {
                CronusItems.clearDriverLoaded(s);
            }
        }
    }

    private static void clearDriverLoadedInContainer(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (CronusItems.isDriver(s) && CronusItems.isDriverLoaded(s)) {
                CronusItems.clearDriverLoaded(s);
            }
        }
    }

    /** 解除变身时，将右键变身期间收纳进腰带的【假面骑士编年史转玩卡带】原样归还至玩家背包。 */
    public static void returnConsumedGashat(ServerPlayer player, PlayerSystemData data, long heldToken) {
        if (player == null) {
            return;
        }
        ItemStack returned = new ItemStack(CronusItems.CHRONICLE_GASHAT.get());
        if (heldToken > 0L) {
            CronusItems.stampOwned(returned, player.getUUID(), heldToken);
        }
        if (!player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.85f, 1.45f);
        syncGearStates(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject) {
            PlayerSystemData active = CronusKeys.activeData(subject);
            CronusNet.syncSuitStateTo(subject, viewer, active);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PlayerSystemData active = CronusKeys.activeData(sp);
            CronusNet.syncSuitState(sp, active);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PlayerSystemData active = CronusKeys.activeData(sp);
            CronusNet.syncSuitState(sp, active);
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
        ItemStack stack = event.getEntity().getItem();
        if (!CronusItems.isBugvisor(stack) || !CronusItems.isBugvisorBound(stack)) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer sp) {
            PlayerSystemData active = CronusKeys.activeData(sp);
            if (active != null && CronusKeys.isTransformed(active)) {
                sp.getInventory().add(stack);
                Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.bugvisor_no_drop")
                    .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(ie -> {
            ItemStack s = ie.getItem();
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
            return CronusItems.isWearingDriver(player) ? STATE_WORN : STATE_STORED;
        }
        if (isWearingOwnedDriver(player, player.getUUID(), token)) {
            return STATE_WORN;
        }
        if (hasOwnedDriver(player, player.getUUID(), token)) {
            return STATE_HELD;
        }
        if (findNearbyDroppedDriver(player, player.getUUID(), token) != null) {
            return STATE_DROPPED_NEARBY;
        }
        return STATE_LOST;
    }

    private static boolean isWearingOwnedDriver(ServerPlayer p, UUID ownerId, long token) {
        return matchesOwnedDriver(p.getItemBySlot(EquipmentSlot.LEGS), ownerId, token);
    }

    private static int computeGashatState(ServerPlayer player, PlayerSystemData data) {
        if (data.value(CronusKeys.KEY_GASHAT_IN_HENSHIN, 0.0) > 0.5) {
            return STATE_IN_DRIVER;
        }
        long token = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        if (token <= 0L) {
            return STATE_STORED;
        }
        if (isGashatInsideAnyDriver(player, player.getUUID(), token)) {
            return STATE_IN_DRIVER;
        }
        if (hasOwnedGashat(player, player.getUUID(), token)) {
            return STATE_HELD;
        }
        if (findNearbyDroppedGashat(player, player.getUUID(), token) != null) {
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
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.armor) if (matchesOwnedDriver(s, ownerId, token)) return true;
        for (ItemStack s : inv.items) if (matchesOwnedDriver(s, ownerId, token)) return true;
        for (ItemStack s : inv.offhand) if (matchesOwnedDriver(s, ownerId, token)) return true;
        if (matchesOwnedDriver(p.containerMenu.getCarried(), ownerId, token)) return true;
        if (containerHasDriver(p.getEnderChestInventory(), ownerId, token)) return true;
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        return vault != null && containerHasDriver(vault, ownerId, token);
    }

    private static boolean hasOwnedGashat(ServerPlayer p, UUID ownerId, long token) {
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) if (matchesOwnedGashat(s, ownerId, token)) return true;
        for (ItemStack s : inv.offhand) if (matchesOwnedGashat(s, ownerId, token)) return true;
        if (matchesOwnedGashat(p.containerMenu.getCarried(), ownerId, token)) return true;
        if (containerHasGashat(p.getEnderChestInventory(), ownerId, token)) return true;
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        return vault != null && containerHasGashat(vault, ownerId, token);
    }

    private static boolean isGashatInsideAnyDriver(ServerPlayer p, UUID ownerId, long gToken) {
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.armor) if (driverHoldsGashat(s, ownerId, gToken)) return true;
        for (ItemStack s : inv.items) if (driverHoldsGashat(s, ownerId, gToken)) return true;
        for (ItemStack s : inv.offhand) if (driverHoldsGashat(s, ownerId, gToken)) return true;
        if (driverHoldsGashat(p.containerMenu.getCarried(), ownerId, gToken)) return true;
        Container ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) if (driverHoldsGashat(ender.getItem(i), ownerId, gToken)) return true;
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            for (int i = 0; i < vault.getContainerSize(); i++) if (driverHoldsGashat(vault.getItem(i), ownerId, gToken)) return true;
        }
        return false;
    }

    private static boolean containerHasDriver(Container c, UUID ownerId, long token) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (matchesOwnedDriver(c.getItem(i), ownerId, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containerHasGashat(Container c, UUID ownerId, long token) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (matchesOwnedGashat(c.getItem(i), ownerId, token)) {
                return true;
            }
        }
        return false;
    }

    private static ItemEntity findNearbyDroppedDriver(ServerPlayer p, UUID ownerId, long token) {
        if (!(p.level() instanceof ServerLevel sl)) {
            return null;
        }
        for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class,
            p.getBoundingBox().inflate(NEARBY_DROP_RANGE, NEARBY_DROP_RANGE, NEARBY_DROP_RANGE),
            e -> e.isAlive() && matchesOwnedDriver(e.getItem(), ownerId, token))) {
            return ie;
        }
        return null;
    }

    private static ItemEntity findNearbyDroppedGashat(ServerPlayer p, UUID ownerId, long token) {
        if (!(p.level() instanceof ServerLevel sl)) {
            return null;
        }
        for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class,
            p.getBoundingBox().inflate(NEARBY_DROP_RANGE, NEARBY_DROP_RANGE, NEARBY_DROP_RANGE),
            e -> e.isAlive() && (matchesOwnedGashat(e.getItem(), ownerId, token)
                || driverHoldsGashat(e.getItem(), ownerId, token)))) {
            return ie;
        }
        return null;
    }

    private static boolean removePlayerDrivers(ServerPlayer p, UUID ownerId, long token, boolean[] hadGashat) {
        boolean removed = false;
        Inventory inv = p.getInventory();
        removed |= removeDriversInList(inv.armor, ownerId, token, hadGashat);
        removed |= removeDriversInList(inv.items, ownerId, token, hadGashat);
        removed |= removeDriversInList(inv.offhand, ownerId, token, hadGashat);
        ItemStack carried = p.containerMenu.getCarried();
        if (matchesOwnedDriver(carried, ownerId, token)) {
            if (CronusItems.isDriverLoaded(carried) && CronusItems.loadedGashatToken(carried) > 0L) hadGashat[0] = true;
            p.containerMenu.setCarried(ItemStack.EMPTY);
            removed = true;
        }
        removed |= removeDriversInContainer(p.getEnderChestInventory(), ownerId, token, hadGashat);
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
                if (CronusItems.isDriverLoaded(s) && CronusItems.loadedGashatToken(s) > 0L) hadGashat[0] = true;
                list.set(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean removeDriversInContainer(Container c, UUID ownerId, long token, boolean[] hadGashat) {
        boolean removed = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (matchesOwnedDriver(s, ownerId, token)) {
                if (CronusItems.isDriverLoaded(s) && CronusItems.loadedGashatToken(s) > 0L) hadGashat[0] = true;
                c.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean removePlayerGashats(ServerPlayer p, UUID ownerId, long token) {
        boolean removed = false;
        Inventory inv = p.getInventory();
        removed |= removeGashatsInList(inv.items, ownerId, token);
        removed |= removeGashatsInList(inv.offhand, ownerId, token);
        ItemStack carried = p.containerMenu.getCarried();
        if (matchesOwnedGashat(carried, ownerId, token)) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
            removed = true;
        }
        removed |= removeGashatsInContainer(p.getEnderChestInventory(), ownerId, token);
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
                list.set(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean removeGashatsInContainer(Container c, UUID ownerId, long token) {
        boolean removed = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (matchesOwnedGashat(c.getItem(i), ownerId, token)) {
                c.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean clearLoadedGashatFromDrivers(ServerPlayer p, UUID ownerId, long gToken) {
        boolean cleared = false;
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) {
            if (driverHoldsGashat(s, ownerId, gToken)) {
                CronusItems.clearDriverLoaded(s);
                cleared = true;
            }
        }
        for (ItemStack s : inv.offhand) {
            if (driverHoldsGashat(s, ownerId, gToken)) {
                CronusItems.clearDriverLoaded(s);
                cleared = true;
            }
        }
        Container ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            ItemStack s = ender.getItem(i);
            if (driverHoldsGashat(s, ownerId, gToken)) {
                CronusItems.clearDriverLoaded(s);
                cleared = true;
            }
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            for (int i = 0; i < vault.getContainerSize(); i++) {
                ItemStack s = vault.getItem(i);
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
        UUID selfId = p.getUUID();
        long validDriver = (long) data.value(CronusKeys.KEY_DRIVER_TOKEN, 0.0);
        long validGashat = (long) data.value(CronusKeys.KEY_GASHAT_TOKEN, 0.0);
        Inventory inv = p.getInventory();
        purgeStaleList(inv.armor, selfId, validDriver, validGashat);
        purgeStaleList(inv.items, selfId, validDriver, validGashat);
        purgeStaleList(inv.offhand, selfId, validDriver, validGashat);
    }

    private static void purgeStaleList(NonNullList<ItemStack> list, UUID selfId, long validDriver, long validGashat) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (CronusItems.isDriver(s)) {
                UUID o = CronusItems.ownerOf(s);
                long t = CronusItems.tokenOf(s);
                if (selfId.equals(o) && t > 0L && t != validDriver) {
                    list.set(i, ItemStack.EMPTY);
                }
            } else if (CronusItems.isGashat(s)) {
                UUID o = CronusItems.ownerOf(s);
                long t = CronusItems.tokenOf(s);
                if (selfId.equals(o) && t > 0L && t != validGashat) {
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private static ItemStack findAnyBugvisor(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) {
            if (CronusItems.isBugvisor(s)) return s;
        }
        for (ItemStack s : inv.offhand) {
            if (CronusItems.isBugvisor(s)) return s;
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (CronusItems.isBugvisor(carried)) {
            return carried;
        }
        Container ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            ItemStack s = ender.getItem(i);
            if (CronusItems.isBugvisor(s)) return s;
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(p);
        if (vault != null) {
            for (int i = 0; i < vault.getContainerSize(); i++) {
                ItemStack s = vault.getItem(i);
                if (CronusItems.isBugvisor(s)) return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void clearBoundBugvisorList(NonNullList<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (CronusItems.isBugvisor(s) && CronusItems.isBugvisorBound(s)) {
                list.set(i, ItemStack.EMPTY);
            }
        }
    }

    private static void clearBoundBugvisorContainer(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (CronusItems.isBugvisor(s) && CronusItems.isBugvisorBound(s)) {
                c.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
