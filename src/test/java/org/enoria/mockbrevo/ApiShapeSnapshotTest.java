package org.enoria.mockbrevo;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Guards the JSON contract. The Brevo PHP SDK deserializes strictly, so field
 * names, which fields are present, value types and date formats must not change
 * (e.g. across a Spring Boot / Jackson upgrade).
 *
 * Each case records the HTTP status, content type and the "shape" of the body:
 * every JSON path with its value type, strings classified by date format. Values
 * themselves vary (ids, timestamps, faker data) and are not compared.
 *
 * Snapshots live in src/test/resources/api-shapes/. After an intentional change,
 * regenerate them with {@code ./mvnw test -Dtest=ApiShapeSnapshotTest -Dsnapshots.update=true}
 * and review the diff.
 *
 * Talks to a real embedded server over HTTP and parses with json-smart, so the
 * check doesn't depend on the Jackson version or Spring's test support.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:mockbrevo-shapes;DB_CLOSE_DELAY=-1;MODE=LEGACY",
                "mock-brevo.smtp.enabled=false",
                "mock-brevo.auto-fire-delivered=false"
        })
class ApiShapeSnapshotTest {

    private static final Path SNAPSHOT_DIR = Path.of("src/test/resources/api-shapes");
    private static final boolean UPDATE = Boolean.getBoolean("snapshots.update");

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String key = "shape-" + UUID.randomUUID();
    private final Map<String, String> actual = new LinkedHashMap<>();

    @Test
    void responseShapesMatchSnapshots() throws Exception {
        // Unauthenticated
        capture("v3-account-401", "GET", "/v3/account", null, null);

        // Provisioning + read endpoints on a fresh tenant (seeded defaults)
        capture("v3-account", "GET", "/v3/account", null, key);
        capture("v3-senders", "GET", "/v3/senders", null, key);
        capture("v3-folders", "GET", "/v3/contacts/folders", null, key);

        // Writes
        capture("v3-folders-create", "POST", "/v3/contacts/folders", "{\"name\":\"Snapshots\"}", key);
        String listJson = capture("v3-lists-create", "POST", "/v3/contacts/lists",
                "{\"name\":\"Snapshot list\",\"folderId\":1}", key);
        long listId = Long.parseLong(firstNumber(listJson, "id"));
        capture("v3-contacts-import", "POST", "/v3/contacts/import",
                "{\"fileBody\":\"EMAIL,FIRST_NAME,LAST_NAME\\nada@example.com,Ada,Lovelace\\nalan@example.com,Alan,Turing\","
                        + "\"listIds\":[" + listId + "],\"updateExistingContacts\":true}", key);
        capture("v3-contacts-update", "PUT", "/v3/contacts/ada@example.com",
                "{\"emailBlacklisted\":true,\"listIds\":[" + listId + "]}", key);
        capture("v3-templates-create", "POST", "/v3/smtp/templates",
                "{\"templateName\":\"Snapshot\",\"subject\":\"Hi\",\"htmlContent\":\"<p>Hi</p>\","
                        + "\"sender\":{\"name\":\"S\",\"email\":\"s@example.com\"},\"isActive\":true}", key);
        String campaignJson = capture("v3-campaigns-create", "POST", "/v3/emailCampaigns",
                "{\"name\":\"Snapshot campaign\",\"subject\":\"Hello\",\"sender\":{\"name\":\"S\",\"email\":\"s@example.com\"},"
                        + "\"replyTo\":\"r@example.com\",\"recipients\":{\"listIds\":[" + listId + "]},"
                        + "\"utmCampaign\":\"snap\",\"params\":{\"URL_DON\":\"https://example.com\"}}", key);
        long campaignId = Long.parseLong(firstNumber(campaignJson, "id"));
        capture("v3-campaigns-send-now", "POST", "/v3/emailCampaigns/" + campaignId + "/sendNow", null, key);
        capture("v3-smtp-email", "POST", "/v3/smtp/email",
                "{\"sender\":{\"email\":\"s@example.com\",\"name\":\"S\"},\"to\":[{\"email\":\"to@example.com\",\"name\":\"T\"}],"
                        + "\"cc\":[{\"email\":\"cc@example.com\"}],\"replyTo\":{\"email\":\"r@example.com\"},"
                        + "\"subject\":\"Snapshot\",\"htmlContent\":\"<p>x</p>\",\"params\":{\"a\":1},\"tags\":[\"t\"],"
                        + "\"unmodelledField\":true}", key);
        capture("v3-lists-remove-contacts", "DELETE", "/v3/contacts/lists/" + listId + "/contacts",
                "{\"emails\":[\"alan@example.com\"]}", key);

        // Reads after writes (populated shapes)
        capture("v3-lists", "GET", "/v3/contacts/lists", null, key);
        capture("v3-list-contacts", "GET", "/v3/contacts/lists/" + listId + "/contacts", null, key);
        capture("v3-list-contacts-404", "GET", "/v3/contacts/lists/999999/contacts", null, key);
        capture("v3-templates", "GET", "/v3/smtp/templates", null, key);
        capture("v3-campaigns", "GET", "/v3/emailCampaigns", null, key);

        // Error handling for a malformed body (Spring Boot's default error JSON)
        capture("v3-smtp-email-malformed", "POST", "/v3/smtp/email", "{not json", key);

        // Admin routes the UI and test assertions rely on
        capture("mock-status", "GET", "/mock-status", null, null);
        String emailsJson = capture("mock-status-emails", "GET", "/mock-status/accounts/" + key + "/emails", null, null);
        // The captured request is stored as a JSON string; tests parse it, so guard its shape too.
        Object payload = ((Map<?, ?>) ((List<?>) ((Map<?, ?>) JSONValue.parse(emailsJson)).get("emails")).get(0)).get("payload");
        actual.put("stored-email-payload", shape(payload.toString()));
        capture("mock-status-account-campaigns", "GET", "/mock-status/accounts/" + key + "/campaigns", null, null);
        capture("mock-status-account-lists", "GET", "/mock-status/accounts/" + key + "/lists", null, null);
        capture("mock-status-campaign", "GET", "/mock-status/campaigns/" + campaignId, null, null);
        capture("mock-status-list", "GET", "/mock-status/lists/" + listId, null, null);
        capture("mock-status-requests", "GET", "/mock-status/requests?limit=5", null, null);
        capture("mock-status-create-campaign", "POST", "/mock-status/accounts/" + key + "/campaigns",
                "{\"name\":\"From UI\",\"status\":\"draft\"}", null);

        captureWebhook();

        compareAll();
    }

