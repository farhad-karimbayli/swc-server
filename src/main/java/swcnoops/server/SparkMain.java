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
                LOG.error("Caught exception ", e);
                response.status(404);
                response.body("Resource not found");
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

        Thread shutdownThread = new Thread(() -> ServiceFactory.instance().shutdown());
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
            String batchRequestJson = parameters.get("batch").get(0);
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
            LOG.info("batch #{} written to {}", n, DUMP_DIR);
            response.type("application/json");
            response.body(json);
            return json;
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger exchangeCounter =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.nio.file.Path DUMP_DIR =
            java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "swc-batches");

    /** Writes one side of a batch exchange to a file, ignoring any failure. */
    private static void dump(int number, String kind, String body) {
        if (body == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(DUMP_DIR);
            java.nio.file.Files.write(
                    DUMP_DIR.resolve(String.format("%03d-%s.json", number, kind)),
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
            String path = ServiceFactory.instance().getConfig().swcRootPath + request.pathInfo();
            // Campaign attack missions fetch an enemy base layout from
            // swcFiles/<v>/battles/<name>.json. The original designed layouts
            // are almost all lost, so a missing one falls back to a valid base
            // instead of a 404, which lets the mission load and be played.
            if (path.contains("/battles/") && !new java.io.File(path).exists()) {
                path = ServiceFactory.instance().getConfig().swcRootPath
                        + "/swcFiles/battles/_default_mission.json";
            }
            //long fileSize = Files.size(Paths.get(path));
            response.type("octet-stream");
            //response.raw().setContentLength((int) fileSize);
            response.status(200);

            final ServletOutputStream os = response.raw().getOutputStream();
            final InputStream in = new BufferedInputStream(new FileInputStream(path));
            IOUtils.copy(in, os);
            in.close();
            os.close();
            return "";
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
