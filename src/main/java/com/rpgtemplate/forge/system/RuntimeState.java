package com.rpgtemplate.forge.system;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeState {
    private static final Map<UUID, RuntimeState.State> STATES = new ConcurrentHashMap<>();

    private RuntimeState() {
    }

    public static RuntimeState.State of(UUID var0) {
        return STATES.computeIfAbsent(var0, var0x -> new RuntimeState.State());
    }

    public static void forget(UUID var0) {
        STATES.remove(var0);
    }

    public static final class State {
        public long lastHurtTick = -4611686018427387904L;
        public long lastSpUseTick = -4611686018427387904L;
        public boolean exhausted;
        public long lastChunk = Long.MIN_VALUE;
        public String lastFoodId;
        public int foodStreak;
        public boolean pendingSoulHit;
    }
}
