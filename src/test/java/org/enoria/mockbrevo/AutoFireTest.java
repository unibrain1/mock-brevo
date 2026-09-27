package org.enoria.mockbrevo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sends with MOCK_AUTO_FIRE_DELIVERED on. The delivered webhook goes out after the
 * send commits, so a receiver that reads the event report on arrival finds both events.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mockbrevo-autofire;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "mock-brevo.smtp.enabled=false",
        "mock-brevo.auto-fire-delivered=true",
        "mock-brevo.webhook-token=auto-token"
})
@AutoConfigureMockMvc
class AutoFireTest {

    private static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();
    private static final HttpServer HOOK = startHook();

    @Autowired
    private MockMvc mvc;

    private static HttpServer startHook() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/hook", exchange -> {
                RECEIVED.add(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void webhookUrl(DynamicPropertyRegistry registry) {
        registry.add("mock-brevo.default-webhook-url",
                () -> "http://127.0.0.1:" + HOOK.getAddress().getPort() + "/hook");
    }

    @AfterAll
    static void stopHook() {
        HOOK.stop(0);
    }

    @Test
    void autoFiredDeliveredIsSentAfterCommitAndReported() throws Exception {
        String key = "autofire-" + UUID.randomUUID();
        // The null address is skipped, not a failed send.
        mvc.perform(post("/v3/smtp/email").header("api-key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":{\"email\":\"s@example.com\"},\"to\":[{\"email\":\"to@example.com\"},{\"name\":\"no address\"}],"
                                + "\"subject\":\"Hi\",\"htmlContent\":\"<p>x</p>\",\"tags\":[\"registry\"]}"))
                .andExpect(status().isCreated());

        String auth = RECEIVED.poll(10, TimeUnit.SECONDS);
        assertNotNull(auth, "no delivered webhook received");
        assertEquals("Bearer auto-token", auth);

        mvc.perform(get("/v3/smtp/statistics/events").header("api-key", key).param("sort", "asc"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].event").value("requests"))
                .andExpect(jsonPath("$.events[1].event").value("delivered"))
                .andExpect(jsonPath("$.events[1].tag").value("registry"));
    }
}
