package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 一无所有指令（需要 OP 权限，等级 2）：
 *   /nt unlockall   解锁执行者的全部形态（骨刺连射、贪食之口、脊骨鞭、全收）。只改解锁位，不改当前形态。
 *
 * 形态 0 锤、1 手刀本来就是默认解锁的，所以只需要处理 2..5。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NtCommands {
    private static final int OP_LEVEL = 2;

    private NtCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.m_82127_("nt")
            .requires(source -> source.m_6761_(OP_LEVEL))
            .then(Commands.m_82127_("unlockall")
                .executes(c -> {
                    unlockAll(c.getSource().m_81375_());
                    return 1;
                })));
    }

    private static void unlockAll(ServerPlayer player) {
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            Msg.chat(player, Component.m_237113_("当前没有激活一无所有模板，无法解锁形态。")
                .m_130940_(ChatFormatting.RED));
            return;
        }
        List<String> added = new ArrayList<>();
        for (int form = 2; form < NtForms.NAMES.length; form++) {
            if (!NtForms.unlocked(data, form)) {
                NtForms.unlock(data, form);
                added.add(NtForms.NAMES[form]);
            }
        }
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
        String text = added.isEmpty()
            ? "全部形态已经解锁。"
            : "已解锁形态：" + String.join("、", added) + "。";
        Msg.chat(player, Component.m_237113_(text).m_130940_(ChatFormatting.LIGHT_PURPLE));
    }
}
