package swcnoops.server.game;

import java.util.List;

public class PerkData implements JoeData {
    private String uid;
    private String perkGroup;
    private int perkTier;
    private int squadLevelUnlock;
    private int repCost;
    private int activeDuration;
    private int cooldownDuration;
    private List<String> activationCost;
    private List<String> perkEffects;

    @Override
    public String getUid() {
        return uid;
    }

    public String getPerkGroup() {
        return perkGroup;
    }

    public int getPerkTier() {
        return perkTier;
    }

    public int getSquadLevelUnlock() {
        return squadLevelUnlock;
    }

    public int getRepCost() {
        return repCost;
    }

    public int getActiveDuration() {
        return activeDuration;
    }

    public int getCooldownDuration() {
        return cooldownDuration;
    }

    public List<String> getActivationCost() {
        return activationCost;
    }

    public List<String> getPerkEffects() {
        return perkEffects;
    }
}
