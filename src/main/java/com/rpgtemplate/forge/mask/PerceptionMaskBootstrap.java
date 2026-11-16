package com.rpgtemplate.forge.mask;

import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.registry.ModCreativeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 注册「认知阻碍面具」物品并加入主创造模式标签页。 */
public final class PerceptionMaskBootstrap {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, "rpgtemplate");

    public static final DeferredHolder<Item, PerceptionBlockingMaskItem> MASK =
        ITEMS.register("perception_blocking_mask", PerceptionBlockingMaskItem::new);

    private PerceptionMaskBootstrap() {
    }

    public static void init() {
        init(RpgTemplateMod.MOD_BUS);
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(PerceptionMaskBootstrap::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) {
            event.accept(MASK.get());
        }
    }
}
