package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.game.EquipmentData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class PlayerEquipmentUpgradeStart extends AbstractCommandAction<PlayerEquipmentUpgradeStart, CommandResult> {
    private String equipmentUid;

    @Override
    protected CommandResult execute(PlayerEquipmentUpgradeStart arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        String uid = arguments.getEquipmentUid();
        Map<String, EquipmentData> eqMap = ServiceFactory.instance().getGameDataManager()
                .getPatchData().getMap(EquipmentData.class);
        EquipmentData eq = (uid != null && eqMap != null) ? eqMap.get(uid) : null;

        if (eq != null && eq.getEquipmentID() != null) {
            if (!ServiceFactory.instance().getConfig().freeResources && eq.getUpgradeShards() > 0) {
                String shardKey = "shrd_" + eq.getEquipmentID();
                Map<String, Integer> shards = playerSession.getPlayerSettings().getShards();
                int current = shards.getOrDefault(shardKey, 0);
                shards.put(shardKey, Math.max(0, current - eq.getUpgradeShards()));
            }
            Map<String, Integer> eqUpgrades = playerSession.getPlayerSettings().getUpgrades().equipment;
            int currentLvl = eqUpgrades.getOrDefault(eq.getEquipmentID(), 0);
            eqUpgrades.put(eq.getEquipmentID(), Math.max(currentLvl, eq.getLvl()));
            playerSession.savePlayerKeepAlive();
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerEquipmentUpgradeStart parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerEquipmentUpgradeStart.class);
    }

    @Override
    public String getAction() {
        return "player.equipment.upgrade.start";
    }

    public String getEquipmentUid() {
        return equipmentUid;
    }
}
