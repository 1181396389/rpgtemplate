package com.rpgtemplate.forge.system;

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
        ServerLevel overworld = server.m_129783_();
        return overworld.m_8895_().m_164861_(TravelerVaultStorage::load, TravelerVaultStorage::new, DATA_NAME);
    }

    public static SimpleContainer containerOf(ServerPlayer player) {
        if (player == null || player.m_20194_() == null) {
            return null;
        }
        return get(player.m_20194_()).getOrCreate(player.m_20148_());
    }

    public static int countUsedSlots(SimpleContainer container) {
        if (container == null) {
            return 0;
        }
        int used = 0;
        for (int i = 0; i < container.m_6643_(); i++) {
            if (!container.m_8020_(i).m_41619_()) {
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
        player.m_9236_().m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(),
            SoundEvents.f_11889_, SoundSource.PLAYERS, 0.5f, 1.05f);
        player.m_5893_(new SimpleMenuProvider(
            (id, inv, p) -> ChestMenu.m_39237_(id, inv, vault),
            Component.m_237115_("gui.rpgtemplate.items.vault_title")
        ));
    }

    private SimpleContainer getOrCreate(UUID ownerId) {
        return this.vaults.computeIfAbsent(ownerId, id -> createTrackedContainer());
    }

    private SimpleContainer createTrackedContainer() {
        SimpleContainer container = new SimpleContainer(SLOTS) {
            @Override
            public void m_5785_(Player player) {
                super.m_5785_(player);
                TravelerVaultStorage.this.m_77762_();
                if (player instanceof ServerPlayer sp) {
                    syncVaultCount(sp);
                }
            }
        };
        container.m_19164_(c -> this.m_77762_());
        return container;
    }

    @Override
    public CompoundTag m_7176_(CompoundTag root) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, SimpleContainer> entry : this.vaults.entrySet()) {
            SimpleContainer c = entry.getValue();
            ListTag list = new ListTag();
            for (int i = 0; i < c.m_6643_(); i++) {
                ItemStack stack = c.m_8020_(i);
                if (!stack.m_41619_()) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.m_128344_(TAG_SLOT, (byte) i);
                    stack.m_41739_(slotTag);
                    list.add(slotTag);
                }
            }
            if (!list.isEmpty()) {
                all.m_128365_(entry.getKey().toString(), list);
            }
        }
        root.m_128365_(TAG_VAULTS, all);
        return root;
    }

    public static TravelerVaultStorage load(CompoundTag root) {
        TravelerVaultStorage storage = new TravelerVaultStorage();
        if (!root.m_128425_(TAG_VAULTS, Tag.f_178203_)) {
            return storage;
        }
        CompoundTag all = root.m_128469_(TAG_VAULTS);
        for (String key : all.m_128431_()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            SimpleContainer c = storage.createTrackedContainer();
            ListTag list = all.m_128437_(key, Tag.f_178203_);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag slotTag = list.m_128728_(i);
                int slot = slotTag.m_128445_(TAG_SLOT) & 0xFF;
                if (slot >= 0 && slot < SLOTS) {
                    ItemStack stack = ItemStack.m_41712_(slotTag);
                    if (!stack.m_41619_()) {
                        c.m_6836_(slot, stack);
                    }
                }
            }
            storage.vaults.put(id, c);
        }
        return storage;
    }
}
