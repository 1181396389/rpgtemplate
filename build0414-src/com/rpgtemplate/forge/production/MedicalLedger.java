package com.rpgtemplate.forge.production;
import net.minecraft.server.MinecraftServer;import net.minecraft.nbt.*;import net.minecraft.world.level.saveddata.SavedData;
/** World-side receipts for participants ending an operation while the other participant is offline. */
public final class MedicalLedger extends SavedData {
 private CompoundTag pending=new CompoundTag(),ended=new CompoundTag();
 public static MedicalLedger get(MinecraftServer server){return server.m_129783_().m_8895_().m_164861_(MedicalLedger::load,MedicalLedger::new,"rpgtemplate_medical_receipts");}
 public static MedicalLedger load(CompoundTag t){MedicalLedger l=new MedicalLedger();l.pending=t.m_128469_("pending");l.ended=t.m_128469_("ended");return l;}
 @Override public CompoundTag m_7176_(CompoundTag t){t.m_128365_("pending",pending);t.m_128365_("ended",ended);return t;}
 public void queue(String patient,String kind,String token){CompoundTag t=new CompoundTag();t.m_128359_("kind",kind);t.m_128359_("token",token);pending.m_128365_(patient,t);m_77762_();}
 public CompoundTag take(String patient){CompoundTag t=pending.m_128469_(patient);pending.m_128473_(patient);m_77762_();return t;}
 public void end(String token){if(!token.isEmpty()){ended.m_128379_(token,true);m_77762_();}}
 public boolean consumeEnd(String token){if(!ended.m_128471_(token))return false;ended.m_128473_(token);m_77762_();return true;}
}
