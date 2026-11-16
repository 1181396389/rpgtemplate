package com.rpgtemplate.core.ui;

public final class UiLayout {
    private UiLayout() {
    }

    public static UiLayout.Rect panel(int var0, int var1) {
        int var2 = Math.max(1, Math.min(740, var0 - 16));
        int var3 = Math.max(1, Math.min(440, var1 - 16));
        return new UiLayout.Rect((var0 - var2) / 2, (var1 - var3) / 2, var2, var3);
    }

    public static int scrollMax(int var0, int var1) {
        return Math.max(0, var0 - Math.max(0, var1));
    }

    public static double scroll(double var0, int var2) {
        return Math.max(0.0, Math.min((double)Math.max(0, var2), var0));
    }

    public static float hudScale(float var0, int var1, int var2, int var3, int var4) {
        return Math.max(0.1F, Math.min(var0, Math.min(((float)var1 - 12.0F) / (float)Math.max(1, var3), ((float)var2 - 12.0F) / (float)Math.max(1, var4))));
    }

    public static int anchor(int var0, int var1, int var2) {
        return Math.max(4, Math.min(var0, Math.max(4, var2 - var1 - 4)));
    }

    public static float progress(double var0, double var2) {
        return (float)(var2 > 0.0 ? Math.max(0.0, Math.min(1.0, var0 / var2)) : 0.0);
    }

