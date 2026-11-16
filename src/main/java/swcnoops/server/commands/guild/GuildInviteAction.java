package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;
import swcnoops.server.session.SessionManager;

/**
 * Handles guild.invite, guild.invite.accept, and guild.invite.reject.
 */
public class GuildInviteAction extends AbstractCommandAction<GuildInviteAction, CommandResult> {
    private final String actionName;
    private String guildId;
    private String recipientPlayerId;

    public GuildInviteAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(GuildInviteAction arguments, long time) throws Exception {
        if ("guild.invite.accept".equals(this.actionName)
                && arguments.getGuildId() != null && !arguments.getGuildId().isEmpty()) {
            SessionManager sm = ServiceFactory.instance().getSessionManager();
            PlayerSession playerSession = sm.getPlayerSession(arguments.getPlayerId());
            GuildSession guildSession = sm.getGuildSession(arguments.getGuildId());
            if (guildSession != null && playerSession != null) {
                guildSession.join(playerSession);
            }
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected GuildInviteAction parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildInviteAction.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getRecipientPlayerId() {
        return recipientPlayerId;
    }
}
