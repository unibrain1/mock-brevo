package org.enoria.mockbrevo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.EmailEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end checks of the invariants described in CLAUDE.md: api-key auth,
 * lazy account provisioning, per-account scoping and email capture.
 * Each test uses its own random api-key so tests don't share data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mockbrevo-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "mock-brevo.smtp.enabled=false",
        "mock-brevo.auto-fire-delivered=false",
        "mock-brevo.webhook-token=default-token"
})
@AutoConfigureMockMvc
class BrevoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private EmailEventRepository emailEvents;

    private static String newKey() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    void missingApiKeyIsRejected() throws Exception {
        mvc.perform(get("/v3/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void blankApiKeyIsRejected() throws Exception {
        mvc.perform(get("/v3/account").header("api-key", "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApiKeyProvisionsAnAccountWithDefaults() throws Exception {
        String key = newKey();

        mvc.perform(get("/v3/account").header("api-key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.firstName").isNotEmpty());

        mvc.perform(get("/v3/senders").header("api-key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senders[0].active").value(true));

        mvc.perform(get("/v3/emailCampaigns").header("api-key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(2));

        mvc.perform(get("/mock-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[*].apiKey", hasItem(key)));
    }

    @Test
    void listsAreScopedPerAccount() throws Exception {
        String keyA = newKey();
        String keyB = newKey();

        long listId = createList(keyA, "Only for A");

        mvc.perform(get("/v3/contacts/lists").header("api-key", keyA))
                .andExpect(jsonPath("$.lists[*].name", hasItem("Only for A")));

        mvc.perform(get("/v3/contacts/lists").header("api-key", keyB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lists[*].name", not(hasItem("Only for A"))));

        mvc.perform(get("/v3/contacts/lists/" + listId + "/contacts").header("api-key", keyB))
                .andExpect(status().isNotFound());
    }

    @Test
    void importedContactsAppearInTheirList() throws Exception {
        String key = newKey();
        long listId = createList(key, "Imported");

        String body = json.writeValueAsString(java.util.Map.of(
                "fileBody", "EMAIL,FIRST_NAME,LAST_NAME\nada@example.com,Ada,Lovelace\nalan@example.com,Alan,Turing",
                "listIds", java.util.List.of(listId),
                "updateExistingContacts", true));

        mvc.perform(post("/v3/contacts/import").header("api-key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processId").isNumber());

        mvc.perform(get("/v3/contacts/lists/" + listId + "/contacts").header("api-key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.contacts[*].email", hasItem("ada@example.com")));
    }

    @Test
    void transactionalEmailIsCapturedForTheSendingAccountOnly() throws Exception {
        String key = newKey();
        String other = newKey();
        mvc.perform(get("/v3/account").header("api-key", other)).andExpect(status().isOk());

        String body = """
                {
                  "sender": {"email": "noreply@example.com", "name": "Example"},
                  "to": [{"email": "someone@example.com"}],
                  "subject": "Hello from the test",
                  "htmlContent": "<p>Hi</p>",
                  "someFieldWeDontModel": true
                }""";

        mvc.perform(post("/v3/smtp/email").header("api-key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId", startsWith("<")));

        mvc.perform(get("/mock-status/accounts/" + key + "/emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.emails[0].subject").value("Hello from the test"))
                .andExpect(jsonPath("$.emails[0].recipients").value("someone@example.com"));

        mvc.perform(get("/mock-status/accounts/" + other + "/emails"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void brevoDeepLinksServeTheAdminUi() throws Exception {
        mvc.perform(get("/marketing-campaign/edit/42"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/contact/list/id/7"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void adminUiFilesAreRevalidatedOnEveryLoad() throws Exception {
        // A cached app.js from an older image breaks the newer index.html.
        for (String path : new String[] {"/index.html", "/js/app.js", "/js/i18n.js", "/css/app.css"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-cache"))
                    .andExpect(header().doesNotExist("Last-Modified"));
        }
    }

    // Needs META-INF/build-info.properties, which the build-info goal writes
    // during `./mvnw test`. A test run from an IDE without Maven fails here.
    @Test
    void versionEndpointReportsTheBuild() throws Exception {
        mvc.perform(get("/mock-status/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("mock-brevo"))
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.upstreamVersion").isNotEmpty())
                .andExpect(jsonPath("$.buildTime").isNotEmpty());
    }

    @Test
    void firedWebhookCopiesTagsFromTheSentEmailAndSendsTheToken() throws Exception {
        String key = newKey();
        try (Hook hook = Hook.start()) {
            String messageId = sendEmail(key, "to@example.com", "[\"car_verification\"]");

            fire(hook.url(), "hard_bounce", "to@example.com", messageId, ",\"token\":\"per-call\"");
            Hook.Request r = hook.next();
            assertEquals("Bearer per-call", r.authorization());
            JsonNode body = json.readTree(r.body());
            assertEquals("car_verification", body.get("tag").asString());
            assertEquals("car_verification", body.get("tags").get(0).asString());
            assertEquals(messageId, body.get("message-id").asString());

            // Fire-body tags replace the copied ones; no token falls back to mock-brevo.webhook-token.
            fire(hook.url(), "delivered", "to@example.com", messageId, ",\"tags\":[\"override\"]");
            r = hook.next();
            assertEquals("Bearer default-token", r.authorization());
            assertEquals("override", json.readTree(r.body()).get("tag").asString());
        }
    }

    @Test
    void firedEventsAreRecordedUnderTheSendingAccount() throws Exception {
        String key = newKey();
        String other = newKey();
        try (Hook hook = Hook.start()) {
            String messageId = sendEmail(key, "to@example.com", "[\"registry\"]");
            fire(hook.url(), "hard_bounce", "to@example.com", messageId, "")
                    .andExpect(jsonPath("$.recorded").value(true));
            // No known messageId: recorded only when an apiKey names the account.
            fire(hook.url(), "spam", "x@example.com", null, ",\"apiKey\":\"" + other + "\"")
                    .andExpect(jsonPath("$.recorded").value(true));
            fire(hook.url(), "spam", "y@example.com", null, "")
                    .andExpect(jsonPath("$.recorded").value(false));
            hook.next();
            hook.next();
            hook.next();
        }
        Account account = accounts.findByApiKey(key).orElseThrow();
        Account otherAccount = accounts.findByApiKey(other).orElseThrow();
        assertEquals(1, emailEvents.countByAccount(account));
        assertEquals(1, emailEvents.countByAccount(otherAccount));
    }

    @Test
    void fireRejectsAnotherAccountsMessageIdAndOverlongValues() throws Exception {
        String owner = newKey();
        String messageId = sendEmail(owner, "to@example.com", "[\"registry\"]");
        mvc.perform(post("/mock-webhooks/fire").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:1/\",\"event\":\"spam\",\"email\":\"to@example.com\","
                                + "\"messageId\":\"" + messageId + "\",\"apiKey\":\"" + newKey() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("different account")));
        // The rejected fire must not leave a new account behind.
        String typo = newKey();
        mvc.perform(post("/mock-webhooks/fire").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:1/\",\"event\":\"spam\",\"email\":\"to@example.com\","
                                + "\"messageId\":\"" + messageId + "\",\"apiKey\":\"" + typo + "\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(accounts.findByApiKey(typo).isEmpty());
        mvc.perform(post("/mock-webhooks/fire").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:1/\",\"event\":\"" + "x".repeat(41)
                                + "\",\"email\":\"to@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("event is longer than 40 characters"));
    }

    @Test
    void blankTokenFallsBackToTheDefault() throws Exception {
        try (Hook hook = Hook.start()) {
            fire(hook.url(), "delivered", "to@example.com", null, ",\"token\":\" \"");
            assertEquals("Bearer default-token", hook.next().authorization());
        }
    }

    @Test
    void webhookTokenIsMaskedWhenTheLoggedBodyIsCutInsideIt() throws Exception {
        // The log keeps the first 16 KB (16384 bytes). With this pad the token value
        // starts at byte 16354, so the cut falls 30 bytes into it.
        String pad = "x".repeat(16262);
        mvc.perform(post("/mock-webhooks/fire").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"http://127.0.0.1:1/\",\"event\":\"delivered\",\"email\":\"to@example.com\","
                        + "\"pad\":\"" + pad + "\",\"token\":\"" + "SECRET".repeat(20) + "\"}"));
        String list = mvc.perform(get("/mock-status/requests?limit=1")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(list).get("requests").get(0).get("id").asLong();
        mvc.perform(get("/mock-status/requests/" + id))
                .andExpect(jsonPath("$.requestTruncated").value(true))
                .andExpect(jsonPath("$.requestBody", containsString("\"token\":\"***\"")))
                .andExpect(jsonPath("$.requestBody", not(containsString("SECRET"))));
    }

    @Test
    void webhookTokenIsNotKeptInTheRequestLog() throws Exception {
        try (Hook hook = Hook.start()) {
            fire(hook.url(), "delivered", "to@example.com", null, ",\"token\":\"do-not-log-me\"");
            // No messageId in the fire body: the webhook still carries one.
            assertTrue(json.readTree(hook.next().body()).get("message-id").asString().endsWith("@mock-brevo.local>"));
        }
        String list = mvc.perform(get("/mock-status/requests?limit=1"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(list).get("requests").get(0).get("id").asLong();
        mvc.perform(get("/mock-status/requests/" + id))
                .andExpect(jsonPath("$.path").value("/mock-webhooks/fire"))
                .andExpect(jsonPath("$.requestBody", containsString("\"token\":\"***\"")))
                .andExpect(jsonPath("$.requestBody", not(containsString("do-not-log-me"))));
    }

    private String sendEmail(String key, String to, String tagsJson) throws Exception {
        String response = mvc.perform(post("/v3/smtp/email").header("api-key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":{\"email\":\"s@example.com\"},\"to\":[{\"email\":\"" + to + "\"}],"
                                + "\"subject\":\"Hi\",\"htmlContent\":\"<p>x</p>\",\"tags\":" + tagsJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("messageId").asString();
    }

    private ResultActions fire(
            String url, String event, String email, String messageId, String extra) throws Exception {
        return mvc.perform(post("/mock-webhooks/fire").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\",\"event\":\"" + event + "\",\"email\":\"" + email + "\""
                                + (messageId != null ? ",\"messageId\":\"" + messageId + "\"" : "") + extra + "}"))
                .andExpect(status().isAccepted());
    }

    /** A local HTTP receiver for outbound webhooks. */
    private record Hook(HttpServer server,
                        BlockingQueue<Request> received) implements AutoCloseable {

        record Request(String authorization, String body) {}

        static Hook start() throws IOException {
            var queue = new LinkedBlockingQueue<Request>();
            var server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/hook", exchange -> {
                queue.add(new Request(exchange.getRequestHeaders().getFirst("Authorization"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return new Hook(server, queue);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        }

        Request next() throws InterruptedException {
            Request r = received.poll(10, TimeUnit.SECONDS);
            if (r == null) throw new AssertionError("no webhook received");
            return r;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private long createList(String key, String name) throws Exception {
        String response = mvc.perform(post("/v3/contacts/lists").header("api-key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("id").asLong();
    }
}
