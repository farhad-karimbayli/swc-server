package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.requests.AbstractCommandResult;

import java.util.Map;

public class PlayerPlanetStats extends AbstractCommandAction<PlayerPlanetStats, PlayerPlanetStats.PlanetStatsResult> {
    public static class PlanetStatsResult extends AbstractCommandResult {
        public Map<String, Integer> stats;

        @Override
        public Object getResult() {
            return this.stats;
        }
    }

    @Override
    protected PlanetStatsResult execute(PlayerPlanetStats arguments, long time) throws Exception {
        PlanetStatsResult result = new PlanetStatsResult();
        result.stats = ServiceFactory.instance().getPlayerDatasource().getPlanetPopulationStats();
        return result;
    }

    @Override
    protected PlayerPlanetStats parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerPlanetStats.class);
    }

    @Override
    public String getAction() {
        return "player.planet.stats";
    }
}
