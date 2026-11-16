package swcnoops.server.model;

import java.util.HashMap;
import java.util.Map;

public class ShardShopData {
    public long offerExpiration;
    public long offsetMinutes;
    public Map<String, Integer> pool_0 = new HashMap<>();
    public Map<String, Integer> pool_1 = new HashMap<>();
    public Map<String, Integer> pool_2 = new HashMap<>();
    public Map<String, Integer> pool_3 = new HashMap<>();
    public Map<String, Integer> pool_4 = new HashMap<>();
    public String seriesId;
}
