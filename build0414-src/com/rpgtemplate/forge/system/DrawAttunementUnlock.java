package com.rpgtemplate.forge.system;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 跨次元抽取初步同调解锁 & 角色癖好契合加速支持：
 * <ul>
 *   <li>当最新抽取的模板达到初步同调等级（{@code min(maxLevel, 10)}）时，提前解除跨次元抽取锁定，
 *       避免低等级玩家抽到 EX / 重症高阶模板后长期卡死卡池（满级仍作为进阶与同源抽取的硬性门槛）。</li>
 *   <li>当玩家顺应角色模板「癖好（Quirks）」行事时，授予额外模板契合经验。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class DrawAttunementUnlock {
    public static final int ATTUNE_LEVEL = 10;
    private static final long QUIRK_MSG_INTERVAL = 80L;
    private static final Map<UUID, Long> LAST_QUIRK_MSG = new ConcurrentHashMap<>();
    private static final Method SET_DRAW_UNLOCKED = findSetDrawUnlocked();

    private DrawAttunementUnlock() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.m_9236_().m_46467_() % 20L != 0L || !SystemDataManager.available(player)) {
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated() || data.isDrawUnlocked()) {
            return;
        }
        TemplateInstance gate = findGateTemplate(data);
        if (gate == null) {
            return;
        }
        TemplateDefinition def = TemplateRegistry.get().find(gate.templateId()).orElse(null);
        if (def == null || def.levelless()) {
            return;
        }
        int targetLv = Math.min(def.maxLevel(), ATTUNE_LEVEL);
        if (gate.level() < targetLv) {
            return;
        }
        unlockDraws(data);
        if (data.isDrawUnlocked()) {
            SystemDataManager.markDirty((Player) player);
            SystemDataManager.sync(player);
            Msg.chat(player, Component.m_237110_("message.rpgtemplate.draw.attuned",
                def.displayName(), targetLv).m_130940_(ChatFormatting.GOLD));
        }
    }

    /** 顺应角色「癖好」时奖励额外模板经验（配合击杀或特定行为触发）。 */
    public static void grantQuirkXp(ServerPlayer player, PlayerSystemData data, double baseXp, String quirkName) {
        if (player == null || data == null || !data.isActivated()) {
            return;
        }
        TemplateInstance inst = data.loadedTemplate().orElse(null);
        if (inst == null) {
            return;
        }
        TemplateDefinition def = TemplateRegistry.get().find(inst.templateId()).orElse(null);
        if (def == null || def.levelless() || inst.level() >= def.maxLevel()) {
            return;
        }
        double bonus = Math.max(1.0, baseXp + data.level() * 1.2);
        inst.setXp(inst.xp() + bonus);
        SystemDataManager.markDirty((Player) player);
        long now = player.m_9236_().m_46467_();
        UUID id = player.m_20148_();
        if (now - LAST_QUIRK_MSG.getOrDefault(id, Long.MIN_VALUE / 2) >= QUIRK_MSG_INTERVAL) {
            LAST_QUIRK_MSG.put(id, now);
            Msg.actionBar(player, Component.m_237110_("message.rpgtemplate.quirk.xp_bonus",
                quirkName, String.format(java.util.Locale.ROOT, "%.0f", bonus))
                .m_130940_(ChatFormatting.AQUA));
        }
    }

    private static TemplateInstance findGateTemplate(PlayerSystemData data) {
        List<TemplateInstance> owned = data.templates();
        for (int i = owned.size() - 1; i >= 0; i--) {
            TemplateInstance inst = owned.get(i);
            TemplateDefinition def = TemplateRegistry.get().find(inst.templateId()).orElse(null);
            if (def != null && !def.levelless() && inst.level() < def.maxLevel()) {
                return inst;
            }
        }
        return null;
    }

    private static void unlockDraws(PlayerSystemData data) {
        try {
            data.setNewestTemplateId(null);
            if (!data.isDrawUnlocked()) {
                data.setNewestTemplateId("");
            }
        } catch (RuntimeException ignored) {
            data.setNewestTemplateId("");
        }
        if (!data.isDrawUnlocked() && SET_DRAW_UNLOCKED != null) {
            try {
                SET_DRAW_UNLOCKED.invoke(data, Boolean.TRUE);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static Method findSetDrawUnlocked() {
        try {
            return PlayerSystemData.class.getMethod("setDrawUnlocked", boolean.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
