package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.game.TournamentData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.session.PlayerSession;

import java.util.List;

public class PlayerStoreTournamentRedeem extends PlayerChecksum<PlayerStoreTournamentRedeem, CrateDataResult> {
    private String uid;
    private String planetId;

    @Override
    protected CrateDataResult execute(PlayerStoreTournamentRedeem arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        String tournamentUid = arguments.getUid();
        if ((tournamentUid == null || tournamentUid.isEmpty()) && arguments.getPlanetId() != null) {
            TournamentData t = ServiceFactory.instance().getGameDataManager()
                    .getConflictManager().getConflict(arguments.getPlanetId());
            if (t != null) {
                tournamentUid = t.getUid();
            }
        }

        CrateDataResult result = new CrateDataResult();
        List<String> redeemed = playerSession.getPlayerSettings().getRedeemedTournaments();
        if (tournamentUid == null || !redeemed.contains(tournamentUid)) {
            if (tournamentUid != null) {
                redeemed.add(tournamentUid);
            }
            CrateData crate = CrateService.createAndStoreCrate(
                    playerSession, "lc_daily", "tournament", arguments.getPlanetId(), now);
            result.setCrateData(crate);
            playerSession.savePlayerKeepAlive();
        }
        return result;
    }

    @Override
    protected PlayerStoreTournamentRedeem parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStoreTournamentRedeem.class);
    }

    @Override
    public String getAction() {
        return "player.store.tournament.redeem";
    }

    public String getUid() {
        return uid;
    }

    public String getPlanetId() {
        return planetId;
    }
}
