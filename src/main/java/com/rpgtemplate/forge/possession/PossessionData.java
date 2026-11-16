package com.rpgtemplate.forge.possession;
import net.minecraft.core.HolderLookup;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PossessionData extends SavedData {
    public static final String NAME = "rpgtemplate_possession";
    public final Map<UUID, PossessionData.Rec> recs = new LinkedHashMap<>();
    public final Map<UUID, CompoundTag> pending = new HashMap<>();
    public int nextSlot;
    private final ListTag quarantine = new ListTag();

    public static PossessionData load(CompoundTag var0) {
        PossessionData var1 = new PossessionData();
        ListTag var2 = var0.getList("Recs", 10);

        for (int var3 = 0; var3 < var2.size(); var3++) {
            try {
                PossessionData.Rec var4 = PossessionData.Rec.load(var2.getCompound(var3));
                var1.recs.put(var4.sukuna, var4);
            } catch (RuntimeException var8) {
                var1.quarantine.add(var2.getCompound(var3).copy());
                LogUtils.getLogger().error("[rpgtemplate] malformed possession record quarantined", var8);
            }
        }

        CompoundTag var9 = var0.getCompound("Pending");

        for (String var5 : var9.getAllKeys()) {
            try {
                var1.pending.put(UUID.fromString(var5), var9.getCompound(var5));
            } catch (IllegalArgumentException var7) {
            }
        }

        var1.quarantine.addAll(var0.getList("Quarantine", 10));
        var1.nextSlot = Math.max(0, Math.min(7000, var0.getInt("NextSlot")));
        return var1;
    }

    public CompoundTag save(CompoundTag var1, HolderLookup.Provider provider) {
        ListTag var2 = new ListTag();

        for (PossessionData.Rec var4 : this.recs.values()) {
            var2.add(var4.save());
        }

        var1.put("Recs", var2);
        var1.put("Quarantine", this.quarantine.copy());
        CompoundTag var5 = new CompoundTag();
        this.pending.forEach((var1x, var2x) -> var5.put(var1x.toString(), var2x));
        var1.put("Pending", var5);
        var1.putInt("NextSlot", this.nextSlot);
        return var1;
    }

    public static PossessionData get(MinecraftServer var0) {
        return var0.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PossessionData::new, (tag, prov) -> load(tag)), "rpgtemplate_possession");
    }

    public void addPending(UUID var1, CompoundTag var2) {
        CompoundTag var3 = this.pending.computeIfAbsent(var1, var0 -> new CompoundTag());
        var3.merge(var2);
        this.setDirty();
    }

    public static enum Phase {
        FINGER,
        BATTLE,
        TRAPPED,
        POSSESSING,
        CONTRACT;
    }

    public static final class Rec {
        public UUID sukuna;
        public String sukunaName = "";
        public PossessionData.Phase phase = PossessionData.Phase.FINGER;
        public UUID fingerId;
        public UUID host;
        public String hostName = "";
        public int sukunaMode;
        public int hostMode;
        public CompoundTag sukunaStash;
        public CompoundTag hostBackup;
        public boolean swapping;
        public boolean suspended;
        public boolean watch = true;
        public boolean lending;
        public boolean hostUsesBorrowed = true;
        public long contractUntil;
        public long contractCooldownUntil;
        public int realmSlot = -1;
        public double progress;
        public long battleStart;
        public long battleEnd;
        public double bx;
        public double by;
        public double bz;
        public String bdim = "minecraft:overworld";
        public String params = "{}";
        public long startedAt;

        CompoundTag save() {
            CompoundTag var1 = new CompoundTag();
            var1.putUUID("S", this.sukuna);
            var1.putString("SName", this.sukunaName);
            var1.putString("Phase", this.phase.name());
            if (this.fingerId != null) {
                var1.putUUID("Finger", this.fingerId);
            }

            if (this.host != null) {
                var1.putUUID("H", this.host);
            }

            var1.putString("HName", this.hostName);
            var1.putInt("SMode", this.sukunaMode);
            var1.putInt("HMode", this.hostMode);
            if (this.sukunaStash != null) {
                var1.put("Stash", this.sukunaStash);
            }

            if (this.hostBackup != null) {
                var1.put("HostBackup", this.hostBackup);
            }

            var1.putBoolean("Swapping", this.swapping);
            var1.putBoolean("Suspended", this.suspended);
            var1.putBoolean("Watch", this.watch);
            var1.putBoolean("Lending", this.lending);
            var1.putBoolean("HostBorrow", this.hostUsesBorrowed);
            var1.putLong("ContractUntil", this.contractUntil);
            var1.putLong("ContractCd", this.contractCooldownUntil);
            var1.putInt("Slot", this.realmSlot);
            var1.putDouble("Progress", this.progress);
            var1.putLong("BStart", this.battleStart);
            var1.putLong("BEnd", this.battleEnd);
            var1.putDouble("BX", this.bx);
            var1.putDouble("BY", this.by);
            var1.putDouble("BZ", this.bz);
            var1.putString("BDim", this.bdim);
            var1.putString("Params", this.params);
            var1.putLong("Started", this.startedAt);
            return var1;
        }

        static PossessionData.Rec load(CompoundTag var0) {
            PossessionData.Rec var1 = new PossessionData.Rec();
            var1.sukuna = var0.getUUID("S");
            var1.sukunaName = var0.getString("SName");

            try {
                var1.phase = PossessionData.Phase.valueOf(var0.getString("Phase"));
            } catch (IllegalArgumentException var3) {
                var1.phase = PossessionData.Phase.FINGER;
            }

            if (var0.hasUUID("Finger")) {
                var1.fingerId = var0.getUUID("Finger");
            }

            if (var0.hasUUID("H")) {
                var1.host = var0.getUUID("H");
            }

            var1.hostName = var0.getString("HName");
            var1.sukunaMode = var0.getInt("SMode");
            var1.hostMode = var0.getInt("HMode");
            if (var0.contains("Stash", 10)) {
                var1.sukunaStash = var0.getCompound("Stash");
            }

            if (var0.contains("HostBackup", 10)) {
                var1.hostBackup = var0.getCompound("HostBackup");
            }

            var1.swapping = var0.getBoolean("Swapping");
            var1.suspended = var0.getBoolean("Suspended");
            var1.watch = var0.getBoolean("Watch");
            var1.lending = var0.getBoolean("Lending");
            var1.hostUsesBorrowed = !var0.contains("HostBorrow") || var0.getBoolean("HostBorrow");
            var1.contractUntil = var0.getLong("ContractUntil");
            var1.contractCooldownUntil = var0.getLong("ContractCd");
            var1.realmSlot = var0.contains("Slot") ? var0.getInt("Slot") : -1;
            var1.progress = var0.getDouble("Progress");
            if (!Double.isFinite(var1.progress)) {
                var1.progress = 0.0;
            }

            var1.progress = Math.max(-100.0, Math.min(100.0, var1.progress));
            var1.battleStart = var0.getLong("BStart");
            var1.battleEnd = var0.getLong("BEnd");
            var1.bx = var0.getDouble("BX");
            var1.by = var0.getDouble("BY");
            var1.bz = var0.getDouble("BZ");
            var1.bdim = var0.getString("BDim");
            var1.params = var0.contains("Params") ? var0.getString("Params") : "{}";
            var1.startedAt = var0.getLong("Started");
            return var1;
        }
    }
}
