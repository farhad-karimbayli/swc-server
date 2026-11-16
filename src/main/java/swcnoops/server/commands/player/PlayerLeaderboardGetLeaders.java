package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.LeaderboardResult;
import swcnoops.server.datasource.Player;
import swcnoops.server.datasource.PlayerSettings;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.*;
import swcnoops.server.model.mini.MiniPlayerModel;

import java.util.List;

public class PlayerLeaderboardGetLeaders extends AbstractCommandAction<PlayerLeaderboardGetLeaders, LeaderboardResult> {
    private String planetUid;

    @Override
    protected LeaderboardResult execute(PlayerLeaderboardGetLeaders arguments, long time) throws Exception {
        LeaderboardResult result = new LeaderboardResult();
        List<Player> players = ServiceFactory.instance().getPlayerDatasource()
                .getPlayerLeaderboard(arguments.getPlanetUid(), 50);

        int rank = 1;
        for (Player p : players) {
            result.getLeaders().add(toPlayerEntity(p, rank++));
        }
        return result;
    }

    static PlayerEntity toPlayerEntity(Player p, int rank) {
        PlayerSettings s = p.getPlayerSettings();
        PlayerEntity entity = new PlayerEntity();
        entity._id = p.getPlayerId();
        entity.rank = rank;
        entity.value = (s != null && s.getScalars() != null) ? s.getScalars().attackRating : 0;
        entity.account = new PlayerAccount();
        entity.account.manimal = new PlayerManimal();
        entity.account.manimal.data = new PlayerData();
        entity.account.manimal.data.name = s != null ? s.getName() : null;
        entity.account.manimal.data.scalars = new LeaderboardBattleHistory();
        if (s != null && s.getScalars() != null) {
            entity.account.manimal.data.scalars.attacksWon = s.getScalars().attacksWon;
            entity.account.manimal.data.scalars.defensesWon = s.getScalars().defensesWon;
        }
        entity.account.manimal.data.playerModel = new MiniPlayerModel();
        if (s != null) {
            entity.account.manimal.data.playerModel.faction = s.getFaction();
            if (s.getGuildId() != null) {
                entity.account.manimal.data.playerModel.guildInfo = new GuildInfo();
                entity.account.manimal.data.playerModel.guildInfo.guildId = s.getGuildId();
                entity.account.manimal.data.playerModel.guildInfo.guildName = s.getGuildName();
            }
            if (s.getBaseMap() != null) {
                entity.account.manimal.data.playerModel.map = new PlayerMap();
                entity.account.manimal.data.playerModel.map.planet = s.getBaseMap().planet;
            }
        }
        return entity;
    }

    @Override
    protected PlayerLeaderboardGetLeaders parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerLeaderboardGetLeaders.class);
    }

    @Override
    public String getAction() {
        return "player.leaderboard.getLeaders";
    }

    public String getPlanetUid() {
        return planetUid;
    }
}
