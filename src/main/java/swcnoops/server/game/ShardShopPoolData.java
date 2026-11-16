package swcnoops.server.game;

import java.util.List;

public class ShardShopPoolData implements JoeData {
    private String uid;
    private List<String> costs;
    private String empireCrateSupplyPool;
    private String rebelCrateSupplyPool;

    @Override
    public String getUid() {
        return uid;
    }

    public List<String> getCosts() {
        return costs;
    }

    public String getEmpireCrateSupplyPool() {
        return empireCrateSupplyPool;
    }

    public String getRebelCrateSupplyPool() {
        return rebelCrateSupplyPool;
    }
}
