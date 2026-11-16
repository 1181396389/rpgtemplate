package com.rpgtemplate.forge.skill;

import com.rpgtemplate.forge.king.KingDefenseSkill;
import com.rpgtemplate.forge.possession.PossessionSkill;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SkillRegistry {
    private static final Map<String, Skill> SKILLS = new ConcurrentHashMap<>();

    private SkillRegistry() {
    }

    public static void register(String var0, Skill var1) {
        SKILLS.put(var0, var1);
    }

    public static Optional<Skill> get(String var0) {
        return Optional.ofNullable(SKILLS.get(var0));
    }

    static {
        register("rpgtemplate:domain_amplification", new KingDefenseSkill(false));
        register("rpgtemplate:hollow_wicker_basket", new KingDefenseSkill(true));
        register("rpgtemplate:dismantle", new DismantleSkill());
        register("rpgtemplate:cleave", new CleaveSkill());
        register("rpgtemplate:reverse_cursed_technique", new ReverseCursedTechniqueSkill());
        register("rpgtemplate:kamino", new KaminoSkill());
        register("rpgtemplate:domain_expansion", new DomainExpansionSkill());
        register("rpgtemplate:grid_slash", new GridSlashSkill());
        register("rpgtemplate:possession", new PossessionSkill());
    }
}
