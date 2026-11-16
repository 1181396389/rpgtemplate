package com.rpgtemplate.core.terrain;

public final class WedgeGeometry {
    public final double ox;
    public final double oy;
    public final double oz;
    public final double fx;
    public final double fy;
    public final double fz;
    public final double sx;
    public final double sy;
    public final double sz;
    public final double nx;
    public final double ny;
    public final double nz;
    public final double range;
    public final double baseHalf;
    public final double spread;
    public final double maxHalf;
    public final double halfThickness;

    public WedgeGeometry(double[] var1, double[] var2, double[] var3, double var4, double var6, double var8, double var10, double var12) {
        if (var1.length == 3 && var2.length == 3 && var3.length == 3) {
            for (double var17 : new double[]{var1[0], var1[1], var1[2], var2[0], var2[1], var2[2], var3[0], var3[1], var3[2], var4, var6, var8, var10, var12}) {
                if (!Double.isFinite(var17)) {
                    throw new IllegalArgumentException("non-finite wedge parameter");
                }
            }

            if (!(var4 <= 0.0) && !(var6 < 0.0) && !(var8 < 0.0) && !(var10 <= 0.0) && !(var12 <= 0.0)) {
                this.ox = var1[0];
                this.oy = var1[1];
                this.oz = var1[2];
                double var26 = Math.sqrt(var2[0] * var2[0] + var2[1] * var2[1] + var2[2] * var2[2]);
                if (var26 < 1.0E-9) {
                    throw new IllegalArgumentException("zero forward vector");
                } else {
                    this.fx = var2[0] / var26;
                    this.fy = var2[1] / var26;
                    this.fz = var2[2] / var26;
                    double var27 = var3[0] * this.fx + var3[1] * this.fy + var3[2] * this.fz;
                    double var18 = var3[0] - var27 * this.fx;
                    double var20 = var3[1] - var27 * this.fy;
                    double var22 = var3[2] - var27 * this.fz;
                    double var24 = Math.sqrt(var18 * var18 + var20 * var20 + var22 * var22);
                    if (var24 < 1.0E-6) {
                        throw new IllegalArgumentException("side vector parallel to forward");
                    } else {
                        this.sx = var18 / var24;
                        this.sy = var20 / var24;
                        this.sz = var22 / var24;
                        this.nx = this.fy * this.sz - this.fz * this.sy;
                        this.ny = this.fz * this.sx - this.fx * this.sz;
                        this.nz = this.fx * this.sy - this.fy * this.sx;
                        this.range = var4;
                        this.baseHalf = var6;
                        this.spread = var8;
                        this.maxHalf = Math.max(var10, var6);
                        this.halfThickness = var12 * 0.5;
                    }
                }
            } else {
                throw new IllegalArgumentException("wedge sizes must be positive");
            }
        } else {
            throw new IllegalArgumentException("vectors must have 3 components");
        }
    }

    public double halfWidth(double var1) {
        double var3 = Math.max(0.0, Math.min(this.range, var1));
        return Math.min(this.maxHalf, this.baseHalf + var3 * this.spread);
    }

    public double depth(double var1, double var3, double var5) {
        return (var1 - this.ox) * this.fx + (var3 - this.oy) * this.fy + (var5 - this.oz) * this.fz;
    }

    public double lateral(double var1, double var3, double var5) {
        return (var1 - this.ox) * this.sx + (var3 - this.oy) * this.sy + (var5 - this.oz) * this.sz;
    }

    public double offset(double var1, double var3, double var5) {
        return (var1 - this.ox) * this.nx + (var3 - this.oy) * this.ny + (var5 - this.oz) * this.nz;
    }

    public boolean intersects(double var1, double var3, double var5, double var7, double var9, double var11) {
        double var13 = Math.max(0.0, var7);
        double var15 = this.offset(var1, var3, var5);
        if (Math.abs(var15) > this.halfThickness + var13) {
            return false;
        } else {
            double var17 = this.depth(var1, var3, var5);
            return !(var17 < Math.max(-var13, var9 - var13)) && !(var17 > Math.min(this.range + var13, var11 + var13))
                ? Math.abs(this.lateral(var1, var3, var5)) <= this.halfWidth(var17) + var13
                : false;
        }
    }

    public double[] bandBounds(double var1, double var3, double var5) {
        double var7 = Math.max(0.0, Math.min(this.range, var1));
        double var9 = Math.max(var7, Math.min(this.range, var3));
        double[] var11 = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (double var15 : new double[]{var7, var9}) {
            double var17 = this.halfWidth(var15);

            for (byte var19 = -1; var19 <= 1; var19 += 2) {
                for (byte var20 = -1; var20 <= 1; var20 += 2) {
                    double var21 = this.ox + this.fx * var15 + this.sx * var17 * (double)var19 + this.nx * this.halfThickness * (double)var20;
                    double var23 = this.oy + this.fy * var15 + this.sy * var17 * (double)var19 + this.ny * this.halfThickness * (double)var20;
                    double var25 = this.oz + this.fz * var15 + this.sz * var17 * (double)var19 + this.nz * this.halfThickness * (double)var20;
                    var11[0] = Math.min(var11[0], var21);
                    var11[1] = Math.min(var11[1], var23);
                    var11[2] = Math.min(var11[2], var25);
                    var11[3] = Math.max(var11[3], var21);
                    var11[4] = Math.max(var11[4], var23);
                    var11[5] = Math.max(var11[5], var25);
                }
            }
        }

        for (int var27 = 0; var27 < 3; var27++) {
            var11[var27] -= var5;
            var11[var27 + 3] = var11[var27 + 3] + var5;
        }

        return var11;
    }

    public double area() {
        double var1 = this.spread <= 0.0 ? this.range : Math.min(this.range, Math.max(0.0, (this.maxHalf - this.baseHalf) / this.spread));
        double var3 = var1 * (2.0 * this.baseHalf + this.spread * var1);
        double var5 = (this.range - var1) * 2.0 * this.maxHalf;
        return var3 + var5;
    }
}