    public static int color(int var0) {
        return switch (var0 & 16777215) {
            case 131072 -> 14811136;
            case 262144 -> 14811136;
            case 397852 -> 15265527;
            case 461327 -> 4416667;
            case 462107 -> 2576821;
            case 527378 -> 4220317;
            case 529441 -> 2386614;
            case 593696 -> 15660026;
            case 857897 -> 15659768;
            case 997962 -> 14545656;
            case 1053980 -> 15659768;
            case 1054251 -> 3493281;
            case 1055276 -> 15857146;
            case 1120810 -> 16777215;
            case 1121586 -> 16777215;
            case 1185158 -> 1053876;
            case 1317410 -> 16777215;
            case 1320246 -> 15397112;
            case 1514531 -> 16777215;
            case 1515047 -> 16777215;
            case 1731200 -> 947344;
            case 1780806 -> 15857146;
            case 2107442 -> 16119803;
            case 2242131 -> 15397112;
            case 2308171 -> 14082798;
            case 2309973 -> 14082798;
            case 2506066 -> 3958670;
            case 2699837 -> 13687268;
            case 2765630 -> 13687268;
            case 2896960 -> 13884648;
            case 2902881 -> 3828364;
            case 2911335 -> 3510152;
            case 3292232 -> 13358561;
            case 3492456 -> 13884648;
            case 3560298 -> 12109785;
            case 3561069 -> 12109785;
            case 4743800 -> 4481144;
            case 4810622 -> 4350329;
            case 4892927 -> 1920728;
            case 5628159 -> 223649;
            case 5846678 -> 15988475;
            case 6477263 -> 1874322;
            case 6541239 -> 2200955;
            case 6673603 -> 1939332;
            case 7045535 -> 6583435;
            case 7240072 -> 6583435;
            case 7263427 -> 2069119;
            case 7264451 -> 1807484;
            case 7330231 -> 1741933;
            case 7526876 -> 1610132;
            case 7588341 -> 2450411;
            case 8033448 -> 5991298;
            case 8191898 -> 1264928;
            case 8234701 -> 3894949;
            case 8249549 -> 2313284;
            case 8364208 -> 6583435;
            case 8425632 -> 5266277;
            case 8632264 -> 3107711;
            case 8633324 -> 1914186;
            case 8828356 -> 3369339;
            case 8886695 -> 5991298;
            case 9023425 -> 3564151;
            case 9099960 -> 292951;
            case 9240426 -> 2315285;
            case 9240496 -> 1197602;
            case 9282740 -> 4674921;
            case 9300434 -> 1918267;
            case 9353704 -> 1978437;
            case 9362676 -> 223649;
            case 9491908 -> 2573373;
            case 9547199 -> 4674921;
            case 9547451 -> 4087919;
            case 9549249 -> 3761266;
            case 9553080 -> 3568741;
            case 9871791 -> 5991298;
            case 10481919 -> 223649;
            case 10532045 -> 4674921;
            case 10533013 -> 5202247;
            case 10533023 -> 4873545;
            case 11058376 -> 4674921;
            case 11442932 -> 8266446;
            case 11641035 -> 3287869;
            case 11717852 -> 2240053;
            case 11982054 -> 1846067;
            case 12045534 -> 2107955;
            case 12098276 -> 2891584;
            case 12110552 -> 2305074;
            case 12111064 -> 4674921;
            case 12111581 -> 2173746;
            case 12114152 -> 1780531;
            case 12166263 -> 8349752;
            case 12178662 -> 1845554;
            case 12230634 -> 2628414;
            case 12375267 -> 1911089;
            case 12378841 -> 1978668;
            case 12428734 -> 3879227;
            case 12559095 -> 8141549;
            case 12619007 -> 2757190;
            case 12826079 -> 2498612;
            case 12952697 -> 8609327;
            case 12953073 -> 2496315;
            case 13020798 -> 8480047;
            case 13098215 -> 1779244;
            case 13477223 -> 9594660;
            case 13543526 -> 9660963;
            case 13626341 -> 1583141;
            case 13637656 -> 13305093;
            case 13756154 -> 990250;
            case 13758184 -> 1452068;
            case 13926023 -> 8987181;
            case 13956588 -> 1189665;
            case 13994632 -> 4663336;
            case 14018277 -> 1778980;
            case 14083567 -> 1449765;
            case 14272499 -> 1971245;
            case 14413039 -> 1450275;
            case 14467990 -> 4273955;
            case 14596998 -> 4733475;
            case 14800633 -> 1511207;
            case 14806773 -> 1186849;
            case 14807541 -> 1187361;
            case 14932475 -> 1379622;
            case 14993565 -> 11817737;
            case 15004905 -> 1253399;
            case 15128502 -> 3353628;
            case 15136511 -> 988970;
            case 15198701 -> 988970;
            case 15267071 -> 988970;
            case 15387039 -> 11817737;
            case 15397887 -> 988970;
            case 15517065 -> 11817737;
            case 15520934 -> 11817737;
            case 15596031 -> 988970;
            case 15660287 -> 988970;
            case 15728880 -> 13435085;
            case 15793407 -> 988970;
            case 15827355 -> 14753096;
            case 15835810 -> 4003607;
            case 16728128 -> 11665408;
            case 16730714 -> 11403279;
            case 16738922 -> 5510421;
            case 16743034 -> 5116691;
            case 16743152 -> 5116743;
            case 16746392 -> 14427686;
            case 16746751 -> 4723272;
            case 16756832 -> 5781270;
            case 16760928 -> 5783062;
            case 16769126 -> 5654805;
            case 24643590 -> 13173760;
            case 55125078 -> 8075155;
            case 117724469 -> 53630;
            case 123241281 -> 5472315;
            case 151624032 -> 180341;
            case 154543734 -> 4271001;
            case 273883205 -> 9909372;
            case 289679666 -> 13878473;
            case 307597461 -> 4621450;
            case 357697844 -> 12848248;
            case 359006855 -> 9830599;
            case 360870741 -> 8811600;
            case 376726418 -> 6115952;
            default -> var0 & 16777215;
        };
    }

    public static record Rect(int x, int y, int w, int h) {
        public boolean contains(double var1, double var3) {
            return var1 >= (double)this.x && var1 < (double)(this.x + this.w) && var3 >= (double)this.y && var3 < (double)(this.y + this.h);
        }
    }
}
