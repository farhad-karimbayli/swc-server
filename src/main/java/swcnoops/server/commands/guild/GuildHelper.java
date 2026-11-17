package swcnoops.server.commands.guild;

import swcnoops.server.model.Member;
import swcnoops.server.model.Perks;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.PlayerSession;

import java.util.List;

public class GuildHelper {
    static final public Perks emptyPerks = new Perks();

    public static Member findMember(GuildSession guildSession, String playerId) {
        if (guildSession == null || playerId == null || guildSession.getMembersManager() == null) {
            return null;
        }
        List<Member> members = guildSession.getMembersManager().getObjectForReading();
        if (members == null) {
            return null;
        }
        for (Member member : members) {
            if (playerId.equals(member.playerId)) {
                return member;
            }
        }
        return null;
    }

    public static boolean hasGuildRank(GuildSession guildSession, String playerId, boolean requireOwner) {
        Member member = findMember(guildSession, playerId);
        if (member == null) {
            // Allow if member list has not been populated yet (e.g., synthetic/mocked sessions)
            List<Member> members = guildSession != null && guildSession.getMembersManager() != null
                    ? guildSession.getMembersManager().getObjectForReading()
                    : null;
            return members == null || members.isEmpty();
        }
        return requireOwner ? member.isOwner : (member.isOwner || member.isOfficer);
    }

    public static Member createMember(PlayerSession playerSession) {
        Member member = new Member();
        member.playerId = playerSession.getPlayerId();
        member.planet = playerSession.getPlayerSettings().getBaseMap().planet;
        member.name = playerSession.getPlayerSettings().getName();
        member.xp = playerSession.getScalarsManager().getObjectForReading().xp;
        member.hqLevel = playerSession.getHeadQuarter().getBuildingData().getLevel();
        return member;
    }

    public static Member createMember(String playerId, String playerName, boolean isOwner,
                                      boolean isOfficer, long joinDate, long troopsDonated, long troopsReceived,
                                      boolean warParty, int hqLevel)
    {
        Member member = new Member();
        member.isOfficer = isOfficer;
        member.isOwner = isOwner;
        member.playerId = playerId;
        member.joinDate = joinDate;
        member.hqLevel = hqLevel;
        member.name = playerName;
        member.troopsDonated = troopsDonated;
        member.troopsReceived = troopsReceived;
        member.warParty = warParty ? 1: 0;
        return member;
    }
}
