package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.game.CrateSupplyData;
import swcnoops.server.game.PatchData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.ShardShopData;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class PlayerStoreShardOffersBuy extends AbstractCommandAction<PlayerStoreShardOffersBuy, CommandResult> {
    private static final int MAX_SLOT_PURCHASES = 10;
    private String poolSlotId;
    private int poolIndex;
    private String supplyUid;

    @Override
    protected CommandResult execute(PlayerStoreShardOffersBuy arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        ShardShopData shopData = PlayerStoreShardOffersGet.ensureShardShopData(playerSession, now, false);

        Map<String, Integer> targetPool = getPool(shopData, arguments.getPoolIndex());
        if (targetPool != null && arguments.getPoolSlotId() != null && targetPool.containsKey(arguments.getPoolSlotId())) {
            int bought = targetPool.getOrDefault(arguments.getPoolSlotId(), 0);
            if (!ServiceFactory.instance().getConfig().freeResources && bought >= MAX_SLOT_PURCHASES) {
                return ResponseHelper.newStatusResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
            }
        }

        String targetSupply = arguments.getSupplyUid();
        if (targetSupply == null || targetSupply.isEmpty()) {
            targetSupply = arguments.getPoolSlotId();
        }
        if (targetSupply != null && !targetSupply.isEmpty()) {
            PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
            Map<String, CrateSupplyData> supplyMap = patchData.getMap(CrateSupplyData.class);
            CrateSupplyData supply = supplyMap != null ? supplyMap.get(targetSupply) : null;
            String shardUid = (supply != null && supply.getRewardUid() != null)
                    ? (supply.getRewardUid().startsWith("shrd_") ? supply.getRewardUid() : "shrd_" + supply.getRewardUid())
                    : targetSupply;
            int grant = (supply != null && supply.getAmount() > 0) ? supply.getAmount() : 5;
            Map<String, Integer> shards = playerSession.getPlayerSettings().getShards();
            shards.put(shardUid, shards.getOrDefault(shardUid, 0) + grant);
        }

        incrementPoolPurchase(shopData, arguments.getPoolIndex(), arguments.getPoolSlotId());
        playerSession.savePlayerKeepAlive();
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    private Map<String, Integer> getPool(ShardShopData shopData, int idx) {
        switch (idx) {
            case 1: return shopData.pool_1;
            case 2: return shopData.pool_2;
            case 3: return shopData.pool_3;
            case 4: return shopData.pool_4;
            default: return shopData.pool_0;
        }
    }

    private void incrementPoolPurchase(ShardShopData shopData, int idx, String slotId) {
        Map<String, Integer> pool = getPool(shopData, idx);
        if (pool != null && slotId != null && pool.containsKey(slotId)) {
            pool.put(slotId, pool.get(slotId) + 1);
        }
    }

    @Override
    protected PlayerStoreShardOffersBuy parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStoreShardOffersBuy.class);
    }

    @Override
    public String getAction() {
        return "player.store.shard.offers.buy";
    }

    public String getPoolSlotId() {
        return poolSlotId;
    }

    public int getPoolIndex() {
        return poolIndex;
    }

    public String getSupplyUid() {
        return supplyUid;
    }
}
