package com.rpgtemplate.forge.production;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.commands.Commands;
import com.rpgtemplate.forge.system.SystemDataManager;
@Mod.EventBusSubscriber(modid="rpgtemplate")
public final class CraftBootstrap {
 static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.f_279569_,"rpgtemplate");
 public static void init(){var bus=FMLJavaModLoadingContext.get().getModEventBus();BuildVisualConfig.register();Pandora.init(bus);Fabricator.init(bus);CraftItems.REGISTER.register(bus);TABS.register("production",()->tab("production","workshop_manual",0));TABS.register("build_bottles",()->tab("build_bottles","fullbottle_rabbit",1));TABS.register("build_devices",()->tab("build_devices","hazard_trigger",2));TABS.register(bus);CraftNet.init();BuildCinematics.init();BuildSystem.registerSkills();}
 static int creativeCategory(String id){if(com.rpgtemplate.core.production.BuildCatalog.ALL.stream().anyMatch(b->id.equals("fullbottle_"+b.id()))||java.util.Set.of("empty_fullbottle","sample_fullbottle","genius_fullbottle").contains(id))return 1;if(id.startsWith("pandora_")||id.equals("fullbottle_fabricator"))return 2;var r=com.rpgtemplate.core.production.Catalog.BY_ID.get(id);if(r!=null&&r.role().equals("build"))return BuildSystem.modern(r)&&!r.school().equals("fabricator")?2:-1;return r!=null&&BuildSystem.modern(r)?0:-1;}
 static CreativeModeTab tab(String key,String icon,int group){return CreativeModeTab.builder().m_257941_(Component.m_237115_("itemGroup.rpgtemplate."+key)).m_257737_(()->new ItemStack(CraftItems.ITEMS.get(icon).get())).m_257501_((parameters,output)->{if(group==1){for(var b:com.rpgtemplate.core.production.BuildCatalog.CANON)output.m_246326_(CraftItems.ITEMS.get("fullbottle_"+b.id()).get());for(String id:new String[]{"genius_fullbottle","empty_fullbottle","sample_fullbottle"})output.m_246326_(CraftItems.ITEMS.get(id).get());}else CraftItems.ITEMS.forEach((id,item)->{if(creativeCategory(id)==group)output.m_246326_(item.get());});}).m_257652_();}
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p){CraftSessions.tick(p);MedicalJobs.tick(p);BuildBottles.tick(p);Enhancers.tick(p);CapturedBottles.tick(p);if(p.f_19797_%20==0){BuildSystem.tickSecond(p);CraftEffects.tick(p);if(CraftState.storage(p).m_128471_("viewing"))CraftNet.sync(p,false);}}}
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){BuildSystem.ensureProfessions(p);Enhancers.clear(p);CraftSessions.restore(p);MedicalJobs.login(p);CapturedBottles.login(p);CraftState.storage(p).m_128379_("viewing",false);}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){BuildCinematics.cancel(p);CraftSessions.pause(p);MedicalJobs.pause(p);BuildBottles.clearTransient(p);Enhancers.clear(p);}}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){BuildCinematics.cancelFrom(p,e.getFrom());BuildBottles.clearTransient(p);Enhancers.clear(p);}}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal().getPersistentData().m_128441_(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG))e.getEntity().getPersistentData().m_128365_(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,e.getOriginal().getPersistentData().m_128469_(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).m_6426_());}
 @SubscribeEvent public static void stop(ServerStoppingEvent e){for(ServerPlayer p:e.getServer().m_6846_().m_11314_()){CraftSessions.pause(p);MedicalJobs.pause(p);Enhancers.clear(p);}CraftSessions.ACTIVE.clear();}
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p){CraftSessions.finish(p,true);BuildSystem.onDeath(p);}}
 @SubscribeEvent public static void fall(LivingFallEvent e){if(e.getEntity() instanceof ServerPlayer p&&BuildSystem.eligible(p)&&BuildSystem.danger(p)>0){var b=BuildSystem.body(p);if(b.m_128471_("passive")&&b.m_128459_("energy")>0&&b.m_128461_("organic").equals("rabbit")&&BuildBottles.has(p,"rabbit"))e.setDamageMultiplier(e.getDamageMultiplier()*.6f);}}
 @SubscribeEvent public static void knock(LivingKnockBackEvent e){if(e.getEntity() instanceof ServerPlayer p&&BuildSystem.eligible(p)){var b=BuildSystem.body(p);if(b.m_128471_("passive")&&b.m_128459_("energy")>0){String a=b.m_128461_("organic"),i=b.m_128461_("inorganic");if(a.equals("gorilla")&&BuildBottles.has(p,a)||i.equals("tank")&&p.m_20096_()&&BuildBottles.has(p,i))e.setStrength(e.getStrength()*.75f);}}}
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void hurt(LivingHurtEvent e){BuildBottles.damage(e);if(e.getSource().m_7639_() instanceof ServerPlayer source)e.setAmount((float)(e.getAmount()*Enhancers.scale(source)));if(e.getSource().m_7639_() instanceof ServerPlayer p&&e.getSource().m_7640_()==p){var r=CraftItems.recipe(p.m_21205_());if(r!=null&&r.school().equals("odd")&&r.role().equals("smith")&&e.getEntity().getPersistentData().m_128454_("mark."+p.m_20148_())>p.m_9236_().m_46467_())e.setAmount(e.getAmount()+1+r.rank()*.25f);}}
 @SubscribeEvent public static void effectAdded(MobEffectEvent.Added e){if(e.getEntity() instanceof ServerPlayer p)CapturedBottles.externalEffect(p,e.getEffectInstance().m_19544_());}
 @SubscribeEvent public static void sample(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e){if(e.getItemStack().m_41720_() instanceof CapturedBottles.Empty&&e.getTarget() instanceof net.minecraft.world.entity.LivingEntity target){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);if(e.getEntity() instanceof ServerPlayer p)CapturedBottles.capture(p,target,e.getItemStack());}}
 @SubscribeEvent public static void protectSlate(net.minecraftforge.event.level.BlockEvent.BreakEvent e){if(e.getLevel().m_7702_(e.getPos()) instanceof Pandora.Slate s&&!s.owner.isEmpty()&&!s.owner.equals(e.getPlayer().m_20148_().toString())&&!e.getPlayer().m_7500_())e.setCanceled(true);}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){MedicalJobs.commands(e);}
}
