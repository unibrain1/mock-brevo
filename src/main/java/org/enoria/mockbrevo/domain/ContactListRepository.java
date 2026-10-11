package org.enoria.mockbrevo.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactListRepository extends JpaRepository<ContactList, Long> {
    Page<ContactList> findByAccountOrderByIdAsc(Account account, Pageable pageable);

    Optional<ContactList> findByIdAndAccount(Long id, Account account);

    long countByAccount(Account account);

    /**
     * Bulk delete: no row is loaded. Runs after {@code ContactRepository.deleteByAccount}:
     * flushAutomatically writes its pending contact removals (and contact_in_list rows) first.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ContactList e where e.account = :account")
    int deleteByAccount(@Param("account") Account account);
}
