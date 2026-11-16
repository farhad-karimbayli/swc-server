package swcnoops.server.game;

public class EpisodeTaskActionData implements JoeData {
    private String uid;
    private String type;
    private String item;
    private String faction;
    private String scaleId;
    private int minHQ;
    private int maxHQ;

    @Override
    public String getUid() {
        return uid;
    }

    public String getType() {
        return type;
    }

    public String getItem() {
        return item;
    }

    public String getFaction() {
        return faction;
    }

    public String getScaleId() {
        return scaleId;
    }

    public int getMinHQ() {
        return minHQ;
    }

    public int getMaxHQ() {
        return maxHQ;
    }
}
