package swcnoops.server;

import org.junit.Test;
import swcnoops.server.commands.guild.GuildHelper;
import swcnoops.server.game.CurrencyHelper;
import swcnoops.server.game.GameDataManager;
import swcnoops.server.game.GameDataManagerImpl;
import swcnoops.server.game.PatchData;
import swcnoops.server.game.PerkEffectData;
import swcnoops.server.model.Member;
import swcnoops.server.requests.Batch;
import swcnoops.server.requests.BatchProcessor;
import swcnoops.server.requests.BatchProcessorImpl;
import swcnoops.server.requests.BatchResponse;
import swcnoops.server.requests.ResponseHelper;
import swcnoops.server.session.AuthenticationService;
import swcnoops.server.session.GuildSession;
import swcnoops.server.session.MembersManager;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class SecurityAndGameplayTest {

    static {
        ServiceFactory.instance(new Config());
    }

    @Test
    public void authTokenImpersonationRejectedAndSecondaryAllowed() throws Exception {
        AuthenticationService auth = ServiceFactory.instance().getAuthenticationService();
        String playerA = "player-a-" + System.nanoTime();
        String playerB = "player-b-" + System.nanoTime();
        String tokenA = auth.createToken(playerA);

        assertTrue(AuthenticationService.isAuthorizedPlayerId(playerA, playerA));
        assertTrue(AuthenticationService.isAuthorizedPlayerId(playerA, playerA + "_1"));
        assertFalse(AuthenticationService.isAuthorizedPlayerId(playerA, playerB));

        BatchProcessor batchProcessor = new BatchProcessorImpl();
        String impersonateBatch = "{\"authKey\":\"" + tokenA + "\",\"pickupMessages\":true,\"lastLoginTime\":0,"
                + "\"commands\":[{\"action\":\"player.preferences.set\",\"args\":{\"playerId\":\"" + playerB + "\"},"
                + "\"requestId\":1,\"time\":0,\"token\":\"t1\"}]}";

        Batch batch = batchProcessor.decode(impersonateBatch);
        batchProcessor.decodeCommands(batch);
        BatchResponse response = batchProcessor.executeCommands(batch);
        assertEquals(1, response.getData().size());
        assertEquals(Integer.valueOf(ResponseHelper.STATUS_CODE_BAD_INPUT), response.getData().get(0).status);
    }

    @Test
    public void perkEffectsAndCurrencyHelpersTest() {
        GameDataManager gameDataManager = ServiceFactory.instance().getGameDataManager();
        if (gameDataManager.getPatchData() == null) {
            ((GameDataManagerImpl) gameDataManager).initOnStartup();
        }
        PatchData patchData = gameDataManager.getPatchData();
        Map<String, PerkEffectData> effects = patchData.getMap(PerkEffectData.class);
        assertNotNull(effects);

        PerkEffectData timeEffect = effects.get("perkEffect_TDrd03");
        assertNotNull(timeEffect);
        assertTrue(timeEffect.getTimeReductionFactor() > 0.0);

        PerkEffectData genEffect = effects.get("perkEffect_GAll05");
        assertNotNull(genEffect);
        assertEquals("materials", genEffect.getCurrency());
        assertTrue(genEffect.getGenerationRateFactor() > 0.0);

        PerkEffectData reqAmt = effects.get("perkEffect_DntAmt01");
        assertNotNull(reqAmt);
        assertEquals(1, reqAmt.getTroopRequestAmountValue());

        PerkEffectData reqTime = effects.get("perkEffect_DntT115");
        assertNotNull(reqTime);
        assertEquals(115, reqTime.getTroopRequestTimeDiscountValue());

        assertEquals(90, CurrencyHelper.applyTimeReduction(100, 0.10));
        assertEquals(110, CurrencyHelper.applyGeneratorBoost(100, 0.10));
    }

    @Test
    public void guildRankCheckTest() {
        Member owner = new Member();
        owner.playerId = "u1";
        owner.isOwner = true;
        Member officer = new Member();
        officer.playerId = "u2";
        officer.isOfficer = true;
        Member member = new Member();
        member.playerId = "u3";

        MembersManager mm = new MembersManager("g1", Arrays.asList(owner, officer, member), false, false);
        GuildSession session = (GuildSession) java.lang.reflect.Proxy.newProxyInstance(
                GuildSession.class.getClassLoader(),
                new Class<?>[]{GuildSession.class},
                (proxy, method, args) -> "getMembersManager".equals(method.getName()) ? mm : null);

        assertTrue(GuildHelper.hasGuildRank(session, "u1", true));
        assertTrue(GuildHelper.hasGuildRank(session, "u1", false));
        assertFalse(GuildHelper.hasGuildRank(session, "u2", true));
        assertTrue(GuildHelper.hasGuildRank(session, "u2", false));
        assertFalse(GuildHelper.hasGuildRank(session, "u3", true));
        assertFalse(GuildHelper.hasGuildRank(session, "u3", false));
    }
}
