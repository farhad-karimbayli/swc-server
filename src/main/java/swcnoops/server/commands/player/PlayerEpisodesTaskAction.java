package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.game.EpisodeData;
import swcnoops.server.game.EpisodeTaskData;
import swcnoops.server.game.PatchData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.CrateData;
import swcnoops.server.model.EpisodeProgressInfo;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

/**
 * Shared action handler for episode progression commands:
 * intro.viewed, timegate.start, timegate.skip, progress.complete, progress.skip, claim.
 */
public class PlayerEpisodesTaskAction extends AbstractCommandAction<PlayerEpisodesTaskAction, CommandResult> {
    private final String actionName;
    private String actionUID;

    public PlayerEpisodesTaskAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(PlayerEpisodesTaskAction arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        EpisodeProgressInfo info = PlayerEpisodesProgressGet.ensureEpisodeProgress(playerSession, now, false);
        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();

        CommandResult result = ResponseHelper.SUCCESS_COMMAND_RESULT;
        switch (this.actionName) {
            case "player.episodes.tasks.intro.viewed":
                info.introStoryViewed = true;
                break;
            case "player.episodes.tasks.timegate.start":
                if (info.currentTask != null) {
                    Map<String, EpisodeTaskData> taskMap = patchData.getMap(EpisodeTaskData.class);
                    EpisodeTaskData td = taskMap != null ? taskMap.get(info.currentTask.uid) : null;
                    int gate = td != null ? td.getTimeGate() : 0;
                    info.currentTask.timeGateEnd = now + gate;
                }
                break;
            case "player.episodes.tasks.timegate.skip":
                if (info.currentTask != null) {
                    info.currentTask.timeGateEnd = now;
                }
                break;
            case "player.episodes.tasks.progress.complete":
            case "player.episodes.tasks.progress.skip":
                if (info.currentTask != null) {
                    info.currentTask.count = Math.max(1, info.currentTask.target);
                    info.currentTask.completed = true;
                }
                break;
            case "player.episodes.tasks.claim":
                result = claimCurrentTask(playerSession, info, patchData, now);
                break;
            default:
                break;
        }

        playerSession.savePlayerKeepAlive();
        return result;
    }

    private CommandResult claimCurrentTask(PlayerSession playerSession, EpisodeProgressInfo info,
                                         PatchData patchData, long now) {
        CrateDataResult crateResult = new CrateDataResult();
        if (info.currentTask != null) {
            if (!info.finishedTasks.contains(info.currentTask.uid)) {
                info.finishedTasks.add(info.currentTask.uid);
            }
            Map<String, EpisodeTaskData> taskMap = patchData.getMap(EpisodeTaskData.class);
            EpisodeTaskData td = taskMap != null ? taskMap.get(info.currentTask.uid) : null;
            String crateId = (td != null && td.getCrateId() != null && !td.getCrateId().isEmpty())
                    ? td.getCrateId() : "lc_daily";
            CrateData crate = CrateService.createAndStoreCrate(playerSession, crateId, "episode", null, now);
            crateResult.setCrateData(crate);

            Map<String, EpisodeData> episodeMap = patchData.getMap(EpisodeData.class);
            EpisodeData ep = (info.uid != null && episodeMap != null) ? episodeMap.get(info.uid) : null;
            int nextIdx = info.currentTaskIndex + 1;
            if (ep != null && ep.getTasks() != null && nextIdx < ep.getTasks().size()) {
                info.currentTaskIndex = nextIdx;
                info.currentTask = PlayerEpisodesProgressGet.buildTaskProgress(ep.getTasks().get(nextIdx), patchData, now);
            } else {
                info.currentTask.claimed = true;
            }
        }
        return crateResult;
    }

    @Override
    protected PlayerEpisodesTaskAction parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerEpisodesTaskAction.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }

    public String getActionUID() {
        return actionUID;
    }
}
