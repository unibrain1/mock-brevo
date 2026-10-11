package org.enoria.mockbrevo.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {
    Page<EmailCampaign> findByAccountOrderByIdDesc(Account account, Pageable pageable);

    Optional<EmailCampaign> findByIdAndAccount(Long id, Account account);

    long countByAccount(Account account);

    /**
     * Bulk delete: no row is loaded. Runs after {@code ContactRepository.deleteByAccount}:
     * flushAutomatically writes its pending contact removals (and contact_in_list rows) first.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from EmailCampaign e where e.account = :account")
    int deleteByAccount(@Param("account") Account account);
}
