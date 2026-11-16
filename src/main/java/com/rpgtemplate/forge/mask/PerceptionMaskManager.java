package com.rpgtemplate.forge.mask;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TravelerVaultStorage;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 穿越者面板「认知阻碍面具」签发、存回、遗失检测与防复制唯一性管理器。
 * <ul>
 *   <li>{@code mask.state == 0}：面具存放于穿越者面板中，可随时拿取。</li>
 *   <li>{@code mask.state == 1}：玩家随身持有（装备/背包/副手/指针/末影箱），可一键存回面板。</li>
 *   <li>{@code mask.state == 2}：面具正掉落在附近世界地面，需拾回或待其损毁/远离遗失。</li>
 *   <li>{@code mask.state == 3}：上一个面具已遗失或销毁，可重新拿取新面具（旧面具自动失效消散）。</li>
 * </ul>
 */
public final class PerceptionMaskManager {
    public static final String KEY_SEQ = "mask.seq";
    public static final String KEY_TOKEN = "mask.token";
    public static final String KEY_STATE = "mask.state";

    public static final int STATE_STORED = 0;
    public static final int STATE_HELD = 1;
    public static final int STATE_DROPPED_NEARBY = 2;
    public static final int STATE_LOST = 3;

    private static final double NEARBY_DROP_RANGE = 64.0;

    private PerceptionMaskManager() {
    }

    /** 玩家在穿越者面板点击「拿取 / 补领面具」。 */
    public static void handleTake(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        long activeToken = (long) data.value(KEY_TOKEN, 0.0);
        if (activeToken > 0L) {
            if (hasPlayerMask(player, player.getUUID(), activeToken)) {
                updateState(player, data, STATE_HELD);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.already_held")
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
            ItemEntity nearby = findNearbyDroppedMask(player, player.getUUID(), activeToken);
            if (nearby != null) {
                updateState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.dropped_nearby",
                    (int) Math.floor(nearby.getX()),
                    (int) Math.floor(nearby.getY()),
                    (int) Math.floor(nearby.getZ()))
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        // 签发新令牌（若上一个遗失则自动使旧令牌作废）
        long nextToken = Math.max(1L, (long) data.value(KEY_SEQ, 0.0) + 1L);
        ItemStack stack = new ItemStack(PerceptionMaskBootstrap.MASK.get());
        PerceptionBlockingMaskItem.stamp(stack, player.getUUID(), nextToken);

        boolean placed = false;
        if (player.getInventory().add(stack)) {
            placed = true;
        } else if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.setItemSlot(EquipmentSlot.HEAD, stack);
            placed = true;
        }
        if (!placed) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.inventory_full")
                .withStyle(ChatFormatting.RED));
            return;
        }

