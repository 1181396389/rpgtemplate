package com.rpgtemplate.forge.production;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.production.CraftMath;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
public final class CraftState {
 public static PlayerSystemData data(Player p){return SystemDataManager.raw(p);}
 public static int rank(Player p){return CraftMath.rank(data(p).value("craft.rank",1));}
 public static int equipped(Player p,String role){String id=data(p).loadedTemplateId();if(id==null)return 0;String prefix="rpgtemplate:"+role+"_grade_";if(!id.startsWith(prefix))return 0;try{return Math.max(0,Math.min(9,Integer.parseInt(id.substring(prefix.length()))));}catch(NumberFormatException e){return 0;}}
 public static int knowledge(Player p,String role){return (int)data(p).value("craft.knowledge."+role,0);}
 public static void learn(Player p){if(p instanceof net.minecraft.server.level.ServerPlayer sp)BuildSystem.ensureProfessions(sp);}
 public static void gain(Player p,double amount){int r=rank(p);if(r<9&&amount>0){data(p).setValue("craft.progress",Math.min(CraftMath.needed(r),data(p).value("craft.progress",0)+amount));SystemDataManager.markDirty(p);}}
 public static boolean advance(Player p){int r=rank(p);if(r>=9||data(p).value("craft.progress",0)<CraftMath.needed(r))return false;data(p).setValue("craft.rank",r+1);data(p).setValue("craft.progress",0);SystemDataManager.markDirty(p);return true;}
 public static CompoundTag storage(Player p){CompoundTag all=p.getPersistentData();if(!all.m_128441_(Player.PERSISTED_NBT_TAG))all.m_128365_(Player.PERSISTED_NBT_TAG,new CompoundTag());CompoundTag persist=all.m_128469_(Player.PERSISTED_NBT_TAG);if(!persist.m_128441_("rpgProduction"))persist.m_128365_("rpgProduction",new CompoundTag());return persist.m_128469_("rpgProduction");}
 public static double repeatedGain(Player p,String id,int recipeRank){long day=p.m_9236_().m_46467_()/24000;PlayerSystemData d=data(p);String key="craft.repeat."+id;if(d.value(key+".day",-1)!=day){d.setValue(key+".day",day);d.setValue(key,0);}double count=d.value(key,0);d.setValue(key,count+1);double factor=count<5?1:count<15?.25:0;return (5+recipeRank*3)*factor*(recipeRank<rank(p)-1?.2:1);}
}
