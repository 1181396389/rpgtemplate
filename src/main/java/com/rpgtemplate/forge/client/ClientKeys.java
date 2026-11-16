package com.rpgtemplate.forge.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.client.gui.SystemPanelScreen;
import com.rpgtemplate.forge.network.PacketDistributor;
import com.rpgtemplate.forge.network.SkillInputPayload;
import com.rpgtemplate.forge.possession.client.ClientPossession;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class ClientKeys {
    public static final String CATEGORY = "key.categories.rpgtemplate";
    public static final int PER_PAGE = 4;
    public static final int PAGES = 3;
    private static final int[] DEFAULTS = new int[]{90, 88, 67, 86};
    public static final KeyMapping[] SKILLS = new KeyMapping[4];
    public static final KeyMapping PANEL = new KeyMapping("key.rpgtemplate.panel", KeyConflictContext.IN_GAME, Type.KEYSYM, 75, "key.categories.rpgtemplate");
    public static final KeyMapping PAGE = new KeyMapping(
        "key.rpgtemplate.skill_page", KeyConflictContext.IN_GAME, Type.KEYSYM, 82, "key.categories.rpgtemplate"
    );
    private static final boolean[] DOWN = new boolean[4];
    private static final int[] HELD_SLOT = new int[4];
    private static int page;
    private static int borrowedPage;
    private static long pageFlipTick = Long.MIN_VALUE;

    private ClientKeys() {
    }

    public static void register(RegisterKeyMappingsEvent var0) {
        for (KeyMapping var4 : SKILLS) {
            var0.register(var4);
        }

        var0.register(PAGE);
        var0.register(PANEL);
    }

    public static void tick() {
        Minecraft var0 = Minecraft.getInstance();
        boolean var1 = var0.player != null && var0.getConnection() != null && (ClientSystemData.get().isActivated() || ClientPossession.borrowing());

        while (PAGE.consumeClick()) {
            if (var1 && var0.screen == null) {
                togglePage(var0);
            }
        }

        for (int var2 = 0; var2 < SKILLS.length; var2++) {
            boolean var3 = var1 && var0.screen == null && SKILLS[var2].isDown();
            if (var3 != DOWN[var2]) {
                DOWN[var2] = var3;
                if (var3) {
                    HELD_SLOT[var2] = page() * 4 + var2;
                }

                int var4 = HELD_SLOT[var2];
                if (var0.getConnection() != null) {
                    PacketDistributor.sendToServer(new SkillInputPayload(var4, var3));
                }

                if (!ClientPossession.borrowing() && !var3 && var4 >= 4 && page != 0) {
                }
            }

            while (SKILLS[var2].consumeClick()) {
            }
        }

        while (PANEL.consumeClick()) {
            if (var0.player != null && var0.screen == null && ClientSystemData.get().isActivated()) {
                var0.setScreen(new SystemPanelScreen());
            } else if (var0.player != null && var0.screen == null) {
                var0.player.displayClientMessage(Component.translatable("gui.rpgtemplate.not_activated").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    private static void togglePage(Minecraft var0) {
        int var1 = skillCount();
        if (var1 <= 4) {
            setActivePage(0);
            var0.player.displayClientMessage(Component.translatable("hud.rpgtemplate.skill_page_single", new Object[]{4}).withStyle(ChatFormatting.GRAY), true);
        } else {
            setActivePage((page() + 1) % pageCount());
            pageFlipTick = var0.level != null ? var0.level.getGameTime() : 0L;
            var0.player.playSound((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, page() == 0 ? 1.3F : 1.7F);
            var0.player
                .displayClientMessage(
                    Component.translatable(
                            "hud.rpgtemplate.skill_page_switched",
                            new Object[]{pageMark(page()), PAGE.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT)}
                        )
                        .withStyle(page() == 0 ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE),
                    true
                );
        }
    }

    private static boolean anyDown() {
        for (boolean var3 : DOWN) {
            if (var3) {
                return true;
            }
        }

        return false;
    }

    public static int skillCount() {
        if (ClientPossession.borrowing()) {
            return ClientPossession.borrowedCount();
        } else {
            PlayerSystemData var0 = ClientSystemData.get();
            TemplateRegistry var1 = TemplateRegistry.client();
            TemplateDefinition var2 = var0.loadedTemplate().flatMap(var1x -> var1.find(var1x.templateId())).orElse(null);
            List var3 = var2 == null ? List.of() : var1.effectiveSkills(var2.id());
            return var3.size();
        }
    }

    public static int pageCount() {
        return Math.max(1, Math.min(3, (skillCount() + 4 - 1) / 4));
    }

    public static int page() {
        int var0 = ClientPossession.borrowing() ? borrowedPage : page;
        return Math.min(var0, pageCount() - 1);
    }

    private static void setActivePage(int var0) {
        if (ClientPossession.borrowing()) {
            borrowedPage = var0;
        } else {
            page = var0;
        }
    }

    public static void resetBorrowedPage() {
        borrowedPage = 0;
    }

    public static boolean multiPage() {
        return skillCount() > 4;
    }

    public static long ticksSincePageFlip(long var0) {
        return pageFlipTick == Long.MIN_VALUE ? Long.MAX_VALUE : var0 - pageFlipTick;
    }

    public static void reset() {
        for (int var0 = 0; var0 < DOWN.length; HELD_SLOT[var0] = var0++) {
            DOWN[var0] = false;
        }

        page = 0;
        borrowedPage = 0;
        pageFlipTick = Long.MIN_VALUE;
    }

    public static String pageMark(int var0) {
        return var0 <= 0 ? "①" : (var0 == 1 ? "②" : "③");
    }

    public static String pageKeyLabel() {
        return PAGE.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
    }

    public static String physicalKeyLabel(int var0) {
        return var0 >= 0 && var0 < SKILLS.length ? SKILLS[var0].getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT) : "?";
    }

    public static String keyLabel(int var0) {
        if (var0 >= 0 && var0 < 12) {
            int var1 = var0 / 4;
            String var2 = physicalKeyLabel(var0 % 4);
            return var1 == 0 ? var2 : pageMark(var1) + var2;
        } else {
            return "?";
        }
    }

    static {
        for (int var0 = 0; var0 < SKILLS.length; HELD_SLOT[var0] = var0++) {
            SKILLS[var0] = new KeyMapping(
                "key.rpgtemplate.skill_" + (var0 + 1), KeyConflictContext.IN_GAME, Type.KEYSYM, DEFAULTS[var0], "key.categories.rpgtemplate"
            );
        }
    }
}
