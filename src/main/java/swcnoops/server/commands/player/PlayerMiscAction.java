package swcnoops.server.commands.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;

/**
 * Handles auxiliary client lifecycle commands:
 * player.error, player.inventory.transfer, player.account.external.unregister, player.money.receipt.verify.
 */
public class PlayerMiscAction extends AbstractCommandAction<PlayerMiscAction, CommandResult> {
    private static final Logger LOG = LoggerFactory.getLogger(PlayerMiscAction.class);
    private final String actionName;
    private String message;
    private String clientState;

    public PlayerMiscAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(PlayerMiscAction arguments, long time) throws Exception {
        if ("player.error".equals(this.actionName)) {
            LOG.warn("Client error from player {}: state={} message={}",
                    arguments.getPlayerId(), arguments.getClientState(), arguments.getMessage());
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerMiscAction parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerMiscAction.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }

    public String getMessage() {
        return message;
    }

    public String getClientState() {
        return clientState;
    }
}
