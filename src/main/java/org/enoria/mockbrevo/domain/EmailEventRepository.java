package org.enoria.mockbrevo.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {
    long countByAccount(Account account);

    /** Bulk delete: no row is loaded. */
    @Modifying(flushAutomatically = true)
    @Query("delete from EmailEvent e where e.account = :account")
    int deleteByAccount(@Param("account") Account account);

    List<EmailEvent> findByAccountAndOccurredAtBetween(Account account, Instant from, Instant to);
}
