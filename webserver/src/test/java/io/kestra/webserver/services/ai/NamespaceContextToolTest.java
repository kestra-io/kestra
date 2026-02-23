package io.kestra.webserver.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NamespaceContextToolTest {

    @Test
    public void getKvStoreKeysReturnsExpectedJson() throws Exception {
        KVStoreService kvStoreService = mock(KVStoreService.class);
        KVStore kvStore = mock(KVStore.class);

        String namespace = "my-namespace";
        String tenantId = null;

        KVEntry entry = new KVEntry(namespace, "key1", 1, "desc", Instant.now(), Instant.now(), null);

        ArrayListTotal<KVEntry> list = new ArrayListTotal<>(List.of(entry), 1);

        when(kvStoreService.get(tenantId, namespace, namespace)).thenReturn(kvStore);
        when(kvStore.list()).thenReturn(list);

        NamespaceContextTool tool = new NamespaceContextTool(kvStoreService);

        String json = tool.getKvStoreKeys(namespace, tenantId);

        JsonNode node = JacksonMapper.ofJson().readTree(json);

        assertThat(node.path("namespace").asText()).isEqualTo(namespace);
        assertThat(node.path("kvKeys").isArray()).isTrue();
        JsonNode first = node.path("kvKeys").get(0);
        assertThat(first.path("key").asText()).isEqualTo("key1");
        assertThat(first.path("description").asText()).isEqualTo("desc");
        assertThat(first.path("updateDate").asText()).isNotEmpty();
    }
}

