package org.enoria.mockbrevo;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end checks of the invariants described in CLAUDE.md: api-key auth,
 * lazy account provisioning, per-account scoping and email capture.
 * Each test uses its own random api-key so tests don't share data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mockbrevo-test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "mock-brevo.smtp.enabled=false",
        "mock-brevo.auto-fire-delivered=false"
})
@AutoConfigureMockMvc
class BrevoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

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
