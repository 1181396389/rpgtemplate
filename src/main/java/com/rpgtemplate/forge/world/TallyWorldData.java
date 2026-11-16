package com.rpgtemplate.forge.world;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

public final class TallyWorldData extends SavedData {
    public static final String NAME = "rpgtemplate_tally";
    private UUID owner;
    private String ownerName = "";
    private long boundAt;

    public static TallyWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(TallyWorldData::new, (tag, prov) -> load(tag)),
            NAME
        );
    }

    public boolean hasOwner() {
        return this.owner != null;
    }

    public boolean isOwner(Player player) {
        return this.owner != null && this.owner.equals(player.getUUID());
    }

    public UUID owner() {
        return this.owner;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public long boundAt() {
        return this.boundAt;
    }

    public void bind(Player player, long now) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        this.boundAt = now;
        this.setDirty();
    }

    public void clear() {
        this.owner = null;
        this.ownerName = "";
        this.boundAt = 0L;
        this.setDirty();
    }

    private static TallyWorldData load(CompoundTag tag) {
        TallyWorldData data = new TallyWorldData();
        if (tag.hasUUID("owner")) {
            data.owner = tag.getUUID("owner");
        }
        data.ownerName = tag.getString("owner_name");
        data.boundAt = tag.getLong("bound_at");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.owner != null) {
            tag.putUUID("owner", this.owner);
        }
        tag.putString("owner_name", this.ownerName);
        tag.putLong("bound_at", this.boundAt);
        return tag;
    }
}
