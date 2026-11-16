package swcnoops.server.requests;

import org.junit.Assume;
import org.junit.Test;
import swcnoops.server.Config;
import swcnoops.server.ServiceFactory;
import swcnoops.server.game.CampaignData;
import swcnoops.server.game.CampaignSet;
import swcnoops.server.model.FactionType;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.Assert.*;

public class BatchProcessorTest {

    static {
        ServiceFactory.instance(new Config());
    }

    private static boolean isMongoReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    public void batchDecodeAndAuthValidationTest() throws Exception {
        String batchRequest = "{\"authKey\":\"\",\"pickupMessages\":true,\"lastLoginTime\":0,\"commands\":[{\"action\":\"auth.preauth.generatePlayer\",\"args\":{\"locale\":\"en_US\"},\"requestId\":2,\"time\":0,\"token\":\"9cf2d3ec-93db-4a25-8140-e3d1f0d5b323\"}]}";

        BatchProcessor batchProcessor = new BatchProcessorImpl();
        Batch batch = batchProcessor.decode(batchRequest);
        assertEquals(1, batch.getCommands().size());

        batchProcessor.decodeCommands(batch);
        assertNotNull(batch.getCommands().get(0).getCommandAction());

        // Malformed authKey without ":" must return BAD_INPUT instead of throwing StringIndexOutOfBoundsException
        String malformedAuthRequest = "{\"authKey\":\"malformed-token-without-colon\",\"pickupMessages\":true,\"lastLoginTime\":0,\"commands\":[{\"action\":\"player.login\",\"args\":{},\"requestId\":3,\"time\":0,\"token\":\"9cf2d3ec-93db-4a25-8140-e3d1f0d5b323\"}]}";
        Batch malformedBatch = batchProcessor.decode(malformedAuthRequest);
        batchProcessor.decodeCommands(malformedBatch);
        BatchResponse authError = ServiceFactory.instance().getAuthenticationService().validateBatch(malformedBatch);
        assertNotNull(authError);
        assertEquals(Integer.valueOf(ResponseHelper.STATUS_CODE_BAD_INPUT), authError.getData().get(0).status);

        // Out-of-bounds campaign order must return null cleanly
        CampaignSet campaignSet = new CampaignSet(FactionType.empire);
        CampaignData data = ServiceFactory.instance().getJsonParser()
                .fromJsonString("{\"uid\":\"cmp_1\",\"unlockOrder\":1}", CampaignData.class);
        campaignSet.addCampaignData(data);
        assertNotNull(campaignSet.getCampaignMissionSet(1));
        assertNull(campaignSet.getCampaignMissionSet(0));
        assertNull(campaignSet.getCampaignMissionSet(2));

        // Verify newly wired commands decode cleanly in BatchProcessor
        String newCommandsBatch = "{\"authKey\":\"\",\"pickupMessages\":true,\"lastLoginTime\":0,\"commands\":["
                + "{\"action\":\"player.crate.open\",\"args\":{},\"requestId\":1,\"time\":0,\"token\":\"t1\"},"
                + "{\"action\":\"player.store.shard.offers.buy\",\"args\":{},\"requestId\":2,\"time\":0,\"token\":\"t2\"},"
                + "{\"action\":\"player.equipment.activate\",\"args\":{},\"requestId\":3,\"time\":0,\"token\":\"t3\"},"
                + "{\"action\":\"player.equipment.deactivate\",\"args\":{},\"requestId\":4,\"time\":0,\"token\":\"t4\"},"
                + "{\"action\":\"player.equipment.upgrade.start\",\"args\":{},\"requestId\":5,\"time\":0,\"token\":\"t5\"},"
                + "{\"action\":\"player.episodes.tasks.claim\",\"args\":{},\"requestId\":6,\"time\":0,\"token\":\"t6\"},"
                + "{\"action\":\"player.perks.activate\",\"args\":{},\"requestId\":7,\"time\":0,\"token\":\"t7\"},"
                + "{\"action\":\"player.holonet.claimReward\",\"args\":{},\"requestId\":8,\"time\":0,\"token\":\"t8\"},"
                + "{\"action\":\"player.planet.stats\",\"args\":{},\"requestId\":9,\"time\":0,\"token\":\"t9\"},"
                + "{\"action\":\"player.building.move\",\"args\":{},\"requestId\":10,\"time\":0,\"token\":\"t10\"},"
                + "{\"action\":\"player.missions.showIntro\",\"args\":{},\"requestId\":11,\"time\":0,\"token\":\"t11\"},"
                + "{\"action\":\"player.missions.startSpecop\",\"args\":{},\"requestId\":12,\"time\":0,\"token\":\"t12\"},"
                + "{\"action\":\"player.campaignStore.buy\",\"args\":{},\"requestId\":13,\"time\":0,\"token\":\"t13\"},"
                + "{\"action\":\"player.store.tournament.buy\",\"args\":{},\"requestId\":14,\"time\":0,\"token\":\"t14\"},"
                + "{\"action\":\"player.award.rateMyApp\",\"args\":{},\"requestId\":15,\"time\":0,\"token\":\"t15\"},"
                + "{\"action\":\"guild.war.claim\",\"args\":{},\"requestId\":16,\"time\":0,\"token\":\"t16\"},"
                + "{\"action\":\"guild.invite.accept\",\"args\":{},\"requestId\":17,\"time\":0,\"token\":\"t17\"}"
                + "]}";
        Batch decodedNew = batchProcessor.decode(newCommandsBatch);
        batchProcessor.decodeCommands(decodedNew);
        decodedNew.getCommands().forEach(c -> assertNotNull("Missing handler for " + c.getAction(), c.getCommandAction()));
    }

    @Test
    public void batchProcessorExecuteIntegrationTest() throws Exception {
        Assume.assumeTrue("MongoDB is not running on localhost:27017", isMongoReachable());
        ServiceFactory.instance().getPlayerDatasource().initOnStartup();

        String batchRequest = "{\"authKey\":\"\",\"pickupMessages\":true,\"lastLoginTime\":0,\"commands\":[{\"action\":\"auth.preauth.generatePlayer\",\"args\":{\"locale\":\"en_US\"},\"requestId\":2,\"time\":0,\"token\":\"9cf2d3ec-93db-4a25-8140-e3d1f0d5b323\"}]}";

        BatchProcessor batchProcessor = new BatchProcessorImpl();
        Batch batch = batchProcessor.decode(batchRequest);
        assertEquals(1, batch.getCommands().size());

        batchProcessor.decodeCommands(batch);
        assertNotNull(batch.getCommands().get(0).getCommandAction());

        BatchResponse batchResponse = batchProcessor.executeCommands(batch);
        assertNotNull(batch.getCommands().get(0).getResponse());
        assertNotNull(batchResponse);
        String json = ServiceFactory.instance().getJsonParser().toJson(batchResponse);
        assertNotNull(json);
    }
}
