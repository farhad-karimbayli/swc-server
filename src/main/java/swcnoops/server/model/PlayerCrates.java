package swcnoops.server.model;

public class PlayerCrates {
    public Available available = new Available();
    public InProgress inProgress = new InProgress();
    public long next = 1;
    public long nextDailyCrateTime;
    public Long nextHolonetTime;
}
