package swcnoops.server.commands;

import org.junit.Ignore;
import swcnoops.server.Config;
import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.player.PlayerPvpGetNextTarget;
import org.junit.Test;
import swcnoops.server.requests.CommandResult;

import static org.junit.Assert.assertNotNull;

public class PlayerPvpGetNextTargetTest {
    static {
        ServiceFactory.instance(new Config());
    }

    @Ignore
    @Test
    public void testNextLayout() throws Exception {
        ServiceFactory.instance().getPlayerDatasource().initOnStartup();
        ServiceFactory.instance().getGameDataManager().initOnStartup();
        PlayerPvpGetNextTarget playerPvpGetNextTarget = new PlayerPvpGetNextTarget();
        CommandResult response = playerPvpGetNextTarget.execute(null, 1);
        assertNotNull(response);
    }
}
