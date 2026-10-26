package com.rpgtemplate.core.production;
import java.util.*;
public final class PersonalRules {
 public static int[] generate(Random rng){return generate(rng,10);}
 public static int[] generate(Random rng,int size){if(size<2||size>256)throw new IllegalArgumentException("pair pool size");int[] p=new int[size];do{for(int i=0;i<size;i++)p[i]=i;for(int i=size-1;i>0;i--){int j=rng.nextInt(i+1),v=p[i];p[i]=p[j];p[j]=v;}}while(!valid(p));return p;}
 public static boolean valid(int[] p){if(p.length<2)return false;boolean[] seen=new boolean[p.length];for(int i=0;i<p.length;i++){int v=p[i];if(v<0||v>=p.length||v==i||seen[v])return false;seen[v]=true;}return true;}
 public static <T> List<T> preferUnknown(List<T> all,java.util.function.Predicate<T> known){var missing=all.stream().filter(x->!known.test(x)).toList();return missing.isEmpty()?all:missing;}
 public static int weighted(List<Integer> weights,Random rng){int total=weights.stream().mapToInt(Integer::intValue).sum();if(total<=0)throw new IllegalArgumentException();int roll=rng.nextInt(total);for(int i=0;i<weights.size();i++){roll-=weights.get(i);if(roll<0)return i;}throw new AssertionError();}
}
