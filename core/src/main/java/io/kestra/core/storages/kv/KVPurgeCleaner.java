package io.kestra.core.storages.kv;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.tenant.TenantService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Requires(property = "kestra.kv.purge-expired.enabled", value = "true", defaultValue = "true")
@Singleton
public class KVPurgeCleaner {

    @Inject
    private KVStoreService kvStoreService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @Value("${kestra.kv.purge-expired.batch-size:1000}")
    private Integer batchSize;

    @Scheduled(initialDelay = "${kestra.kv.purge-expired.initial-delay:PT1H}", fixedDelay = "${kestra.kv.purge-expired.fixed-delay:PT1H}")
    public  void purgeExpired(){
        log.info("Start cleaning expired KV store entries");
        List<String> tenants = findTenants();
        for (String tenant : tenants) {
            purgeKVEntriesForTenant(tenant);
        }
    }

    private void purgeKVEntriesForTenant(String tenant) {
        List<String> namespaces = findNamespaces(tenant);
        Instant now = Instant.now();
        for (String namespace : namespaces) {
            try {
                KVStore kvStore = kvStoreService.get(tenant, namespace, namespace);
                List<KVEntry> expiredEntries;
                do {
                    expiredEntries = kvMetadataRepository.find(
                            //We always fetch the first page because we delete every KV entries that we find.
                            Pageable.from(1, batchSize, Sort.of(Order.asc("name"))),
                            tenant,
                            List.of(
                                QueryFilter.builder().field(Field.NAMESPACE).value(namespace).operation(Op.EQUALS).build(),
                                QueryFilter.builder().field(Field.EXPIRATION_DATE).value(now).operation(Op.LESS_THAN).build()
                            ),
                            false,
                            true,
                            FetchVersion.ALL
                        ).stream()
                        .map(KVEntry::from)
                        .toList();
                    if (!expiredEntries.isEmpty()){
                        kvStore.purge(expiredEntries);
                        log.info("{} KV store entries have been deleted on the namespace {} on tenant {}",
                            expiredEntries.size(), namespace, tenant);
                    }
                } while (!expiredEntries.isEmpty());
            } catch (IOException e) {
                log.error("Unable to delete KV entries for the namespace {} on tenant {}", namespace, tenant, e);
            }
        }
    }

    protected List<String> findNamespaces(String tenant) {
        return flowRepository.findDistinctNamespace(tenant);
    }

    protected List<String> findTenants(){
        return List.of(TenantService.MAIN_TENANT);
    }
}
