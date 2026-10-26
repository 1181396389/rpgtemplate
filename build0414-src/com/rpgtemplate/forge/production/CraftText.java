package com.rpgtemplate.forge.production;
import net.minecraft.network.chat.Component;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.item.ItemStack;import net.minecraftforge.registries.ForgeRegistries;
/** Keep translation components intact across the server/client boundary. */
public final class CraftText {
 public static Component item(String id){var key=ResourceLocation.m_135820_(id);if(key==null||!ForgeRegistries.ITEMS.containsKey(key))return Component.m_237113_(id);return new ItemStack(ForgeRegistries.ITEMS.getValue(key)).m_41786_();}
 public static Component missing(String id,int count){return Component.m_237113_("材料不足：").m_7220_(item(id)).m_130946_(" ×"+count);}
}
