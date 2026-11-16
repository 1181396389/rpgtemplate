package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.RpgTemplateMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 拟态（Nothing There）相关的启动注册。
 */
public final class NtBootstrap {
    static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "rpgtemplate");

    private NtBootstrap() {
    }

    public static void init() {
        init(RpgTemplateMod.MOD_BUS);
    }

    public static void init(IEventBus bus) {
        NtMimicBlade.init();
        NtMimicBlade.REGISTER.register(bus);
        TABS.register("nt_weapons", NtBootstrap::ntTab);
        TABS.register(bus);
    }

    static CreativeModeTab ntTab() {
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.rpgtemplate.nt_weapons"))
                .icon(() -> new ItemStack(NtMimicBlade.ITEM.get()))
                .displayItems((parameters, output) -> output.accept(NtMimicBlade.ITEM.get()))
                .build();
    }
}