        data.setValue(KEY_SEQ, (double) nextToken);
        data.setValue(KEY_TOKEN, (double) nextToken);
        data.setValue(KEY_STATE, (double) STATE_HELD);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.7f, 0.85f);
        Msg.actionBar(player, Component.translatable(activeToken > 0L
            ? "message.rpgtemplate.mask.reclaimed"
            : "message.rpgtemplate.mask.taken")
            .withStyle(ChatFormatting.GRAY));
    }

    /** 玩家在穿越者面板「物品」界面点击「一键佩戴」（若已持有则直接切换至头盔槽，若存放/遗失则签发并直接佩戴）。 */
    public static void handleEquip(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (PerceptionBlockingMaskItem.isMask(head) && isStackValidOnServer(player, head)) {
            updateState(player, data, STATE_HELD);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.already_worn")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        long activeToken = (long) data.value(KEY_TOKEN, 0.0);
        if (activeToken > 0L) {
            if (hasPlayerMask(player, player.getUUID(), activeToken)) {
                boolean maskInMainInv = containsInList(player.getInventory().items, player.getUUID(), activeToken);
                if (!head.isEmpty() && !canAddToInventory(player) && !maskInMainInv) {
                    Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.equip_helmet_full")
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                ItemStack pulled = extractFirstPlayerMask(player, player.getUUID(), activeToken);
                if (!pulled.isEmpty()) {
                    if (!head.isEmpty()) {
                        player.getInventory().add(head.copy());
                    }
                    player.setItemSlot(EquipmentSlot.HEAD, pulled);
                    updateState(player, data, STATE_HELD);
                    TravelerVaultStorage.syncVaultCount(player);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.75f, 0.8f);
                    Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.equipped")
                        .withStyle(ChatFormatting.GRAY));
                    return;
                }
            }
            ItemEntity nearby = findNearbyDroppedMask(player, player.getUUID(), activeToken);
            if (nearby != null) {
                updateState(player, data, STATE_DROPPED_NEARBY);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.dropped_nearby",
                    (int) Math.floor(nearby.getX()),
                    (int) Math.floor(nearby.getY()),
                    (int) Math.floor(nearby.getZ()))
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        if (!head.isEmpty() && !canAddToInventory(player)) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.equip_helmet_full")
                .withStyle(ChatFormatting.RED));
            return;
        }
        long nextToken = Math.max(1L, (long) data.value(KEY_SEQ, 0.0) + 1L);
        ItemStack stack = new ItemStack(PerceptionMaskBootstrap.MASK.get());
        PerceptionBlockingMaskItem.stamp(stack, player.getUUID(), nextToken);
        if (!head.isEmpty()) {
            player.getInventory().add(head.copy());
        }
        player.setItemSlot(EquipmentSlot.HEAD, stack);

        data.setValue(KEY_SEQ, (double) nextToken);
        data.setValue(KEY_TOKEN, (double) nextToken);
        data.setValue(KEY_STATE, (double) STATE_HELD);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.75f, 0.8f);
        Msg.actionBar(player, Component.translatable(activeToken > 0L
            ? "message.rpgtemplate.mask.reclaimed_equipped"
            : "message.rpgtemplate.mask.equipped")
            .withStyle(ChatFormatting.GRAY));
    }

    /** 玩家在穿越者面板点击「存回面板」。 */
    public static void handleStore(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        long activeToken = (long) data.value(KEY_TOKEN, 0.0);
        boolean removed = removePlayerMasks(player, player.getUUID(), activeToken);
        if (!removed) {
            int state = computeLiveState(player, data);
            updateState(player, data, state);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.not_found_to_store")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }
        data.setValue(KEY_TOKEN, 0.0);
        data.setValue(KEY_STATE, (double) STATE_STORED);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        TravelerVaultStorage.syncVaultCount(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.65f, 1.15f);
        Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.stored")
            .withStyle(ChatFormatting.AQUA));
    }

    /** 服务端周期校验：清理失效的旧代面具（防止补领后复制）并保持面板状态与随身仓库格数同步。 */
    public static void tickPlayer(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        if (purgeStaleMasks(player)) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.mask.stale_dissolved")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        int live = computeLiveState(player, data);
        int prev = (int) Math.round(data.value(KEY_STATE, (double) STATE_STORED));
        if (live != prev) {
            updateState(player, data, live);
        }
        TravelerVaultStorage.syncVaultCount(player);
    }

    /** 校验某张面具在服务端是否仍然有效（非已作废的旧代复制品）。 */
    public static boolean isStackValidOnServer(ServerPlayer holder, ItemStack stack) {
        if (!PerceptionBlockingMaskItem.isMask(stack)) {
            return false;
        }
        UUID owner = PerceptionBlockingMaskItem.ownerOf(stack);
        long token = PerceptionBlockingMaskItem.tokenOf(stack);
        if (owner == null || token <= 0L) {
            // 创造模式或命令直接给予的无主面具始终有效
            return true;
        }
        if (holder != null && owner.equals(holder.getUUID()) && SystemDataManager.available(holder)) {
            long current = (long) SystemDataManager.get(holder).value(KEY_TOKEN, 0.0);
            return current == token;
        }
        MinecraftServer server = holder == null ? null : holder.getServer();
        if (server != null) {
            ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
            if (ownerPlayer != null && SystemDataManager.available(ownerPlayer)) {
                long current = (long) SystemDataManager.get(ownerPlayer).value(KEY_TOKEN, 0.0);
                return current == token;
            }
        }
        return true;
    }

    private static void updateState(ServerPlayer player, PlayerSystemData data, int state) {
        data.setValue(KEY_STATE, (double) state);
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
    }

    private static int computeLiveState(ServerPlayer player, PlayerSystemData data) {
        long token = (long) data.value(KEY_TOKEN, 0.0);
        if (token <= 0L) {
            return STATE_STORED;
        }
        if (hasPlayerMask(player, player.getUUID(), token)) {
            return STATE_HELD;
        }
        if (findNearbyDroppedMask(player, player.getUUID(), token) != null) {
            return STATE_DROPPED_NEARBY;
        }
        return STATE_LOST;
    }

    private static boolean matchesActive(ItemStack stack, UUID ownerId, long token) {
        if (!PerceptionBlockingMaskItem.isMask(stack)) {
            return false;
        }
        UUID stackOwner = PerceptionBlockingMaskItem.ownerOf(stack);
        long stackToken = PerceptionBlockingMaskItem.tokenOf(stack);
        if (stackOwner == null || stackToken <= 0L) {
            // 允许将手中无主面具一并存回面板（避免误锁）
            return token <= 0L;
        }
        return ownerId.equals(stackOwner) && (token <= 0L || stackToken == token);
    }

    private static boolean canAddToInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack extractFirstPlayerMask(ServerPlayer player, UUID ownerId, long token) {
        Inventory inv = player.getInventory();
        ItemStack found = takeFromList(inv.items, ownerId, token);
        if (!found.isEmpty()) {
            return found;
        }
        found = takeFromList(inv.offhand, ownerId, token);
        if (!found.isEmpty()) {
            return found;
        }
        if (player.containerMenu != null && matchesActive(player.containerMenu.getCarried(), ownerId, token)) {
            ItemStack carried = player.containerMenu.getCarried().copy();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            return carried;
        }
        found = takeFromContainer(player.getEnderChestInventory(), ownerId, token);
        if (!found.isEmpty()) {
            return found;
        }
        return takeFromContainer(TravelerVaultStorage.containerOf(player), ownerId, token);
    }

    private static ItemStack takeFromList(NonNullList<ItemStack> list, UUID ownerId, long token) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (matchesActive(s, ownerId, token)) {
                ItemStack copy = s.copy();
                list.set(i, ItemStack.EMPTY);
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeFromContainer(Container container, UUID ownerId, long token) {
        if (container == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (matchesActive(s, ownerId, token)) {
                ItemStack copy = s.copy();
                container.setItem(i, ItemStack.EMPTY);
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasPlayerMask(ServerPlayer player, UUID ownerId, long token) {
        Inventory inv = player.getInventory();
        if (containsInList(inv.items, ownerId, token)
            || containsInList(inv.armor, ownerId, token)
            || containsInList(inv.offhand, ownerId, token)) {
            return true;
        }
        if (player.containerMenu != null && matchesActive(player.containerMenu.getCarried(), ownerId, token)) {
            return true;
        }
        if (containsInContainer(player.getEnderChestInventory(), ownerId, token)) {
            return true;
        }
        return containsInContainer(TravelerVaultStorage.containerOf(player), ownerId, token);
    }

    private static boolean containsInContainer(Container container, UUID ownerId, long token) {
        if (container == null) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (matchesActive(container.getItem(i), ownerId, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInList(NonNullList<ItemStack> list, UUID ownerId, long token) {
        for (ItemStack s : list) {
            if (matchesActive(s, ownerId, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removePlayerMasks(ServerPlayer player, UUID ownerId, long token) {
        boolean removed = false;
        Inventory inv = player.getInventory();
        removed |= clearFromList(inv.items, ownerId, token);
        removed |= clearFromList(inv.armor, ownerId, token);
        removed |= clearFromList(inv.offhand, ownerId, token);
        if (player.containerMenu != null && matchesActive(player.containerMenu.getCarried(), ownerId, token)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed = true;
        }
        removed |= clearFromContainer(player.getEnderChestInventory(), ownerId, token);
        removed |= clearFromContainer(TravelerVaultStorage.containerOf(player), ownerId, token);
        return removed;
    }

    private static boolean clearFromContainer(Container container, UUID ownerId, long token) {
        if (container == null) {
            return false;
        }
        boolean removed = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (matchesActive(container.getItem(i), ownerId, token)) {
                container.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean clearFromList(NonNullList<ItemStack> list, UUID ownerId, long token) {
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (matchesActive(list.get(i), ownerId, token)) {
                list.set(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean purgeStaleMasks(ServerPlayer player) {
        boolean purged = false;
        Inventory inv = player.getInventory();
        purged |= purgeList(player, inv.items);
        purged |= purgeList(player, inv.armor);
        purged |= purgeList(player, inv.offhand);
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (PerceptionBlockingMaskItem.isMask(carried) && !isStackValidOnServer(player, carried)) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                purged = true;
            }
        }
        SimpleContainer vault = TravelerVaultStorage.containerOf(player);
        if (vault != null) {
            for (int i = 0; i < vault.getContainerSize(); i++) {
                ItemStack s = vault.getItem(i);
                if (PerceptionBlockingMaskItem.isMask(s) && !isStackValidOnServer(player, s)) {
                    vault.setItem(i, ItemStack.EMPTY);
                    purged = true;
                }
            }
        }
        return purged;
    }

    private static boolean purgeList(ServerPlayer player, NonNullList<ItemStack> list) {
        boolean purged = false;
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (PerceptionBlockingMaskItem.isMask(s) && !isStackValidOnServer(player, s)) {
                list.set(i, ItemStack.EMPTY);
                purged = true;
            }
        }
        return purged;
    }

    private static ItemEntity findNearbyDroppedMask(ServerPlayer player, UUID ownerId, long token) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return null;
        }
        double maxDistSq = NEARBY_DROP_RANGE * NEARBY_DROP_RANGE;
        for (Entity e : sl.getAllEntities()) {
            if (e instanceof ItemEntity item && item.isAlive()
                && item.distanceToSqr(player) <= maxDistSq
                && matchesActive(item.getItem(), ownerId, token)) {
                return item;
            }
        }
        return null;
    }
}
