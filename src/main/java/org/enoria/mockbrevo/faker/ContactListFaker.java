package org.enoria.mockbrevo.faker;

import java.util.ArrayList;
import java.util.List;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.ContactList;
import org.enoria.mockbrevo.domain.ContactListRepository;
import org.enoria.mockbrevo.domain.Folder;
import org.springframework.stereotype.Service;

@Service
public class ContactListFaker {

    private static final String[] DEFAULT_NAMES = {
            "Parish newsletter",
            "Announcements",
            "Volunteers"
    };

    private final ContactListRepository lists;

    public ContactListFaker(ContactListRepository lists) {
        this.lists = lists;
    }

    public ContactList create(Account account, String name, Folder folder) {
        ContactList l = new ContactList();
        l.setAccount(account);
        l.setName(name);
        l.setFolder(folder);
        return lists.save(l);
    }

    public List<ContactList> seedDefaults(Account account, Folder folder) {
        List<ContactList> created = new ArrayList<>();
        for (String name : DEFAULT_NAMES) {
            created.add(create(account, name, folder));
        }
        return created;
    }
}
