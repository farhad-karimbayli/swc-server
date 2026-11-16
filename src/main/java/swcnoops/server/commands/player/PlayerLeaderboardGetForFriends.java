package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.LeaderboardResult;
import swcnoops.server.datasource.Player;
import swcnoops.server.json.JsonParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerLeaderboardGetForFriends extends AbstractCommandAction<PlayerLeaderboardGetForFriends, LeaderboardResult> {
    private String friendIds;

    @Override
    protected LeaderboardResult execute(PlayerLeaderboardGetForFriends arguments, long time) throws Exception {
        LeaderboardResult result = new LeaderboardResult();
        List<String> ids = (arguments.getFriendIds() == null || arguments.getFriendIds().trim().isEmpty())
                ? Collections.emptyList()
                : Arrays.stream(arguments.getFriendIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

        List<Player> players = ServiceFactory.instance().getPlayerDatasource().getPlayersByIds(ids);
        int rank = 1;
        for (Player p : players) {
            result.getLeaders().add(PlayerLeaderboardGetLeaders.toPlayerEntity(p, rank++));
        }
        return result;
    }

    @Override
    protected PlayerLeaderboardGetForFriends parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerLeaderboardGetForFriends.class);
    }

    @Override
    public String getAction() {
        return "player.leaderboard.getForFriends";
    }

    public String getFriendIds() {
        return friendIds;
    }
}
