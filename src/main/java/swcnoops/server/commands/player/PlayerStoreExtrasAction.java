package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.model.CurrencyType;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.CurrencyDelta;
import swcnoops.server.session.PlayerSession;

/**
 * Handles store, campaign store, tournament store, and rateMyApp rewards.
 */
public class PlayerStoreExtrasAction extends AbstractCommandAction<PlayerStoreExtrasAction, CommandResult> {
    private final String actionName;
    private String crateId;
    private String uid;

    public PlayerStoreExtrasAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(PlayerStoreExtrasAction arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();

        switch (this.actionName) {
            case "player.award.rateMyApp":
                if (!Boolean.TRUE.equals(playerSession.getPlayerSettings().getSharedPreferences() != null
                        && "1".equals(playerSession.getPlayerSettings().getSharedPreferences().get("rateMyAppAwarded")))) {
                    playerSession.processInventoryStorage(new CurrencyDelta(25, 25, CurrencyType.crystals, false));
                    if (playerSession.getPlayerSettings().getSharedPreferences() != null) {
                        playerSession.getPlayerSettings().getSharedPreferences().put("rateMyAppAwarded", "1");
                    }
                    playerSession.savePlayerKeepAlive();
                }
                return ResponseHelper.SUCCESS_COMMAND_RESULT;

            case "player.campaignStore.buy":
            case "player.store.tournament.buy":
            case "player.store.offers.buy":
                String targetCrate = arguments.getCrateId() != null && !arguments.getCrateId().isEmpty()
                        ? arguments.getCrateId() : "lc_daily";
                CrateData crate = CrateService.createAndStoreCrate(
                        playerSession, targetCrate, this.actionName, null, now);
                playerSession.savePlayerKeepAlive();
                CrateDataResult crateResult = new CrateDataResult();
                crateResult.setCrateData(crate);
                return crateResult;

            default:
                return ResponseHelper.SUCCESS_COMMAND_RESULT;
        }
    }

    @Override
    protected PlayerStoreExtrasAction parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStoreExtrasAction.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }

    public String getCrateId() {
        return crateId;
    }

    public String getUid() {
        return uid;
    }
}
