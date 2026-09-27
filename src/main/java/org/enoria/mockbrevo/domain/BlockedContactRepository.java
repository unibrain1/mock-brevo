package org.enoria.mockbrevo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedContactRepository extends JpaRepository<BlockedContact, Long> {
    List<BlockedContact> findByAccount(Account account);

    Optional<BlockedContact> findByAccountAndEmailIgnoreCase(Account account, String email);

    long countByAccount(Account account);
}
