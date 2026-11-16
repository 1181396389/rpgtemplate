package com.rpgtemplate.core.template;

import com.google.gson.JsonObject;

public record TraitEntry(String type, String name, String description, JsonObject params, boolean remove) {
    public TraitEntry(String var1, String var2, String var3, JsonObject var4) {
        this(var1, var2, var3, var4, false);
    }
}
