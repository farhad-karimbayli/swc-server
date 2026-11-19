package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerStoreCrateBuyResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.game.LimitedEditionItemData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.model.CurrencyType;
import swcnoops.server.model.InventoryStorage;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.CurrencyDelta;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class PlayerStoreCrateBuy extends AbstractCommandAction<PlayerStoreCrateBuy, CommandResult> {
    private String crateId;
    private String leUid;

    @Override
    protected CommandResult execute(PlayerStoreCrateBuy arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        if (!ServiceFactory.instance().getConfig().freeResources && arguments.getLeUid() != null) {
            Map<String, LimitedEditionItemData> leMap = ServiceFactory.instance().getGameDataManager()
                    .getPatchData().getMap(LimitedEditionItemData.class);
            LimitedEditionItemData le = leMap != null ? leMap.get(arguments.getLeUid()) : null;
            if (le != null) {
                InventoryStorage storage = playerSession.getInventoryManager().getObjectForReading();
                if (le.getCrystals() > 0) {
                    if (storage.crystals.amount < le.getCrystals()) {
                        return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
                    }
                    playerSession.processInventoryStorage(new CurrencyDelta(le.getCrystals(), le.getCrystals(), CurrencyType.crystals, true));
                } else if (le.getCredits() > 0) {
                    if (storage.credits.amount < le.getCredits()) {
                        return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
                    }
                    playerSession.processInventoryStorage(new CurrencyDelta(le.getCredits(), le.getCredits(), CurrencyType.credits, true));
                } else if (le.getMaterials() > 0) {
                    if (storage.materials.amount < le.getMaterials()) {
                        return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
                    }
                    playerSession.processInventoryStorage(new CurrencyDelta(le.getMaterials(), le.getMaterials(), CurrencyType.materials, true));
                } else if (le.getContraband() > 0) {
                    if (storage.contraband.amount < le.getContraband()) {
                        return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
                    }
                    playerSession.processInventoryStorage(new CurrencyDelta(le.getContraband(), le.getContraband(), CurrencyType.contraband, true));
                }
            }
        }

        CrateData crate = CrateService.createAndStoreCrate(playerSession, arguments.getCrateId(), "store", null, now);
        playerSession.savePlayerKeepAlive();

        PlayerStoreCrateBuyResult result = new PlayerStoreCrateBuyResult();
        result.getCrateData().uid = crate.uid;
        result.getCrateData().crateId = crate.crateId;
        result.getCrateData().context = crate.context;
        result.getCrateData().planet = crate.planet;
        result.getCrateData().hqLevel = crate.hqLevel;
        result.getCrateData().received = crate.received;
        result.getCrateData().expires = crate.expires;
        result.getCrateData().doesExpire = crate.doesExpire;
        result.getCrateData().claimed = crate.claimed;
        result.getCrateData().resolvedSupplies.addAll(crate.resolvedSupplies);
        return result;
    }

    @Override
    protected PlayerStoreCrateBuy parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStoreCrateBuy.class);
    }

    @Override
    public String getAction() {
        return "player.store.crate.buy";
    }

    public String getCrateId() {
        return crateId;
    }

    public String getLeUid() {
        return leUid;
    }
}
