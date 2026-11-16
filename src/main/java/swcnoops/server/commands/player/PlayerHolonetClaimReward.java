package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.session.PlayerSession;

public class PlayerHolonetClaimReward extends AbstractCommandAction<PlayerHolonetClaimReward, CrateDataResult> {
    private String rewardUid;

    @Override
    protected CrateDataResult execute(PlayerHolonetClaimReward arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        CrateData crate = CrateService.createAndStoreCrate(playerSession, "lc_daily", "holonet", null, now);
        playerSession.savePlayerKeepAlive();

        CrateDataResult result = new CrateDataResult();
        result.setCrateData(crate);
        return result;
    }

    @Override
    protected PlayerHolonetClaimReward parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerHolonetClaimReward.class);
    }

    @Override
    public String getAction() {
        return "player.holonet.claimReward";
    }

    public String getRewardUid() {
        return rewardUid;
    }
}
