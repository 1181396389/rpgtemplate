package com.rpgtemplate.forge.api.event;

import com.rpgtemplate.core.template.TemplateDefinition;
import net.minecraft.server.level.ServerPlayer;

public class TemplateDrawEvent extends RpgPlayerEvent {
    private final TemplateDefinition template;

    public TemplateDrawEvent(ServerPlayer var1, TemplateDefinition var2) {
        super(var1);
        this.template = var2;
    }

    public TemplateDefinition getTemplate() {
        return this.template;
    }
}
