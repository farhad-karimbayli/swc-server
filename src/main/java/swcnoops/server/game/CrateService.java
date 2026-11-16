package swcnoops.server.game;

import swcnoops.server.ServiceFactory;
import swcnoops.server.datasource.PlayerSettings;
import swcnoops.server.model.*;
import swcnoops.server.session.CurrencyDelta;
import swcnoops.server.session.PlayerSession;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves supply crates from olc.json (Crate, CrateSupply, CrateSupplyPool, CrateSupplyScale)
 * and awards currencies and upgrade shards to a player session.
 */
public final class CrateService {
    private CrateService() {
    }

    public static CrateData createAndStoreCrate(PlayerSession playerSession, String crateId, String context,
                                                String planet, long time) {
        PlayerSettings settings = playerSession.getPlayerSettings();
        PlayerCrates crates = settings.getCrates();
        long seq = Math.max(1L, crates.next++);
        String uid = "crt_" + seq;

        int hqLevel = Math.max(1, settings.getHqLevel());
        if (playerSession.getHeadQuarter() != null && playerSession.getHeadQuarter().getBuildingData() != null) {
            hqLevel = playerSession.getHeadQuarter().getBuildingData().getLevel();
        }
        String resolvedPlanet = (planet != null && !planet.isEmpty()) ? planet
                : (settings.getBaseMap() != null && settings.getBaseMap().planet != null
                    ? settings.getBaseMap().planet : "planet1");

        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
        Map<String, CrateItemData> crateMap = patchData.getMap(CrateItemData.class);
        CrateItemData crateDef = crateMap != null ? crateMap.get(crateId) : null;

        CrateData crate = new CrateData();
        crate.uid = uid;
        crate.crateId = crateId != null ? crateId : "lc_daily";
        crate.context = context;
        crate.planet = resolvedPlanet;
        crate.hqLevel = hqLevel;
        crate.received = time;
        crate.claimed = false;

        long expireMinutes = 0;
        if (crateDef != null && crateDef.getExpirationTime() != null) {
            try {
                expireMinutes = Long.parseLong(crateDef.getExpirationTime());
            } catch (NumberFormatException ignored) {
            }
        }
        if (expireMinutes > 0) {
            crate.doesExpire = true;
            crate.expires = time + expireMinutes * 60L;
        }

        resolveSupplies(crate, crateDef, settings.getFaction(), hqLevel, patchData);
        crates.available.put(crate.uid, crate);
        return crate;
    }

    private static void resolveSupplies(CrateData crate, CrateItemData crateDef, FactionType faction,
                                        int hqLevel, PatchData patchData) {
        Map<String, CrateSupplyData> supplyMap = patchData.getMap(CrateSupplyData.class);
        if (crateDef == null || crateDef.getSupplyPoolUid() == null || supplyMap == null) {
            String factionName = faction != null ? faction.getNameForLookup() : "Empire";
            crate.resolvedSupplies.add(new SupplyData(
                    "lcitem_shrd_eqp" + factionName + "PentagonJetpackTrooper_st3_normal",
                    "lcpool_shrd_st3"));
            return;
        }

        String factionStr = faction != null ? faction.name() : FactionType.empire.name();
        for (String poolUid : crateDef.getSupplyPoolUid()) {
            List<CrateSupplyData> candidates = new ArrayList<>();
            for (CrateSupplyData supply : supplyMap.values()) {
                if (supply.getCrateSupplyPoolUid() == null || !supply.getCrateSupplyPoolUid().contains(poolUid)) {
                    continue;
                }
                if (supply.getFaction() != null && !supply.getFaction().isEmpty()
                        && !supply.getFaction().equalsIgnoreCase(factionStr)) {
                    continue;
                }
                if (supply.getMinHQ() > 0 && hqLevel < supply.getMinHQ()) {
                    continue;
                }
                if (supply.getMaxHQ() > 0 && hqLevel > supply.getMaxHQ()) {
                    continue;
                }
                candidates.add(supply);
            }

            if (!candidates.isEmpty()) {
                CrateSupplyData picked = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                crate.resolvedSupplies.add(new SupplyData(picked.getUid(), poolUid));
            } else {
                Map<String, CrateSupplyPoolData> poolMap = patchData.getMap(CrateSupplyPoolData.class);
                CrateSupplyPoolData pool = poolMap != null ? poolMap.get(poolUid) : null;
                if (pool != null && pool.getFallbackUid() != null) {
                    crate.resolvedSupplies.add(new SupplyData(pool.getFallbackUid(), poolUid));
                }
            }
        }
    }

    public static void awardCrate(PlayerSession playerSession, String crateUid) {
        PlayerSettings settings = playerSession.getPlayerSettings();
        PlayerCrates crates = settings.getCrates();
        CrateData crate = crates.available.remove(crateUid);
        if (crate == null) {
            crate = crates.inProgress.remove(crateUid);
        }
        if (crate == null) {
            return;
        }

        int hqLevel = crate.hqLevel > 0 ? crate.hqLevel : Math.max(1, settings.getHqLevel());
        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
        Map<String, CrateSupplyData> supplyMap = patchData.getMap(CrateSupplyData.class);
        Map<String, CrateSupplyScaleData> scaleMap = patchData.getMap(CrateSupplyScaleData.class);

        if (supplyMap != null && crate.resolvedSupplies != null) {
            for (SupplyData supplyData : crate.resolvedSupplies) {
                CrateSupplyData def = supplyMap.get(supplyData.supplyId);
                if (def == null) {
                    continue;
                }
                int amount = resolveAmount(def, hqLevel, scaleMap);
                if (amount <= 0) {
                    continue;
                }
                applyReward(playerSession, def.getRewardType(), def.getRewardUid(), amount);
            }
        }
    }

    private static int resolveAmount(CrateSupplyData def, int hqLevel, Map<String, CrateSupplyScaleData> scaleMap) {
        if (def.getAmount() > 0) {
            return def.getAmount();
        }
        if (def.getScalingUid() != null && scaleMap != null) {
            CrateSupplyScaleData scale = scaleMap.get(def.getScalingUid());
            if (scale != null) {
                int scaled = scale.getAmountForHq(hqLevel);
                if (scaled > 0) {
                    return scaled;
                }
            }
        }
        return 1;
    }

    private static void applyReward(PlayerSession playerSession, String rewardType, String rewardUid, int amount) {
        if (rewardType == null || rewardUid == null) {
            return;
        }
        switch (rewardType) {
            case "currency":
                CurrencyType currencyType = parseCurrency(rewardUid);
                if (currencyType != null) {
                    playerSession.processInventoryStorage(new CurrencyDelta(amount, amount, currencyType, false));
                }
                break;
            case "shard":
            case "shardTroop":
            case "shardSpecialAttack":
                String shardKey = normalizeShardUid(rewardUid);
                Map<String, Integer> shards = playerSession.getPlayerSettings().getShards();
                shards.put(shardKey, shards.getOrDefault(shardKey, 0) + amount);
                break;
            default:
                break;
        }
    }

    private static CurrencyType parseCurrency(String rewardUid) {
        switch (rewardUid) {
            case "credits": return CurrencyType.credits;
            case "materials": return CurrencyType.materials;
            case "contraband": return CurrencyType.contraband;
            case "crystals": return CurrencyType.crystals;
            default: return null;
        }
    }

    private static String normalizeShardUid(String rewardUid) {
        return rewardUid.startsWith("shrd_") ? rewardUid : "shrd_" + rewardUid;
    }
}
