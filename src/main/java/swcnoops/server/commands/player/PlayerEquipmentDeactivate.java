package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

public class PlayerEquipmentDeactivate extends AbstractCommandAction<PlayerEquipmentDeactivate, CommandResult> {
    private String equipmentUid;

    @Override
    protected CommandResult execute(PlayerEquipmentDeactivate arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        if (arguments.getEquipmentUid() != null) {
            playerSession.getPlayerSettings().getActiveArmory().equipment.remove(arguments.getEquipmentUid());
            playerSession.savePlayerKeepAlive();
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerEquipmentDeactivate parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerEquipmentDeactivate.class);
    }

    @Override
    public String getAction() {
        return "player.equipment.deactivate";
    }

    public String getEquipmentUid() {
        return equipmentUid;
    }
}
