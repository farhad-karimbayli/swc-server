package swcnoops.server.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swcnoops.server.Config;
import swcnoops.server.ServiceFactory;
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
}
