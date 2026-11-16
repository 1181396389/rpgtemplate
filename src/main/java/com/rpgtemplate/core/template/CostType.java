package com.rpgtemplate.core.template;

import java.util.Locale;

public enum CostType {
    MP,
    SP,
    NONE;

    public static CostType parse(String var0) {
        return valueOf(var0.trim().toUpperCase(Locale.ROOT));
    }
}
