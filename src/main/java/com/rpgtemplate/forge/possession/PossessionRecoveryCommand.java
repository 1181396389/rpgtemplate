package com.rpgtemplate.forge.possession;


import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class PossessionRecoveryCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent var0) {
        var0.getDispatcher()
            .register(
                (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("rpgpossession")
                            .requires(var0x -> var0x.hasPermission(2)))
                        .then(Commands.literal("recover").then(Commands.argument("player", EntityArgument.player()).executes(var0x -> {
                            ServerPlayer var1 = EntityArgument.getPlayer(var0x, "player");
                            boolean var2 = recover(var1.getUUID());
                            ((CommandSourceStack)var0x.getSource()).sendSuccess(() -> Component.literal(var2 ? "夺舍会话已恢复。" : "该玩家没有待恢复的夺舍会话。"), true);
                            return var2 ? 1 : 0;
                        }))))
                    .then(Commands.literal("recover_uuid").then(Commands.argument("uuid", StringArgumentType.word()).executes(var0x -> {
                        try {
                            UUID var1 = UUID.fromString(StringArgumentType.getString(var0x, "uuid"));
                            boolean var2 = recover(var1);
                            ((CommandSourceStack)var0x.getSource()).sendSuccess(() -> Component.literal(var2 ? "已安排恢复；离线玩家下次登录时恢复身体。" : "未找到夺舍会话。"), true);
                            return var2 ? 1 : 0;
                        } catch (IllegalArgumentException var3) {
                            ((CommandSourceStack)var0x.getSource()).sendFailure(Component.literal("请输入有效的玩家 UUID。"));
                            return 0;
                        } catch (IllegalStateException var4) {
                            ((CommandSourceStack)var0x.getSource()).sendFailure(Component.literal("无法安全恢复：" + var4.getMessage() + "。未删除背包或存档。"));
                            return 0;
                        }
                    })))
            );
    }

    public static boolean recover(UUID var0) {
        PossessionData.Rec var1 = Possession.recOf(var0);
        if (var1 != null) {
            return Possession.recover(var1, null);
        } else {
            ServerPlayer var2 = Possession.online(var0);
            return var2 != null && Possession.runPending(var2);
        }
    }
}
