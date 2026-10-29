package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 一无所有的外观体型（人形态：模板载入后、茧孵化后）。
 * 人形态时判定箱放大到 1.2 宽、3.0 高，渲染见 client/NtBodyLayer。茧态保持普通玩家体型。
 * 服务端根据模板数据判断，并把结果写入实体同步数据（DATA_HUMAN / DATA_GOODBYE）。所有客户端读取同步数据，
 * 因此其他玩家也能看到这个体型，判定箱在每个客户端上都一致。
 */
public final class NtBody {
    public static final String TEMPLATE_ID = "rpgtemplate:nothing_there";
    /** 判定箱：原版人形宽 0.6、高 1.8，放大后宽 1.2、高 3.0。 */
    public static final float HITBOX_WIDTH_SCALE = 2.0F;
    public static final float HITBOX_HEIGHT_SCALE = 3.0F / 1.8F;

    /** Synced human flag (every client sees it). Written by the server in refreshIfChanged. */
    public static final EntityDataAccessor<Boolean> DATA_HUMAN =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);
    /** Synced goodbye-pending flag (the left arm shows the giant blade on every client). */
    public static final EntityDataAccessor<Boolean> DATA_GOODBYE =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);
    /** Synced blade form: the left hand is the blade (技能 4 变形 on 手刀 form). */
    public static final EntityDataAccessor<Boolean> DATA_BLADE =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);

    /** Synced Holle cast flag (every client plays the cast pose). Written by the server in refreshIfChanged. */
    public static final EntityDataAccessor<Boolean> DATA_HOLLE =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);

    /** Synced cocoon flag (every client draws the egg). Written by the server in refreshIfChanged. */
    public static final EntityDataAccessor<Boolean> DATA_COCOON =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);

    /** Synced oh man charge flag (the right arm goes overhead while the hammer is held). Written in refreshIfChanged. */
    public static final EntityDataAccessor<Boolean> DATA_OH_CHARGE =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);
    /** Synced oh man slam flag (the right arm snaps down on every client). Written in refreshIfChanged. */
    public static final EntityDataAccessor<Boolean> DATA_OH_SLAM =
        SynchedEntityData.m_135353_(Player.class, EntityDataSerializers.f_135035_);

    private static final Logger LOG = LogManager.getLogger("rpgtemplate/NtBody");
    private static final Map<UUID, Boolean> LAST_SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LAST_CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, String> DIAG_LAST = new ConcurrentHashMap<>();

    private NtBody() {
    }

    /** Called from common setup, before any player exists, so the synced data ids are registered early. */
    public static void init() {
        LOG.info("[NtBody] synced data registered (human, goodbye)");
    }

    /** True when the player is a 一无所有 user in human form (not inside a 茧). Clients read the synced flag. */
    public static boolean isHuman(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_HUMAN);
            }
            return computeHuman(player);
        } catch (RuntimeException e) {
            // Entity construction can call getDimensions before the entity data exists.
            return false;
        }
    }

    /** True while the player is a 蛋 (茧) after death. Clients read the synced flag. */
    public static boolean inCocoon(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_COCOON);
            }
            return computeCocoon(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean computeCocoon(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            PlayerSystemData data = SystemDataManager.get(player);
            if (!data.isActivated() || !TEMPLATE_ID.equals(data.loadedTemplateId())) {
                return false;
            }
            long now = player.m_9236_().m_46467_();
            return data.value(NtForms.KEY_COCOON_HATCH, 0.0) > (double) now;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the left hand is the blade (form index 1 = 手刀). Clients read the synced flag. */
    public static boolean bladeForm(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_BLADE);
            }
            return computeBlade(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean computeBlade(Player player) {
        try {
            return computeHuman(player) && NtForms.current(SystemDataManager.get(player)) == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Server-side check from the template data (the source of the synced flag). */
    private static boolean computeHuman(Player player) {
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

    /** True while a goodbye strike is pending (the left arm shows the giant blade). Clients read the synced flag. */
    /** Holle cast pose flag, read on both sides. */
    public static boolean hollePending(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_HOLLE);
            }
            return computeHolle(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean computeHolle(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            return SystemDataManager.get(player).value(NtForms.KEY_HOLLE_UNTIL, 0.0) > (double) player.m_9236_().m_46467_();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean goodbyePending(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_GOODBYE);
            }
            return computeGoodbye(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean computeGoodbye(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            return SystemDataManager.get(player).value(NtForms.KEY_GB_STRIKE_AT, 0.0) > 0.0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** oh man charge flag, read on both sides. */
    public static boolean ohCharging(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_OH_CHARGE);
            }
            return computeOhCharge(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** oh man slam flag, read on both sides. */
    public static boolean ohSlamming(Player player) {
        try {
            if (player.m_9236_().m_5776_()) {
                return player.m_20088_().m_135370_(DATA_OH_SLAM);
            }
            return computeOhSlam(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean computeOhCharge(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            return SystemDataManager.get(player).value(NtForms.KEY_OH_CHARGE_UNTIL, 0.0) > (double) player.m_9236_().m_46467_();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean computeOhSlam(Player player) {
        try {
            if (!SystemDataManager.available(player)) {
                return false;
            }
            return SystemDataManager.get(player).value(NtForms.KEY_OH_SLAM_UNTIL, 0.0) > (double) player.m_9236_().m_46467_();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Re-runs the bounding box when the human state flips. Called from the player tick on both sides. */
    public static void refreshIfChanged(Player player) {
        boolean client = player.m_9236_().m_5776_();
        if (!client) {
            // The server owns the state: push both flags to every tracking client (only sent when they change).
            player.m_20088_().m_135381_(DATA_HUMAN, computeHuman(player));
            player.m_20088_().m_135381_(DATA_GOODBYE, computeGoodbye(player));
            player.m_20088_().m_135381_(DATA_HOLLE, computeHolle(player));
            player.m_20088_().m_135381_(DATA_BLADE, computeBlade(player));
            player.m_20088_().m_135381_(DATA_COCOON, computeCocoon(player));
            player.m_20088_().m_135381_(DATA_OH_CHARGE, computeOhCharge(player));
            player.m_20088_().m_135381_(DATA_OH_SLAM, computeOhSlam(player));
        }
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
