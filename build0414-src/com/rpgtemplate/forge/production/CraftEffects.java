package com.rpgtemplate.forge.production;
import com.rpgtemplate.core.production.*;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.*;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
public final class CraftEffects {
 public static final String[] POWERS={"tech_7","tech_8","tech_9","magic_7","magic_8","magic_9","odd_7","odd_8","odd_9"};
 public static final String[] POWER_NAMES={"推进冲刺","光学感知","应急屏障","浮空跃升","净化术","短距闪现","水行适应","短暂隐匿","生命回响"};
 static final Map<String,String> EFFECTS=Map.ofEntries(Map.entry("jump","jump_boost"),Map.entry("water","water_breathing"),Map.entry("speed","speed"),Map.entry("resist","resistance"),Map.entry("slowfall","slow_falling"),Map.entry("fire","fire_resistance"),Map.entry("absorb","absorption"),Map.entry("night","night_vision"),Map.entry("invisible","invisibility"),Map.entry("strength","strength"),Map.entry("haste","haste"));
 public static void effect(LivingEntity p,String id,int ticks,int amp){MobEffect e=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft",EFFECTS.getOrDefault(id,id)));if(e!=null)p.m_7292_(new MobEffectInstance(e,ticks,amp));}
 public static boolean isBottle(Catalog.Recipe r){return r!=null&&(r.school().equals("bio")||r.school().equals("matter"));}
 public static int charge(ItemStack s){return s.m_41784_().m_128441_("Charge")?s.m_41784_().m_128451_("Charge"):4;}
 static boolean drain(ItemStack s){int c=charge(s);if(c<=0)return false;s.m_41784_().m_128405_("Charge",c-1);return true;}
 static boolean ready(ServerPlayer p,String k,int ticks){long now=p.m_9236_().m_46467_();PlayerSystemData d=CraftState.data(p);if(d.value("craft.cd."+k,0)>now)return false;d.setValue("craft.cd."+k,now+ticks);return true;}
 public static boolean consume(ServerPlayer p,String item,int count){Item i=ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.contains(":")?item:"minecraft:"+item));int n=0;for(int k=0;k<36;k++)if(p.m_150109_().m_8020_(k).m_41720_()==i)n+=p.m_150109_().m_8020_(k).m_41613_();if(n<count)return false;for(int k=0;k<36&&count>0;k++){ItemStack s=p.m_150109_().m_8020_(k);if(s.m_41720_()==i){int take=Math.min(s.m_41613_(),count);s.m_41774_(take);count-=take;}}return true;}
 static double max(ServerPlayer p,StatType t){return StatCalculator.maxStat(t,CraftState.data(p),TemplateRegistry.get(),CoreConfig.get());}
 static void restore(ServerPlayer p,StatType t,double part){PlayerSystemData d=CraftState.data(p);double m=max(p,t);switch(t){case HP->d.setHp(Math.min(m,d.hp()+m*part));case MP->d.setMp(Math.min(m,d.mp()+m*part));case SP->d.setSp(Math.min(m,d.sp()+m*part));default->{}}SystemDataManager.markDirty(p);}
 public static void use(ServerPlayer p,ItemStack s,Catalog.Recipe r){
  if(!CraftState.data(p).isActivated()){CraftSessions.say(p,"此旧版主动功能需要已激活的系统；不是成品品级限制。");return;}
  PlayerSystemData d=CraftState.data(p);String e=r.effect();
  if(r.role().equals("pharma")){drug(p,s,r);return;}
  if(e.equals("adapter")){if(d.value("craft.adapted",0)>0)return;d.setValue("craft.adapted",1);s.m_41774_(1);CraftSessions.say(p,"本体适配完成；瓶罐可在物品栏提供被动。");SystemDataManager.markDirty(p);return;}
  if(isBottle(r)){if(p.m_6144_()){s.m_41784_().m_128379_("Passive",!s.m_41784_().m_128471_("Passive"));CraftSessions.say(p,"瓶罐被动："+s.m_41784_().m_128471_("Passive"));return;}if(charge(s)>0&&ready(p,"bottle",160)&&drain(s))effect(p,e,(10+r.rank()*2)*20,0);return;}
  ItemStack off=p.m_21206_();Catalog.Recipe bottle=CraftItems.recipe(off);
  if(e.equals("charger")){if(bottle!=null&&isBottle(bottle)&&charge(off)<4&&consume(p,"redstone",2)){off.m_41784_().m_128405_("Charge",charge(off)+1);CraftSessions.say(p,"瓶罐储能+1。");}return;}
  if(e.equals("emitter")){if(bottle!=null&&isBottle(bottle)&&CraftState.rank(p)>=bottle.rank()&&charge(off)>0){LivingEntity target=target(p,8);if(target!=null&&ready(p,"emitter",60)&&drain(off)){target.m_6469_(p.m_269291_().m_269075_(p),2+bottle.rank());effect(p,bottle.effect(),60,0);}}return;}
  if(!r.role().equals("smith"))return;
  LivingEntity target=target(p,4);if(target==null)return;
  if(e.equals("tech")){if(s.m_41784_().m_128451_("Pulse")<3||!ready(p,"weapon",80))return;s.m_41784_().m_128405_("Pulse",0);target.m_6469_(p.m_269291_().m_269075_(p),3+r.rank());}
  else if(e.equals("magic")){double cost=max(p,StatType.MP)*.08;if(d.mp()<cost||!ready(p,"weapon",80))return;d.setMp(d.mp()-cost);target.m_6469_(p.m_269291_().m_269390_(p,p),3+r.rank());SystemDataManager.markDirty(p);}
  else if(ready(p,"weapon",80)){Vec3 v=p.m_20182_().m_82546_(target.m_20182_());target.m_147240_(1.2,v.f_82479_,v.f_82481_);effect(target,"slowness",40,1);}
 }
 static LivingEntity target(ServerPlayer p,double reach){return p.m_9236_().m_6443_(LivingEntity.class,p.m_20191_().m_82400_(reach),e->e!=p&&e.m_6084_()&&p.m_142582_(e)&&p.m_20270_(e)<=reach&&p.m_20154_().m_82526_(e.m_20182_().m_82546_(p.m_20182_()).m_82541_())>.6).stream().min(Comparator.comparingDouble(e->p.m_20270_(e))).orElse(null);}
 public static void hit(ServerPlayer p,ItemStack s,Catalog.Recipe r,LivingEntity target){String e=r.effect();if(e.equals("saber")){ItemStack b=p.m_21206_();Catalog.Recipe br=CraftItems.recipe(b);if(br!=null&&isBottle(br)&&CraftState.rank(p)>=br.rank()&&charge(b)>0&&ready(p,"saber",40)&&drain(b))effect(p,br.effect(),80,0);return;}if(e.equals("tech")){s.m_41784_().m_128405_("Pulse",Math.min(3,s.m_41784_().m_128451_("Pulse")+1));}else if(e.equals("magic"))effect(target,"slowness",25,0);else if(e.equals("odd")){String key="mark."+p.m_20148_();long now=p.m_9236_().m_46467_();if(target.getPersistentData().m_128454_(key)>now){effect(target,"slowness",40,2);target.getPersistentData().m_128473_(key);}else target.getPersistentData().m_128356_(key,now+100);}}
 static void drug(ServerPlayer p,ItemStack s,Catalog.Recipe r){PlayerSystemData d=CraftState.data(p);String e=r.effect(),school=r.school();long now=p.m_9236_().m_46467_();if(d.value("craft.cd.drug",0)>now)return;
  if(e.equals("mp")&&d.mp()>=max(p,StatType.MP)||e.equals("sp")&&d.sp()>=max(p,StatType.SP)||e.equals("aid")&&!school.equals("odd")&&d.hp()>=max(p,StatType.HP)&&p.m_21223_()>=p.m_21233_())return;
  if(e.startsWith("perm_")){String k="craft.perm."+e.substring(5);if(d.value(k,0)>=.149)return;d.setValue(k,Math.min(.15,d.value(k,0)+.05));}
  else if(e.startsWith("ability_")){String k="craft.ability."+e.substring(8);if(d.value(k,0)>0)return;d.setValue(k,1);CraftSessions.say(p,"已永久学会能力；工坊中选择，按O发动。");}
  else if(e.equals("aid")){if(school.equals("magic")){d.setValue("craft.regen.hp",10);effect(p,"regeneration",200,0);}else if(school.equals("odd")){effect(p,"absorb",600,1);if(d.hp()<max(p,StatType.HP)*.5)restore(p,StatType.HP,.15);}else{restore(p,StatType.HP,.2);p.m_5634_(4);}}
  else if(e.equals("mp")){if(school.equals("magic"))d.setValue("craft.regen.mp",5);else restore(p,StatType.MP,school.equals("odd")?(d.mp()<max(p,StatType.MP)*.25?.3:.1):.2);}
  else if(e.equals("sp")){if(school.equals("magic")){d.setValue("craft.regen.sp",5);effect(p,"slowfall",100,0);}else{restore(p,StatType.SP,school.equals("odd")?.2:.3);if(school.equals("odd"))effect(p,"speed",200,0);}}
  else if(e.equals("buff"))effect(p,school.equals("tech")?"haste":school.equals("magic")?"resist":"jump",600,0);
  d.setValue("craft.cd.drug",now+300);s.m_41774_(1);SystemDataManager.markDirty(p);
 }
 public static void power(ServerPlayer p){PlayerSystemData d=CraftState.data(p);int i=(int)d.value("craft.selected",0);if(i<0||i>=9||d.value("craft.ability."+POWERS[i],0)<1)return;String id=POWERS[i];double cost=max(p,StatType.SP)*.1;if(d.sp()<cost||!ready(p,"power",600))return;d.setSp(d.sp()-cost);
  switch(id){case "tech_7"->effect(p,"speed",100,2);case "tech_8"->effect(p,"night",600,0);case "tech_9"->effect(p,"absorb",200,2);case "magic_7"->{effect(p,"jump",200,2);effect(p,"slowfall",200,0);}case "magic_8"->{for(MobEffectInstance e:new ArrayList<>(p.m_21220_()))if(!e.m_19544_().m_19486_())p.m_21195_(e.m_19544_());}case "magic_9"->{Vec3 dir=p.m_20154_();for(int n=5;n>=1;n--){Vec3 pos=p.m_20182_().m_82549_(dir.m_82490_(n));if(p.m_9236_().m_45756_(p,p.m_20191_().m_82386_(dir.f_82479_*n,dir.f_82480_*n,dir.f_82481_*n))){p.m_6021_(pos.f_82479_,pos.f_82480_,pos.f_82481_);break;}}}case "odd_7"->effect(p,"water",600,0);case "odd_8"->effect(p,"invisible",120,0);case "odd_9"->{restore(p,StatType.HP,.15);p.m_5634_(4);}default->{}}
  SystemDataManager.markDirty(p);
 }
 public static void upgrade(ServerPlayer p){ItemStack s=p.m_21205_();Catalog.Recipe r=CraftItems.recipe(s);if(r==null||!r.role().equals("smith"))return;int u=s.m_41784_().m_128451_("Upgrade");if(u>=3||!BuildSystem.known(p,r))return;if(consume(p,"amethyst_shard",(u+1)*4)){s.m_41784_().m_128405_("Upgrade",u+1);CraftSessions.say(p,"武器强化至 +"+(u+1)+"。每级基础攻击+0.75。");}}
 public static void tick(ServerPlayer p){if(!CraftState.data(p).isActivated())return;PlayerSystemData d=CraftState.data(p);for(StatType t:new StatType[]{StatType.HP,StatType.MP,StatType.SP}){String k="craft.regen."+t.name().toLowerCase(Locale.ROOT);double n=d.value(k,0);if(n>0){restore(p,t,t==StatType.HP?.02:t==StatType.MP?.04:.06);d.setValue(k,n-1);}}
 }
}
