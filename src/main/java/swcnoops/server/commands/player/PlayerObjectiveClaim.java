package swcnoops.server.commands.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.CrateDataResult;
import swcnoops.server.game.ObjTableData;
import swcnoops.server.game.PatchData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.ObjectiveGroup;
import swcnoops.server.model.ObjectiveProgress;
import swcnoops.server.model.ObjectiveState;
import swcnoops.server.session.PlayerSession;
import java.util.Map;
import java.util.Optional;

public class PlayerObjectiveClaim extends AbstractCommandAction<PlayerObjectiveClaim, CrateDataResult> {
    private static final Logger LOG = LoggerFactory.getLogger(PlayerObjectiveClaim.class);

    private String planetId;
    private String objectiveId;

    @Override
    protected CrateDataResult execute(PlayerObjectiveClaim arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        ObjectiveGroup group =
                playerSession.getPlayerObjectivesManager().getObjectForReading().get(arguments.getPlanetId());

        ObjectiveProgress progress = group != null ? getProgress(group, arguments.getObjectiveId()) : null;
        CrateDataResult result = new CrateDataResult();
        if (progress != null && !progress.claimAttempt) {
            if (progress.state != ObjectiveState.complete) {
                LOG.warn("Rejecting incomplete objective claim {} ({}/{}) for player {}",
                        arguments.getObjectiveId(), progress.count, progress.target, arguments.getPlayerId());
                return result;
            }

            progress.claimAttempt = true;
            progress.state = ObjectiveState.rewarded;
            playerSession.getPlayerObjectivesManager().getObjectForWriting();

            long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
            String crateUid = resolveCrateRewardUid(arguments.getObjectiveId());
            swcnoops.server.model.CrateData crate = swcnoops.server.game.CrateService.createAndStoreCrate(
                    playerSession, crateUid, "objective", arguments.getPlanetId(), now);
            result.setCrateData(crate);
            playerSession.savePlayerKeepAlive();
        }

        return result;
    }

    private static String resolveCrateRewardUid(String objectiveUid) {
        if (objectiveUid != null && ServiceFactory.instance().getGameDataManager() != null) {
            PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
            Map<String, ObjTableData> map = patchData != null ? patchData.getMap(ObjTableData.class) : null;
            ObjTableData objData = map != null ? map.get(objectiveUid) : null;
            if (objData != null && objData.getCrateRewardUid() != null && !objData.getCrateRewardUid().isEmpty()) {
                return objData.getCrateRewardUid();
            }
        }
        return "lc_daily";
    }

    private ObjectiveProgress getProgress(ObjectiveGroup group, String objectiveId) {
        ObjectiveProgress objectiveProgress = null;
        if (group.progress != null) {
            Optional<ObjectiveProgress> o = group.progress.stream().filter(p -> p.uid.equals(objectiveId)).findFirst();
            if (o.isPresent()) {
                objectiveProgress = o.get();
            }
        }

        return objectiveProgress;
    }

    @Override
    protected PlayerObjectiveClaim parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerObjectiveClaim.class);
    }

    @Override
    public String getAction() {
        return "player.objective.claim";
    }

    public String getPlanetId() {
        return planetId;
    }

    public void setPlanetId(String planetId) {
        this.planetId = planetId;
    }

    public String getObjectiveId() {
        return objectiveId;
    }

    public void setObjectiveId(String objectiveId) {
        this.objectiveId = objectiveId;
    }
}
