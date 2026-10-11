package org.enoria.mockbrevo.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
    Page<SentEmail> findByAccountOrderByIdDesc(Account account, Pageable pageable);

    long countByAccount(Account account);

    /** Bulk delete: no row is loaded. flushAutomatically writes pending contact removals first. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SentEmail e where e.account = :account")
    int deleteByAccount(@Param("account") Account account);

    Optional<SentEmail> findByMessageId(String messageId);
}
