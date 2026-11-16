package com.rpgtemplate.core.terrain;

public final class PlaneRaster {
    private final WedgeGeometry g;
    private final double band;
    private final double lateralMargin;
    private final int bands;
    private final int axisA;
    private final int axisU;
    private final int axisV;
    private final double na;
    private final double nu;
    private final double nv;
    private final double c;
    private int bandIndex;
    private boolean bandOpen;
    private double t0;
    private double t1;
    private int u0;
    private int u1;
    private int v0;
    private int v1;
    private int cu;
    private int cv;
    private boolean columnOpen;
    private int colU;
    private int colV;
    private int a;
    private int aEnd;
    private boolean finished;
    private long emitted;

    public PlaneRaster(WedgeGeometry var1, double var2, double var4) {
        if (var2 > 0.0 && Double.isFinite(var2)) {
            this.g = var1;
            this.band = var2;
            this.lateralMargin = Math.max(0.0, var4);
            this.bands = Math.max(1, (int)Math.ceil(var1.range / var2));
            double[] var6 = new double[]{var1.nx, var1.ny, var1.nz};
            int var7 = 0;

            for (int var8 = 1; var8 < 3; var8++) {
                if (Math.abs(var6[var8]) > Math.abs(var6[var7])) {
                    var7 = var8;
                }
            }

            this.axisA = var7;
            this.axisU = (var7 + 1) % 3;
            this.axisV = (var7 + 2) % 3;
            this.na = var6[this.axisA];
            this.nu = var6[this.axisU];
            this.nv = var6[this.axisV];
            this.c = var1.nx * var1.ox + var1.ny * var1.oy + var1.nz * var1.oz;
        } else {
            throw new IllegalArgumentException("band size must be positive");
        }
    }

    public boolean finished() {
        return this.finished;
    }

    public long emitted() {
        return this.emitted;
    }

    public double depth() {
        return this.finished ? this.g.range : Math.min(this.g.range, (double)this.bandIndex * this.band);
    }

    public VoxelLine.Cell next(double var1) {
        while (true) {
            if (this.columnOpen) {
                if (this.a <= this.aEnd) {
                    VoxelLine.Cell var5 = this.cell(this.colU, this.colV, this.a++);
                    this.emitted++;
                    return var5;
                }

                this.columnOpen = false;
            }

            if (this.finished) {
                return null;
            }

            if (!this.bandOpen) {
                if (this.bandIndex >= this.bands) {
                    this.finished = true;
                    return null;
                }

                double var3 = (double)this.bandIndex * this.band;
                if (var3 > var1 + 1.0E-9) {
                    return null;
                }

                this.openBand(var3);
            }

            if (!this.advanceColumn()) {
                this.bandOpen = false;
                this.bandIndex++;
            }
        }
    }

    private void openBand(double var1) {
        this.t0 = var1;
        this.t1 = Math.min(this.g.range, var1 + this.band);
        double var3 = Double.MAX_VALUE;
        double var5 = -Double.MAX_VALUE;
        double var7 = Double.MAX_VALUE;
        double var9 = -Double.MAX_VALUE;

        for (double var14 : new double[]{this.t0, this.t1}) {
            double var16 = this.g.halfWidth(var14) + this.lateralMargin;

            for (byte var18 = -1; var18 <= 1; var18 += 2) {
                double[] var19 = new double[]{
                    this.g.ox + this.g.fx * var14 + this.g.sx * var16 * (double)var18,
                    this.g.oy + this.g.fy * var14 + this.g.sy * var16 * (double)var18,
                    this.g.oz + this.g.fz * var14 + this.g.sz * var16 * (double)var18
                };
                var3 = Math.min(var3, var19[this.axisU]);
                var5 = Math.max(var5, var19[this.axisU]);
                var7 = Math.min(var7, var19[this.axisV]);
                var9 = Math.max(var9, var19[this.axisV]);
            }
        }

        this.u0 = (int)Math.floor(var3) - 1;
        this.u1 = (int)Math.floor(var5) + 1;
        this.v0 = (int)Math.floor(var7) - 1;
        this.v1 = (int)Math.floor(var9) + 1;
        this.cu = this.u0;
        this.cv = this.v0 - 1;
        this.bandOpen = true;
    }

    private boolean advanceColumn() {
        boolean var1 = this.bandIndex == this.bands - 1;

        double[] var2;
        double var3;
        do {
            this.cv++;
            if (this.cv > this.v1) {
                this.cv = this.v0;
                this.cu++;
                if (this.cu > this.u1) {
                    return false;
                }
            }

            double var5 = (double)this.cu + 0.5;
            double var7 = (double)this.cv + 0.5;
            double var9 = (this.c - this.nu * var5 - this.nv * var7) / this.na;
            var2 = new double[3];
            var2[this.axisU] = var5;
            var2[this.axisV] = var7;
            var2[this.axisA] = var9;
            var3 = this.g.depth(var2[0], var2[1], var2[2]);
        } while (
            var3 < this.t0
                || var3 < 0.0
                || (var1 ? var3 > this.g.range : var3 >= this.t1)
                || Math.abs(this.g.lateral(var2[0], var2[1], var2[2])) > this.g.halfWidth(var3) + this.lateralMargin
        );

        double var19 = this.nu * (double)this.cu + this.nv * (double)this.cv;
        double var20 = var19 + Math.min(0.0, this.nu) + Math.min(0.0, this.nv);
        double var21 = var19 + Math.max(0.0, this.nu) + Math.max(0.0, this.nv);
        double var11 = this.g.halfThickness;
        double var13;
        double var15;
        if (this.na > 0.0) {
            var13 = (this.c - var11 - var21) / this.na;
            var15 = (this.c + var11 - var20) / this.na;
        } else {
            var13 = (this.c + var11 - var20) / this.na;
            var15 = (this.c - var11 - var21) / this.na;
        }

        int var17 = (int)Math.floor(var13);
        int var18 = (int)Math.ceil(var15) - 1;
        if (var18 < var17) {
            var18 = var17;
        }

        this.colU = this.cu;
        this.colV = this.cv;
        this.a = var17;
        this.aEnd = var18;
        this.columnOpen = true;
        return true;
    }

    private VoxelLine.Cell cell(int var1, int var2, int var3) {
        int[] var4 = new int[3];
        var4[this.axisU] = var1;
        var4[this.axisV] = var2;
        var4[this.axisA] = var3;
        return new VoxelLine.Cell(var4[0], var4[1], var4[2]);
    }
}
