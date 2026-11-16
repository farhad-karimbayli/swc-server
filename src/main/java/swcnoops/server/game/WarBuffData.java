package swcnoops.server.game;

public class WarBuffData implements JoeData {
    private String uid;
    private String buffBaseName;
    private String buffIcon;
    private String buffType;
    private String buildingBuffUid;
    private String troopBuffUid;
    private String planetId;
    private String empireBattlesByLevel;
    private String rebelBattlesByLevel;
    private String masterEmpireBuildingUid;
    private String masterRebelBuildingUid;
    private String masterNeutralBuildingUid;

    @Override
    public String getUid() {
        return uid;
    }

    public String getBuffBaseName() {
        return buffBaseName;
    }

    public String getBuffIcon() {
        return buffIcon;
    }

    public String getBuffType() {
        return buffType;
    }

    public String getBuildingBuffUid() {
        return buildingBuffUid;
    }

    public String getTroopBuffUid() {
        return troopBuffUid;
    }

    public String getPlanetId() {
        return planetId;
    }

    public String getEmpireBattlesByLevel() {
        return empireBattlesByLevel;
    }

    public String getRebelBattlesByLevel() {
        return rebelBattlesByLevel;
    }

    public String getMasterEmpireBuildingUid() {
        return masterEmpireBuildingUid;
    }

    public String getMasterRebelBuildingUid() {
        return masterRebelBuildingUid;
    }

    public String getMasterNeutralBuildingUid() {
        return masterNeutralBuildingUid;
    }
}
