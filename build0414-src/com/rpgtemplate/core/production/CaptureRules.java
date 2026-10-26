package com.rpgtemplate.core.production;
public final class CaptureRules {
 public static boolean sampleable(double hp,double max){return Double.isFinite(hp)&&Double.isFinite(max)&&max>0&&hp>0&&hp/max<=.050000001;}
 public static double scale(int hazard){return .25+.075*Math.max(1,Math.min(10,hazard));}
 public static double stat(double value,int hazard,double min,double cap){return Double.isFinite(value)?Math.max(min,Math.min(cap,value*scale(hazard))):min;}
 public static double ratio(double hp,double max){return max>0&&Double.isFinite(hp)&&Double.isFinite(max)?Math.max(0,Math.min(1,hp/max)):0;}
 public static boolean handPanel(String id){return "rpgtemplate:sukuna_20_fingers".equals(id);}
 public static boolean pairValid(String organic,String inorganic){var a=BuildCatalog.BY_ID.get(organic);var b=BuildCatalog.BY_ID.get(inorganic);return a!=null&&b!=null&&a.kind().equals("organic")&&b.kind().equals("inorganic");}
}