    /** Outbound webhook JSON as received by a client such as Enoria. */
    private void captureWebhook() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.complete(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    + "\n" + exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            capture("mock-webhooks-fire", "POST", "/mock-webhooks/fire",
                    "{\"url\":\"" + url + "\",\"event\":\"hard_bounce\",\"email\":\"to@example.com\","
                            + "\"reason\":\"mailbox full\",\"messageId\":\"<m@x>\"}", null);
            capture("mock-webhooks-fire-400", "POST", "/mock-webhooks/fire", "{\"event\":\"delivered\"}", null);
            String[] parts = received.get(10, TimeUnit.SECONDS).split("\n", 2);
            actual.put("outbound-webhook", "content-type: " + mediaType(parts[1]) + "\n" + shape(parts[0]));
        } finally {
            server.stop(0);
        }
    }

    private String capture(String name, String method, String path, String body, String apiKey)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("accept", "application/json");
        if (apiKey != null) req.header("api-key", apiKey);
        if (body != null) {
            req.header("content-type", "application/json");
            req.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        String contentType = resp.headers().firstValue("content-type").map(ApiShapeSnapshotTest::mediaType).orElse("-");
        actual.put(name, "status: " + resp.statusCode() + "\ncontent-type: " + contentType + "\n" + shape(resp.body()));
        return resp.body();
    }

    private void compareAll() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : actual.entrySet()) {
            Path file = SNAPSHOT_DIR.resolve(e.getKey() + ".txt");
            if (UPDATE) {
                Files.createDirectories(SNAPSHOT_DIR);
                Files.writeString(file, e.getValue());
                continue;
            }
            if (!Files.exists(file)) {
                problems.add("missing snapshot " + file + " (run with -Dsnapshots.update=true)");
                continue;
            }
            String expected = Files.readString(file);
            if (!expected.equals(e.getValue())) {
                problems.add("shape changed for " + e.getKey() + ":\n" + diff(expected, e.getValue()));
            }
        }
        if (!problems.isEmpty()) fail(String.join("\n\n", problems));
        assertTrue(actual.size() > 20, "expected every case to be captured");
    }

    private static String diff(String expected, String actual) {
        TreeSet<String> exp = new TreeSet<>(expected.lines().toList());
        TreeSet<String> act = new TreeSet<>(actual.lines().toList());
        StringBuilder sb = new StringBuilder();
        exp.stream().filter(l -> !act.contains(l)).forEach(l -> sb.append("  - ").append(l).append('\n'));
        act.stream().filter(l -> !exp.contains(l)).forEach(l -> sb.append("  + ").append(l).append('\n'));
        return sb.toString();
    }

    private static String mediaType(String contentType) {
        return contentType == null ? "-" : contentType.split(";")[0].trim().toLowerCase();
    }

    // ---- shape extraction ---------------------------------------------------

    private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    private static final Pattern OFFSET = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?[+-]\\d{2}:\\d{2}");
    private static final Pattern LOCAL = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    static String shape(String body) {
        if (body == null || body.isBlank()) return "body: <empty>";
        Object parsed = JSONValue.parse(body);
        if (parsed == null) return "body: <not json>";
        TreeSet<String> lines = new TreeSet<>();
        walk(parsed, "$", lines);
        return String.join("\n", lines) + "\n";
    }

    private static void walk(Object v, String path, TreeSet<String> out) {
        if (v == null) {
            out.add(path + " : null");
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) out.add(path + " : {}");
            m.forEach((k, val) -> walk(val, path + "." + k, out));
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) out.add(path + " : []");
            l.forEach(e -> walk(e, path + "[]", out));
        } else if (v instanceof Boolean) {
            out.add(path + " : boolean");
        } else if (v instanceof Integer || v instanceof Long || v instanceof java.math.BigInteger) {
            out.add(path + " : integer");
        } else if (v instanceof Number) {
            out.add(path + " : decimal");
        } else {
            out.add(path + " : " + classify(v.toString()));
        }
    }

    private static String classify(String s) {
        if (INSTANT.matcher(s).matches()) return "string<instant-utc>";
        if (OFFSET.matcher(s).matches()) return "string<datetime-offset>";
        if (LOCAL.matcher(s).matches()) return "string<datetime-local>";
        if (DATE.matcher(s).matches()) return "string<date>";
        return "string";
    }

    private static String firstNumber(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new IllegalStateException("no " + field + " in " + json);
        return m.group(1);
    }
}
