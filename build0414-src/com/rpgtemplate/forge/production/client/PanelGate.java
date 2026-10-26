package com.rpgtemplate.forge.production.client;
import com.rpgtemplate.forge.client.ClientSystemData;
import com.rpgtemplate.core.production.CaptureRules;
public final class PanelGate {
 public static boolean allowed(){return CaptureRules.handPanel(ClientSystemData.get().loadedTemplateId());}
 public static boolean hide(String label){return "独立手槽 / 防御状态".equals(label)&&!allowed();}
 public static int extraHeight(){return allowed()?28:0;}
}
