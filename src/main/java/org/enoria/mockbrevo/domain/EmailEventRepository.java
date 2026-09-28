package org.enoria.mockbrevo.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {
    long countByAccount(Account account);

    List<EmailEvent> findByAccountAndOccurredAtBetween(Account account, Instant from, Instant to);
}
