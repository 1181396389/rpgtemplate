package com.rpgtemplate.forge.skill;

public interface Skill {
    default boolean holdable() {
        return false;
    }

    default boolean bypassCooldown(SkillContext var1) {
        return false;
    }

    void onPress(SkillContext var1, HeldState var2);

    default void onHoldTick(SkillContext var1, HeldState var2) {
    }

    default void onRelease(SkillContext var1, HeldState var2) {
    }
}
