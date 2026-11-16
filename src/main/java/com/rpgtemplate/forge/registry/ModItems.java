package com.rpgtemplate.forge.registry;

import com.rpgtemplate.forge.item.TigerTallyItem;
import com.rpgtemplate.forge.possession.SukunaFingerItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(BuiltInRegistries.ITEM, "rpgtemplate");
    public static final DeferredHolder<Item, TigerTallyItem> GOLDEN_LEOPARD_TALLY = REGISTER.register(
        "golden_leopard_tally", () -> new TigerTallyItem(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );
    public static final DeferredHolder<Item, SukunaFingerItem> SUKUNA_FINGER = REGISTER.register(
        "sukuna_finger", () -> new SukunaFingerItem(SukunaFingerItem.props())
    );

    private ModItems() {
    }
}
