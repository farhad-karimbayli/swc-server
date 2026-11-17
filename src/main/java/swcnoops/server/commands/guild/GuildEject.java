package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.SquadMsgType;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;
import swcnoops.server.session.SessionManager;

public class GuildEject extends AbstractCommandAction<GuildEject, CommandResult> {
    private String memberId;

    @Override
    protected CommandResult execute(GuildEject arguments, long time) throws Exception {
        SessionManager sessionManager = ServiceFactory.instance().getSessionManager();
        PlayerSession playerSession = sessionManager.getPlayerSession(arguments.getPlayerId());
        GuildSession oldSquad = playerSession.getGuildSession();
        if (oldSquad == null) {
            return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_NOT_IN_GUILD);
        }
        if (!GuildHelper.hasGuildRank(oldSquad, arguments.getPlayerId(), false)) {
            return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_NOT_ENOUGH_GUILD_RANK);
        }
        PlayerSession ejectPlayerSession = sessionManager.getPlayerSession(arguments.getMemberId());
        ejectPlayerSession.getDonatedTroops().clear();
        oldSquad.leave(ejectPlayerSession, SquadMsgType.ejected);
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected GuildEject parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildEject.class);
    }

    @Override
    public String getAction() {
        return "guild.eject";
    }

    public String getMemberId() {
        return memberId;
    }
}
