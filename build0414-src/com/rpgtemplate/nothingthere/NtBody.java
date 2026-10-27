package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 一无所有的外观体型（人形态：模板载入后、茧孵化后）。
 * 人形态时判定箱放大到 1.2 宽、3.0 高，渲染见 client/NtBodyLayer。茧态保持普通玩家体型。
 * 服务端与客户端都用同一套判断；客户端只能看到本地玩家的数据（远程玩家保持 1.8 高，直到做状态同步）。
 */
public final class NtBody {
    public static final String TEMPLATE_ID = "rpgtemplate:nothing_there";
    /** 判定箱：原版人形宽 0.6、高 1.8，放大后宽 1.2、高 3.0。 */
    public static final float HITBOX_WIDTH_SCALE = 2.0F;
    public static final float HITBOX_HEIGHT_SCALE = 3.0F / 1.8F;

    private static final Logger LOG = LogManager.getLogger("rpgtemplate/NtBody");
    private static final Map<UUID, Boolean> LAST_SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LAST_CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, String> DIAG_LAST = new ConcurrentHashMap<>();

    private NtBody() {
    }

    /** True when the player is a 一无所有 user in human form (not inside a 茧). */
    public static boolean isHuman(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            PlayerSystemData data = SystemDataManager.get(player);
            if (!data.isActivated() || !TEMPLATE_ID.equals(data.loadedTemplateId())) {
                return false;
            }
            long now = player.m_9236_().m_46467_();
            return data.value(NtForms.KEY_COCOON_HATCH, 0.0) <= (double) now;
        } catch (RuntimeException e) {
            // Entity construction can call getDimensions before the capability exists.
            return false;
        }
    }

    /** True while a goodbye strike is pending (the left arm shows the giant blade). */
    public static boolean goodbyePending(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            return SystemDataManager.get(player).value(NtForms.KEY_GB_STRIKE_AT, 0.0) > 0.0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Re-runs the bounding box when the human state flips. Called from the player tick on both sides. */
    public static void refreshIfChanged(Player player) {
        boolean client = player.m_9236_().m_5776_();
        Map<UUID, Boolean> last = client ? LAST_CLIENT : LAST_SERVER;
        UUID id = player.m_20148_();
        boolean now = isHuman(player);
        boolean before = last.getOrDefault(id, Boolean.FALSE);
        logDiagnostics(player, client, now);
        if (now != before) {
            last.put(id, now);
            player.m_6210_();
        }
    }

    /** Logs the inputs of isHuman, only when they change (keeps latest.log readable). */
    private static void logDiagnostics(Player player, boolean client, boolean human) {
        String diag;
        try {
            boolean available = SystemDataManager.available(player);
            if (!available) {
                diag = "available=false";
            } else {
                PlayerSystemData data = SystemDataManager.get(player);
                diag = "available=true activated=" + data.isActivated()
                    + " loaded=" + data.loadedTemplateId()
                    + " hatch=" + data.value(NtForms.KEY_COCOON_HATCH, 0.0)
                    + " tick=" + player.m_9236_().m_46467_();
            }
        } catch (RuntimeException e) {
            diag = "exception=" + e;
        }
        String line = (client ? "client" : "server") + " " + diag + " -> human=" + human;
        if (!line.equals(DIAG_LAST.put(player.m_20148_(), line))) {
            LOG.info("[NtBody] player={} {}", player.m_36316_().getName(), line);
        }
    }
}
