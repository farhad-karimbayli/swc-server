package swcnoops.server.game;

public class ShardShopSeriesData implements JoeData {
    private String uid;
    private String startDate;
    private String endDate;
    private String pool_0;
    private String pool_1;
    private String pool_2;
    private String pool_3;
    private String pool_4;

    @Override
    public String getUid() {
        return uid;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getPool_0() {
        return pool_0;
    }

    public String getPool_1() {
        return pool_1;
    }

    public String getPool_2() {
        return pool_2;
    }

    public String getPool_3() {
        return pool_3;
    }

    public String getPool_4() {
        return pool_4;
    }
}
