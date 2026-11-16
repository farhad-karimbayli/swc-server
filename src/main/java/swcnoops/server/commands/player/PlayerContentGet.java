package swcnoops.server.commands.player;

import swcnoops.server.Config;
import swcnoops.server.ServiceFactory;
import swcnoops.server.commands.AbstractCommandAction;
import swcnoops.server.commands.Command;
import swcnoops.server.game.Manifest;
import swcnoops.server.game.Patch;
import swcnoops.server.json.JsonParser;
import swcnoops.server.commands.player.response.PlayerContentGetCommandResult;
import swcnoops.server.requests.EmptyMessage;
import swcnoops.server.requests.Messages;

import swcnoops.server.datasource.PlayerSettings;
import swcnoops.server.session.PlayerSession;

import java.util.Arrays;
import java.util.List;

public class PlayerContentGet extends AbstractCommandAction<PlayerContentGet, PlayerContentGetCommandResult> {
    private String locale;
    private List<String> defaultPatches = Arrays.asList("patches/arc.json",
            "patches/base.json",
            "patches/fue.json",
            "patches/reserved.json",
            "patches/cae.json",
            "patches/wts.json",
            "patches/holo.json",
            "patches/olc.json",
            "patches/war.json",
            "patches/prk.json",
            "patches/trp.json",
            "patches/epi.json");

    @Override
    final public String getAction() {
        return "player.content.get";
    }

    @Override
    protected PlayerContentGet parseArgument(JsonParser jsonParser, Object argumentObject) {
        return jsonParser.fromJsonObject(argumentObject, PlayerContentGet.class);
    }

    @Override
    protected PlayerContentGetCommandResult execute(PlayerContentGet arguments, long time) throws Exception {
        PlayerContentGetCommandResult response = new PlayerContentGetCommandResult();

        response.cdnRoots.add(ServiceFactory.instance().getConfig().cdnRoots);
        response.secureCdnRoots.add(ServiceFactory.instance().getConfig().cdnRoots);

        // Determine player locale from argument, session, or settings
        String locale = arguments.getLocale();
        if ((locale == null || locale.isEmpty()) && arguments.getPlayerId() != null) {
            PlayerSession playerSession = ServiceFactory.instance().getSessionManager()
                    .getPlayerSession(arguments.getPlayerId());
            if (playerSession != null && playerSession.getPlayerSettings() != null
                    && playerSession.getPlayerSettings().getSharedPreferences() != null) {
                locale = playerSession.getPlayerSettings().getSharedPreferences().get("locale");
            }
            if (locale == null || locale.isEmpty()) {
                try {
                    PlayerSettings settings = ServiceFactory.instance().getPlayerDatasource()
                            .loadPlayerSettings(arguments.getPlayerId(), false, "sharedPreferences");
                    if (settings != null && settings.getSharedPreferences() != null) {
                        locale = settings.getSharedPreferences().get("locale");
                    }
                } catch (Exception ex) {
                    // Ignore and fallback to English
                }
            }
        }

        // Add ONLY the active language strings patch. Adding both English and Russian
        // causes LocalizedStrings collision in GameDataManager during ApplicationLoadState.
        if (locale != null && (locale.equalsIgnoreCase("ru_RU") || locale.equalsIgnoreCase("ru-RU") || locale.toLowerCase().startsWith("ru"))) {
            response.patches.add("strings/strings_ru-RU.json");
        } else if (locale != null && !locale.isEmpty() && !locale.toLowerCase().startsWith("en")) {
            String normalized = locale.replace('_', '-');
            response.patches.add("strings/strings_" + normalized + ".json");
        } else {
            response.patches.add("strings/strings_en-US.json");
        }

        response.patches.addAll(defaultPatches);

        if (ServiceFactory.instance().getGameDataManager().getPatchesAvailable() != null) {
            for (Patch patch : ServiceFactory.instance().getGameDataManager().getPatchesAvailable()) {
                response.patches.add("patches/" + patch.patchName);
            }
        }

        String customManifest = Config.manifestFileTemplate + arguments.getPlayerId() + ".json";
        java.io.File customManifestFile = new java.io.File(ServiceFactory.instance().getConfig().swcRootPath
                + "/" + ServiceFactory.instance().getConfig().swcFolderName + "/" + customManifest);
        if (arguments.getPlayerId() != null && customManifestFile.exists()) {
            String v = readCachedManifestVersion(customManifestFile);
            response.manifestVersion = isNumeric(v) ? v : "02048";
            response.manifest = customManifest;
        } else {
            response.manifestVersion = Config.padManifestVersion(ServiceFactory.instance().getConfig().getManifestVersionToUse());
            response.manifest = Config.manifestFileTemplate + response.manifestVersion + ".json";
        }

        return response;
    }

    private static final class CachedManifestVersion {
        final long lastModified;
        final String version;

        CachedManifestVersion(long lastModified, String version) {
            this.lastModified = lastModified;
            this.version = version;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedManifestVersion> manifestVersionCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static String readCachedManifestVersion(java.io.File manifestFile) {
        String path = manifestFile.getAbsolutePath();
        long mtime = manifestFile.lastModified();
        CachedManifestVersion cached = manifestVersionCache.get(path);
        if (cached != null && cached.lastModified == mtime) {
            return cached.version;
        }
        try {
            Manifest manifest = ServiceFactory.instance().getJsonParser().fromJsonFile(path, Manifest.class);
            String version = manifest != null ? manifest.version : null;
            manifestVersionCache.put(path, new CachedManifestVersion(mtime, version));
            return version;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    protected Messages createMessage(Command command, PlayerContentGetCommandResult commandResult) {
        return EmptyMessage.instance;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
