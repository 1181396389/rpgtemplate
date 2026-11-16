package com.rpgtemplate.core.template;

import java.util.Locale;

public enum TemplateType {
    PHYSICAL,
    MAGICAL,
    BALANCED;

    public static TemplateType parse(String var0) {
        String var1 = var0.trim().toUpperCase(Locale.ROOT);
        switch (var1) {
            case "体力":
                return PHYSICAL;
            case "魔力":
                return MAGICAL;
            case "均衡":
                return BALANCED;
            default:
                return valueOf(var1);
        }
    }
}
