package com.rpgtemplate.core.production;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class Catalog {
 public static final List<Recipe> ALL;
 public static final Map<String,Recipe> BY_ID;
 static {
  try(InputStream in=Catalog.class.getResourceAsStream("/data/rpgtemplate/production/catalog.json")){
   if(in==null)throw new IllegalStateException("Production catalog missing");
   JsonArray array=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();
   List<Recipe> list=new ArrayList<>();Map<String,Recipe> map=new LinkedHashMap<>();
   for(JsonElement e:array){JsonObject o=e.getAsJsonObject();List<Ingredient> costs=new ArrayList<>();for(JsonElement ce:o.getAsJsonArray("costs")){JsonObject c=ce.getAsJsonObject();costs.add(new Ingredient(c.get("item").getAsString(),c.get("count").getAsInt(),c.has("sample")?c.get("sample").getAsString():""));}
    Recipe r=new Recipe(o.get("id").getAsString(),o.get("name").getAsString(),o.get("role").getAsString(),o.get("school").getAsString(),o.get("rank").getAsInt(),o.get("effect").getAsString(),o.get("description").getAsString(),o.has("source")?o.get("source").getAsString():"",List.copyOf(costs),o.has("aux")&&o.get("aux").getAsBoolean());
    if(r.rank<1||r.rank>9||map.put(r.id,r)!=null)throw new IllegalArgumentException("Invalid/duplicate recipe");list.add(r);
   }ALL=List.copyOf(list);BY_ID=Collections.unmodifiableMap(map);
  }catch(IOException e){throw new ExceptionInInitializerError(e);}
 }
 public static String roleName(String role){return switch(role){case "build"->"满装研究者";case "smith"->"锻造大师";default->"药理大师";};}
 public static String schoolName(String s){return switch(s){case "tech"->"科技";case "magic"->"魔法";case "odd"->"特异";case "organic"->"有机物";case "inorganic"->"无机物";case "medical"->"医疗";case "enhancer"->"强化装置";case "fabricator"->"收集合成";case "bio"->"生物";case "matter"->"无机物";default->"辅助";};}
 public record Ingredient(String item,int count,String sample){}
 public record Recipe(String id,String name,String role,String school,int rank,String effect,String description,String source,List<Ingredient> costs,boolean aux){}
}
