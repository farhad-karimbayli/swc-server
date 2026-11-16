package swcnoops.server.game;

public class CostData implements JoeData {
    private String uid;
    private int credits;
    private int materials;
    private int contraband;
    private int crystals;

    @Override
    public String getUid() {
        return uid;
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

    public int getCrystals() {
        return crystals;
    }
}
