package com.rpgtemplate.forge.system;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusGearManager;
import com.rpgtemplate.forge.mask.PerceptionMaskManager;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * 穿越者系统面板（K 键）服务端指令路由：
 * 保留既有 {@code draw / load / unload / advance / lineage_draw / setting} 全部协议，
 * 并新增 {@code mask_take} 与 {@code mask_store} 以支持在穿越者面板拿取/存回「认知阻碍面具」。
 */
public final class PanelActions {
    public static final String SETTING_PREFIX = "setting.";
    public static final String TERRAIN = "terrain_destruction";

    private static final Set<String> SETTINGS = Set.of(TERRAIN);
    private static final Map<String, Double> DEFAULTS = Map.of(TERRAIN, 1.0);

    private PanelActions() {
    }

    public static void handle(ServerPlayer player, String action, String arg) {
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        switch (action) {
            case "draw" -> SystemActions.draw(player);
            case "load" -> SystemActions.requestLoad(player, arg);
            case "unload" -> SystemActions.unload(player);
            case "advance" -> SystemActions.advance(player, arg);
            case "lineage_draw" -> {
                int bar = arg.indexOf('|');
                if (bar > 0) {
                    SystemActions.lineageDraw(player, arg.substring(0, bar), arg.substring(bar + 1));
                } else {
                    SystemActions.lineageDraw(player, arg, null);
                }
            }
            case "setting" -> {
                int eq = arg.indexOf('=');
                if (eq <= 0) {
                    return;
                }
                String key = arg.substring(0, eq);
                if (!SETTINGS.contains(key)) {
                    return;
                }
                String val = arg.substring(eq + 1);
                boolean on = "1".equals(val) || "true".equalsIgnoreCase(val);
                data.setValue(SETTING_PREFIX + key, on ? 1.0 : 0.0);
                SystemDataManager.sync(player);
            }
            case "mask_take" -> PerceptionMaskManager.handleTake(player);
            case "mask_equip" -> PerceptionMaskManager.handleEquip(player);
            case "mask_store" -> PerceptionMaskManager.handleStore(player);
            case "vault_open" -> TravelerVaultStorage.openVault(player);
            case "cronus_driver_take" -> CronusGearManager.handleDriverTake(player);
            case "cronus_driver_store" -> CronusGearManager.handleDriverStore(player);
            case "cronus_gashat_take" -> CronusGearManager.handleGashatTake(player);
            case "cronus_gashat_store" -> CronusGearManager.handleGashatStore(player);
            case "cronus_set_take" -> CronusGearManager.handleTakeSet(player);
            default -> {
            }
        }
    }

    public static boolean setting(PlayerSystemData data, String key) {
        return data.value(SETTING_PREFIX + key, DEFAULTS.getOrDefault(key, 0.0)) > 0.5;
    }
}
