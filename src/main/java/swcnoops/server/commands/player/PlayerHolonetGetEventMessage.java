package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.StringListResult;
import swcnoops.server.game.TransmissionData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.FactionType;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class PlayerHolonetGetEventMessage extends AbstractCommandAction<PlayerHolonetGetEventMessage, StringListResult> {
    @Override
    protected StringListResult execute(PlayerHolonetGetEventMessage arguments, long time) throws Exception {
        StringListResult result = new StringListResult();
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        FactionType faction = playerSession.getPlayerSettings().getFaction();
        String factionStr = faction != null ? faction.name() : FactionType.empire.name();

        Map<String, TransmissionData> map = ServiceFactory.instance().getGameDataManager()
                .getPatchData().getMap(TransmissionData.class);
        if (map != null) {
            for (TransmissionData t : map.values()) {
                if (t.getFaction() == null || t.getFaction().isEmpty()
                        || t.getFaction().equalsIgnoreCase(factionStr)) {
                    result.getData().add(t.getUid());
                    if (result.getData().size() >= 3) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected PlayerHolonetGetEventMessage parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerHolonetGetEventMessage.class);
    }

    @Override
    public String getAction() {
        return "player.holonet.getEventMessage";
    }
}
