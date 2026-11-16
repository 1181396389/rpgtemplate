package com.rpgtemplate.forge.system;
import com.rpgtemplate.forge.util.ItemNbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.service.AdvanceService;
import com.rpgtemplate.core.service.DrawService;
import com.rpgtemplate.core.service.LevelingService;
import com.rpgtemplate.core.service.LineageDrawService;
import com.rpgtemplate.core.service.TemplateLoadService;
import com.rpgtemplate.core.stats.NumberFormatter;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatFormula;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.LineageReward;
import com.rpgtemplate.core.template.Quality;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.api.event.LevelUpEvent;
import com.rpgtemplate.forge.api.event.SystemActivatedEvent;
import com.rpgtemplate.forge.api.event.TemplateDrawEvent;
import com.rpgtemplate.forge.api.event.TemplateLoadEvent;
import com.rpgtemplate.forge.api.event.XpGainEvent;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.skill.SkillManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

public final class SystemActions {
    private static final Map<UUID, SystemActions.PendingLoad> PENDING_LOADS = new ConcurrentHashMap<>();
    public static final String KEY_SWITCH_COOLDOWN = "switch_cooldown_until";

    private SystemActions() {
    }

    private static TemplateRegistry reg() {
        return TemplateRegistry.get();
    }

    private static CoreConfig cfg() {
        return CoreConfig.get();
    }

    public static void activate(ServerPlayer var0) {
        PlayerSystemData var1 = SystemDataManager.get(var0);
        var1.activate();
        if (reg().find("rpgtemplate:traveler").isEmpty()) {
            RpgTemplateMod.LOGGER.warn("[RPG转生模板] 找不到基础模板 {}，请检查数据包", "rpgtemplate:traveler");
        }

        StatCalculator.refill(var1, reg(), cfg());
        PassiveApplier.refresh(var0);
        SystemDataManager.sync(var0);
        Effects.rainbow(var0, 0, 16777215);
        Msg.chat(var0, Msg.system(Msg.tr("activate.success").withStyle(ChatFormatting.GOLD)));
        reg().find("rpgtemplate:traveler").ifPresent(var1x -> Msg.chat(var0, Msg.system(Msg.tr("activate.base_template", Msg.template(var1x)))));
        NeoForge.EVENT_BUS.post(new SystemActivatedEvent(var0));
    }

    public static void grantXp(ServerPlayer var0, double var1, ResourceLocation var3) {
        PlayerSystemData var4 = SystemDataManager.get(var0);
        if (var4.isActivated() && !(var1 <= 0.0)) {
            XpGainEvent var5 = new XpGainEvent(var0, var1, var3);
            NeoForge.EVENT_BUS.post(var5);
            if (!var5.isCanceled() && !(var5.getAmount() <= 0.0)) {
                int var6 = var4.level();
                LevelingService.Result var7 = LevelingService.addXp(var4, var5.getAmount(), reg(), cfg());
                if (var7.playerLevelsGained > 0) {
                    var0.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
                    Msg.chat(var0, Msg.system(Msg.tr("level_up", var4.level(), var7.playerLevelsGained).withStyle(ChatFormatting.YELLOW)));
                    NeoForge.EVENT_BUS.post(new LevelUpEvent(var0, var6, var4.level()));
                }

                if (var7.templateLevelsGained > 0) {
                    var4.loadedTemplate()
                        .ifPresent(
                            var1x -> reg()
                                    .find(var1x.templateId())
                                    .ifPresent(
                                        var2 -> Msg.chat(var0, Msg.system(Msg.tr("template_level_up", Msg.template(var2), var1x.level(), var2.maxLevel())))
                                    )
                        );
                }

                if (var7.templateReachedMax) {
                    var4.loadedTemplate()
                        .ifPresent(
                            var2 -> reg()
                                    .find(var2.templateId())
                                    .ifPresent(
                                        var2x -> {
                                            Msg.chat(var0, Msg.system(Msg.tr("template_max", Msg.template(var2x)).withStyle(ChatFormatting.GOLD)));
                                            lineageHint(var0, var4, var2x);
                                            if (AdvanceService.check(var4, var2x.id(), reg()) == AdvanceService.Status.SUCCESS) {
                                                reg()
                                                    .advancementOf(var2x.id())
                                                    .ifPresent(
                                                        var1xxx -> Msg.chat(
                                                                var0,
                                                                Msg.system(
                                                                    Msg.tr("advance.available", Msg.template(var1xxx)).withStyle(ChatFormatting.LIGHT_PURPLE)
                                                                )
                                                            )
                                                    );
                                            }
                                        }
                                    )
                        );
                }

                if (var7.drawJustUnlocked || var7.playerLevelsGained > 0 && var4.isDrawUnlocked()) {
                    if (var4.pendingDraws() > 0) {
                        Msg.chat(var0, Msg.system(Msg.tr("draw.available", var4.pendingDraws()).withStyle(ChatFormatting.AQUA)));
                    }
                } else if (var7.playerLevelsGained > 0) {
                    Msg.chat(var0, Msg.system(Msg.tr("draw.queued", var4.pendingDraws()).withStyle(ChatFormatting.GRAY)));
                }

                if (var7.playerLevelsGained <= 0 && var7.templateLevelsGained <= 0) {
                    SystemDataManager.markDirty(var0);
                } else {
                    PassiveApplier.refresh(var0);
                    SystemDataManager.sync(var0);
                }
            }
        }
    }

