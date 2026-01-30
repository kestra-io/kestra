package io.kestra.core.storages.kv;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.tenant.TenantService;
import io.micronaut.context.annotation.Requires;
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
public class KVPurgeScheduler {

    @Inject
    private KVStoreService kvStoreService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Scheduled(initialDelay = "${kestra.kv.purge-expired.initial-delay:PT6H}", fixedDelay = "${kestra.kv.purge-expired.fixed-delay:PT6H}")
    public  void purgeExpired(){
        log.info("Start cleaning expired KV store entries");
        List<String> tenants = findTenants();
        for (String tenant : tenants) {
            purgeKVEntriesForTenant(tenant);
        }
    }

    private void purgeKVEntriesForTenant(String tenant) {
        List<String> namespaces = findNamespaces(tenant);
        for (String namespace : namespaces) {
            try {
                KVStore kvStore = kvStoreService.get(tenant, namespace, namespace);
                List<KVEntry> expiredEntries = kvStore.listAll()
                    .stream()
                    .filter(kv -> kv.expirationDate() != null &&
                        kv.expirationDate().isBefore(Instant.now()))
                    .toList();
                if (!expiredEntries.isEmpty()){
                    kvStore.purge(expiredEntries);
                    log.info("{} KV store entries have been deleted on the namespace {} on tenant {}",
                        expiredEntries.size(), namespace, tenant);
                }
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
