package swcnoops.server.game;

import java.util.List;

public class LimitedEditionItemData implements JoeData {
    private String uid;
    private List<String> audienceConditions;
    private String crateId;
    private int crystals;
    private int credits;
    private int materials;
    private int contraband;
    private String faction;
    private String startDate;
    private String endDate;
    private String storeTab;

    @Override
    public String getUid() {
        return uid;
    }

    public List<String> getAudienceConditions() {
        return audienceConditions;
    }

    public String getCrateId() {
        return crateId;
    }

    public int getCrystals() {
        return crystals;
    }

    public int getCredits() {
        return credits;
    }

    public int getMaterials() {
        return materials;
    }

    public int getContraband() {
        return contraband;
    }

    public String getFaction() {
        return faction;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getStoreTab() {
        return storeTab;
    }
}
