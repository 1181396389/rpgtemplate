package com.rpgtemplate.forge.system;

import com.google.gson.JsonObject;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.service.LevelingService;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatFormula;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.network.PacketDistributor;
import com.rpgtemplate.forge.network.SyncSystemDataPayload;
import com.rpgtemplate.forge.possession.SkillBorrow;
import com.rpgtemplate.forge.registry.ModCapabilities;
import com.rpgtemplate.forge.registry.PlayerDataStore;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;

public final class SystemDataManager {
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, PlayerDataStore> LAST_STORE = new ConcurrentHashMap<>();

    private SystemDataManager() {
    }

    public static void markDirty(Player player) {
        DIRTY.add(player.getUUID());
        UUID lender = SkillBorrow.lenderOf(player.getUUID());
        if (lender != null) {
            DIRTY.add(lender);
        }
    }

    public static void flushIfDirty(ServerPlayer player) {
        if (DIRTY.remove(player.getUUID())) {
            sync(player);
        }
    }

    public static void forget(UUID uuid) {
        DIRTY.remove(uuid);
        LAST_STORE.remove(uuid);
    }

    public static boolean available(Player player) {
        return player != null;
    }

    public static PlayerSystemData get(Player player) {
        if (!player.level().isClientSide) {
            PlayerSystemData borrowed = SkillBorrow.dataFor(player.getUUID());
            if (borrowed != null) {
                return borrowed;
            }
        }
        return raw(player);
    }

    public static PlayerSystemData raw(Player player) {
        PlayerDataStore store = player.getData(ModCapabilities.PLAYER_DATA);
        boolean isServer = !player.level().isClientSide;
        if (store != null) {
            if (isServer) {
                LAST_STORE.put(player.getUUID(), store);
            }
            return store.get();
        }
        PlayerDataStore cached = isServer ? LAST_STORE.get(player.getUUID()) : null;
        if (cached != null) {
            return cached.get();
        }
        throw new IllegalStateException("Missing RPG player attachment (" + player.getGameProfile().getName() + ", removed=" + player.isRemoved() + ")");
    }

    public static void sync(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        PlayerSystemData data = raw(player);
        TemplateRegistry reg = TemplateRegistry.get();
        CoreConfig cfg = CoreConfig.get();
        if (data.isActivated()) {
            StatCalculator.normalizeCurrent(data, reg, cfg);
        }

        JsonObject json = data.toJson();
        JsonObject derived = new JsonObject();
        if (data.isActivated()) {
            derived.addProperty("maxHp", StatCalculator.maxStat(StatType.HP, data, reg, cfg));
            derived.addProperty("maxMp", StatCalculator.maxStat(StatType.MP, data, reg, cfg));
            derived.addProperty("defense", StatCalculator.maxStat(StatType.DEF, data, reg, cfg));
            derived.addProperty("maxSp", StatCalculator.maxStat(StatType.SP, data, reg, cfg));
            derived.addProperty("xpToNext", StatFormula.xpToNextLevel(data.level(), cfg));
            TemplateInstance loaded = data.loadedTemplate().orElse(null);
            if (loaded != null) {
                TemplateDefinition def = reg.find(loaded.templateId()).orElse(null);
                if (def != null && !def.levelless()) {
                    derived.addProperty("templateXpToNext", LevelingService.templateXpToNext(loaded.level(), def, cfg));
                    derived.addProperty("templateMaxLevel", def.maxLevel());
                }
            }

            derived.addProperty("growthMode", cfg.growthMode.name());

            for (StatType type : StatType.values()) {
                String label = type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT);
                derived.addProperty("level" + label, StatFormula.levelStat(type, data.level(), cfg));
                derived.addProperty("loaded" + label, StatCalculator.loadedBonus(type, data, reg, cfg));
                derived.addProperty("collection" + label, StatCalculator.collectionBonus(type, data, reg, cfg));
            }

            JsonObject tplXp = new JsonObject();
            for (TemplateInstance inst : data.templates()) {
                reg.find(inst.templateId())
                    .filter(d -> !d.levelless())
                    .ifPresent(d -> tplXp.addProperty(inst.templateId(), LevelingService.templateXpToNext(inst.level(), d, cfg)));
            }

            derived.add("templateXp", tplXp);
            derived.addProperty("templateLevelBonus", cfg.templateLevelBonusPerLevel);
            derived.addProperty("collectionRatio", cfg.collectionBonusRatio);
            derived.addProperty("skillDamageConversion", ModConfigs.SERVER.skillDamageConversion.get());
            derived.addProperty("skillHealConversion", ModConfigs.SERVER.skillHealConversion.get());
            derived.addProperty(
                "terrainAllowed",
                ModConfigs.SERVER.terrainDestruction.get()
                    && (!ModConfigs.SERVER.terrainRespectMobGriefing.get() || player.serverLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))
            );
            derived.addProperty("drawLuck", PassiveApplier.drawLuck(data));
            derived.addProperty("gameTime", player.serverLevel().getGameTime());
        }

        json.add("derived", derived);
        PacketDistributor.sendToPlayer(player, new SyncSystemDataPayload(json.toString()));
    }
}
