package swcnoops.server.game;

public class PerkEffectData implements JoeData {
    private String uid;
    private String type;
    private String building;
    private String currency;
    private String contractDiscount;
    private String contractTimeReduction;
    private String generationRate;
    private String relocationDiscount;
    private String troopRequestAmount;
    private String troopRequestTimeDiscount;

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

    public String getCurrency() {
        return currency;
    }

    public String getContractDiscount() {
        return contractDiscount;
    }

    public String getContractTimeReduction() {
        return contractTimeReduction;
    }

    public String getGenerationRate() {
        return generationRate;
    }

    public String getRelocationDiscount() {
        return relocationDiscount;
    }

    public String getTroopRequestAmount() {
        return troopRequestAmount;
    }

    public String getTroopRequestTimeDiscount() {
        return troopRequestTimeDiscount;
    }

    public double getDiscountFactor() {
        return parseDoubleOrZero(contractDiscount);
    }

    public double getTimeReductionFactor() {
        return parseDoubleOrZero(contractTimeReduction);
    }

    public double getGenerationRateFactor() {
        return parseDoubleOrZero(generationRate);
    }

    public int getRelocationDiscountValue() {
        return parseIntOrZero(relocationDiscount);
    }

    public int getTroopRequestAmountValue() {
        return parseIntOrZero(troopRequestAmount);
    }

    public int getTroopRequestTimeDiscountValue() {
        return parseIntOrZero(troopRequestTimeDiscount);
    }

    private static double parseDoubleOrZero(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
