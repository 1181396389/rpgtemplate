package com.rpgtemplate.core.production;
public final class BuildRules {
 public static final int[] RESEARCH={2,4,6,8,10,14,18,20},MATCHES={0,0,1,2,3,4,6,8},MEDICAL={0,1,2,3,4,6,8,10};
 public record Cultivation(int level,double progress,double pending,double load){}
 public static Cultivation second(int level,double progress,double pending,double load){level=danger(level);load=Math.max(0,load-.25);if(level>=10)return new Cultivation(10,0,0,load);if(level>0&&pending>0){double absorbed=Math.min(1,pending);pending-=absorbed;progress+=absorbed;if(progress>=need(level)){progress-=need(level);level++;}}return new Cultivation(level,level>=10?0:progress,level>=10?0:pending,load);}
 public static int danger(int n){return Math.max(0,Math.min(10,n));}
 public static int need(int level){return level>=10?0:Math.max(1,level)*100;}
 public static double scale(int level){return .55+.095*Math.max(1,danger(level));}
 public static boolean advance(int grade,int research,int crafted,int pairs,int medical){return grade>=1&&grade<9&&research>=RESEARCH[grade-1]&&crafted>=RESEARCH[grade-1]&&pairs>=MATCHES[grade-1]&&medical>=MEDICAL[grade-1];}
 public static boolean sufficient(boolean surgery,double score){return Double.isFinite(score)&&score>=(surgery?.5:.25);}
 public static double chance(boolean surgery,int grade,double score){return Math.min(100,(surgery?65:80)+Math.max(0,Math.min(9,grade))*2+Math.max(0,Math.min(1,score))*25);}
}
