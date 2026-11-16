package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerStoreShardOffersGetResult;
import swcnoops.server.game.*;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.FactionType;
import swcnoops.server.model.ShardShopData;
import swcnoops.server.session.PlayerSession;

import java.util.*;

public class PlayerStoreShardOffersGet extends AbstractCommandAction<PlayerStoreShardOffersGet, PlayerStoreShardOffersGetResult> {
    @Override
    protected PlayerStoreShardOffersGetResult execute(PlayerStoreShardOffersGet arguments, long time) throws Exception {
        if (arguments == null || arguments.getPlayerId() == null) {
            return new PlayerStoreShardOffersGetResult();
        }
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        ShardShopData shopData = ensureShardShopData(playerSession, now, true);

        PlayerStoreShardOffersGetResult result = new PlayerStoreShardOffersGetResult();
        result.shardShopData = shopData;
        return result;
    }

    public static ShardShopData ensureShardShopData(PlayerSession playerSession, long now, boolean persist) {
        ShardShopData shopData = playerSession.getPlayerSettings().getShardShopData();
        if (shopData != null && shopData.offerExpiration > now && shopData.seriesId != null) {
            return shopData;
        }

        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
        Map<String, ShardShopSeriesData> seriesMap = patchData.getMap(ShardShopSeriesData.class);
        ShardShopSeriesData series = (seriesMap != null && !seriesMap.isEmpty())
                ? seriesMap.values().iterator().next()
                : null;

        shopData = new ShardShopData();
        shopData.seriesId = series != null ? series.getUid() : "sss_elite_3max_scale_A";
        shopData.offerExpiration = now + 24L * 3600L;
        shopData.offsetMinutes = 0;

        if (series != null) {
            FactionType faction = playerSession.getPlayerSettings().getFaction();
            populatePool(shopData.pool_0, series.getPool_0(), faction, patchData);
            populatePool(shopData.pool_1, series.getPool_1(), faction, patchData);
            populatePool(shopData.pool_2, series.getPool_2(), faction, patchData);
            populatePool(shopData.pool_3, series.getPool_3(), faction, patchData);
            populatePool(shopData.pool_4, series.getPool_4(), faction, patchData);
        }

        playerSession.getPlayerSettings().setShardShopData(shopData);
        if (persist) {
            playerSession.savePlayerKeepAlive();
        }
        return shopData;
    }

    private static void populatePool(Map<String, Integer> poolOut, String poolUid, FactionType faction, PatchData patchData) {
        if (poolUid == null || poolUid.isEmpty()) {
            return;
        }
        Map<String, ShardShopPoolData> poolMap = patchData.getMap(ShardShopPoolData.class);
        ShardShopPoolData pool = poolMap != null ? poolMap.get(poolUid) : null;
        if (pool == null) {
            return;
        }
        String supplyPool = faction == FactionType.rebel
                ? pool.getRebelCrateSupplyPool()
                : pool.getEmpireCrateSupplyPool();
        Map<String, CrateSupplyData> supplyMap = patchData.getMap(CrateSupplyData.class);
        if (supplyMap == null || supplyPool == null) {
            return;
        }
        String factionName = faction != null ? faction.name() : FactionType.empire.name();
        for (CrateSupplyData supply : supplyMap.values()) {
            if (supply.getCrateSupplyPoolUid() != null && supply.getCrateSupplyPoolUid().contains(supplyPool)) {
                if (supply.getFaction() == null || supply.getFaction().equalsIgnoreCase(factionName)) {
                    poolOut.put(supply.getUid(), 0);
                    if (poolOut.size() >= 3) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected PlayerStoreShardOffersGet parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerStoreShardOffersGet.class);
    }

    @Override
    public String getAction() {
        return "player.store.shard.offers.get";
    }
}
