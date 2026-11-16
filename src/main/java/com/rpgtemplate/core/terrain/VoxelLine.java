package com.rpgtemplate.core.terrain;

import java.util.ArrayList;
import java.util.List;

public final class VoxelLine {
    private VoxelLine() {
    }

    public static List<VoxelLine.Cell> cells(double var0, double var2, double var4, double var6, double var8, double var10) {
        ArrayList var12 = new ArrayList();
        int var13 = (int)Math.floor(var0);
        int var14 = (int)Math.floor(var2);
        int var15 = (int)Math.floor(var4);
        int var16 = (int)Math.floor(var6);
        int var17 = (int)Math.floor(var8);
        int var18 = (int)Math.floor(var10);
        int var19 = Double.compare(var6, var0);
        int var20 = Double.compare(var8, var2);
        int var21 = Double.compare(var10, var4);
        double var22 = var6 - var0;
        double var24 = var8 - var2;
        double var26 = var10 - var4;
        double var28 = first(var0, var13, var19, var22);
        double var30 = first(var2, var14, var20, var24);
        double var32 = first(var4, var15, var21, var26);
        double var34 = var19 == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / var22);
        double var36 = var20 == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / var24);
        double var38 = var21 == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / var26);
        var12.add(new VoxelLine.Cell(var13, var14, var15));
        int var40 = Math.abs(var16 - var13) + Math.abs(var17 - var14) + Math.abs(var18 - var15) + 4;

        while ((var13 != var16 || var14 != var17 || var15 != var18) && var40-- > 0) {
            double var41 = Math.min(var28, Math.min(var30, var32));
            if (var41 > 1.000000001) {
                break;
            }

            boolean var43 = var28 <= var41 + 1.0E-10;
            boolean var44 = var30 <= var41 + 1.0E-10;
            boolean var45 = var32 <= var41 + 1.0E-10;

            for (int var46 = 1; var46 < 8; var46++) {
                if (((var46 & 1) == 0 || var43) && ((var46 & 2) == 0 || var44) && ((var46 & 4) == 0 || var45)) {
                    var12.add(
                        new VoxelLine.Cell(
                            var13 + ((var46 & 1) != 0 ? var19 : 0), var14 + ((var46 & 2) != 0 ? var20 : 0), var15 + ((var46 & 4) != 0 ? var21 : 0)
                        )
                    );
                }
            }

            if (var43) {
                var13 += var19;
                var28 += var34;
            }

            if (var44) {
                var14 += var20;
                var30 += var36;
            }

            if (var45) {
                var15 += var21;
                var32 += var38;
            }
        }

        return var12;
    }

    private static double first(double var0, int var2, int var3, double var4) {
        return var3 == 0 ? Double.POSITIVE_INFINITY : ((var3 > 0 ? (double)var2 + 1.0 : (double)var2) - var0) / var4;
    }

    public static record Cell(int x, int y, int z) {
    }
}
