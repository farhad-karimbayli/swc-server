package swcnoops.server.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.Command;
import swcnoops.server.commands.player.PlayerIdentitySwitch;
import swcnoops.server.requests.Batch;
import swcnoops.server.requests.BatchResponse;
import swcnoops.server.requests.ResponseData;
import swcnoops.server.requests.ResponseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
    private Map<String, String> playerTokens = new ConcurrentHashMap<>();
    public String createToken(String playerId) {
        String token = playerId + ":" + ServiceFactory.createRandomUUID();
        playerTokens.put(playerId, token);
        try {
            if (ServiceFactory.instance() != null && ServiceFactory.instance().getPlayerDatasource() != null) {
                ServiceFactory.instance().getPlayerDatasource().saveAuthToken(playerId, token);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to persist auth token for player {}: {}", playerId, ex.getMessage());
        }
        return token;
    }

    public static String extractPlayerId(String authKey) {
        if (authKey == null || authKey.isEmpty()) {
            return null;
        }
        int separator = authKey.indexOf(':');
        return separator > 0 ? authKey.substring(0, separator) : null;
    }

    public static boolean isAuthorizedPlayerId(String authenticatedPlayerId, String targetPlayerId) {
        if (authenticatedPlayerId == null || targetPlayerId == null || targetPlayerId.isEmpty()) {
            return true;
        }
        return PlayerIdentitySwitch.getPrimaryAccount(authenticatedPlayerId)
                .equals(PlayerIdentitySwitch.getPrimaryAccount(targetPlayerId));
    }

    private boolean validAuthCommands(List<Command> commands) {
        boolean allAuthCommands = false;
        for (Command command : commands) {
            if (command.getCommandAction().isAuthCommand()) {
                allAuthCommands = true;
            } else {
                allAuthCommands = false;
                break;
            }
        }

        return allAuthCommands;
    }

    private Integer validAuthKey(String authKey) {
        Integer errorCode = null;

        int separator = authKey.indexOf(':');
        if (separator <= 0) {
            return ResponseHelper.STATUS_CODE_BAD_INPUT;
        }
        String playerId = authKey.substring(0, separator);
        String expectedKey = playerTokens.get(playerId);
        if (expectedKey == null) {
            try {
                if (ServiceFactory.instance() != null && ServiceFactory.instance().getPlayerDatasource() != null) {
                    expectedKey = ServiceFactory.instance().getPlayerDatasource().getAuthToken(playerId);
                    if (expectedKey != null) {
                        playerTokens.put(playerId, expectedKey);
                    }
                }
            } catch (Exception ex) {
                LOG.warn("Failed to load auth token for player {}: {}", playerId, ex.getMessage());
            }
        }
        if (expectedKey != null) {
            if (!expectedKey.equals(authKey))
                errorCode = ResponseHelper.LOGIN_TIME_MISMATCH;
        } else {
            // not found a login session, either server rebooted or client is trying it on
            errorCode = ResponseHelper.STATUS_CODE_BAD_INPUT;
        }

        return errorCode;
    }

    public BatchResponse validateBatch(Batch batch) throws Exception {
        BatchResponse batchResponse = null;

        if (batch.getAuthKey() == null || batch.getAuthKey().isEmpty()) {
            if (!this.validAuthCommands(batch.getCommands())) {
                throw new Exception("Invalid commands without authKey");
            }
        } else {
            Integer errorCode = this.validAuthKey(batch.getAuthKey());
            if (errorCode != null) {
                // another device
                batchResponse = createAuthErrorResponse(batch, errorCode);
            }
        }

        return batchResponse;
    }

    private BatchResponse createAuthErrorResponse(Batch batch, Integer errorCode) {
        List<ResponseData> responseDatum = new ArrayList<>();
        for (Command command : batch.getCommands()) {
            ResponseData responseData = new ResponseData();
            responseData.requestId = command.getRequestId();
            responseData.status = errorCode;
            responseDatum.add(responseData);
        }

        return new BatchResponse(responseDatum);
    }
}
