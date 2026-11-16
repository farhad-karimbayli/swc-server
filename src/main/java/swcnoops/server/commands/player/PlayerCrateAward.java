package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.game.CrateService;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

public class PlayerCrateAward extends AbstractCommandAction<PlayerCrateAward, CommandResult> {
    private String crateUid;

    @Override
    protected CommandResult execute(PlayerCrateAward arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        CrateService.awardCrate(playerSession, arguments.getCrateUid());
        playerSession.savePlayerKeepAlive();
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerCrateAward parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerCrateAward.class);
    }

    @Override
    public String getAction() {
        return "player.crate.award";
    }

    public String getCrateUid() {
        return crateUid;
    }
}
