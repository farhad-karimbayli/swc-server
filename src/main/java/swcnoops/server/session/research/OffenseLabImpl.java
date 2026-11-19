package swcnoops.server.session.research;

import swcnoops.server.ServiceFactory;
import swcnoops.server.game.*;
import swcnoops.server.model.Building;
import swcnoops.server.model.CurrencyType;
import swcnoops.server.model.Position;
import swcnoops.server.session.CurrencyDelta;
import swcnoops.server.session.PlayerSession;
import swcnoops.server.session.inventory.TroopUpgrade;
import swcnoops.server.session.inventory.Troops;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OffenseLabImpl implements OffenseLab {
    final private Building building;
    private BuildingData buildingData;
    final private PlayerSession playerSession;
    public OffenseLabImpl(PlayerSession playerSession, Building building, BuildingData buildingData) {
        this.playerSession = playerSession;
        this.building = building;
        this.buildingData = buildingData;
    }

    @Override
    public String getBuildingKey() {
        return building.key;
    }

    @Override
    public String getBuildingUid() {
        return building.uid;
    }


    @Override
    public Building getBuilding() {
        return this.building;
    }

    @Override
    public BuildingData getBuildingData() {
        return buildingData;
    }

    public void setBuildingData(BuildingData buildingData) {
        this.buildingData = buildingData;
    }

    @Override
    public CurrencyDelta buyout(int crystals, long time) {
        // can only really handle 1 upgrade at a time
        CurrencyDelta currencyDelta = null;
        if (isResearchingTroop()) {
            Troops troops = this.playerSession.getTroopInventory().getTroops();
            List<TroopUpgrade> buyoutList = troops.getUpgrades();
            if (buyoutList.size() > 0) {
                TroopUpgrade troopUpgrade = buyoutList.get(0);

                int secondsToBuy = (int)(troopUpgrade.getEndTime() - time);
                troopUpgrade.buyout(time);
                this.processCompletedUpgrades(time);
                int expectedDelta = CrystalHelper.secondsToCrystalsForResearch(secondsToBuy);
                int givenDelta = CrystalHelper.calculateGivenCrystalDeltaToRemove(this.playerSession, crystals);
                currencyDelta = new CurrencyDelta(givenDelta, expectedDelta, CurrencyType.crystals, true);
            }
        }

        return currencyDelta;
    }

    @Override
    public CurrencyDelta cancel(long time, int credits, int materials, int contraband) {
        CurrencyDelta currencyDelta = null;
        if (isResearchingTroop()) {
            Troops troops = this.playerSession.getTroopInventory().getTroops();
            if (troops.getUpgrades().size() > 0) {
                TroopUpgrade troopUpgrade = troops.getUpgrades().get(0);
                troops.getUpgrades().clear();

                TroopData troopData = ServiceFactory.instance().getGameDataManager().getTroopDataByUid(troopUpgrade.getTroopUId());
                CurrencyType currencyType = CurrencyHelper.getCurrencyType(troopData);
                int givenDelta = CurrencyHelper.calculateGivenRefund(this.playerSession, credits, materials, contraband, currencyType);
                GameConstants constants = ServiceFactory.instance().getGameDataManager().getGameConstants();
                int expectedRefund = (int) ((float) troopUpgrade.getUpgradeCost() * constants.contract_refund_percentage_buildings / 100f);
                int availableStorage = CurrencyHelper.calculateStorageAvailable(currencyType, playerSession);
                if (expectedRefund > availableStorage)
                    expectedRefund = availableStorage;
                currencyDelta = new CurrencyDelta(givenDelta, expectedRefund, currencyType, false);
            }
        }

        return currencyDelta;
    }

    @Override
    public CurrencyDelta upgradeStart(String buildingId, String troopUid, int credits, int materials, int contraband, long time) {
        CurrencyDelta currencyDelta = null;
        if (this.playerSession.getTroopInventory().getTroops() != null) {
            TroopData troopData = ServiceFactory.instance().getGameDataManager().getTroopDataByUid(troopUid);
            if (troopData == null) {
                return null;
            }
            if (troopData.getUpgradeShards() > 0 && troopData.getUpgradeShardUid() != null) {
                Map<String, Integer> shards = this.playerSession.getPlayerSettings().getShards();
                int currentShards = shards.getOrDefault(troopData.getUpgradeShardUid(), 0);
                if (currentShards >= troopData.getUpgradeShards()) {
                    shards.put(troopData.getUpgradeShardUid(), currentShards - troopData.getUpgradeShards());
                } else if (!ServiceFactory.instance().getConfig().freeResources) {
                    return null;
                }
            }
            long endTime = time + troopData.getUpgradeTime();
            CurrencyType currencyType = CurrencyHelper.getCurrencyType(troopData);
            int upgradeCost = CurrencyHelper.getUpgradeCost(troopData, currencyType);
            TroopUpgrade troopUpgrade = new TroopUpgrade(buildingId, troopUid, endTime, upgradeCost);
            this.playerSession.getTroopInventory().getTroops().getUpgrades().add(troopUpgrade);

            if (currencyType != null) {
                int givenTotal = CurrencyHelper.getGivenTotal(currencyType, credits, materials, contraband);
                int givenDelta = CurrencyHelper.calculateGivenConstructionCost(this.playerSession, givenTotal, currencyType);
                currencyDelta = new CurrencyDelta(givenDelta, upgradeCost, currencyType, true);
            }
        }

        return currencyDelta;
    }

    @Override
    public boolean processCompletedUpgrades(long time) {
        boolean hasUpgrade = false;

        Troops troops = this.playerSession.getTroopInventory().getTroops();
        Iterator<TroopUpgrade> troopUpgradeIterator = troops.getUpgrades().iterator();
        while (troopUpgradeIterator.hasNext()) {
            TroopUpgrade troopUpgrade = troopUpgradeIterator.next();
            if (troopUpgrade.getEndTime() <= time) {
                troopUpgradeIterator.remove();
                TroopData troopData = ServiceFactory.instance().getGameDataManager()
                        .getTroopDataByUid(troopUpgrade.getTroopUId());
                this.playerSession.getTroopInventory().upgradeTroop(troopData, time);
                hasUpgrade = true;
            }
        }

        return hasUpgrade;
    }

    @Override
    public boolean isResearchingTroop() {
        Troops troops = this.playerSession.getTroopInventory().getTroops();
        return troops.getUpgrades().size() > 0;
    }

    @Override
    public void changeBuildingData(BuildingData buildingData) {
        if (buildingData != null) {
            this.buildingData = buildingData;
            this.building.uid = buildingData.getUid();
        }
    }

    @Override
    public void upgradeComplete(PlayerSession playerSession, String unitId, String tag, long endTime) {
        BuildingData upgraded = ServiceFactory.instance().getGameDataManager().getBuildingDataByUid(unitId);
        if (upgraded != null) {
            changeBuildingData(upgraded);
        }
    }

    @Override
    public void moveTo(Position newPosition) {
        if (newPosition != null && this.building != null) {
            this.building.x = newPosition.x;
            this.building.z = newPosition.z;
        }
    }

    @Override
    public CurrencyDelta collect(PlayerSession playerSession, int credits, int materials, int contraband, long time, boolean collectAll) {
        return null;
    }

    @Override
    public void buildComplete(PlayerSession playerSession, String unitId, String tag, long endTime) {
    }

    @Override
    public void setupForConstruction() {
    }

    @Override
    public void upgradeCancelled(long time) {
    }
}
