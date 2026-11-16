package swcnoops.server.game;

import java.util.List;

public class CrateItemData implements JoeData {
    private String uid;
    private String titleString;
    private String assetName;
    private String bundleName;
    private String expirationTime;
    private boolean purchasable;
    private List<String> supplyPoolUid;
    private List<String> featuredEmpireSupplies;
    private List<String> featuredRebelSupplies;

    @Override
    public String getUid() {
        return uid;
    }

    public String getTitleString() {
        return titleString;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getExpirationTime() {
        return expirationTime;
    }

    public boolean isPurchasable() {
        return purchasable;
    }

    public List<String> getSupplyPoolUid() {
        return supplyPoolUid;
    }

    public List<String> getFeaturedEmpireSupplies() {
        return featuredEmpireSupplies;
    }

    public List<String> getFeaturedRebelSupplies() {
        return featuredRebelSupplies;
    }
}
