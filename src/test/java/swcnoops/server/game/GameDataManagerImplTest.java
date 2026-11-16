package swcnoops.server.game;

import org.junit.Test;
import swcnoops.server.Config;
import swcnoops.server.ServiceFactory;
import swcnoops.server.model.FactionType;
import swcnoops.server.model.ObjectiveGroup;
import swcnoops.server.model.UnlockedPlanets;

import java.util.Map;

import static org.junit.Assert.*;

public class GameDataManagerImplTest {
    static {
        ServiceFactory.instance(new Config());
    }

    @Test
    public void test() {
        GameDataManager gameDataManager = new GameDataManagerImpl();
        gameDataManager.initOnStartup();
        TroopData troopData = gameDataManager.getTroopDataByUid("troopEmpireRageRancorCreature10");
        assertNotNull(troopData);
        BuildingData buildingData = gameDataManager.getBuildingDataByUid("empireTrapDropshipCreature10");
        assertNotNull(buildingData);
        troopData = gameDataManager.getTroopDataByUid("specialAttackEmpireCreatureDropship10");
        assertNotNull(troopData);
        TrapData trapData = gameDataManager.getTrapDataByUid(buildingData.getTrapId());
        assertNotNull(trapData);
        buildingData = gameDataManager.getBuildingDataByUid("rebelPlatformDroideka50");
        assertNotNull(buildingData);
        troopData = gameDataManager.getTroopDataByUid("troopChampionRebelDroideka50");
        assertNotNull(troopData);
        buildingData = gameDataManager.getBuildingDataByUid("rebelPlatformHeavyDroideka47");
        assertNotNull(buildingData);
        troopData = gameDataManager.getTroopDataByUid("specialAttackEmpireHauler11");
        assertNotNull(troopData);
        assertNotNull(troopData.getUnitId());
        troopData = gameDataManager.getTroopDataByUid("troopMercenaryEmpireRider1");
        assertNotNull(troopData);
        assertNotNull(troopData.getUnitId());
        troopData = gameDataManager.getTroopDataByUid("troopSniper1");
        assertNotNull(troopData);
        assertNotNull(troopData.getUnitId());
        troopData = gameDataManager.getTroopDataByUid("troopHeroATAT1");
        assertNotNull(troopData);
        assertNotNull(troopData.getUnitId());

        // Verify newly extracted game patches are loaded
        PatchData patchData = gameDataManager.getPatchData();
        assertFalse(patchData.getMap(CrateItemData.class).isEmpty());
        assertFalse(patchData.getMap(CrateSupplyData.class).isEmpty());
        assertFalse(patchData.getMap(ShardShopSeriesData.class).isEmpty());
        assertFalse(patchData.getMap(EquipmentData.class).isEmpty());
        assertFalse(patchData.getMap(CommandCenterEntryData.class).isEmpty());
        assertFalse(patchData.getMap(TransmissionData.class).isEmpty());
        assertFalse(patchData.getMap(PerkData.class).isEmpty());
        assertFalse(patchData.getMap(EpisodeData.class).isEmpty());
        assertFalse(patchData.getMap(WarBuffData.class).isEmpty());

        // Verify live planetary conflicts and daily objectives are active
        assertNotNull(gameDataManager.getConflictManager().getConflict("planet1"));
        assertNotNull(gameDataManager.getConflictManager().getConflict("planet3"));
        UnlockedPlanets planets = new UnlockedPlanets();
        planets.add("planet1");
        Map<String, ObjectiveGroup> objectives = gameDataManager.getObjectiveManager()
                .getObjectiveGroups(null, planets, null, FactionType.empire, 11, 0f);
        assertNotNull(objectives);
        assertTrue(objectives.containsKey("planet1"));
        assertEquals(3, objectives.get("planet1").progress.size());
    }
}
