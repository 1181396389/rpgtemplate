package com.rpgtemplate.core.production;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.stats.StatType;
public final class CraftMath {
 public static final String[] GRADES={"N","R","SR","SSR","UR","EX","EX+","LR","MR"};
 public static final int[] NEED={100,250,500,900,1500,2400,3600,5200};
 public static int rank(double n){return (int)Math.max(1,Math.min(9,n));}
 public static int needed(int rank){return rank>=9?0:NEED[Math.max(0,rank-1)];}
 public static double base(int body,int recipe,int equipped){return Math.max(5,Math.min(100,65+Math.max(0,Math.min(9,equipped))*2+Math.max(-12,Math.min(12,(body-recipe)*4))));}
 public static double chance(double base,double score){return Math.max(5,Math.min(100,base+Math.max(0,Math.min(1,score))*25));}
 public static double timing(int elapsed,int target,int width){return Math.max(0,1-Math.abs(elapsed-target)/(double)Math.max(1,width));}
 public static double enhanceStat(double original,StatType stat,PlayerSystemData d,CoreConfig config){
  if(stat==StatType.HP&&d.value("capture.active",0)>0)return Math.max(1,Math.min(config.statCap,d.value("capture.hp",1)));
  String key=stat==StatType.HP?"hp":stat==StatType.MP?"mp":stat==StatType.SP?"sp":"";
  double bonus=key.isEmpty()?0:Math.max(0,Math.min(.15,d.value("craft.perm."+key,0)));
  return Math.min(config.statCap,original*(1+bonus));
 }
}
