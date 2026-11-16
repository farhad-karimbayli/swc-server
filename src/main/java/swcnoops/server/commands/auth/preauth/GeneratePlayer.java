package swcnoops.server.commands.auth.preauth;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.Command;
import swcnoops.server.commands.player.response.PlayerLoginCommandResult;
import swcnoops.server.json.JsonParser;
import swcnoops.server.commands.auth.preauth.response.GeneratePlayerCommandResult;
import swcnoops.server.requests.EmptyMessage;
import swcnoops.server.requests.Messages;

import java.util.HashMap;
import java.util.Map;

public class GeneratePlayer extends AbstractCommandAction<GeneratePlayer, GeneratePlayerCommandResult> {
    private String locale;
    @Override
    final public String getAction() {
        return "auth.preauth.generatePlayer";
    }

    @Override
    protected GeneratePlayer parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GeneratePlayer.class);
    }

    @Override
    protected GeneratePlayerCommandResult execute(GeneratePlayer arguments, long time) throws Exception {
        GeneratePlayerCommandResult generatePlayerResponse = GeneratePlayerCommandResult.newInstance();
        PlayerLoginCommandResult template = loadPlayerTemplate();
        Map<String, String> sharedPrefs = new HashMap<>();
        if (arguments.getLocale() != null && !arguments.getLocale().isEmpty()) {
            sharedPrefs.put("locale", arguments.getLocale());
        }
        ServiceFactory.instance().getPlayerDatasource()
                .newPlayer(generatePlayerResponse.playerId, generatePlayerResponse.secret, template.playerModel, sharedPrefs, "new");
        return generatePlayerResponse;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    private PlayerLoginCommandResult loadPlayerTemplate() throws Exception {
        PlayerLoginCommandResult response = ServiceFactory.instance().getJsonParser()
                .toObjectFromResource(ServiceFactory.instance().getConfig().newPlayerTemplate, PlayerLoginCommandResult.class);
        return response;
    }

    @Override
    protected Messages createMessage(Command command, GeneratePlayerCommandResult commandResult) {
        return EmptyMessage.instance;
    }

    @Override
    public boolean canAttachGuildNotifications() {
        return false;
    }

    @Override
    public boolean isAuthCommand() {
        return true;
    }
}
