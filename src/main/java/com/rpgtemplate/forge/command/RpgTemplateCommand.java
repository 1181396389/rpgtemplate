package com.rpgtemplate.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.service.LevelingService;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.registry.ModItems;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.PassiveApplier;
import com.rpgtemplate.forge.system.SystemActions;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.world.TallyWorldData;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class RpgTemplateCommand {
    private static final SuggestionProvider<CommandSourceStack> LINEAGE_BRANCHES = (var0, var1) -> {
        String var2;
        try {
            var2 = ResourceLocationArgument.getId(var0, "template").toString();
        } catch (IllegalArgumentException var4) {
            return var1.buildFuture();
        }

        return SharedSuggestionProvider.suggest(TemplateRegistry.get().lineageChildrenOf(var2).stream().map(TemplateDefinition::id), var1);
    };
    private static final SuggestionProvider<CommandSourceStack> OWNED = (var0, var1) -> {
        ServerPlayer var2 = ((CommandSourceStack)var0.getSource()).getEntity() instanceof ServerPlayer var3 ? var3 : null;
        return var2 == null
            ? var1.buildFuture()
            : SharedSuggestionProvider.suggest(SystemDataManager.get(var2).templates().stream().map(TemplateInstance::templateId), var1);
    };

    private RpgTemplateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> var0) {
        var0.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                                "rpgtemplate"
                                                            )
                                                            .then(
                                                                ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("give")
                                                                            .requires(var0x -> var0x.hasPermission(2)))
                                                                        .executes(
                                                                            var0x -> give(
                                                                                    var0x,
                                                                                    List.of(((CommandSourceStack)var0x.getSource()).getPlayerOrException())
                                                                                )
                                                                        ))
                                                                    .then(
                                                                        Commands.argument("targets", EntityArgument.players())
                                                                            .executes(var0x -> give(var0x, EntityArgument.getPlayers(var0x, "targets")))
                                                                    )
                                                            ))
                                                        .then(
                                                            ((LiteralArgumentBuilder)Commands.literal("info")
                                                                    .executes(
                                                                        var0x -> info(var0x, ((CommandSourceStack)var0x.getSource()).getPlayerOrException())
                                                                    ))
                                                                .then(
                                                                    ((RequiredArgumentBuilder)Commands.argument("target", EntityArgument.player())
                                                                            .requires(var0x -> var0x.hasPermission(2)))
                                                                        .executes(var0x -> info(var0x, EntityArgument.getPlayer(var0x, "target")))
                                                                )
                                                        ))
                                                    .then(
                                                        Commands.literal("draw")
                                                            .executes(
                                                                var0x -> SystemActions.draw(((CommandSourceStack)var0x.getSource()).getPlayerOrException())
                                                                        ? 1
                                                                        : 0
                                                            )
                                                    ))
                                                .then(
                                                    Commands.literal("load")
                                                        .then(
                                                            Commands.argument("template", ResourceLocationArgument.id())
                                                                .suggests(OWNED)
                                                                .executes(
                                                                    var0x -> {
                                                                        SystemActions.requestLoad(
                                                                            ((CommandSourceStack)var0x.getSource()).getPlayerOrException(),
                                                                            ResourceLocationArgument.getId(var0x, "template").toString()
                                                                        );
                                                                        return 1;
                                                                    }
                                                                )
                                                        )
                                                ))
                                            .then(Commands.literal("unload").executes(var0x -> {
                                                SystemActions.unload(((CommandSourceStack)var0x.getSource()).getPlayerOrException());
                                                return 1;
                                            })))
                                        .then(
                                            Commands.literal("advance")
                                                .then(
                                                    Commands.argument("template", ResourceLocationArgument.id())
                                                        .suggests(OWNED)
                                                        .executes(
                                                            var0x -> {
                                                                SystemActions.advance(
                                                                    ((CommandSourceStack)var0x.getSource()).getPlayerOrException(),
                                                                    ResourceLocationArgument.getId(var0x, "template").toString()
                                                                );
                                                                return 1;
                                                            }
                                                        )
                                                )
                                        ))
                                    .then(
                                        Commands.literal("lineage")
                                            .then(
                                                ((RequiredArgumentBuilder)Commands.argument("template", ResourceLocationArgument.id())
                                                        .suggests(OWNED)
                                                        .executes(
                                                            var0x -> SystemActions.lineageDraw(
                                                                        ((CommandSourceStack)var0x.getSource()).getPlayerOrException(),
                                                                        ResourceLocationArgument.getId(var0x, "template").toString(),
                                                                        null
                                                                    )
                                                                    ? 1
                                                                    : 0
                                                        ))
                                                    .then(
                                                        Commands.argument("branch", ResourceLocationArgument.id())
                                                            .suggests(LINEAGE_BRANCHES)
                                                            .executes(
                                                                var0x -> SystemActions.lineageDraw(
                                                                            ((CommandSourceStack)var0x.getSource()).getPlayerOrException(),
                                                                            ResourceLocationArgument.getId(var0x, "template").toString(),
                                                                            ResourceLocationArgument.getId(var0x, "branch").toString()
                                                                        )
                                                                        ? 1
                                                                        : 0
                                                            )
                                                    )
                                            )
                                    ))
                                .then(
                                    ((LiteralArgumentBuilder)Commands.literal("xp").requires(var0x -> var0x.hasPermission(2)))
                                        .then(
                                            Commands.literal("add")
                                                .then(
                                                    ((RequiredArgumentBuilder)Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                                            .executes(var0x -> addXp(var0x, ((CommandSourceStack)var0x.getSource()).getPlayerOrException())))
                                                        .then(
                                                            Commands.argument("target", EntityArgument.player())
                                                                .executes(var0x -> addXp(var0x, EntityArgument.getPlayer(var0x, "target")))
                                                        )
                                                )
                                        )
                                ))
                            .then(
                                ((LiteralArgumentBuilder)Commands.literal("level").requires(var0x -> var0x.hasPermission(2)))
                                    .then(
                                        Commands.literal("set")
                                            .then(
                                                ((RequiredArgumentBuilder)Commands.argument("level", IntegerArgumentType.integer(0, 1000000))
                                                        .executes(var0x -> setLevel(var0x, ((CommandSourceStack)var0x.getSource()).getPlayerOrException())))
                                                    .then(
                                                        Commands.argument("target", EntityArgument.player())
                                                            .executes(var0x -> setLevel(var0x, EntityArgument.getPlayer(var0x, "target")))
                                                    )
                                            )
                                    )
                            ))
                        .then(
                            ((LiteralArgumentBuilder)Commands.literal("template").requires(var0x -> var0x.hasPermission(2)))
                                .then(
                                    ((LiteralArgumentBuilder)Commands.literal("maxlevel")
                                            .executes(var0x -> maxTemplate(var0x, ((CommandSourceStack)var0x.getSource()).getPlayerOrException())))
                                        .then(
                                            Commands.argument("target", EntityArgument.player())
                                                .executes(var0x -> maxTemplate(var0x, EntityArgument.getPlayer(var0x, "target")))
                                        )
                                )
                        ))
                    .then(
                        ((LiteralArgumentBuilder)Commands.literal("templates").requires(var0x -> var0x.hasPermission(2)))
                            .executes(RpgTemplateCommand::listTemplates)
                    ))
                .then(((LiteralArgumentBuilder)Commands.literal("reset").requires(var0x -> var0x.hasPermission(2))).executes(RpgTemplateCommand::reset))
        );
    }

    private static int give(CommandContext<CommandSourceStack> var0, Collection<ServerPlayer> var1) {
        for (ServerPlayer var3 : var1) {
            ItemStack var4 = new ItemStack((ItemLike)ModItems.GOLDEN_LEOPARD_TALLY.get());
            if (!var3.getInventory().add(var4)) {
                var3.drop(var4, false);
            }
        }

        ((CommandSourceStack)var0.getSource()).sendSuccess(() -> Msg.tr("cmd.give", var1.size()), true);
        return var1.size();
    }

    private static int info(CommandContext<CommandSourceStack> var0, ServerPlayer var1) throws CommandSyntaxException {
        ServerPlayer var2 = ((CommandSourceStack)var0.getSource()).getPlayerOrException();
        SystemActions.sendStatus(var2, var1);
        return 1;
    }

    private static int addXp(CommandContext<CommandSourceStack> var0, ServerPlayer var1) {
        double var2 = DoubleArgumentType.getDouble(var0, "amount");
        if (!SystemDataManager.get(var1).isActivated()) {
            ((CommandSourceStack)var0.getSource()).sendFailure(Msg.tr("not_activated"));
            return 0;
        } else {
            SystemActions.grantXp(var1, var2, RpgTemplateMod.id("command"));
            ((CommandSourceStack)var0.getSource()).sendSuccess(() -> Msg.tr("cmd.xp", var2, var1.getDisplayName()), true);
            return 1;
        }
    }

    private static int setLevel(CommandContext<CommandSourceStack> var0, ServerPlayer var1) {
        int var2 = IntegerArgumentType.getInteger(var0, "level");
        PlayerSystemData var3 = SystemDataManager.get(var1);
        if (!var3.isActivated()) {
            ((CommandSourceStack)var0.getSource()).sendFailure(Msg.tr("not_activated"));
            return 0;
        } else {
            LevelingService.setLevel(var3, var2, TemplateRegistry.get(), CoreConfig.get());
            PassiveApplier.refresh(var1);
            SystemDataManager.sync(var1);
            ((CommandSourceStack)var0.getSource()).sendSuccess(() -> Msg.tr("cmd.level", var1.getDisplayName(), var2), true);
            return 1;
        }
    }

    private static int maxTemplate(CommandContext<CommandSourceStack> var0, ServerPlayer var1) {
        PlayerSystemData var2 = SystemDataManager.get(var1);
        TemplateInstance var3 = var2.loadedTemplate().orElse(null);
        TemplateDefinition var4 = var3 == null ? null : TemplateRegistry.get().find(var3.templateId()).orElse(null);
        if (var4 != null && !var4.levelless()) {
            var3.setLevel(var4.maxLevel());
            var3.setXp(0.0);
            LevelingService.checkDrawUnlock(var2, TemplateRegistry.get(), null);
            SystemDataManager.sync(var1);
            ((CommandSourceStack)var0.getSource()).sendSuccess(() -> Msg.tr("cmd.maxlevel", Msg.template(var4)), true);
            return 1;
        } else {
            ((CommandSourceStack)var0.getSource()).sendFailure(Msg.tr("cmd.maxlevel.fail"));
            return 0;
        }
    }

    private static int listTemplates(CommandContext<CommandSourceStack> var0) {
        TemplateRegistry var1 = TemplateRegistry.get();
        ((CommandSourceStack)var0.getSource()).sendSuccess(() -> Msg.tr("cmd.templates", var1.size()), false);

        for (TemplateDefinition var3 : var1.all()) {
            MutableComponent var4 = Component.literal(" • ")
                .append(Msg.template(var3))
                .append(
                    Component.literal("  " + var3.id() + (var3.parent() != null ? "  ← " + var3.parent() : "") + (var3.drawable() ? "" : "  [不可抽取]"))
                        .withStyle(ChatFormatting.DARK_GRAY)
                );
            ((CommandSourceStack)var0.getSource()).sendSuccess(() -> var4, false);
        }

        return var1.size();
    }

    private static int reset(CommandContext<CommandSourceStack> var0) {
        TallyWorldData var1 = TallyWorldData.get(((CommandSourceStack)var0.getSource()).getServer());
        if (var1.owner() != null) {
            ServerPlayer var2 = ((CommandSourceStack)var0.getSource()).getServer().getPlayerList().getPlayer(var1.owner());
            if (var2 != null) {
                SystemDataManager.get(var2).reset();
                SystemActions.clearPending(var2.getUUID());
                PassiveApplier.refresh(var2);
                SystemDataManager.sync(var2);
            }
        }

        var1.clear();
        ((CommandSourceStack)var0.getSource()).sendSuccess(() -> Msg.tr("cmd.reset"), true);
        return 1;
    }
}
