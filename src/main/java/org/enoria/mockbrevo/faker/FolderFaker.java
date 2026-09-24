package org.enoria.mockbrevo.faker;

import java.util.List;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.Folder;
import org.enoria.mockbrevo.domain.FolderRepository;
import org.springframework.stereotype.Service;

@Service
public class FolderFaker {

    private static final String DEFAULT_FOLDER_NAME = "General";

    private final FolderRepository folders;

    public FolderFaker(FolderRepository folders) {
        this.folders = folders;
    }

    public Folder create(Account account, String name) {
        Folder f = new Folder();
        f.setAccount(account);
        f.setName(name != null && !name.isBlank() ? name : DEFAULT_FOLDER_NAME);
        return folders.save(f);
    }

    public List<Folder> seedDefaults(Account account) {
        return List.of(create(account, DEFAULT_FOLDER_NAME));
    }
}
