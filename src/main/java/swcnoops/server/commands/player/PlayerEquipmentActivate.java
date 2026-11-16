package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.game.EquipmentData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.ActiveArmory;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

import java.util.ArrayList;
import java.util.List;
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

            List<String> candidate = new ArrayList<>(armory.equipment);
            if (eq != null && eq.getEquipmentID() != null) {
                String eqId = eq.getEquipmentID();
                candidate.removeIf(existing -> {
                    EquipmentData e = eqMap.get(existing);
                    return e != null && eqId.equals(e.getEquipmentID());
                });
            }
            if (!candidate.contains(uid)) {
                candidate.add(uid);
            }

            if (eqMap != null && armory.capacity > 0) {
                int usedSize = 0;
                for (String itemUid : candidate) {
                    EquipmentData item = eqMap.get(itemUid);
                    if (item != null) {
                        usedSize += Math.max(0, item.getSize());
                    }
                }
                if (usedSize > armory.capacity) {
                    return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
                }
            }

            armory.equipment.clear();
            armory.equipment.addAll(candidate);
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
