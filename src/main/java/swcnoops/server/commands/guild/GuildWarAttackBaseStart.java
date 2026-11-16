package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.guild.response.BattleIdResult;
import swcnoops.server.datasource.War;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.BuffBase;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;

import java.util.HashMap;

public class GuildWarAttackBaseStart extends AbstractCommandAction<GuildWarAttackBaseStart, CommandResult> {
    private String buffBaseUid;

    @Override
    protected CommandResult execute(GuildWarAttackBaseStart arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager().getPlayerSession(arguments.getPlayerId());
        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession == null) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_NOT_IN_GUILD);
        }

        War war = guildSession.getCurrentWar();
        if (war == null) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_GUILD_WAR_EXPIRED);
        }

        String battleId = ServiceFactory.createRandomUUID();
        BuffBase buffBase = war.getBuffBases().get(arguments.getBuffBaseUid());
        if (buffBase != null) {
            buffBase.battleInProgress = true;
            buffBase.currentlyDefending = playerSession.getPlayerId();
            ServiceFactory.instance().getPlayerDatasource().saveWar(war);
        }
        return new BattleIdResult(battleId);
    }

    @Override
    protected GuildWarAttackBaseStart parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildWarAttackBaseStart.class);
    }

    @Override
    public String getAction() {
        return "guild.war.attackBase.start";
    }

    public String getBuffBaseUid() {
        return buffBaseUid;
    }
}
