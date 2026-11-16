package com.rpgtemplate.core.terrain;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class GridRaster {
    private final double[] origin;
    private final double[] forward;
    private final double[] right;
    private final double[] up;
    private final double size;
    private final double range;
    private final int lines;
    private final int slices;
    private int slice;
    private int line;
    private int axis;
    private Iterator<VoxelLine.Cell> current = Collections.emptyIterator();

    public GridRaster(double[] var1, double[] var2, double[] var3, double[] var4, double var5, int var7, double var8) {
        this.origin = (double[])var1.clone();
        this.forward = (double[])var2.clone();
        this.right = (double[])var3.clone();
        this.up = (double[])var4.clone();
        this.size = var5;
        this.lines = Math.max(2, var7);
        this.range = var8;
        this.slices = Math.max(1, (int)Math.ceil(var8 / 0.5));
    }

    public boolean finished() {
        return this.slice > this.slices && !this.current.hasNext();
    }

    public double depth() {
        return Math.min(this.range, this.range * (double)this.slice / (double)this.slices);
    }

    public VoxelLine.Cell next(double var1) {
        if (this.current.hasNext()) {
            return this.current.next();
        } else if (this.slice <= this.slices && !(this.depth() > var1 + 1.0E-9)) {
            double var3 = this.depth();
            double var5 = -this.size * 0.5 + this.size * (double)this.line / ((double)this.lines - 1.0);
            double[] var7 = new double[3];
            double[] var8 = new double[3];

            for (int var9 = 0; var9 < 3; var9++) {
                double var10 = this.origin[var9] + this.forward[var9] * var3 + (this.axis == 0 ? this.up[var9] : this.right[var9]) * var5;
                double var12 = this.axis == 0 ? this.right[var9] : this.up[var9];
                var7[var9] = var10 - var12 * this.size * 0.5;
                var8[var9] = var10 + var12 * this.size * 0.5;
            }

            List var14 = VoxelLine.cells(var7[0], var7[1], var7[2], var8[0], var8[1], var8[2]);
            this.current = var14.iterator();
            if (++this.axis == 2) {
                this.axis = 0;
                if (++this.line == this.lines) {
                    this.line = 0;
                    this.slice++;
                }
            }

            return this.current.hasNext() ? this.current.next() : null;
        } else {
            return null;
        }
    }
}
