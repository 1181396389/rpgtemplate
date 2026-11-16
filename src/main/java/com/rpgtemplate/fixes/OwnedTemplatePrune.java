package com.rpgtemplate.fixes;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class OwnedTemplatePrune {
    private OwnedTemplatePrune() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            TemplateRegistry var8 = TemplateRegistry.get();
            if (var8 != null && !var8.all().isEmpty()) {
                PlayerSystemData var3 = SystemDataManager.get(var1);
                List var4 = ownedList(var3);
                if (var4 != null) {
                    boolean var5 = false;
                    Iterator var6 = var4.iterator();

                    while (var6.hasNext()) {
                        TemplateInstance var7 = (TemplateInstance)var6.next();
                        if (var8.find(var7.templateId()).isEmpty()) {
                            var6.remove();
                            var5 = true;
                            if (var7.templateId().equals(var3.loadedTemplateId())) {
                                var3.setLoadedTemplateId(null);
                            }

                            if (var7.templateId().equals(var3.newestTemplateId())) {
                                var3.setNewestTemplateId(null);
                            }
                        }
                    }

                    if (var5) {
                        SystemDataManager.markDirty(var1);
                    }
                }
            }
        }
    }

    private static List<TemplateInstance> ownedList(PlayerSystemData var0) {
        try {
            Field var1 = PlayerSystemData.class.getDeclaredField("templates");
            var1.setAccessible(true);
            return (List<TemplateInstance>)var1.get(var0);
        } catch (ReflectiveOperationException var2) {
            return null;
        }
    }
}
