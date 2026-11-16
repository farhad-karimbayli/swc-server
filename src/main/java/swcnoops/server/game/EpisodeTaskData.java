package swcnoops.server.game;

import java.util.List;

public class EpisodeTaskData implements JoeData {
    private String uid;
    private List<String> actions;
    private String crateId;
    private int timeGate;
    private boolean isSignificant;

    @Override
    public String getUid() {
        return uid;
    }

    public List<String> getActions() {
        return actions;
    }

    public String getCrateId() {
        return crateId;
    }

    public int getTimeGate() {
        return timeGate;
    }

    public boolean isSignificant() {
        return isSignificant;
    }
}
