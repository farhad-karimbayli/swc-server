package swcnoops.server.commands.guild.response;

import swcnoops.server.model.Member;
import swcnoops.server.requests.CommandResult;

public class GuildMemberResult implements CommandResult {
    private Member member;
    private final int status;

    public GuildMemberResult(Member member) {
        this.member = member;
        this.status = 0;
    }

    public GuildMemberResult(int status) {
        this.status = status;
    }

    @Override
    public Integer getStatus() {
        return Integer.valueOf(this.status);
    }

    @Override
    public Object getResult() {
        return this.member;
    }
}
