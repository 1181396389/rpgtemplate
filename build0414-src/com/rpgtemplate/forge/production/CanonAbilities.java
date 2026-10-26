package com.rpgtemplate.forge.production;
import com.rpgtemplate.core.production.*;import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.*;import net.minecraft.world.entity.player.Player;import net.minecraft.world.effect.*;import net.minecraft.world.phys.*;import java.util.*;
/** Explicit MC adaptations; forms not demonstrated on-screen are not claimed as exact canon choreography. */
public final class CanonAbilities {
 public static final Set<String> EXPANDED=Set.of("ninja","comic","panda","rocket","fire_engine","lion","vacuum","dragon","lock","pirate","train","light","phoenix","robot","smartphone","bear","television","unicorn","eraser","rose","helicopter","watch","beetle","camera","spider","refrigerator","dog","microphone","santa","cake","tiger","ufo","whale","jet","deer","pyramid","giraffe","fan","penguin","skateboard","shark","bee","rhino","hairdryer","ghost","engine");
 static double scale(ServerPlayer p){return BuildBottles.scale(p);}
 static void effect(LivingEntity p,String id,int ticks,int level){if(p!=null)CraftEffects.effect(p,id,ticks,level);}
 static void ward(ServerPlayer p,int ticks){BuildBottles.active(p,"shell",ticks);}
 static void strike(ServerPlayer p,double range,double damage){BuildBottles.hit(p,BuildBottles.target(p,range),damage*scale(p));}
 static void field(ServerPlayer p,String effect,int ticks,int level,double range){for(var e:BuildBottles.nearby(p,range,true))if(!(e instanceof Player other)||p.m_7099_(other))effect(e,effect,ticks,level);}
 static void wind(ServerPlayer p,double range,double force){for(var e:BuildBottles.nearby(p,range,true))if(p.m_20154_().m_82526_(e.m_20182_().m_82546_(p.m_20182_()).m_82541_())>.45)BuildBottles.impulse(e,p.m_20154_().m_82490_(force));BuildBottles.particles(p,"cloud",p.m_20185_(),p.m_20186_()+1,p.m_20189_(),18);}
 static void heal(ServerPlayer p,double fraction){if(CraftState.data(p).isActivated()){var d=CraftState.data(p);double max=BuildSystem.maxHp(p);d.setHp(Math.min(max,(d.hp()<0?max:d.hp())+max*fraction));com.rpgtemplate.forge.system.SystemDataManager.markDirty(p);}else p.m_21153_(Math.min(p.m_21233_(),p.m_21223_()+(float)(p.m_21233_()*fraction)));}
 static void purge(LivingEntity target,boolean harmful){if(target==null)return;for(var effect:new ArrayList<>(target.m_21220_()))if(effect.m_19544_().m_19486_()!=harmful){target.m_21195_(effect.m_19544_());break;}}
 public static boolean single(ServerPlayer p,String id){double s=scale(p);var t=BuildBottles.target(p,14);int duration=(int)(100*s);switch(id){
 case "ninja"->{effect(p,"invisibility",duration,0);effect(p,"speed",duration,1);BuildBottles.propel(p,.4*s,.1);}
 case "comic"->{BuildBottles.smoke(p,40);for(var mob:BuildBottles.nearby(p,6,true))if(mob instanceof Mob m)m.m_6710_(null);effect(p,"invisibility",60,0);}
 case "panda"->{BuildBottles.active(p,"fist",100);ward(p,60);}
 case "rocket"->{BuildBottles.impulse(p,p.m_20154_().m_82490_(.9*s).m_82520_(0,.25,0));effect(p,"slowfall",140,0);}
 case "fire_engine"->{p.m_20095_();effect(p,"fire_resistance",duration,0);wind(p,6,.35);for(var e:BuildBottles.nearby(p,5,true))e.m_20095_();}
 case "lion"->{field(p,"weakness",duration,0,5);BuildBottles.propel(p,.6*s,.15);strike(p,4,5);}
 case "vacuum"->{BuildBottles.pullItems(p,8);for(var e:BuildBottles.nearby(p,6,true))BuildBottles.impulse(e,p.m_20182_().m_82546_(e.m_20182_()).m_82541_().m_82490_(.35));}
 case "dragon"->{BuildBottles.active(p,"fist",100);strike(p,7,6);if(t!=null)t.m_20254_(3);BuildBottles.particles(p,"soul_fire_flame",p.m_20185_(),p.m_20186_()+1,p.m_20189_(),24);}
 case "lock"->{effect(t,"slowness",duration,3);BuildBottles.mark(p,t,duration);}
 case "pirate"->{strike(p,5,6);effect(p,"water",duration,0);}
 case "train"->{BuildBottles.propel(p,1.0*s,.08);strike(p,5,7);}
 case "light"->{effect(p,"night",240,0);field(p,"blindness",40,0,4);BuildBottles.particles(p,"end_rod",p.m_20185_(),p.m_20186_()+1,p.m_20189_(),30);}
 case "phoenix"->{effect(p,"fire_resistance",duration,0);effect(p,"slowfall",140,0);BuildBottles.propel(p,.45*s,.5);heal(p,.025*s);}
 case "robot"->{BuildBottles.active(p,"crystalFist",100);ward(p,60);}
 case "smartphone"->{BuildBottles.mark(p,t,240);BuildBottles.scan(p,false,true);}
 case "bear"->{ward(p,duration);BuildBottles.active(p,"fist",100);}
 case "television"->{BuildBottles.scan(p,false,false);effect(t,"weakness",duration,0);}
 case "unicorn"->{BuildBottles.propel(p,.7*s,.15);strike(p,5,7);}
 case "eraser"->{purge(p,true);if(t!=null&&(!(t instanceof Player other)||p.m_7099_(other)))purge(t,false);}
 case "rose"->{effect(t,"slowness",duration,2);BuildBottles.hit(p,t,3*s);}
 case "helicopter"->{BuildBottles.impulse(p,new Vec3(0,.7*s,0));effect(p,"slowfall",180,0);wind(p,4,.3);}
 case "watch"->field(p,"slowness",60,3,5);
 case "beetle"->{ward(p,duration);if(t!=null){BuildBottles.impulse(t,new Vec3(0,.5*s,0));BuildBottles.hit(p,t,4*s);}}
 case "camera"->{BuildBottles.mark(p,t,300);effect(t,"blindness",35,0);}
 case "spider"->{BuildBottles.active(p,"climb",160);effect(t,"slowness",duration,2);}
 case "refrigerator"->{field(p,"slowness",duration,1,5);wind(p,4,.15);}
 case "dog"->{BuildBottles.scan(p,true,true);BuildBottles.mark(p,t,200);}
 case "microphone"->{wind(p,7,.7*s);field(p,"weakness",60,0,5);}
 case "santa"->{heal(p,.03*s);effect(p,"absorption",160,0);}
 case "cake"->{heal(p,.06*s);effect(p,"regeneration",80,0);}
 case "tiger"->{BuildBottles.propel(p,.7*s,.2);BuildBottles.active(p,"fist",80);}
 case "ufo"->{effect(t,"levitation",30,0);effect(p,"slowfall",140,0);BuildBottles.pullItems(p,6);}
 case "whale"->{effect(p,"water",200,0);wind(p,6,.5*s);strike(p,6,5);}
 case "jet"->{BuildBottles.impulse(p,p.m_20154_().m_82490_(1.1*s));effect(p,"slowfall",160,0);}
 case "deer"->{BuildBottles.propel(p,.55*s,.6*s);strike(p,4,5);}
 case "pyramid"->{ward(p,140);wind(p,3,.45);BuildBottles.particles(p,"enchant",p.m_20185_(),p.m_20186_()+1,p.m_20189_(),30);}
 case "giraffe"->{strike(p,9,4);BuildBottles.mark(p,t,100);}
 case "fan"->wind(p,9,.9*s);
 case "penguin"->{effect(p,"speed",duration,1);effect(p,"water",duration,0);BuildBottles.propel(p,.7*s,.05);}
 case "skateboard"->{if(p.m_20096_())BuildBottles.propel(p,1.0*s,.08);effect(p,"jump",100,0);}
 case "shark"->{if(p.m_20069_()){effect(p,"dolphins_grace",100,0);BuildBottles.impulse(p,p.m_20154_().m_82490_(.8*s));}strike(p,5,7);}
 case "bee"->{BuildBottles.propel(p,.35*s,.35);effect(p,"slowfall",120,0);effect(t,"poison",60,0);}
 case "rhino"->{ward(p,60);BuildBottles.propel(p,.85*s,.08);strike(p,5,8);}
 case "hairdryer"->{wind(p,7,.65*s);if(t!=null)t.m_20254_(2);}
 case "ghost"->{effect(p,"invisibility",duration,0);effect(p,"slowfall",duration,0);}
 case "engine"->{effect(p,"speed",duration,1);BuildBottles.active(p,"fist",100);}
 default->{return false;}}
 return true;}
 public static void combo(ServerPlayer p,BuildCatalog.Bottle a,BuildCatalog.Bottle b){int pair=BuildCatalog.canonicalPair(a);if(pair<3){BuildBottles.combo(p,pair);return;}BuildBottles.single(p,a.id());BuildBottles.single(p,b.id());double s=scale(p);var t=BuildBottles.target(p,14);switch(pair){
 case 3->{effect(p,"invisibility",160,0);BuildBottles.active(p,"airControl",100);}
 case 4->{BuildBottles.active(p,"crystalFist",120);effect(p,"slowfall",200,0);}
 case 5->{BuildBottles.active(p,"thorns",180);effect(p,"fire_resistance",240,0);}
 case 6->{field(p,"weakness",140,1,6);strike(p,5,4);}
 case 7->{effect(t,"slowness",100,4);if(t!=null){t.m_20254_(5);BuildBottles.hit(p,t,5*s);}}
 case 8->{BuildBottles.active(p,"burst",60);BuildSystem.body(p).m_128405_("shots",4);}
 case 9->{BuildBottles.active(p,"inkTurn",160);BuildBottles.active(p,"climb",160);effect(t,"blindness",70,0);}
 case 10->{BuildBottles.active(p,"crystalFist",160);heal(p,.03*s);}
 case 11->{BuildBottles.active(p,"pounce",200);BuildBottles.active(p,"chase",200);}
 case 12->{ward(p,180);effect(t,"weakness",120,1);}
 case 13->{purge(p,true);strike(p,6,5);}
 case 14->{field(p,"slowness",120,2,6);effect(p,"slowfall",220,0);}
 case 15->{ward(p,160);field(p,"slowness",80,4,6);}
 case 16->{BuildBottles.mark(p,t,360);strike(p,8,6);}
 case 17->{field(p,"slowness",140,3,6);}
 case 18->{BuildBottles.mark(p,t,300);wind(p,9,.9*s);}
 case 19->{heal(p,.05*s);effect(p,"absorption",240,1);}
 case 20->{effect(t,"levitation",50,0);BuildBottles.active(p,"crystalFist",100);}
 case 21->{wind(p,9,1.1*s);strike(p,9,8);}
 case 22->{ward(p,200);BuildBottles.active(p,"fist",100);}
 case 23->{wind(p,12,1.0*s);strike(p,11,5);}
 case 24->{effect(p,"speed",180,2);effect(p,"jump",140,1);}
 case 25->{BuildBottles.mark(p,t,200);BuildBottles.active(p,"pounce",160);}
 case 26->{effect(p,"dolphins_grace",160,0);effect(t,"poison",100,0);BuildBottles.scan(p,false,true);}
 case 27->{BuildBottles.active(p,"crystalFist",140);wind(p,6,.8*s);}
 case 28->{BuildBottles.pullItems(p,10);for(var e:BuildBottles.nearby(p,6,true))if(BuildBottles.metal(e))effect(e,"slowness",100,2);}
 case 29->{BuildBottles.active(p,"airControl",180);BuildBottles.scan(p,false,true);effect(p,"speed",180,1);}
 default->throw new IllegalArgumentException("Missing canonical combination");}}
}
