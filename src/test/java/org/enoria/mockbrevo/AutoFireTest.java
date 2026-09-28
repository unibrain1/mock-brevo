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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    /** What the receiver saw on arrival: the Authorization header and the committed event count. */
    private record Arrival(String authorization, int committedEvents) {}

    private static final BlockingQueue<Arrival> RECEIVED = new LinkedBlockingQueue<>();
    private static final HttpServer HOOK = startHook();
    /** Set before each test; the receiver thread reads it to see what has committed. */
    private static volatile JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void shareJdbc() {
        jdbc = jdbcTemplate;
    }

    private static HttpServer startHook() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/hook", exchange -> {
                // Counted on a separate connection, so only committed rows are visible.
                Integer events = jdbc.queryForObject("select count(*) from email_event", Integer.class);
                RECEIVED.add(new Arrival(exchange.getRequestHeaders().getFirst("Authorization"),
                        events == null ? 0 : events));
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
                                + "\"cc\":[{\"name\":\"no address either\"}],"
                                + "\"subject\":\"Hi\",\"htmlContent\":\"<p>x</p>\",\"tags\":[\"registry\"]}"))
                .andExpect(status().isCreated());

        Arrival arrival = RECEIVED.poll(10, TimeUnit.SECONDS);
        assertNotNull(arrival, "no delivered webhook received");
        assertEquals("Bearer auto-token", arrival.authorization());
        // Both rows are visible to a receiver when the webhook arrives. (The strict
        // after-commit order is checked in WebhookAfterCommitTest.)
        assertEquals(2, arrival.committedEvents());

        mvc.perform(get("/v3/smtp/statistics/events").header("api-key", key).param("sort", "asc"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].event").value("requests"))
                .andExpect(jsonPath("$.events[1].event").value("delivered"))
                .andExpect(jsonPath("$.events[1].tag").value("registry"));
    }
}
