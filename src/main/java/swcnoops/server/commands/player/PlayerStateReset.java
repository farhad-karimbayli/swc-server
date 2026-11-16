package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerLoginCommandResult;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

/**
 * Handles player.state.reset and player.content.reset.
 */
public class PlayerStateReset extends AbstractCommandAction<PlayerStateReset, CommandResult> {
    private final String actionName;

    public PlayerStateReset(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(PlayerStateReset arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        if ("player.state.reset".equals(this.actionName)) {
            PlayerLoginCommandResult freshTemplate = ServiceFactory.instance().getJsonParser()
                    .toObjectFromResource(ServiceFactory.instance().getConfig().newPlayerTemplate,
                            PlayerLoginCommandResult.class);
            playerSession.recoverWithPlayerSettings(freshTemplate.playerModel, freshTemplate.sharedPrefs);
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerStateReset parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStateReset.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }
}
