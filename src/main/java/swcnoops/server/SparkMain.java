package swcnoops.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.utils.IOUtils;
import javax.servlet.ServletOutputStream;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static spark.Spark.*;
import static swcnoops.server.requests.BatchProcessorImpl.decodeParameters;

public class SparkMain {
    private static final Logger LOG = LoggerFactory.getLogger(SparkMain.class);

    public static void main(String[] args) {
        try {
            LOG.info("Initialising");
            Properties properties = readProperties(args.length >= 1 ? args[0] : null);
            initialise(properties);
            LOG.info("Initialising complete, going to setup webserver and routes");
            port(ServiceFactory.instance().getConfig().webServicePort);
            BatchRoute batchRoute = new BatchRoute();
            post("/starts/batch/json", batchRoute);
            post("/starts/batch/jsons", batchRoute);
            post("/bi_event2", (a, b) -> {
                // The client reports its own failures here. Discarding the body
                // threw away the only explanation the client ever gives for
                // stalling, so it is written to the log instead.
                String body = a.body();
                if (body != null && !body.isEmpty()) {
                    LOG.info("client event: {}", body.length() > 4000
                            ? body.substring(0, 4000) + "...[truncated]" : body);
                }
                b.type("octet-stream");
                return "{}";
            });
            get("/health", (req, res) -> {
                boolean dbOk = ServiceFactory.instance().getPlayerDatasource() != null
                        && ServiceFactory.instance().getPlayerDatasource().isHealthy();
                res.type("application/json");
                res.status(dbOk ? 200 : 503);
                return "{\"status\":\"" + (dbOk ? "ok" : "degraded") + "\",\"mongo\":" + dbOk + "}";
            });
            get("/swcFiles/*", new GetFile());
            get("/*", new ConnectionTest());

            // Nothing recorded what the client asked for, which made every
            // failure look identical from the server side. This prints one
            // line per request, and marks the ones that were not served so a
            // missing asset is visible immediately.
            // afterAfter, not after: Spark skips "after" filters when a route
            // throws, which is exactly the case for a file that is not there.
            afterAfter((request, response) -> {
                String marker = response.status() >= 400 ? "MISS" : "ok  ";
                LOG.info("{} {} {} -> {}", marker, request.requestMethod(),
                        request.pathInfo(), response.status());
            });

            exception(Exception.class, (e, request, response) -> {
                LOG.error("Caught exception on {} {}", request.requestMethod(), request.pathInfo(), e);
                response.status(500);
                if (request.pathInfo() != null && request.pathInfo().startsWith("/starts/batch/")) {
                    response.type("application/json");
                    response.body("{\"error\":\"Internal server error\"}");
                } else {
                    response.body("Internal server error");
                }
            });

            LOG.info("Done ready to process from port " + ServiceFactory.instance().getConfig().webServicePort);

        } catch (Exception ex) {
            LOG.error("Failed with exception", ex);
            System.exit(-1);
        }
    }

    private static void initialise(Properties properties) throws Exception {
        Config config = new Config();
        config.setFromProperties(properties);
        ServiceFactory.instance(config);

        Thread shutdownThread = new Thread(() -> {
            try {
                stop();
            } catch (Exception ignored) {
            }
            ServiceFactory.instance().shutdown();
        });
        Runtime.getRuntime().addShutdownHook(shutdownThread);

        ServiceFactory.instance().getPlayerDatasource().initOnStartup();
        ServiceFactory.instance().getGameDataManager().initOnStartup();
    }

    private static Properties readProperties(String fileName) throws Exception {
        Properties prop = new Properties();
        if (fileName != null) {
            LOG.info("Reading properties file " + fileName);
            File configFile = new File(fileName);
            try (FileReader reader = new FileReader(configFile)) {
                prop.load(reader);
            }
        }

        return prop;
    }

    private static class BatchRoute implements Route {
        @Override
        public Object handle(Request request, Response response) throws Exception {
            String postBody = request.body();
            Map<String, List<String>> parameters = decodeParameters(postBody);
            List<String> batchParams = parameters.get("batch");
            if (batchParams == null || batchParams.isEmpty() || batchParams.get(0) == null) {
                response.status(400);
                return "Missing batch parameter";
            }
            String batchRequestJson = batchParams.get(0);
            // Log which commands the client asked for. When it stalls, the
            // last command in this list is where it stopped, and that is the
            // only clue the client gives.
            // Bodies are far too long for a log line but are the only record
            // of what the client asked for and what it was told, so each pair
            // is written to its own file for later reading.
            int n = exchangeCounter.incrementAndGet();
            dump(n, "request", batchRequestJson);
            String json = ServiceFactory.instance().getBatchProcessor().processBatchPostBody(batchRequestJson);
            dump(n, "response", json);
            LOG.info("batch #{} [{}]", n, commandNames(batchRequestJson));
            response.type("application/json");
            response.body(json);
            return json;
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger exchangeCounter =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.nio.file.Path DUMP_DIR =
            java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "swc-batches");

