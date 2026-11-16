package com.rpgtemplate.forge.mask;

import com.rpgtemplate.forge.registry.ModCreativeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 注册「认知阻碍面具」物品并加入主创造模式标签页。 */
public final class PerceptionMaskBootstrap {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, "rpgtemplate");

    public static final RegistryObject<PerceptionBlockingMaskItem> MASK =
        ITEMS.register("perception_blocking_mask", PerceptionBlockingMaskItem::new);

    private PerceptionMaskBootstrap() {
    }

    public static void init() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(PerceptionMaskBootstrap::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) {
            event.accept(MASK);
        }
    }
}
