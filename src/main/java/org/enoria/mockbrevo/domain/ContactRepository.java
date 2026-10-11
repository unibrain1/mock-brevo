package org.enoria.mockbrevo.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByAccountAndEmail(Account account, String email);

    List<Contact> findByAccountAndEmailIn(Account account, List<String> emails);

    Page<Contact> findByAccountAndListsContainingOrderByIdAsc(Account account, ContactList list, Pageable pageable);

    List<Contact> findByListsContainingOrderByIdAsc(ContactList list);

    long countByAccount(Account account);

    /** Loads and removes each row, so JPA also clears join-table rows (contact_in_list). */
    long deleteByAccount(Account account);

    long countByAccountAndListsContaining(Account account, ContactList list);
}
