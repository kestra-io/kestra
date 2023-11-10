package io.kestra.core.services;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;

public class StorageInterfaceService {
    @Inject
    private StorageInterface storageInterface;

    public List<String> distinctNamespacesFolders(String tenantId) throws IOException {
        return storageInterface.list(tenantId, null)
            .stream()
            .distinct()
            .filter(fileAttributes -> fileAttributes.getType().equals(FileAttributes.FileType.Directory))
            .map(FileAttributes::getFileName).toList();
    }
}
