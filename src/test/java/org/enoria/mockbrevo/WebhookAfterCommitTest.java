package org.enoria.mockbrevo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.enoria.mockbrevo.webhook.WebhookSender;
import org.enoria.mockbrevo.webhook.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The webhook goes out only after the surrounding transaction commits, and not at all on rollback.
 * WebhookSender is @Async, so an end-to-end test can't see this order reliably.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mockbrevo-aftercommit;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "mock-brevo.smtp.enabled=false",
        "mock-brevo.auto-fire-delivered=false"
})
class WebhookAfterCommitTest {

    @MockitoBean
    private WebhookSender sender;

    @Autowired
    private WebhookService webhooks;

    @Autowired
    private TransactionTemplate tx;

    private static WebhookService.Fire fire(String url) {
        return new WebhookService.Fire(url, null, null, "delivered", "to@example.com", null, null, null, null);
    }

    @Test
    void sentOnlyAfterCommit() {
        tx.executeWithoutResult(status -> {
            webhooks.fire(fire("http://127.0.0.1:1/commit"));
            verifyNoInteractions(sender);
        });
        verify(sender).send(eq("http://127.0.0.1:1/commit"), any(), any());
    }

    @Test
    void notSentOnRollback() {
        tx.executeWithoutResult(status -> {
            webhooks.fire(fire("http://127.0.0.1:1/rollback"));
            status.setRollbackOnly();
        });
        verifyNoInteractions(sender);
    }
}