    public static boolean draw(ServerPlayer var0) {
        PlayerSystemData var1 = SystemDataManager.get(var0);
        DrawService.Result var2 = DrawService.draw(var1, reg(), cfg(), PassiveApplier.drawLuck(var1), () -> var0.getRandom().nextDouble());
        switch (var2.status) {
            case NOT_ACTIVATED:
                Msg.chat(var0, Msg.system(Msg.tr("not_activated").withStyle(ChatFormatting.RED)));
                break;
            case NO_PENDING:
                Msg.chat(var0, Msg.system(Msg.tr("draw.none").withStyle(ChatFormatting.GRAY)));
                break;
            case LOCKED:
                MutableComponent var3 = var1.findTemplate(var1.newestTemplateId())
                    .flatMap(var0x -> reg().find(var0x.templateId()))
                    .map(Msg::template)
                    .orElse(Component.literal(String.valueOf(var1.newestTemplateId())));
                Msg.chat(var0, Msg.system(Msg.tr("draw.locked", var3, var1.pendingDraws()).withStyle(ChatFormatting.GRAY)));
                break;
            case POOL_EMPTY:
                Msg.chat(var0, Msg.system(Msg.tr("draw.pool_empty", NumberFormatter.format(var2.compensationXp)).withStyle(ChatFormatting.GRAY)));
                SystemDataManager.sync(var0);
                break;
            case SUCCESS:
                TemplateDefinition var4 = var2.template;
                int var5 = var4.quality().color() & 16777215;
                Effects.rainbow(var0, 1, var5);
                float var6 = var4.quality().ordinal() >= Quality.SSR.ordinal() ? 0.8F : 1.2F;
                var0.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, var6);
                Msg.chat(var0, Msg.system(Msg.tr("draw.success", Msg.template(var4), var4.source()).withStyle(ChatFormatting.WHITE)));
                NeoForge.EVENT_BUS.post(new TemplateDrawEvent(var0, var4));
                SystemDataManager.sync(var0);
                return true;
        }

