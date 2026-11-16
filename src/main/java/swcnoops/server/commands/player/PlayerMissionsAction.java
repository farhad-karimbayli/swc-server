package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

/**
 * Handles player.missions.showIntro and player.missions.startSpecop.
 */
public class PlayerMissionsAction extends AbstractCommandAction<PlayerMissionsAction, CommandResult> {
    private final String actionName;
    private String missionUid;

    public PlayerMissionsAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(PlayerMissionsAction arguments, long time) throws Exception {
        if ("player.missions.startSpecop".equals(this.actionName)
                && arguments.getMissionUid() != null && !arguments.getMissionUid().isEmpty()) {
            PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                    .getPlayerSession(arguments.getPlayerId());
            playerSession.activateMission(arguments.getMissionUid(), time);
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerMissionsAction parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerMissionsAction.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }

    public String getMissionUid() {
        return missionUid;
    }
}
