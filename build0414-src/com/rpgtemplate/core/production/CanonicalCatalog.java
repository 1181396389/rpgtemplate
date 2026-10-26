package com.rpgtemplate.core.production;
import java.util.*;import java.nio.charset.StandardCharsets;import com.google.gson.*;
/** Separate canonical definitions so old 20-slot inventories retain their original order. No item registration here. */
public final class CanonicalCatalog {
 public record Bottle(String id,String name,String kind,int pair,String region,String material,String effectPlan){}
 public static final List<Bottle> ALL;public static final Map<String,Bottle> BY_ID;
 static {try(var in=CanonicalCatalog.class.getResourceAsStream("/data/rpgtemplate/production/canonical_bottles.json")){var array=JsonParser.parseReader(new java.io.InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();List<Bottle> rows=new ArrayList<>();Map<String,Bottle> map=new LinkedHashMap<>();for(var element:array){var j=element.getAsJsonObject();var b=new Bottle(j.get("id").getAsString(),j.get("name").getAsString(),j.get("kind").getAsString(),j.get("pair").getAsInt(),j.get("region").getAsString(),j.get("research_sample").getAsString(),j.get("effect_plan").getAsString());if(map.put(b.id(),b)!=null)throw new IllegalArgumentException("Duplicate canonical bottle");rows.add(b);}if(rows.size()!=60)throw new IllegalArgumentException("Canonical roster must have 60 bottles");for(int pair=0;pair<30;pair++){final int index=pair;for(String kind:List.of("organic","inorganic"))if(rows.stream().filter(b->b.pair()==index&&b.kind().equals(kind)).count()!=1)throw new IllegalArgumentException("Malformed canonical pairing");}ALL=List.copyOf(rows);BY_ID=Map.copyOf(map);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 public static boolean best(String a,String b){var x=BY_ID.get(a);var y=BY_ID.get(b);return x!=null&&y!=null&&!x.kind().equals(y.kind())&&x.pair()==y.pair();}
}