        return false;
    }

    public static void requestLoad(ServerPlayer var0, String var1) {
        PlayerSystemData var2 = SystemDataManager.get(var0);
        if (!var2.isActivated()) {
            Msg.chat(var0, Msg.system(Msg.tr("not_activated").withStyle(ChatFormatting.RED)));
        } else if (var2.findTemplate(var1).isEmpty()) {
            Msg.chat(var0, Msg.system(Msg.tr("load.not_owned", var1).withStyle(ChatFormatting.RED)));
        } else if (var1.equals(var2.loadedTemplateId())) {
            Msg.chat(var0, Msg.system(Msg.tr("load.already").withStyle(ChatFormatting.GRAY)));
        } else {
            long var3 = var0.serverLevel().getGameTime();
            double var5 = var2.value("switch_cooldown_until", 0.0);
            if (!((double)var3 < var5) && !PENDING_LOADS.containsKey(var0.getUUID())) {
                TemplateLoadEvent var7 = new TemplateLoadEvent(var0, var2.loadedTemplateId(), var1);
                NeoForge.EVENT_BUS.post(var7);
                if (!var7.isCanceled()) {
                    PENDING_LOADS.put(var0.getUUID(), new SystemActions.PendingLoad(var1, var3 + 20L));
                    Msg.actionBar(var0, Msg.tr("load.syncing").withStyle(ChatFormatting.AQUA));
                }
            } else {
                Msg.actionBar(var0, Msg.tr("load.cooldown").withStyle(ChatFormatting.RED));
            }
        }
    }

    public static void tickPendingLoad(ServerPlayer var0) {
        SystemActions.PendingLoad var1 = PENDING_LOADS.get(var0.getUUID());
        if (var1 != null && var0.serverLevel().getGameTime() >= var1.readyAt()) {
            PENDING_LOADS.remove(var0.getUUID());
            PlayerSystemData var2 = SystemDataManager.get(var0);
            TemplateLoadService.Status var3 = TemplateLoadService.load(var2, var1.templateId(), reg(), cfg());
            if (var3 == TemplateLoadService.Status.SUCCESS) {
                onTemplateChanged(var0, var2);
                var2.setValue("switch_cooldown_until", (double)(var0.serverLevel().getGameTime() + 20L));
                PassiveApplier.refresh(var0);
                var0.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
                reg()
                    .find(var1.templateId())
                    .ifPresentOrElse(
                        var1x -> Msg.chat(var0, Msg.system(Msg.tr("load.success", Msg.template(var1x)))),
                        () -> Msg.chat(var0, Msg.system(Msg.tr("load.success", var1.templateId())))
                    );
                SystemDataManager.sync(var0);
            }
        }
    }

    public static void unload(ServerPlayer var0) {
        PlayerSystemData var1 = SystemDataManager.get(var0);
        if (var1.isActivated()) {
            if (var1.loadedTemplateId() == null) {
                Msg.chat(var0, Msg.system(Msg.tr("unload.none").withStyle(ChatFormatting.GRAY)));
            } else {
                TemplateLoadEvent var2 = new TemplateLoadEvent(var0, var1.loadedTemplateId(), null);
                NeoForge.EVENT_BUS.post(var2);
                if (!var2.isCanceled()) {
                    PENDING_LOADS.remove(var0.getUUID());
                    TemplateLoadService.unload(var1, reg(), cfg());
                    onTemplateChanged(var0, var1);
                    PassiveApplier.refresh(var0);
                    Msg.chat(var0, Msg.system(Msg.tr("unload.success")));
                    SystemDataManager.sync(var0);
                }
            }
        }
    }

    private static void onTemplateChanged(ServerPlayer var0, PlayerSystemData var1) {
        SkillManager.cancel(var0);
        var1.removeValue("hp_max_penalty");
        var1.removeValue("sealed_until");
        StatCalculator.normalizeCurrent(var1, reg(), cfg());
    }

    public static void clearPending(UUID var0) {
        PENDING_LOADS.remove(var0);
    }

    public static void advance(ServerPlayer var0, String var1) {
        PlayerSystemData var2 = SystemDataManager.get(var0);
        AdvanceService.Status var3 = AdvanceService.advance(var2, var1, reg(), cfg());
        switch (var3) {
            case SUCCESS:
                TemplateInstance var4 = var2.templates()
                    .stream()
                    .filter(var1x -> reg().rootOf(var1x.templateId()).equals(reg().rootOf(var1)))
                    .findFirst()
                    .orElse(null);
                if (var4 != null) {
                    reg()
                        .find(var4.templateId())
                        .ifPresent(
                            var2x -> Msg.chat(
                                    var0,
                                    Msg.system(
                                        Msg.tr("advance.success", Msg.template(var2x), var4.level(), var2x.maxLevel()).withStyle(ChatFormatting.LIGHT_PURPLE)
                                    )
                                )
                        );
                }

                SkillManager.cancel(var0);
                if (var2.pendingDraws() > 0 && !var2.isDrawUnlocked()) {
                    Msg.chat(var0, Msg.system(Msg.tr("draw.relocked", var2.pendingDraws()).withStyle(ChatFormatting.GRAY)));
                }

                Effects.rainbow(var0, 2, 16777215);
                var0.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
                PassiveApplier.refresh(var0);
                SystemDataManager.sync(var0);
                break;
            case NOT_OWNED:
                Msg.chat(var0, Msg.system(Msg.tr("load.not_owned", var1).withStyle(ChatFormatting.RED)));
                break;
            case NO_ADVANCEMENT:
                Msg.chat(var0, Msg.system(Msg.tr("advance.none").withStyle(ChatFormatting.GRAY)));
                break;
            case NOT_MAX_LEVEL:
                Msg.chat(var0, Msg.system(Msg.tr("advance.not_max").withStyle(ChatFormatting.GRAY)));
                break;
            case PLAYER_LEVEL_TOO_LOW:
                int var5 = reg().advancementOf(var1).map(TemplateDefinition::advanceRequiredPlayerLevel).orElse(0);
                Msg.chat(var0, Msg.system(Msg.tr("advance.level_low", var5).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static void lineageHint(ServerPlayer var0, PlayerSystemData var1, TemplateDefinition var2) {
        List var3 = reg().lineageChildrenOf(var2.id());
        if (!var3.isEmpty()) {
            int var4 = LineageDrawService.requiredPlayerLevel(var2.id(), reg());
            if (var1.level() < var4) {
                Msg.chat(var0, Msg.system(Msg.tr("lineage.level_low", var4).withStyle(ChatFormatting.GRAY)));
            } else {
                double var5 = PassiveApplier.drawLuck(var1);
                MutableComponent var7 = Component.literal("");

                for (int var8 = 0; var8 < var3.size(); var8++) {
                    TemplateDefinition var9 = (TemplateDefinition)var3.get(var8);
                    if (var8 > 0) {
                        var7.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY));
                    }

                    var7.append(Msg.template(var9))
                        .append(Component.literal(" " + percent(var9.lineage().effectiveChance(var5))).withStyle(ChatFormatting.GRAY));
                }

                Msg.chat(var0, Msg.system(Msg.tr("lineage.available", var7, var1.pendingDraws()).withStyle(ChatFormatting.LIGHT_PURPLE)));
            }
        }
    }

    private static String percent(double var0) {
        return String.format(Locale.ROOT, "%.1f%%", var0 * 100.0);
    }

    public static boolean lineageDraw(ServerPlayer var0, String var1) {
        return lineageDraw(var0, var1, null);
    }

    public static boolean lineageDraw(ServerPlayer var0, String var1, String var2) {
        PlayerSystemData var3 = SystemDataManager.get(var0);
        if (var2 == null || var2.isEmpty()) {
            List<TemplateDefinition> var4 = reg().lineageChildrenOf(var1);
            if (var4.size() == 1) {
                var2 = ((TemplateDefinition)var4.get(0)).id();
            } else if (var4.size() > 1) {
                LineageDrawService.Status var5 = LineageDrawService.check(var3, var1, reg());
                if (var5 == LineageDrawService.Status.SUCCESS) {
                    double var14 = PassiveApplier.drawLuck(var3);
                    Msg.chat(var0, Msg.system(Msg.tr("lineage.choose").withStyle(ChatFormatting.LIGHT_PURPLE)));

                    for (TemplateDefinition var9 : var4) {
                        String var10 = "/rpgtemplate lineage " + var1 + " " + var9.id();
                        MutableComponent var11 = Component.literal("  ▶ ")
                            .append(Msg.template(var9))
                            .append(Component.literal("  " + percent(var9.lineage().effectiveChance(var14))).withStyle(ChatFormatting.GRAY))
                            .withStyle(
                                var2x -> var2x.withClickEvent(new ClickEvent(Action.RUN_COMMAND, var10))
                                        .withHoverEvent(
                                            new HoverEvent(
                                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Msg.tr("lineage.choose_hover", var9.displayName())
                                            )
                                        )
                            );
                        Msg.chat(var0, var11);
                    }

                    return false;
                }
            }
        }

        LineageDrawService.Result var12 = LineageDrawService.draw(
            var3, var1, var2, reg(), cfg(), PassiveApplier.drawLuck(var3), () -> var0.getRandom().nextDouble()
        );
        MutableComponent var13 = var12.source != null ? Msg.template(var12.source) : Component.literal(var1);
        switch (var12.status) {
            case BRANCH_UNAVAILABLE:
                Msg.chat(var0, Msg.system(Msg.tr("lineage.branch_unavailable", String.valueOf(var2), var13).withStyle(ChatFormatting.RED)));
                break;
            case NOT_ACTIVATED:
                Msg.chat(var0, Msg.system(Msg.tr("not_activated").withStyle(ChatFormatting.RED)));
                break;
            case NOT_OWNED:
                Msg.chat(var0, Msg.system(Msg.tr("load.not_owned", var1).withStyle(ChatFormatting.RED)));
                break;
            case NO_LINEAGE:
                Msg.chat(var0, Msg.system(Msg.tr("lineage.none", var13).withStyle(ChatFormatting.GRAY)));
                break;
            case NOT_MAX_LEVEL:
                Msg.chat(var0, Msg.system(Msg.tr("lineage.not_max", var13).withStyle(ChatFormatting.GRAY)));
                break;
            case PLAYER_LEVEL_TOO_LOW:
                Msg.chat(var0, Msg.system(Msg.tr("lineage.level_low", LineageDrawService.requiredPlayerLevel(var1, reg())).withStyle(ChatFormatting.GRAY)));
                break;
            case NO_PENDING:
                Msg.chat(var0, Msg.system(Msg.tr("lineage.no_draw").withStyle(ChatFormatting.GRAY)));
                break;
            case LOCKED:
                MutableComponent var6 = var3.findTemplate(var3.newestTemplateId())
                    .flatMap(var0x -> reg().find(var0x.templateId()))
                    .map(Msg::template)
                    .orElse(Component.literal(String.valueOf(var3.newestTemplateId())));
                Msg.chat(var0, Msg.system(Msg.tr("draw.locked", var6, var3.pendingDraws()).withStyle(ChatFormatting.GRAY)));
                break;
            case FAILED:
                SkillManager.cancel(var0);
                Msg.chat(var0, Msg.system(Msg.tr("lineage.fail", var13, Msg.template(var12.target), percent(var12.chance)).withStyle(ChatFormatting.GRAY)));
                if (var12.consolation != null) {
                    giveConsolation(var0, var3, var12);
                }

                if (var12.pityIn > 0) {
                    Msg.chat(var0, Msg.system(Msg.tr("lineage.pity_in", var12.pityIn).withStyle(ChatFormatting.DARK_GRAY)));
                } else if (var12.pityIn == 0) {
                    Msg.chat(var0, Msg.system(Msg.tr("lineage.pity_next").withStyle(ChatFormatting.LIGHT_PURPLE)));
                }

                var0.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
                var0.serverLevel().sendParticles(ParticleTypes.SMOKE, var0.getX(), var0.getY() + 1.0, var0.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
                PassiveApplier.refresh(var0);
                SystemDataManager.sync(var0);
                break;
            case SUCCESS:
                TemplateDefinition var7 = var12.target;
                int var8 = var7.quality().color() & 16777215;
                SkillManager.cancel(var0);
                Effects.rainbow(var0, 2, var8);
                var0.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 0.8F);
                var0.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
                Msg.chat(
                    var0,
                    Msg.system(Msg.tr("lineage.success", var13, Msg.template(var7), var12.mergedLevel, var7.maxLevel()).withStyle(ChatFormatting.LIGHT_PURPLE))
                );
                if (var12.pityTriggered) {
                    Msg.chat(var0, Msg.system(Msg.tr("lineage.pity_hit").withStyle(ChatFormatting.GOLD)));
                }

                if (var3.pendingDraws() > 0 && !var3.isDrawUnlocked()) {
                    Msg.chat(var0, Msg.system(Msg.tr("draw.relocked", var3.pendingDraws()).withStyle(ChatFormatting.GRAY)));
                }

                NeoForge.EVENT_BUS.post(new TemplateDrawEvent(var0, var7));
                PassiveApplier.refresh(var0);
                SystemDataManager.sync(var0);
                return true;
        }

        return false;
    }

    private static void giveConsolation(ServerPlayer var0, PlayerSystemData var1, LineageDrawService.Result var2) {
        LineageReward var3 = var2.consolation;
        String var4 = var3.type();
        MutableComponent var5;
        switch (var4) {
            case "xp":
                var5 = Msg.tr("lineage.reward.xp", NumberFormatter.format(var2.compensationXp));
                if (var2.xpResult != null && var2.xpResult.playerLevelsGained > 0) {
                    var0.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
                    Msg.chat(var0, Msg.system(Msg.tr("level_up", var1.level(), var2.xpResult.playerLevelsGained).withStyle(ChatFormatting.YELLOW)));
                    NeoForge.EVENT_BUS.post(new LevelUpEvent(var0, var1.level() - var2.xpResult.playerLevelsGained, var1.level()));
                }
                break;
            case "draw":
                var5 = Msg.tr("lineage.reward.draw", Math.max(1, var3.integer("count", 1)));
                break;
            case "shard":
                var5 = Msg.tr("lineage.reward.shard", Math.max(1, var3.integer("count", 1)));
                break;
            case "stat_bonus":
                String var8 = var3.str("stat", "hp").toUpperCase(Locale.ROOT);
                double var9 = var1.value(LineageDrawService.bonusKey(var3.str("stat", "hp")), 0.0);
                var5 = var2.consolationApplied
                    ? Msg.tr("lineage.reward.stat", var8, percent(var3.num("value", 0.02)), percent(var9))
                    : Msg.tr("lineage.reward.stat_capped", var8, percent(var9));
                break;
            case "trait":
                var5 = var2.consolationApplied ? Msg.tr("lineage.reward.trait", var3.displayName()) : Msg.tr("lineage.reward.trait_dup", var3.displayName());
                break;
            case "item":
                int var11 = giveItem(var0, var3);
                var5 = var11 > 0 ? Msg.tr("lineage.reward.item", var11, var3.displayName()) : Msg.tr("lineage.reward.item_invalid", var3.str("id", "?"));
                break;
            default:
                var5 = Component.literal(var3.displayName());
        }

        Msg.chat(var0, Msg.system(Msg.tr("lineage.consolation", var5).withStyle(ChatFormatting.AQUA)));
    }

    private static int giveItem(ServerPlayer var0, LineageReward var1) {
        ResourceLocation var2 = ResourceLocation.tryParse(var1.str("id", ""));
        Item var3 = var2 == null ? null : (Item)BuiltInRegistries.ITEM.get(var2);
        if (var3 != null && var3 != Items.AIR) {
            int var4 = Math.max(1, var1.integer("count", 1));
            ItemStack var5 = new ItemStack(var3, var4);
            String var6 = var1.str("nbt", null);
            if (var6 != null && !var6.isEmpty()) {
                try {
                    ItemNbt.setTag(var5, TagParser.parseTag(var6));
                } catch (CommandSyntaxException var8) {
                    RpgTemplateMod.LOGGER.warn("[rpgtemplate] lineage consolation item nbt invalid: {}", var6);
                }
            }

            if (!var0.getInventory().add(var5)) {
                var0.drop(var5, false);
            }

            return var4;
        } else {
            RpgTemplateMod.LOGGER.warn("[rpgtemplate] lineage consolation item not found: {}", var1.str("id", ""));
            return 0;
        }
    }

    public static void sendStatus(ServerPlayer var0, ServerPlayer var1) {
        PlayerSystemData var2 = SystemDataManager.get(var1);
        if (!var2.isActivated()) {
            var0.sendSystemMessage(Msg.system(Msg.tr("not_activated").withStyle(ChatFormatting.GRAY)));
        } else {
            TemplateRegistry var3 = reg();
            CoreConfig var4 = cfg();
            StatCalculator.normalizeCurrent(var2, var3, var4);
            String var5 = NumberFormatter.format(StatCalculator.maxStat(StatType.HP, var2, var3, var4));
            String var6 = NumberFormatter.format(StatCalculator.maxStat(StatType.MP, var2, var3, var4));
            String var7 = NumberFormatter.format(StatCalculator.maxStat(StatType.SP, var2, var3, var4));
            var0.sendSystemMessage(Component.literal("§b§l══════ 万象模板 ══════"));
            var0.sendSystemMessage(
                Msg.tr(
                    "status.level",
                    var1.getGameProfile().getName(),
                    var2.level(),
                    NumberFormatter.format(var2.xp()),
                    NumberFormatter.format(StatFormula.xpToNextLevel(var2.level(), var4)),
                    var4.growthMode.name()
                )
            );
            var0.sendSystemMessage(
                Msg.tr(
                    "status.stats", NumberFormatter.format(var2.hp()), var5, NumberFormatter.format(var2.mp()), var6, NumberFormatter.format(var2.sp()), var7
                )
            );
            MutableComponent var8 = var2.loadedTemplate()
                .flatMap(
                    var1x -> var3.find(var1x.templateId())
                            .map(
                                var1xx -> {
                                    MutableComponent var2x = Msg.template(var1xx);
                                    return var1xx.levelless()
                                        ? var2x
                                        : var2x.append(Component.literal(" Lv." + var1x.level() + "/" + var1xx.maxLevel()).withStyle(ChatFormatting.WHITE));
                                }
                            )
                )
                .orElse(Msg.tr("status.none").withStyle(ChatFormatting.DARK_GRAY));
            var0.sendSystemMessage(Msg.tr("status.loaded", var8));
            var0.sendSystemMessage(
                Msg.tr(
                    "status.draws",
                    var2.pendingDraws(),
                    var2.isDrawUnlocked() ? Msg.tr("status.unlocked").withStyle(ChatFormatting.GREEN) : Msg.tr("status.locked").withStyle(ChatFormatting.RED)
                )
            );
            var0.sendSystemMessage(Msg.tr("status.inventory", var2.templates().size()));

            for (TemplateInstance var10 : var2.templates()) {
                var3.find(var10.templateId()).ifPresentOrElse(var2x -> {
                    MutableComponent var3x = Component.literal("  • ").append(Msg.template(var2x));
                    if (!var2x.levelless()) {
                        var3x.append(Component.literal(" Lv." + var10.level() + "/" + var2x.maxLevel()).withStyle(ChatFormatting.GRAY));
                    }

                    var3x.append(Component.literal("  " + var10.templateId()).withStyle(ChatFormatting.DARK_GRAY));
                    var0.sendSystemMessage(var3x);
                }, () -> var0.sendSystemMessage(Component.literal("  • ? " + var10.templateId()).withStyle(ChatFormatting.DARK_RED)));
            }
        }
    }

    private static record PendingLoad(String templateId, long readyAt) {
    }
}
