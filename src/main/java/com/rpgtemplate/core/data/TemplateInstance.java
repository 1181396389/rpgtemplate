package com.rpgtemplate.core.data;

public final class TemplateInstance {
    private String templateId;
    private int level;
    private double xp;

    public TemplateInstance(String var1, int var2, double var3) {
        this.templateId = var1;
        this.level = var2;
        this.xp = var3;
    }

    public String templateId() {
        return this.templateId;
    }

    public int level() {
        return this.level;
    }

    public double xp() {
        return this.xp;
    }

    public void setTemplateId(String var1) {
        this.templateId = var1;
    }

    public void setLevel(int var1) {
        this.level = var1;
    }

    public void setXp(double var1) {
        this.xp = var1;
    }

    public TemplateInstance copy() {
        return new TemplateInstance(this.templateId, this.level, this.xp);
    }
}
