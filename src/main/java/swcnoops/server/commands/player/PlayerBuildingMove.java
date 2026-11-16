package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.Position;
import swcnoops.server.model.PositionMap;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.PlayerSession;

public class PlayerBuildingMove extends AbstractCommandAction<PlayerBuildingMove, CommandResult> {
    private String instanceId;
    private String buildingId;
    private Position position;
    private int x;
    private int z;

    @Override
    protected CommandResult execute(PlayerBuildingMove arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());

        String key = arguments.getInstanceId() != null ? arguments.getInstanceId() : arguments.getBuildingId();
        Position pos = arguments.getPosition();
        if (pos == null) {
            pos = new Position();
            pos.x = arguments.getX();
            pos.z = arguments.getZ();
        }
        if (key != null && !key.isEmpty()) {
            PositionMap map = new PositionMap();
            map.put(key, pos);
            playerSession.buildingMultimove(map, time);
        }
        return ResponseHelper.SUCCESS_COMMAND_RESULT;
    }

    @Override
    protected PlayerBuildingMove parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerBuildingMove.class);
    }

    @Override
    public String getAction() {
        return "player.building.move";
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public Position getPosition() {
        return position;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }
}
