package com.rpgtemplate.forge.skillbar;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.skill.SkillRegistry;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端直接槽位技能调度器：支持副战备栏（{@code Alt + 1~4}）与战术轮盘直接按槽位触发任意已解锁技能，
 * 完整支持点按、按住蓄力（{@code onHoldTick}）与松开结算（{@code onRelease}）。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class ServerSkillInvoker {
    private static final Map<String, Skill> MIRROR = new ConcurrentHashMap<>();
    private static final Map<UUID, ActiveHold> HOLDS = new ConcurrentHashMap<>();

    private record ActiveHold(int slot, String skillId, long startTick, HeldState heldState) {
    }

    private ServerSkillInvoker() {
    }

    /** 供本模组扩展技能同时注册到镜像表，确保零反射开销直接命中。 */
    public static void registerMirror(String id, Skill skill) {
        if (id != null && skill != null) {
            MIRROR.put(id, skill);
        }
    }

    public static void pressSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot > 15 || !SystemDataManager.available(player)) {
            return;
        }
        // 0..11 槽位直接走原生 SkillManager.onInput，完整继承夺舍拦截、技能借用、蓄力、冷却同步与提示反馈
        if (slot < 12) {
            SkillManager.onInput(player, slot, true);
            return;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        if (!data.isActivated()) {
            return;
        }
        TemplateInstance inst = data.loadedTemplate().orElse(null);
        if (inst == null) {
            return;
        }
        TemplateDefinition def = TemplateRegistry.get().find(inst.templateId()).orElse(null);
        if (def == null) {
            return;
        }
        List<SkillEntry> skills = TemplateRegistry.get().effectiveSkills(def.id());
        if (slot >= skills.size()) {
            return;
        }
        SkillEntry entry = skills.get(slot);
        if (!entry.unlockedAt(inst.level())) {
            return;
        }
        long now = player.m_9236_().m_46467_();
        if (now < data.value("sealed_until", 0.0)) {
            return;
        }
        Skill impl = resolveSkill(entry.id());
        if (impl == null) {
            return;
        }
        SkillContext ctx = SkillContext.create(player, slot);
        if (ctx == null) {
            return;
        }
        if (now < data.value("cd:" + entry.id(), 0.0) && !impl.bypassCooldown(ctx)) {
            return;
        }
        HeldState held = createHeldState(slot, entry.id(), now);
        impl.onPress(ctx, held);
        if (impl.holdable()) {
            HOLDS.put(player.m_20148_(), new ActiveHold(slot, entry.id(), now, held));
        }
    }

    public static void releaseSlot(ServerPlayer player, int slot) {
        if (player == null) {
            return;
        }
        if (slot >= 0 && slot < 12) {
            SkillManager.onInput(player, slot, false);
            return;
        }
        ActiveHold hold = HOLDS.remove(player.m_20148_());
        if (hold == null || (slot >= 0 && hold.slot() != slot)) {
            return;
        }
        Skill impl = resolveSkill(hold.skillId());
        if (impl == null) {
            return;
        }
        SkillContext ctx = SkillContext.create(player, hold.slot());
        if (ctx != null) {
            impl.onRelease(ctx, hold.heldState());
        }
    }

    @SubscribeEvent
    public static void onServerPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ActiveHold hold = HOLDS.get(player.m_20148_());
        if (hold == null) {
            return;
        }
        if (!player.m_6084_()) {
            HOLDS.remove(player.m_20148_());
            return;
        }
        Skill impl = resolveSkill(hold.skillId());
        SkillContext ctx = SkillContext.create(player, hold.slot());
        if (impl == null || ctx == null) {
            HOLDS.remove(player.m_20148_());
            return;
        }
        impl.onHoldTick(ctx, hold.heldState());
        if (player.m_9236_().m_46467_() - hold.startTick() > 200L) {
            HOLDS.remove(player.m_20148_());
            impl.onRelease(ctx, hold.heldState());
        }
    }

    @SuppressWarnings("unchecked")
    private static Skill resolveSkill(String id) {
        Skill direct = MIRROR.get(id);
        if (direct != null) {
            return direct;
        }
        try {
            for (Method m : SkillRegistry.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    m.setAccessible(true);
                    Object res = m.invoke(null, id);
                    if (res instanceof Skill s) {
                        MIRROR.put(id, s);
                        return s;
                    }
                    if (res instanceof Optional<?> opt && opt.orElse(null) instanceof Skill s) {
                        MIRROR.put(id, s);
                        return s;
                    }
                }
            }
            for (Field f : SkillRegistry.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object mapObj = f.get(null);
                    if (mapObj instanceof Map<?, ?> map && map.get(id) instanceof Skill s) {
                        MIRROR.put(id, s);
                        return s;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static HeldState createHeldState(int slot, String skillId, long now) {
        try {
            for (Constructor<?> c : HeldState.class.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 1 && p[0] == long.class) {
                    return (HeldState) c.newInstance(now);
                }
                if (p.length == 2 && p[0] == int.class && p[1] == long.class) {
                    return (HeldState) c.newInstance(slot, now);
                }
                if (p.length == 2 && p[0] == String.class && p[1] == long.class) {
                    return (HeldState) c.newInstance(skillId, now);
                }
                if (p.length == 3 && p[0] == int.class && p[1] == String.class && p[2] == long.class) {
                    return (HeldState) c.newInstance(slot, skillId, now);
                }
                if (p.length == 0) {
                    return (HeldState) c.newInstance();
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
