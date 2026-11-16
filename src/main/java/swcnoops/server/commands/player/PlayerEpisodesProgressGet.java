package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerEpisodesProgressGetCommandResult;
import swcnoops.server.game.EpisodeData;
import swcnoops.server.game.EpisodeTaskData;
import swcnoops.server.game.PatchData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.EpisodeProgressInfo;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class PlayerEpisodesProgressGet extends AbstractCommandAction<PlayerEpisodesProgressGet, PlayerEpisodesProgressGetCommandResult> {
    @Override
    protected PlayerEpisodesProgressGetCommandResult execute(PlayerEpisodesProgressGet arguments, long time) throws Exception {
        if (arguments == null || arguments.getPlayerId() == null) {
            return ServiceFactory.instance().getJsonParser()
                    .toObjectFromResource("templates/playerEpisodesProgressGet.json",
                            PlayerEpisodesProgressGetCommandResult.class);
        }

        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        EpisodeProgressInfo info = ensureEpisodeProgress(playerSession, now, true);

        PlayerEpisodesProgressGetCommandResult response = new PlayerEpisodesProgressGetCommandResult();
        response.uid = info.uid;
        response.endTime = info.endTime;
        response.finishedTasks = info.finishedTasks;
        response.currentTaskIndex = info.currentTaskIndex;
        response.introStoryViewed = info.introStoryViewed;
        response.currentTask = info.currentTask;
        response.grind = info.grind;
        return response;
    }

    public static EpisodeProgressInfo ensureEpisodeProgress(PlayerSession playerSession, long now, boolean persist) {
        EpisodeProgressInfo info = playerSession.getPlayerSettings().getEpisodeProgressInfo();
        if (info != null && info.uid != null && info.endTime != null && info.endTime > now) {
            return info;
        }

        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
        Map<String, EpisodeData> episodeMap = patchData.getMap(EpisodeData.class);
        EpisodeData activeEpisode = null;
        if (episodeMap != null) {
            for (EpisodeData ep : episodeMap.values()) {
                if (ep.getTasks() != null && !ep.getTasks().isEmpty()) {
                    activeEpisode = ep;
                }
            }
        }

        info = new EpisodeProgressInfo();
        if (activeEpisode != null) {
            info.uid = activeEpisode.getUid();
            info.endTime = now + 30L * 24L * 3600L;
            info.currentTaskIndex = 0;
            info.introStoryViewed = false;
            String firstTaskUid = activeEpisode.getTasks().get(0);
            info.currentTask = buildTaskProgress(firstTaskUid, patchData, now);
        }
        playerSession.getPlayerSettings().setEpisodeProgressInfo(info);
        if (persist) {
            playerSession.savePlayerKeepAlive();
        }
        return info;
    }

    public static EpisodeProgressInfo.EpisodeTaskProgress buildTaskProgress(String taskUid, PatchData patchData, long now) {
        EpisodeProgressInfo.EpisodeTaskProgress progress = new EpisodeProgressInfo.EpisodeTaskProgress();
        progress.uid = taskUid;
        Map<String, EpisodeTaskData> taskMap = patchData.getMap(EpisodeTaskData.class);
        EpisodeTaskData taskData = taskMap != null ? taskMap.get(taskUid) : null;
        if (taskData != null && taskData.getActions() != null && !taskData.getActions().isEmpty()) {
            progress.actionUID = taskData.getActions().get(0);
        }
        progress.count = 0;
        progress.target = 1;
        progress.timeGateEnd = now;
        return progress;
    }

    @Override
    protected PlayerEpisodesProgressGet parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerEpisodesProgressGet.class);
    }

    @Override
    public String getAction() {
        return "player.episodes.progress.get";
    }
}
