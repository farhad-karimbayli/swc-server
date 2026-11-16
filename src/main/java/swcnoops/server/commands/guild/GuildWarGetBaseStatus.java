package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.guild.response.GuildWarGetBaseStatusResult;
import swcnoops.server.datasource.War;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.BuffBase;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;

public class GuildWarGetBaseStatus extends AbstractCommandAction<GuildWarGetBaseStatus, GuildWarGetBaseStatusResult> {
    private String buffBaseUid;

    @Override
    protected GuildWarGetBaseStatusResult execute(GuildWarGetBaseStatus arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        GuildWarGetBaseStatusResult result = new GuildWarGetBaseStatusResult();
        result.buffUid = arguments.getBuffBaseUid();

        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession != null && guildSession.getCurrentWar() != null) {
            War war = guildSession.getCurrentWar();
            BuffBase buffBase = war.getBuffBases().get(arguments.getBuffBaseUid());
            if (buffBase != null) {
                result.ownerId = buffBase.ownerId;
                result.level = buffBase.level;
                if (buffBase.battleInProgress) {
                    long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
                    result.currentlyDefending.put("expiration", now + ServiceFactory.instance().getConfig().attackDuration);
                }
            }
        }
        return result;
    }

    @Override
    protected GuildWarGetBaseStatus parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildWarGetBaseStatus.class);
    }

    @Override
    public String getAction() {
        return "guild.war.getBaseStatus";
    }

    public String getBuffBaseUid() {
        return buffBaseUid;
    }
}
