package com.example.edgedashanalytics.advanced.coordinator;

import java.util.HashMap;

public class IPLists {
    public static HashMap<String, String> s22, splab, p6, oneplus, lineage2;

    public static void createIPList() {
        final int s22IP = 94;
        final String h = "192.168";
        final String hs = h + "." + s22IP + ".";
        {
            s22 = new HashMap<>();
            s22.put("self", "127.0.0.1");
            s22.put("lineage", hs + 32);
            s22.put("oneplus", hs + 172);
            s22.put("pixel6", hs + 79);
            s22.put("oppo", hs + 230);
            s22.put("pixel5", hs + 145);
            s22.put("lineage2", hs + 72);
            s22.put("s22", "127.0.0.1");
        } // s22
        {
            splab = new HashMap<>();
            splab.put("self", "127.0.0.1");
            splab.put("lineage", "192.168.0.105");
            splab.put("oneplus", "192.168.0.106");
            splab.put("pixel6", "192.168.0.103");
            splab.put("oppo", "192.168.0.104");
            splab.put("pixel5", "192.168.0.101");
            splab.put("lineage2", "192.168.0.107");
            splab.put("s22", "192.168.0.108");
        } // splab

        final int p6IP = 228;
        {
            p6 = new HashMap<>();
            p6.put("self", "127.0.0.1");
            p6.put("lineage", "192.168." + p6IP + ".163");
            p6.put("oneplus", "192.168." + p6IP + ".191");
            p6.put("pixel6", "192.168." + p6IP + ".85");
            p6.put("oppo", "192.168." + p6IP + ".20");
            p6.put("pixel5", "192.168." + p6IP + ".201");
            p6.put("lineage2", "192.168." + p6IP + ".213");
            p6.put("s22", "192.168." + p6IP + ".6");
        } // pixel6

        final int oneplusIP = 194;
        final String ho = h + "." + oneplusIP + ".";
        {
            oneplus = new HashMap<>();
            oneplus.put("self", "127.0.0.1");
            oneplus.put("lineage", ho + 143);
            oneplus.put("oneplus", "127.0.0.1");
            oneplus.put("oppo", ho + 87);
            oneplus.put("pixel5", ho + 193);
            oneplus.put("lineage2", ho + 253);
            oneplus.put("s22", ho + 204);
            oneplus.put("pixel6", ho + 14);
        } // oneplus

        final int lineage2IP = 141;
        final String hl = h + "." + lineage2IP + ".";
        {
            lineage2 = new HashMap<>();
            lineage2.put("self", "127.0.0.1");
            lineage2.put("lineage", hl + 253);
            lineage2.put("oneplus", hl + 178);
            lineage2.put("oppo", hl + 14);
            lineage2.put("pixel5", hl + 55);
            lineage2.put("lineage2", "127.0.0.1");
            lineage2.put("s22", hl + 78);
            lineage2.put("pixel6", hl + 122);
        } // lineage2
    }

    public static HashMap<String, String> getByName(String name) {
        return name.equals("oneplus") ? oneplus : name.equals("lineage2") ? lineage2 :
                name.equals("s22") ? s22 : null;
    }
}
