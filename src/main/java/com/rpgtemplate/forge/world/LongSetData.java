package com.rpgtemplate.forge.world;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class LongSetData extends SavedData {
    public static final String PLACED = "rpgtemplate_placed";
    public static final String EXPLORED = "rpgtemplate_explored";
    private static final int PLACED_CAP = 200000;
    private static final int EXPLORED_CAP = 2000000;
    private final LongLinkedOpenHashSet set = new LongLinkedOpenHashSet();
    private final int cap;

    private LongSetData(int cap) {
        this.cap = cap;
    }

    public static LongSetData placed(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> new LongSetData(PLACED_CAP), (tag, prov) -> load(tag, PLACED_CAP)),
            PLACED
        );
    }

    public static LongSetData explored(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> new LongSetData(EXPLORED_CAP), (tag, prov) -> load(tag, EXPLORED_CAP)),
            EXPLORED
        );
    }

    public boolean add(long v) {
        boolean added = this.set.add(v);
        if (added) {
            while (this.set.size() > this.cap) {
                this.set.removeFirstLong();
            }
            this.setDirty();
        }
        return added;
    }

    public boolean remove(long v) {
        boolean removed = this.set.remove(v);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public boolean contains(long v) {
        return this.set.contains(v);
    }

    public int size() {
        return this.set.size();
    }

    public void clear() {
        this.set.clear();
        this.setDirty();
    }

    private static LongSetData load(CompoundTag tag, int cap) {
        LongSetData data = new LongSetData(cap);
        for (long v : tag.getLongArray("values")) {
            data.set.add(v);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        long[] arr = new long[this.set.size()];
        int i = 0;
        LongListIterator it = this.set.iterator();
        while (it.hasNext()) {
            arr[i++] = it.nextLong();
        }
        tag.putLongArray("values", arr);
        return tag;
    }
}
