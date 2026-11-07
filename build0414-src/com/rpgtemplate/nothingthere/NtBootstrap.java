package com.rpgtemplate.nothingthere;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 拟态（Nothing There）相关的启动注册。
 *
 * <p>原先这部分挂在已移除的职业/build 系统 {@code CraftBootstrap} 里，这里单独保留：
 * 注册拟态刀物品、以及 "nt_weapons" 创造模式标签页。
 */
public final class NtBootstrap {

    static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.f_279569_, "rpgtemplate");

    private NtBootstrap() {
    }

    public static void init() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        NtMimicBlade.init();
        NtMimicBlade.REGISTER.register(bus);
        TABS.register("nt_weapons", () -> ntTab());
        TABS.register(bus);
    }

    static CreativeModeTab ntTab() {
        return CreativeModeTab.builder()
                .m_257941_(Component.m_237115_("itemGroup.rpgtemplate.nt_weapons"))
                .m_257737_(() -> new ItemStack(NtMimicBlade.ITEM.get()))
                .m_257501_((parameters, output) -> output.m_246326_(NtMimicBlade.ITEM.get()))
                .m_257652_();
    }
}
