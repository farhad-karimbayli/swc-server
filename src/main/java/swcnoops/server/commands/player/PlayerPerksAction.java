package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.game.PerkData;
import swcnoops.server.game.SquadLevelData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.Perks;
import swcnoops.server.model.Squad;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

/**
 * Handles squad perk commands: activate, cancel, invest, skipCooldown.
 */
public class PlayerPerksAction extends AbstractCommandAction<PlayerPerksAction, CommandResult> {
    private final String actionName;
    private String perkId;
    private String perkUid;
    private int amount;

    public PlayerPerksAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    protected CommandResult execute(PlayerPerksAction arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession == null) {
            return ResponseHelper.SUCCESS_COMMAND_RESULT;
        }

        Squad squad = guildSession.getSquadManager().getObjectForReading();
        if (squad.perks == null) {
            squad.perks = new Perks();
        }
        String targetPerk = arguments.getPerkId() != null ? arguments.getPerkId() : arguments.getPerkUid();
        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();

        switch (this.actionName) {
            case "player.perks.activate":
                if (targetPerk != null && !targetPerk.isEmpty()) {
                    squad.perks.available.put(targetPerk, String.valueOf(now));
                }
                break;
            case "player.perks.cancel":
                if (targetPerk != null) {
                    squad.perks.available.remove(targetPerk);
                    squad.perks.inProgress.remove(targetPerk);
                }
                break;
            case "player.perks.invest":
                if (targetPerk != null && !targetPerk.isEmpty()) {
                    int add = Math.max(1, arguments.getAmount());
                    int invested = squad.perks.inProgress.getOrDefault(targetPerk, 0) + add;
                    Map<String, PerkData> perkMap = ServiceFactory.instance().getGameDataManager()
                            .getPatchData().getMap(PerkData.class);
                    PerkData def = perkMap != null ? perkMap.get(targetPerk) : null;
                    if (def != null && def.getRepCost() > 0 && invested >= def.getRepCost()) {
                        squad.perks.inProgress.remove(targetPerk);
                        squad.perks.available.put(targetPerk, String.valueOf(now));
                    } else {
                        squad.perks.inProgress.put(targetPerk, invested);
                    }
                    squad.rep += add;
                    updateSquadLevel(squad);
                }
                break;
            case "player.perks.skipCooldown":
                if (targetPerk != null) {
                    squad.perks.available.remove(targetPerk);
                }
                break;
            default:
                break;
        }

        ServiceFactory.instance().getPlayerDatasource().saveSquadPerks(guildSession, squad);
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    private void updateSquadLevel(Squad squad) {
        Map<String, SquadLevelData> levels = ServiceFactory.instance().getGameDataManager()
                .getPatchData().getMap(SquadLevelData.class);
        if (levels == null) {
            return;
        }
        int bestLevel = Math.max(1, squad.level);
        for (SquadLevelData lvl : levels.values()) {
            if (squad.rep >= lvl.getRepThreshold() && lvl.getLevel() > bestLevel) {
                bestLevel = lvl.getLevel();
            }
        }
        squad.level = bestLevel;
    }

    @Override
    protected PlayerPerksAction parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerPerksAction.class);
    }

    @Override
    public String getAction() {
        return this.actionName;
    }

    public String getPerkId() {
        return perkId;
    }

    public String getPerkUid() {
        return perkUid;
    }

    public int getAmount() {
        return amount;
    }
}
