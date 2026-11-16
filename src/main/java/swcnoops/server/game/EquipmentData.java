package swcnoops.server.game;

import java.util.List;

public class EquipmentData implements JoeData {
    private String uid;
    private String equipmentID;
    private String faction;
    private int lvl;
    private int size;
    private int upgradeShards;
    private int upgradeTime;
    private List<String> effectUids;
    private List<String> planetIDs;
    private List<String> requirements;
    private List<String> skins;
    private boolean playerFacing;

    @Override
    public String getUid() {
        return uid;
    }

    public String getEquipmentID() {
        return equipmentID;
    }

    public String getFaction() {
        return faction;
    }

    public int getLvl() {
        return lvl;
    }

    public int getSize() {
        return size;
    }

    public int getUpgradeShards() {
        return upgradeShards;
    }

    public int getUpgradeTime() {
        return upgradeTime;
    }

    public List<String> getEffectUids() {
        return effectUids;
    }

    public List<String> getPlanetIDs() {
        return planetIDs;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public List<String> getSkins() {
        return skins;
    }

    public boolean isPlayerFacing() {
        return playerFacing;
    }
}
