package com.rpgtemplate.forge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "rpgtemplate");
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register(
        "main",
        () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.rpgtemplate"))
                .icon(() -> new ItemStack(ModItems.GOLDEN_LEOPARD_TALLY.get()))
                .displayItems((params, output) -> ModItems.REGISTER.getEntries().forEach(entry -> output.accept((ItemLike) entry.get())))
                .build()
    );

    private ModCreativeTabs() {
    }
}
