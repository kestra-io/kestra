package org.kestra.core.repositories;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.utils.IdUtils;

import java.time.ZonedDateTime;
import java.util.Optional;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public abstract class AbstractTriggerRepositoryTest {
    @Inject
    protected TriggerRepositoryInterface triggerRepository;

    private static Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("org.kestra.unittest")
            .flowRevision(1)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .date(ZonedDateTime.now());
    }

    @Test
    void all() {
        Trigger.TriggerBuilder<?, ?> builder = trigger();

        Optional<Trigger> find = triggerRepository.findLast(builder.build());
        assertThat(find.isPresent(), is(false));

        Trigger save = triggerRepository.save(builder.build());

        find = triggerRepository.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));

        save = triggerRepository.save(builder.executionId(IdUtils.create()).build());

        find = triggerRepository.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));
    }
}
