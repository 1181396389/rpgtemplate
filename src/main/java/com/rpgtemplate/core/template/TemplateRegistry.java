package com.rpgtemplate.core.template;

import com.rpgtemplate.core.skill.KingSkillOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TemplateRegistry {
    private static final TemplateRegistry SERVER = new TemplateRegistry();
    private static final TemplateRegistry CLIENT = new TemplateRegistry();
    private volatile Map<String, TemplateDefinition> templates = Collections.emptyMap();
    private final ConcurrentHashMap<String, TemplateRegistry.Memo> memo = new ConcurrentHashMap<>();

    private <T> T memoized(String var1, Supplier<T> var2) {
        Map var3 = this.templates;
        TemplateRegistry.Memo var4 = this.memo.get(var1);
        if (var4 != null && var4.snapshot == var3) {
            return (T)var4.value;
        } else {
            Object var5 = var2.get();
            this.memo.put(var1, new TemplateRegistry.Memo(var3, var5));
            return (T)var5;
        }
    }

    public static TemplateRegistry get() {
        return SERVER;
    }

    public static TemplateRegistry client() {
        return CLIENT;
    }

    private TemplateRegistry() {
    }

    public List<String> replaceAll(Collection<TemplateDefinition> var1) {
        LinkedHashMap<String, TemplateDefinition> var2 = new LinkedHashMap<>();

        for (TemplateDefinition var4 : var1) {
            var2.put(var4.id(), var4);
        }

        ArrayList var7 = new ArrayList();

        for (TemplateDefinition var5 : var2.values()) {
            if (var5.parent() != null && !var2.containsKey(var5.parent())) {
                var7.add("模板 " + var5.id() + " 的前置模板不存在: " + var5.parent());
            }

            if (hasCycle(var5, var2)) {
                var7.add("模板 " + var5.id() + " 的进阶链存在循环");
            }

            if (var5.lineage() != null) {
                if (var5.parent() == null) {
                    var7.add("模板 " + var5.id() + " 声明了 lineage 但没有 parent，传承块将被忽略");
                } else if (var5.lineage().chance() <= 0.0 && var5.lineage().pity() <= 0) {
                    var7.add("模板 " + var5.id() + " 的传承成功率为 0 且无保底，永远无法获得");
                }
            }
        }

        LinkedHashMap<String, Integer> var9 = new LinkedHashMap<>();

        for (TemplateDefinition var6 : var2.values()) {
            if (var6.parent() != null && var6.lineage() == null) {
                var9.merge(var6.parent(), Integer.valueOf(1), Integer::sum);
            }
        }

        for (Entry<String, Integer> var12 : var9.entrySet()) {
            if ((Integer)var12.getValue() > 1) {
                var7.add("模板 " + (String)var12.getKey() + " 有 " + var12.getValue() + " 个进阶子模板，只有第一个可达；多分支请改用 lineage 传承抽取");
            }
        }

        this.templates = Collections.unmodifiableMap(var2);
        return var7;
    }

    public Optional<TemplateDefinition> find(String var1) {
        return Optional.ofNullable(var1 == null ? null : this.templates.get(var1));
    }

    public Collection<TemplateDefinition> all() {
        return this.templates.values();
    }

    public int size() {
        return this.templates.size();
    }

    public Optional<TemplateDefinition> advancementOf(String var1) {
        if (var1 == null) {
            return Optional.empty();
        } else {
            for (TemplateDefinition var3 : this.templates.values()) {
                if (var1.equals(var3.parent()) && var3.lineage() == null) {
                    return Optional.of(var3);
                }
            }

            return Optional.empty();
        }
    }

    public List<TemplateDefinition> childrenOf(String var1) {
        ArrayList var2 = new ArrayList();
        if (var1 != null) {
            for (TemplateDefinition var4 : this.templates.values()) {
                if (var1.equals(var4.parent())) {
                    var2.add(var4);
                }
            }
        }

        return var2;
    }

    public List<TemplateDefinition> lineageChildrenOf(String var1) {
        ArrayList var2 = new ArrayList();
        if (var1 != null) {
            for (TemplateDefinition var4 : this.templates.values()) {
                if (var1.equals(var4.parent()) && var4.lineage() != null) {
                    var2.add(var4);
                }
            }
        }

        return var2;
    }

    public String rootOf(String var1) {
        String var2 = var1;
        HashSet var3 = new HashSet();

        while (true) {
            TemplateDefinition var4 = this.templates.get(var2);
            if (var4 == null || var4.parent() == null || !var3.add(var2)) {
                return var2;
            }

            var2 = var4.parent();
        }
    }

    public List<TemplateDefinition> lineage(String var1) {
        ArrayList var2 = new ArrayList();
        HashSet var3 = new HashSet();
        String var4 = var1;

        while (var4 != null && var3.add(var4)) {
            TemplateDefinition var5 = this.templates.get(var4);
            if (var5 == null) {
                break;
            }

            var2.add(0, var5);
            var4 = var5.parent();
        }

        return var2;
    }

    public List<SkillEntry> effectiveSkills(String var1) {
        return var1 == null
            ? Collections.unmodifiableList(this.computeEffectiveSkills(null))
            : this.memoized("skills:" + var1, () -> Collections.unmodifiableList(this.computeEffectiveSkills(var1)));
    }

    private List<SkillEntry> computeEffectiveSkills(String var1) {
        LinkedHashMap<String, SkillEntry> var2 = new LinkedHashMap<>();

        for (TemplateDefinition var4 : this.lineage(var1)) {
            for (SkillEntry var6 : var4.skills()) {
                if (var6.remove()) {
                    var2.remove(var6.id());
                } else {
                    var2.put(var6.id(), var6);
                }
            }
        }

        SkillEntry var7 = (SkillEntry)var2.remove("rpgtemplate:domain_expansion");
        if (var7 != null) {
            SkillEntry var8 = (SkillEntry)var2.remove("rpgtemplate:possession");
            var2.put("rpgtemplate:domain_expansion", var7);
            if (var8 != null) {
                var2.put("rpgtemplate:possession", var8);
            }
        }

        return KingSkillOrder.appendDefenses(new ArrayList<>(var2.values()));
    }

    public List<TraitEntry> effectivePassives(String var1) {
        return var1 == null
            ? this.mergeTraits(null, TemplateDefinition::passives)
            : this.memoized("passives:" + var1, () -> Collections.unmodifiableList(this.mergeTraits(var1, TemplateDefinition::passives)));
    }

    public List<TraitEntry> effectiveWeaknesses(String var1) {
        return var1 == null
            ? this.mergeTraits(null, TemplateDefinition::weaknesses)
            : this.memoized("weaknesses:" + var1, () -> Collections.unmodifiableList(this.mergeTraits(var1, TemplateDefinition::weaknesses)));
    }

    public List<TraitEntry> effectiveNegativeStates(String var1) {
        return this.mergeTraits(var1, TemplateDefinition::negativeStates);
    }

    public List<TraitEntry> effectiveQuirks(String var1) {
        return this.mergeTraits(var1, TemplateDefinition::quirks);
    }

    private List<TraitEntry> mergeTraits(String var1, Function<TemplateDefinition, List<TraitEntry>> var2) {
        LinkedHashMap var3 = new LinkedHashMap();

        for (TemplateDefinition var5 : this.lineage(var1)) {
            for (TraitEntry var7 : var2.apply(var5)) {
                if (var7.remove()) {
                    var3.remove(var7.type());
                } else {
                    var3.put(var7.type(), var7);
                }
            }
        }

        return new ArrayList<>(var3.values());
    }

    private static boolean hasCycle(TemplateDefinition var0, Map<String, TemplateDefinition> var1) {
        HashSet var2 = new HashSet();
        TemplateDefinition var3 = var0;

        while (var3 != null && var3.parent() != null) {
            if (!var2.add(var3.id())) {
                return true;
            }

            var3 = (TemplateDefinition)var1.get(var3.parent());
        }

        return false;
    }

    private static final class Memo {
        final Map<String, TemplateDefinition> snapshot;
        final Object value;

        Memo(Map<String, TemplateDefinition> var1, Object var2) {
            this.snapshot = var1;
            this.value = var2;
        }
    }
}
