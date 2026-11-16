package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerLoginCommandResult;
import swcnoops.server.json.JsonParser;
import swcnoops.server.session.PlayerSession;

/**
 * Handles player.get and player.getSyncContent by returning a fresh PlayerLoginCommandResult snapshot.
 */
public class PlayerGet extends AbstractCommandAction<PlayerGet, PlayerLoginCommandResult> {
    private final String actionName;

    public PlayerGet(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected PlayerLoginCommandResult execute(PlayerGet arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        return PlayerLogin.buildLoginResponse(
                playerSession, playerSession.getPlayerSettings().getTimeZoneOffset(), null, time);
    }

    @Override
    protected PlayerGet parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerGet.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }
}
