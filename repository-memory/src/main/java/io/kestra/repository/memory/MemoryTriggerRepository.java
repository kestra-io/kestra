package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Singleton
@MemoryRepositoryEnabled
public class MemoryTriggerRepository implements TriggerRepositoryInterface {

    private final List<Trigger> triggers = new ArrayList<>();

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return triggers.stream()
            .filter(t -> t.uid().equals(trigger.uid()))
            .findFirst();
    }

    @Override
    public Optional<Trigger> findByExecution(Execution execution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Trigger> findAllForAllTenants() {
        return this.triggers;
    }

    @Override
    public Trigger save(Trigger trigger) {
        triggers.add(trigger);

        return trigger;
    }

    @Override
    public Trigger update(Trigger trigger) {
        triggers.add(trigger);

        return trigger;
    }

    @Override
    public Trigger lock(String triggerUid, Function<Trigger, Trigger> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Trigger trigger) {
        triggers.remove(trigger);
    }

    @Override
    public ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<Trigger> find(Pageable from, String query, String tenantId, String namespace, String flowId) {
        List<Trigger> filteredTriggers = triggers.stream().filter(trigger -> {
            if (tenantId != null && !tenantId.equals(trigger.getTenantId())) {
                return false;
            }

            if (namespace != null && !namespace.equals(trigger.getNamespace())) {
                return false;
            }

            if (flowId != null && !flowId.equals(trigger.getFlowId())) {
                return false;
            }

            return true;
        }).toList();
        return new ArrayListTotal<>(filteredTriggers, filteredTriggers.size());
    }
}
