package swcnoops.server.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swcnoops.server.Config;
import swcnoops.server.ServiceFactory;
import swcnoops.server.game.GameDataManager;
import swcnoops.server.game.TroopData;
import swcnoops.server.model.DonatedTroops;
import swcnoops.server.model.GuildDonatedTroops;
import swcnoops.server.session.training.DeployableQueue;
import swcnoops.server.session.training.TrainingManager;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tops a player's army back up, for the players listed in the refill file.
 *
 * The file (autoRefillFile in swc.properties, relative to swcRootPath) names the
 * players, the amount every listed troop, hero and starship is brought up to, and
 * a separate number per champion, since a platform holds one. Units are only ever
 * added, never taken away. The file is read on every call, so editing it needs no
 * restart.
 *
 * Also refills the Squad Center (donatedTroops) for units listed in "squad" up to
 * "squadAmount" (or amount if not set).
 *
 * This works on the session in memory, which the caller saves afterwards. Editing
 * the database instead would be overwritten by the session's own copy.
 */
public final class AutoRefill {
    private static final Logger LOG = LoggerFactory.getLogger(AutoRefill.class);

    /** The refill file's shape. Fields are public for Jackson. */
    public static class Spec {
        public String _comment;
        public List<String> players;
        public int amount;
        public List<String> troop;
        public List<String> hero;
        public List<String> specialAttack;
        public Map<String, Integer> champion;
        public Integer squadAmount;
        public List<String> squad;
    }

    private AutoRefill() {
    }

    public static void apply(PlayerSession playerSession, String reason) {
        Config config = ServiceFactory.instance().getConfig();
        if (config.autoRefillFile == null || config.autoRefillFile.trim().isEmpty())
            return;

        File file = new File(config.swcRootPath, config.autoRefillFile);
        if (!file.exists())
            return;

        Spec spec;
        try {
            spec = ServiceFactory.instance().getJsonParser().fromJsonFile(file.getAbsolutePath(), Spec.class);
        } catch (Exception e) {
            LOG.warn("auto refill skipped, cannot read {}: {}", file, e.toString());
            return;
        }

        if (spec.players == null || !spec.players.contains(playerSession.getPlayerId()))
            return;

        TrainingManager trainingManager = playerSession.getTrainingManager();
        int added = 0;
        added += topUp(trainingManager.getDeployableTroops(), spec.troop, spec.amount);
        added += topUp(trainingManager.getDeployableHero(), spec.hero, spec.amount);
        added += topUp(trainingManager.getDeployableSpecialAttack(), spec.specialAttack, spec.amount);
        added += topUp(trainingManager.getDeployableChampion(), spec.champion);

        if (spec.squad != null && !spec.squad.isEmpty()) {
            int squadTarget = (spec.squadAmount != null && spec.squadAmount > 0) ? spec.squadAmount : 30;
            added += topUpSquad(playerSession, spec.squad, squadTarget);
        }

        if (added > 0)
            LOG.info("auto refill after {} for {}: {} units added", reason, playerSession.getPlayerId(), added);
    }

    private static int topUp(DeployableQueue queue, List<String> unitIds, int amount) {
        if (unitIds == null)
            return 0;
        Map<String, Integer> wanted = new HashMap<>();
        unitIds.forEach(unitId -> wanted.put(unitId, amount));
        return topUp(queue, wanted);
    }

    private static int topUp(DeployableQueue queue, Map<String, Integer> wanted) {
        if (wanted == null)
            return 0;
        Map<String, Integer> units = new HashMap<>(queue.getDeployableUnits());
        int added = 0;
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            int current = units.getOrDefault(entry.getKey(), 0);
            if (current < entry.getValue()) {
                added += entry.getValue() - current;
                units.put(entry.getKey(), entry.getValue());
            }
        }
        // initialiseDeployableUnits also recounts the housing the queue holds
        if (added > 0)
            queue.initialiseDeployableUnits(units);
        return added;
    }

    private static int topUpSquad(PlayerSession playerSession, List<String> squadUnits, int amount) {
        if (squadUnits == null || squadUnits.isEmpty())
            return 0;

        DonatedTroops donatedTroops = playerSession.getDonatedTroops();
        if (donatedTroops == null)
            return 0;

        GameDataManager gameDataManager = ServiceFactory.instance().getGameDataManager();
        String donorId = playerSession.getPlayerId();
        int added = 0;

        for (String unitName : squadUnits) {
            String troopUid = resolveTroopUid(playerSession, gameDataManager, unitName);
            if (troopUid == null)
                continue;

            GuildDonatedTroops guildDonatedTroops = donatedTroops.get(troopUid);
            if (guildDonatedTroops == null) {
                guildDonatedTroops = new GuildDonatedTroops();
                guildDonatedTroops.put(donorId, amount);
                donatedTroops.put(troopUid, guildDonatedTroops);
                added += amount;
            } else {
                int currentTotal = guildDonatedTroops.values().stream().mapToInt(Integer::intValue).sum();
                if (currentTotal < amount) {
                    int diff = amount - currentTotal;
                    int donorCurrent = guildDonatedTroops.getOrDefault(donorId, 0);
                    guildDonatedTroops.put(donorId, donorCurrent + diff);
                    added += diff;
                }
            }
        }
        return added;
    }

    private static String resolveTroopUid(PlayerSession playerSession, GameDataManager gameDataManager, String unitName) {
        if (unitName == null || unitName.trim().isEmpty())
            return null;

        // 1. Check if unitName is already a valid UID in game data
        TroopData directTroop = gameDataManager.getTroopDataByUid(unitName);
        if (directTroop != null) {
            return directTroop.getUid();
        }

        // 2. unitName is a unitId (e.g. "Storm", "ATAT", "2MTank") - resolve to max level for squad
        TroopData lowestTroop = gameDataManager.getLowestLevelTroopDataByUnitId(unitName);
        if (lowestTroop != null) {
            int maxLvl = gameDataManager.getMaxlevelForTroopUnitId(lowestTroop.getUnitId());
            if (maxLvl > 0) {
                TroopData maxTroop = gameDataManager.getTroopDataByUnitId(lowestTroop.getUnitId(), maxLvl);
                if (maxTroop != null) {
                    return maxTroop.getUid();
                }
            }
            return lowestTroop.getUid();
        }

        if (playerSession.getTroopInventory() != null) {
            TroopData playerTroop = playerSession.getTroopInventory().getTroopByUnitId(unitName);
            if (playerTroop != null) {
                return playerTroop.getUid();
            }
        }

        return null;
    }
}
