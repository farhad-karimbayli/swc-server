package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.game.EquipmentData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.ActiveArmory;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class PlayerEquipmentActivate extends AbstractCommandAction<PlayerEquipmentActivate, CommandResult> {
    private String equipmentUid;

    @Override
    protected CommandResult execute(PlayerEquipmentActivate arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        String uid = arguments.getEquipmentUid();
        if (uid != null && !uid.isEmpty()) {
            ActiveArmory armory = playerSession.getPlayerSettings().getActiveArmory();
            Map<String, EquipmentData> eqMap = ServiceFactory.instance().getGameDataManager()
                    .getPatchData().getMap(EquipmentData.class);
            EquipmentData eq = eqMap != null ? eqMap.get(uid) : null;
            if (eq != null) {
                // Remove any lower/other level of the same equipmentID first
                String eqId = eq.getEquipmentID();
                if (eqId != null && eqMap != null) {
                    armory.equipment.removeIf(existing -> {
                        EquipmentData e = eqMap.get(existing);
                        return e != null && eqId.equals(e.getEquipmentID());
                    });
                }
            }
            if (!armory.equipment.contains(uid)) {
                armory.equipment.add(uid);
            }
            playerSession.savePlayerKeepAlive();
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerEquipmentActivate parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerEquipmentActivate.class);
    }

    @Override
    public String getAction() {
        return "player.equipment.activate";
    }

    public String getEquipmentUid() {
        return equipmentUid;
    }
}
