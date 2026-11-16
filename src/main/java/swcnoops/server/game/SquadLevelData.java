package swcnoops.server.game;

public class SquadLevelData implements JoeData {
    private String uid;
    private int level;
    private int repThreshold;
    private int slots;

    @Override
    public String getUid() {
        return uid;
    }

    public int getLevel() {
        return level;
    }

    public int getRepThreshold() {
        return repThreshold;
    }

    public int getSlots() {
        return slots;
    }
}
