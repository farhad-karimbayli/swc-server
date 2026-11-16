package swcnoops.server.game;

import java.util.List;

public class EpisodeData implements JoeData {
    private String uid;
    private String duration;
    private String grindTask;
    private String panel;
    private String pointScale;
    private String startDate;
    private List<String> tasks;

    @Override
    public String getUid() {
        return uid;
    }

    public String getDuration() {
        return duration;
    }

    public String getGrindTask() {
        return grindTask;
    }

    public String getPanel() {
        return panel;
    }

    public String getPointScale() {
        return pointScale;
    }

    public String getStartDate() {
        return startDate;
    }

    public List<String> getTasks() {
        return tasks;
    }
}
