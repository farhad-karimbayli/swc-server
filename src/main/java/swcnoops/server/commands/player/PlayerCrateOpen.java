package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.model.PlayerCrates;
import swcnoops.server.session.PlayerSession;

public class PlayerCrateOpen extends AbstractCommandAction<PlayerCrateOpen, CrateDataResult> {
    private String crateUid;

    @Override
    protected CrateDataResult execute(PlayerCrateOpen arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        PlayerCrates crates = playerSession.getPlayerSettings().getCrates();
        CrateData crate = crates.available.get(arguments.getCrateUid());
        if (crate != null) {
            crate.claimed = true;
            playerSession.savePlayerKeepAlive();
        }
        CrateDataResult result = new CrateDataResult();
        result.setCrateData(crate);
        return result;
    }

    @Override
    protected PlayerCrateOpen parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerCrateOpen.class);
    }

    @Override
    public String getAction() {
        return "player.crate.open";
    }

    public String getCrateUid() {
        return crateUid;
    }
}
