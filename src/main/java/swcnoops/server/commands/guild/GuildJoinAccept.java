package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.guild.response.GuildMemberResult;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.Member;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;
import swcnoops.server.session.SessionManager;

public class GuildJoinAccept extends AbstractCommandAction<GuildJoinAccept, GuildMemberResult> {
    private String memberId;

    @Override
    protected GuildMemberResult execute(GuildJoinAccept arguments, long time) throws Exception {
        SessionManager sessionManager = ServiceFactory.instance().getSessionManager();
        PlayerSession playerSession = sessionManager.getPlayerSession(arguments.getPlayerId());
        PlayerSession memberSession = sessionManager.getPlayerSession(arguments.getMemberId());

        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession == null) {
            return new GuildMemberResult(ResponseHelper.STATUS_CODE_NOT_IN_GUILD);
        }
        if (!GuildHelper.hasGuildRank(guildSession, arguments.getPlayerId(), false)) {
            return new GuildMemberResult(ResponseHelper.STATUS_CODE_NOT_ENOUGH_GUILD_RANK);
        }
        guildSession.joinRequestAccepted(arguments.getPlayerId(), memberSession);

        Member member = GuildHelper.createMember(memberSession);
        return new GuildMemberResult(member);
    }

    @Override
    protected GuildJoinAccept parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildJoinAccept.class);
    }

    @Override
    public String getAction() {
        return "guild.join.accept";
    }

    public String getMemberId() {
        return memberId;
    }
}
