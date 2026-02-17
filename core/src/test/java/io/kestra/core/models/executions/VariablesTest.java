package io.kestra.core.models.executions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class VariablesTest {

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    @SuppressWarnings("unchecked")
    void inMemory() {
        // simple
        Map<String, Object> outputs = Map.of("key", "value");
        var variables = Variables.inMemory(outputs);
        assertThat(variables.get("key")).isEqualTo("value");

        // nested
        Map<String, Object> nest = Map.of("nest", "value");
        var nestVariables = Variables.inMemory(nest);
        Map<String, Object> host = Map.of("key", nestVariables);
        var hostVariables = Variables.inMemory(host);
        assertThat(((Map<String, Object>) hostVariables.get("key")).get("nest")).isEqualTo("value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inStorage() {
        var storageContext = StorageContext.forTask(MAIN_TENANT, "namespace", "flow", "execution", "task", "taskRun", null);
        var internalStorage = new InternalStorage(storageContext, storageInterface, namespaceFactory);
        Variables.StorageContext variablesContext = new Variables.StorageContext(MAIN_TENANT, "namespace");

        // simple
        Map<String, Object> outputs = Map.of("key", "value");
        var variables = Variables.inStorage(internalStorage, outputs);
        assertThat(variables.get("key")).isEqualTo("value");

        // re-read it from URI
        URI uri = ((Variables.InStorageVariables) variables).getStorageUri();
        variables = Variables.inStorage(variablesContext, uri);
        assertThat(variables.get("key")).isEqualTo("value");

        // nested
        Map<String, Object> nest = Map.of("nest", "value");
        var nestVariables = Variables.inStorage(internalStorage, nest);
        Map<String, Object> host = Map.of("key", nestVariables);
        var hostVariables = Variables.inStorage(internalStorage, host);
        assertThat(((Map<String, Object>) hostVariables.get("key")).get("nest")).isEqualTo("value");

        // re-read it from URI
        uri = ((Variables.InStorageVariables) hostVariables).getStorageUri();
        hostVariables = Variables.inStorage(variablesContext, uri);
        assertThat(((Map<String, Object>) hostVariables.get("key")).get("nest")).isEqualTo("value");
    }
}
