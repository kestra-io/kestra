package org.kestra.repository.memory;

import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.repositories.TriggerRepositoryInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;

@Singleton
@MemoryRepositoryEnabled
public class MemoryTriggerRepository implements TriggerRepositoryInterface {
    private final List<Trigger> triggers = new ArrayList<>();

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Trigger> findAll() {
        return this.triggers;
    }
}
