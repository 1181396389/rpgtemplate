package com.rpgtemplate.core.production;
import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;import com.google.gson.*;
public final class BuildCatalog {
 public record Bottle(String id,String name,String kind,int pair,String material,String skill,String description){}
 public static final List<Bottle> ALL,LEGACY,CANON;public static final Map<String,Bottle> BY_ID;
 static {try(var in=BuildCatalog.class.getResourceAsStream("/data/rpgtemplate/production/build_bottles.json")){var a=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();List<Bottle> list=new ArrayList<>();Map<String,Bottle> map=new LinkedHashMap<>();for(var e:a){var j=e.getAsJsonObject();var b=new Bottle(j.get("id").getAsString(),j.get("name").getAsString(),j.get("kind").getAsString(),j.get("pair").getAsInt(),j.get("material").getAsString(),j.get("skill").getAsString(),j.get("description").getAsString());list.add(b);map.put(b.id,b);}LEGACY=List.copyOf(list);List<Bottle> canon=new ArrayList<>();for(var c:CanonicalCatalog.ALL){Bottle b=map.get(c.id());if(b==null){b=new Bottle(c.id(),c.name().replace("满装瓶罐",""),c.kind(),c.pair(),c.material(),c.name().replace("满装瓶罐","")+"能力",c.effectPlan());list.add(b);map.put(b.id(),b);}canon.add(b);}CANON=List.copyOf(canon);ALL=CANON;map.keySet().removeIf(id->!CanonicalCatalog.BY_ID.containsKey(id));BY_ID=Map.copyOf(map);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 public static boolean canonical(Bottle b){return b!=null&&CanonicalCatalog.BY_ID.containsKey(b.id());}
 public static int canonicalPair(Bottle b){var c=CanonicalCatalog.BY_ID.get(b.id());return c==null?-1:c.pair();}
 public static boolean best(Bottle a,Bottle b){if(a==null||b==null||a.kind.equals(b.kind))return false;if(canonical(a)&&canonical(b)&&CanonicalCatalog.best(a.id(),b.id()))return true;return false;}
}
