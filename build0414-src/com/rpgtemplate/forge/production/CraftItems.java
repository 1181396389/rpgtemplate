package com.rpgtemplate.forge.production;
import com.rpgtemplate.core.production.Catalog;import com.rpgtemplate.core.production.BuildCatalog;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.*;
import com.google.common.collect.*;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.*;
public final class CraftItems {
 public static final DeferredRegister<Item> REGISTER=DeferredRegister.create(ForgeRegistries.ITEMS,"rpgtemplate");
 public static final Map<String,RegistryObject<Item>> ITEMS=new LinkedHashMap<>();
 static {ITEMS.put("empty_fullbottle",REGISTER.register("empty_fullbottle",CapturedBottles.Empty::new));ITEMS.put("sample_fullbottle",REGISTER.register("sample_fullbottle",CapturedBottles.Filled::new));for(Catalog.Recipe r:Catalog.ALL)ITEMS.put(r.id(),REGISTER.register(r.id(),()->r.role().equals("smith")||r.effect().equals("saber")?new Weapon(r):new Product(r)));for(String id:new String[]{"research_sample","research_sampler","workshop_manual"})ITEMS.put(id,REGISTER.register(id,()->new Utility(id)));}
 public static Catalog.Recipe recipe(ItemStack s){return s.m_41720_() instanceof Product p?p.recipe:s.m_41720_() instanceof Weapon w?w.recipe:null;}
 static void tooltip(Catalog.Recipe r,ItemStack s,List<Component> text){if(r.id().startsWith("fullbottle_")){var b=com.rpgtemplate.core.production.BuildCatalog.BY_ID.get(r.effect());if(b!=null){text.add(Component.m_237113_("来源：模组满装瓶 · 合成台随机产出"));text.add(Component.m_237113_((BuildCatalog.canonical(b)?"原著搭配组 "+(BuildCatalog.canonicalPair(b)+1):"旧版兼容瓶")+" · 危险等级影响发挥"));text.add(Component.m_237113_("右键发动 / 潜行右键配对 / 潜行点方块设置"));}}text.add(Component.m_237113_((r.id().startsWith("fullbottle_")?"满装瓶罐":r.rank()+"品")+" · "+Catalog.schoolName(r.school())));for(int i=0;i<r.description().length();i+=25)text.add(Component.m_237113_(r.description().substring(i,Math.min(r.description().length(),i+25))));if(r.role().equals("smith"))text.add(Component.m_237113_("结构 "+s.m_41784_().m_128451_("Design")+" / 强化 +"+s.m_41784_().m_128451_("Upgrade")+" / 主体 "+s.m_41784_().m_128461_("Subject")));}
 public static class Product extends Item {
  final Catalog.Recipe recipe;
  Product(Catalog.Recipe r){super(new Properties().m_41487_(r.role().equals("pharma")?16:1));recipe=r;}
  @Override public InteractionResultHolder<ItemStack> m_7203_(Level l,Player p,InteractionHand h){ItemStack s=p.m_21120_(h);if(p instanceof ServerPlayer sp){if(recipe.id().startsWith("fullbottle_"))BuildBottles.use(sp,com.rpgtemplate.core.production.BuildCatalog.BY_ID.get(recipe.effect()));else if(recipe.school().equals("enhancer"))Enhancers.use(sp,s,recipe.id());else if(MedicalJobs.medical(recipe.id()))MedicalJobs.use(sp,sp,s,recipe.id());else if(recipe.role().equals("build"))CraftSessions.say(sp,"旧版Build物品仅保留存档，不再作为新体系入口或使用资格。");else CraftEffects.use(sp,s,recipe);}return InteractionResultHolder.m_19092_(s,l.f_46443_);}
  @Override public InteractionResult m_6225_(net.minecraft.world.item.context.UseOnContext c){if(recipe.id().startsWith("fullbottle_")&&c.m_43723_()!=null&&c.m_43723_().m_6144_()){if(c.m_43723_() instanceof ServerPlayer p)BuildBottles.settings(p);return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
  @Override public InteractionResult m_6880_(ItemStack s,Player player,LivingEntity target,InteractionHand hand){if(MedicalJobs.medical(recipe.id())){if(player instanceof ServerPlayer op&&target instanceof ServerPlayer patient)MedicalJobs.use(op,patient,s,recipe.id());else if(player instanceof ServerPlayer op)CraftSessions.say(op,"医疗道具只能对玩家使用。");return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
  @Override public void m_7373_(ItemStack s,Level l,List<Component> t,TooltipFlag f){tooltip(recipe,s,t);}
 }
 public static class Weapon extends SwordItem {
  final Catalog.Recipe recipe;
  Weapon(Catalog.Recipe r){super(Tiers.IRON,2+r.rank(),-2.4f,new Item.Properties().m_41499_(250+r.rank()*120));recipe=r;}
  @Override public Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack s){if(slot!=EquipmentSlot.MAINHAND)return ImmutableMultimap.of();int d=s.m_41784_().m_128451_("Design"),u=Math.min(3,s.m_41784_().m_128451_("Upgrade"));return ImmutableMultimap.of(Attributes.f_22281_,new AttributeModifier(UUID.fromString("fa233e1c-4180-4865-b01b-bcce9785aca3"),"Production damage",4+recipe.rank()+d+u*.75,AttributeModifier.Operation.ADDITION),Attributes.f_22283_,new AttributeModifier(UUID.fromString("fa233e1c-4180-4865-b01b-bcce9785aca4"),"Production speed",-2.4-d*.3,AttributeModifier.Operation.ADDITION));}
  @Override public boolean m_7579_(ItemStack s,LivingEntity target,LivingEntity attacker){if(attacker instanceof ServerPlayer p&&!recipe.role().equals("build"))CraftEffects.hit(p,s,recipe,target);return super.m_7579_(s,target,attacker);}
  @Override public InteractionResultHolder<ItemStack> m_7203_(Level l,Player p,InteractionHand h){ItemStack s=p.m_21120_(h);if(p instanceof ServerPlayer sp){if(recipe.id().startsWith("fullbottle_"))BuildBottles.use(sp,com.rpgtemplate.core.production.BuildCatalog.BY_ID.get(recipe.effect()));else if(recipe.school().equals("enhancer"))Enhancers.use(sp,s,recipe.id());else if(MedicalJobs.medical(recipe.id()))MedicalJobs.use(sp,sp,s,recipe.id());else if(recipe.role().equals("build"))CraftSessions.say(sp,"旧版Build物品仅保留存档，不再作为新体系入口或使用资格。");else CraftEffects.use(sp,s,recipe);}return InteractionResultHolder.m_19092_(s,l.f_46443_);}
  @Override public void m_7373_(ItemStack s,Level l,List<Component> t,TooltipFlag f){tooltip(recipe,s,t);}
 }
 static class Utility extends Item {
  final String id;Utility(String id){super(new Properties().m_41487_(id.equals("research_sample")?16:1));this.id=id;}
  @Override public InteractionResultHolder<ItemStack> m_7203_(Level l,Player p,InteractionHand h){if(p instanceof ServerPlayer sp&&id.equals("workshop_manual"))CraftSessions.say(sp,"请通过所装备职业的面板技能打开工坊。");return InteractionResultHolder.m_19092_(p.m_21120_(h),l.f_46443_);}
  @Override public InteractionResult m_6880_(ItemStack s,Player player,LivingEntity target,InteractionHand hand){if(!id.equals("research_sampler"))return InteractionResult.PASS;if(player instanceof ServerPlayer p){String source=ForgeRegistries.ENTITY_TYPES.getKey(target.m_6095_()).toString();boolean supported=Catalog.ALL.stream().anyMatch(r->r.school().equals("bio")&&r.source().equals(source));long now=p.m_9236_().m_46467_();if(!supported){CraftSessions.say(p,"该生物不在首批采样兼容表中。");return InteractionResult.FAIL;}if(target.getPersistentData().m_128454_("rpgSampleUntil")>now){CraftSessions.say(p,"该生物尚未恢复，请等待一分钟。");return InteractionResult.FAIL;}if(!CraftEffects.consume(p,"glass_bottle",1)){CraftSessions.say(p,"需要1个玻璃瓶。");return InteractionResult.FAIL;}ItemStack sample=new ItemStack(ITEMS.get("research_sample").get());sample.m_41784_().m_128359_("Sample",source);if(!p.m_150109_().m_36054_(sample))p.m_36176_(sample,false);target.getPersistentData().m_128356_("rpgSampleUntil",now+1200);CraftSessions.say(p,"已取得生物样本："+source);}return InteractionResult.SUCCESS;}
  @Override public void m_7373_(ItemStack s,Level l,List<Component> t,TooltipFlag f){if(id.equals("research_sample"))t.add(Component.m_237113_(s.m_41784_().m_128461_("Sample")));}
 }
}
