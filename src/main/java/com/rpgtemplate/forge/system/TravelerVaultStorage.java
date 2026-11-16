package com.rpgtemplate.forge.system;
import net.minecraft.core.HolderLookup;

import com.rpgtemplate.core.data.PlayerSystemData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 穿越者随身储物空间（27 格跨维度/跨死亡持久化仓库）：
 * <ul>
 *   <li>数据持久化于主世界 {@code DimensionDataStorage}（{@code rpgtemplate_traveler_vault.dat}），按玩家 UUID 独立保存，
 *       死亡不掉落、跨维度通用、重启世界不丢失。</li>
 *   <li>采用原版 3×9 {@link ChestMenu} 容器协议，完整兼容 Shift 快捷存取、右键拆分、整理模组与联机防并发复制。</li>
 *   <li>同步 {@link #KEY_VAULT_USED} 至面板数据，令系统「物品」界面实时显示已用格数。</li>
 * </ul>
 */
public final class TravelerVaultStorage extends SavedData {
    public static final int SLOTS = 27;
    public static final String KEY_VAULT_USED = "vault.used";

    private static final String DATA_NAME = "rpgtemplate_traveler_vault";
    private static final String TAG_VAULTS = "Vaults";
    private static final String TAG_SLOT = "Slot";

    private final Map<UUID, SimpleContainer> vaults = new HashMap<>();

    public TravelerVaultStorage() {
    }

    public static TravelerVaultStorage get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TravelerVaultStorage::new, TravelerVaultStorage::load), DATA_NAME);
    }

    public static SimpleContainer containerOf(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return null;
        }
        return get(player.getServer()).getOrCreate(player.getUUID());
    }

    public static int countUsedSlots(SimpleContainer container) {
        if (container == null) {
            return 0;
        }
        int used = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** 将玩家随身仓库的已用格数同步至穿越者面板数据（仅在变动时标记脏并下发）。 */
    public static void syncVaultCount(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        int used = countUsedSlots(containerOf(player));
        int prev = (int) Math.round(data.value(KEY_VAULT_USED, -1.0));
        if (used != prev) {
            data.setValue(KEY_VAULT_USED, (double) used);
            SystemDataManager.markDirty((Player) player);
            SystemDataManager.sync(player);
        }
    }

    /** 玩家在穿越者面板「物品」界面点击「打开随身仓库」。 */
    public static void openVault(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        SimpleContainer vault = containerOf(player);
        if (vault == null) {
            return;
        }
        syncVaultCount(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.05f);
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> ChestMenu.threeRows(id, inv, vault),
            Component.translatable("gui.rpgtemplate.items.vault_title")
        ));
    }

    private SimpleContainer getOrCreate(UUID ownerId) {
        return this.vaults.computeIfAbsent(ownerId, id -> createTrackedContainer());
    }

    private SimpleContainer createTrackedContainer() {
        SimpleContainer container = new SimpleContainer(SLOTS) {
            @Override
            public void stopOpen(Player player) {
                super.stopOpen(player);
                TravelerVaultStorage.this.setDirty();
                if (player instanceof ServerPlayer sp) {
                    syncVaultCount(sp);
                }
            }
        };
        container.addListener(c -> this.setDirty());
        return container;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, SimpleContainer> entry : this.vaults.entrySet()) {
            SimpleContainer c = entry.getValue();
            ListTag list = new ListTag();
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!stack.isEmpty()) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.putByte(TAG_SLOT, (byte) i);
                    slotTag.put("Item", stack.save(provider));
                    list.add(slotTag);
                }
            }
            if (!list.isEmpty()) {
                all.put(entry.getKey().toString(), list);
            }
        }
        root.put(TAG_VAULTS, all);
        return root;
    }

    public static TravelerVaultStorage load(CompoundTag root, HolderLookup.Provider provider) {
        TravelerVaultStorage storage = new TravelerVaultStorage();
        if (!root.contains(TAG_VAULTS, Tag.TAG_COMPOUND)) {
            return storage;
        }
        CompoundTag all = root.getCompound(TAG_VAULTS);
        for (String key : all.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            SimpleContainer c = storage.createTrackedContainer();
            ListTag list = all.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag slotTag = list.getCompound(i);
                int slot = slotTag.getByte(TAG_SLOT) & 0xFF;
                if (slot >= 0 && slot < SLOTS) {
                    ItemStack stack = slotTag.contains("Item", Tag.TAG_COMPOUND) ? ItemStack.parseOptional(provider, slotTag.getCompound("Item")) : ItemStack.parseOptional(provider, slotTag);
                    if (!stack.isEmpty()) {
                        c.setItem(slot, stack);
                    }
                }
            }
            storage.vaults.put(id, c);
        }
        return storage;
    }
}
