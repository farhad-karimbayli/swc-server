package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.PlayerCrateCheckDailyCommandResult;
import swcnoops.server.game.CrateService;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.PlayerCrates;
import swcnoops.server.session.PlayerSession;

public class PlayerCrateCheckDaily extends AbstractCommandAction<PlayerCrateCheckDaily, PlayerCrateCheckDailyCommandResult> {
    private static final long DAILY_CRATE_INTERVAL_SECONDS = 24L * 3600L;

    @Override
    protected PlayerCrateCheckDailyCommandResult execute(PlayerCrateCheckDaily arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        PlayerCrates crates = playerSession.getPlayerSettings().getCrates();

        long now = time > 0 ? time : ServiceFactory.getSystemTimeSecondsFromEpoch();
        boolean changed = false;
        if (crates.nextDailyCrateTime <= now) {
            crates.available.entrySet().removeIf(e -> e.getValue() != null && e.getValue().claimed);
            boolean hasDailyAlready = crates.available.values().stream()
                    .anyMatch(c -> "daily".equals(c.context) && !c.claimed);
            if (!hasDailyAlready) {
                CrateService.createAndStoreCrate(playerSession, "lc_daily", "daily", null, now);
                changed = true;
            }
            crates.nextDailyCrateTime = now + DAILY_CRATE_INTERVAL_SECONDS;
            changed = true;
        }
        if (changed) {
            playerSession.savePlayerKeepAlive();
        }

        PlayerCrateCheckDailyCommandResult result = new PlayerCrateCheckDailyCommandResult();
        result.available = crates.available;
        result.inProgress = crates.inProgress;
        result.next = crates.next;
        result.nextDailyCrateTime = crates.nextDailyCrateTime;
        result.nextHolonetTime = crates.nextHolonetTime;
        return result;
    }

    @Override
    protected PlayerCrateCheckDaily parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerCrateCheckDaily.class);
    }

    @Override
    public String getAction() {
        return "player.crate.checkDaily";
    }
}
