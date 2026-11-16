package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerStoreOffersGetCommandResult;
import swcnoops.server.game.LimitedEditionItemData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.FactionType;
import swcnoops.server.session.PlayerSession;

import java.util.*;

public class PlayerStoreOffersGet extends AbstractCommandAction<PlayerStoreOffersGet, PlayerStoreOffersGetCommandResult> {
    @Override
    public String getAction() {
        return "player.store.offers.get";
    }

    @Override
    protected PlayerStoreOffersGetCommandResult execute(PlayerStoreOffersGet arguments, long time) throws Exception {
        PlayerStoreOffersGetCommandResult response = new PlayerStoreOffersGetCommandResult();
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        FactionType faction = playerSession.getPlayerSettings().getFaction();
        String factionStr = faction != null ? faction.name() : FactionType.empire.name();

        Map<String, LimitedEditionItemData> leMap = ServiceFactory.instance().getGameDataManager()
                .getPatchData().getMap(LimitedEditionItemData.class);
        if (leMap != null) {
            List<Map<String, Object>> offers = new ArrayList<>();
            for (LimitedEditionItemData item : leMap.values()) {
                if (item.getCrateId() != null && (item.getFaction() == null || item.getFaction().equalsIgnoreCase(factionStr))) {
                    Map<String, Object> offer = new LinkedHashMap<>();
                    offer.put("uid", item.getUid());
                    offer.put("crateId", item.getCrateId());
                    offer.put("crystals", item.getCrystals());
                    offers.add(offer);
                    if (offers.size() >= 6) {
                        break;
                    }
                }
            }
            if (!offers.isEmpty()) {
                response.availableOffer = offers.get(0);
            }
        }
        return response;
    }

    @Override
    protected PlayerStoreOffersGet parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStoreOffersGet.class);
    }
}
