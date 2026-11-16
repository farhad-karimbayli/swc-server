package swcnoops.server.commands.guild;

import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.guild.response.BattleIdResult;
import swcnoops.server.datasource.War;
import swcnoops.server.game.PatchData;
import swcnoops.server.game.WarBuffData;
import swcnoops.server.json.JsonParser;
import swcnoops.server.model.BuffBase;
import swcnoops.server.model.FactionType;
import swcnoops.server.requests.CommandResult;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;

import java.util.Map;

public class GuildWarAttackBaseStart extends AbstractCommandAction<GuildWarAttackBaseStart, CommandResult> {
    private String buffBaseUid;

    @Override
    protected CommandResult execute(GuildWarAttackBaseStart arguments, long time) throws Exception {
        PlayerSession playerSession = ServiceFactory.instance().getSessionManager().getPlayerSession(arguments.getPlayerId());
        GuildSession guildSession = playerSession.getGuildSession();
        if (guildSession == null) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_NOT_IN_GUILD);
        }

        War war = guildSession.getCurrentWar();
        if (war == null) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_GUILD_WAR_EXPIRED);
        }

        String battleId = ServiceFactory.createRandomUUID();
        BuffBase buffBase = war.getBuffBases().get(arguments.getBuffBaseUid());
        if (buffBase == null) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
        }
        if (buffBase.battleInProgress) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_GUILD_WAR_EXPIRED);
        }
        String resolvedBattleUid = resolveBuffBaseBattleUid(arguments.getBuffBaseUid(), buffBase.level, playerSession.getFaction());
        if (resolvedBattleUid == null && war.getBuffBases().containsKey(arguments.getBuffBaseUid()) == false) {
            return new BattleIdResult(ResponseHelper.STATUS_CODE_BAD_INPUT);
        }
        buffBase.battleInProgress = true;
        buffBase.currentlyDefending = playerSession.getPlayerId();
        ServiceFactory.instance().getPlayerDatasource().saveWar(war);
        return new BattleIdResult(battleId);
    }

    /**
     * Resolves the encounter battle UID for a war buff base and level from WarBuffData.
     */
    public static String resolveBuffBaseBattleUid(String buffBaseUid, int level, FactionType faction) {
        if (buffBaseUid == null || ServiceFactory.instance().getGameDataManager() == null) {
            return null;
        }
        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
        Map<String, WarBuffData> buffMap = patchData != null ? patchData.getMap(WarBuffData.class) : null;
        WarBuffData buffData = buffMap != null ? buffMap.get(buffBaseUid) : null;
        if (buffData == null) {
            return null;
        }
        String raw = faction == FactionType.rebel ? buffData.getRebelBattlesByLevel() : buffData.getEmpireBattlesByLevel();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] levels = raw.trim().split("\\s+");
        int idx = Math.max(0, Math.min(levels.length - 1, Math.max(1, level) - 1));
        return levels[idx];
    }

    public static int getMaxBuffBaseLevel(String buffBaseUid) {
        if (buffBaseUid == null || ServiceFactory.instance().getGameDataManager() == null) {
            return 5;
        }
        PatchData patchData = ServiceFactory.instance().getGameDataManager().getPatchData();
        Map<String, WarBuffData> buffMap = patchData != null ? patchData.getMap(WarBuffData.class) : null;
        WarBuffData buffData = buffMap != null ? buffMap.get(buffBaseUid) : null;
        if (buffData == null || buffData.getEmpireBattlesByLevel() == null || buffData.getEmpireBattlesByLevel().isBlank()) {
            return 5;
        }
        return buffData.getEmpireBattlesByLevel().trim().split("\\s+").length;
    }

    @Override
    protected GuildWarAttackBaseStart parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, GuildWarAttackBaseStart.class);
    }

    @Override
    public String getAction() {
        return "guild.war.attackBase.start";
    }

    public String getBuffBaseUid() {
        return buffBaseUid;
    }
}
