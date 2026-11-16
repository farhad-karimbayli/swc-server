package swcnoops.server.game;

import java.util.List;

public class CrateSupplyData implements JoeData {
    private String uid;
    private List<String> crateSupplyPoolUid;
    private String faction;
    private int minHQ;
    private int maxHQ;
    private int amount;
    private String rewardType;
    private String rewardUid;
    private String scalingUid;
    private List<String> planet;

    @Override
    public String getUid() {
        return uid;
    }

    public List<String> getCrateSupplyPoolUid() {
        return crateSupplyPoolUid;
    }

    public String getFaction() {
        return faction;
    }

    public int getMinHQ() {
        return minHQ;
    }

    public int getMaxHQ() {
        return maxHQ;
    }

    public int getAmount() {
        return amount;
    }

    public String getRewardType() {
        return rewardType;
    }

    public String getRewardUid() {
        return rewardUid;
    }

    public String getScalingUid() {
        return scalingUid;
    }

    public List<String> getPlanet() {
        return planet;
    }
}
