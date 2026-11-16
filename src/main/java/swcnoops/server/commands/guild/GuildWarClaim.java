package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.session.PlayerSession;

public class GuildWarClaim extends AbstractCommandAction<GuildWarClaim, CrateDataResult> {
    private String warId;

    @Override
    protected CrateDataResult execute(GuildWarClaim arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        CrateData crate = CrateService.createAndStoreCrate(playerSession, "lc_daily", "war", null, now);
        crate.warId = arguments.getWarId();
        playerSession.savePlayerKeepAlive();

        CrateDataResult result = new CrateDataResult();
        result.setCrateData(crate);
        return result;
    }

    @Override
    protected GuildWarClaim parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildWarClaim.class);
    }

    @Override
    public String getAction() {
        return "guild.war.claim";
    }

    public String getWarId() {
        return warId;
    }
}
