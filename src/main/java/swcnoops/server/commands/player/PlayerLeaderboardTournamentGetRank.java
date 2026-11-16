package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.TournamentRankResult;
import swcnoops.server.datasource.TournamentStat;
import swcnoops.server.game.ConflictManager;
import swcnoops.server.game.TournamentData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.Tournament;
import swcnoops.server.requests.CommandResult;

/**
 * This gets called on PvP find screen to update the conflict status at the bottom for that planet
 */
public class PlayerLeaderboardTournamentGetRank extends AbstractCommandAction<PlayerLeaderboardTournamentGetRank, CommandResult> {
    private String planetId;

    @Override
    protected CommandResult execute(PlayerLeaderboardTournamentGetRank arguments, long time) throws Exception {
        ConflictManager conflictManager = ServiceFactory.instance().getGameDataManager().getConflictManager();
        TournamentData tournamentData = conflictManager.getConflict(arguments.getPlanetId());

        TournamentRankResult tournamentRankResult = null;
        if (tournamentData != null && tournamentData.isActive(time)) {
            TournamentStat tournamentStat = ServiceFactory.instance().getPlayerDatasource()
                    .getTournamentPlayerRank(tournamentData.getUid(), arguments.getPlayerId());

            tournamentRankResult = new TournamentRankResult();
            tournamentRankResult.tournament = new Tournament();
            tournamentRankResult.tournament.uid = tournamentData.getUid();
            tournamentRankResult.tournament.bestTier = 1;
            tournamentRankResult.tournament.redeemedRewards = null;

            if (tournamentStat != null) {
                float pct = tournamentStat.percentile != null ? tournamentStat.percentile : 100f;
                tournamentRankResult.tournament.percentile = pct;
                tournamentRankResult.tournament.rating = tournamentStat.value;
                tournamentRankResult.tournament.collected = false;
                tournamentRankResult.percentile = pct;
                tournamentRankResult.tier = null;
            } else {
                tournamentRankResult.tournament.percentile = 100f;
                tournamentRankResult.tournament.rating = 0;
                tournamentRankResult.tournament.collected = false;
                tournamentRankResult.percentile = 100f;
                tournamentRankResult.tier = null;
            }
        }

        return tournamentRankResult;
    }

    @Override
    protected PlayerLeaderboardTournamentGetRank parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerLeaderboardTournamentGetRank.class);
    }

    @Override
    public String getAction() {
        return "player.leaderboard.tournament.getRank";
    }

    public String getPlanetId() {
        return planetId;
    }

    public void setPlanetId(String planetId) {
        this.planetId = planetId;
    }
}
