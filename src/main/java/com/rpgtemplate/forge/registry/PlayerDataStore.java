package com.rpgtemplate.forge.registry;

import com.rpgtemplate.core.data.PlayerSystemData;

public final class PlayerDataStore {
    private PlayerSystemData data = new PlayerSystemData();

    public PlayerSystemData get() {
        return this.data;
    }

    public void load(String var1) {
        this.data = PlayerSystemData.fromJsonString(var1);
    }

    public void copyFrom(PlayerDataStore var1) {
        this.data = var1.data.copy();
    }
}
