package swcnoops.server.game;

public class PerkEffectData implements JoeData {
    private String uid;
    private String type;
    private String building;
    private String contractDiscount;

    @Override
    public String getUid() {
        return uid;
    }

    public String getType() {
        return type;
    }

    public String getBuilding() {
        return building;
    }

    public String getContractDiscount() {
        return contractDiscount;
    }

    public double getDiscountFactor() {
        if (contractDiscount == null || contractDiscount.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(contractDiscount);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
