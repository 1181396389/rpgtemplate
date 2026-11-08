package com.rpgtemplate.forge.skillbar.client;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.skill.SkillFormula;
import com.rpgtemplate.core.stats.NumberFormatter;
import com.rpgtemplate.core.template.CostType;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.ClientKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import com.rpgtemplate.forge.client.gui.UiTheme;
import com.rpgtemplate.forge.skillbar.TacticalNet;
import com.rpgtemplate.forge.skillbar.TacticalSkillMsg;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端战术技能栏增强与 Cronus 三段跳/Shift 滞空输入控制：
 * <ol>
 *   <li><b>免翻页副栏直放（Alt + 技能键 1~4）</b>：按住 Alt 按下技能键直接释放副战备层技能（支持按住蓄力与松开结算）。</li>
 *   <li><b>次元战术轮盘（长按技能翻页键 ≥ 180ms）</b>：移动准星指向任意技能槽位，松开即刻释放；轻点仍为传统翻页。</li>
 *   <li><b>Cronus 三段跳与主动 Shift 滞空</b>：客户端即时物理响应 + 服务端同步，零回弹延迟。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TacticalSkillClient {
    private static final long WHEEL_HOLD_MS = 180L;
    private static final int MAX_AIR_JUMPS = 2;
    private static final int MAX_HOVER_TICKS = 24; // 1.2 秒主动滞空

    private static KeyMapping[] cachedSkillKeys;
    private static KeyMapping cachedPageKey;

    private static int activeAltKeyIndex = -1;
    private static int activeAltSlot = -1;

    private static boolean pageKeyDown;
    private static long pageKeyPressMs;
    private static boolean wheelActive;
    private static double wheelAimX;
    private static double wheelAimY;
    private static float lastYaw;
    private static float lastPitch;

    // Cronus 客户端空中三段跳与 Shift 滞空状态
    private static boolean wasJumpDown;
    private static int airJumpsUsed;
    private static int hoverTicksUsed;
    private static boolean clientHovering;

    private TacticalSkillClient() {
    }

    private static void ensureKeysResolved(Minecraft mc) {
        if (cachedSkillKeys != null) {
            return;
        }
        if (ClientKeys.SKILLS != null && ClientKeys.SKILLS.length >= 4 && ClientKeys.PAGE != null) {
            cachedSkillKeys = new KeyMapping[]{
                ClientKeys.SKILLS[0], ClientKeys.SKILLS[1], ClientKeys.SKILLS[2], ClientKeys.SKILLS[3]
            };
            cachedPageKey = ClientKeys.PAGE;
            return;
        }
        if (mc.f_91066_ == null || mc.f_91066_.f_92059_ == null) {
            return;
        }
        KeyMapping[] found = new KeyMapping[4];
        KeyMapping page = null;
        for (KeyMapping km : mc.f_91066_.f_92059_) {
            if (km == null) {
                continue;
            }
            String name = km.m_90860_();
            switch (name) {
                case "key.rpgtemplate.skill_1" -> found[0] = km;
                case "key.rpgtemplate.skill_2" -> found[1] = km;
                case "key.rpgtemplate.skill_3" -> found[2] = km;
                case "key.rpgtemplate.skill_4" -> found[3] = km;
                case "key.rpgtemplate.skill_page" -> page = km;
                default -> {
                }
            }
        }
        cachedSkillKeys = found;
        cachedPageKey = page;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || mc.f_91080_ != null) {
            return;
        }
        ensureKeysResolved(mc);
        if (cachedSkillKeys == null) {
            return;
        }
        int key = event.getKey();
        int scan = event.getScanCode();
        int action = event.getAction(); // 0=RELEASE, 1=PRESS, 2=REPEAT
        boolean altDown = (event.getModifiers() & 4) != 0 || Screen.m_96639_();

        // 1. Alt + 技能键 1~4 免翻页直放副战备层技能
        for (int i = 0; i < 4; i++) {
            KeyMapping km = cachedSkillKeys[i];
            if (km == null || !km.m_90832_(key, scan)) {
                continue;
            }
            if (action == 1 && altDown) {
                List<SkillEntry> skills = currentSkills();
                if (skills.size() > 4) {
                    int pages = (skills.size() + 3) / 4;
                    int subPage = (ClientKeys.page() + 1) % pages;
                    int targetSlot = subPage * 4 + i;
                    if (targetSlot < skills.size()) {
                        suppressKey(km);
                        activeAltKeyIndex = i;
                        activeAltSlot = targetSlot;
                        TacticalNet.CHANNEL.sendToServer(
                            new TacticalSkillMsg(TacticalSkillMsg.KIND_SKILL_PRESS, targetSlot));
                        return;
                    }
                }
            } else if (action == 0 && activeAltKeyIndex == i) {
                suppressKey(km);
                int slot = activeAltSlot;
                activeAltKeyIndex = -1;
                activeAltSlot = -1;
                if (slot >= 0) {
                    TacticalNet.CHANNEL.sendToServer(
                        new TacticalSkillMsg(TacticalSkillMsg.KIND_SKILL_RELEASE, slot));
                }
                return;
            }
        }

        // 2. 长按技能翻页键呼出战术轮盘
        if (cachedPageKey != null && cachedPageKey.m_90832_(key, scan)) {
            if (action == 1 && !pageKeyDown) {
                pageKeyDown = true;
                pageKeyPressMs = System.currentTimeMillis();
                wheelActive = false;
                wheelAimX = 0.0;
                wheelAimY = 0.0;
                lastYaw = mc.f_91074_.m_146908_();
                lastPitch = mc.f_91074_.m_146909_();
            } else if (action == 0 && pageKeyDown) {
                pageKeyDown = false;
                if (wheelActive) {
                    int chosen = selectedWheelSlot(currentSkills().size());
                    wheelActive = false;
                    if (chosen >= 0) {
                        TacticalNet.CHANNEL.sendToServer(
                            new TacticalSkillMsg(TacticalSkillMsg.KIND_SKILL_PRESS, chosen));
                        TacticalNet.CHANNEL.sendToServer(
                            new TacticalSkillMsg(TacticalSkillMsg.KIND_SKILL_RELEASE, chosen));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer player = mc.f_91074_;
        if (player == null || mc.f_91073_ == null) {
            wheelActive = false;
            pageKeyDown = false;
            clientHovering = false;
            return;
        }
        ensureKeysResolved(mc);

        // 维持拦截 Alt 按住期间的原版技能键触发
        if (activeAltKeyIndex >= 0 && cachedSkillKeys != null && cachedSkillKeys[activeAltKeyIndex] != null) {
            suppressKey(cachedSkillKeys[activeAltKeyIndex]);
        }

        // 战术轮盘激活与视角偏移追踪
        if (pageKeyDown && mc.f_91080_ == null) {
            List<SkillEntry> skills = currentSkills();
            if (skills.size() > 1 && System.currentTimeMillis() - pageKeyPressMs >= WHEEL_HOLD_MS) {
                if (!wheelActive) {
                    wheelActive = true;
                    // 打开轮盘时抵消刚才轻点翻页的影响：不吃掉玩家正常习惯，视角锁定转为轮盘指针
                    lastYaw = player.m_146908_();
                    lastPitch = player.m_146909_();
                }
                float dyaw = player.m_146908_() - lastYaw;
                float dpitch = player.m_146909_() - lastPitch;
                wheelAimX = Math.max(-90.0, Math.min(90.0, wheelAimX + dyaw * 3.2));
                wheelAimY = Math.max(-90.0, Math.min(90.0, wheelAimY + dpitch * 3.2));
                player.m_146922_(lastYaw);
                player.m_146926_(lastPitch);
            }
        } else {
            wheelActive = false;
        }

        // Cronus 三段跳与主动 Shift 滞空客户端控制
        tickCronusAirMobility(mc, player);
    }

    private static void tickCronusAirMobility(Minecraft mc, LocalPlayer player) {
        PlayerSystemData data = ClientSystemData.get();
        boolean cronusTransformed = data != null && data.isActivated()
            && CronusKeys.TEMPLATE_ID.equals(data.loadedTemplateId())
            && data.value(CronusKeys.KEY_TRANSFORMED, 0.0) > 0.5;

        if (!cronusTransformed || mc.f_91080_ != null) {
            if (clientHovering) {
                clientHovering = false;
                TacticalNet.CHANNEL.sendToServer(
                    new TacticalSkillMsg(TacticalSkillMsg.KIND_AIR_ACTION, TacticalSkillMsg.AIR_HOVER_STOP));
            }
            wasJumpDown = false;
            return;
        }

        if (player.m_20096_() || player.m_20069_()) {
            airJumpsUsed = 0;
            hoverTicksUsed = 0;
            if (clientHovering) {
                clientHovering = false;
                TacticalNet.CHANNEL.sendToServer(
                    new TacticalSkillMsg(TacticalSkillMsg.KIND_AIR_ACTION, TacticalSkillMsg.AIR_HOVER_STOP));
            }
        }

        boolean jumpDown = mc.f_91066_.f_92089_.m_90857_();
        if (jumpDown && !wasJumpDown && !player.m_20096_() && !player.m_20069_() && airJumpsUsed < MAX_AIR_JUMPS) {
            airJumpsUsed++;
            hoverTicksUsed = Math.max(0, hoverTicksUsed - 8); // 再次起跳微回滞空余量
            boolean speedUp = data.value(CronusKeys.KEY_ITEM_SPEED_UNTIL, 0.0) > mc.f_91073_.m_46467_();
            boolean jumpUp = data.value(CronusKeys.KEY_ITEM_JUMP_UNTIL, 0.0) > mc.f_91073_.m_46467_();
            Vec3 cur = player.m_20184_();
            Vec3 look = player.m_20154_();
            double boost = speedUp ? 0.28 : 0.16;
            double vy = jumpUp ? 0.62 : 0.50;
            player.m_20256_(new Vec3(cur.f_82479_ * 0.7 + look.f_82479_ * boost, vy,
                cur.f_82481_ * 0.7 + look.f_82481_ * boost));
            TacticalNet.CHANNEL.sendToServer(
                new TacticalSkillMsg(TacticalSkillMsg.KIND_AIR_ACTION, TacticalSkillMsg.AIR_JUMP));
        }
        wasJumpDown = jumpDown;

        // 空中按住 Shift 主动滞空（进行过至少一次跳跃或离地、且未超最大滞空时长）
        boolean shiftDown = mc.f_91066_.f_92090_.m_90857_();
        int maxHover = data.value(CronusKeys.KEY_ITEM_JUMP_UNTIL, 0.0) > mc.f_91073_.m_46467_()
            ? MAX_HOVER_TICKS + 16 : MAX_HOVER_TICKS;
        boolean wantHover = shiftDown && !player.m_20096_() && !player.m_20069_() && hoverTicksUsed < maxHover;
        if (wantHover) {
            hoverTicksUsed++;
            Vec3 v = player.m_20184_();
            player.m_20256_(new Vec3(v.f_82479_ * 0.82, 0.0, v.f_82481_ * 0.82));
            if (!clientHovering) {
                clientHovering = true;
                TacticalNet.CHANNEL.sendToServer(
                    new TacticalSkillMsg(TacticalSkillMsg.KIND_AIR_ACTION, TacticalSkillMsg.AIR_HOVER_START));
            }
        } else if (clientHovering) {
            clientHovering = false;
            TacticalNet.CHANNEL.sendToServer(
                new TacticalSkillMsg(TacticalSkillMsg.KIND_AIR_ACTION, TacticalSkillMsg.AIR_HOVER_STOP));
        }
    }

    @SubscribeEvent
    public static void onRenderWheel(RenderGuiOverlayEvent.Post event) {
        if (!wheelActive || event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        PlayerSystemData data = ClientSystemData.get();
        if (data == null || !data.isActivated() || mc.f_91073_ == null) {
            return;
        }
        TemplateInstance inst = data.loadedTemplate().orElse(null);
        TemplateDefinition def = inst == null ? null : TemplateRegistry.client().find(inst.templateId()).orElse(null);
        if (inst == null || def == null) {
            return;
        }
        List<SkillEntry> skills = TemplateRegistry.client().effectiveSkills(def.id());
        if (skills.isEmpty()) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.f_91062_;
        int sw = event.getWindow().m_85445_();
        int sh = event.getWindow().m_85446_();
        int cx = sw / 2;
        int cy = sh / 2;
        long gameTick = mc.f_91073_.m_46467_();
        int selected = selectedWheelSlot(skills.size());

        // 半透明中心暗幕与指针十字
        g.m_280509_(cx - 118, cy - 102, cx + 118, cy + 102, UiTheme.alpha(0.38f, UiTheme.E_SHADOW));
        UiTheme.border(g, cx - 22, cy - 12, 44, 24, selected >= 0 ? UiTheme.E_JADE : UiTheme.E_EDGE);
        String centerLabel = selected >= 0 ? "#" + (selected + 1) : "\u00d7";
        UiTheme.text(g, font, centerLabel, cx - font.m_92895_(centerLabel) / 2, cy - 4,
            selected >= 0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_MUTED);

        int radius = 72;
        int cardW = 76;
        int cardH = 26;
        for (int i = 0; i < skills.size(); i++) {
            double ang = -Math.PI / 2.0 + (Math.PI * 2.0 * i) / skills.size();
            int x = cx + (int) Math.round(Math.cos(ang) * radius) - cardW / 2;
            int y = cy + (int) Math.round(Math.sin(ang) * (radius * 0.85)) - cardH / 2;
            SkillEntry sk = skills.get(i);
            boolean unlocked = sk.unlockedAt(inst.level());
            double cd = Math.max(0.0, data.value("cd:" + sk.id(), 0.0) - (double) gameTick);
            double cost = SkillFormula.cost(sk.baseCost(), def.quality());
            boolean afford = sk.costType() == CostType.MP ? data.mp() >= cost : data.sp() >= cost;
            boolean hov = i == selected;

            int accent = !unlocked ? UiTheme.E_FAINT
                : (!afford ? UiTheme.E_DEEP_RED : (cd > 0.0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_JADE));
            UiTheme.buttonSurface(g, x, y, cardW, cardH, hov, hov, accent);
            if (hov) {
                UiTheme.marching(g, x, y, cardW, cardH, accent, 1.2f);
            }
            UiTheme.text(g, font, UiTheme.fit(font, (i + 1) + "." + sk.name(), cardW - 8),
                x + 4, y + 4, unlocked ? UiTheme.E_INK : UiTheme.E_FAINT);
            String sub = !unlocked ? "Lv." + sk.unlockLevel()
                : (cd > 0.0 ? String.format(Locale.ROOT, "%.1fs", cd / 20.0)
                : NumberFormatter.format(cost) + (sk.costType() == CostType.MP ? "MP" : "SP"));
            UiTheme.text(g, font, sub, x + 4, y + 15, accent);
        }

        // 准星偏移指示点
        int dotX = cx + (int) Math.round(wheelAimX * 0.45);
        int dotY = cy + (int) Math.round(wheelAimY * 0.45);
        g.m_280509_(dotX - 2, dotY - 2, dotX + 2, dotY + 2, UiTheme.alpha(0.95f, UiTheme.E_GOLD_TEXT));
        String wheelHint = Component.m_237115_("hud.rpgtemplate.ui.wheel_hint").getString();
        UiTheme.text(g, font, wheelHint, cx - font.m_92895_(wheelHint) / 2, cy + 92, UiTheme.E_MUTED);
    }

    private static int selectedWheelSlot(int count) {
        if (count <= 0) {
            return -1;
        }
        double dist = Math.hypot(wheelAimX, wheelAimY);
        if (dist < 14.0) {
            return -1;
        }
        double ang = Math.atan2(wheelAimY, wheelAimX) + Math.PI / 2.0;
        if (ang < 0.0) {
            ang += Math.PI * 2.0;
        }
        double sector = (Math.PI * 2.0) / count;
        return ((int) Math.floor((ang + sector * 0.5) / sector)) % count;
    }

    private static List<SkillEntry> currentSkills() {
        PlayerSystemData data = ClientSystemData.get();
        if (data == null || !data.isActivated()) {
            return List.of();
        }
        TemplateInstance inst = data.loadedTemplate().orElse(null);
        if (inst == null) {
            return List.of();
        }
        TemplateDefinition def = TemplateRegistry.client().find(inst.templateId()).orElse(null);
        return def == null ? List.of() : TemplateRegistry.client().effectiveSkills(def.id());
    }

    private static void suppressKey(KeyMapping km) {
        km.m_7249_(false);
        while (km.m_90859_()) {
            // 吞掉点击计数，防止主页面技能同时触发
        }
    }
}
