package com.rpgtemplate.forge.registry;

import com.rpgtemplate.forge.RpgTemplateMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge 1.21.1 玩家系统数据 Attachment 注册（替代 Forge 1.20.1 Capability + Clone 事件）。
 */
public final class ModCapabilities {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RpgTemplateMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerDataStore>> PLAYER_DATA =
        ATTACHMENTS.register("player_data", () -> AttachmentType.builder(PlayerDataStore::new)
            .serialize(new IAttachmentSerializer<CompoundTag, PlayerDataStore>() {
                @Override
                public PlayerDataStore read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    PlayerDataStore store = new PlayerDataStore();
                    if (tag != null && tag.contains("json")) {
                        store.load(tag.getString("json"));
                    }
                    return store;
                }

                @Override
                public CompoundTag write(PlayerDataStore attachment, HolderLookup.Provider provider) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("json", attachment.get().toJsonString());
                    return tag;
                }
            })
            .copyOnDeath()
            .build());

    private ModCapabilities() {
    }
}
