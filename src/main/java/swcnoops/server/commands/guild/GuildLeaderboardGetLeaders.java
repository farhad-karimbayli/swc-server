package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.guild.response.LeaderboardResult;
import swcnoops.server.datasource.SquadInfo;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.*;

import java.util.List;

public class GuildLeaderboardGetLeaders extends AbstractCommandAction<GuildLeaderboardGetLeaders, LeaderboardResult> {
    @Override
    protected LeaderboardResult execute(GuildLeaderboardGetLeaders arguments, long time) throws Exception {
        LeaderboardResult result = new LeaderboardResult();
        List<SquadInfo> squads = ServiceFactory.instance().getPlayerDatasource().getSquadLeaderboard(50);

        int rank = 1;
        for (SquadInfo squad : squads) {
            GuildBoardDetail detail = new GuildBoardDetail();
            detail._id = squad._id;
            detail.rank = rank++;
            detail.value = squad.score;
            detail.account = new GuildAccount();
            detail.account._id = squad._id;
            detail.account.manimal = new GuildManimal();
            detail.account.manimal.data = new GuildData();
            detail.account.manimal.data.name = squad.name;
            detail.account.manimal.data.icon = squad.icon;
            detail.account.manimal.data.level = squad.level > 0 ? squad.level : 1;
            int memberCount = squad.getSquadMembers() != null ? squad.getSquadMembers().size() : squad.members;
            detail.account.manimal.data.memberCount = memberCount;
            detail.account.manimal.data.activeMemberCount = memberCount;
            detail.account.manimal.data.membershipRestrictions = new MembershipRestrictions();
            detail.account.manimal.data.membershipRestrictions.faction = squad.faction;
            detail.account.manimal.data.membershipRestrictions.openEnrollment = squad.openEnrollment;
            detail.account.manimal.data.membershipRestrictions.minScoreAtEnrollment = squad.minScore;
            result.getLeaders().add(detail);
        }
        return result;
    }

    @Override
    protected GuildLeaderboardGetLeaders parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildLeaderboardGetLeaders.class);
    }

    @Override
    public String getAction() {
        return "guild.leaderboard.getLeaders";
    }
}
