package com.rpgtemplate.forge.production;
import java.util.Set;import net.minecraftforge.fml.common.Mod;import net.minecraftforge.eventbus.api.SubscribeEvent;import net.minecraftforge.registries.*;import net.minecraft.world.item.Items;import net.minecraft.world.level.block.Blocks;
/** Explicit destructive migration approved by the user. No old inventory is converted or dropped. */
@Mod.EventBusSubscriber(modid="rpgtemplate",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class RetiredBuildContent {
 public static final Set<String> ITEMS=Set.of("fullbottle_firefly","fullbottle_vine","fullbottle_smoke","fullbottle_battery","fullbottle_chain","fullbottle_radar","fabricator_legacy_batch","pandora_slate");
 @SubscribeEvent public static void missing(MissingMappingsEvent event){for(var mapping:event.getMappings(ForgeRegistries.Keys.ITEMS,"rpgtemplate"))if(ITEMS.contains(mapping.getKey().m_135815_()))mapping.remap(Items.f_41852_);for(var mapping:event.getMappings(ForgeRegistries.Keys.BLOCKS,"rpgtemplate"))if(mapping.getKey().m_135815_().equals("pandora_slate"))mapping.remap(Blocks.f_50016_);for(var mapping:event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES,"rpgtemplate"))if(mapping.getKey().m_135815_().equals("pandora_slate"))mapping.ignore();}
}
