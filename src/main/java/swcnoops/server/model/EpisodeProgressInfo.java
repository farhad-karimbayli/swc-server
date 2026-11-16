package swcnoops.server.model;

import java.util.ArrayList;
import java.util.List;

public class EpisodeProgressInfo {
    public String uid;
    public Long endTime;
    public List<String> finishedTasks = new ArrayList<>();
    public int currentTaskIndex;
    public boolean introStoryViewed;
    public EpisodeTaskProgress currentTask;
    public Grind grind;

    public static class EpisodeTaskProgress {
        public String uid;
        public String actionUID;
        public int count;
        public int target;
        public long timeGateEnd;
        public boolean completed;
        public boolean claimed;
    }
}
