package swcnoops.server.commands.player;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.player.response.StringListResult;
import swcnoops.server.game.CommandCenterEntryData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.FactionType;
import swcnoops.server.session.PlayerSession;

import java.util.*;

public class PlayerHolonetGetCommandCenterEntry extends AbstractCommandAction<PlayerHolonetGetCommandCenterEntry, StringListResult> {
    @Override
    protected StringListResult execute(PlayerHolonetGetCommandCenterEntry arguments, long time) throws Exception {
        StringListResult result = new StringListResult();
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                .getPlayerSession(arguments.getPlayerId());
        FactionType faction = playerSession.getPlayerSettings().getFaction();
        String factionCond = "faction:" + (faction != null ? faction.name() : FactionType.empire.name());

        Map<String, CommandCenterEntryData> entries = ServiceFactory.instance().getGameDataManager()
                .getPatchData().getMap(CommandCenterEntryData.class);
        if (entries != null && !entries.isEmpty()) {
            List<CommandCenterEntryData> matching = new ArrayList<>();
            for (CommandCenterEntryData entry : entries.values()) {
                List<String> conds = entry.getAudienceConditions();
                boolean factionOk = conds == null || conds.stream()
                        .noneMatch(c -> c.startsWith("faction:"))
                        || conds.contains(factionCond);
                if (factionOk) {
                    matching.add(entry);
                }
            }
            matching.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            for (int i = 0; i < Math.min(5, matching.size()); i++) {
                result.getData().add(matching.get(i).getUid());
            }
        }

        if (result.getData().isEmpty()) {
            result.getData().add(faction == FactionType.rebel ? "hn_cc_1_undead_raid_r" : "hn_cc_1_undead_raid_e");
        }
        return result;
    }

    @Override
    protected PlayerHolonetGetCommandCenterEntry parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerHolonetGetCommandCenterEntry.class);
    }

    @Override
    public String getAction() {
        return "player.holonet.getCommandCenterEntry";
    }
}