    /** Writes one side of a batch exchange to a rotating file, ignoring any failure. */
    private static void dump(int number, String kind, String body) {
        Config config = ServiceFactory.instance().getConfig();
        if (body == null || !config.batchDumpEnabled) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(DUMP_DIR);
            int slot = config.batchDumpMaxFiles > 0
                    ? ((number - 1) % config.batchDumpMaxFiles) + 1
                    : number;
            java.nio.file.Files.write(
                    DUMP_DIR.resolve(String.format("%03d-%s.json", slot, kind)),
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.warn("could not write batch dump: {}", e.toString());
        }
    }

    /** Pulls the "cmd" values out of a batch payload, in order. */
    private static String commandNames(String json) {
        if (json == null) {
            return "(empty)";
        }
        Matcher matcher = COMMAND_NAME.matcher(json);
        StringJoiner joiner = new StringJoiner(", ");
        while (matcher.find()) {
            joiner.add(matcher.group(1));
        }
        return joiner.length() == 0 ? "(no commands)" : joiner.toString();
    }

    private static final Pattern COMMAND_NAME =
            Pattern.compile("\"(?:cmd|command)\"\\s*:\\s*\"([^\"]+)\"");

    private static class GetFile implements Route {
        @Override
        public Object handle(Request request, Response response) throws Exception {
            java.nio.file.Path swcRoot = java.nio.file.Paths.get(ServiceFactory.instance().getConfig().swcRootPath)
                    .toAbsolutePath().normalize();
            String relPath = request.pathInfo() == null ? "" : request.pathInfo().replaceFirst("^/+", "");
            java.nio.file.Path requested = swcRoot.resolve(relPath).normalize();
            if (!requested.startsWith(swcRoot)) {
                response.status(403);
                return "Forbidden";
            }

            java.nio.file.Path resolved = resolveFallbackFile(swcRoot, requested);
            if (resolved == null || !java.nio.file.Files.isRegularFile(resolved)) {
                response.status(404);
                return "Resource not found";
            }

            long size = java.nio.file.Files.size(resolved);
            long lastModified = java.nio.file.Files.getLastModifiedTime(resolved).toMillis();
            String etag = "\"" + size + "-" + lastModified + "\"";
            response.header("ETag", etag);
            String ifNoneMatch = request.headers("If-None-Match");
            if (etag.equals(ifNoneMatch)) {
                response.status(304);
                return "";
            }
            response.type("octet-stream");
            response.header("Content-Length", String.valueOf(size));
            String fileName = resolved.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".assetbundle") || pathStr(resolved).contains("/patches/")) {
                response.header("Cache-Control", "public, max-age=86400");
            }
            response.status(200);

            ServletOutputStream os = response.raw().getOutputStream();
            try (InputStream in = new BufferedInputStream(java.nio.file.Files.newInputStream(resolved))) {
                IOUtils.copy(in, os);
            }
            os.flush();
            return "";
        }

        private static String pathStr(java.nio.file.Path path) {
            return path.toString().replace('\\', '/');
        }

        private java.nio.file.Path resolveFallbackFile(java.nio.file.Path swcRoot, java.nio.file.Path requested) {
            if (java.nio.file.Files.isRegularFile(requested)) {
                return requested;
            }
            String pathStr = requested.toString().replace('\\', '/');
            // Campaign attack missions fetch an enemy base layout from
            // swcFiles/<v>/battles/<name>.json. The original designed layouts
            // are almost all lost, so a missing one falls back to a valid base
            // instead of a 404, which lets the mission load and be played.
            if (pathStr.contains("/battles/")) {
                return swcRoot.resolve("swcFiles/battles/_default_mission.json");
            }
            if (pathStr.contains("/strings/")) {
                String lower = pathStr.toLowerCase();
                boolean isRu = lower.contains("ru-ru") || lower.contains("ru_ru");
                boolean isHn = lower.contains("strings-hn");
                String[] candidates = isRu
                        ? (isHn
                            ? new String[]{"swcFiles/1604000000/strings/strings-hn_ru-ru.json.android.assetbundle",
                                           "swcFiles/1584443067/strings/strings-hn_ru-ru.json.android.assetbundle"}
                            : new String[]{"swcFiles/1604000000/strings/strings_ru-ru.json.android.assetbundle",
                                           "swcFiles/1576242239/strings/strings_ru-ru.json.android.assetbundle"})
                        : (isHn
                            ? new String[]{"swcFiles/1584443067/strings/strings-hn_en-us.json.android.assetbundle"}
                            : new String[]{"swcFiles/1576242239/strings/strings_en-us.json.android.assetbundle"});
                for (String candidate : candidates) {
                    java.nio.file.Path p = swcRoot.resolve(candidate);
                    if (java.nio.file.Files.isRegularFile(p)) {
                        return p;
                    }
                }
            }
            return requested;
        }
    }

    private static class ConnectionTest implements Route {
        @Override
        public Object handle(Request request, Response response) throws Exception {
            if (request.pathInfo() != null && request.pathInfo().contains("connection_test.txt")) {
                response.type("octet-stream");
                response.status(200);
                return "success";
            }

            response.status(404);
            return "";
        }
    }
}
