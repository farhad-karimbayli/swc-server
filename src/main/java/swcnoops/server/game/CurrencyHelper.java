package swcnoops.server.game;

import swcnoops.server.ServiceFactory;
import swcnoops.server.model.CurrencyType;
import swcnoops.server.model.InventoryStorage;
import swcnoops.server.model.Squad;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;
import swcnoops.server.session.map.MapItem;
import swcnoops.server.session.map.StorageBuilding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CurrencyHelper {

    static public CurrencyType getCurrencyType(MapItem mapItem) {
        CurrencyType currencyType = null;
        if (mapItem != null) {
            currencyType = getCurrencyType(mapItem.getBuildingData());
        }
        return currencyType;
    }

    static public CurrencyType getCurrencyType(BuildingData buildingData) {
        CurrencyType currencyType = null;
        if (buildingData != null) {
            if (buildingData.getMaterials() != 0)
                currencyType = CurrencyType.materials;
            else if (buildingData.getCredits() != 0)
                currencyType = CurrencyType.credits;
            else if (buildingData.getContraband() != 0)
                currencyType = CurrencyType.contraband;
        }
        return currencyType;
    }

    static public int getConstructionCost(MapItem mapItem, CurrencyType currencyType) {
        int cost = 0;
        if (mapItem != null) {
            cost = getConstructionCost(mapItem.getBuildingData(), currencyType);
        }

        return cost;
    }

    static public int getConstructionCost(BuildingData buildingData, CurrencyType currencyType) {
        int cost = 0;
        if (buildingData != null && currencyType != null) {
            switch (currencyType) {
                case credits:
                    cost = buildingData.getCredits();
                    break;
                case materials:
                    cost = buildingData.getMaterials();
                    break;
                case contraband:
                    cost = buildingData.getContraband();
                    break;
            }
        }

        return cost;
    }

    static public int getCrossCost(BuildingData buildingData, CurrencyType currencyType) {
        int cost = 0;
        if (buildingData != null && currencyType != null) {
            switch (currencyType) {
                case credits:
                    cost = buildingData.getCrossCredits();
                    break;
                case materials:
                    cost = buildingData.getCrossMaterials();
                    break;
            }
        }

        return cost;
    }

    static public int calculateGivenConstructionCost(PlayerSession playerSession, int givenTotal,
                                               CurrencyType currencyType)
    {
        int givenCost = 0;
        if (currencyType != null) {
            InventoryStorage inventoryStorage = playerSession.getInventoryManager().getObjectForReading();
            switch (currencyType) {
                case credits:
                    givenCost = inventoryStorage.credits.amount - givenTotal;
                    break;
                case materials:
                    givenCost = inventoryStorage.materials.amount - givenTotal;
                    break;
                case contraband:
                    givenCost = inventoryStorage.contraband.amount - givenTotal;
                    break;
            }
        }

        return givenCost;
    }

    static public int getGivenTotal(CurrencyType currency, int credits, int materials, int contraband) {
        int givenTotal = 0;
        if (currency != null) {
            switch (currency) {
                case credits:
                    givenTotal = credits;
                    break;
                case materials:
                    givenTotal = materials;
                    break;
                case contraband:
                    givenTotal = contraband;
                    break;
            }
        }
        return givenTotal;
    }


    static public int calculateGivenRefund(PlayerSession playerSession, int credits, int materials, int contraband,
                                           CurrencyType currencyType)
    {
        int givenRefund = 0;
        if (currencyType != null) {
            InventoryStorage inventoryStorage = playerSession.getInventoryManager().getObjectForReading();
            switch (currencyType) {
                case credits:
                    givenRefund = credits - inventoryStorage.credits.amount;
                    break;
                case materials:
                    givenRefund = materials - inventoryStorage.materials.amount;
                    break;
                case contraband:
                    givenRefund = contraband - inventoryStorage.contraband.amount;
                    break;
            }
        }

        return givenRefund;
    }

    public static CurrencyType getCurrencyType(TroopData troopData) {
        CurrencyType currencyType = null;
        if (troopData != null) {
            if (troopData.getMaterials() != 0)
                currencyType = CurrencyType.materials;
            else if (troopData.getCredits() != 0)
                currencyType = CurrencyType.credits;
            else if (troopData.getContraband() != 0)
                currencyType = CurrencyType.contraband;
        }
        return currencyType;
    }

    public static int getUpgradeCost(TroopData troopData, CurrencyType currencyType) {
        int upgradeCost = 0;
        if (currencyType != null && troopData != null) {
            switch (currencyType) {
                case credits:
                    upgradeCost = troopData.getUpgradeCredits();
                    break;
                case materials:
                    upgradeCost = troopData.getUpgradeMaterials();
                    break;
                case contraband:
                    upgradeCost = troopData.getUpgradeContraband();
                    break;
            }
        }
        return upgradeCost;
    }

    public static int getTotalCapacity(PlayerSession playerSession, CurrencyType currency) {
        List<StorageBuilding> storageBuildings = playerSession.getPlayerMapItems().getStorageMapItemsByCurrency(currency);
        AtomicInteger totalStorage = new AtomicInteger();
        storageBuildings.forEach(a -> totalStorage.addAndGet(a.getBuildingData().getStorage()));
        return totalStorage.get();
    }

    public static int calculateStorageAvailable(CurrencyType currency, PlayerSession playerSession) {
        int totalCapacity = CurrencyHelper.getTotalCapacity(playerSession, currency);
        int available = totalCapacity;
        InventoryStorage inventoryStorage = playerSession.getInventoryManager().getObjectForReading();

        switch (currency) {
            case credits:
                available -= inventoryStorage.credits.amount;
                break;
            case materials:
                available -= inventoryStorage.materials.amount;
                break;
            case contraband:
                available -= inventoryStorage.contraband.amount;
                break;
            default:
                available = 0;
                break;
        }

        if (available < 0)
            available = 0;

        return available;
    }

    /**
     * Collects all currently active squad perk effects matching {@code effectType}.
     */
    private static List<PerkEffectData> getActivePerkEffects(PlayerSession playerSession, String effectType, long now) {
        List<PerkEffectData> matches = new ArrayList<>();
        if (playerSession == null || effectType == null) {
            return matches;
        }
        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession == null || guildSession.getSquadManager() == null) {
            return matches;
        }
        Squad squad = guildSession.getSquadManager().getObjectForReading();
        if (squad == null || squad.perks == null || squad.perks.available == null || squad.perks.available.isEmpty()) {
            return matches;
        }
        GameDataManager gameDataManager = ServiceFactory.instance().getGameDataManager();
        PatchData patchData = gameDataManager != null ? gameDataManager.getPatchData() : null;
        Map<String, PerkData> perkMap = patchData != null ? patchData.getMap(PerkData.class) : null;
        Map<String, PerkEffectData> effectMap = patchData != null ? patchData.getMap(PerkEffectData.class) : null;
        if (perkMap == null || effectMap == null) {
            return matches;
        }
        long checkTime = now > 0 ? now : ServiceFactory.getSystemTimeSecondsFromEpoch();
        for (Map.Entry<String, String> entry : squad.perks.available.entrySet()) {
            PerkData perk = perkMap.get(entry.getKey());
            if (perk == null || perk.getPerkEffects() == null || !isPerkActive(perk, entry.getValue(), checkTime)) {
                continue;
            }
            for (String effectUid : perk.getPerkEffects()) {
                PerkEffectData effect = effectMap.get(effectUid);
                if (effect != null && effectType.equals(effect.getType())) {
                    matches.add(effect);
                }
            }
        }
        return matches;
    }

    /**
     * Returns active squad-perk contractCost discount factor in [0.0, 0.95] for the given building.
     */
    public static double getActiveContractDiscount(PlayerSession playerSession, BuildingData buildingData, long now) {
        if (buildingData == null || buildingData.getType() == null) {
            return 0.0;
        }
        String targetBuilding = buildingData.getType().name();
        double discount = 0.0;
        for (PerkEffectData effect : getActivePerkEffects(playerSession, "contractCost", now)) {
            if (targetBuilding.equals(effect.getBuilding())) {
                discount = Math.max(discount, effect.getDiscountFactor());
            }
        }
        return Math.min(0.95, Math.max(0.0, discount));
    }

    /**
     * Returns active squad-perk contractTime reduction factor in [0.0, 0.95] for the given building.
     */
    public static double getActiveContractTimeReduction(PlayerSession playerSession, BuildingData buildingData, long now) {
        if (buildingData == null || buildingData.getType() == null) {
            return 0.0;
        }
        String targetBuilding = buildingData.getType().name();
        double reduction = 0.0;
        for (PerkEffectData effect : getActivePerkEffects(playerSession, "contractTime", now)) {
            if (targetBuilding.equals(effect.getBuilding())) {
                reduction = Math.max(reduction, effect.getTimeReductionFactor());
            }
        }
        return Math.min(0.95, Math.max(0.0, reduction));
    }

    /**
     * Returns active resource generator multiplier bonus (>= 0.0) for the given currency.
     */
    public static double getActiveGeneratorBoost(PlayerSession playerSession, CurrencyType currency, long now) {
        if (currency == null) {
            return 0.0;
        }
        double boost = 0.0;
        for (PerkEffectData effect : getActivePerkEffects(playerSession, "generator", now)) {
            if (currency.name().equalsIgnoreCase(effect.getCurrency())) {
                boost = Math.max(boost, effect.getGenerationRateFactor());
            }
        }
        return Math.max(0.0, boost);
    }

    /**
     * Returns active relocation star discount (>= 0).
     */
    public static int getActiveRelocationDiscount(PlayerSession playerSession, long now) {
        int discount = 0;
        for (PerkEffectData effect : getActivePerkEffects(playerSession, "relocation", now)) {
            discount = Math.max(discount, effect.getRelocationDiscountValue());
        }
        return Math.max(0, discount);
    }

    /**
     * Returns active squad troop request extra capacity bonus (>= 0).
     */
    public static int getActiveTroopRequestAmountBonus(PlayerSession playerSession, long now) {
        int bonus = 0;
        for (PerkEffectData effect : getActivePerkEffects(playerSession, "troopRequestAmount", now)) {
            bonus = Math.max(bonus, effect.getTroopRequestAmountValue());
        }
        return Math.max(0, bonus);
    }

    /**
     * Returns active squad troop request cooldown discount in seconds (>= 0).
     */
    public static int getActiveTroopRequestTimeDiscount(PlayerSession playerSession, long now) {
        int discount = 0;
        for (PerkEffectData effect : getActivePerkEffects(playerSession, "troopRequestTime", now)) {
            discount = Math.max(discount, effect.getTroopRequestTimeDiscountValue());
        }
        return Math.max(0, discount);
    }

    private static boolean isPerkActive(PerkData perk, String storedTimestamp, long now) {
        if (storedTimestamp == null || storedTimestamp.isEmpty()) {
            return false;
        }
        try {
            long ts = Long.parseLong(storedTimestamp);
            long durationSec = Math.max(1L, perk.getActiveDuration()) * 60L;
            return ts > now || (ts + durationSec > now);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Applies a fractional contractCost discount to a positive base cost.
     */
    public static int applyContractDiscount(int baseCost, double discount) {
        if (baseCost <= 0 || discount <= 0.0) {
            return baseCost;
        }
        return Math.max(1, (int) Math.floor(baseCost * (1.0 - Math.min(0.95, discount))));
    }

    /**
     * Applies a fractional contractTime reduction to a positive duration in seconds.
     */
    public static long applyTimeReduction(long seconds, double reduction) {
        if (seconds <= 0 || reduction <= 0.0) {
            return seconds;
        }
        return Math.max(1L, (long) Math.floor(seconds * (1.0 - Math.min(0.95, reduction))));
    }

    /**
     * Applies a fractional generator rate boost (>= 0) to generated resources.
     */
    public static int applyGeneratorBoost(int baseAmount, double boost) {
        if (baseAmount <= 0 || boost <= 0.0) {
            return baseAmount;
        }
        return (int) Math.floor(baseAmount * (1.0 + boost));
    }
}
