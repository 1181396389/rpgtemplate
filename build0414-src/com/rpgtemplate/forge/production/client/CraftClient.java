package com.rpgtemplate.forge.production.client;
import com.google.gson.*;import com.rpgtemplate.forge.production.CraftNet;import net.minecraft.client.Minecraft;
public final class CraftClient {
 public static JsonObject state=new JsonObject();public static long received;
 public static void receive(CraftNet.Snapshot s){state=JsonParser.parseString(s.json()).getAsJsonObject();received=System.nanoTime();if(Minecraft.m_91087_().f_91080_ instanceof CraftScreen screen)screen.acknowledge();if(s.open()&&!(Minecraft.m_91087_().f_91080_ instanceof CraftScreen))Minecraft.m_91087_().m_91152_(new CraftScreen());}
 public static String notice(){String json=string("noticeJson");if(!json.isEmpty())try{var message=net.minecraft.network.chat.Component.Serializer.m_130701_(json);if(message!=null)return message.getString();}catch(Exception ignored){}return string("notice");}
 public static int number(String k){return state.has(k)?state.get(k).getAsInt():0;}public static String string(String k){return state.has(k)?state.get(k).getAsString():"";}public static boolean bool(String k){return state.has(k)&&state.get(k).getAsBoolean();}
}
