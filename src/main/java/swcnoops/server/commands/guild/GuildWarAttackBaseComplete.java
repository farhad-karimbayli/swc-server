package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.guild.response.BattleIdResult;
import swcnoops.server.commands.player.PlayerBattleComplete;
import swcnoops.server.datasource.War;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.BuffBase;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;

public class GuildWarAttackBaseComplete extends PlayerBattleComplete<GuildWarAttackBaseComplete, CommandResult> {
    private String buffBaseUid;

    @Override
    protected CommandResult execute(GuildWarAttackBaseComplete arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager().getPlayerSession(arguments.getPlayerId());
        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession != null && guildSession.getCurrentWar() != null) {
            War war = guildSession.getCurrentWar();
            BuffBase buffBase = arguments.getBuffBaseUid() != null
                    ? war.getBuffBases().get(arguments.getBuffBaseUid())
                    : null;
            if (buffBase != null) {
                buffBase.battleInProgress = false;
                buffBase.currentlyDefending = null;
                if (arguments.getStars() > 0) {
                    int maxLevel = GuildWarAttackBaseStart.getMaxBuffBaseLevel(arguments.getBuffBaseUid());
                    buffBase.ownerId = guildSession.getGuildId();
                    buffBase.level = Math.min(maxLevel, Math.max(1, buffBase.level + 1));
                }
                ServiceFactory.instance().getPlayerDatasource().saveWar(war);
            }
        }
        return new BattleIdResult(ResponseHelper.RECEIPT_STATUS_COMPLETE);
    }

    @Override
    protected GuildWarAttackBaseComplete parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildWarAttackBaseComplete.class);
    }

    @Override
    public String getAction() {
        return "guild.war.attackBase.complete";
    }

    public String getBuffBaseUid() {
        return buffBaseUid;
    }
}
