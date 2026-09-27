package org.enoria.mockbrevo.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {
    long countByAccount(Account account);
}
