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
