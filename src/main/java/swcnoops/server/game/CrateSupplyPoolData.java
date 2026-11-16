package swcnoops.server.game;

public class CrateSupplyPoolData implements JoeData {
    private String uid;
    private String fallbackUid;
    private int luckThreshold;

    @Override
    public String getUid() {
        return uid;
    }

    public String getFallbackUid() {
        return fallbackUid;
    }

    public int getLuckThreshold() {
        return luckThreshold;
    }
}
