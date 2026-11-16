package swcnoops.server.game;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CrateSupplyScaleData implements JoeData {
    private String uid;
    @JsonProperty("HQ1")
    private int hq1;
    @JsonProperty("HQ2")
    private int hq2;
    @JsonProperty("HQ3")
    private int hq3;
    @JsonProperty("HQ4")
    private int hq4;
    @JsonProperty("HQ5")
    private int hq5;
    @JsonProperty("HQ6")
    private int hq6;
    @JsonProperty("HQ7")
    private int hq7;
    @JsonProperty("HQ8")
    private int hq8;
    @JsonProperty("HQ9")
    private int hq9;
    @JsonProperty("HQ10")
    private int hq10;
    @JsonProperty("HQ11")
    private int hq11;

    @Override
    public String getUid() {
        return uid;
    }

    public int getAmountForHq(int hqLevel) {
        switch (hqLevel) {
            case 1: return hq1 > 0 ? hq1 : hq2;
            case 2: return hq2;
            case 3: return hq3;
            case 4: return hq4;
            case 5: return hq5;
            case 6: return hq6;
            case 7: return hq7;
            case 8: return hq8;
            case 9: return hq9;
            case 10: return hq10;
            default: return hq11 > 0 ? hq11 : hq10;
        }
    }
}
